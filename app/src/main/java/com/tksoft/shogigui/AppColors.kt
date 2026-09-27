package com.tksoft.shogigui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

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

// 駒を選択したマス・持ち駒の背景色。盤面（黄・ベージュ・緑系のテーマ色）や
// 候補手カードの色と区別できるよう、テーマに無い青系を使う
val pieceSelectionColorLight = Color(0xFF9CCBFF)
val pieceSelectionColorDark = Color(0xFF2D5B91)

@androidx.compose.runtime.Composable
fun pieceSelectionColor(): Color =
    if (androidx.compose.material3.MaterialTheme.colorScheme.surface.luminance() < 0.5f) pieceSelectionColorDark
    else pieceSelectionColorLight
