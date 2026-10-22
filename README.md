# DialKit for Android

[![JitPack](https://jitpack.io/v/lixiaolin94/dialkit-android.svg)](https://jitpack.io/#lixiaolin94/dialkit-android)

[中文](README.zh-CN.md)

A live tuning panel for Android demos, modelled on [DialKit](https://www.dialkit.dev/) by Josh Puckett: declare a few dials in Kotlin, then drag, toggle and pick values on the device while your UI updates in place. Built with plain Android Views.

> An unofficial, independent port. It is not affiliated with or endorsed by Josh Puckett or the DialKit project; see [Relationship to DialKit](#relationship-to-dialkit).

- **No runtime dependencies.** The library ships only Kotlin code and resources (about 84 KB of dex after R8). No AndroidX, no Compose, no coroutines.
- **No overlay permission.** The panel lives inside each Activity's own window, above your content, and never steals your touches or gestures.
- **Drop-in.** It installs itself in debuggable builds; swap in `dialkit-noop` for release builds, which keeps the same API and returns the declared defaults.
- **Views and Compose.** Compose hosts add the small `dialkit-compose` adapter and recompose only when a value they read changes.
- **Interchangeable with DialKit on the web.** Values, versions and the Copy output use DialKit's JSON, so parameters move between a web prototype and the Android demo in both directions.

## Contents

- [Quick start](#quick-start)
- [Install](#install)
- [Controls](#controls)
- [Read values](#read-values)
- [Use the panel](#use-the-panel)
- [From tuning back to code](#from-tuning-back-to-code)
- [Control the panel from code](#control-the-panel-from-code)
- [Panel in a page](#panel-in-a-page)
- [Configure](#configure)
- [Troubleshooting](#troubleshooting)
- [Modules, samples and docs](#modules-samples-and-docs)
- [Relationship to DialKit](#relationship-to-dialkit)

## Quick start

1. Add the dependencies ([Install](#install)).
2. Declare what you want to tune, and read it:

   ```kotlin
   class CardDials : DialGroup() {
       val radius by slider(24f, 0f..64f)
       val tint by color("#a78bfa")
   }

   // Compose
   val dials = rememberDialKit("Card") { CardDials() }
   Box(Modifier.size(120.dp).clip(RoundedCornerShape(dials.radius.dp)).background(Color(dials.tint)))

   // Views, in an Activity
   val dials = DialKit.panel(this, "Card") { CardDials() }
   dials.observe(this) { shape.cornerRadius = it.radius * density; shape.setColor(it.tint) }
   ```

3. Run a **debug** build and **double-tap with three fingers** anywhere. A round entry button appears; tap it to open the panel, then drag the dials and watch the card change.

That is all the setup: no `Application` code, no permission, nothing to remove for release.

## Install

DialKit for Android is published on [JitPack](https://jitpack.io/#lixiaolin94/dialkit-android). Add the repository in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Then the dependencies in your app module:

```kotlin
dependencies {
    debugImplementation("com.github.lixiaolin94.dialkit-android:dialkit:0.0.1")
    releaseImplementation("com.github.lixiaolin94.dialkit-android:dialkit-noop:0.0.1")
    // Compose hosts only:
    implementation("com.github.lixiaolin94.dialkit-android:dialkit-compose:0.0.1")
}
```

- `dialkit` is the real panel. It installs itself when the app is debuggable.
- `dialkit-noop` has the same API and no UI. In release builds every dial returns its declared default, so your code compiles and runs unchanged.
- The first request for a new version makes JitPack build it, which can take a few minutes.

Requirements: minSdk 23, compileSdk 33 or newer, Kotlin 2.0 or newer. `dialkit-compose` also needs Compose 1.7 (BOM 2024.12.01) or newer.

## Controls

Declare dials as properties of a `DialGroup`. The property name is the key; the label is its Title Case form unless you pass `label = "…"`.

| Control | Declaration | Reads as |
|---|---|---|
| Slider | `slider(24f, 0f..64f)`, optional `step = 2f` | `Float` |
| Slider, inferred range | `slider(1.2f)`: range and step come from the default, as in DialKit | `Float` |
| Integer slider | `slider(3, 1..6)` | `Int` |
| Toggle | `toggle(true)` | `Boolean` |
| Select | `select("grid", "list", default = "list")` | `String` |
| Select with labels | `selectOptions(listOf(DialOption("start", "Left"), DialOption("end", "Right")))` | `String` (the value) |
| Text | `text("Hello", placeholder = "Title")` | `String` |
| Color | `color("#a78bfa")`; also `oklch(…)`, `rgb(…)`, `hsl(…)`, `color(display-p3 …)` or an ARGB `Int` | `@ColorInt Int` (sRGB) |
| Image | `image("android.resource://com.example/drawable/hero", "https://…")`, or `imageOptions(…)` with labels | `String` URI, `""` for none |
| Pad (2D) | `pad()`, or `pad(x = DialAxis(0f, -1f, 1f), y = DialAxis(…), xLabel = "H", yLabel = "V")` | `DialPoint(x, y)`, y up |
| Time spring | `spring(visualDuration = 0.3f, bounce = 0.2f)` | `DialTransition` |
| Physics spring | `physicsSpring(stiffness = 200f, damping = 25f, mass = 1f)` | `DialTransition` |
| Easing | `easing(duration = 0.4f, ease = floatArrayOf(0.42f, 0f, 0.58f, 1f))` | `DialTransition` |
| Folder | `folder(collapsed = true) { ShadowDials() }` | the child group |
| Button | `action("Shuffle") { … }` | `DialAction` |

A fuller example:

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
    val blur by slider(8f, 0f..40f)       // its path is "shadow.blur"
}
```

Image values are URIs Android can open: `android.resource://`, `content://`, `file://`, `file:///android_asset/`, `http(s)://` (needs the INTERNET permission) or `data:`. The panel can also upload a picture through the system photo picker; it is copied into app storage and read as `file://…`. Load the value with `ImageView.setImageURI(Uri.parse(value))` or any image library.

## Read values

**Compose.** Reading a dial in a composable subscribes that composable to that dial only:

```kotlin
val dials = rememberDialKit("Card") { CardDials() }
Box(Modifier.clip(RoundedCornerShape(dials.radius.dp)).background(Color(dials.tint)))
val scale by animateFloatAsState(if (pressed) 0.9f else 1f, dials.press.toAnimationSpec())
```

The panel is registered while the composable is on screen and removed when it leaves.

**Views.** Works in any `android.app.Activity`, AppCompat or not:

```kotlin
val dials = DialKit.panel(this, "Card") { CardDials() }
dials.observe(this) { d ->
    card.alpha = if (d.elevated) 1f else 0.6f
    shape.cornerRadius = d.radius * resources.displayMetrics.density
}
```

`observe` calls back once immediately, then after every change (changes in the same frame are coalesced), and stops when the Activity is destroyed. Fragments pass `viewLifecycleOwner.lifecycle` instead of `this`. You can also read `dials.radius` directly at any time.

**Curves on Views.** An easing gives `toInterpolator()` and `durationMillis`; a spring gives `stiffness` and `dampingRatio`, ready for `SpringForce`.

## Use the panel

| Action | How |
|---|---|
| Show / hide the entry button | Three-finger double tap anywhere, Ctrl+Shift+D on a hardware keyboard, `DialKit.toggleEntry()`, or `adb shell am broadcast -a <applicationId>.dialkit.TOGGLE -p <applicationId>` |
| Open / close the panel | Tap the entry; tap outside the panel or press Back to close |
| Change a value | Drag anywhere on a slider's row, or tap to jump; tap a select, colour or image row to open its picker |
| Type a value | Long-press a slider, type, then Done (Back cancels) |
| Move / resize | Drag the title bar; drag the handle under the panel, double-tap it to reset |
| Versions | The menu on the left of the toolbar: "Version 1" holds the base values, "New version" saves the current values as a new one |
| Copy | Copies the values as DialKit JSON plus Kotlin declarations, and logs them under `adb logcat -s DialKit` |
| Import | Paste DialKit JSON from the clipboard or a file; the values land in a new version. Over adb: add `--es cmd import --es json '<JSON>'` to the broadcast |
| Read values from a computer | Add `--es cmd dump` to the broadcast, then `adb logcat -d -v raw -s DialKit`: the same text as Copy, for every registered panel (or `--es id <id>` for one) |

With several groups on one screen, the panel shows each as a section. The entry remembers whether it was visible, so it comes back after a restart.

## From tuning back to code

The usual loop:

1. Tune on the device until it looks right.
2. Tap **Copy**. The clipboard (and `adb logcat -s DialKit`) now holds the values as DialKit JSON, followed by the same dials written as Kotlin with the new defaults, such as `val radius by slider(32f, 0f..64f, step = 1f)`.
   Without touching the device, `--es cmd dump` on the adb broadcast writes the same text to logcat, which also lets a coding agent pick the values up.
3. Paste those lines over the old declarations in your `DialGroup`. The tuned values are now the defaults, so release builds (`dialkit-noop`) ship exactly what you saw.

The JSON is the same as DialKit's on the web: paste it into a web prototype, or Import a web export on the device.

Values live in memory for the whole process, so rotation and theme changes keep them. To keep values and versions across app restarts too:

```kotlin
val dials = rememberDialKit("Card", persist = DialPersist()) { CardDials() }
// DialPersist(key = "card", presets = false): choose the storage key, or keep values without versions.
```

## Control the panel from code

```kotlin
dials.setValues("radius" to 32, "tint" to "#f472b6", "shadow" to mapOf("blur" to 12))  // one update
dials.resetValues()                // back to the declared defaults
dials.setOpen(true)                // open this panel; dials.getOpen() reads it
DialKit.showEntry(); DialKit.open(); DialKit.close(); DialKit.hideEntry()
```

Buttons report to the panel's `onAction` as well as to their own callback:

```kotlin
val dials = rememberDialKit("Card", onAction = { path -> if (path == "reset") log("reset") }) { CardDials() }
```

Panels with the same `id` share one set of values, which helps when several screens tune the same component.

## Panel in a page

Some apps keep their tuning on a screen of its own, an Activity or a Fragment, instead of a floating button. Show a registered group there with `DialPanelView` (Views) or `DialKitPanel` (Compose):

```kotlin
// Views: in an Activity, or a Fragment's onViewCreated
val dials = DialKit.panel(this, "Card") { CardDials() }
findViewById<DialPanelView>(R.id.tuning).group = dials      // or DialPanelView(context).apply { group = dials }

// Compose
val dials = rememberDialKit("Card") { CardDials() }
Column(Modifier.verticalScroll(rememberScrollState())) {
    Preview(dials)
    DialKitPanel(dials, Modifier.fillMaxWidth())
}
```

- It shows the group's name, the toolbar (versions, Import, Copy) and the controls, full width on the panel background, with no border, shadow or close button.
- With a wrap-content height it grows with its controls and your page scrolls them; with a fixed or `match_parent` height the header stays and the controls scroll inside.
- Pickers and menus float above the page, and Back closes an open one first.
- While the view is on screen, its group is left out of the floating panel (other groups stay there), `getOpen()` is `true` and `setOpen()` does nothing.
- It works in any build type that uses the full `dialkit` artifact; the floating overlay still installs only in debuggable builds. With `dialkit-noop` it draws nothing.
- For an app that tunes only in its own screens, turn the floating panel off entirely with `DialKit.configure { floatingPanel = false }` (see [`samples/embedded`](samples/embedded)): nothing is added to your Activities, and the gesture, shortcut and adb `toggle` do nothing; adb `dump` and `import` still work.
- In a `LazyColumn`, an item scrolled far enough away is disposed, and the group returns to the floating panel until it comes back; use a plain scrolling `Column` to keep it.

## Configure

All optional. Call it in `Application.onCreate` or the launcher Activity's `onCreate`, before it resumes:

```kotlin
DialKit.configure {
    entryVisibleByDefault = true                            // show the entry on first launch, no gesture needed
    entryButton = false                                     // no entry button: open with DialKit.open() / setOpen(true)
    floatingPanel = false                                   // no floating panel at all: in-page panels only
    toggleGesture = ToggleGesture.ThreeFingerLongPress()    // or ToggleGesture.None
    keyShortcut = null                                      // turn off Ctrl+Shift+D
    entryPosition = DialPosition.BottomEnd                  // where the entry first appears (default TopEnd)
    defaultOpen = true                                      // open the panel at launch when the entry is visible
    theme = DialTheme.Dark                                  // default: follow the system
    haptics = false                                         // panel haptics, Android 14+ only
    onOpenChange = { open -> /* panel opened or closed */ }
}
```

Colours come from resources named `dialkit_*` (see [`dialkit_colors.xml`](dialkit/src/main/res/values/dialkit_colors.xml) and its `values-night` twin). Redefine any of them in your app to restyle the panel.

To install manually, remove `io.github.lixiaolin94.dialkit.DialKitInitProvider` from the merged manifest with `tools:node="remove"` and call `DialKit.install(application)`.

## Troubleshooting

| Problem | What to check |
|---|---|
| Nothing appears | The entry starts hidden: double-tap with three fingers, or set `entryVisibleByDefault = true`. The panel exists only in debuggable builds that use `dialkit`, not `dialkit-noop`. |
| The three-finger gesture does nothing | Some ROMs use three-finger gestures for screenshots. Use Ctrl+Shift+D, the adb broadcast, `DialKit.showEntry()`, or `ToggleGesture.ThreeFingerLongPress()`. |
| The panel says "No dials registered on this screen" | No group is registered at the moment: call `rememberDialKit` or `DialKit.panel` from the screen you want to tune. |
| `Could not find com.github.lixiaolin94…` | Add `maven("https://jitpack.io")` to `dependencyResolutionManagement.repositories`. |
| Values reset after restarting the app | That is the default; pass `persist = DialPersist()`. |
| A release build shows no panel | Expected: release uses `dialkit-noop`, and dials return their defaults. |
| `IllegalArgumentException` when a group is created | A declaration cannot work: a slider needs finite values, `min < max` and a positive step; an easing needs a duration ≥ 0 and x1, x2 in 0..1; a spring needs a positive duration, or stiffness and mass > 0 and damping ≥ 0. |

## Modules, samples and docs

| Artifact | Contents | Runtime dependencies |
|---|---|---|
| `dialkit` | DSL, overlay, gestures, all UI, persistence | kotlin-stdlib |
| `dialkit-noop` | The same API with no UI, for release builds | kotlin-stdlib |
| `dialkit-compose` | `rememberDialKit`, `toAnimationSpec()`, `DialKitPanel`, value-level recomposition | Compose runtime, animation-core, ui |

| Sample | What it shows |
|---|---|
| [`samples/catalog`](samples/catalog) | Every control and panel feature, one screen each, including panels in a Compose and a Views page |
| [`samples/compose`](samples/compose) | A Compose host with a single playground panel |
| [`samples/views`](samples/views) | A plain `android.app.Activity` with View-based observation |

Install the catalog with `./gradlew :samples:catalog:installDebug`.

- [docs/DESIGN.md](docs/DESIGN.md): architecture, and every value with its source (DialKit, an Android mapping, or our own design).
- [docs/WEB_PARITY.md](docs/WEB_PARITY.md): feature parity with DialKit on the web, and how its updates are ported.
- [CONTRIBUTING.md](CONTRIBUTING.md): building and testing. [docs/RELEASING.md](docs/RELEASING.md): publishing a release.

## Relationship to DialKit

[DialKit](https://www.dialkit.dev/) is a React tuning panel by Josh Puckett, released under the MIT License. DialKit for Android is a separate project by a different author: an unofficial port to native Android, not affiliated with or endorsed by Josh Puckett.

- **Ported from DialKit 2.0.2:** the data model and config rules, value formats and JSON (values, versions, Copy), the maths (springs, easing, colour, pad snapping), the icons and the visual specification. These follow DialKit's source as closely as Android allows.
- **Adapted:** inline mode becomes a per-group panel in a page (`DialPanelView` / `DialKitPanel`), without switching between floating and inline.
- **Android's own:** the overlay inside the host window, the entry button and every gesture, haptics, Import, IME handling, accessibility, the Views and Compose APIs, and `dialkit-noop`.
- **Not ported:** `productionEnabled`, keyboard shortcuts, Shift axis locking and Timeline; [docs/WEB_PARITY.md](docs/WEB_PARITY.md) lists each with the reason, and the rules for following future DialKit releases.

DialKit's copyright notice ships inside every artifact (`META-INF/<module>-NOTICES.txt`) and in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). DialKit's own source is not included in this repository; the port follows DialKit 2.0.2 ([npm](https://www.npmjs.com/package/dialkit/v/2.0.2), [source](https://github.com/joshpuckett/dialkit)). The title-bar handle's proportions start from [Leva](https://github.com/pmndrs/leva)'s (MIT); no Leva code is included.

Bug reports about this port belong in [this repository's issues](https://github.com/lixiaolin94/dialkit-android/issues), not DialKit's.

## License

[MIT](LICENSE)
