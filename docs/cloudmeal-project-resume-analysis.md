# 餐云全项目分析与简历取舍

分析日期：2026-09-13。目标：判断当前个人实践项目有哪些真实完成的能力，哪些最适合 Java 后端简历，以及每条如何接受追问。

后续修复更新：第 5 节记录的地址归属、报表日期、菜品状态缓存、ThreadLocal 和 JWT 日志问题已完成修复，本轮指定范围 60 项回归通过。第 5 节保留原始发现用于理解原因，当前状态以 [后端收尾修复记录](D:/canyun/docs/backend-hardening-fixes.md) 为准；这不是全项目无缺陷证明。

## 1. 结论与本次核查范围

餐云已具备作为 Java 后端实习/校招简历项目的业务广度和工程深度。推荐定位为“餐饮点餐与智能客服系统”，以交易一致性、受控 AI 客服、知识反馈闭环为重点。

这里的完成指已有可展示、可解释的项目成果，不要求个人项目接入无法申请的商业支付能力。支付采用演示流程，按既定范围说明即可。

本次覆盖后端服务和关键 SQL、管理端/小程序入口、测试源码和已有验收记录。关键链路直接读取代码核查，辅助模块以入口、服务和测试交叉核查；不是逐行安全审计或一次新的全项目运行验收。没有重新执行数据库、外部模型或页面测试，不能据此宣称当前全量回归通过。

## 2. 全项目能力地图

| 模块 | 当前实现 | 对 Java 简历的作用 |
| --- | --- | --- |
| 架构与三端 | Maven common/pojo/server 多模块，Spring Boot 单体；Vue 2 管理端、UniApp 小程序 | 说明项目形态，多模块不等同微服务 |
| 用户与身份 | 微信登录、JWT 用户/管理员身份、登录上下文；订单、AI 查询等有归属校验，但地址按 ID 操作存在缺口 | 作为业务基础，权限约束必须按具体接口说明 |
| 商品与点餐 | 分类、菜品、口味、套餐、起售停售、购物车、地址、门店营业状态 | 形成可演示业务背景，普通 CRUD 不逐条占简历 |
| 缓存 | 菜品分类查询 Redis 缓存与过期，套餐使用 Spring Cache，管理修改有失效逻辑；菜品状态修改存在缓存键错误 | 可解释缓存读写，当前不能宣称所有修改都正确失效 |
| 订单计价 | 后端查询购物车、校验地址和配送距离、BigDecimal 计价、优惠及金额快照 | 是服务端权威校验的具体例子 |
| 优惠券 | 模板草稿/发放/停用；领取库存扣减与防重；用户券资格校验、锁定、核销、释放 | 核心亮点：SQL 条件更新、唯一约束、状态机、本地事务 |
| 订单与管理 | 提交、演示支付、未支付取消、超时处理、历史/详情、催单/再来一单；管理端接单、配送等 | 核心讲用户支付/取消竞争，其余作为完整业务背景 |
| 订单补偿 | 失败记录独立提交、按到期时间小批量扫描、抢占、有限重试、超时恢复、待人工终态 | 展示对故障恢复和事务边界的理解 |
| 运营与上传 | 工作台、营业额/用户/订单统计、销量 Top 10、Excel 报表、OSS 图片上传和真实解码校验 | 可作为替换亮点，通常不优先于交易和 AI |
| 消息提醒 | WebSocket 新订单/催单提示及管理端接收 | 基础提醒功能，不包装成可靠消息系统 |
| AI 会话 | 消息持久化、历史恢复、新会话、上下文、轮次占用与超时接替 | 可作为短事务与并发控制的独立面试案例 |
| RAG 与理解 | 余弦相似度 Top-K、知识兼容过滤、一次改写重检/澄清、证据充分性判断、固定兜底 | AI 应用特色，需说明小库精确检索的规模边界 |
| AI 业务工具 | 当前用户订单状态/详情、优惠券资格与差额；会话订单选择；未支付取消二次确认 | 很强的业务工程亮点，体现模型权限与后端职责 |
| 知识运营 | 知识版本、向量任务同事务、事务外生成、失败重试、过期任务隔离、管理端状态查看 | 核心亮点：外部调用与数据库一致性 |
| 反馈闭环 | 会话评价、未解决列表、知识关联、后台复测、版本记录、人工确认、错误后继续复测 | 核心亮点：处理完成条件、幂等及实际验收 |
| 工程与验证 | JUnit/Mockito、数据库集成/并发测试、管理端 Jest、小程序 Node 测试、构建/同步脚本 | 为各条亮点提供证据，测试源码与运行结果分开 |

