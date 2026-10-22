# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- `DialKitConfig.floatingPanel = false` turns the floating panel off entirely, for apps that tune only in their
  own screens with in-page panels.
- Panel in a page: `DialPanelView` (Views) and `DialKitPanel` (Compose) show a registered group as part of a
  screen, like DialKit's inline mode; while shown, the group is left out of the floating panel.
- `adb shell am broadcast … --es cmd dump [--es id <panel>]` writes Copy's text (JSON and Kotlin) to logcat, so
  scripts and coding agents can read tuned values without touching the device.
- `DialKitConfig.entryButton = false` hides the floating entry; the host opens the panel with `DialKit.open()` or
  `setOpen(true)`, and closing it hides it.
- Focused text fields in the panel (Text controls, a slider's value editor) scroll into view, also after the keyboard
  shrinks the panel.

### Fixed
- Values from persistence, versions, Import, adb or `setValues` can no longer crash the host: non-finite numbers and
  invalid curves (negative durations, non-positive stiffness or mass, negative damping, bezier x outside 0..1) are
  ignored or clamped, and Import reports them as ignored.
- Slider hash marks follow DialKit's count rule (no mark on an uneven last step).
- Moving the finger after a long press opened a slider's value input no longer drags the value underneath.
- The spring curve and panel springs use a numerically stable form near critical damping and when heavily
  overdamped.

### Changed
- Declarations check their parameters: a slider needs finite values, `min < max` and a positive step, and curve
  defaults must be valid; otherwise they throw `IllegalArgumentException` at declaration.

## [0.0.1] - 2026-10-21

First public release. Aligned with DialKit 2.0.2.

### Added
- Controls: slider, toggle, transition (time / physics spring, easing), folder, action, select, color, image, text, pad.
- Overlay inside the host window with no overlay permission; three-finger double tap, keyboard shortcut and adb
  broadcast to show the entry; draggable, snapping entry; movable, resizable panel with IME avoidance.
- Versions, persistence and Copy in DialKit's formats; Import from the clipboard, a file or adb.
- Haptic feedback for panel operations on Android 14+.
- `dialkit-noop` for release builds and `dialkit-compose` for Compose hosts.
- Image control: options plus uploads through the system photo picker, with platform-only thumbnail loading.
- Long-press a slider to type its value; accessibility actions and hardware-keyboard control for sliders,
  toggles, segmented controls, the pad, colour tracks, folders and menus.
- DialKit controller and root options: `setValues`, `getOpen` / `setOpen`, `DialPersist(key, presets)`,
  `entryPosition`, `defaultOpen`, `onOpenChange`.
