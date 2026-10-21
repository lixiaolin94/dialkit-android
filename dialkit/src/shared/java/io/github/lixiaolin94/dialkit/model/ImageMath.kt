package io.github.lixiaolin94.dialkit.model

import java.net.URLDecoder

/** SOURCE image-control.ts MAX_FILE_SIZE: uploads above 10 MB are refused. */
internal const val MAX_IMAGE_BYTES = 10 * 1024 * 1024

/** What [imageLabel] says for an empty value or an upload; the UI maps these to localized strings. */
internal const val IMAGE_LABEL_NONE = "No image"
internal const val IMAGE_LABEL_UPLOADED = "Uploaded image"

/**
 * SOURCE imageLabel: "No image" for an empty value, "Uploaded image" for data: / blob: values, else the last path
 * segment (query and fragment dropped, percent-decoded), or "Image".
 */
internal fun imageLabel(value: String): String {
    if (value.isEmpty()) return IMAGE_LABEL_NONE
    if (value.startsWith("data:") || value.startsWith("blob:")) return IMAGE_LABEL_UPLOADED
    val name = value.split('?', '#')[0].split('/').lastOrNull { it.isNotEmpty() } ?: return "Image"
    return runCatching { URLDecoder.decode(name.replace("+", "%2B"), "UTF-8") }.getOrDefault(name)
}

/**
 * SOURCE imageOptions: declared options, then this session's uploads, de-duplicated by value; a current value that
 * is in neither (restored versions, code, imports) is appended so it stays selectable.
 */
internal fun imageChoices(options: List<DialOption>, uploaded: List<DialOption>, value: String): List<DialOption> {
    val unique = LinkedHashMap<String, DialOption>()
    for (option in options + uploaded) if (option.value.isNotEmpty() && option.value !in unique) unique[option.value] = option
    if (value.isNotEmpty() && value !in unique) unique[value] = DialOption(value, imageLabel(value))
    return unique.values.toList()
}
