# 安全测试（ZAP baseline）

唯一工具：OWASP ZAP（DAST 动态扫描）。不引入其它扫描器堆叠。

## 运行方式（本机或 CI 需 Docker；生产环境扫描须经授权）

```bash
# 被动基线扫描：爬虫 + 被动规则，不做主动攻击，适合测试环境常态化
docker run -t --rm \
  -v "$PWD/reports/zap:/zap/wrk:rw" \
  ghcr.io/zaproxy/zaproxy:stable \
  zap-baseline.py -t http://dms-dev.mysolmed.com/dms/ \
  -r zap-baseline-report.html -J zap-baseline.json -I
```

认证后接口扫描（发版前/安全评审手动）：用有效 token 走 ZAP 上下文，
Authorization 头注入，见 https://www.zaproxy.org/docs/authentication/ 。

## 关注点（结合 DMS 多租户 / 金额业务）
- 多租户越权：A 租户 token 访问 B 租户 dealerId/orderId（IDOR）
- 金额/数量篡改：下单/审批接口改单价、折扣、代金券面值
- 注入：MyBatis 拼接处 SQL 注入、XSS（列表筛选/备注字段）
- 认证：过期/伪造 token、越权调用审批/状态变更接口

报告输出到 `reports/zap/`，纳入发版检查；高危项必须清零。

