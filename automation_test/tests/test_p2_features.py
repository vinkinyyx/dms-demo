"""
P2 feature automation tests:
- MFA (TOTP) setup / confirm / two-step login / disable
- Rate limiting (429) on login endpoint
- Stocktake list & detail
- Report subscription CRUD / toggle / run-now
- Expiry alerts summary
- Traceability endpoint
"""
import base64
import hashlib
import hmac
import struct
import time

import pytest
import requests

from utils.api_client import ApiClient
import config


def _totp(secret: str) -> str:
    key = base64.b32decode(secret)
    counter = int(time.time()) // 30
    msg = struct.pack(">Q", counter)
    h = hmac.new(key, msg, hashlib.sha1).digest()
    o = h[-1] & 0x0F
    code = ((h[o] & 0x7F) << 24 | (h[o + 1] & 0xFF) << 16 | (h[o + 2] & 0xFF) << 8 | (h[o + 3] & 0xFF)) % 1000000
    return f"{code:06d}"


@pytest.fixture(scope="module")
def admin():
    c = ApiClient()
    resp = c.post(config.ApiPaths.LOGIN, {"tenantCode": "", "username": "admin", "password": "Sh123456"})
    assert resp.is_success, f"admin login failed: {resp.msg}"
    token = resp.body.get("accessToken") if isinstance(resp.body, dict) else None
    assert token
    c.set_token(token)
    return c


class TestMfa:
    """SEC-03 Two-factor verification (TOTP)."""

    def test_mfa_full_lifecycle(self):
        # We use the sales account which should have MFA disabled at start.
        c = ApiClient()
        login = c.post(config.ApiPaths.LOGIN, {"tenantCode": "", "username": "sales", "password": "Dms@123456"})
        token = login.body.get("accessToken")
        c.set_token(token)

        # setup returns secret + otpAuthUrl
        setup = c.get("/api/auth/mfa/setup")
        assert setup.is_success, setup.msg
        secret = setup.body.get("secret")
        assert secret and len(secret) >= 16
        assert setup.body.get("otpAuthUrl", "").startswith("otpauth://totp/")
        assert setup.body.get("enabled") is False

        # wrong code rejected
        bad = c.post("/api/auth/mfa/confirm", {"code": "000000"})
        assert not bad.is_success, "wrong code should be rejected"

        # correct code enables
        ok = c.post("/api/auth/mfa/confirm", {"code": _totp(secret)})
        assert ok.is_success, ok.msg

        # re-login requires MFA
        relogin = c.post(config.ApiPaths.LOGIN, {"tenantCode": "", "username": "sales", "password": "Dms@123456"})
        assert relogin.is_success
        assert relogin.body.get("mfaRequired") is True
        mfa_token = relogin.body.get("mfaToken")
        assert mfa_token

        # wrong code at verify is rejected (no access token)
        wrong = c.post("/api/auth/mfa/verify", {"mfaToken": mfa_token, "code": "000000"})
        assert not wrong.is_success

        # correct verify issues tokens
        # need a fresh mfa token (wrong attempt may not invalidate, but obtain a new one to be safe)
        relogin2 = c.post(config.ApiPaths.LOGIN, {"tenantCode": "", "username": "sales", "password": "Dms@123456"})
        mfa_token2 = relogin2.body.get("mfaToken")
        verified = c.post("/api/auth/mfa/verify", {"mfaToken": mfa_token2, "code": _totp(secret)})
        assert verified.is_success, verified.msg
        assert verified.body.get("accessToken")

        # disable with correct code (using the verified token)
        c.set_token(verified.body["accessToken"])
        disabled = c.post("/api/auth/mfa/disable", {"code": _totp(secret)})
        assert disabled.is_success, disabled.msg

    def test_mfa_setup_requires_auth(self):
        c = ApiClient()
        resp = c.get("/api/auth/mfa/setup")
        assert resp.status_code == 401


class TestRateLimit:
    """SEC-04 Interface rate limiting."""

    def test_zz_login_burst_eventually_rate_limited(self):
        # Rate limit default is 60/min per IP. We don't want to lock out other tests,
        # so we verify the endpoint returns 429 after a large burst, using a unique invalid body
        # to avoid successful side effects. We send up to 80 rapid requests.
        url = config.API_BASE + config.ApiPaths.LOGIN
        saw_429 = False
        for _ in range(70):
            r = requests.post(url, json={"tenantCode": "", "username": "__ratelimit_probe__", "password": "x"}, timeout=10)
            if r.status_code == 429:
                saw_429 = True
                body = r.json()
                assert body.get("code") == 42901
                break
        assert saw_429, "expected 429 after >60 login attempts within a minute"


class TestStocktake:
    """BIZ-02 Inventory stocktake."""

    def test_stocktake_list_and_detail(self, admin):
        client = admin
        resp = client.get("/api/stocktakes", {"page": 1, "size": 10})
        assert resp.is_success, resp.msg
        # list response should be paginated
        assert isinstance(resp.body, dict)
        items = resp.items
        assert isinstance(items, list)
        if items:
            sid = items[0].get("id")
            detail = client.get(f"/api/stocktakes/{sid}")
            assert detail.is_success, detail.msg
            assert detail.body.get("id") == sid


class TestReportSubscription:
    """DAT-05 Report subscription & scheduled push."""

    def test_subscription_crud_toggle_runnow(self, admin):
        client = admin
        created = client.post("/api/report-subscriptions", {
            "reportType": "sales-ranking",
            "name": "P2_AUTO_TEST_SUB",
            "cronExpr": "DAILY",
            "emails": "autotest@example.com",
            "active": True,
            "params": "{}",
        })
        assert created.is_success, created.msg
        sub_id = (created.body or {}).get("id")
        assert sub_id

        try:
            toggle = client.post(f"/api/report-subscriptions/{sub_id}/toggle")
            assert toggle.is_success, toggle.msg

            run = client.post(f"/api/report-subscriptions/{sub_id}/run-now")
            # run-now may succeed or return a known business status; should not 500
            assert run.status_code < 500, run.msg
        finally:
            client.delete(f"/api/report-subscriptions/{sub_id}")


class TestExpiryAlerts:
    """NEW-13 Inventory batch expiry alert dashboard."""

    def test_expiry_alerts_and_summary(self, admin):
        client = admin
        alerts = client.get("/api/inventory/expiry-alerts", {"page": 1, "size": 5})
        assert alerts.is_success, alerts.msg
        summary = client.get("/api/inventory/expiry-summary")
        assert summary.is_success, summary.msg


class TestTraceability:
    """BIZ-01 Serial number / UDI traceability."""

    def test_traceability_endpoint_available(self, admin):
        client = admin
        # endpoint should respond (even if no data)
        resp = client.get("/api/traceability", {"keyword": "TEST"})
        assert resp.status_code < 500, resp.msg
