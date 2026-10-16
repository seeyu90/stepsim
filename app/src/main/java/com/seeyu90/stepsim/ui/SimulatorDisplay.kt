package com.seeyu90.stepsim.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// 主畫面的純顯示函式：不依賴 Compose 與 Android，直接以 JVM 單元測試驗證。

/** 步數加千分位，依 Locale 格式化（例如 zh-TW 為 1,000，de-DE 為 1.000）。 */
fun formatSteps(steps: Long, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(steps)

/** 時刻（例如最後存入時間），依 Locale 的短時間格式。 */
fun formatClockTime(instant: Instant, zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone))

/** 剩餘或預估時間的顯示單位；文字在畫面以 strings.xml 組出。 */
sealed interface RemainingTime {
    /** 剩餘分鐘為 0。 */
    data object Soon : RemainingTime
    data class Minutes(val minutes: Long) : RemainingTime
    /** 剛好整點，不顯示「0 分鐘」。 */
    data class Hours(val hours: Long) : RemainingTime
    data class HoursMinutes(val hours: Long, val minutes: Long) : RemainingTime

    companion object {
        fun of(totalMinutes: Long): RemainingTime {
            val minutes = totalMinutes.coerceAtLeast(0)
            val hours = minutes / 60
            val rest = minutes % 60
            return when {
                minutes == 0L -> Soon
                hours == 0L -> Minutes(rest)
                rest == 0L -> Hours(hours)
                else -> HoursMinutes(hours, rest)
            }
        }
    }
}

/** 底部固定的主要動作；一個畫面只有一個。 */
enum class BottomAction {
    START, PAUSE, RESUME, RETRY, GO_ALLOW, GO_CHECK, START_NEW,

    /** 權限檢查本身失敗：重新檢查（不是重送批次）。 */
    RECHECK,
}

/** 捲動區裡的次要操作（文字按鈕）。「結束本次模擬」一律先跳確認對話框。 */
enum class SecondaryAction { RETRY, END }

/** 「開始模擬」停用時，按鈕下方的原因。 */
enum class StartHint { NEED_GOAL }
