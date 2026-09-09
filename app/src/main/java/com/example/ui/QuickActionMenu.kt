package com.example.ui

import android.graphics.PointF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Radial / context menu triggered when S Pen dwells in one spot on screen.
 * Provides instant Paste, Import Image, and selection shortcuts.
 */
@Composable
fun QuickActionMenu(
    anchorPoint: PointF?,
    onPaste: () -> Unit,
    onImportImage: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (anchorPoint == null) return

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    (anchorPoint.x - 75f).toInt().coerceAtLeast(10),
                    (anchorPoint.y - 120f).toInt().coerceAtLeast(10)
                )
            }
            .testTag("quick_action_dwell_menu")
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF1E202B).copy(alpha = 0.96f),
            shadowElevation = 14.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4C5065))
        ) {
            Column(
                modifier = Modifier.padding(6.dp)
            ) {
                QuickMenuItem(
                    icon = Icons.Default.ContentPaste,
                    title = "Paste",
                    onClick = {
                        onPaste()
                        onDismiss()
                    }
                )

                QuickMenuItem(
                    icon = Icons.Default.AddPhotoAlternate,
                    title = "Import Media",
                    onClick = {
                        onImportImage()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color(0xFFE2E8F0),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            color = Color(0xFFE2E8F0),
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}
