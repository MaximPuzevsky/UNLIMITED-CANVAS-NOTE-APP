package com.example.ui

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color

/**
 * Color Spectrum Data structure for Concepts-inspired Color Wheel Torus.
 *
 * Provides categorized warm-to-cool spectrum swatches:
 * - Organized with warm colors on one side (Yellows, Oranges, Reds, Earth/Browns)
 * - Cool colors on the other side (Violets, Blues, Cyans, Greens, Yellow-Greens)
 * - Neutral & Greys bridging the two sides (Black, Cool Greys, Warm Greys, White)
 * - Each color family has concentric bands from light/pastel to saturated/vibrant to deep/dark.
 */
data class SpectrumFamily(
    val id: String,
    val name: String,
    val isWarm: Boolean,
    // concentric bands from inside (light) to outside (dark/deep)
    val shades: List<Int>
)

object ConceptsSpectrumPalette {

    val families: List<SpectrumFamily> = listOf(
        // === NEUTRALS & GRAYS (Top bridge) ===
        SpectrumFamily(
            id = "neutral",
            name = "Neutrals",
            isWarm = false,
            shades = listOf(
                AndroidColor.parseColor("#FFFFFF"),
                AndroidColor.parseColor("#E4E4E4"),
                AndroidColor.parseColor("#C8C8C8"),
                AndroidColor.parseColor("#9A9A9A"),
                AndroidColor.parseColor("#646464"),
                AndroidColor.parseColor("#282828"),
                AndroidColor.parseColor("#101010")
            )
        ),
        SpectrumFamily(
            id = "cool_gray",
            name = "Cool Grays",
            isWarm = false,
            shades = listOf(
                AndroidColor.parseColor("#F0F4F8"),
                AndroidColor.parseColor("#D9E2EC"),
                AndroidColor.parseColor("#BCCCDC"),
                AndroidColor.parseColor("#829AB1"),
                AndroidColor.parseColor("#486581"),
                AndroidColor.parseColor("#243B53"),
                AndroidColor.parseColor("#102A43")
            )
        ),
        SpectrumFamily(
            id = "warm_gray",
            name = "Warm Grays",
            isWarm = true,
            shades = listOf(
                AndroidColor.parseColor("#F7F5F2"),
                AndroidColor.parseColor("#E8E5DF"),
                AndroidColor.parseColor("#D2CDC4"),
                AndroidColor.parseColor("#ABA49A"),
                AndroidColor.parseColor("#7D756C"),
                AndroidColor.parseColor("#534C45"),
                AndroidColor.parseColor("#2C2621")
            )
        ),

        // === WARM COLORS SIDE (Yellows -> Oranges -> Reds -> Earth) ===
        SpectrumFamily(
            id = "yellows",
            name = "Yellows",
            isWarm = true,
            shades = listOf(
                AndroidColor.parseColor("#FFFDE7"),
                AndroidColor.parseColor("#FFF59D"),
                AndroidColor.parseColor("#FFEE58"),
                AndroidColor.parseColor("#FFD600"),
                AndroidColor.parseColor("#F57F17"),
                AndroidColor.parseColor("#C46200"),
                AndroidColor.parseColor("#8C4500")
            )
        ),
        SpectrumFamily(
            id = "amber_orange",
            name = "Ambers",
            isWarm = true,
            shades = listOf(
                AndroidColor.parseColor("#FFF8E1"),
                AndroidColor.parseColor("#FFE082"),
                AndroidColor.parseColor("#FFCA28"),
                AndroidColor.parseColor("#FFA000"),
                AndroidColor.parseColor("#FF6F00"),
                AndroidColor.parseColor("#C65100"),
                AndroidColor.parseColor("#8F3600")
            )
        ),
        SpectrumFamily(
            id = "pure_orange",
            name = "Oranges",
            isWarm = true,
            shades = listOf(
                AndroidColor.parseColor("#FFF3E0"),
                AndroidColor.parseColor("#FFCC80"),
                AndroidColor.parseColor("#FFA726"),
                AndroidColor.parseColor("#FB8C00"),
                AndroidColor.parseColor("#E65100"),
                AndroidColor.parseColor("#BF360C"),
                AndroidColor.parseColor("#7A1C00")
            )
        ),
        SpectrumFamily(
            id = "coral_red",
            name = "Coral & Flame",
            isWarm = true,
            shades = listOf(
                AndroidColor.parseColor("#FBE9E7"),
                AndroidColor.parseColor("#FFAB91"),
                AndroidColor.parseColor("#FF7043"),
                AndroidColor.parseColor("#F4511E"),
                AndroidColor.parseColor("#D84315"),
                AndroidColor.parseColor("#BF360C"),
                AndroidColor.parseColor("#6F1600")
            )
        ),
        SpectrumFamily(
            id = "crimson_red",
            name = "Reds",
            isWarm = true,
            shades = listOf(
                AndroidColor.parseColor("#FFEBEE"),
                AndroidColor.parseColor("#EF9A9A"),
                AndroidColor.parseColor("#E57373"),
                AndroidColor.parseColor("#E62B34"),
                AndroidColor.parseColor("#C62828"),
                AndroidColor.parseColor("#B71C1C"),
                AndroidColor.parseColor("#5C0A0A")
            )
        ),
        SpectrumFamily(
            id = "earth_sepia",
            name = "Earth & Sepia",
            isWarm = true,
            shades = listOf(
                AndroidColor.parseColor("#EFEBE9"),
                AndroidColor.parseColor("#D7CCC8"),
                AndroidColor.parseColor("#BCAAA4"),
                AndroidColor.parseColor("#8D6E63"),
                AndroidColor.parseColor("#6C4334"),
                AndroidColor.parseColor("#4E342E"),
                AndroidColor.parseColor("#2B1713")
            )
        ),
        SpectrumFamily(
            id = "magenta_rose",
            name = "Rose & Magenta",
            isWarm = true,
            shades = listOf(
                AndroidColor.parseColor("#FCE4EC"),
                AndroidColor.parseColor("#F48FB1"),
                AndroidColor.parseColor("#F06292"),
                AndroidColor.parseColor("#E91E63"),
                AndroidColor.parseColor("#C2185B"),
                AndroidColor.parseColor("#880E4F"),
                AndroidColor.parseColor("#4A042A")
            )
        ),

        // === COOL COLORS SIDE (Violets -> Blues -> Cyans -> Greens) ===
        SpectrumFamily(
            id = "violet_purple",
            name = "Violets",
            isWarm = false,
            shades = listOf(
                AndroidColor.parseColor("#F3E5F5"),
                AndroidColor.parseColor("#CE93D8"),
                AndroidColor.parseColor("#BA68C8"),
                AndroidColor.parseColor("#9C27B0"),
                AndroidColor.parseColor("#7B1FA2"),
                AndroidColor.parseColor("#4A148C"),
                AndroidColor.parseColor("#26084D")
            )
        ),
        SpectrumFamily(
            id = "deep_indigo",
            name = "Indigos",
            isWarm = false,
            shades = listOf(
                AndroidColor.parseColor("#E8EAF6"),
                AndroidColor.parseColor("#9FA8DA"),
                AndroidColor.parseColor("#5C6BC0"),
                AndroidColor.parseColor("#3F51B5"),
                AndroidColor.parseColor("#283593"),
                AndroidColor.parseColor("#1A237E"),
                AndroidColor.parseColor("#0C1147")
            )
        ),
        SpectrumFamily(
            id = "ocean_blue",
            name = "Blues",
            isWarm = false,
            shades = listOf(
                AndroidColor.parseColor("#E3F2FD"),
                AndroidColor.parseColor("#90CAF9"),
                AndroidColor.parseColor("#42A5F5"),
                AndroidColor.parseColor("#1E88E5"),
                AndroidColor.parseColor("#1565C0"),
                AndroidColor.parseColor("#0D47A1"),
                AndroidColor.parseColor("#062456")
            )
        ),
        SpectrumFamily(
            id = "cyan_turquoise",
            name = "Cyans & Aquas",
            isWarm = false,
            shades = listOf(
                AndroidColor.parseColor("#E0F7FA"),
                AndroidColor.parseColor("#80DEEA"),
                AndroidColor.parseColor("#26C6DA"),
                AndroidColor.parseColor("#00ACC1"),
                AndroidColor.parseColor("#00838F"),
                AndroidColor.parseColor("#004D40"),
                AndroidColor.parseColor("#002A24")
            )
        ),
        SpectrumFamily(
            id = "teal_mint",
            name = "Teals",
            isWarm = false,
            shades = listOf(
                AndroidColor.parseColor("#E0F2F1"),
                AndroidColor.parseColor("#80CBC4"),
                AndroidColor.parseColor("#26A69A"),
                AndroidColor.parseColor("#00897B"),
                AndroidColor.parseColor("#00695C"),
                AndroidColor.parseColor("#004D40"),
                AndroidColor.parseColor("#002822")
            )
        ),
        SpectrumFamily(
            id = "forest_green",
            name = "Forest Greens",
            isWarm = false,
            shades = listOf(
                AndroidColor.parseColor("#E8F5E9"),
                AndroidColor.parseColor("#A5D6A7"),
                AndroidColor.parseColor("#66BB6A"),
                AndroidColor.parseColor("#388E3C"),
                AndroidColor.parseColor("#2E7D32"),
                AndroidColor.parseColor("#1B5E20"),
                AndroidColor.parseColor("#0A350F")
            )
        ),
        SpectrumFamily(
            id = "lime_green",
            name = "Lime & Chartreuse",
            isWarm = false,
            shades = listOf(
                AndroidColor.parseColor("#F9FBE7"),
                AndroidColor.parseColor("#E6EE9C"),
                AndroidColor.parseColor("#D4E157"),
                AndroidColor.parseColor("#AFB42B"),
                AndroidColor.parseColor("#827717"),
                AndroidColor.parseColor("#5A520E"),
                AndroidColor.parseColor("#342E05")
            )
        )
    )

    /**
     * Look up closest shade or representative family for a given color int.
     */
    fun getRepresentativeColor(familyIndex: Int, shadeIndex: Int): Int {
        val family = families.getOrNull(familyIndex) ?: families.first()
        return family.shades.getOrNull(shadeIndex) ?: family.shades.first()
    }
}
