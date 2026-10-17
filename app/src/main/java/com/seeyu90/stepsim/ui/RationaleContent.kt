package com.seeyu90.stepsim.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.seeyu90.stepsim.R
import com.seeyu90.stepsim.ui.components.IconTextRow
import com.seeyu90.stepsim.ui.components.InfoCard

/** 權限用途說明：StepSim 只「存入」步數，不讀取任何健康資料。 */
@Composable
fun RationaleContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.rationale_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.rationale_intro), style = MaterialTheme.typography.bodyLarge)
        InfoCard {
            IconTextRow(stringResource(R.string.rationale_write))
            IconTextRow(stringResource(R.string.rationale_privacy))
        }
    }
}