## 3. 最值得写的内容及证据

### 交易一致性

领取优惠券时用库存条件更新和用户—券模板联合唯一索引兜底，并在事务中插入用户券；订单提交重新计价并保存快照。支付与未支付取消依靠订单状态条件更新竞争，券核销/释放和订单变更一起提交或回滚。

证据：[领券服务](D:/canyun/houduan/sky-server/src/main/java/com/sky/service/impl/CouponServiceImpl.java:56)、[下单服务](D:/canyun/houduan/sky-server/src/main/java/com/sky/service/impl/OrderServiceImpl.java:66)、[用户券 SQL](D:/canyun/houduan/sky-server/src/main/resources/mapper/UserCouponMapper.xml:6)、[数据库约束](D:/canyun/houduan/sql/coupon.sql)。

面试价值：能明确区分前置校验、数据库竞争裁决和事务原子性，说明删掉 WHERE 条件或唯一索引会发生什么。

### 异常补偿

超时取消失败后，独立事务记录任务；执行器抢占到期任务后重试完整取消流程，任务回写携带 processing_time，超时回收后旧结果不能随意覆盖新任务。失败达到上限后转为待人工处理状态。

证据：[补偿服务](D:/canyun/houduan/sky-server/src/main/java/com/sky/service/impl/OrderCompensationServiceImpl.java:37)、[任务 SQL](D:/canyun/houduan/sky-server/src/main/resources/mapper/OrderCompensationTaskMapper.xml)、[普通扫描与补偿入口](D:/canyun/houduan/sky-server/src/main/java/com/sky/task/OrderTask.java:29)。

面试价值：事务能回滚，但不能使异常自动消失；任务可重试，不代表业务恰好执行一次。当前调度路径在取消事务结束后记录失败，REQUIRES_NEW 在此入口不必然发生外层事务挂起。

### 受控 AI 客服

明确的订单请求优先走规则路由，普通知识问题经过检索和证据判断；无命中可根据上下文澄清或改写，最多重检一次。当前用户订单和券查询通过固定 Java 服务执行，金额/状态的回答直接由 Java 生成。取消订单先生成绑定订单的动作，确认时校验身份、期限、动作和订单状态。

证据：[主编排](D:/canyun/houduan/sky-server/src/main/java/com/sky/service/impl/AiChatServiceImpl.java:141)、[确定性业务回答](D:/canyun/houduan/sky-server/src/main/java/com/sky/service/impl/AiToolCallingServiceImpl.java:59)、[取消动作确认](D:/canyun/houduan/sky-server/src/main/java/com/sky/service/impl/AiOrderCancellationServiceImpl.java:129)、[小程序 API](D:/canyun/xiaochengxu-source/pages/api/api.js:393)。

面试价值：说明规则问答、实时数据查询和写操作的不同权限，能够解释为什么不能相信模型传入的用户 ID 或“已经取消”文字。

### 会话和知识任务

会话提问、回答分别在短事务中落库，通过处理轮次防止旧调用回写；外部模型不在这两个持久化事务内。知识新版本和向量任务共同提交，旧版本更新时停用；后台通过条件抢占、有限重试、超时回收和令牌检查处理任务异常。

证据：[会话持久化](D:/canyun/houduan/sky-server/src/main/java/com/sky/service/impl/AiChatPersistenceServiceImpl.java:111)、[会话轮次 SQL](D:/canyun/houduan/sky-server/src/main/resources/mapper/AiChatSessionMapper.xml)、[知识版本事务](D:/canyun/houduan/sky-server/src/main/java/com/sky/service/impl/AiKnowledgePersistenceServiceImpl.java:59)、[向量执行器](D:/canyun/houduan/sky-server/src/main/java/com/sky/service/impl/AiKnowledgeEmbeddingServiceImpl.java:40)。

