package com.example.data

import android.content.Context
import android.graphics.Color
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.BrushType
import com.example.model.FingerMode
import com.example.model.GridType
import com.example.model.ToolSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "concepts_preferences")

data class PersistentWheelPreferences(
    val activeSlotIndex: Int = 0,
    val toolSlots: List<ToolSlot> = emptyList(),
    val gridType: GridType = GridType.DOT,
    val canvasBgColor: Int = Color.parseColor("#15161C"),
    val angleSnapping: Boolean = true,
    val fingerMode: FingerMode = FingerMode.PAN,
    val pressureCurve: Float = 0.5f,
    val wheelOffsetX: Float = 16f,
    val wheelOffsetY: Float = 120f
)

/**
 * Android Jetpack Preferences DataStore repository for persistent storage of:
 * - Active tool slot index & per-slot brush parameters (size, opacity, smoothing, color)
 * - Canvas background style & color
 * - S Pen pressure sensitivity calibration curve
 * - Angle snapping & finger navigation mode
 * - Floating tool wheel coordinate position
 *
 * Implements write-on-change disk serialization and cold-boot restoration.
 */
class DataStorePreferenceRepository private constructor(private val context: Context) {

    private val dataStore: DataStore<Preferences> = context.preferencesDataStore
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "DataStorePrefRepo"

        val KEY_ACTIVE_SLOT_INDEX = intPreferencesKey("active_slot_index")
        val KEY_TOOL_SLOTS_JSON = stringPreferencesKey("tool_slots_json")
        val KEY_GRID_TYPE = stringPreferencesKey("grid_type")
        val KEY_CANVAS_BG_COLOR = intPreferencesKey("canvas_bg_color")
        val KEY_ANGLE_SNAPPING = booleanPreferencesKey("angle_snapping")
        val KEY_FINGER_MODE = stringPreferencesKey("finger_mode")
        val KEY_PRESSURE_CURVE = floatPreferencesKey("pressure_curve")
        val KEY_WHEEL_POS_X = floatPreferencesKey("tool_wheel_x")
        val KEY_WHEEL_POS_Y = floatPreferencesKey("tool_wheel_y")

        val DEFAULT_TOOL_SLOTS = listOf(
            ToolSlot(0, BrushType.PEN, Color.parseColor("#FFFFFF"), 3.5f, 1.0f, 0.45f),
            ToolSlot(1, BrushType.SOFT_PENCIL, Color.parseColor("#CBD5E1"), 2.8f, 0.85f, 0.20f),
            ToolSlot(2, BrushType.HARD_PENCIL, Color.parseColor("#F8F9FA"), 1.4f, 0.95f, 0.10f),
            ToolSlot(3, BrushType.FOUNTAIN_PEN, Color.parseColor("#00E5FF"), 4.5f, 1.0f, 0.60f),
            ToolSlot(4, BrushType.LASSO, Color.parseColor("#00F5D4"), 2.0f, 1.0f, 0.0f),
            ToolSlot(5, BrushType.MARKER, Color.parseColor("#FFB703"), 22.0f, 0.50f, 0.25f),
            ToolSlot(6, BrushType.SLICE, Color.parseColor("#FF4D6D"), 2.5f, 1.0f, 0.0f),
            ToolSlot(7, BrushType.ERASER_HARD, Color.TRANSPARENT, 24.0f, 1.0f, 0.0f)
        )

        @Volatile
        private var INSTANCE: DataStorePreferenceRepository? = null

