package com.seeyu90.stepsim

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ui/ 的 Kotlin 原始碼不得寫死使用者看得到的文字：字串常值裡不能有中日韓文字或 "StepSim"，
 * 文案一律放在 strings.xml（多語系與改名時才不會漏）。
 *
 * 不檢查：註解、package／import、`@Preview(...)` 的參數（Preview 名稱只在 Android Studio 顯示）。
 */
class UiHardcodedStringTest {

    /** Gradle 單元測試的工作目錄是模組目錄（app/）。 */
    private val uiDir = File("src/main/java/com/seeyu90/stepsim/ui")

    /** 允許的例外（檔名 → 字串常值），例如 log tag；目前沒有。 */
    private val allowed = emptyMap<String, Set<String>>()

    private val cjk = Regex("[\\u3000-\\u303F\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uFF00-\\uFFEF]")

    @Test
    fun `ui sources do not hardcode user visible text`() {
        assertTrue("找不到 ${uiDir.absolutePath}", uiDir.isDirectory)
        val violations = uiDir.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            KotlinStringLiterals.find(file.readText())
                .filter { (cjk.containsMatchIn(it.text) || "StepSim" in it.text) && it.text !in allowed[file.name].orEmpty() }
                .map { "${file.name}:${it.line} \"${it.text}\"" }
        }.toList()
        assertEquals("寫死的文字：\n" + violations.joinToString("\n"), emptyList<String>(), violations)
    }

    @Test
    fun `scanner skips comments, imports and preview names but finds real literals`() {
        val source = """
            package com.example
            import com.example.Thing // "註解裡的字"
            /* 區塊註解 "也不算" */
            @Preview(name = "預覽名稱", showBackground = true)
            fun f() = Text("寫死") + "a${'$'}{g("巢狀")}b" + "StepSim"
        """.trimIndent()
        val texts = KotlinStringLiterals.find(source).map { it.text }
        assertEquals(listOf("寫死", "a\${g(\"巢狀\")}b", "StepSim"), texts)
    }

    @Test
    fun `scanner skips preview names in a multi line annotation`() {
        val source = """
            @Preview(
                name = "多行預覽名稱",
                fontScale = 2f,
            )
            @Composable
            fun f() = Text("寫死")
        """.trimIndent()
        assertEquals(listOf("寫死"), KotlinStringLiterals.find(source).map { it.text })
    }
}

/**
 * 極簡的 Kotlin 詞法掃描：只為了找出字串常值，略過註解、import／package 與 `@Preview(...)` 的參數。
 * `@Preview(` 以括號深度追蹤，參數跨多行也會略過。
 */
internal object KotlinStringLiterals {

    data class Literal(val line: Int, val text: String)

    fun find(source: String): List<Literal> {
        val result = mutableListOf<Literal>()
        var i = 0
        var line = 1
        var lineStart = 0
        // 目前在 @Preview( 的括號內第幾層；0 代表不在 Preview 參數內
        var previewDepth = 0
        fun currentLine() = source.substring(lineStart, source.indexOf('\n', lineStart).let { if (it < 0) source.length else it }).trim()

        while (i < source.length) {
            val c = source[i]
            when {
                c == '\n' -> { line++; i++; lineStart = i }
                source.startsWith("//", i) -> i = source.indexOf('\n', i).let { if (it < 0) source.length else it }
                source.startsWith("/*", i) -> {
                    var depth = 1
                    i += 2
                    while (i < source.length && depth > 0) {
                        when {
                            source.startsWith("/*", i) -> { depth++; i += 2 }
                            source.startsWith("*/", i) -> { depth--; i += 2 }
                            else -> { if (source[i] == '\n') { line++; lineStart = i + 1 }; i++ }
                        }
                    }
                }
                isPreviewStart(source, i) -> {
                    i = source.indexOf('(', i) + 1
                    previewDepth = 1
                }
                previewDepth > 0 && c == '(' -> { previewDepth++; i++ }
                previewDepth > 0 && c == ')' -> { previewDepth--; i++ }
                c == '"' -> {
                    val startLine = line
                    val skip = previewDepth > 0 ||
                        currentLine().let { it.startsWith("import ") || it.startsWith("package ") }
                    val raw = source.startsWith("\"\"\"", i)
                    val end = if (raw) source.indexOf("\"\"\"", i + 3) + 3 else stringEnd(source, i + 1)
                    val text = if (raw) source.substring(i + 3, end - 3) else source.substring(i + 1, end - 1)
                    text.forEach { if (it == '\n') line++ }
                    if (!skip) result += Literal(startLine, text)
                    i = end
                }
                c == '\'' -> i = charEnd(source, i)
                else -> i++
            }
        }
        return result
    }

    /** `@Preview` 後面（可隔空白與換行）緊接 `(`；不含 `@PreviewParameter` 之類的其他註解。 */
    private fun isPreviewStart(source: String, at: Int): Boolean {
        if (!source.startsWith("@Preview", at)) return false
        var i = at + "@Preview".length
        if (i < source.length && source[i].isLetterOrDigit()) return false
        while (i < source.length && source[i].isWhitespace()) i++
        return i < source.length && source[i] == '('
    }

    /** 一般字串的結尾（回傳結尾引號之後的位置），處理跳脫與 `${...}` 樣板。 */
    private fun stringEnd(source: String, from: Int): Int {
        var i = from
        while (i < source.length) {
            when {
                source[i] == '\\' -> i += 2
                source.startsWith("\${", i) -> i = templateEnd(source, i + 2)
                source[i] == '"' -> return i + 1
                else -> i++
            }
        }
        return source.length
    }

    /** `${...}` 的結尾，可能包含巢狀的大括號與字串。 */
    private fun templateEnd(source: String, from: Int): Int {
        var depth = 1
        var i = from
        while (i < source.length && depth > 0) {
            when (source[i]) {
                '{' -> { depth++; i++ }
                '}' -> { depth--; i++ }
                '"' -> i = stringEnd(source, i + 1)
                else -> i++
            }
        }
        return i
    }

    /** 字元常值（例如 '"'）的結尾，避免把其中的引號當成字串開頭。 */
    private fun charEnd(source: String, from: Int): Int {
        var i = from + 1
        while (i < source.length && source[i] != '\'') i += if (source[i] == '\\') 2 else 1
        return i + 1
    }
}
