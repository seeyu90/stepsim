package com.seeyu90.stepsim.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 系統字型、不內嵌字型檔。內文最低 16sp（bodyLarge／bodyMedium 都是 16sp）。
// 會跳動的數字一律用等寬數字（tnum），見 displayLarge 與 [TabularNumbers]。

/** 套用等寬數字，避免步數更新時寬度跳動。 */
const val TabularNumbers = "tnum"

private val Base = TextStyle(fontFamily = FontFamily.Default)

val Typography = Typography(
    /** 圓環中央的大數字；實際大小由 autoSize 在上限內縮放。 */
    displayLarge = Base.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 56.sp,
        lineHeight = 64.sp,
        fontFeatureSettings = TabularNumbers,
    ),
    headlineSmall = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleMedium = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = Base.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = Base.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = Base.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
)
