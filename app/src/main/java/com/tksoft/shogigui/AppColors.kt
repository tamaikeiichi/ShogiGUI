package com.tksoft.shogigui

import androidx.compose.ui.graphics.Color

// Default player name strings
const val senteColorName = "先手"
const val goteColorName = "後手"

// Player name text colors
val senteNameColor = Color(0xFFAA0000)
val goteNameColor = Color(0xFF0000AA)

// Evaluation graph bar colors
val senteMateColor = Color(0xFFAA0000)
val goteMateColor = Color(0xFF0000AA)
val senteBarColor = Color.Red.copy(alpha = 0.5f)
val goteBarColor = Color.Blue.copy(alpha = 0.5f)