        fun getInstance(context: Context): DataStorePreferenceRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DataStorePreferenceRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Reactive preferences flow.
     */
    val preferencesFlow: Flow<PersistentWheelPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading DataStore preferences", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            mapPreferencesToData(prefs)
        }

    /**
     * Cold-boot blocking read: guarantees UI initializes with exact 1:1 saved state
     * from disk without resetting to defaults or screen flickering.
     */
    fun loadPreferencesBlocking(): PersistentWheelPreferences {
        return try {
            runBlocking(Dispatchers.IO) {
                val prefs = dataStore.data.first()
                mapPreferencesToData(prefs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed cold-boot read from DataStore, falling back to defaults", e)
            PersistentWheelPreferences(
                toolSlots = DEFAULT_TOOL_SLOTS
            )
        }
    }

    private fun mapPreferencesToData(prefs: Preferences): PersistentWheelPreferences {
        val activeSlotIndex = prefs[KEY_ACTIVE_SLOT_INDEX] ?: 0
        val slotsJson = prefs[KEY_TOOL_SLOTS_JSON]
        val toolSlots = parseToolSlotsJson(slotsJson)

        val gridName = prefs[KEY_GRID_TYPE] ?: GridType.DOT.name
        val gridType = try { GridType.valueOf(gridName) } catch (e: Exception) { GridType.DOT }

        val canvasBgColor = prefs[KEY_CANVAS_BG_COLOR] ?: Color.parseColor("#15161C")
        val angleSnapping = prefs[KEY_ANGLE_SNAPPING] ?: true

        val fingerModeName = prefs[KEY_FINGER_MODE] ?: FingerMode.PAN.name
        val fingerMode = try { FingerMode.valueOf(fingerModeName) } catch (e: Exception) { FingerMode.PAN }

        val pressureCurve = prefs[KEY_PRESSURE_CURVE] ?: 0.5f
        val wheelX = prefs[KEY_WHEEL_POS_X] ?: 16f
        val wheelY = prefs[KEY_WHEEL_POS_Y] ?: 120f

        return PersistentWheelPreferences(
            activeSlotIndex = activeSlotIndex.coerceIn(0, toolSlots.size - 1),
            toolSlots = toolSlots,
            gridType = gridType,
            canvasBgColor = canvasBgColor,
            angleSnapping = angleSnapping,
            fingerMode = fingerMode,
            pressureCurve = pressureCurve,
            wheelOffsetX = wheelX,
            wheelOffsetY = wheelY
        )
    }

    /**
     * Asynchronously saves active tool slot index.
     */
    fun saveActiveSlotIndex(index: Int) {
        scope.launch {
            dataStore.edit { it[KEY_ACTIVE_SLOT_INDEX] = index }
        }
    }

    /**
     * Asynchronously serializes the full tool slots list to DataStore JSON.
     */
    fun saveToolSlots(slots: List<ToolSlot>) {
        val jsonString = serializeToolSlotsToJson(slots)
        scope.launch {
            dataStore.edit { it[KEY_TOOL_SLOTS_JSON] = jsonString }
        }
    }

    /**
     * Asynchronously writes all wheel preferences simultaneously.
     */
    fun saveFullPreferences(prefs: PersistentWheelPreferences) {
        val slotsJson = serializeToolSlotsToJson(prefs.toolSlots)
        scope.launch {
            dataStore.edit { p ->
                p[KEY_ACTIVE_SLOT_INDEX] = prefs.activeSlotIndex
                p[KEY_TOOL_SLOTS_JSON] = slotsJson
                p[KEY_GRID_TYPE] = prefs.gridType.name
                p[KEY_CANVAS_BG_COLOR] = prefs.canvasBgColor
                p[KEY_ANGLE_SNAPPING] = prefs.angleSnapping
                p[KEY_FINGER_MODE] = prefs.fingerMode.name
                p[KEY_PRESSURE_CURVE] = prefs.pressureCurve
                p[KEY_WHEEL_POS_X] = prefs.wheelOffsetX
                p[KEY_WHEEL_POS_Y] = prefs.wheelOffsetY
            }
        }
    }

    fun saveGridType(grid: GridType) {
        scope.launch {
            dataStore.edit { it[KEY_GRID_TYPE] = grid.name }
        }
    }

    fun saveCanvasBgColor(color: Int) {
        scope.launch {
            dataStore.edit { it[KEY_CANVAS_BG_COLOR] = color }
        }
    }

    fun saveAngleSnapping(snapping: Boolean) {
        scope.launch {
            dataStore.edit { it[KEY_ANGLE_SNAPPING] = snapping }
        }
    }

    fun saveFingerMode(mode: FingerMode) {
        scope.launch {
            dataStore.edit { it[KEY_FINGER_MODE] = mode.name }
        }
    }

    fun savePressureCurve(curve: Float) {
        scope.launch {
            dataStore.edit { it[KEY_PRESSURE_CURVE] = curve }
        }
    }

    fun saveWheelPosition(x: Float, y: Float) {
        scope.launch {
            dataStore.edit {
                it[KEY_WHEEL_POS_X] = x
                it[KEY_WHEEL_POS_Y] = y
            }
        }
    }

    private fun serializeToolSlotsToJson(slots: List<ToolSlot>): String {
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
        return array.toString()
    }

    private fun parseToolSlotsJson(json: String?): List<ToolSlot> {
        if (json.isNullOrBlank()) return DEFAULT_TOOL_SLOTS
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<ToolSlot>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val brushName = obj.optString("brushType", BrushType.PEN.name)
                val brushType = try {
                    val parsed = BrushType.valueOf(brushName)
                    if (parsed == BrushType.WIRE) BrushType.FOUNTAIN_PEN else parsed
                } catch (e: Exception) {
                    BrushType.PEN
                }
                list.add(
                    ToolSlot(
                        id = obj.optInt("id", i),
                        brushType = brushType,
                        color = obj.optInt("color", Color.WHITE),
                        strokeWidth = obj.optDouble("strokeWidth", 3.5).toFloat(),
                        opacity = obj.optDouble("opacity", 1.0).toFloat(),
                        smoothing = obj.optDouble("smoothing", 0.45).toFloat()
                    )
                )
            }
            if (list.size == DEFAULT_TOOL_SLOTS.size) list else DEFAULT_TOOL_SLOTS
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tool slots JSON, using default slots", e)
            DEFAULT_TOOL_SLOTS
        }
    }
}
