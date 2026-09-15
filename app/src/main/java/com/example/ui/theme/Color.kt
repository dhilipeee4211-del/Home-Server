package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// "Home Lab" brand palette — a self-hosted server control panel, not a generic
// SaaS dashboard. Amber reads as rack/equipment indicator LEDs; teal is the
// cool counterpart used for live data readouts. Kept deliberately apart from
// the common blue/cyan SaaS default and from warm-clay/terracotta tones.
// ---------------------------------------------------------------------------

// Brand — Amber (primary accent, used sparingly: CTAs, active states, brand marks)
val Amber80 = Color(0xFFF3C374)
val Amber60 = Color(0xFFE8A33D)
val Amber40 = Color(0xFFB9791F)

// Data accent — Teal (readouts, secondary emphasis, links)
val Teal80 = Color(0xFF8FE0D2)
val Teal60 = Color(0xFF4FB8A8)
val Teal40 = Color(0xFF2C8578)

val Graphite80 = Color(0xFFC7CBD1)
val Graphite40 = Color(0xFF4A4F58)

// Functional / status indicators
val StatusRunning = Color(0xFF3DDC84)
val StatusStopped = Color(0xFFF1554C)
val StatusWarning = Color(0xFFF0B429)
val StatusDemo = Color(0xFF9B8AFB)

// Dark Theme Surfaces — near-black graphite, not blue-black
val DarkBackground = Color(0xFF0A0B0D)
val DarkSurface = Color(0xFF15171B)
val DarkSurfaceVariant = Color(0xFF1D2024)
val DarkBorder = Color(0xFF2A2E33)

// Light Theme Surfaces
val LightBackground = Color(0xFFF7F6F3)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEFEDE8)
val LightBorder = Color(0xFFE0DDD6)
