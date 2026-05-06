package com.example.shogigui

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
    val isPvBranch: Boolean = false  // 読み筋（一時的）かどうか
) {
    val children = mutableStateListOf<KifuNode>()
    val moveCount: Int = (parent?.moveCount ?: -1) + 1
}

data class PendingMove(
    val from: Pair<Int, Int>,
    val to: Pair<Int, Int>,
    val piece: Piece,
    val captured: Piece?
)
