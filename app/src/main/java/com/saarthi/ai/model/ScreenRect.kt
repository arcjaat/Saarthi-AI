package com.saarthi.ai.model

import android.graphics.Rect

/**
 * Pure Kotlin data class representing a rectangular screen boundary.
 *
 * Decoupled from android.graphics.Rect so that:
 * 1. Unit tests run deterministically on local JVM without requiring Android stubs or mocks.
 * 2. Immutable, thread-safe, and garbage-collection friendly.
 * 3. Exact field equality and coordinate math.
 */
data class ScreenRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    val isValid: Boolean
        get() = right > left && bottom > top && left >= 0 && top >= 0

    fun toAndroidRect(): Rect = Rect(left, top, right, bottom)

    override fun toString(): String = "($left,$top,$right,$bottom)"

    companion object {
        fun fromAndroidRect(rect: Rect): ScreenRect =
            ScreenRect(rect.left, rect.top, rect.right, rect.bottom)

        val EMPTY = ScreenRect(0, 0, 0, 0)
    }
}
