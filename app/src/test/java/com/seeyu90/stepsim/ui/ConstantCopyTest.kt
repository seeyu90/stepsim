package com.seeyu90.stepsim.ui

import com.seeyu90.stepsim.simulator.Gait
import com.seeyu90.stepsim.simulator.StepSimulator
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * 目標上限與步頻只存在程式常數裡：字串用佔位符，畫面以 [goalLimitText]／[Gait.stepsPerMinute] 填入。
 * 常數改變時，文字跟著改變，不會和驗證規則或狀態列的步頻對不上。
 */
class ConstantCopyTest {

    private val locale = Locale.TRADITIONAL_CHINESE

    /** Gradle 單元測試的工作目錄是模組目錄（app/）。 */
    private val strings: Map<String, String> by lazy {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        (0 until nodes.length).associate { (nodes.item(it) as Element).let { e -> e.getAttribute("name") to e.textContent } }
    }

    private fun format(key: String, vararg args: Any) = String.format(locale, strings.getValue(key), *args)

    @Test
    fun `goal hint and error show the current limit from the constant`() {
        val limit = formatSteps(StepSimulator.MAX_TARGET_STEPS, locale)
        assertEquals(limit, goalLimitText(locale))
        assertEquals("輸入 1 到 $limit", format("goal_hint", goalLimitText(locale)))
        assertEquals("請輸入 1 到 $limit 之間的數字", format("goal_error", goalLimitText(locale)))
    }

    @Test
    fun `speed options show the cadence from the gait`() {
        assertEquals("走路（約每分鐘 ${Gait.WALK.stepsPerMinute} 步）", format("speed_walk", Gait.WALK.stepsPerMinute))
        assertEquals("跑步（約每分鐘 ${Gait.RUN.stepsPerMinute} 步）", format("speed_run", Gait.RUN.stepsPerMinute))
    }

    @Test
    fun `templates use placeholders instead of hard coded numbers`() {
        val numbers = mapOf(
            "goal_hint" to formatSteps(StepSimulator.MAX_TARGET_STEPS, locale),
            "goal_error" to formatSteps(StepSimulator.MAX_TARGET_STEPS, locale),
            "speed_walk" to Gait.WALK.stepsPerMinute.toString(),
            "speed_run" to Gait.RUN.stepsPerMinute.toString(),
        )
        for ((key, number) in numbers) {
            val template = strings.getValue(key)
            assertTrue("$key 應使用佔位符：$template", "%1$" in template)
            assertFalse("$key 不應寫死 $number：$template", number in template)
        }
    }
}
