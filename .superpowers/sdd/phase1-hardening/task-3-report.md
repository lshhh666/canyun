# Task 3 — AI 用户级 Redis 限流

## 实施

- 仅在用户端 `POST /user/ai/chat` 调用聊天服务前，使用登录用户 ID 限流；历史、会话关闭和确认动作不计数。
- Redis Lua 脚本在一次原子执行中递增用户计数，并在首次请求设置窗口 TTL。默认 60 秒最多 10 次，可通过 `cloudmeal.ai.rate-limit.max-requests` 和 `window-seconds` 调整。各实例使用同一 Redis 用户键。
- 达到阈值时返回现有 `BaseException`/`Result.error` 业务响应。Redis 执行失败或结果异常时拒绝请求，统一返回“AI客服暂时繁忙，请稍后再试”，不向客户端暴露 Redis 错误。
- 限流发生在 `AiChatServiceImpl` 前，没有改变其会话锁、轮次、待确认动作和持久化逻辑；拒绝的请求不创建聊天轮次，也不会调用外部模型。

## 验证

- 变更前基线：`mvn -q -pl cloudmeal-server -am '-Dtest=AiChatControllerTest,AiChatServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，退出码 0。
- 变更后聚焦测试：`mvn -q -pl cloudmeal-server -am '-Dtest=AiChatRateLimiterTest,AiChatControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，12 个测试通过，0 失败、0 错误。
- 新增测试覆盖同一用户 10/11 次阈值、不同用户隔离、60 秒窗口到期、并发请求计数、单次 Lua 原子执行与过期设置、Redis 异常/空结果安全失败，以及限流拒绝后聊天服务未被调用。
- `git diff --check` 通过。

## 限制

- Redis 行为使用线程安全模拟器和 Lua 脚本文本断言测试；本地未运行真实 Redis 集成测试。此次未运行全量测试，以控制验证耗时。
- 代码审查技能要求独立子代理审查，但当前会话未提供子代理调用入口；仅完成了本地差异复核。上线前可补真实 Redis 多实例验证。
