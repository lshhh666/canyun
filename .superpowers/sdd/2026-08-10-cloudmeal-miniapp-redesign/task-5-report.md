# Task 5 Report: 确认订单、地址和备注流程

## Status

完成。确认订单页按“地址 → 商品 → 备注/餐具 → 费用 → 提交”重构；地址列表、新增/编辑地址和备注页统一使用 CloudMeal 页头、蓝色主按钮和 safe-area 固定操作栏，任务页均未渲染 app-tabbar。

## Business Contract

- 保留 `order/index.js` 原有下单 API、路由、字段和值来源。
- 提交 `amount` 继续使用现有 `orderDishPrice`；固定栏“合计”也展示同一值。
- `isHandlePy` 同时控制重复提交、按钮 disabled 和 loading，并在成功或失败后解除。
- 保留地址、菜品和备注/餐具子组件的既有 props、父级事件绑定及页面方法。
- 空地址直接进入真实新增地址页；地址选择、编辑、新增、设默认、保存、删除及备注保存流程保持可执行。
- 订单、默认地址、地址列表、地址详情、保存、修改和删除请求均使用 `getErrorMessage` 展示具体错误。

## Verification

- `node --test tests/miniapp/checkout-ui.test.cjs`: 8 passed, 0 failed.
- `node --test tests/miniapp/*.test.cjs`: 27 passed, 0 failed.
- `git diff --check`: passed.
- 新增行为测试覆盖信息顺序、提交防重及失败解锁、提交金额契约、无地址路由、地址选择/编辑/新增/设默认、地址保存/删除和失败回滚、备注初始化/保存。

## Concerns

- `xiaochengxu-source/package.json` 未提供可调用的构建脚本，因此本任务未执行 uni-app 编译；建议合入前在微信开发者工具中做一次真机/模拟器视觉检查，重点查看弹层与不同安全区高度。

## Fix round 1

### Changes

- 将下单参数构造与 `submitOrderSubmit` 调用放入同一 `try/catch/finally`。`dateFormat`、Vuex accessor、门店信息读取或请求同步/异步抛错时，均使用 `getErrorMessage` 提示并解除 `isHandlePy`。
- 新增 `addressLoadState`：仅 `ready` 且服务端成功返回空列表时进入新增地址；`loading` 显示“地址加载中，请稍候”且不跳转；`error` 保留失败状态并进入地址管理页重试，不再伪造成成功空列表。
- 删除地址管理页无效的 `onShow(options)`、`formRouter` 查询读取逻辑，返回路径继续只依赖已有 `addressBackUrl`。
- 补充成功提交、同步参数构造异常、地址 loading/error/ready-empty、编辑保存失败和删除失败行为测试；原 API URL、提交 payload、props 和 emits 未改。

### TDD evidence

- RED：`node --test tests/miniapp/checkout-ui.test.cjs` 为 8 passed / 3 failed；失败点分别是同步异常未解锁、缺少 loading 状态、缺少 error 状态。
- GREEN：`node --test tests/miniapp/checkout-ui.test.cjs` 为 11 passed / 0 failed。
- 回归：`node --test tests/miniapp/*.test.cjs` 为 30 passed / 0 failed。
- `git diff --check`：通过。

### Failure coverage

- 已执行验证：下单请求失败、下单参数同步构造失败、地址列表失败、默认地址设置失败、新增地址失败、编辑地址失败、删除地址失败，以及对应的解锁、不误跳转或状态回滚行为。
- 未声称覆盖：地址详情加载失败和默认地址查询失败目前保留实现级 `getErrorMessage` 处理，但本轮没有新增对应行为测试。
