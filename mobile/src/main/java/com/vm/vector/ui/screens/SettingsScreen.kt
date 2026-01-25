package com.vm.vector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.VectorTheme
import com.vm.vector.ui.viewmodel.SettingsViewModel
import com.vm.vector.ui.viewmodel.SettingsViewModelFactory

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.showSnackbar) {
        if (uiState.showSnackbar) {
            snackbarHostState.showSnackbar(uiState.snackbarMessage)
            viewModel.dismissSnackbar()
        }
    }

    VectorTheme {
        Scaffold(
            containerColor = PureWhite,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            val layoutDirection = LocalLayoutDirection.current
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PureWhite)
                    .padding(
                        start = paddingValues.calculateLeftPadding(layoutDirection),
                        end = paddingValues.calculateRightPadding(layoutDirection),
                        bottom = paddingValues.calculateBottomPadding()
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.permissionRequired) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OffWhite),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Permission Required",
                                style = MaterialTheme.typography.titleMedium,
                                color = PureBlack,
                            )
                            Text(
                                text = "Drive access needs your consent. The permission screen should open — grant access, then connection will be validated again.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureBlack,
                            )
                        }
                    }
                }
                if (uiState.isLoading || uiState.isValidating) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = ElectricBlue,
                    )
                }
                OutlinedTextField(
                    value = uiState.folderId,
                    onValueChange = viewModel::updateFolderId,
                    label = { Text("Data folder ID (Root)", color = PureBlack) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading && !uiState.isValidating,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PureBlack,
                        unfocusedTextColor = PureBlack,
                        focusedLabelColor = PureBlack,
                        unfocusedLabelColor = PureBlack,
                        focusedBorderColor = NavyDeep,
                        unfocusedBorderColor = PureBlack,
                    ),
                )

                Button(
                    onClick = viewModel::saveFolderId,
                    enabled = !uiState.isLoading && !uiState.isValidating,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                ) {
                    Text(if (uiState.isLoading) "Saving…" else "Save", color = PureWhite)
                }

                Button(
                    onClick = viewModel::validateConnection,
                    enabled = !uiState.isLoading && !uiState.isValidating,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                ) {
                    Text(if (uiState.isValidating) "Validating…" else "Validate Connection", color = PureWhite)
                }
            }
        }
    }
}
