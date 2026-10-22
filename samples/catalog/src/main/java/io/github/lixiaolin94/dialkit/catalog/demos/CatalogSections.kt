package io.github.lixiaolin94.dialkit.catalog.demos

import io.github.lixiaolin94.dialkit.catalog.CatalogSection
import io.github.lixiaolin94.dialkit.catalog.Demo

/** The catalog's table of contents, in the order of the home screen. */
internal val catalogSections: List<CatalogSection> = listOf(
    CatalogSection(
        "Controls",
        listOf(
            Demo("sliders", "Sliders", "Explicit and inferred ranges, steps, integers, rubber band, typed values.", ::SlidersDemo),
            Demo("toggles", "Toggles", "On / off switches as two-segment controls.", ::TogglesDemo),
            Demo("select", "Select", "Dropdowns with Title Case or explicit labels.", ::SelectDemo),
            Demo("color", "Color", "Hex, OKLCH and Display P3 with the full picker.", ::ColorDemo),
            Demo("text", "Text", "Free text with placeholders, up to five lines.", ::TextDemo),
            Demo("pad", "Pad", "Two-axis position with custom axes and labels.", ::PadDemo),
            Demo("image", "Image", "Bundled, remote and uploaded images as URI strings.", ::ImageDemo),
            Demo("folders", "Folders and actions", "Nested and collapsed folders, buttons and onAction.", ::FoldersDemo),
        ),
    ),
    CatalogSection(
        "Motion",
        listOf(
            Demo("springs", "Springs", "Time and physics springs driving Compose animations.", ::SpringsDemo),
            Demo("easing", "Easing", "Cubic-bezier curves with the control-point editor.", ::EasingDemo),
        ),
    ),
    CatalogSection(
        "Panels",
        listOf(
            Demo("multiple", "Multiple panels", "Two groups on one screen, one collapsed by default.", ::MultiplePanelsDemo),
            Demo("controller", "Controller API", "setValues, resetValues, getOpen, setOpen and onOpenChange.", ::ControllerDemo),
            Demo("persistence", "Persistence and versions", "Values and versions that survive restarts; Copy and Import.", ::PersistenceDemo),
            Demo("host", "Host gestures", "Taps, pinch and scrolling still reach the app.", ::HostGesturesDemo),
        ),
    ),
    CatalogSection(
        "In a page",
        listOf(
            Demo("page", "Panel in a page", "The panel as part of a Compose screen, scrolling with the page.", ::PagePanelDemo),
            Demo("views-page", "Panel in a Views screen", "A fixed-height DialPanelView in a plain Activity.", ::ViewsPageDemo),
        ),
    ),
)
