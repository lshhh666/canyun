# Task 6 Report: 订单中心、详情、支付和成功页

## Status

完成。`historyOrder` 已成为带 `app-tabbar active="orders"` 的订单主入口，按当前订单 `1..5`、历史订单 `6..7` 分组；订单详情、支付和成功页保持无 tabbar 的专注任务流，并统一为 CloudMeal 蓝白浅灰视觉。

## Business Contract

- 保留 `getOrderPage`、`getOrderDetail`、`paymentOrder`、`cancelOrder`、`reminderOrder`、`repetitionOrder` 调用及状态值 `1..7`。
- 新增 `ORDER_SEGMENTS`、`filterOrdersBySegment` 与集中式合法动作映射：状态 1 继续支付（超时除外）、状态 2 催单、状态 6/7 再来一单。
- 列表分页会在当前分组没有可见订单时继续按页请求，首次命中即停，末页或空原始页也停止；已请求页和加载锁共同避免重复请求、递归与无限加载。
- 详情页保留菜品、金额、商家/骑手联系、配送与订单信息；`status.vue` 保留 `orderDetailsData`、`timeout`、`rocallTime` props，以及 `statusWord`、`paymentTime`、`handlePay`、`handleReminder`、`handleRefund` 事件契约。
- 支付继续调用 `paymentOrder` 和 `uni.requestPayment`，用 `isPaying` 防止重复提交；失败留在支付页并解除锁以便重试，成功才进入成功页。
- 成功页提供两个明确出口：“查看订单”与“返回点餐”，分别用 `uni.reLaunch` 进入 `/pages/historyOrder/historyOrder` 和 `/pages/index/index`。
- 任务页面与公共订单按钮未使用黄色、旧品牌或渐变；主按钮为 `#147EE8`，危险操作使用 `#D94B4B` 语义。

## Files

- 新增 `xiaochengxu-source/utils/order-segments.js`、`tests/miniapp/orders-ui.test.cjs`。
- 重构 `pages/historyOrder/historyOrder.vue`、`pages/details/index.vue`、`pages/details/index.js` 及详情四个子组件。
- 重构 `pages/pay/index.vue`、`pages/success/index.vue`。
- 更新 `styles/common.scss` 的订单主/危险按钮颜色，以及 `pages.json` 的支付自定义导航和成功页标题。

## TDD Evidence

- RED：首次运行 `node --test tests/miniapp/orders-ui.test.cjs` 为 0 passed / 9 failed；失败覆盖 helper 缺失、分组分页缺失、动作条件缺失、详情 `paymentTime` 事件缺失、支付未防重、失败不可重试、成功页路由不符、CloudMeal 任务页外壳缺失。
- GREEN：`node --test tests/miniapp/orders-ui.test.cjs` 为 9 passed / 0 failed。
- 回归：`node --test tests/miniapp/*.test.cjs` 为 39 passed / 0 failed。
- `git diff --check`：通过。

## Executable Behavior Coverage

- helper 对数字/字符串状态的当前与历史过滤。
- 分组第一页无可见项时继续请求、首次命中停止、无匹配时末页停止、重复调用不重复请求。
- 状态 1/2/6/7 与其他状态的合法动作，以及超时订单不再继续支付。
- 支付重复点击只发一次请求；支付成功才跳成功页；失败不误跳、解除锁且可再次发起。
- 成功页两个 `reLaunch` 出口的准确目标。
- 详情状态子组件 props 与五个既有事件的真实 `$emit` 行为。
- 订单主入口 tabbar、任务页无 tabbar、CloudMeal 页头与禁用黄色/渐变约束。

## Concerns

- `xiaochengxu-source/package.json` 没有可调用的 uni-app 构建脚本，因此未执行编译；建议合入前在微信开发者工具中检查自定义页头、固定支付栏、长菜名/长地址和不同安全区高度。
- 支付页延续现有 Vuex `orderData` 数据来源；未在本任务新增刷新支付页后重新拉取订单详情的跨任务恢复机制。
