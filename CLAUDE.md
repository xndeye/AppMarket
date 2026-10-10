# AppMarket

Kotlin Multiplatform（Android + Desktop）应用商店客户端，UI 使用 Compose Multiplatform + miuix（`top.yukonga.miuix.kmp`）。

## UI 设计规范：垂直间距

全局垂直间距体系（常量定义在 [MarketScaffold.kt](app/shared/src/commonMain/kotlin/com/app/market/ui/component/MarketScaffold.kt)）：

- **页面出入距 `PageVerticalPadding` = 12dp**：所有滚动页面「顶栏底部 → 内容顶部」「内容末尾 → 页面底部」的额外间距，一律引用该常量，不写字面量。
  - 出入距统一通过列表 `contentPadding` 实现（顶部 `顶栏 padding + PageVerticalPadding`、底部 `innerPadding/bottomPadding + PageVerticalPadding`），不用首 / 末 `Spacer` item 承载。
  - `bottomPadding: Dp` 参数只出现在主 Tab 页签名里（外层 Scaffold 持有悬浮底栏，需透传其高度）；二级推入页一律用自己脚手架的 `innerPadding`，签名不带 `bottomPadding`。
- **卡片 / 区块间距 = 20dp**：推荐页网格 `spacedBy`、更新页汇总卡到列表、应用详情主列表 `spacedBy` 等。
- **小标题 `SectionTitle`**（应用详情页直接用 `SmallTitle`，同一套规则）：
  - 上方 20dp，由标题自身的 `topPadding` 参数提供；标题上方的元素一律不加自己的底边距；
  - 紧贴顶栏时传 `topPadding = 0.dp`，只保留页面 12dp 出入距（详情页由列表 contentPadding 提供）；
  - 标题 → 下方内容 8dp；水平 28dp（对齐卡片内容：12dp 卡片边距 + 16dp 内边距）。
- **卡片自身不携带外部垂直边距**：垂直节奏统一由列表 / 区块的 spacing 提供，卡片带自身外边距会造成间距叠加不一致。
- **特例**（不套用上述规则）：关于页（视差布局）、推荐文章页（沉浸式）；更新 / 更新历史等页的行由 `CardSegmentContainer` 无缝拼接（`spacedBy(0)`）。

## UI 规范：通用模式

- **所有 UI 组件使用 miuix**（Card、TopAppBar、NavigationBar、SmallTitle 等）；miuix 组件内部已用 squircle 渲染圆角，直接用即可。
- **自定义形状用 squircle modifier**：手搓形状不用 `clip(RoundedCornerShape)`，改用 `top.yukonga.miuix.kmp.squircle.*`——非点击纯色背景 `squircleBackground`、需裁剪的图片 `squircleClip`、可点击元素（涟漪裁进圆角）`squircleSurface`。关于页视差特效为既有特例。
- **返回按钮统一 `MiuixIcons.Back`**。
- **滚动容器三件套**：`.scrollEndHaptic().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection)`（无折叠行为的页面可省 nestedScroll）。
- **顶栏 / 底栏毛玻璃**：`rememberBlurBackdrop()` + `blurActive` 判空 + 栏色 `if (blurActive) Color.Transparent else surface`，内容区应用 `layerBackdrop`（即脚手架回调给的 `backdropModifier`，必须挂到滚动内容或其容器上）。该模式已封装在 [MarketScaffold.kt](app/shared/src/commonMain/kotlin/com/app/market/ui/component/MarketScaffold.kt) 的各脚手架里，新页面优先复用现成脚手架而不是手搓 Scaffold。
- **宽屏适配**：以 `rememberIsWideScreen()` 为唯一判定；宽屏用可展开 `NavigationRail`（`rememberNavigationRailState()`），顶栏走 `AdaptiveTopAppBar`（宽屏固定小顶栏、窄屏大标题可折叠）。
- **多行卡片拆独立 lazy item**：LazyColumn 里禁止单 item 塞整卡多行；列表行用 `CardSegmentContainer`（isFirst/isLast 分角）拼成视觉连续的卡片，每行独立 item 以保证滚动性能。
- **长内容 Dialog**：内容 `Column` 用 `heightIn(max = …)` 限高 + 内部 `verticalScroll`，按钮作为非滚动子项固定底部（参照 App.kt 安装失败弹窗）。
- **用户反馈**：轻量操作结果用 `uiPlatform.showToast(message)`。
- **i18n**：所有用户可见字符串走 Compose Resources（`stringResource(Res.string.xxx)`），禁止硬编码；日志消息英文，代码注释中文。
- **Flow 收集**：屏幕一律 `collectAsStateWithLifecycle()`，不用 `collectAsState()`。
- **可复用组件 API**：`ui/component/*` 的可复用 composable 暴露 `modifier: Modifier = Modifier` 作为第一个可选参数，并应用到根节点。

## UI 设计规范：按钮

- 全部按钮使用 [AppButton.kt](app/shared/src/commonMain/kotlin/com/app/market/ui/component/AppButton.kt) 中的 `AppButton` / `AppTextButton` / `AppCompactButton` / `AppActionButton`，不要直接使用 miuix 的 `Button` / `TextButton`。
- 按钮文字统一经 `AppButtonText`：`body2`（14sp），超宽时自动缩字至最小 8sp 再省略号。
- 调整按钮样式只改 AppButton.kt 一处；不通过主题 `textStyles` 覆盖实现。

## 构建验证

- 快速编译检查（commonMain + desktop）：`.\gradlew.bat :app:shared:compileKotlinDesktop`
- Android 目标：`.\gradlew.bat :app:shared:compileAndroidMain`
