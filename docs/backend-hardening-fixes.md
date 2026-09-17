# 后端收尾修复记录

日期：2026-09-13。范围来自项目分析发现并经用户确认的四类缺陷。

## 修复内容

### 地址归属

- 地址 Service 的列表、新增、查询、编辑、删除、设置默认全部要求当前登录身份，不信任请求中的 userId。
- 增加 `getOwnedById(id, userId)`，编辑和删除 SQL 同时匹配地址 ID 与当前用户 ID；原 `getById` Mapper 留给已有下单校验链路使用，用户地址查询入口不再调用它。
- 越权和不存在的地址统一返回“地址不存在或不可访问”。
- 普通编辑不能更改默认标志；设置默认时只更新默认标志，不接收请求附带的地址内容修改。
- 设置默认前检查归属；清除旧默认和设置目标默认同事务执行。目标更新失败时回滚旧默认清除。

实现：[地址 Service](D:/canyun/houduan/cloudmeal-server/src/main/java/com/cloudmeal/service/impl/AddressBookServiceImpl.java)、[Mapper](D:/canyun/houduan/cloudmeal-server/src/main/java/com/cloudmeal/mapper/AddressBookMapper.java)、[更新 SQL](D:/canyun/houduan/cloudmeal-server/src/main/resources/mapper/AddressBookMapper.xml)。

### 报表日期

- 营业额、用户、订单和 Top 10 查询共用日期校验。
- 在访问数据库、转换日期和建立列表之前，拒绝空日期、开始日期晚于结束日期、超过 366 天的查询；366 天包含起止日期，可覆盖完整闰年。
- 日期列表使用经过校验的有限次数循环，兼容同一天、闰日和 LocalDate.MAX 的单日边界。
- 保留现有 `Result` 业务错误响应格式，前端无需更改接口调用。

实现：[ReportServiceImpl](D:/canyun/houduan/cloudmeal-server/src/main/java/com/cloudmeal/service/impl/ReportServiceImpl.java)。

### 菜品状态与缓存

- Controller 只向 Service 传递菜品 ID 与状态；请求中的分类、价格、名称等不会借状态接口写入数据库。
- Service 校验状态只允许 0/1；事务中锁定菜品行并读取真实分类，随后仅更新状态。
- 使用 Spring 事务提交后回调删除真实分类的缓存；事务回滚时不删除缓存。
- 此次修复针对起售/停售入口，不声称整个菜单缓存已经强一致，也未增加可靠缓存失效消息机制。

实现：[DishController](D:/canyun/houduan/cloudmeal-server/src/main/java/com/cloudmeal/controller/admin/DishController.java)、[DIshServiceImpl](D:/canyun/houduan/cloudmeal-server/src/main/java/com/cloudmeal/service/impl/DIshServiceImpl.java)、[DishMapper](D:/canyun/houduan/cloudmeal-server/src/main/java/com/cloudmeal/mapper/DishMapper.java)。

### 身份上下文与 JWT 日志

- 两端 JWT 拦截器进入请求时清除残留身份，失败时清理，请求完成时清理。
- 异步交接时也清理原请求线程的身份，避免原线程回到线程池时携带旧身份。
- 删除管理员日志中的完整 JWT，仅保留员工 ID 日志。
- 这不自动向异步工作线程传播 ThreadLocal；若以后增加异步业务，应显式传递身份。

实现：[用户拦截器](D:/canyun/houduan/cloudmeal-server/src/main/java/com/cloudmeal/interceptor/JwtTokenUserInterceptor.java)、[管理员拦截器](D:/canyun/houduan/cloudmeal-server/src/main/java/com/cloudmeal/interceptor/JwtTokenAdminInterceptor.java)。

## 回归方式

新增 H2 2.3.232 测试依赖，仅为 test scope。数据库测试每个用例建立独立内存库并在结束后关闭，运行真实 MyBatis Mapper 和 Spring 事务代理，不启动完整应用，也不连接业务 MySQL、Redis、OSS 或模型。Redis 调用使用 Mock 验证时序。

新增测试：

- [BackendHardeningSqlTest](D:/canyun/houduan/cloudmeal-server/src/test/java/com/cloudmeal/service/BackendHardeningSqlTest.java)：地址越权、伪造身份、直接 Mapper 防护、默认切换、写失败回滚、菜品真实分类、字段限制、提交/回滚时序及 HTTP 原始请求方式。
- [ReportDateRangeTest](D:/canyun/houduan/cloudmeal-server/src/test/java/com/cloudmeal/service/ReportDateRangeTest.java)：四接口空值/倒置/超限拒绝、错误响应、同日/闰年/极值及日期序列。
- [JwtContextCleanupTest](D:/canyun/houduan/cloudmeal-server/src/test/java/com/cloudmeal/interceptor/JwtContextCleanupTest.java)：用户与管理员正常/异常请求清理、失效/错误签名拒绝、静态和异步分支及无 JWT 日志。

同时回归 `OrderPricingServiceImplTest`、`OrderServiceImplSubmitTest`、`OrderServiceOwnershipTest`，核对已有下单地址校验与订单链路。

从 `D:/canyun/houduan` 执行：

```powershell
mvn.cmd -o -pl cloudmeal-server -am test '-Dtest=BackendHardeningSqlTest,ReportDateRangeTest,JwtContextCleanupTest,OrderPricingServiceImplTest,OrderServiceImplSubmitTest,OrderServiceOwnershipTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.redirectTestOutputToFile=true'
```

最终运行：2026-09-13 21:14:58，BUILD SUCCESS，60 项通过、0 失败、0 错误、0 跳过。其中新增回归 33 项（SQL/接口/事务 12、日期 13、JWT 8），已有交易回归 27 项（计价 5、下单 16、订单归属 6）。

H2 验证能覆盖本次 SQL 限定和本地事务行为，不替代 MySQL 特定锁交错测试或真实页面验收；60 项是本轮指定范围，不是项目所有测试。

## 交付范围

后续页面复验已部分完成：管理员登录、工作台、报表五种范围、菜品停售后恢复启售通过；四报表共8次异常日期真实接口检查通过。地址小程序页面尚未复验，并发现外链图标字体加载失败。详见 [页面复验记录](D:/canyun/docs/page-recheck-2026-09-13.md)。以下为原修复交付时的状态。

没有数据库迁移或前端接口调整。应用使用新代码需正常重新构建和重启；本次没有重启用户正在运行的服务。

按 requesting-code-review 技能安排的独立只读代码审查已完成，本次范围无 Critical、Important 或需处理的 Minor 问题。审查覆盖归属约束、事务回滚、日期边界、提交后缓存失效及 JWT 生命周期；异步清理验证采用回调测试，并未执行完整异步业务调度。
