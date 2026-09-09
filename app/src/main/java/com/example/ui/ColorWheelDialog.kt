package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.CopicColor

/**
 * Concepts 1:1 Radial COPIC Color Wheel Dialog.
 * Replaces static rectangular dialogs with an authentic multi-tiered radial color wheel
 * that supports infinite 2-way touch rotation physics.
 */
@Composable
fun ColorWheelDialog(
    currentColor: Int,
    copicColors: List<CopicColor> = emptyList(),
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            CopicRadialColorWheel(
                selectedColor = currentColor,
                onColorSelected = onColorSelected,
                onClose = onDismiss
            )
        }
    }
}
