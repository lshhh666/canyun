# Task 3 报告：餐云品牌外壳、设计令牌和主导航

## RED

先按 brief 新增 `tests/miniapp/brand-shell.test.cjs`，未写实现时运行：

```text
node --test tests/miniapp/brand-shell.test.cjs
tests 3
pass 0
fail 3
```

三个失败分别证明：品牌 Logo 缺失、`app-tabbar` 缺失、manifest/pages 仍使用旧品牌配置。

## GREEN

实现后聚焦测试：

```text
node --test tests/miniapp/brand-shell.test.cjs
tests 3
pass 3
fail 0
```

最终全量小程序测试：

```text
node --test tests/miniapp/*.test.cjs
tests 12
pass 12
fail 0
```

附加校验：

```text
pages.json valid
page paths preserved: 11
website logo SHA-256: 8b06b58ad7a2cefb07a6132fc39cfb6bcdf0a0a41294514cf3e8bf293a9293f9
miniapp logo SHA-256: 8b06b58ad7a2cefb07a6132fc39cfb6bcdf0a0a41294514cf3e8bf293a9293f9
git diff --check: PASS
```

## 文件

新增：

- `xiaochengxu-source/static/brand/cloudmeal-logo.png`
- `xiaochengxu-source/styles/tokens.scss`
- `xiaochengxu-source/components/cloudmeal-header/cloudmeal-header.vue`
- `xiaochengxu-source/components/app-tabbar/app-tabbar.vue`
- `xiaochengxu-source/components/state-panel/state-panel.vue`
- `tests/miniapp/brand-shell.test.cjs`

修改：

- `xiaochengxu-source/App.vue`
- `xiaochengxu-source/uni.scss`
- `xiaochengxu-source/pages.json`
- `xiaochengxu-source/manifest.json`

`xiaochengxu-source/main.js` 已经只导入 `@/styles/common.scss`，因此保留原文件，不添加 `tokens.scss` 的 JS 全局导入。

## 自查

- Logo 与网站源文件逐字节一致，长度均为 193277 字节，SHA-256 相同。
- `mp-weixin.appid` 固定为 `wx718a307127ebbc96`，manifest/pages 已移除旧品牌名称和黄色品牌值。
- `pages.json` 已规范为严格 JSON；原有 11 个页面路径及顺序全部保留；使用自定义头部的页面继续保留 `navigationStyle: "custom"`。
- 三个公共组件分别满足 brief 的 props、事件、三目的地和 `uni.reLaunch` 路由契约。
- 每个使用 `$cm-*` 的项目 SCSS 块均在开头显式导入 `@/styles/tokens.scss`；`App.vue` 使用 `lang="scss"` 并设置全局 page 品牌底色与文字色。
- 未修改点餐页面主体或后续业务页，未升级 Vue2/HBuilderX 依赖。
- 独立代码审查结论为 APPROVED，无 Critical/Important 问题。

## 顾虑

- brief 指定的品牌测试主要是静态契约检查，未自动执行组件事件和 `reLaunch` 行为；独立审查将其列为非阻塞 Minor，已人工核对相关实现。
- 当前仓库未提供 HBuilderX/uni-app CLI 构建脚本，本任务以 Node 契约测试、严格 JSON 解析、SCSS 导入检查和代码审查完成验证。三个公共组件按范围只提供稳定接口，留待后续页面任务接入。
