# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Fixed
- The spring curve and panel springs use a numerically stable form near critical damping and when heavily
  overdamped.

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
