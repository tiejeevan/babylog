package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Light Palette - Warm BabyCare Theme
val BabyCoralPrimary = Color(0xFFFF6F59)
val BabyCoralOnPrimary = Color(0xFFFFFFFF)
val BabyCoralContainer = Color(0xFFFFDBCD)
val BabyCoralOnContainer = Color(0xFF3B0900)

val BabyTealSecondary = Color(0xFF00897B)
val BabyTealOnSecondary = Color(0xFFFFFFFF)
val BabyTealContainer = Color(0xFFB2DFDB)
val BabyTealOnContainer = Color(0xFF00201A)

val BabyAmberTertiary = Color(0xFFFFB300)
val BabyAmberContainer = Color(0xFFFFECB3)
val BabyAmberOnContainer = Color(0xFF261A00)

val WarmBackground = Color(0xFFFAF7F2)
val WarmSurface = Color(0xFFFFFFFF)
val WarmSurfaceVariant = Color(0xFFF4EFEA)

// Custom Category Accent Colors for Activities
val FeedingColor = Color(0xFFFF7043)
val SleepColor = Color(0xFF7E57C2)
val DiaperColor = Color(0xFF26A69A)
val PumpingColor = Color(0xFF42A5F5)
val MedicineColor = Color(0xFFEC407A)
val HealthColor = Color(0xFFAB47BC)
val TummyTimeColor = Color(0xFF66BB6A)
val MilestoneColor = Color(0xFFFFA726)

// Dark Palette
val BabyCoralPrimaryDark = Color(0xFFFFB5A0)
val BabyTealSecondaryDark = Color(0xFF80CBC4)
val BabyAmberTertiaryDark = Color(0xFFFFE082)
val DarkBackground = Color(0xFF191210)
val DarkSurface = Color(0xFF241C1A)
val DarkSurfaceVariant = Color(0xFF352B28)

fun parseHexColor(colorHex: String?, fallback: Color = Color(0xFFFF7043)): Color {
    if (colorHex.isNullOrBlank()) return fallback
    return try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: Throwable) {
        fallback
    }
}

