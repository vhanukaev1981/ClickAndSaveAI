package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Frozen premium visual tokens from the approved V3 source of truth.
val V3Background = Color(0xFFF8FAFC)
val V3Surface = Color(0xFFFFFFFF)
val V3Navy = Color(0xFF0F172A)
val V3Primary = Color(0xFF2563EB)
val V3Teal = Color(0xFF14B8A6)
val V3Success = Color(0xFF00B879)
val V3SuccessSoft = Color(0xFFECFDF5)
val V3Muted = Color(0xFFF1F5F9)
val V3MutedForeground = Color(0xFF64748B)
val V3PrimarySoft = Color(0xFFEFF6FF)
val V3Border = Color(0xFFE2E8F0)
val V3Warning = Color(0xFFF59E0B)
val V3WarningSoft = Color(0xFFFFFBEB)
val V3Destructive = Color(0xFFEF4444)
val V3Aurora1 = Color(0xFF071638)
val V3Aurora2 = Color(0xFF17419E)
val V3Aurora3 = Color(0xFF0E7490)

// Restrained AI accent: premium indigo, subordinate to the core blue identity.
val V3AiViolet = Color(0xFF6366F1)
val V3AiSoft = Color(0xFFF2F3FF)

// Compatibility aliases used throughout the existing native product. Keeping the
// aliases makes this pass presentation-only and avoids changing product behavior.
val TechBluePrimary = V3Primary
val TechBlueLight = Color(0xFF3B82F6)
val TechBlueDark = Color(0xFF1D4ED8)
val AiVioletPrimary = V3AiViolet

val EmeraldSavings = V3Success
val EmeraldSavingsLight = Color(0xFF34D399)
val EmeraldSavingsDark = Color(0xFF047857)

val BrandNavy = V3Navy

val AlertRed = V3Destructive
val AmberDeal = V3Warning
val AmberDealLight = Color(0xFFFBBF24)

val BackgroundDark = V3Navy
val SurfaceDark = Color(0xFF1E293B)
val SurfaceVariantDark = Color(0xFF334155)
val TextPrimaryDark = V3Background
val TextSecondaryDark = Color(0xFF94A3B8)
val BackgroundLight = V3Background
val SurfaceLight = V3Surface
val SurfaceVariantLight = V3Muted
val TextPrimaryLight = V3Navy
val TextSecondaryLight = V3MutedForeground

val V3SurfaceSoft = V3Muted
val V3BlueSoft = V3PrimarySoft
val V3EmeraldSoft = V3SuccessSoft
val V3AmberSoft = V3WarningSoft
val V3ErrorSoft = Color(0xFFFEF2F2)
