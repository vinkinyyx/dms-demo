# Browser-Use 使用指南

## 什么是 browser-use？

browser-use 是一个将 AI 大模型与浏览器自动化深度融合的开源框架，核心理念是让 AI Agent 像人类一样"看见"网页并与之交互。

- GitHub: https://github.com/browser-use/browser-use
- 文档: https://docs.browser-use.com/
- Stars: ~105K

## 环境配置

### 1. 激活虚拟环境

```powershell
cd d:\Workspace\TRAE\DMS
.browser-use-env\Scripts\Activate.ps1
```

### 2. 配置 API Key

复制 `.env.example` 为 `.env` 并填入你的 API Key：

```
OPENAI_API_KEY=sk-your-actual-api-key
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-4o-mini
```

支持的 LLM 提供商：
- OpenAI (GPT-4o, GPT-4o-mini, GPT-4)
- Anthropic (Claude)
- Google Gemini
- Ollama (本地模型)
- Groq
- Azure OpenAI
- 等...

## 快速开始

### 方式一：纯浏览器 API（不依赖 LLM）

直接使用 browser-use 的底层浏览器 API，适合编写确定性测试：

```powershell
python tools/browser-use/test_browser_api.py
```

### 方式二：AI Agent 驱动（需要 LLM）

用自然语言描述任务，让 AI 自动执行：

```powershell
python tools/browser-use/test_smoke.py
```

### 方式三：交互式 Python 脚本

```python
import asyncio
from browser_use import Agent
from langchain_openai import ChatOpenAI

async def main():
    agent = Agent(
        task="去百度搜索 'DMS 文档管理系统'，找到前3个结果的标题和链接",
        llm=ChatOpenAI(model="gpt-4o-mini"),
    )
    history = await agent.run()
    print(history.final_result())

asyncio.run(main())
```

## DMS 项目测试场景

### 1. 冒烟测试

`test_smoke.py` - 自动执行完整的 DMS 系统冒烟测试：
- 登录验证
- 菜单导航
- 产品列表/详情/新增/删除
- 操作日志
- 退出登录

### 2. 自定义测试任务

创建你自己的测试脚本：

```python
from browser_use import Agent, Browser
from langchain_openai import ChatOpenAI

browser = Browser(headless=False)

agent = Agent(
    task="""
    测试 DMS 系统的移动端 H5 页面：
    1. 打开 http://8.133.193.238:8083/mobile/login
    2. 验证移动端登录页面样式
    3. 登录后检查移动端工作台
    """,
    llm=ChatOpenAI(model="gpt-4o-mini"),
    browser=browser,
    use_vision=True,
)

await agent.run()
```

## 常用配置

### Browser 配置

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `headless` | 无头模式 | False |
| `enable_default_extensions` | 启用默认扩展 | True |
| `window_size` | 窗口大小 | 1920x1200 |
| `user_data_dir` | 用户数据目录 | 临时目录 |
| `storage_state` | 存储状态（登录态） | None |
| `proxy` | 代理设置 | None |

### Agent 配置

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `use_vision` | 使用视觉模型（截图理解） | True |
| `max_actions_per_step` | 每步最大操作数 | 5 |
| `max_failures` | 最大失败重试次数 | 5 |
| `save_conversation_path` | 保存对话记录路径 | None |
| `generate_gif` | 生成操作 GIF | False |

## 与 Playwright 的区别

| 特性 | browser-use | Playwright |
|------|-------------|------------|
| 驱动方式 | AI 自然语言驱动 | 代码驱动 |
| 元素定位 | AI 自动识别 | CSS/XPath 选择器 |
| 适用场景 | 探索式测试、冒烟测试 | 回归测试、CI/CD |
| 稳定性 | 依赖 LLM 能力 | 稳定可靠 |
| 维护成本 | 低（自然语言） | 高（选择器维护） |
| 执行速度 | 较慢（LLM 推理时间） | 快 |

## 最佳实践

1. **先用 Browser API 做确定性测试**，稳定后再考虑 AI 驱动
2. **给 AI 明确的任务描述和验收标准**，越具体效果越好
3. **开启视觉模式** `use_vision=True` 提高复杂页面的识别准确率
4. **保存对话记录** `save_conversation_path` 便于排查问题
5. **合理设置重试次数** `max_failures` 避免无限循环
6. **重要操作前截图留证** 便于事后分析

## 注意事项

- 首次运行会下载浏览器扩展（uBlock 等），需要网络
- 默认扩展下载可能失败，建议设置 `enable_default_extensions=False`
- `from_system_chrome()` 会复制 Chrome 配置，需关闭 Chrome 后使用
- LLM API 会产生费用，注意控制调用次数
- Windows 环境下无头模式可能有 CDP 连接问题，建议先使用有头模式调试
