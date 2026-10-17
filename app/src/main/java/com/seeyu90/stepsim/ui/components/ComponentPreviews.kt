package com.seeyu90.stepsim.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.seeyu90.stepsim.R
import com.seeyu90.stepsim.ui.theme.StepSimTheme

// 共用元件的 Preview；圓環的壓力測試（目標 100,000、已存入 99,999）確認數字在大字體下不超出圓環。

@Composable
private fun Frame(content: @Composable () -> Unit) = StepSimTheme {
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
private fun StressRing(size: StepRingSize = StepRingSize.FULL) = StepRing(
    progress = 0.99999f,
    value = "99,999",
    caption = stringResource(R.string.unit_steps),
    contentDescription = "",
    size = size,
)

@Preview(name = "圓環 · 壓力測試 99,999", showBackground = true)
@Composable
private fun RingStressPreview() = Frame { StressRing() }

@Preview(name = "圓環 · 壓力測試 · 字體 1.5 倍", showBackground = true, fontScale = 1.5f)
@Composable
private fun RingStress15Preview() = Frame { StressRing() }

@Preview(name = "圓環 · 壓力測試 · 字體 2.0 倍", showBackground = true, fontScale = 2f)
@Composable
private fun RingStress20Preview() = Frame { StressRing() }

@Preview(name = "圓環 · 壓力測試 · 小螢幕 · 字體 2.0 倍", showBackground = true, fontScale = 2f, widthDp = 320, heightDp = 568)
@Composable
private fun RingStressSmallPreview() = Frame { StressRing() }

@Preview(name = "圓環 · 準備（176dp）· 字體 2.0 倍", showBackground = true, fontScale = 2f)
@Composable
private fun RingCompactPreview() = Frame {
    StepRing(0f, "0", stringResource(R.string.unit_steps), "", size = StepRingSize.COMPACT)
}

@Preview(name = "圓環 · 準備（176dp）· 壓力測試 · 字體 2.0 倍", showBackground = true, fontScale = 2f)
@Composable
private fun RingCompactStressPreview() = Frame { StressRing(StepRingSize.COMPACT) }

@Preview(name = "圓環 · 失敗（灰色進度弧）", showBackground = true)
@Composable
private fun RingFailedPreview() = Frame {
    StepRing(0.61f, "3,050", stringResource(R.string.unit_steps), "", failed = true)
}

@Preview(name = "圓環 · 深色", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RingDarkPreview() = Frame {
    StepRing(0.42f, "2,100", stringResource(R.string.unit_steps), "")
}
