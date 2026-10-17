package com.seeyu90.stepsim.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seeyu90.stepsim.R

/**
 * 授權頁外層：任何階段都有畫面上的返回與系統返回（不把使用者困在授權頁），
 * 並在授權成功後自動回到模擬頁。只讀取同一個 [PermissionViewModel] 的狀態。
 */
@Composable
fun PermissionRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionViewModel = viewModel(factory = PermissionViewModel.Factory),
) {
    BackHandler(onBack = onBack)

    LaunchedEffect(viewModel) {
        val tracker = PermissionAutoReturn()
        viewModel.state.collect { state ->
            if (tracker.shouldReturn(state)) onBack()
        }
    }

    Column(modifier) {
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .padding(start = 8.dp, top = 4.dp)
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.perm_back), style = MaterialTheme.typography.labelLarge)
        }
        PermissionScreen(onBack = onBack, modifier = Modifier.weight(1f), viewModel = viewModel)
    }
}

/**
 * 決定授權頁是否自動返回：本次進入授權頁後，先確認過「未授權」、之後變成「已授權」才返回。
 * 進入時本來就已授權（例如來重新授權）不會自動跳走；檢查中或讀取失敗的狀態不列入判斷。
 */
class PermissionAutoReturn {
    private var sawNotGranted = false

    fun shouldReturn(state: PermissionUiState): Boolean {
        if (!state.checked || state.refreshFailed) return false
        if (!state.granted) {
            sawNotGranted = true
            return false
        }
        return sawNotGranted
    }
}
