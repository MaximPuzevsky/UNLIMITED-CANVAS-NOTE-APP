package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import com.example.model.BrushType
import com.example.model.FingerMode
import com.example.model.GridType
import com.example.model.ToolSlot
import org.json.JSONArray
import org.json.JSONObject

/**
 * State Retention Engine: Persists user settings, brush configurations, tool wheel slots,
 * active colors, smoothing, and canvas paper backgrounds.
 */
class UserSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("concepts_user_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACTIVE_SLOT = "active_slot_index"
        private const val KEY_ACTIVE_COLOR = "active_color"
        private const val KEY_ACTIVE_BRUSH = "active_brush_type"
        private const val KEY_ACTIVE_SIZE = "active_stroke_width"
        private const val KEY_ACTIVE_OPACITY = "active_opacity"
        private const val KEY_ACTIVE_SMOOTHING = "active_smoothing"
        private const val KEY_GRID_TYPE = "grid_type"
        private const val KEY_BG_COLOR = "canvas_bg_color"
        private const val KEY_FINGER_MODE = "finger_mode"
        private const val KEY_TOOL_SLOTS = "tool_slots_json"

        val DEFAULT_BG_COLOR = Color.parseColor("#15161C")
    }

    fun saveActiveSlotIndex(index: Int) {
        prefs.edit().putInt(KEY_ACTIVE_SLOT, index).apply()
    }

    fun getActiveSlotIndex(): Int = prefs.getInt(KEY_ACTIVE_SLOT, 0)

    fun saveActiveColor(color: Int) {
        prefs.edit().putInt(KEY_ACTIVE_COLOR, color).apply()
    }

    fun getActiveColor(): Int = prefs.getInt(KEY_ACTIVE_COLOR, Color.WHITE)

    fun saveActiveBrushType(type: BrushType) {
        prefs.edit().putString(KEY_ACTIVE_BRUSH, type.name).apply()
    }

    fun getActiveBrushType(): BrushType {
        val name = prefs.getString(KEY_ACTIVE_BRUSH, BrushType.PEN.name)
        return try {
            BrushType.valueOf(name ?: BrushType.PEN.name)
        } catch (e: Exception) {
            BrushType.PEN
        }
    }

    fun saveActiveSize(size: Float) {
        prefs.edit().putFloat(KEY_ACTIVE_SIZE, size).apply()
    }

    fun getActiveSize(): Float = prefs.getFloat(KEY_ACTIVE_SIZE, 3.5f)

    fun saveActiveOpacity(opacity: Float) {
        prefs.edit().putFloat(KEY_ACTIVE_OPACITY, opacity).apply()
    }

    fun getActiveOpacity(): Float = prefs.getFloat(KEY_ACTIVE_OPACITY, 1.0f)

    fun saveActiveSmoothing(smoothing: Float) {
        prefs.edit().putFloat(KEY_ACTIVE_SMOOTHING, smoothing).apply()
    }

    fun getActiveSmoothing(): Float = prefs.getFloat(KEY_ACTIVE_SMOOTHING, 0.45f)

    fun saveGridType(gridType: GridType) {
        prefs.edit().putString(KEY_GRID_TYPE, gridType.name).apply()
    }

    fun getGridType(): GridType {
        val name = prefs.getString(KEY_GRID_TYPE, GridType.DOT.name)
        return try {
            GridType.valueOf(name ?: GridType.DOT.name)
        } catch (e: Exception) {
            GridType.DOT
        }
    }

    fun saveBackgroundColor(color: Int) {
        prefs.edit().putInt(KEY_BG_COLOR, color).apply()
    }

    fun getBackgroundColor(): Int = prefs.getInt(KEY_BG_COLOR, DEFAULT_BG_COLOR)

    fun saveFingerMode(fingerMode: FingerMode) {
        prefs.edit().putString(KEY_FINGER_MODE, fingerMode.name).apply()
    }

    fun getFingerMode(): FingerMode {
        val name = prefs.getString(KEY_FINGER_MODE, FingerMode.PAN.name)
        return try {
            FingerMode.valueOf(name ?: FingerMode.PAN.name)
        } catch (e: Exception) {
            FingerMode.PAN
        }
    }

    fun saveToolSlots(slots: List<ToolSlot>) {
        val array = JSONArray()
        for (slot in slots) {
            val obj = JSONObject().apply {
                put("id", slot.id)
                put("brushType", slot.brushType.name)
                put("color", slot.color)
                put("strokeWidth", slot.strokeWidth.toDouble())
                put("opacity", slot.opacity.toDouble())
                put("smoothing", slot.smoothing.toDouble())
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_TOOL_SLOTS, array.toString()).apply()
    }

    fun getToolSlots(defaultSlots: List<ToolSlot>): List<ToolSlot> {
        val json = prefs.getString(KEY_TOOL_SLOTS, null) ?: return defaultSlots
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<ToolSlot>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val brushType = try {
                    val type = BrushType.valueOf(obj.getString("brushType"))
                    // Exclude WIRE if legacy saved
                    if (type == BrushType.WIRE) BrushType.FOUNTAIN_PEN else type
                } catch (e: Exception) {
                    BrushType.PEN
                }
                list.add(
                    ToolSlot(
                        id = obj.getInt("id"),
                        brushType = brushType,
                        color = obj.getInt("color"),
                        strokeWidth = obj.getDouble("strokeWidth").toFloat(),
                        opacity = obj.getDouble("opacity").toFloat(),
                        smoothing = obj.getDouble("smoothing").toFloat()
                    )
                )
            }
            if (list.size == defaultSlots.size) list else defaultSlots
        } catch (e: Exception) {
            defaultSlots
        }
    }
}