面试价值：能解释外部调用为何不占长事务、进程失效如何接替、接替后旧结果如何处理。会话轮次序号与任务时间戳令牌是两种具体实现，不混为一谈。

### 反馈闭环

同一反馈的复测提交先锁反馈行，活动任务已存在时幂等返回；关联知识、任务和反馈状态同事务保存。后台记录回答和实际使用的知识版本，只有执行成功且人工确认正确才关闭反馈，错误确认允许重新复测。

证据：[提交与确认](D:/canyun/houduan/sky-server/src/main/java/com/sky/service/impl/AiChatFeedbackReviewServiceImpl.java:105)、[复测执行](D:/canyun/houduan/sky-server/src/main/java/com/sky/service/impl/AiFeedbackRetestServiceImpl.java:77)、[真实数据库并发测试源码](D:/canyun/houduan/sky-server/src/test/java/com/sky/service/AiFeedbackRetestSubmissionIntegrationTest.java:59)、[已有验收记录](D:/canyun/docs/ai-feedback-resolution-progress.md)。

面试价值：业务“已处理”条件清楚，可说明模型执行成功和问题解决不是同一个状态，且处理状态不改写原始用户评价。

## 4. 对上一版结论的修正

此前只重点核查了反馈子模块与旧面试资料，不能称作整个项目已经全面分析。现在补入当前代码后，应作以下修正：

- README 的“AI 尚未实现”和旧手册中的“前端没回传 sessionId、工具未完成”等属于过时描述，不能继续作为当前简历依据。
- 多轮会话已接入小程序，当前代码也有超时接替和轮次校验，旧文档中相应“待实现”判断已不适用。
- 订单状态、详情、优惠券资格及取消二次确认已经形成具体受控服务，应进入项目亮点候选。
- 反馈复测只直接使用关联知识调用共用生成器，不走线上余弦检索、改写、证据判断和工具；可写“关联知识回答复测”，不可写“全链路 RAG 自动评测”。
- 原简历稿过度集中在反馈任务，多处重复参数和流程；已改成交易、补偿、受控客服、知识任务、反馈闭环五条。

## 5. 本次发现的具体代码缺陷

以下为分析时发现、后续已修复的问题，保留触发原因与原始位置供追溯。修复内容和最新测试结果见本页顶部链接；原始行号可能因修复发生偏移。

| 优先级 | 问题与影响 | 已核对证据 |
| --- | --- | --- |
| 高 | 地址查询、修改、删除和设默认地址的目标更新只按 ID，没有结合当前 userId；登录用户可请求不属于自己的地址记录 | [地址 Service](D:/canyun/houduan/sky-server/src/main/java/com/sky/service/impl/AddressBookServiceImpl.java:48)、[按 ID SQL](D:/canyun/houduan/sky-server/src/main/java/com/sky/mapper/AddressBookMapper.java:33)、[地址更新](D:/canyun/houduan/sky-server/src/main/resources/mapper/AddressBookMapper.xml:42) |
| 高 | 报表日期循环只递增 begin，未先拒绝 begin 晚于 end；倒置日期会使列表持续增长，存在资源耗尽风险 | [日期循环](D:/canyun/houduan/sky-server/src/main/java/com/sky/service/impl/ReportServiceImpl.java:188)、[报表入口](D:/canyun/houduan/sky-server/src/main/java/com/sky/controller/admin/ReportController.java) |
| 中 | 菜品状态切换前端仅传 id，后端却使用 DTO.categoryId 删除缓存，可能删除 dish_category_null，原分类缓存仍保留 | [前端参数](D:/canyun/wangye/src/api/dish.ts:82)、[删除缓存](D:/canyun/houduan/sky-server/src/main/java/com/sky/controller/admin/DishController.java:115) |
| 中 | JWT 拦截器设置 ThreadLocal 后未见请求结束清理调用；线程复用可能残留身份上下文，不能声称已完善请求隔离 | [用户拦截器](D:/canyun/houduan/sky-server/src/main/java/com/sky/interceptor/JwtTokenUserInterceptor.java:35)、[上下文清理方法](D:/canyun/houduan/sky-common/src/main/java/com/sky/context/BaseContext.java:15) |
| 中 | 管理员 JWT 校验日志记录完整 token，应移除或脱敏，避免日志中保存可用凭据 | [日志语句](D:/canyun/houduan/sky-server/src/main/java/com/sky/interceptor/JwtTokenAdminInterceptor.java:48) |

