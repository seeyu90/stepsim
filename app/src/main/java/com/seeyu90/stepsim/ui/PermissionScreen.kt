package com.seeyu90.stepsim.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seeyu90.stepsim.R
import com.seeyu90.stepsim.health.HealthConnectAvailability

@Composable
fun PermissionScreen(
    modifier: Modifier = Modifier,
    viewModel: PermissionViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val launcher = rememberLauncherForActivityResult(remember { viewModel.permissionContract() }) {
        viewModel.refresh()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineSmall)

        if (state.checked) {
            Text(stringResource(availabilityText(state.availability)))

            when (state.availability) {
                HealthConnectAvailability.AVAILABLE -> {
                    Text(
                        text = stringResource(
                            if (state.granted) R.string.status_granted else R.string.status_not_granted
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Button(onClick = { launcher.launch(viewModel.permissions) }) {
                        Text(
                            stringResource(
                                if (state.granted) R.string.action_re_request_permission
                                else R.string.action_request_permission
                            )
                        )
                    }
                }

                HealthConnectAvailability.NEEDS_INSTALL,
                HealthConnectAvailability.NEEDS_UPDATE -> {
                    Button(onClick = { context.startActivity(viewModel.installIntent()) }) {
                        Text(stringResource(R.string.action_install_health_connect))
                    }
                }

                HealthConnectAvailability.NOT_SUPPORTED -> Unit
            }
        }
    }
}

private fun availabilityText(availability: HealthConnectAvailability): Int =
    when (availability) {
        HealthConnectAvailability.AVAILABLE -> R.string.availability_available
        HealthConnectAvailability.NEEDS_INSTALL -> R.string.availability_needs_install
        HealthConnectAvailability.NEEDS_UPDATE -> R.string.availability_needs_update
        HealthConnectAvailability.NOT_SUPPORTED -> R.string.availability_not_supported
    }
