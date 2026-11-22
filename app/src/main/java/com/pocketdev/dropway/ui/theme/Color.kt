package com.pocketdev.dropway.ui.theme

import androidx.compose.ui.graphics.Color

// Dropway Brand Palette
val DropwayPrimary = Color(0xFF0284C7)      // Sky 600 - Brand blue
val DropwayPrimaryDark = Color(0xFF0369A1)  // Sky 700
val DropwayNavy = Color(0xFF0F172A)         // Slate 900
val DropwaySurfaceDark = Color(0xFF1E293B)  // Slate 800
val DropwayBackground = Color(0xFFF1F5F9)   // Slate 100
val DropwayCard = Color(0xFFFFFFFF)
val DropwayBorder = Color(0xFFE2E8F0)

// Status Colors (High contrast for outdoors & sunlight)
val StatusDelivered = Color(0xFF047857)     // Deep Emerald 700
val StatusDeliveredBg = Color(0xFFD1FAE5)
val StatusPending = Color(0xFFB45309)       // Deep Amber 700
val StatusPendingBg = Color(0xFFFEF3C7)
val StatusFailed = Color(0xFFB91C1C)        // Deep Red 700
val StatusFailedBg = Color(0xFFFEE2E2)
val StatusAwaitingTransfer = Color(0xFF6D28D9) // Purple 700
val StatusAwaitingTransferBg = Color(0xFFEDE9FE)
val StatusPartial = Color(0xFF0369A1)
val StatusPartialBg = Color(0xFFE0F2FE)

// Text Colors - HIGH CONTRAST (Never faint or washed-out)
val TextPrimary = Color(0xFF0F172A)         // Slate 900 - Darkest, sharpest readability
val TextSecondary = Color(0xFF1E293B)       // Slate 800 - Crisp high contrast
val TextMuted = Color(0xFF334155)           // Slate 700 - Legible supporting text (>5:1 contrast)
val TextSubtle = Color(0xFF475569)          // Slate 600 - Tertiary text

// High Contrast Text on Dark Surfaces (TopBar, Dark Cards, Badges)
val TextOnDarkPrimary = Color(0xFFFFFFFF)
val TextOnDarkSecondary = Color(0xFFF8FAFC) // Slate 50 - Very clear and bright
val TextOnDarkMuted = Color(0xFFE2E8F0)     // Slate 200 - High legibility on dark
