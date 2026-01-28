package com.vm.vector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vm.core.ui.theme.DeleteRed
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
                    enabled = !uiState.isValidating && !uiState.isResetting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = PureWhite,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (uiState.isLoading) "Saving…" else "Save", color = PureWhite)
                }

                Button(
                    onClick = viewModel::validateConnection,
                    enabled = !uiState.isLoading && !uiState.isResetting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                ) {
                    if (uiState.isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = PureWhite,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (uiState.isValidating) "Validating…" else "Validate Connection", color = PureWhite)
                }

                // Divider or spacing before reset button
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = viewModel::showPasswordDialog,
                    enabled = !uiState.isLoading && !uiState.isValidating && !uiState.isResetting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeleteRed,
                        contentColor = PureWhite
                    ),
                ) {
                    if (uiState.isResetting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = PureWhite,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Reset Database", color = PureWhite)
                }
            }
        }

        // Password dialog
        if (uiState.showPasswordDialog) {
            var password by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = viewModel::dismissPasswordDialog,
                title = {
                    Text("Enter Password", color = PureBlack)
                },
                text = {
                    Column {
                        Text(
                            "Please enter the password to reset the database.",
                            color = PureBlack,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password", color = PureBlack) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PureBlack,
                                unfocusedTextColor = PureBlack,
                                focusedLabelColor = PureBlack,
                                unfocusedLabelColor = PureBlack,
                                focusedBorderColor = DeleteRed,
                                unfocusedBorderColor = PureBlack,
                            ),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.verifyPasswordAndShowConfirmation(password)
                            password = ""
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = DeleteRed
                        ),
                        enabled = password.isNotBlank()
                    ) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.dismissPasswordDialog()
                            password = ""
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = PureBlack
                        )
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = PureWhite
            )
        }

        // Reset confirmation dialog
        if (uiState.showResetConfirmation) {
            AlertDialog(
                onDismissRequest = viewModel::dismissResetConfirmation,
                title = {
                    Text("Reset Database", color = PureBlack)
                },
                text = {
                    Text(
                        "This will delete all daily diet entries from the database. " +
                        "Presets and JSON files will not be affected.\n\n" +
                        "This action cannot be undone.",
                        color = PureBlack
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::resetDatabase,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = viewModel::dismissResetConfirmation,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = PureBlack
                        )
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = PureWhite
            )
        }
    }
}
