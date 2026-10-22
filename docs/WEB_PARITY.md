# 与网页版 DialKit 的对应关系与移植原则

对照基准：DialKit 2.0.2（[npm](https://www.npmjs.com/package/dialkit/v/2.0.2)、[GitHub](https://github.com/joshpuckett/dialkit)）。数值来源标签的含义见 [DESIGN.md §0](DESIGN.md#0-数值来源标签)。

---

## 1. 功能对照

### 1.1 已对齐

| 网页版 | Android | 说明 |
|---|---|---|
| 配置写法：裸数字、`[v, min, max, step]`、嵌套对象即文件夹、`_collapsed` | `DialGroup` 属性委托：`slider` / `folder(collapsed)` 等 | 写法换成 Kotlin DSL，path 规则、label 生成、范围与步长推断全部一致（`SOURCE`，单元测试覆盖） |
| 控件：slider、toggle、transition（spring time / physics、easing）、folder、action、select、color、image、text、pad | 全部 | 网页版的 `spring` 类型在解析时也会转成 transition 控件，两边一致 |
| Slider：绝对定位、点按吸附、橡皮筋、刻度线、拖压标签时淡出 | 一致 | |
| Transition：三种模式各自缓存、`.__mode`、曲线预览与 easing 编辑器 | 一致 | |
| Color：hex / oklch / display-p3，色板 + Hue + Opacity | 一致 | |
| Pad：网格吸附、拖点保持偏移、双击复位 | 一致 | |
| Text：`<textarea>` 自动增高 | 一致（最多 5 行） | |
| 多个 panel 同屏，按 id 共享、引用计数 | 一致 | |
| 版本（presets）：Version 1 为基础值、新建、切换、删除、自动写回 | 一致 | |
| 持久化：`dialkit:<id>`，`{version, values, baseValues, presets, activePresetId}` | 一致（SharedPreferences） | JSON 与网页版 localStorage 互通 |
| Copy：说明句 + JSON 代码块 | 一致，另附 Kotlin 声明 | |
| Controller：`setValue`、`setValues`、`resetValues`、`getValues`、`getOpen` / `setOpen`，`onAction` | `setValue()`、`setValues()`、`resetValues()`、`values()`、`getOpen()` / `setOpen()`，`onAction` | `setValues` 接受嵌套 Map 和普通 Kotlin 值 |
| `persist`：布尔或 `{ key, presets }` | `DialPersist(key, presets)` | `storage: 'sessionStorage'` 对应 Android 默认的进程内保留 |
| `DialRoot`：`position`、`defaultOpen`、`onOpenChange` | `DialKitConfig.entryPosition` / `defaultOpen` / `onOpenChange` | `defaultOpen` 在 Android 默认 false（`DESIGN`） |
| Slider 数值输入（悬停 800ms 后点开） | 静止长按打开输入框 | 提交规则相同；返回键对应 Escape |
| 键盘操作（Slider、分段、Pad、取色器、下拉） | 无障碍动作 + 外接键盘 | 见 DESIGN.md §8.6；上下键留给焦点导航 |
| Image 控件（预设图片 + 上传） | `image(...)` / `imageOptions(...)` | 上传走系统照片选择器，复制进应用私有目录，值为 `file://`（DialKit 为 data URL）；能显示 DialKit 导出的 data URL |
| 主题 system / light / dark | `DialTheme` | |
| 面板拖动、收起为按钮、最近角展开 | 一致 | 收起态是可吸边的入口 |
| `DialRoot` 的 `mode: 'inline'`（面板嵌在页面里） | `DialPanelView` / `DialKitPanel`，按分组 | 外观同 inline（平铺、无描边阴影、宿主滚动）；不提供浮层与页面之间的切换，页面里显示的分组不再出现在悬浮面板中。见 DESIGN.md §5.6 |

### 1.2 网页版有、Android 没有

| # | 网页版功能 | 建议 | 理由 / 适配方式 |
|---|---|---|---|
| 1 | `productionEnabled` | **不移植** | Android 用"只在 debuggable 包里自动安装"加 `dialkit-noop` 解决同一个问题，更安全 |
| 2 | Pad 拖动时按 Shift 锁定一个轴 | **不移植** | 触屏没有修饰键，另造手势容易和滚动、拖动冲突 |
| 3 | 键盘快捷键（`shortcuts` 选项与快捷键菜单） | **暂不移植** | 默认依赖物理键盘和鼠标，手机上只有外接键盘才有意义 |
| 4 | Timeline（`useDialTimeline` / `DialTimeline`） | **暂不考虑** | 功能面大（时间轴编辑与播放），先保证核心面板的完整度 |
| 5 | 配置对象运行时变化时热更新面板（`updatePanel`） | **不适用** | Kotlin DSL 是静态声明，结构不会在运行时变化 |
| 6 | 悬停色、鼠标指针样式、焦点环 | **不适用** | 触屏没有对应状态 |
| 7 | 20px backdrop blur | **不移植** | glass-bg 本身是不透明色，模糊几乎看不出来，平台实现成本高 |

### 1.3 Android 独有（网页版没有，不随网页版改动）

- 浮层注入、三指双击 / 快捷键 / adb 切换入口、入口吸边与甩动。
- 面板调高抓手、键盘（IME）避让、滚动区边缘渐隐、Transition 切换模式时的滚动锚定。
- 触感反馈（Android 14+）。
- 外部写入值的校验（非有限数、非法曲线被忽略；按当前声明夹紧取整），以及声明时的参数检查。
- adb `cmd dump`：把 Copy 文本输出到 logcat（借鉴 dialkit-macos 的双向协议，只取读值这一半）。
- `entryButton = false`：不显示入口，由宿主 UI 打开面板（借鉴 dialkit-ios 的 `showsFAB: false`）。
- `floatingPanel = false`：完全不要悬浮面板，只用页面内面板。
- Import（剪贴板 / 文件 / adb），以及 Copy 附带的 Kotlin 声明。
- 进程内总是保留值（旋转、深色模式切换都不会丢）。
- `dialkit-noop`、`dialkit-compose` 的模块拆分，核心库零运行时依赖。

---

## 2. 移植原则

网页版每次更新，都按下面四层分类处理。一个改动跨多层时，按最严格的那一层处理。

### 2.1 数据与语义层：必须忠实移植

**范围**：配置写法、path 规则、label 生成、范围 / 步长推断、取整、颜色解析与格式化、spring / easing 公式、Pad 吸附、持久化 JSON 形状、Copy 文本格式、版本语义。

**规则**：
- 逐行对照源码移植，数值标 `SOURCE`，并在 `src/test` 里加用例，用网页版的计算结果做期望值。
- JSON 必须和网页版双向兼容：不改已有 key 的名字和含义，新增字段要能被旧版本忽略，读取时忽略不认识的 key。
- 这一层的改动是网页版和 Android 之间参数互通的基础，不能为了 Android 的方便而偏离。

### 2.2 视觉层：按 MAP 换算移植

**范围**：CSS 里的尺寸、间距、圆角、字号、颜色 token、动画时长和 spring 参数。

**规则**：
- px → dp 按 1:1 换算（`MAP`）。颜色写进 `res/values(-night)/dialkit_colors.xml`，尺寸写成带 `SOURCE` 注释的常量。
- 已经由作者在真机上调过的值（标 `DESIGN (user)` 的）不随网页版改动：入口 48dp、原生阴影、标题栏留白、点阵、默认 16:9 高度等。网页版改了对应项时，只在文档里记一笔，由维护者决定是否跟进。

### 2.3 交互层：适配后移植

**范围**：指针、悬停、键盘、修饰键、桌面窗口概念（角落定位）。

**规则**：
- 先问"这个交互在触屏上的等价物是什么"，有清晰等价物才移植：悬停 → 长按或不做；键盘操作 → 无障碍动作；拖动 → 加上触屏的手势仲裁（方向锁、按住激活）。
- 没有等价物的（修饰键锁轴、桌面快捷键）不移植，在 §1.2 里写明理由。
- 新交互不能破坏已有的手势仲裁：面板竖向滚动始终优先，横向和按住才归控件。

### 2.4 平台层：不移植机制，只对齐行为

**范围**：React hooks、DOM、portal、localStorage、ResizeObserver、浏览器的 scroll anchoring 等。

**规则**：用 Android 的原生机制实现同样的行为（`DialCell`、浮层、SharedPreferences、View 测量、手动滚动补偿），不引入运行时依赖。

### 2.5 不能动的部分

以下是 Android 版的设计约束，网页版无论怎么改都不跟：

1. 不申请悬浮窗权限，浮层只在宿主窗口内。
2. 核心库零运行时依赖，release 包用 noop。
3. 入口与手势体系（§1.3），以及所有面板内的手势仲裁规则。
4. 触感只用于面板内的操作，只在 Android 14+ 生效。
5. 标 `DESIGN (user)` 的视觉值。

### 2.6 每次跟进网页版的流程

1. 下载新版 npm 包，从 `dist/*.js.map` 的 `sourcesContent` 还原源码，放进本地的 `reference/dialkit-<版本>/`（该目录被 git 忽略，不进仓库）。
2. 对比新旧 `reference/`，把每个改动归到 §2.1–§2.4 的某一层，填进本文 §1 的表格。
3. 先移植数据层并补单元测试，再做视觉层，最后做交互层。
4. 用网页版导出的 JSON 和 Android 的 Copy 输出互相导入，确认参数仍然互通。
5. 在 `CHANGELOG.md` 里写明对齐到的 DialKit 版本。
