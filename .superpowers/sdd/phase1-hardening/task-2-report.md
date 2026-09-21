# Task 2 报告：订单号唯一性

## 修改文件

- `houduan/cloudmeal-server/src/main/java/com/cloudmeal/service/impl/OrderNumberGenerator.java`：使用 Redis 原子递增序列生成订单号。
- `houduan/cloudmeal-server/src/main/java/com/cloudmeal/service/impl/OrderServiceImpl.java`：接入生成器，订单号唯一冲突有限重试，数据访问错误转订单业务异常。
- `houduan/sql/orders_number_unique_migration.sql`：为实际使用的 `orders.number` 添加可重复执行的单列唯一索引。
- `houduan/sql/README.md`：补充迁移顺序、历史数据前置检查及 Redis 持久化要求。
- `houduan/cloudmeal-server/src/test/java/com/cloudmeal/service/OrderNumberGeneratorTest.java`、`houduan/cloudmeal-server/src/test/java/com/cloudmeal/service/OrderServiceImplSubmitTest.java`：覆盖格式、连续唯一性、Redis 故障、重复键重试和写入失败。

## 设计与兼容性

新订单号仍为 18 位纯数字，格式为 `9` 加 17 位补零的 Redis 全局序列。旧号是以年份开头的 18 位数字；新格式与其区分，同时保留管理端和小程序的字符串展示、复制与精确查询方式。AI 状态查询、详情和取消逻辑仍从订单号末尾取数字尾号，多个候选尾号重复时原有扩展尾号逻辑继续生效，无需修改接口或前端。

`StringRedisTemplate` 对固定键 `orders:number:sequence:v1` 执行原子 `INCR`，不设置过期时间。Redis 不可用、无效序号或超出 17 位上限时抛出 `OrderBusinessException`，此时尚未插入订单。`orders.number` 的数据库唯一索引是最终约束：只有该索引发生重复键错误时才重新取号，最多尝试三次；其他插入错误直接失败。下单方法保留原有 `@Transactional(rollbackFor = Exception.class)`，订单明细、锁券或购物车写入报错时抛出业务异常，由事务回滚已插入的订单。

迁移参照仓库现有 `information_schema.STATISTICS`、`PREPARE` 和 `EXECUTE` 风格；已有同名单列唯一索引则跳过。执行前必须检查历史重复值：

```sql
SELECT number, COUNT(*) FROM orders GROUP BY number HAVING COUNT(*) > 1;
```

若有重复值，须依据业务记录人工处理后再建索引。脚本不删除或覆盖历史订单；重复值未处理时 `ALTER TABLE` 会失败。先执行迁移，再部署应用。

## 验证

- 基线：`mvn -pl cloudmeal-server -am '-Dtest=OrderServiceImplSubmitTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`：17 个测试通过。
- 修改后：`mvn -q -pl cloudmeal-server -am '-Dtest=OrderNumberGeneratorTest,OrderServiceImplSubmitTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`：25 个测试通过，0 失败、0 错误。
- SQL 静态校验：核对目标表/列、单列唯一索引判断、`PREPARE`/`EXECUTE`/`DEALLOCATE`、重复执行时的 `SELECT 1` 分支及无删除/改写历史订单语句，通过。
- `git diff --check`：通过。

## 残余风险和部署条件

- 仓库未包含基础 `orders` 建表脚本，也没有目标库连接；需在目标 MySQL 库确认 `number` 列至少可容纳 18 位字符串、运行历史重复值查询并实际执行迁移。当前只做了 SQL 静态检查。
- Redis 序列键须持久保存，不可被缓存淘汰。若键丢失，数据库索引仍阻止重复订单号，但历史序列范围内的有限重试可能使新建订单暂时失败。Redis 计数可能因事务回滚而出现空号，这不影响唯一性。
- 单元测试覆盖失败分支，但没有运行真实 Redis/MySQL 端到端测试；生产部署前应在测试库执行迁移并验证唯一索引。
