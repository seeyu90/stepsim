package com.seeyu90.stepsim.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.seeyu90.stepsim.R

/** 「結束本次模擬」的確認：進度歸零，已存入 Health Connect 的步數保留。 */
@Composable
fun EndSimulationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.end_confirm_title)) },
        text = { Text(stringResource(R.string.end_confirm_body), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.end_confirm_yes), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.end_confirm_no)) }
        },
    )
}
