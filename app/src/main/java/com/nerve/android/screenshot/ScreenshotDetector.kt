package com.nerve.android.screenshot

/**
 * Pure logic for deciding whether a MediaStore image is a screenshot, and for
 * de-duplicating ContentObserver callbacks (which can fire repeatedly per file).
 */

/** True if a MediaStore image row looks like a screenshot. */
fun isScreenshot(relativePath: String?, bucketName: String?): Boolean {
    val needle = "screenshots"
    if (relativePath?.lowercase()?.contains(needle) == true) return true
    if (bucketName?.lowercase()?.contains(needle) == true) return true
    return false
}

/** Tracks already-handled image URIs so a given screenshot is processed once. */
class SeenUris {
    private val seen = HashSet<String>()

    /** Returns true the first time `uri` is seen; false afterwards. */
    @Synchronized
    fun firstTimeFor(uri: String): Boolean = seen.add(uri)
}
