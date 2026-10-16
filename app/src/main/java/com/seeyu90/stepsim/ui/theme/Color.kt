package com.seeyu90.stepsim.ui.theme

import androidx.compose.ui.graphics.Color

// 湖水綠品牌色。對比值（WCAG 2.x）見 docs/phase-9.5/plan.md §2.1；
// primary 文字放在 primaryContainer 上只有 4.43，不及格：容器內文字一律用 onPrimaryContainer。

// 淺色
val TealPrimaryLight = Color(0xFF00796B)
val OnTealPrimaryLight = Color(0xFFFFFFFF)
val TealContainerLight = Color(0xFFD5F0EC)
val OnTealContainerLight = Color(0xFF00201C)
val BackgroundLight = Color(0xFFF6FAF9)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF17201F)
val SurfaceVariantLight = Color(0xFFDAE5E2)
val OnSurfaceVariantLight = Color(0xFF3F4947)
val OutlineLight = Color(0xFF6F7977)
val OutlineVariantLight = Color(0xFFDCE5E3)
val ErrorLight = Color(0xFFB3261E)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFF9DEDC)
val OnErrorContainerLight = Color(0xFF410E0B)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF0F5F4)
val SurfaceContainerLight = Color(0xFFEAF0EE)
val SurfaceContainerHighLight = Color(0xFFE4EAE9)
val SurfaceContainerHighestLight = Color(0xFFDEE4E3)

// 深色
val TealPrimaryDark = Color(0xFF4DB6AC)
val OnTealPrimaryDark = Color(0xFF00201C)
val TealContainerDark = Color(0xFF00504A)
val OnTealContainerDark = Color(0xFFD5F0EC)
val BackgroundDark = Color(0xFF0F1514)
val SurfaceDark = Color(0xFF161D1C)
val OnSurfaceDark = Color(0xFFE0E8E6)
val SurfaceVariantDark = Color(0xFF3F4947)
val OnSurfaceVariantDark = Color(0xFFBEC9C6)
val OutlineDark = Color(0xFF899390)
val OutlineVariantDark = Color(0xFF2A3533)
val ErrorDark = Color(0xFFF2B8B5)
val OnErrorDark = Color(0xFF601410)
val ErrorContainerDark = Color(0xFF8C1D18)
val OnErrorContainerDark = Color(0xFFF9DEDC)
val SurfaceContainerLowestDark = Color(0xFF0A0F0E)
val SurfaceContainerLowDark = Color(0xFF161D1C)
val SurfaceContainerDark = Color(0xFF1A2120)
val SurfaceContainerHighDark = Color(0xFF252B2A)
val SurfaceContainerHighestDark = Color(0xFF2F3635)

/** 進度環軌道：淺色用 primaryContainer（與進度 4.43:1）；深色另設（與進度 5.20:1，primaryContainer 只有 3.83）。 */
val RingTrackLight = TealContainerLight
val RingTrackDark = Color(0xFF2A3533)
