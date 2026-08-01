"""
DMS browser-use AI ??? -- DeepSeek?????
- ??????????? GFW ??? uBlock/cookies extension????
- ?? use_vision=False?DeepSeek ??????
"""
import asyncio, os, sys, json, datetime
from pathlib import Path

# ?? .env
env_path = Path(__file__).parent / ".env"
if env_path.exists():
    for line in env_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        os.environ.setdefault(k.strip(), v.strip())

from browser_use import Agent, BrowserProfile, BrowserSession
from browser_use.llm import ChatOpenAI

BASE = os.environ.get("DMS_BASE_URL", "http://8.133.193.238:8083")
USER = os.environ.get("DMS_ADMIN_USER", "admin")
PWD  = os.environ.get("DMS_ADMIN_PASSWORD", "Sh123456")
TEN  = os.environ.get("DMS_TENANT", "default")

TASK = os.environ.get("DMS_TASK", f"""
Open {BASE}. On the login page there are three input fields in this order: tenant, username, password, plus a login button.
Fill: tenant='{TEN}', username='{USER}', password='{PWD}', then click login.
After login (URL should contain /home), click the sidebar menu named exactly "??".
Wait for the product list table to load.
Report finding a total count number typically shown in the pagination area at the bottom right (e.g. "? 200 ?").
Return a JSON on the final answer: {{"login": true_or_false, "products_total": <int or null>, "notes": "<any error text you saw>"}}
""")

async def run():
    llm = ChatOpenAI(
        model=os.environ.get("OPENAI_MODEL", "deepseek-v4-flash"),
        api_key=os.environ["OPENAI_API_KEY"],
        base_url=os.environ.get("OPENAI_BASE_URL", "https://api.deepseek.com/v1"),
        temperature=0,
        dont_force_structured_output=True,
        add_schema_to_system_prompt=True,
    )
    profile = BrowserProfile(
        enable_default_extensions=False,   # ?????????????????
        headless=False,                     # ??? True??? Playwright ????????????
    )
    session = BrowserSession(browser_profile=profile)
    agent = Agent(task=TASK, llm=llm, browser_session=session, use_vision=False)
    result = await agent.run(max_steps=15)
    ts = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    out = Path(__file__).parent / "reports" / f"ai_smoke_{ts}.json"
    out.parent.mkdir(exist_ok=True)
    # AgentHistoryList ?? final_result
    final = None
    try:
        final = result.final_result() if hasattr(result, 'final_result') else str(result)
    except Exception as e:
        final = f"(no final: {e})"
    out.write_text(json.dumps({"final": final}, ensure_ascii=False, indent=2), encoding="utf-8")
    print("=== AI Smoke FINAL ===")
    print(final)
    print(f"[saved] {out}")

if __name__ == "__main__":
    asyncio.run(run())