package io.github.lixiaolin94.dialkit.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Expected values follow DialKit's imageLabel / imageOptions (image-control.ts). */
class ImageMathTest {
    @Test fun labelsFollowDialKit() {
        assertEquals("No image", imageLabel(""))
        assertEquals("Uploaded image", imageLabel("data:image/png;base64,AAAA"))
        assertEquals("Uploaded image", imageLabel("blob:https://x/1"))
        assertEquals("hero shot.jpg", imageLabel("https://cdn.example.com/a/hero%20shot.jpg?w=400#top"))
        assertEquals("sample_dune", imageLabel("android.resource://pkg/drawable/sample_dune"))
        assertEquals("cat.png", imageLabel("file:///data/user/0/pkg/files/dialkit/images/ab12cd/cat.png"))
        assertEquals("a+b.png", imageLabel("https://x/a+b.png")) // '+' is not a space in a path
    }

    @Test fun choicesDeduplicateAndKeepTheCurrentValue() {
        val options = listOf(DialOption("a.png", "A"), DialOption("b.png", "B"))
        val uploaded = listOf(DialOption("b.png", "dup"), DialOption("file:///u/c.png", "c.png"))
        assertEquals(listOf("a.png", "b.png", "file:///u/c.png"), imageChoices(options, uploaded, "a.png").map { it.value })
        assertEquals("B", imageChoices(options, uploaded, "").first { it.value == "b.png" }.label) // first wins
        // A value restored from a version or an import stays selectable.
        val restored = imageChoices(options, emptyList(), "https://x/z.jpg")
        assertEquals(DialOption("https://x/z.jpg", "z.jpg"), restored.last())
        assertEquals(emptyList<DialOption>(), imageChoices(emptyList(), emptyList(), ""))
    }
}
