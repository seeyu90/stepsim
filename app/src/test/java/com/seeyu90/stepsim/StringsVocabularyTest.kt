package com.seeyu90.stepsim

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * 禁用詞掃描：這些詞會暗示感測器偵測、競賽或舊用語，使用者看得到的字串不得出現。
 * 只有「禁止用途」與「不是感測器偵測到的…」這類否定語境例外，以（key, 詞）配對列在 [allowed]。
 * 掃描所有使用者看得到的文字節點：`<string>`、`<plurals>` 與 `<string-array>` 的 `<item>`；
 * XML 註解不是使用者看得到的文字，不在掃描範圍。`<item>` 以「父節點名稱[數量或索引]」標示，白名單用父節點名稱。
 */
class StringsVocabularyTest {

    private val forbidden = listOf("賺", "獎勵", "挑戰", "排名", "偵測", "追蹤", "記錄", "寫入", "授權")

    /** 例外：key → 只允許出現的禁用詞。 */
    private val allowed = mapOf(
        "guide_3" to setOf("獎勵"),
        "perm_point_4" to setOf("獎勵"),
    )

    /**
     * 「模擬」只用在說明用途的文字；按鈕、狀態與通知的動詞不加「模擬」（開始、暫停、繼續、結束、完成、再來一次）。
     * 前綴以 `_` 結尾者比對整個前綴。
     */
    private val simulationWordKeys = listOf(
        "app_subtitle",
        "perm_why",
        "perm_point_1",
        "guide_",
        "rationale_write",
        "end_confirm_",
    )

    private fun explainsPurpose(key: String) =
        simulationWordKeys.any { if (it.endsWith("_")) key.startsWith(it) else key == it }

    /** Gradle 單元測試的工作目錄是模組目錄（app/）。 */
    private val stringsFile = File("src/main/res/values/strings.xml")

    /** 一個文字節點：[key] 用於白名單比對，[label] 用於錯誤訊息。 */
    private data class TextNode(val key: String, val label: String, val text: String)

    private fun textNodes(file: File = stringsFile): List<TextNode> {
        assertTrue("找不到 ${file.absolutePath}", file.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = document.documentElement
        val result = mutableListOf<TextNode>()
        val children = root.childNodes
        for (index in 0 until children.length) {
            val element = children.item(index) as? Element ?: continue
            val name = element.getAttribute("name")
            when (element.tagName) {
                "string" -> result += TextNode(name, name, element.textContent)
                "plurals", "string-array" -> {
                    val items = element.getElementsByTagName("item")
                    for (i in 0 until items.length) {
                        val item = items.item(i) as Element
                        val which = item.getAttribute("quantity").ifEmpty { i.toString() }
                        result += TextNode(name, "$name[$which]", item.textContent)
                    }
                }
            }
        }
        return result
    }

    private fun strings(): Map<String, String> = textNodes().filter { it.key == it.label }.associate { it.key to it.text }

    @Test
    fun `user visible strings do not contain forbidden words outside the allowed pairs`() {
        val violations = textNodes().flatMap { node ->
            forbidden.filter { it in node.text && it !in allowed[node.key].orEmpty() }
                .map { "${node.label} 含「$it」：${node.text}" }
        }
        assertEquals("禁用詞：\n" + violations.joinToString("\n"), emptyList<String>(), violations)
    }

    @Test
    fun `every allowed pair still matches the actual text`() {
        val all = strings()
        for ((key, words) in allowed) {
            val text = all[key]
            assertTrue("白名單的 $key 不存在，請一併移除例外", text != null)
            for (word in words) {
                assertTrue("白名單的 $key 已不含「$word」，請一併移除例外：$text", word in text!!)
            }
        }
    }

    @Test
    fun `simulation word only appears in strings that explain the purpose`() {
        val violations = textNodes().filter { "模擬" in it.text && !explainsPurpose(it.key) }
            .map { "${it.label}：${it.text}" }
        assertEquals("「模擬」只用在說明用途的文字：\n" + violations.joinToString("\n"), emptyList<String>(), violations)
    }
}
