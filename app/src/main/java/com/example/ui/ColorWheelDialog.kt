package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.CopicColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorWheelDialog(
    currentColor: Int,
    copicColors: List<CopicColor>,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColorInt by remember { mutableIntStateOf(currentColor) }
    val hsv = remember(selectedColorInt) {
        val array = FloatArray(3)
        android.graphics.Color.colorToHSV(selectedColorInt, array)
        array
    }

    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }

    fun updateFromHsv(h: Float, s: Float, v: Float) {
        hue = h
        sat = s
        value = v
        val newColor = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        selectedColorInt = newColor
        onColorSelected(newColor)
    }

    // Favorite / Recent COPIC markers arc
    val favoriteMarkers = remember {
        listOf(
            android.graphics.Color.parseColor("#111111"), // 100 Black
            android.graphics.Color.parseColor("#686E74"), // C-7
            android.graphics.Color.parseColor("#CBD0D4"), // C-3
            android.graphics.Color.parseColor("#6C4334"), // E29 Burnt Umber
            android.graphics.Color.parseColor("#9C6839"), // E37 Sepia
            android.graphics.Color.parseColor("#E62B34"), // R29 Lipstick Red
            android.graphics.Color.parseColor("#FA8223"), // YR04 Chrome Orange
            android.graphics.Color.parseColor("#FBD429"), // Y15 Cadmium Yellow
            android.graphics.Color.parseColor("#74B238"), // YG17 Grass Green
            android.graphics.Color.parseColor("#008C4A"), // G17 Forest Green
            android.graphics.Color.parseColor("#3FB1E5"), // B05 Robin's Egg Blue
            android.graphics.Color.parseColor("#005DA4"), // B29 Night Blue
            android.graphics.Color.parseColor("#6A2A80")  // V09 Violet
        )
    }

    // Color families
    val families = remember(copicColors) {
        copicColors.groupBy { it.family }
    }
    val familyNames = remember(families) {
        listOf("All") + families.keys.toList()
    }
    var selectedFamilyIndex by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF161822),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF33384A)),
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("copic_color_wheel_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "COPIC Spectrum Wheel",
                            color = Color(0xFFF1F5F9),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Standard Architectural & Design Palette",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Active Color preview row & Quick Palette Arc
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(selectedColorInt))
                            .border(2.5.dp, Color(0xFFE2E8F0), CircleShape)
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    val hex = String.format("#%06X", 0xFFFFFF and selectedColorInt)
                    Column {
                        Text(
                            text = hex,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "R: ${android.graphics.Color.red(selectedColorInt)} " +
                                    "G: ${android.graphics.Color.green(selectedColorInt)} " +
                                    "B: ${android.graphics.Color.blue(selectedColorInt)}",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Recent/Favorite Palette Arc
                Text(
                    text = "Quick Architectural Palette",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (c in favoriteMarkers) {
                        val isSelected = (c == selectedColorInt)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(c))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color(0xFF3B4054),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    selectedColorInt = c
                                    val arr = FloatArray(3)
                                    android.graphics.Color.colorToHSV(c, arr)
                                    hue = arr[0]
                                    sat = arr[1]
                                    value = arr[2]
                                    onColorSelected(c)
                                }
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = if (c == android.graphics.Color.parseColor("#FBD429") || c == android.graphics.Color.WHITE) Color.Black else Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Precision Sliders (Hue, Saturation, Brightness)
                Text("Hue: ${hue.toInt()}°", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                Slider(
                    value = hue,
                    onValueChange = { updateFromHsv(it, sat, value) },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF48CAE4)
                    ),
                    modifier = Modifier.height(28.dp)
                )

                Text("Saturation: ${(sat * 100).toInt()}%", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                Slider(
                    value = sat,
                    onValueChange = { updateFromHsv(hue, it, value) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF0077B6)
                    ),
                    modifier = Modifier.height(28.dp)
                )

                Text("Brightness: ${(value * 100).toInt()}%", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                Slider(
                    value = value,
                    onValueChange = { updateFromHsv(hue, sat, it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF023E8A)
                    ),
                    modifier = Modifier.height(28.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Family Filter Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedFamilyIndex,
                    containerColor = Color(0xFF1E212E),
                    contentColor = Color(0xFF48CAE4),
                    edgePadding = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    familyNames.forEachIndexed { index, name ->
                        Tab(
                            selected = (selectedFamilyIndex == index),
                            onClick = { selectedFamilyIndex = index },
                            text = {
                                Text(
                                    text = name,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedFamilyIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // COPIC Swatches Grid
                val selectedFamily = familyNames[selectedFamilyIndex]
                val displayedFamilies = if (selectedFamily == "All") families else families.filterKeys { it == selectedFamily }

                for ((fam, swatches) in displayedFamilies) {
                    Text(
                        text = fam,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        for (c in swatches) {
                            val isSelected = (c.colorInt == selectedColorInt)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedColorInt = c.colorInt
                                        val arr = FloatArray(3)
                                        android.graphics.Color.colorToHSV(c.colorInt, arr)
                                        hue = arr[0]
                                        sat = arr[1]
                                        value = arr[2]
                                        onColorSelected(c.colorInt)
                                    }
                                    .padding(2.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(c.colorInt))
                                        .border(
                                            if (isSelected) 2.5.dp else 1.dp,
                                            if (isSelected) Color.White else Color(0xFF475569),
                                            CircleShape
                                        )
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = if (c.family.contains("Warm") || c.name.contains("Yellow") || c.name.contains("White")) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = c.code,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        onColorSelected(selectedColorInt)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0077B6)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply & Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
