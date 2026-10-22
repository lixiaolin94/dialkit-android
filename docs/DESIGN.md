# dialkit-android 设计

> 一个给 Android Demo 用的实时调参面板。数据结构、控件外观和交互参考 [DialKit](https://www.dialkit.dev/)（MIT，Josh Puckett），用原生 Android View 实现。加一行依赖就能植入任意宿主 Demo，不申请悬浮窗权限，核心库除 Kotlin 标准库外没有运行时依赖。

文中的 `SOURCE` 引用都指向 DialKit 2.0.2 的源码（[npm](https://www.npmjs.com/package/dialkit/v/2.0.2)、[GitHub](https://github.com/joshpuckett/dialkit)）。本地对照时把源码还原到不进仓库的 `reference/` 目录，方法见 [WEB_PARITY.md §2.6](WEB_PARITY.md#26-每次跟进网页版的流程)。

---

## 0. 数值来源标签

文中和代码注释里的每个数值都标了来源：

| 标签 | 含义 |
|---|---|
| `SOURCE` | DialKit 2.0.2 的原值，引用格式 `styles.css:L行` 或 `src/文件:L行`；标题栏点阵引用的是 Leva 0.10.1 |
| `DESIGN` | 本项目自定的值，DialKit 里没有对应项（如移动端手势、面板调高），属于设计决策。标 `(user)` 的是作者在真机上体验后调定的 |
| `MAP` | 从 DialKit 换算到 Android 的规则，如 CSS px → dp 按 1:1 |

DialKit 里**没有**、因此本项目全部是 `DESIGN` 的：面板最小高度、手动调高（DialKit 只有 timeline 有 resize handle）、入口吸边、所有触屏手势仲裁、触感反馈、滚动区边缘渐隐。

---

## 1. 目标与约束

1. **插件化植入**：宿主加一行 `debugImplementation` 即可，Application 和 Activity 代码不用改；View 宿主和 Compose 宿主都支持。
2. **兼容**：minSdk 23，见 §2。
3. **不申请 `SYSTEM_ALERT_WINDOW`**：浮层挂在宿主自己的窗口里，见 §3。
4. **交互形态**：一个复杂手势切换"调试入口"的显隐；点入口展开为页面顶层的悬浮面板，面板可移动、可调高（有上下限）；点面板外的空白处收回成入口。
5. **数据结构对齐 DialKit**：控件类型、path 规则、版本、持久化形状、Copy 导出都与 DialKit 一致，跨端心智一致，网页版和 Android 之间可以互相导入导出参数。
6. **轻**：核心库零运行时依赖，release 包换成 `dialkit-noop`，体积几乎为零。

---

## 2. 兼容与工具链

构建工具链只影响本仓库自己怎么编译；AAR 对宿主提出的要求才决定兼容性，两者分开看。

| | 取值 |
|---|---|
| 构建工具链 | AGP 9.2.1 · Gradle 9.5.0 · Kotlin 2.2.21（取本机 Gradle 缓存里已有的版本） |
| `dialkit` / `dialkit-noop` 对宿主的要求 | minSdk 23 · `minCompileSdk` 33 · Kotlin metadata 2.0（`languageVersion`/`apiVersion` 2.0）· kotlin-stdlib 2.1.21（`kotlin.stdlib.default.dependency=false` 后显式声明）· Java 11 字节码 |
| `dialkit-compose` 额外要求 | Compose BOM 2024.12.01（Compose 1.7），以 `api(platform)` 导出 |

minSdk 取自本机 Android 项目的最低值（o-motion samples 为 23）；Compose 下限取自 appstore-demo / pomodoro-demo 等模板使用的 BOM 2024.12.01。

依赖：

- `dialkit` 运行时只依赖 kotlin-stdlib。`androidx.annotation` 和 `lifecycle-common` 是 compileOnly，只用于注解和可选的 `Lifecycle` 重载。
- JSON 用系统自带的 `org.json`，不引入序列化插件。
- 初始化用库自带的 `ContentProvider`，不依赖 androidx.startup。

---

## 3. 悬浮层：挂在宿主窗口里

### 3.1 方案选择

| 方案 | 权限 | 层级 | 动效 | 输入法 / 焦点 | 结论 |
|---|---|---|---|---|---|
| A. 往每个 Activity 的 `DecorView` 末尾注入一个全屏 Layer | 无 | 当前 Activity 内最上层；宿主的 Dialog、Popup 等独立窗口会盖住它 | 同窗口内的 RenderNode 变换，跟手无延迟 | 与宿主同窗口，天然可用 | **采用** |
| B. `WindowManager.addView` 加子窗口（token 取 Activity） | 无 | 在 Activity 之上，之后弹出的 Dialog 仍会盖住它 | 移动、调高都要 `updateViewLayout`，每帧 relayout 加 surface resize | 要手动切换 `FLAG_NOT_FOCUSABLE` | 不采用 |
| C. `TYPE_APPLICATION_OVERLAY` | 需要悬浮窗权限 | 全局 | — | — | 违反约束 |

### 3.2 实现要点

```
DialKitInitProvider（ContentProvider，仅 debuggable 包生效）
 └─ DialKit.install
     ├─ ActivityTracker（ActivityLifecycleCallbacks）
     │    onActivityResumed  → ActivityAttachment.attach()   // 幂等
     │    onActivityDestroyed→ detach()
     ├─ adb 广播接收器
     └─ DialStore（进程单例，所有值和版本都在这里，Activity 切换和重建不丢）

attach(activity):
  1. 包装 window.callback → OverlayWindowCallback   // 手势识别、快捷键、返回键
  2. decorView.addView(DialKitLayer, MATCH_PARENT)  // 末尾 = 最上层
  3. API 33+：注册 OnBackInvokedCallback（PRIORITY_OVERLAY）
```

- **挂载时机**：在 `onActivityResumed` 挂载，而不是 `onCreate`。过早调用 `getDecorView()` 会让宿主之后的 `requestWindowFeature()` 抛异常；而且 AppCompat 在 `onCreate` 里会先包一层 Window.Callback，我们晚于它包装，互不影响。DialKit 自己的 `DialKitPickerActivity` 不挂浮层。
- **触摸穿透**：`DialKitLayer` 按状态决定是否接收触摸。
  - `Hidden`：不接收。
  - `Entry`：只接收落在入口圆按钮范围内的 `DOWN`（范围由 `OverlayRoot` 每帧回写，包含按压缩放），其他位置交给宿主。
  - `Expanded`：全部接收。点到面板外就收回，这次触摸不传给宿主（与 Dialog 语义一致）。
- **Insets**：API 30 以下，兄弟 View 消费 insets 后分发就会停止，所以 Layer 不依赖分发，而是在布局时主动读 root insets，算出安全区和 IME 高度；insets 变化时让子视图重新布局。
- **返回键**：`Expanded` 时只收起面板，不退出页面。API 33+ 走 `OnBackInvokedCallback`；所有版本在 `OverlayWindowCallback.dispatchKeyEvent` 里拦截 `KEYCODE_BACK`，兼容没开 `enableOnBackInvokedCallback` 的宿主。收起是幂等的。
- **重建和旋转**：入口位置按"贴哪条边 + 在安全区高度上的比例"保存；面板高度、面板偏移用 dp 保存，重建后重新 clamp。视图挂上窗口时先按当前值同步一次，避免错过注册时机。
- **已知限制**：宿主的 `Dialog`、`BottomSheet`、`Popup` 是独立窗口，会盖住面板。

---

## 4. 切换入口

### 4.1 识别位置

在包装后的 **Window.Callback.dispatchTouchEvent** 里旁路观察，看到的是进入 View 树之前的原始事件流：

- 识别命中前：所有事件原样转发给宿主，不吞、不延迟。
- 识别命中后：给宿主补发一个 `ACTION_CANCEL`，吞掉这一串事件直到全部手指抬起。

### 4.2 默认手势：三指双击（`DESIGN`）

- 两指手势会和宿主的 pinch 冲突，而且两指 tap 抬起时会触发宿主的 click。
- 三指长按和三指下滑会被 ColorOS / MIUI 的截屏手势在系统层拦截。
- 三指连点目前没有被主流 ROM 占用；第 3 根手指一落下，宿主就收到 CANCEL，不会触发 click。

| 参数 | 值 |
|---|---|
| 三根手指的落下间隔（相对第一根） | ≤ 150ms |
| 单次 tap：全部落下到全部抬起 | ≤ 300ms |
| 每根手指的位移 | ≤ 2 × touchSlop |
| 两次 tap 的间隔：第一次全部抬起后 | ≤ 350ms 内开始第二次 |

### 4.3 其他触发方式

```kotlin
DialKit.configure {
    toggleGesture = ToggleGesture.ThreeFingerDoubleTap   // 默认；或 ThreeFingerLongPress(600) / None
    keyShortcut = KeyShortcut()                          // 默认 Ctrl+Shift+D；null 关闭
    entryVisibleByDefault = false                        // 首次安装时入口是否可见
    entryButton = true                                   // false：不显示入口按钮，由宿主自己的 UI 打开面板（见下）
    floatingPanel = true                                 // false：完全没有悬浮面板，只用页面内面板（见 §5.6）
    haptics = true                                       // 触感反馈（Android 14+），见 §8.5
    entryPosition = DialPosition.TopEnd                  // 入口首次出现的角（DialKit position）；拖动过之后以拖动位置为准
    defaultOpen = false                                  // 入口可见时启动即展开（DialKit defaultOpen；DialKit 默认 true，Android 默认 false：手机上面板会盖住大半屏）
    onOpenChange = { open -> }                           // 展开 / 收起回调（DialKit onOpenChange）
    theme = DialTheme.System
}
```

- **adb**：`adb shell am broadcast -a <applicationId>.dialkit.TOGGLE -p <applicationId> --es cmd <toggle|show|hide|open|close|import|dump>`。`import` 另带 `--es json '<JSON>'`，可选 `--es id <面板 id>`。广播接收器在 API 33+ 以 `RECEIVER_EXPORTED` 注册。
- **dump**（`DESIGN`，借鉴 dialkit-macos 的双向协议，只取"读出当前值"这一半）：把 Copy 的文本（说明句 + JSON + Kotlin 声明）写进 logcat，不碰剪贴板；不带 `--es id` 时输出所有已注册的面板。每个面板前后各有一行 `----- DialKit dump: <id> -----` / `----- end of DialKit dump: <id> -----`，脚本或编码 agent 可以用 `adb logcat -d -v raw -s DialKit` 读取。
- **不显示入口**（`entryButton = false`，`DESIGN`，借鉴 dialkit-ios 的 `showsFAB: false`）：宿主用 `DialKit.open()` 或分组的 `setOpen(true)` 打开面板，面板直接从隐藏态展开（形变仍从入口的位置长出）；收起即完全隐藏。切换手势、快捷键和 adb `toggle` 改为直接开关面板，`showEntry()` 不起作用，`entryVisibleByDefault` 被忽略、也不记显隐状态，`defaultOpen` 仍然生效。
- **代码**：`DialKit.showEntry()` / `hideEntry()` / `toggleEntry()` / `open()` / `close()`。
- 入口显隐状态写入 SharedPreferences（`io.github.lixiaolin94.dialkit.overlay`），重启后保持。
- 初始状态在第一个 Activity resume 时才确定（不是在 init provider 里），所以在 `Application.onCreate` 或启动 Activity 的 `onCreate` 里设置的启动选项都来得及；在那之前代码里显式调用过 show / hide / open 的，以调用为准。

---

## 5. 状态机与几何

```
         三指双击 / 快捷键 / adb          点击入口
 Hidden ───────────────────────▶ Entry ─────────────▶ Expanded
   ▲  ◀─────────────────────────   │  ◀─────────────  │
   │        同手势再次触发          │  点空白 / 返回键  │
   └───────────────────────────────┴──────────────────┘
                 Expanded 状态下触发手势 → 直接 Hidden
```

### 5.1 入口

| 项 | 值 | 来源 |
|---|---|---|
| 尺寸 | 48dp 圆，图标 18dp | `DESIGN (user)`：安卓最小触摸尺寸；图标按 DialKit 16/42 的比例放大。DialKit 原值 42 |
| 阴影 | 平台 elevation 10dp | `DESIGN (user)`：原生阴影，比 FAB 高一级 |
| 出现 | scale 0.5→1、alpha 0→1，spring(0.3, 0.2)；elevation 随之从 0 升起 | `DESIGN` |
| 按下 | scale 0.9，spring(0.15, 0.3) | `SOURCE` Folder.tsx whileTap |
| 边距 | 16dp | `SOURCE` styles.css L185–208 |
| 拖动 | 超过平台 touch slop（欧氏距离）开始拖，之后只计超出 slop 的部分，入口跟手 | `DESIGN`，与 Compose `detectDragGestures` 相同 |
| 松手 | 落点按速度外推 0.1s，决定吸附左边还是右边、停在哪个高度；spring(0.35, 0.15) 带初速度回位 | `DESIGN`。DialKit 的入口不吸边；手机上入口挡在中间不行 |

### 5.2 面板

| 项 | 值 | 来源 |
|---|---|---|
| 宽度 | 280dp | `SOURCE` Folder.tsx:L162 |
| 展开锚点 | 按入口圆心落在屏幕哪一半，选最近的角，与入口共用这个角 | `SOURCE` panel-drag |
| 默认高度 | `round10(宽 × 16/9)`，280dp 宽对应 500dp；内容更矮时取内容高度 | `DESIGN (user)` |
| 最大高度 | 安全区高 − 32dp − IME 高度 | `SOURCE`（innerHeight − 32）+ `DESIGN` 扣除 IME |
| 最小高度 | 160dp | `DESIGN` |
| 外观 | 圆角 14，1dp 边框，padding 10/12，glass-bg | `SOURCE` `.dialkit-panel-inner` |
| 阴影 | 平台 elevation 16dp | `DESIGN (user)` |

**展开 / 收起**：容器在入口圆和面板矩形之间做 bounds 插值，progress 走 spring(0.3, 0.1)（`DESIGN (user)`；起点借用 DialKit 文件夹高度的 spring(0.35, 0.1)，真机调到 0.3）。

- 面板内容在入口出现后提前构建，展开时如果还没测量完，先等测量完成并绘制两帧再开始形变，最多等 600ms（`DESIGN`），保证动画过程中不构建视图。
- 形变过程中每帧只做两件事：摆放容器；给预先构建好的面板设置 RenderNode 属性（缩放、平移、透明度）。
- 内容不裁切：按 `min(容器宽 / 面板宽, 容器高 / 面板高)` 等比缩放，钉在与入口共用的那个角上；透明度在进度 20%→70% 之间淡入，图标在 0→30% 之间淡出（`DESIGN (user)`）。
- 圆角 24→14、elevation 10→16 随进度过渡。

### 5.3 移动面板

- 拖标题栏移动面板，拖动规则同入口；单击标题栏收起面板。
- 越过安全区时做橡皮筋：`(1 − 1/(超出量 × 0.55 / 维度 + 1)) × 维度`（UIScrollView 式，`DESIGN`）。
- 松手后 clamp 回安全区，spring(0.35, 0.15) 带初速度回位。偏移以 dp 保存，收起再展开时恢复；拖动入口后重新按入口锚定。

### 5.4 调整高度（`DESIGN`，DialKit 没有）

- **抓手**：面板下方居中，36×4dp 的胶囊，热区 120×28dp；颜色是中性灰 `#8E8E93` 加 60% 不透明度。它画在宿主背景上，不跟随面板主题。`translationZ` 高于面板，避免被面板阴影压暗。
- **拖动**：上边缘不动，只有下边缘跟手；越界时做与 §5.3 相同的橡皮筋；松手后把高度"钉住"，spring(0.35, 0.15) 带速度回到合法范围。
- **钉住高度只是上限**：实际高度取 `min(内容高度, 钉住高度)`，内容比钉住高度短时不会在下方留空。
- **双击抓手**：取消钉住，恢复默认高度。
- **IME**：键盘弹出时最大高度随之收缩，面板底边停在键盘上方 16dp。
- **聚焦的输入框**（`DESIGN`，参照 dialkit-ios）：Text 控件或 slider 的数值输入框获得焦点时，以及键盘让面板变矮之后，如果它不在滚动区的可见范围内（上下各留 16dp），就平滑滚到滚动区中间。平台的 ScrollView 只会把它滚到边缘。

### 5.5 标题栏

- 标题行高 18dp，上方留白 10dp（`SOURCE`），下方到工具栏 12dp（`SOURCE` 为 8，`DESIGN (user)` 加 4，给标题和关闭按钮留出呼吸感）；标题、点阵、关闭按钮在这一行里垂直居中。
- 标题 15sp/600；关闭按钮 16dp，点它收回到入口（`contentDescription = "Close"`）。
- 拖拽示意点阵（`PanelView.kt` 的 `DragGrip`，参数都是其中的常量）：以 Leva 0.10.1 标题栏把手为起点（`SOURCE` leva.esm.js L1938–1970：20×10 的 SVG、viewBox 28×14、r=2，换算后点径 2.86dp、间距 8.57×7.14dp），再按作者目测调整（`DESIGN (user)`）：4 列 2 行，间距各减 2dp（6.57×5.14dp）。颜色用 text-tertiary。
- 标题的最大宽度限制在点阵左侧，过长时以省略号结尾。

---

### 5.6 页面内面板（`DialPanelView` / `DialKitPanel`）

把一个已注册的分组当作页面的一部分显示，而不是放在悬浮面板里。对应网页版的 `mode: 'inline'`，不过按分组提供，也不在浮层和页面之间切换。

| 项 | 值 | 来源 |
|---|---|---|
| 外观 | 面板底色 glass-bg，左右内边距 12dp；没有描边、圆角和阴影；宽度占满 | `SOURCE` styles.css `.dialkit-panel-inline` / `.dialkit-panel-inner` |
| 标题行 | 分组名 15sp/600，不可折叠；不显示关闭按钮和拖拽点阵；行距沿用悬浮面板（HEADER_TOP 10、TITLE_BOTTOM 12） | `SOURCE` Folder.tsx 的 inline 根标题；行距 `DESIGN (user)` |
| 工具栏 | 与悬浮面板相同（版本、Import、Copy），下方 6dp + 1dp 分隔线 + 12dp | `SOURCE` |
| 高度 | wrap 时随内容变高，由宿主滚动（网页版同样用宿主的滚动容器）；有上限时标题和工具栏固定，控件区在内部滚动（带渐隐边） | `DESIGN` |
| 弹层 | 每个窗口一个全窗口弹层层（`EmbeddedPopupHost`），按使用者计数挂到 DecorView；不依赖悬浮层，没有弹层时不接收触摸；夹在安全区与键盘以上 | `DESIGN` |
| 返回键 | 弹层打开时先关弹层：API 33+ 注册 `OnBackInvokedCallback`（PRIORITY_OVERLAY），其余靠持有焦点、在 `dispatchKeyEvent` 里处理 Back / Escape | `DESIGN` |
| 与悬浮层的关系 | 视图在屏幕上时，这个分组不出现在悬浮面板里（`DialStore.floating`）；`getOpen()` 恒为 true，`setOpen()` 不起作用 | `DESIGN (user)` |
| 构建类型 | 完整的 `dialkit` 在任何构建类型下都可用（需要时自行初始化存储）；`dialkit-noop` 的同名视图不绘制任何东西 | `DESIGN` |
| 只要页面内面板 | `floatingPanel = false`：Activity resume 时不注入浮层、不包装 Window.Callback（所以没有入口、三指手势和快捷键）；`showEntry` / `open` / adb `toggle` 等显示请求被忽略，logcat 提示一次。adb `dump` / `import` 照常 | `DESIGN` |

- 视图在 attach 时按 `group` 找到已注册的面板，并监听注册变化；`group` 没注册时显示一行提示，而不是空白。
- Compose 的 `DialKitPanel` 是对 `DialPanelView` 的 `AndroidView` 包装。在 `LazyColumn` 里滚出可见范围足够远会被销毁，分组会暂时回到悬浮面板。

---

## 6. 数据模型

### 6.1 声明

DialKit 的配置是 JS 对象，key 即控件，值的写法决定控件类型。Kotlin 版用属性委托：属性名就是 key，label 由 camelCase 自动转成 Title Case（`SOURCE` formatLabel），读出来是类型安全的值。

```kotlin
class CardDials : DialGroup() {
    var radius by slider(24f, 0f..64f)            // [24, 0, 64]
    val scale by slider(1.2f)                     // 裸数字 → 推断范围
    val columns by slider(3, 1..6)                // Int
    val elevated by toggle(true)
    val title by text("Hello", placeholder = "标题")
    val layout by select("grid", "list")          // 带标签的用 selectOptions(...)
    val tint by color("#a78bfa")                  // 读出来是 @ColorInt Int
    val press by spring(visualDuration = 0.3f, bounce = 0.2f)   // 或 physicsSpring(...)
    val fade by easing(duration = 0.4f)           // ease 默认 [0.42, 0, 0.58, 1]
    val origin by pad()                           // 读出来是 DialPoint，y 轴向上为正
    val shadow by folder(collapsed = true) { ShadowDials() }    // 子项 path 为 "shadow.blur"
    val reset by action { resetValues() }
}
```

### 6.2 状态与注册

- 每个值是一个 `DialCell`：可观察的值，带全局读写钩子。`dialkit-compose` 接管这两个钩子，把每个 cell 映射成一个 Compose 状态，所以 Compose 宿主读到哪个值，就只在那个值变化时重组。
- View 宿主用 `observe(lifecycle/activity) { }`：立即回调一次，之后同一轮主线程内的多次变化合并成一次回调；Activity 或 Lifecycle 销毁时自动解绑。
- 相同 id 的多次挂载共享同一组值（`SOURCE` refcount 语义），id 默认等于 name。Activity 重建时新实例先挂载、旧实例后卸载，计数从 2 回到 1，面板不会被旧实例的清理注销（dialkit-macos 专门修过这个问题）。
- 值在卸载后**总是**按 id 保留在进程内（`DESIGN`，**偏离 DialKit**）：DialKit 只有显式传 id 才保留，但在 Android 上，旋转、深色模式切换都会销毁界面，照搬会导致调好的值一转屏就丢。
- 面板同时列出所有已注册的 panel；只有一个时直接显示它的名字。
- **代码操作**（对应 DialKit controller）：`setValue(path, value)`；`setValues(...)` 接受完整路径或按文件夹嵌套的 Map，值可以是 `DialValue` 或普通 Kotlin 值（数字、布尔、字符串、`DialPoint`、`DialTransition`），跳过未知路径、action 和类型不符的值（`SOURCE` updateValues / flattenDialValueUpdates），观察者只收到一次变化；`getOpen()` / `setOpen()`：只有一个面板时就是整个面板的展开 / 收起，有多个时是该分组的展开状态，`setOpen(true)` 同时展开面板（`SOURCE` DialRoot subscribePanelOpen）。未注册时 `getOpen()` 为 null。

### 6.3 规则移植（全部 `SOURCE`，由单元测试固定）

**裸数字推断范围**（DialStore.ts:L1005）：

| 取值 | 范围 | step |
|---|---|---|
| v ∈ [0, 1] | 0..1 | 0.01 |
| v ≤ 10 | 0..(3v ‖ 10) | 0.1 |
| v ≤ 100 | 0..(3v ‖ 100) | 1 |
| v > 100 | 0..(3v ‖ 1000) | 10 |
| v < 0 | 3v..−3v | 1 |

- 给了范围、没给 step 时（inferStep）：range ≤ 1 → 0.01；≤ 10 → 0.1；≤ 100 → 1；否则 10。
- `roundValue` 按 step 的十进制位数精确舍入（避免 60 × 0.01f = 0.59999996）。
- **外部写入的值**（持久化、版本、Import、adb、`setValue(s)`）统一经过 `applyValue`：
  - slider、pad 按**当前**声明夹紧并按 step 取整，所以按旧的、更宽范围存下的值会落进新范围；select 不在选项里、颜色解析不了时保持原值（`SOURCE`）。
  - `DESIGN`（DialKit 没有，网页上这些值不会让页面崩溃，Android 的动画 API 会抛异常）：非有限数被忽略；曲线经 `sanitizeTransition` 校验——所有字段必须是有限数；easing 的 duration ≥ 0、四个控制值齐全，x1 / x2 夹进 [0, 1]（`PathInterpolator` 要求）；physics 弹簧 stiffness > 0、mass > 0、damping ≥ 0；time 弹簧 visualDuration > 0。不合格的曲线被忽略。Import 时这些值记为"已忽略"，不算导入成功。
  - 声明时同样检查（`DESIGN`）：slider 的默认值、范围、step 必须是有限数，min < max，step > 0；曲线默认值必须通过上面的校验。不满足就在声明处抛 `IllegalArgumentException`，而不是等到第一次拖动时崩溃（与 pad 轴、颜色已有的检查一致）。
- **颜色**：支持 #3/4/6/8 位、rgb(a)、hsl(a)、oklch、`color(display-p3 …)`；内部存 CSS 字符串，与 DialKit 一致，对外读出来是 sRGB 的 ARGB Int（广色域值先映射进 sRGB）。
- **Transition**：值存为 `{type:'spring'|'easing', …}`，模式另存在 `<path>.__mode`。三种模式各缓存一份值，切换模式时恢复该模式上次的值，不做换算。

| 模式 | 默认值 |
|---|---|
| Time | visualDuration 0.3，bounce 0.2 |
| Physics | stiffness 200，damping 25，mass 1 |
| Easing | duration 0.3，ease [1, −0.4, 0.5, 1] |

**曲线预览与动画的数值形式**（`DESIGN`）：`springProgress` 和面板动画用的 `springStep` 采用 DialKit 闭式解的数值稳定写法（参照 dialkit-macos SpringResponse.swift）：临界阻尼用相对容差 `|ζω − ω| ≤ 1e-8·ω` 判断，取代 DialKit 固定的 ζ ∈ [0.9999, 1.0001]；欠阻尼分支用 sin(φ)/φ，ωd → 0 时不发散；过阻尼的慢根取 ω²/fast，避免两个相近的数相减。参数非法时返回 0。这一段区间以外与 DialKit 的公式相差小于 1e-12（单元测试固定）。

### 6.4 在宿主里使用曲线

| DialKit 值 | View 宿主（`DialTransition`） | Compose 宿主（`toAnimationSpec()`） | 来源 |
|---|---|---|---|
| Time `{vd, bounce}` | `stiffness` = (2π/(1.2·vd))²，`dampingRatio` = clamp(1−bounce, .05, 1) | `spring(dampingRatio, stiffness)` | `SOURCE` transition-math.ts:L30，`MAP` 化为 ratio |
| Physics `{k, c, m}` | `stiffness` = k/m，`dampingRatio` = c/(2√(k·m)) | 同左 | `MAP`，等价变换（单位质量） |
| Easing `{d, [x1,y1,x2,y2]}` | `toInterpolator()`（PathInterpolator）+ `durationMillis` | `tween(d·1000, CubicBezierEasing)` | `MAP` |

`stiffness` / `dampingRatio` 可以直接传给 `SpringForce`。

### 6.5 版本与持久化（`SOURCE` 形状）

- 由 `persist = DialPersist(key, presets)` 开启（`SOURCE` DialKitPersistOptions；`storage: 'sessionStorage'` 对应 Android 默认的进程内保留，不单设）。SharedPreferences 文件 `io.github.lixiaolin94.dialkit.values`，key 默认 `dialkit:<id>`，值为与 DialKit localStorage 相同的 JSON；`presets = false` 时只写 `{version, values}`，读取时也忽略版本：
  ```json
  {"version":1,"values":{...},"baseValues":{...},
   "presets":[{"id":"preset-<ms>-<rand7>","name":"Version 2","values":{...}}],
   "activePresetId":"..."}
  ```
- 任何写入（面板、宿主代码、加载版本）都会自动保存：有激活版本时写回该版本，否则写回基础值（`SOURCE` updateValue 语义）。拖动时每帧都在写，所以磁盘写入合并成静默 250ms 后再写（`DESIGN`）。
- 新建版本命名为 "Version N+1"。
- `persist` 默认关闭，与 DialKit 一致，避免旧存档覆盖改过的代码默认值。

### 6.6 Copy 与 Import

- **Copy**：剪贴板文本沿用 DialKit 的格式（`SOURCE` copy-instruction.ts：说明句 + JSON 代码块，去掉 `.__mode`，2 空格缩进），把 "useDialKit configuration" 换成 "DialKit defaults in `<GroupClass>`"，并追加一段可以直接粘贴的 Kotlin 声明。同时按行分块输出到 logcat（tag `DialKit`）。
- **Import**（`DESIGN`，DialKit 没有）：
  - 入口：Copy 左边的次级按钮，菜单里有 "Paste from clipboard" 和 "Import from file…"；另有 adb 的 `cmd import`。
  - 解析（`DialImport.kt`）：按顺序尝试 ```json 代码块、整段文字、第一个 `{` 到最后一个 `}`。遇到 `{version, values, …}` 持久化格式，取其中的 `values`。key 可以是扁平路径或嵌套对象；slider 接受 DialKit 配置里的 `[v, min, max]` 元组，pad 接受 `[x, y]`；`.__mode` 只跟着对应的值一起导入。类型不符、不在选项里、解析不了的颜色一律忽略并报告。
  - 应用：先新建一个版本，再写入，所以导入前的值仍然可以在版本菜单里切回。结果用 Toast 提示，被忽略的 key 输出到 logcat。
  - 文件：透明的中转 Activity `DialKitPickerActivity` 调用 `ACTION_OPEN_DOCUMENT`（SAF，不需要任何权限），在后台线程读取，上限 1MB。这样不依赖宿主的 onActivityResult，也不需要 AndroidX。

---

## 7. 控件视觉规格（`SOURCE` + `MAP` px→dp/sp）

### 7.1 Token（暗色是基准，浅色覆盖；默认跟随系统 uiMode）

| Token | Dark | Light |
|---|---|---|
| surface / hover / active / subtle | 白 .05 / .10 / .11 / .06 | 黑 .04 / .08 / .10 / .06 |
| text root / section / label | #FFF / 白.7 / 白.7 | #000 / 黑.65 / 黑.6 |
| text primary / secondary / tertiary | 白 .95 / .6 / .4 | 黑 .9 / .55 / .35 |
| border / border-hover / focus-ring | 白 .1 / .15 / .6 | 黑 .1 / .15 / .55 |
| glass-bg / dropdown-bg | #212121 / #2a2a2a | #fafafa / #ffffff |
| radius / row-height / row-gap | 8 / 36 / 6 | 同左 |

- 错误色 #ef7777。
- 阴影：面板、入口、弹层都用平台 elevation 阴影，透明度由系统主题决定（`DESIGN (user)`），不用 DialKit 的 CSS 阴影三元组。
- 字体：正文用系统 sans 的 500 / 600 字重（API 28 以下退化为普通 / 粗体）；数值用系统 monospace。
- 不做 20px 的 backdrop blur：glass-bg 本身是不透明色，模糊几乎看不出来。

### 7.2 控件

| 控件 | 规格要点 |
|---|---|
| Slider | 整行就是轨道：高 36、圆角 8、底色 surface、填充 surface-active。手柄是 3×20 的竖条，left = `max(5, pct − 9)`；未激活时 scaleX .25、透明，按下时 scaleX 1、不透明度 .9；压到 label 或 value 上时淡出。label 左 10、value 右 12，13sp/500。刻度线 1×8：档数 ≤ 10 时每档一根，数量为 `trunc(档数 − 1)`，所以不整除时最后那段短档没有刻度（`SOURCE` Slider.tsx:354-356 `Array.from({ length: discreteSteps − 1 })`）；否则画十分位线，只在激活时显示。`DESIGN`：档数按十进制计算，0..0.3 步长 0.1 是 3 档、2 根刻度，而不是 JS 里 0.3 / 0.1 = 2.9999999999999996 得到的 1 根。 |
| Slider 输入数值 | `SOURCE`：数值悬停 800ms 后可点开输入，Enter 提交、Escape 取消、失焦提交；提交时 parseFloat → clamp → roundValue，无法解析时保持原值；输入框右 12、3–6ch、13/500 等宽、1px 下划线。`DESIGN`：触屏没有悬停，改为在行上静止长按（系统长按时长）打开输入框；返回键和外接键盘的 Esc 对应 Escape（API 33 以上返回手势先交给输入法收起键盘，所以编辑中键盘被收起即视为取消）；长按触发 `LONG_PRESS` 触感，这次抬手不算点按；外接键盘 Enter 在按键抬起时才打开输入框，否则输入框会把这次抬起当作完成。 |
| Slider 交互 | 绝对定位：值 = min + (x/宽)·range，拖动时按 step 取整。单击：档数 ≤ 10 吸附到最近档，否则吸附十分位（±0.03125），填充用弹簧过渡：DialKit 是物理弹簧 (300, 25, 0.8)，换算成 spring(≈0.270, ≈0.193)，统一按 0.05 取整为 spring(0.25, 0.2)（`DESIGN (user)`；点按、键盘、读屏、输入数值共用）。越界超过 32 的死区后拉伸 `8·√min((越界−32)/200, 1)`，回弹 spring(0.35, 0.15)。`DESIGN`：横向超过 slop 才开始拖动并禁止父容器拦截，竖向留给面板滚动；抬手时用 UP 的位置再更新一次。 |
| Toggle | Off/On 分段控件，不是开关轨道。行高 36，padding 2/10/2/12；分段容器 padding 2，右侧 margin −6；选中块圆角 6、surface-active，left/width 用 0.2s `cubic(.25,1,.5,1)` 过渡。 |
| Select | trigger 高 36，chevron 20（不透明度 .6，旋转 spring(0.2, 0.15)）。下拉：glass-bg、圆角 8、padding 4、距 trigger 4、视口边距 8、最大高 320；选项最小高 36、圆角 6。弹出：spring(0.15, 0)，从 y ±8、scale .95 开始；下方空间不够且上方更宽裕时翻到上方（`SOURCE` dropdown-position.ts）。 |
| Color | 行内色块 20×20、圆角 5，底下是棋盘格。弹层：宽 280、最大高 480、padding 10、圆角 14；色板高 160，滑轨高 16、滑块 16×24（向内描边）；格式 hex / oklch / p3。`DESIGN`：色板按 126×80 采样后放大（原版 252×160），保证拖 Hue 时每帧都能重绘；Hue 上限 359.9（360 重新解析会变成 0）；点 Color 行的 label 也能打开取色器。 |
| Image | `SOURCE` image-control.ts：值是图片 URL 字符串，无默认时取第一项、无选项时为空；行高 36、padding 0 6 0 12、gap 8，名称 12 号 text-secondary 右对齐省略，缩略图 38×26 圆角 5 带内描边；展开时 surface-active 加 border-hover 内环。弹层宽 320、最大高 560、padding 12、gap 10：标题 + Remove（11 号 text-tertiary，有值时才显示，点了关闭弹层）、3 列 4:3 网格（padding 3、1px 边框、圆角 11，选中为 text-label 边框加 surface-active）、空态 "Choose an image to get started."、Upload image、状态行；点选项不关弹层；上传期间再点选项会放弃上传；本次会话上传过的图会继续出现在网格里；显示名按 imageLabel（取路径最后一段，data: / blob: 为 "Uploaded image"）。`DESIGN`：缩略图用平台解码（LRU 缓存、两个后台线程），支持 `android.resource://`（经 Drawable 绘制，矢量也行）、`content://`、`file://`、`file:///android_asset/`、`http(s)`（需宿主有 INTERNET 权限）和 `data:`；上传走系统照片选择器（API 33+）或 SAF，不需要权限，校验类型、≤10MB、能解码后复制到 `filesDir/dialkit/images/<hash>/<文件名>`，值为 `file://`。DialKit 存 data URL，放进 SharedPreferences 太大，所以没有照搬。 |
| Text | `<textarea>` 语义：回车换行，高度随内容自动增长，最多 5 行；label 左、输入右对齐。 |
| Pad | 正方形，内缩 12；6 等分网格，不透明度 .65；点 12dp，阴影用径向渐变模拟 `0 2px 4px #0003`。下方 3 列：caption / x / y，数值可点击编辑。双击复位。手势仲裁见 §8.3。 |
| Spring 曲线 | 宽高比 256:140，采样 2s / 100 步，y 映射到高度的 20%–80%；网格横竖各 3 条；中线虚线 4,4；曲线描边 2。 |
| Easing 曲线 | 宽高比 256:180；曲线描边 2；参考线虚线 3,4；控制点圆点 10，触摸半径 20dp（`DESIGN`）。 |
| Transition | 曲线区、Type 分段（Easing / Time / Physics）、对应参数。切换模式时以 Type 行为锚点补偿滚动位置，Type 行在屏幕上不动（等同浏览器的 CSS scroll anchoring）。 |
| Action | padding 10/16、13sp/500、text-secondary、surface 底、圆角 8；按下 surface-active。 |
| Folder | 标题行高 36，13sp/600、letter-spacing −0.01em。chevron 展开时旋转 180°，spring(0.35, 0.15)；内容高度 0↔auto，spring(0.35, 0.1)；内容区 gap 6、padding-bottom 10。 |
| Toolbar | 高 36、gap 6：版本下拉（flex 1）、Import（surface 底）、Copy（36×36 反色，"已复制"态保持 1500ms）。版本菜单：dropdown-bg、圆角 12、项高 32、圆角 8，分隔线上下 4；删除按钮常显（不透明度 .6，触屏没有 hover，`DESIGN`）。 |
| 滚动区 | 平台 ScrollView，不显示滚动条；还有内容可滚的那一侧用平台 fading edge 渐隐 16dp（`DESIGN (user)`，DialKit 没有）。 |
| 弹层通用 | 画在浮层内部最上层（`translationZ` 64dp），不是独立窗口。卡片先整块填充，再在内侧画 1dp 边框（`SOURCE` `border: 1px solid var(--dial-border)`）。 |

**spring 记法**：spring(a, b) 两个参数是 (visualDuration, bounce)；三个参数是 (stiffness, damping, mass)，都按 §6.4 的公式换算。

---

## 8. 动画与手势的实现

全部用平台原生能力，没有动画库。

### 8.1 动画

- **弹簧**：`SpringValue` 用解析式阻尼振子（`springStep`）逐帧求位置和速度，由 `Choreographer` 驱动。任意帧间隔下结果都精确，掉帧不改变曲线；中途改目标时位置和速度连续。与 DialKit 的 `springProgress` 在 1e-6 内一致，由单元测试验证。
- **补间**：颜色过渡、取色弹层出现用平台的 `ValueAnimator`。
- **渲染**：形变、拖动、调高每帧只改容器 bounds 和 RenderNode 属性；圆角裁切用 `ViewOutlineProvider`，阴影用 elevation。

### 8.2 拖动与速度

- **速度**：`VelocityTracker` 一律喂屏幕坐标（`rawX`/`rawY`）。被拖动的 View 跟着手指走，它的局部坐标几乎不变，测不出速度。
- **起拖**：欧氏距离超过平台 touch slop 后开始拖，之后只计超出 slop 的部分（与 Compose `detectDragGestures` 相同），起拖时既不跳也不额外落后。
- **面板滚动**：平台 ScrollView。平台 VelocityTracker 不处理 ACTION_UP，"拖动、停住、再抬手"时仍会按停住前的速度甩出。抬手时如果距上一次移动超过 40ms，就在抬手位置补一个 MOVE，让追踪器判定为停止（`SOURCE` Compose `AssumePointerMoveStoppedMilliseconds` = 40，与平台 `ASSUME_POINTER_STOPPED_TIME` 相同）。

### 8.3 手势仲裁（面板内）

- **Slider**：横向超过 slop 才接管，竖向交给面板滚动。
- **Pad**（`DESIGN (user)`，方案 1 + 2）：
  - 按在点上（触摸半径 24dp）：立即开始拖动，并保留手指与点的相对偏移（`SOURCE`）。
  - 按在空白处：先不接管。180ms 内移动超过 slop，交给面板滚动；180ms 内抬手算轻点，把点放到该处，8dp 内吸附到网格交点；按住 180ms 不动则激活自由拖拽，并显示拖拽光环。
  - 两次轻点在系统双击间隔内则复位，在点上双击也算（`SOURCE`："including clicks on the point itself"）。
- **Easing 控制点**：只有落在控制点 20dp 内的按下才接管，其余交给面板滚动。
- **取色器**的色板和色条：按下即接管（它们在弹层里，弹层内容不需要滚动）。

### 8.4 层级（Z）

有 elevation 的兄弟视图会按 Z 排序绘制和分发触摸，阴影会盖在更低 Z 的视图上面：

- 弹层层：`translationZ` 64dp，在面板之上，能收到触摸。
- 抓手：`translationZ` 比面板 elevation 高 1dp；它没有背景，也就没有轮廓，自己不投影。

### 8.5 触感（`DESIGN (user)`）

只用 Android 14（API 34）的语义常量，低版本不振。`performHapticFeedback` 遵守系统的触摸反馈开关，不需要振动权限；`DialKitConfig.haptics` 可整体关闭。实现集中在 `ui/Haptics.kt`，每个语义一个函数。

| 场景 | 常量 |
|---|---|
| Slider 拖到最小 / 最大值（离开后再碰才再振） | `GESTURE_THRESHOLD_ACTIVATE` |
| Slider 拖动跨过一档，仅限总档数 ≤ 10 的 slider（与显示每档刻度的条件一致） | `SEGMENT_FREQUENT_TICK` |
| Toggle 切到 On / Off | `TOGGLE_ON` / `TOGGLE_OFF` |
| 分段选择变化（Transition 类型、取色格式） | `SEGMENT_TICK` |
| Pad 按住 180ms 进入拖拽 | `DRAG_START` |
| Pad 轻点吸附到网格交点 | `SEGMENT_FREQUENT_TICK` |
| Pad 双击复位、抓手双击恢复默认高度、Copy 成功、Import 成功 | `CONFIRM` |
| 抓手调高碰到最小 / 最大高度 | `GESTURE_THRESHOLD_ACTIVATE` |
| Copy 写剪贴板失败、Import 没有可导入的值 | `REJECT` |

只有面板内的操作才有触感；入口的拖动、吸边和三指切换都不振（`DESIGN (user)`）。不加触感的还有：普通按钮（Action、展开 / 收起、关闭、菜单项）、下拉选择、文件夹展开、点按 Slider、easing 控制点、面板拖到边缘。Pad 拖动时不吸附交点（`SOURCE`），所以拖过交点也不振。

### 8.6 无障碍与外接键盘

网页版的键盘操作（`SOURCE` control-keyboard.ts）在 Android 上对应两套入口，共用同一套步进函数（`model/ControlKeys.kt`，单元测试覆盖）：读屏软件的无障碍动作，以及外接键盘。

| 控件 | 读屏看到的 | 动作 | 外接键盘 |
|---|---|---|---|
| Slider | SeekBar，范围 + 当前值（API 30+ 用 stateDescription） | 前进 / 后退一步、设置进度（按步长取整）、"Edit value" | 左右一步，Shift 或 PageUp / PageDown 十步，Home / End，Enter 打开输入 |
| Toggle | Switch，选中态 | 点击切换 | 左右切换 |
| 分段（Transition 类型、取色格式） | 当前选项为状态 | 点击切到下一项；每个未选项一个 "Select …" | 左右循环，Home / End |
| Pad | "Origin: X …, Y …" | 上下左右移动一步、Reset | 四个方向一步，Shift 十步，Home 复位 |
| 色相 / 不透明度条 | SeekBar（百分比） | 前进 / 后退 5%（平台 SeekBar 的默认） | 左右按 DialKit 的输入步长（色相 0.1°、不透明度 0.01），PageUp / PageDown 十步 |
| 取色平面 | 不暴露（下方的 hex / oklch 输入框编辑同一个颜色） | — | 方向键 0.01，Shift 0.1（`SOURCE`） |
| 文件夹标题 | 标题 | 展开 / 收起 | Enter |
| 下拉选项、版本菜单项 | 当前项为 selected | 点击 | Enter |
| 入口 | "DialKit" | 点击展开 | Enter |
| 曲线预览、easing 编辑器 | 不暴露（参数由下方的 slider 和 Ease 输入框编辑） | — | — |

- `DESIGN`：上下方向键留给焦点导航（与平台 SeekBar 一致），所以 Slider、分段和色条只响应左右；Pad 和取色平面是二维的，四个方向都响应。
- 焦点高亮用平台默认的 focus highlight（API 26+），不另画焦点环。
- 弹层打开时 Back / Escape 先关弹层；从键盘打开弹层时焦点移进弹层。
- 形变、拖动、调高结束时发出 `TYPE_WINDOW_CONTENT_CHANGED`，读屏软件和 UI 自动化才能拿到最新结构。
- `samples/compose/src/androidTest` 的 `OverlayAccessibilityTest` 用 UiAutomation 按读屏的方式找节点、执行动作、检查状态。

---

## 9. 工程结构

```
dialkit-android/
├─ build-logic/         # 约定插件 dialkit.android.library：compileSdk / minSdk / Java 11 / Kotlin 2.0 metadata / explicit API / 资源前缀 / lint
├─ dialkit/
│  ├─ src/shared/       # 与 noop 共用：DialGroup DSL、DialCell、节点、类型、model/（数值、颜色、曲线、Pad、JSON、导入）
│  ├─ src/main/java/    # DialKit 入口、初始化、store/（DialStore、导入 Activity）、overlay/（注入、手势、返回键）、ui/（全部 View）
│  ├─ src/main/res/     # drawable/ 矢量图标，values(-night)/ 颜色 token，values/ 文案；全部以 dialkit_ 为前缀
│  └─ src/test/         # JVM 单元测试
├─ dialkit-noop/        # 同包名同 API 的空实现，共用 dialkit/src/shared
├─ dialkit-compose/     # rememberDialKit、toAnimationSpec()、DialCell → Compose 状态桥接；对核心库 compileOnly
├─ samples/compose/     # Compose 宿主：验证点击、双指缩放、滚动穿透
├─ samples/views/       # 普通 android.app.Activity + View 宿主；measure 构建类型用来测体积
└─ reference/           # 本地的 DialKit 源码（git 忽略，不进仓库）
```

- 共享源码通过 AGP 9 的新 DSL 加入：`sourceSets.getByName("main").kotlin.directories.add("src/shared/java")`。
- 主代码开启 `-Xexplicit-api=strict`；Lint 对三个库模块零告警。
- **资源**：图标是 `res/drawable/dialkit_ic_*.xml` 的 VectorDrawable（路径取自 DialKit icons.ts，文件头注明来源），由 `IconView`（ImageView）着色；颜色 token 在 `res/values/dialkit_colors.xml`（浅色）与 `res/values-night/`（深色），`DialTheme.Light / Dark` 通过配置上下文强制选择其一，宿主可以覆盖同名资源换肤；文案在 `res/values/dialkit_strings.xml`，可本地化。
- **项目内小组件**（`ui/`）：`SliderView`、`SegmentedView`、`ValueField`、`ToolbarButton`、`DialMenu`、`PressFrame`、`IconView`、`RadialShadow`，以及 `labeledRow` 等行构建函数；工具栏和弹层都由它们组合。
- **发布**：com.vanniktech.maven.publish 发布到 Maven Central（Central Portal），POM 信息在根目录与各模块的 `gradle.properties`；凭据和签名密钥只从 `~/.gradle/gradle.properties` 或环境变量读取。流程见 CONTRIBUTING.md。
- 体积（`samples/views`，纯 View 应用，release + R8，关闭混淆以便按包统计）：

| | 大小 |
|---|---|
| APK | 121 KB |
| DialKit 的 dex | 84 KB，不带任何 androidx 或协程 |
| release 包换成 noop 后的整个 APK | 42 KB |

接入方式见 [README](../README.md)。

---

## 10. 未做与已知限制

- 宿主的 Dialog、BottomSheet、Popup 是独立窗口，会盖住面板。
- 与网页版的功能差异及处理建议见 [WEB_PARITY.md](WEB_PARITY.md)。
- 数值字体用系统 monospace，没有 500 字重，笔画比 DialKit 的 Geist Mono 细。
- 没在 API 23/24 实机或模拟器上跑过；没用 AGP 8.7 + Kotlin 2.1 的宿主实际消费构建过。
