package com.seeyu90.stepsim.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyTest {

    @Test
    fun `body and label text is at least 16sp`() {
        val styles = mapOf(
            "bodyLarge" to Typography.bodyLarge,
            "bodyMedium" to Typography.bodyMedium,
            "bodySmall" to Typography.bodySmall,
            "labelLarge" to Typography.labelLarge,
        )
        for ((name, style) in styles) {
            assertTrue("$name 的字級單位應為 sp：${style.fontSize}", style.fontSize.isSp)
            assertTrue("$name 只有 ${style.fontSize}", style.fontSize.value >= 16f)
        }
    }

    @Test
    fun `ring number line height scales with the auto sized font`() {
        val lineHeight = Typography.displayLarge.lineHeight
        assertTrue("displayLarge 的行高應為 em（跟著 autoSize 縮放）：$lineHeight", lineHeight.isEm)
    }
}
