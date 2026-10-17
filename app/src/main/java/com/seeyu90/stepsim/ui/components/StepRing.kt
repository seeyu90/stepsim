package com.seeyu90.stepsim.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seeyu90.stepsim.ui.theme.LocalStepSimColors

/** 進度環尺寸：準備狀態（尚無進度）縮小，其餘用完整尺寸。 */
enum class StepRingSize(val diameter: Dp) { COMPACT(176.dp), FULL(240.dp) }

private val RingStroke = 16.dp

/** 數字與圓環內緣之間的留白，避免大字體時數字壓到環上。 */
private val RingInnerPadding = 12.dp

/**
 * 大圓環：從 12 點鐘方向順時針畫進度，中央是會依可用寬度自動縮小的大數字。
 *
 * 整個元件對無障礙服務只報 [contentDescription]（例如「進度 42%，已存入 2,100 步，目標 5,000 步」），
 * 不逐一讀出中央的數字與單位。
 *
 * @param progress 0f..1f，超出範圍會被截斷。
 * @param failed 失敗時進度弧改用 outline 灰色。
 */
@Composable
fun StepRing(
    progress: Float,
    value: String,
    caption: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: StepRingSize = StepRingSize.FULL,
    failed: Boolean = false,
) {
    val colors = LocalStepSimColors.current
    val track = colors.ringTrack
    val arc = if (failed) colors.ringProgressFailed else colors.ringProgress
    val sweep = 360f * progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .size(size.diameter)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = RingStroke.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            if (sweep > 0f) {
                drawArc(
                    arc, -90f, sweep, useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RingStroke + RingInnerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BasicText(
                text = value,
                style = MaterialTheme.typography.displayLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                // 上限即 displayLarge 的 56sp；放不下就逐步縮小，最小 20sp（字體放大時以縮放後的大小計算）
                autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 56.sp, stepSize = 2.sp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}
