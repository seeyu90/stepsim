package com.seeyu90.stepsim.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「模擬資料」標籤必須常駐在主畫面、授權頁、使用說明頁：以原始碼掃描確認三個畫面檔都呼叫 SimulatedDataBadge。
 *
 * 限制：只確認呼叫存在（去掉註解後），不保證每個分支都會渲染到；畫面檔改名或標籤元件改名時要一併更新這裡。
 */
class DisclosureSourceTest {

    /** Gradle 單元測試的工作目錄是模組目錄（app/）。 */
    private val uiDir = File("src/main/java/com/seeyu90/stepsim/ui")

    private val screens = listOf("SimulatorScreen.kt", "PermissionScreen.kt", "GuideScreen.kt")

    /** 去掉區塊註解與行註解，避免註解裡提到元件名稱也算數。 */
    private fun codeOf(file: File): String =
        file.readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }

    @Test
    fun `every screen shows the simulated data badge`() {
        for (name in screens) {
            val file = File(uiDir, name)
            assertTrue("找不到 ${file.absolutePath}", file.isFile)
            assertTrue("$name 沒有呼叫 SimulatedDataBadge(", "SimulatedDataBadge(" in codeOf(file))
        }
    }
}
