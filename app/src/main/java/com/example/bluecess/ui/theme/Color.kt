package com.example.bluecess.ui.theme

import androidx.compose.ui.graphics.Color

// Primary colors
val BluecessBlue = Color(0xFF2196F3)  // Primary action blue
val BluecessGreen = Color(0xFF4CAF50)  // Connected/enabled
val BluecessRed = Color(0xFFF44336)    // Disconnected/error
val BluecessGray = Color(0xFF9E9E9E)   // Inactive/disabled

// Background colors
val BackgroundWhite = Color(0xFFFFFFFF)
val SurfaceGray = Color(0xFFF5F5F5)
val CardGray = Color(0xFFEEEEEE)

// Text colors
val TextPrimary = Color(0xFF212121)
val TextSecondary = Color(0xFF757575)
val TextDisabled = Color(0xFFBDBDBD)

// Light Color Scheme
val BluecessLightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = BluecessBlue,
    secondary = BluecessGreen,
    tertiary = BluecessRed,
    background = BackgroundWhite,
    surface = SurfaceGray,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

// Dark Color Scheme
val BluecessDarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = BluecessBlue,
    secondary = BluecessGreen,
    tertiary = BluecessRed,
    background = Color.Black,
    surface = Color(0xFF121212),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

// Old colors (keep for compatibility)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)