这些是静态分析结论，没有对其他用户数据发请求或执行攻击验证。修复应围绕相应接口和回归用例展开，不需要扩大成新的大型功能。

## 6. 面试需要知道的方案边界

| 边界 | 面试应如何说明 |
| --- | --- |
| 演示支付 | 个人项目按演示支付验证订单/券状态竞争，实际支付到账和退款不在本次范围 |
| 计价数据源 | 后端按购物车保存的商品单价计算，不宣称每次提交已重新获取菜品最新价格/起售状态 |
| 订单并发范围 | 完善的竞争控制集中在用户支付、未支付取消和券链路，不推广为全部管理端操作已并发安全 |
| 超时关单 | 定时扫描关闭有调度窗口，不能说到第 15 分钟就严格禁止支付 |
| 订单补偿人工状态 | 有终态和记录，不等同于已实现工单分配、告警、人工修复平台 |
| 缓存 | 有过期和失效策略，不保证并发回填时的强一致 |
| WebSocket | 基础实现使用 HashMap，订单通知调用仍在事务方法返回前，不具备可靠投递或事务消息保证 |
| 知识切换 | 旧版立即停用，新版向量生成期间存在检索空窗；保存已成功和检索已可用是两种状态 |
| 检索规模 | MySQL 保存向量，Java 扫描计算；不是专用向量库、ANN 或大规模检索性能成果 |
| 工具调用 | 固定 Java 工具与路由，不是模型可自主调用任意服务，也不能凭类名声称接入了原生 Function Calling 协议 |
| 任务令牌 | 保护数据库回写，不能撤销已发出的外部调用或保证模型只调用一次；时间戳令牌也有精度/时钟前提 |
| 测试结果 | 测试文件存在、历史报告通过、本次重新通过是三种证据，不互相替代 |

## 7. 如何筛选而不是堆满简历

普通 Java 后端岗优先交易一致性与补偿，再展示 AI 客服及知识闭环。AI 应用后端岗可先展示受控客服和知识任务，并保留一条交易亮点作为业务基础。菜单、员工、报表等通过简介说明覆盖面即可。

餐云建议占一个项目条目，正文 4～5 个重点。核心技术栈挑与贡献相关的技术，图表库、样式库、所有第三方 API 不必全列。此前使用的多种重复简历版本已收敛为一个主版本。

项目背景有既有代码与模板，个人工作范围需本人确认。推荐说明“基于既有点餐系统进行业务扩展与核心链路改造”，再用具体新增和改造证明贡献，避免无依据地写“独立从零自研全部三端”。

## 8. 验证证据的解释

当前后端保留不同日期的 Surefire 报告，不能把这些文件累计后当成本次全量运行。管理端覆盖率报告只对应它纳入的文件和当时运行范围；小程序 Node 测试包含结构断言及模拟请求，不等同微信真机端到端验证。README 中旧测试数量和旧 AI 状态均不能替代当前证据。

本次材料只使用可核对的代码事实及明确标注的既有反馈验收记录，不新增测试通过或性能结论。

## 9. 下一步材料

- 直接使用或精简 [简历项目段](D:/canyun/docs/cloudmeal-resume-project-highlights.md)。
- 按 [写作指南](D:/canyun/docs/java-resume-writing-guide.md) 补齐教育、技能、实习、荣誉和 STAR 故事。
- 最终个人简历还需真实学校/专业/毕业时间、项目时间和个人职责、掌握技能、其他经历、目标岗位 JD；这些不能从代码里代填。

本次只更新项目分析和简历材料；旧手册保留作为历史训练材料，涉及 AI 现状的段落以本次核查为准。
