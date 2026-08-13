package com.dms.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpUtilTest {

    @Test
    @DisplayName("Generated secret is valid Base32 (A-Z2-7, padded)")
    void secretIsBase32() {
        String secret = TotpUtil.generateSecret();
        assertThat(secret).isNotBlank().matches("[A-Z2-7]+=*");
        assertThat(TotpUtil.base32Decode(secret)).hasSize(20);
    }

    @Test
    @DisplayName("codeAt produces a stable 6-digit code for a fixed time")
    void codeIsSixDigits() {
        String secret = TotpUtil.generateSecret();
        String code = TotpUtil.codeAt(secret, 1_700_000_000L);
        assertThat(code).matches("\\d{6}");
        // Deterministic
        assertThat(TotpUtil.codeAt(secret, 1_700_000_000L)).isEqualTo(code);
    }

    @Test
    @DisplayName("RFC 6238 conformance: known vector (SHA1)")
    void rfcVector() {
        // RFC 6238 test secret "12345678901234567890" in ASCII, Base32 = GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        // T=59s -> counter 1 -> expected SHA1 6-digit code 287082
        assertThat(TotpUtil.codeAt(secret, 59L)).isEqualTo("287082");
        // T=1111111109 -> expected 081804
        assertThat(TotpUtil.codeAt(secret, 1111111109L)).isEqualTo("081804");
    }

    @Test
    @DisplayName("verify accepts current code and rejects wrong code")
    void verifyBehaviour() {
        String secret = TotpUtil.generateSecret();
        String current = TotpUtil.currentCode(secret);
        assertThat(TotpUtil.verify(secret, current)).isTrue();
        assertThat(TotpUtil.verify(secret, "000000")).isFalse();
        assertThat(TotpUtil.verify(secret, "abc")).isFalse();
        assertThat(TotpUtil.verify(secret, null)).isFalse();
    }

    @Test
    @DisplayName("otpAuthUrl contains secret and issuer")
    void otpAuthUrl() {
        String url = TotpUtil.otpAuthUrl("DMS", "admin", "SECRETKEY");
        assertThat(url).startsWith("otpauth://totp/");
        assertThat(url).contains("secret=SECRETKEY");
        assertThat(url).contains("issuer=DMS");
        assertThat(url).contains("digits=6");
        assertThat(url).contains("period=30");
    }
}
