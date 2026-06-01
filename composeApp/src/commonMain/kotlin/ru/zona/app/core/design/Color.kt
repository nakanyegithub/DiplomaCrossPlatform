package ru.zona.app.core.design

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ░░ Космическая палитра Zona ░░
// Глубокий космос + неоновая туманность (фиолетово-голубой) + звёздное золото.
val CosmosViolet = Color(0xFF7C5CFF) // основной неон
val CosmosVioletBright = Color(0xFFA68BFF)
val CosmosCyan = Color(0xFF35E0F0) // акцент-голубой (звёзды/орбиты)
val CosmosMagenta = Color(0xFFFF5DA2) // акцент-розовый (туманность)
val CosmosGold = Color(0xFFFFC95C) // золото XP/наград

val SpaceVoid = Color(0xFF070912) // самый тёмный фон
val SpaceDeep = Color(0xFF0C1024) // фон экранов
val SpaceSurface = Color(0xFF141A33) // карточки
val SpaceSurfaceHi = Color(0xFF1E2747) // приподнятые поверхности
val SpaceOutline = Color(0xFF2C3760)
val StarWhite = Color(0xFFEAF0FF)
val StarDim = Color(0xFF9AA6CC)

val ZonaDarkColors =
    darkColorScheme(
        primary = CosmosViolet,
        onPrimary = Color(0xFF0A0620),
        primaryContainer = Color(0xFF2A2160),
        onPrimaryContainer = Color(0xFFE5DEFF),
        secondary = CosmosCyan,
        onSecondary = Color(0xFF002429),
        secondaryContainer = Color(0xFF123E45),
        onSecondaryContainer = Color(0xFFB7F6FF),
        tertiary = CosmosMagenta,
        onTertiary = Color(0xFF2D0017),
        background = SpaceDeep,
        onBackground = StarWhite,
        surface = SpaceSurface,
        onSurface = StarWhite,
        surfaceVariant = SpaceSurfaceHi,
        onSurfaceVariant = StarDim,
        outline = SpaceOutline,
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF2A0000),
    )

// Светлая тема оставлена как «дневной космос» — на случай предпочтения системы.
val ZonaLightColors =
    lightColorScheme(
        primary = Color(0xFF5B3FE0),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE5DEFF),
        onPrimaryContainer = Color(0xFF1B1148),
        secondary = Color(0xFF0E7C8A),
        onSecondary = Color.White,
        background = Color(0xFFF3F4FB),
        onBackground = Color(0xFF14151A),
        surface = Color.White,
        onSurface = Color(0xFF14151A),
        surfaceVariant = Color(0xFFE9EAF4),
        onSurfaceVariant = Color(0xFF565B70),
        outline = Color(0xFFC3C7D8),
        error = Color(0xFFD92D20),
        onError = Color.White,
    )
