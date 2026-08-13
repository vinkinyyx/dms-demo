"""
DMS自动化测试 - API客户端封装
提供统一的HTTP请求方法，自动携带Token、统一异常处理
"""
import requests
import json
import logging
from typing import Optional, Dict, Any
import config

logger = logging.getLogger(__name__)


class ApiClient:
    """统一API客户端，支持业务前台和平台后台两套Token"""

    def __init__(self, token: Optional[str] = None, is_admin: bool = False):
        """
        :param token: JWT token
        :param is_admin: 是否平台后台token（用于区分token来源）
        """
        self.session = requests.Session()
        self.token = token
        self.is_admin = is_admin
        self.base_url = config.API_BASE
        self._setup_headers()

    def _setup_headers(self):
        """设置请求头"""
        self.session.headers.update({
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "DMS-AutoTest/1.0",
        })
        if self.token:
            self.session.headers["Authorization"] = f"Bearer {self.token}"

    def set_token(self, token: str, is_admin: bool = False):
        """更新token"""
        self.token = token
        self.is_admin = is_admin
        self.session.headers["Authorization"] = f"Bearer {token}"

    def clear_token(self):
        """清除token"""
        self.token = None
        self.session.headers.pop("Authorization", None)

    def _url(self, path: str) -> str:
        """拼接完整URL"""
        if path.startswith("http"):
            return path
        return f"{self.base_url}{path}"

    def _log_and_return(self, method: str, path: str, response: requests.Response):
        """统一日志和返回"""
        try:
            body = response.json()
        except Exception:
            body = response.text[:500]

        log_msg = f"[{method}] {path} -> {response.status_code}"
        if response.status_code >= 400:
            logger.warning(f"{log_msg} | Response: {body}")
        else:
            logger.info(log_msg)

        return ApiResponse(response)

    def get(self, path: str, params: Dict = None, **kwargs) -> "ApiResponse":
        url = self._url(path)
        resp = self.session.get(url, params=params, timeout=config.TIMEOUT, **kwargs)
        return self._log_and_return("GET", path, resp)

    def post(self, path: str, json_data: Dict = None, **kwargs) -> "ApiResponse":
        url = self._url(path)
        resp = self.session.post(url, json=json_data, timeout=config.TIMEOUT, **kwargs)
        return self._log_and_return("POST", path, resp)

    def put(self, path: str, json_data: Dict = None, **kwargs) -> "ApiResponse":
        url = self._url(path)
        resp = self.session.put(url, json=json_data, timeout=config.TIMEOUT, **kwargs)
        return self._log_and_return("PUT", path, resp)

    def delete(self, path: str, **kwargs) -> "ApiResponse":
        url = self._url(path)
        resp = self.session.delete(url, timeout=config.TIMEOUT, **kwargs)
        return self._log_and_return("DELETE", path, resp)

    def post_form(self, path: str, data: Dict = None, files: Dict = None) -> "ApiResponse":
        """提交表单/文件上传"""
        url = self._url(path)
        # 文件上传时移除Content-Type让requests自动设置boundary
        headers = dict(self.session.headers)
        headers.pop("Content-Type", None)
        resp = self.session.post(url, data=data, files=files, headers=headers, timeout=config.LONG_TIMEOUT)
        return self._log_and_return("POST(FORM)", path, resp)

    def download(self, path: str, params: Dict = None) -> bytes:
        """下载文件"""
        url = self._url(path)
        resp = self.session.get(url, params=params, timeout=config.LONG_TIMEOUT, stream=True)
        if resp.status_code == 200:
            return resp.content
        logger.error(f"Download failed: {path} -> {resp.status_code}")
        return b""


class ApiResponse:
    """统一响应包装"""

    def __init__(self, response: requests.Response):
        self.response = response
        self.status_code = response.status_code
        try:
            self.data = response.json()
        except Exception:
            self.data = {}

    @property
    def code(self) -> int:
        """业务码（0=成功，与HTTP 200并存）"""
        return self.data.get("code", self.status_code)

    @property
    def msg(self) -> str:
        """业务消息（兼容 msg / message 两种字段）"""
        return self.data.get("msg") or self.data.get("message") or ""

    @property
    def body(self) -> Any:
        """data字段内容（兼容 {data:{...}} 和 顶层data 两种结构）"""
        data = self.data.get("data")
        # 业务实际返回 {code:0, message:"OK", data:{accessToken,...}}
        # 当data本身就是业务对象（含accessToken/items/total等），直接返回
        return data

    @property
    def total(self) -> int:
        """列表总条数"""
        if isinstance(self.body, dict):
            return self.body.get("total", 0)
        return 0

    @property
    def items(self) -> list:
        """列表数据"""
        if isinstance(self.body, dict):
            return self.body.get("items", self.body.get("list", []))
        if isinstance(self.body, list):
            return self.body
        return []

    @property
    def is_success(self) -> bool:
        """是否成功（兼容业务码 0 或 200 两种约定）"""
        return self.status_code == 200 and self.code in (0, 200)

    @property
    def is_auth_error(self) -> bool:
        """是否认证错误"""
        return self.status_code in (401, 403)

    def assert_success(self, msg: str = ""):
        """断言成功"""
        assert self.is_success, f"API失败: {msg} | Status={self.status_code} Code={self.code} Msg={self.msg}"
        return self

    def assert_status(self, code: int, msg: str = ""):
        """断言HTTP状态码"""
        assert self.status_code == code, f"状态码不符: {msg} | Expected={code} Actual={self.status_code}"
        return self

    def assert_code(self, code: int, msg: str = ""):
        """断言业务码"""
        assert self.code == code, f"业务码不符: {msg} | Expected={code} Actual={self.code}"
        return self

    def assert_has_data(self, msg: str = ""):
        """断言有数据"""
        assert self.body is not None, f"数据为空: {msg}"
        return self

    def assert_msg_contains(self, keyword: str, msg: str = ""):
        """断言消息包含关键词"""
        assert keyword in str(self.msg), f"消息不含'{keyword}': {msg} | Msg={self.msg}"
        return self
