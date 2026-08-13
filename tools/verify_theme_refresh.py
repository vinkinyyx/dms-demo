import asyncio, json
from pathlib import Path
from playwright.async_api import async_playwright
OUT=Path('test-results/theme-refresh'); OUT.mkdir(parents=True,exist_ok=True)
presets=['blue','violet','green','orange']
expected={'blue':'#1677ff','violet':'#722ed1','green':'#00b96b','orange':'#fa8c16'}
async def check(page, name, url, vp, mobile=False):
    errors=[]
    page.on('console', lambda m: errors.append(f'{m.type}:{m.text}') if m.type in ('error','warning') else None)
    await page.goto(url, wait_until='networkidle')
    await page.wait_for_timeout(500)
    await page.screenshot(path=str(OUT/f'{name}-light-blue.png'), full_page=True)
    for preset in presets:
        await page.locator(f'.theme-dot, .theme-chip, .m-theme-dot').nth(presets.index(preset)).click()
        await page.wait_for_timeout(250)
    await (page.locator('.mode-toggle, .m-mode').first).click()
    await page.wait_for_timeout(400)
    val=await page.evaluate("""() => {
      const cs=getComputedStyle(document.documentElement);
      const btn=document.querySelector('.el-button--primary, .van-button--primary');
      return {mode:document.documentElement.dataset.mode, theme:document.documentElement.dataset.theme, primary:cs.getPropertyValue('--dms-color-primary').trim(), bodyBg:getComputedStyle(document.body).backgroundColor, btn:btn?getComputedStyle(btn).backgroundColor:null};
    }""")
    await page.screenshot(path=str(OUT/f'{name}-dark-orange.png'), full_page=True)
    print(name, val, 'errors', errors[:3])
    assert val['mode']=='dark'
    assert val['theme']=='orange'
    assert val['primary']==expected['orange']
async def main():
    async with async_playwright() as p:
        b=await p.chromium.launch(headless=True)
        for name,url,vp,mobile in [
            ('pc-login','http://127.0.0.1:4174/login',{'width':1440,'height':960},False),
            ('mobile-login','http://127.0.0.1:4174/mobile/login',{'width':390,'height':844},True),
            ('admin-login','http://127.0.0.1:4175/admin/login',{'width':1440,'height':960},False),
        ]:
            page=await b.new_page(viewport=vp,is_mobile=mobile,device_scale_factor=2)
            try: await check(page,name,url,vp,mobile)
            finally: await page.close()
        await b.close()
asyncio.run(main())
