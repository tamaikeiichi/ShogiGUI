package com.tksoft.shogigui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Default player name strings
const val senteColorName = "先手"
const val goteColorName = "後手"

// 対局者名・実際の手の囲み・形勢グラフの色。ダークモードでも読めるよう、どちらのモードでも同じテーマの色を使う。
// テーマに青は無いので、後手は寒色の tertiary にする
@androidx.compose.runtime.Composable
fun playerNameColor(isSente: Boolean): Color =
    if (isSente) androidx.compose.material3.MaterialTheme.colorScheme.error
    else androidx.compose.material3.MaterialTheme.colorScheme.tertiary

// 駒を選択したマス・持ち駒の背景色。盤面（黄・ベージュ・緑系のテーマ色）や
// 候補手カードの色と区別できるよう、テーマに無い青系を使う
val pieceSelectionColorLight = Color(0xFF9CCBFF)
val pieceSelectionColorDark = Color(0xFF2D5B91)

@androidx.compose.runtime.Composable
fun pieceSelectionColor(): Color =
    if (androidx.compose.material3.MaterialTheme.colorScheme.surface.luminance() < 0.5f) pieceSelectionColorDark
    else pieceSelectionColorLight
