# Task 4：生产配置硬化报告

## 改动

- `application.yml` 的共享配置要求管理端和用户端 JWT 密钥由环境变量或更高优先级的外部配置提供；随机密钥回退仅放在 `dev` profile。
- 历史循环依赖兼容项仅在 `dev` profile 设置为 `true`；`prod` 使用 Spring Boot 默认关闭行为。
- README 的本地配置示例改为现用的 `cloudmeal` 前缀，补充 AI Key、敏感配置来源、开发/生产 profile 用法及随机 JWT 的限制。未接入真实微信支付。
- 新增配置加载测试，验证 `dev` 的临时 JWT 和循环依赖兼容，以及 `prod` 的外部 JWT 和关闭循环依赖。

## 验证

- 基线 `AiPropertiesTest`：1 项通过。
- 聚焦执行 `mvn -q -pl cloudmeal-server -am '-Dtest=ApplicationConfigurationTest,AiPropertiesTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`：3 项通过，0 失败、0 错误。
- 检查 `application.yml`：数据库密码、微信密钥为配置引用；AI Key、对象存储和地图密钥不在提交的配置中；JWT 仅在 `dev` 允许随机回退。README 中的密钥值均为示例占位符。
- `git diff --check`：通过。

## 边界

生产需显式启用 `prod` 并提供两个稳定且不同的 JWT 密钥和实际使用的外部服务配置。配置测试不连接 MySQL、Redis、微信或 AI 服务；未执行全量后端测试。

## Fix round 1

- `JwtProperties` 在属性绑定后拒绝缺失、空白或未解析的管理端/用户端密钥，避免把 `${CLOUDMEAL_JWT_ADMIN_SECRET}` 等占位符用作签名密钥；`dev` 未配置时仍使用启动随机密钥。
- 开发配置段改为仅在 `dev & !prod` 时启用，混合激活 `dev,prod` 不再继承循环依赖开关或随机 JWT 回退。
- 配置测试实际创建并绑定 `JwtProperties`，覆盖生产缺失任一密钥、空白、未解析占位符，以及混合 profile 的成功和失败路径；测试环境移除宿主系统环境变量来源，不连接外部服务。
- 聚焦执行 `mvn -q -pl cloudmeal-server -am '-Dtest=ApplicationConfigurationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`：5 项通过，0 失败、0 错误；未执行全量测试。
