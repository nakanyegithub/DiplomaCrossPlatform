package ru.zona.app.core.design

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Бренд-палитра Zona: глубокий индиго + тёплый акцент.
private val BrandIndigo = Color(0xFF4C5CE0)
private val BrandIndigoDark = Color(0xFF8B97FF)
private val BrandAccent = Color(0xFFFF7A59)

val ZonaLightColors =
    lightColorScheme(
        primary = BrandIndigo,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE2E5FF),
        onPrimaryContainer = Color(0xFF101A57),
        secondary = BrandAccent,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFE0D6),
        onSecondaryContainer = Color(0xFF52160A),
        background = Color(0xFFF7F8FC),
        onBackground = Color(0xFF14151A),
        surface = Color.White,
        onSurface = Color(0xFF14151A),
        surfaceVariant = Color(0xFFEDEFF5),
        onSurfaceVariant = Color(0xFF595D6B),
        outline = Color(0xFFC3C7D2),
        error = Color(0xFFD92D20),
        onError = Color.White,
    )

val ZonaDarkColors =
    darkColorScheme(
        primary = BrandIndigoDark,
        onPrimary = Color(0xFF101A57),
        primaryContainer = Color(0xFF2B379C),
        onPrimaryContainer = Color(0xFFE2E5FF),
        secondary = BrandAccent,
        onSecondary = Color(0xFF3A0F06),
        secondaryContainer = Color(0xFF7A2E1A),
        onSecondaryContainer = Color(0xFFFFE0D6),
        background = Color(0xFF101117),
        onBackground = Color(0xFFECEDF2),
        surface = Color(0xFF181A22),
        onSurface = Color(0xFFECEDF2),
        surfaceVariant = Color(0xFF272A35),
        onSurfaceVariant = Color(0xFFB7BBC8),
        outline = Color(0xFF454959),
        error = Color(0xFFFF6B5E),
        onError = Color(0xFF3A0A06),
    )
