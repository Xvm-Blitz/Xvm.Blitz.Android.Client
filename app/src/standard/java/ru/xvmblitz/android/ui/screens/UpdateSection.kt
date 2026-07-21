package ru.xvmblitz.android.ui.screens

import androidx.compose.runtime.Composable
import ru.xvmblitz.android.update.UpdateUiState

@Composable
fun UpdateSection(
    state: UpdateUiState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
) = Unit
