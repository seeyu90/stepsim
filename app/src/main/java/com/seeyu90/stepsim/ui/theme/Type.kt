package com.seeyu90.stepsim.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// 系統字型、不內嵌字型檔。內文最低 16sp（bodyLarge／bodyMedium／bodySmall 都是 16sp；
// bodySmall 是輸入框輔助文字與浮動標籤的預設字級，不覆寫的話只有 12sp）。
// 會跳動的數字一律用等寬數字（tnum），見 displayLarge 與 [TabularNumbers]。

/** 套用等寬數字，避免步數更新時寬度跳動。 */
const val TabularNumbers = "tnum"

private val Base = TextStyle(fontFamily = FontFamily.Default)

val Typography = Typography(
    /**
     * 圓環中央的大數字；實際大小由 autoSize 在上限內縮放。行高用 em，才會跟著縮小後的字級一起縮
     * （用 sp 的話行高固定，大字體時會把文字撐出圓環）。
     */
    displayLarge = Base.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 56.sp,
        lineHeight = 1.15.em,
        fontFeatureSettings = TabularNumbers,
    ),
    headlineSmall = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleMedium = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = Base.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = Base.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = Base.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = Base.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
)
