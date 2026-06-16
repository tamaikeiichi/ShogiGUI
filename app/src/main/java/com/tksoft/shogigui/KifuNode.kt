package com.tksoft.shogigui

import androidx.compose.runtime.mutableStateListOf

// 棋譜の1局面を管理するノードクラス
class KifuNode(
    val board: Map<Pair<Int, Int>, Piece>,
    val senteHand: Map<PieceType, Int>,
    val goteHand: Map<PieceType, Int>,
    val currentPlayer: Player,
    val moveLabel: String = "開始局面",
    val parent: KifuNode? = null,
    val lastFrom: Pair<Int, Int>? = null,
    val lastTo: Pair<Int, Int>? = null,
    val isPvBranch: Boolean = false,
    val pvColorIndex: Int = 0 // 0:なし, 1:第1候補, 2:第2候補...
) {
    val children = mutableStateListOf<KifuNode>()
    val moveCount: Int = (parent?.moveCount ?: -1) + 1
    // ファイル読み込み時のみ設定される残り時間（null = 情報なし）
    var senteRemainingMs: Long? = null
    var goteRemainingMs: Long? = null
}

data class PendingMove(
    val from: Pair<Int, Int>,
    val to: Pair<Int, Int>,
    val piece: Piece,
    val captured: Piece?
)
