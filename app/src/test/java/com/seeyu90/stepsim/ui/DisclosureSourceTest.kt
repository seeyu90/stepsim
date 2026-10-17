package com.seeyu90.stepsim.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 揭露的位置以原始碼掃描確認：主畫面一定有「使用說明與注意事項」連結，使用說明頁一定有責任使用條文（guide_3）。
 * 授權頁的責任使用條文由 [permissionPoints] 決定，見 PermissionStageTest。
 *
 * 限制：只確認呼叫存在（去掉註解後），不保證每個分支都會渲染到；畫面檔或字串改名時要一併更新這裡。
 */
class DisclosureSourceTest {

    /** Gradle 單元測試的工作目錄是模組目錄（app/）。 */
    private val uiDir = File("src/main/java/com/seeyu90/stepsim/ui")

    /** 去掉區塊註解與行註解，避免註解裡提到的名稱也算數。 */
    private fun codeOf(name: String): String {
        val file = File(uiDir, name)
        assertTrue("找不到 ${file.absolutePath}", file.isFile)
        return file.readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }
    }

    @Test
    fun `main screen links to the guide`() {
        assertTrue("SimulatorScreen.kt 沒有使用說明連結", "R.string.disclaimer_link" in codeOf("SimulatorScreen.kt"))
    }

    @Test
    fun `guide screen keeps the responsible use item`() {
        assertTrue("GuideScreen.kt 沒有 guide_3", "R.string.guide_3" in codeOf("GuideScreen.kt"))
    }
}
