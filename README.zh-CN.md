# DialKit for Android

[![JitPack](https://jitpack.io/v/lixiaolin94/dialkit-android.svg)](https://jitpack.io/#lixiaolin94/dialkit-android)

[English](README.md)

给 Android Demo 用的实时调参面板，参照 Josh Puckett 的 [DialKit](https://www.dialkit.dev/)：用 Kotlin 声明几个调参项，就能在真机上拖动、切换、取值，界面随之实时更新。用原生 Android View 实现。

> 这是非官方的独立移植，与 Josh Puckett 及 DialKit 项目无关，也未经其认可；详见[与 DialKit 的关系](#与-dialkit-的关系)。

- **零运行时依赖**：库里只有 Kotlin 代码和资源，R8 后约 92 KB dex；不带 AndroidX、Compose 和协程。
- **不需要悬浮窗权限**：面板挂在每个 Activity 自己的窗口里，浮在内容上方，不抢宿主的触摸和手势。
- **一行接入**：只在 debuggable 包里自动安装；release 包换成 `dialkit-noop`，API 相同，返回声明时的默认值。
- **View 和 Compose 都能用**：Compose 宿主再加一个很小的 `dialkit-compose`，读到哪个值，就只在那个值变化时重组。
- **和网页版 DialKit 互通**：值、版本和 Copy 输出都用 DialKit 的 JSON，网页原型和 Android Demo 之间可以双向搬运参数。

## 目录

- [快速上手](#快速上手)
- [接入](#接入)
- [控件一览](#控件一览)
- [读取参数](#读取参数)
- [使用面板](#使用面板)
- [调好之后落回代码](#调好之后落回代码)
- [用代码控制面板](#用代码控制面板)
- [页面内面板](#页面内面板)
- [配置](#配置)
- [常见问题](#常见问题)
- [模块、示例与文档](#模块示例与文档)
- [与 DialKit 的关系](#与-dialkit-的关系)

## 快速上手

1. 加上依赖（见[接入](#接入)）。
2. 声明要调的参数，并读取它：

   ```kotlin
   class CardDials : DialGroup() {
       val radius by slider(24f, 0f..64f)
       val tint by color("#a78bfa")
   }

   // Compose
   val dials = rememberDialKit("Card") { CardDials() }
   Box(Modifier.size(120.dp).clip(RoundedCornerShape(dials.radius.dp)).background(Color(dials.tint)))

   // View，在 Activity 里
   val dials = DialKit.panel(this, "Card") { CardDials() }
   dials.observe(this) { shape.cornerRadius = it.radius * density; shape.setColor(it.tint) }
   ```

3. 跑一个 **debug** 包，在屏幕任意位置**三指双击**。会出现一个圆形入口，点它打开面板，拖动参数，卡片会跟着变。

接入就这么多：不用写 `Application`，不用申请权限，发 release 时也不用删任何东西。

## 接入

DialKit for Android 发布在 [JitPack](https://jitpack.io/#lixiaolin94/dialkit-android)。先在 `settings.gradle.kts` 里加上仓库：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

再在 app 模块里加依赖：

```kotlin
dependencies {
    debugImplementation("com.github.lixiaolin94.dialkit-android:dialkit:0.0.1")
    releaseImplementation("com.github.lixiaolin94.dialkit-android:dialkit-noop:0.0.1")
    // 仅 Compose 宿主需要：
    implementation("com.github.lixiaolin94.dialkit-android:dialkit-compose:0.0.1")
}
```

- `dialkit` 是真正的面板，应用是 debuggable 时自动安装。
- `dialkit-noop` 的 API 完全相同但没有 UI。release 包里每个参数都返回声明时的默认值，代码不用改就能编译运行。
- 某个版本第一次被请求时，JitPack 会现场构建，可能要等几分钟。

要求：minSdk 23、compileSdk 33 及以上、Kotlin 2.0 及以上；`dialkit-compose` 另需 Compose 1.7（BOM 2024.12.01）及以上。

## 控件一览

调参项是 `DialGroup` 的属性。属性名就是 key；标签默认是它的 Title Case 形式，也可以传 `label = "…"` 自定义。

| 控件 | 声明 | 读出来是 |
|---|---|---|
| 滑杆 | `slider(24f, 0f..64f)`，可选 `step = 2f` | `Float` |
| 滑杆（自动推断范围） | `slider(1.2f)`：范围和步长按默认值推断，与 DialKit 一致 | `Float` |
| 整数滑杆 | `slider(3, 1..6)` | `Int` |
| 开关 | `toggle(true)` | `Boolean` |
| 下拉选择 | `select("grid", "list", default = "list")` | `String` |
| 带标签的下拉 | `selectOptions(listOf(DialOption("start", "Left"), DialOption("end", "Right")))` | `String`（取 value） |
| 文本 | `text("Hello", placeholder = "Title")` | `String` |
| 颜色 | `color("#a78bfa")`；也支持 `oklch(…)`、`rgb(…)`、`hsl(…)`、`color(display-p3 …)` 或 ARGB `Int` | `@ColorInt Int`（sRGB） |
| 图片 | `image("android.resource://com.example/drawable/hero", "https://…")`，或带标签的 `imageOptions(…)` | URI `String`，没选时为 `""` |
| 二维 Pad | `pad()`，或 `pad(x = DialAxis(0f, -1f, 1f), y = DialAxis(…), xLabel = "H", yLabel = "V")` | `DialPoint(x, y)`，y 向上 |
| 时间弹簧 | `spring(visualDuration = 0.3f, bounce = 0.2f)` | `DialTransition` |
| 物理弹簧 | `physicsSpring(stiffness = 200f, damping = 25f, mass = 1f)` | `DialTransition` |
| 缓动曲线 | `easing(duration = 0.4f, ease = floatArrayOf(0.42f, 0f, 0.58f, 1f))` | `DialTransition` |
| 文件夹 | `folder(collapsed = true) { ShadowDials() }` | 子分组 |
| 按钮 | `action("Shuffle") { … }` | `DialAction` |

完整一点的例子：

```kotlin
class CardDials : DialGroup() {
    val radius by slider(24f, 0f..64f)
    val columns by slider(3, 1..6)
    val elevated by toggle(true)
    val layout by select("grid", "list")
    val tint by color("#a78bfa")
    val press by spring(visualDuration = 0.3f, bounce = 0.2f)
    val shadow by folder(collapsed = true) { ShadowDials() }
    val reset by action("Reset") { resetValues() }
}

class ShadowDials : DialGroup() {
    val blur by slider(8f, 0f..40f)       // 路径是 "shadow.blur"
}
```

图片的值是 Android 能打开的 URI：`android.resource://`、`content://`、`file://`、`file:///android_asset/`、`http(s)://`（需要 INTERNET 权限）或 `data:`。面板里还能通过系统照片选择器上传图片，图片会被复制进应用私有目录，读出来是 `file://…`。用 `ImageView.setImageURI(Uri.parse(value))` 或任意图片库加载即可。

## 读取参数

**Compose**：在 composable 里读哪个参数，就只订阅哪个参数：

```kotlin
val dials = rememberDialKit("Card") { CardDials() }
Box(Modifier.clip(RoundedCornerShape(dials.radius.dp)).background(Color(dials.tint)))
val scale by animateFloatAsState(if (pressed) 0.9f else 1f, dials.press.toAnimationSpec())
```

composable 在屏幕上时面板就注册着，离开时自动移除。

**View**：任何 `android.app.Activity` 都行，用不用 AppCompat 都可以：

```kotlin
val dials = DialKit.panel(this, "Card") { CardDials() }
dials.observe(this) { d ->
    card.alpha = if (d.elevated) 1f else 0.6f
    shape.cornerRadius = d.radius * resources.displayMetrics.density
}
```

`observe` 会先立刻回调一次，之后每次变化再回调（同一帧内的多次变化合并成一次），Activity 销毁时自动停止。Fragment 里把 `this` 换成 `viewLifecycleOwner.lifecycle`。也可以随时直接读 `dials.radius`。

**在 View 里用曲线**：缓动曲线提供 `toInterpolator()` 和 `durationMillis`；弹簧提供 `stiffness` 和 `dampingRatio`，可以直接交给 `SpringForce`。

## 使用面板

| 操作 | 方法 |
|---|---|
| 显示 / 隐藏入口 | 任意位置三指双击；外接键盘按 Ctrl+Shift+D；调用 `DialKit.toggleEntry()`；或 `adb shell am broadcast -a <applicationId>.dialkit.TOGGLE -p <applicationId>` |
| 展开 / 收起面板 | 点入口；点面板外面或按返回键收起 |
| 改值 | 在滑杆整行上任意位置拖动，或点一下跳到该处；点下拉、颜色、图片行打开对应的选择器 |
| 输入精确数值 | 长按滑杆，输入后点完成（返回键取消） |
| 移动 / 调高 | 拖标题栏移动；拖面板下方的抓手调高，双击抓手恢复默认高度 |
| 版本 | 工具栏左侧的菜单："Version 1" 是基础值，"New version" 把当前值另存为新版本 |
| Copy | 把参数复制成 DialKit JSON，外加 Kotlin 声明；同时输出到 `adb logcat -s DialKit` |
| Import | 从剪贴板或文件粘贴 DialKit JSON，导入的值会存成一个新版本。adb 方式：在上面的广播后加 `--es cmd import --es json '<JSON>'` |
| 其他 adb 命令 | 在上面的广播后加 `--es cmd show`、`hide`、`open` 或 `close`。adb 命令只在 debuggable 包里存在 |
| 在电脑上读出当前值 | 在上面的广播后加 `--es cmd dump`，再运行 `adb logcat -d -v raw -s DialKit`：内容和 Copy 一样，包含所有已注册的面板（加 `--es id <id>` 只输出一个） |

一个页面上注册了多个分组时，面板里每个分组是一个分区。入口会记住自己是否显示，重启应用后保持原状。

## 调好之后落回代码

常规流程：

1. 在真机上调到满意为止。
2. 点 **Copy**。剪贴板里（以及 `adb logcat -s DialKit`）会有 DialKit JSON，后面跟着用新默认值写好的 Kotlin 声明，例如 `val radius by slider(32f, 0f..64f, step = 1f)`。
   不想碰手机的话，在 adb 广播后加 `--es cmd dump`，同样的文本会输出到 logcat，编码 agent 也能直接读到这些值。
3. 把这些声明粘贴到你的 `DialGroup` 里，覆盖原来的写法。调好的值就成了默认值，release 包（`dialkit-noop`）出来的效果和你调的一模一样。

这份 JSON 和网页版 DialKit 的格式一致：可以粘贴进网页原型，也可以把网页版导出的 JSON 在真机上 Import 进来。

参数值在整个进程内都会保留，旋转屏幕、切换深色模式都不会丢。想在重启应用后也保留值和版本：

```kotlin
val dials = rememberDialKit("Card", persist = DialPersist()) { CardDials() }
// DialPersist(key = "card", presets = false)：指定存储 key，或只保留值、不保留版本。
```

## 用代码控制面板

```kotlin
dials.setValues("radius" to 32, "tint" to "#f472b6", "shadow" to mapOf("blur" to 12))  // 一次更新多个值
dials.resetValues()                // 恢复声明时的默认值
dials.setOpen(true)                // 打开这个面板；dials.getOpen() 读取状态
DialKit.showEntry(); DialKit.open(); DialKit.close(); DialKit.hideEntry()
```

按钮被点击时，除了执行自己的回调，还会通知面板的 `onAction`：

```kotlin
val dials = rememberDialKit("Card", onAction = { path -> if (path == "reset") log("reset") }) { CardDials() }
```

`id` 相同的面板共享同一组值，适合多个页面调同一个组件的场景。

## 页面内面板

有些应用会把调参做成一个独立的页面（一个 Activity 或 Fragment），而不是挂一个悬浮按钮。用 `DialPanelView`（View）或 `DialKitPanel`（Compose）把一个已注册的分组放进页面：

```kotlin
// View：在 Activity 里，或 Fragment 的 onViewCreated 里
val dials = DialKit.panel(this, "Card") { CardDials() }
findViewById<DialPanelView>(R.id.tuning).group = dials      // 或 DialPanelView(context).apply { group = dials }

// Compose
val dials = rememberDialKit("Card") { CardDials() }
Column(Modifier.verticalScroll(rememberScrollState())) {
    Preview(dials)
    DialKitPanel(dials, Modifier.fillMaxWidth())
}
```

- 显示分组名、工具栏（版本、Import、Copy）和控件，宽度占满，用面板底色平铺，没有描边、阴影和关闭按钮。
- 高度设为 wrap 时随控件变高，由页面负责滚动；设为固定高度或 `match_parent` 时，标题和工具栏固定，控件在内部滚动。
- 下拉、取色器等弹层浮在页面之上，返回键先关弹层。
- 这个视图在屏幕上时，它的分组不再出现在悬浮面板里（其他分组照常）；`getOpen()` 恒为 `true`，`setOpen()` 不起作用。
- 依赖怎么选，看这个页面是干什么的：
  - **只是开发调试工具**，不能带进正式包：保持 `debugImplementation(dialkit)` / `releaseImplementation(dialkit-noop)`。release 包里这个视图什么都不显示，参数都返回默认值。
  - **App 的正式功能**，比如内部版或演示版里的调参页：所有构建类型都用 `implementation("…:dialkit")`。页面内面板在任何构建类型下都能用；悬浮层仍然只在 debuggable 包里自动安装，加上 `floatingPanel = false` 后连那里也不会出现。
- 如果应用只在自己的页面里调参，可以用 `DialKit.configure { floatingPanel = false }` 彻底关掉悬浮面板（见 [`samples/embedded`](samples/embedded)）：不会往 Activity 里注入任何东西，手势、快捷键和 adb `toggle` 都不起作用；adb `dump` 和 `import` 在 debuggable 包里照常可用（adb 命令只在 debuggable 包里存在）。
- 放在 `LazyColumn` 里时，滚得足够远的项会被销毁，分组会暂时回到悬浮面板里，滚回来后再离开；想避免这种情况就用普通的可滚动 `Column`。

## 配置

全部可选。在 `Application.onCreate` 或启动 Activity 的 `onCreate` 里调用，要在它 resume 之前：

```kotlin
DialKit.configure {
    entryVisibleByDefault = true                            // 首次启动就显示入口，不用手势
    entryButton = false                                     // 不显示入口按钮：用 DialKit.open() / setOpen(true) 打开
    floatingPanel = false                                   // 完全不要悬浮面板，只用页面内面板
    toggleGesture = ToggleGesture.ThreeFingerLongPress()    // 或 ToggleGesture.None
    keyShortcut = null                                      // 关闭 Ctrl+Shift+D
    entryPosition = DialPosition.BottomEnd                  // 入口首次出现的位置（默认 TopEnd）
    defaultOpen = true                                      // 入口可见时，启动就展开面板
    theme = DialTheme.Dark                                  // 默认跟随系统
    haptics = false                                         // 面板内的触感反馈，仅 Android 14+
    onOpenChange = { open -> /* 面板展开或收起 */ }
}
```

颜色来自名为 `dialkit_*` 的资源（见 [`dialkit_colors.xml`](dialkit/src/main/res/values/dialkit_colors.xml) 及其 `values-night` 版本），在宿主里重新定义同名资源即可换肤。

想手动安装：用 `tools:node="remove"` 从合并后的清单里去掉 `io.github.lixiaolin94.dialkit.DialKitInitProvider`，再调用 `DialKit.install(application)`。

## 常见问题

| 问题 | 排查 |
|---|---|
| 什么都没出现 | 入口默认是隐藏的：三指双击，或者设置 `entryVisibleByDefault = true`。悬浮面板只在用了 `dialkit`（不是 `dialkit-noop`）的 debuggable 包里存在，设置了 `floatingPanel = false` 时也不会出现。 |
| 三指双击没反应 | 有些 ROM 把三指手势占给了截屏。改用 Ctrl+Shift+D、adb 广播、`DialKit.showEntry()`，或 `ToggleGesture.ThreeFingerLongPress()`。 |
| 面板显示 "No dials registered on this screen" | 当前没有注册任何分组：在要调的页面上调用 `rememberDialKit` 或 `DialKit.panel`。 |
| `Could not find com.github.lixiaolin94…` | 在 `dependencyResolutionManagement.repositories` 里加上 `maven("https://jitpack.io")`。 |
| 重启应用后参数恢复了默认 | 这是默认行为；需要保留就传 `persist = DialPersist()`。 |
| release 包里没有面板 | 正常：release 用的是 `dialkit-noop`，参数返回默认值。 |
| 创建分组时抛 `IllegalArgumentException` | 某个声明的参数不成立：滑杆需要有限数、`min < max`、步长大于 0；缓动需要时长 ≥ 0、x1 和 x2 在 0..1 之间；弹簧需要正的时长，或者刚度和质量大于 0、阻尼 ≥ 0。 |

## 模块、示例与文档

| 构件 | 内容 | 运行时依赖 |
|---|---|---|
| `dialkit` | DSL、浮层、手势、全部 UI、持久化 | kotlin-stdlib |
| `dialkit-noop` | 相同的 API，不含 UI，给 release 包用 | kotlin-stdlib |
| `dialkit-compose` | `rememberDialKit`、`toAnimationSpec()`、`DialKitPanel`、按值重组 | Compose runtime、animation-core、ui |

| 示例 | 内容 |
|---|---|
| [`samples/catalog`](samples/catalog) | 全部控件与面板功能，一屏一项，包括 Compose 页面和 View 页面里的页面内面板 |
| [`samples/embedded`](samples/embedded) | 完全没有悬浮面板的 App（`floatingPanel = false`），调参页是正式功能、release 包里也保留：所有构建类型都依赖完整的 `dialkit` |
| [`samples/compose`](samples/compose) | Compose 宿主，单个综合面板 |
| [`samples/views`](samples/views) | 普通 `android.app.Activity`，用 View 方式监听 |

安装 catalog：`./gradlew :samples:catalog:installDebug`。

- [docs/DESIGN.md](docs/DESIGN.md)：架构，以及每个数值的来源（DialKit 原值、Android 换算或本项目设计）。
- [docs/WEB_PARITY.md](docs/WEB_PARITY.md)：与网页版 DialKit 的功能对照，以及跟进网页版更新的原则。
- [CONTRIBUTING.md](CONTRIBUTING.md)：构建与测试。[docs/RELEASING.md](docs/RELEASING.md)：发布新版本（英文）。

## 与 DialKit 的关系

[DialKit](https://www.dialkit.dev/) 是 Josh Puckett 开发的 React 调参面板，以 MIT 许可证发布。DialKit for Android 是另一位作者的独立项目：一个非官方的原生 Android 移植，与 Josh Puckett 无关，也未经其认可。

- **移植自 DialKit 2.0.2**：数据模型与配置规则、值格式与 JSON（参数值、版本、Copy）、数学计算（弹簧、缓动、颜色、Pad 吸附）、图标和视觉规格。这些部分在 Android 允许的范围内尽量忠实于 DialKit 源码。
- **适配移植**：inline 模式改为按分组的页面内面板（`DialPanelView` / `DialKitPanel`），不在悬浮和页面之间切换。
- **Android 自有**：宿主窗口内的浮层、入口按钮和全部手势、触感反馈、Import、输入法处理、无障碍、View 与 Compose 的 API，以及 `dialkit-noop`。
- **没有移植**：`productionEnabled`、键盘快捷键、Shift 锁轴和 Timeline；每一项的理由以及跟进 DialKit 新版本的原则见 [docs/WEB_PARITY.md](docs/WEB_PARITY.md)。

DialKit 的版权声明随每个构件一起分发（`META-INF/<模块名>-NOTICES.txt`），也写在 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 里。本仓库不包含 DialKit 自身的源码；移植对照的是 DialKit 2.0.2（[npm](https://www.npmjs.com/package/dialkit/v/2.0.2)、[源码](https://github.com/joshpuckett/dialkit)）。标题栏拖拽点阵的比例以 [Leva](https://github.com/pmndrs/leva)（MIT）为起点，没有包含 Leva 的代码。

这个移植版的问题请提到[本仓库的 issues](https://github.com/lixiaolin94/dialkit-android/issues)，不要提给 DialKit。

## 许可证

[MIT](LICENSE)
