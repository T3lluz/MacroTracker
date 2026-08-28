package com.macrotracker.ui.theme

import androidx.compose.ui.graphics.Color

// Matches the original React Native color scheme
val Background = Color(0xFF080D18)
val Surface = Color(0xFF111827)
val Primary = Color(0xFF4F7CFF)
val PrimaryVariant = Color(0xFF3D67DB)
val Secondary = Color(0xFF22C55E)
val Error = Color(0xFFEF4444)
val TextPrimary = Color(0xFFEAF0FB)
val TextSecondary = Color(0xFF99A8C2)
val Border = Color(0xFF24324A)
val Success = Color(0xFF22C55E)

// Semantic alias for screen-level headers — keeps every screen in sync
val HeaderColor = TextPrimary

/** Frosted pill / overlay chrome (navbar, floating composer). */
val GlassTint = Color(0xFF141C2C)
val GlassHairline = Color.White.copy(alpha = 0.22f)
val GlassDot = Color.White.copy(alpha = 0.10f)

/** Health activity maps and inset wells — named, not one-off hex. */
val MapSurface = Color(0xFF0B1424)
val MapWell = Color(0xFF0E1626)
val MapStart = Color(0xFF34D399)
val MapFinish = Color(0xFFFB7185)
