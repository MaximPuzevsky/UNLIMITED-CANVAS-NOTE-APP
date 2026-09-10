package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SelectionState

/**
 * Concepts-grade Floating Selection HUD with dark translucent pill geometry.
 * Positioned cleanly above bottom system navigation bar.
 * Actions: Copy, Delete, Mirror Left/Right, Mirror Top/Bottom, Recolor.
 */
@Composable
fun FloatingSelectionBar(
    selection: SelectionState,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onMirrorHorizontal: () -> Unit,
    onMirrorVertical: () -> Unit,
    onClearSelection: () -> Unit,
    onColorSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = selection.isNotEmpty,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 24.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF141620).copy(alpha = 0.94f),
            shadowElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF323648)),
            modifier = Modifier.testTag("floating_selection_bar")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                // Item count & status badge
                val count = selection.selectedStrokeIds.size + selection.selectedImageIds.size
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF222636),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = if (selection.isCopyPending) "Copied ($count)" else "$count selected",
                        color = if (selection.isCopyPending) Color(0xFF48CAE4) else Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Copy Action
                SelectionIconButton(
                    icon = Icons.Default.ContentCopy,
                    label = "Copy",
                    testTag = "selection_copy_button",
                    onClick = onCopy
                )

                // Mirror Left/Right Action
                SelectionIconButton(
                    icon = Icons.Default.Flip,
                    label = "Flip H",
                    testTag = "selection_mirror_h_button",
                    onClick = onMirrorHorizontal
                )

                // Mirror Top/Bottom Action
                SelectionIconButton(
                    icon = Icons.Default.SwapVert,
                    label = "Flip V",
                    testTag = "selection_mirror_v_button",
                    onClick = onMirrorVertical
                )

                // Delete Action
                SelectionIconButton(
                    icon = Icons.Default.DeleteOutline,
                    label = "Delete",
                    tint = Color(0xFFFF6B6B),
                    testTag = "selection_delete_button",
                    onClick = onDelete
                )

                // Quick Recolor Swatches (if vector strokes are selected)
                if (selection.selectedStrokeIds.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = 1.dp, height = 24.dp)
                            .background(Color(0xFF33384A))
                    )

                    val quickColors = listOf(
                        android.graphics.Color.parseColor("#000000"),
                        android.graphics.Color.parseColor("#E62B34"),
                        android.graphics.Color.parseColor("#005DA4"),
                        android.graphics.Color.parseColor("#008C4A"),
                        android.graphics.Color.parseColor("#FBD429"),
                        android.graphics.Color.parseColor("#FFFFFF")
                    )

                    for (c in quickColors) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(1.dp, Color(0xFF4A4E62), CircleShape)
                                .clickable { onColorSelect(c) }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Close / Deselect Button (>=48dp touch target)
                IconButton(
                    onClick = onClearSelection,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("close_selection_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Deselect",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionIconButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    tint: Color = Color(0xFFF1F5F9),
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = tint.copy(alpha = 0.85f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}
