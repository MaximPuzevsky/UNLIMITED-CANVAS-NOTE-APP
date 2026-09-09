package com.example.data

import android.content.Context
import android.graphics.Color
import android.util.Log
import com.example.model.BrushType
import com.example.model.FingerMode
import com.example.model.GridType
import com.example.model.ToolSlot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Dedicated persistence manager for Concepts user wheel preferences,
 * instrument slot configurations, top bar states, and settings.
 * Saves directly to `user_wheel_preferences.json` in the app's files directory.
 */
data class TopBarPreferences(
    val gridType: GridType = GridType.DOT,
    val canvasBgColor: Int = Color.parseColor("#15161C"),
    val angleSnapping: Boolean = true,
    val fingerMode: FingerMode = FingerMode.PAN
)

data class SettingsPreferences(
    val pressureCurve: Float = 0.5f,
    val spenShortcut: String = "Eraser / Lasso Toggle"
)

data class WheelPreferencesData(
    val activeSlotIndex: Int = 0,
    val toolSlots: List<ToolSlot>,
    val topBar: TopBarPreferences = TopBarPreferences(),
    val settings: SettingsPreferences = SettingsPreferences()
)

class UserWheelPreferencesManager(private val context: Context) {

    private val preferencesFile = File(context.filesDir, PREF_FILE_NAME)

    companion object {
        private const val TAG = "UserWheelPrefs"
        const val PREF_FILE_NAME = "user_wheel_preferences.json"
    }

    /**
     * Saves entire wheel configurations, active tool states, top bar states,
     * and settings to user_wheel_preferences.json.
     */
    @Synchronized
    fun savePreferences(data: WheelPreferencesData) {
        try {
            val root = JSONObject()
            root.put("activeSlotIndex", data.activeSlotIndex)

            // 1. Tool Slots array
            val slotsArray = JSONArray()
            for (slot in data.toolSlots) {
                val slotObj = JSONObject().apply {
                    put("id", slot.id)
                    put("brushType", slot.brushType.name)
                    put("color", slot.color)
                    put("strokeWidth", slot.strokeWidth.toDouble())
                    put("opacity", slot.opacity.toDouble())
                    put("smoothing", slot.smoothing.toDouble())
                }
                slotsArray.put(slotObj)
            }
            root.put("toolSlots", slotsArray)

            // 2. Top bar / Tool bar state
            val topBarObj = JSONObject().apply {
                put("gridType", data.topBar.gridType.name)
                put("canvasBgColor", data.topBar.canvasBgColor)
                put("angleSnapping", data.topBar.angleSnapping)
                put("fingerMode", data.topBar.fingerMode.name)
            }
            root.put("topBarState", topBarObj)

            // 3. Settings preferences
            val settingsObj = JSONObject().apply {
                put("pressureCurve", data.settings.pressureCurve.toDouble())
                put("spenShortcut", data.settings.spenShortcut)
            }
            root.put("settingsPreferences", settingsObj)

            // Write atomically to user_wheel_preferences.json
            preferencesFile.writeText(root.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user_wheel_preferences.json", e)
        }
    }

    /**
     * Loads saved user preferences from user_wheel_preferences.json.
     * Returns fallback defaults if file doesn't exist or error occurs.
     */
    @Synchronized
    fun loadPreferences(defaultSlots: List<ToolSlot>): WheelPreferencesData {
        if (!preferencesFile.exists()) {
            return WheelPreferencesData(
                activeSlotIndex = 0,
                toolSlots = defaultSlots,
                topBar = TopBarPreferences(),
                settings = SettingsPreferences()
            )
        }

        return try {
            val content = preferencesFile.readText()
            val root = JSONObject(content)

            val activeSlotIndex = root.optInt("activeSlotIndex", 0)

            val toolSlots = mutableListOf<ToolSlot>()
            val slotsArray = root.optJSONArray("toolSlots")
            if (slotsArray != null) {
                for (i in 0 until slotsArray.length()) {
                    val obj = slotsArray.getJSONObject(i)
                    val brushName = obj.optString("brushType", BrushType.PEN.name)
                    val brushType = try {
                        val parsed = BrushType.valueOf(brushName)
                        if (parsed == BrushType.WIRE) BrushType.FOUNTAIN_PEN else parsed
                    } catch (e: Exception) {
                        BrushType.PEN
                    }
                    toolSlots.add(
                        ToolSlot(
                            id = obj.optInt("id", i),
                            brushType = brushType,
                            color = obj.optInt("color", Color.BLACK),
                            strokeWidth = obj.optDouble("strokeWidth", 3.0).toFloat(),
                            opacity = obj.optDouble("opacity", 1.0).toFloat(),
                            smoothing = obj.optDouble("smoothing", 0.15).toFloat()
                        )
                    )
                }
            }

            val finalSlots = if (toolSlots.size == defaultSlots.size) toolSlots else defaultSlots

            val topBarObj = root.optJSONObject("topBarState")
            val topBar = if (topBarObj != null) {
                val gridName = topBarObj.optString("gridType", GridType.DOT.name)
                val gridType = try { GridType.valueOf(gridName) } catch (e: Exception) { GridType.DOT }
                val bgColor = topBarObj.optInt("canvasBgColor", Color.parseColor("#15161C"))
                val angleSnapping = topBarObj.optBoolean("angleSnapping", true)
                val fingerModeName = topBarObj.optString("fingerMode", FingerMode.PAN.name)
                val fingerMode = try { FingerMode.valueOf(fingerModeName) } catch (e: Exception) { FingerMode.PAN }
                TopBarPreferences(gridType, bgColor, angleSnapping, fingerMode)
            } else {
                TopBarPreferences()
            }

            val settingsObj = root.optJSONObject("settingsPreferences")
            val settings = if (settingsObj != null) {
                val pressureCurve = settingsObj.optDouble("pressureCurve", 0.5).toFloat()
                val spenShortcut = settingsObj.optString("spenShortcut", "Eraser / Lasso Toggle")
                SettingsPreferences(pressureCurve, spenShortcut)
            } else {
                SettingsPreferences()
            }

            WheelPreferencesData(
                activeSlotIndex = activeSlotIndex.coerceIn(0, finalSlots.size - 1),
                toolSlots = finalSlots,
                topBar = topBar,
                settings = settings
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse user_wheel_preferences.json, using defaults", e)
            WheelPreferencesData(
                activeSlotIndex = 0,
                toolSlots = defaultSlots,
                topBar = TopBarPreferences(),
                settings = SettingsPreferences()
            )
        }
    }
}
