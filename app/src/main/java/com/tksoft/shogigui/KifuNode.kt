package com.tksoft.shogigui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// 棋譜の1局面を管理するノードクラス
class KifuNode(
    // CSAインポート時、駒落ちなど非標準の初期配置を解析結果で上書きできるよう var にしている
    // （それ以外の用途では通常のノードと同様、構築後に書き換えない）
    var board: Map<Pair<Int, Int>, Piece>,
    var senteHand: Map<PieceType, Int>,
    var goteHand: Map<PieceType, Int>,
    var currentPlayer: Player,
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
    // 解析で得た評価値（先手視点。詰みは ±30000 など |x|>10000。null = 未解析）
    var evalScore by mutableStateOf<Int?>(null)

    // 樹形図で同じ行に続ける子。PV分岐は同じ候補順位のPVだけ、それ以外は最初の非PV子を続ける。
    // 手作業で入力した手や別の候補の読み筋は、新しい行（枝分かれ）になる
    fun continuationChild(): KifuNode? =
        if (isPvBranch) children.firstOrNull { it.isPvBranch && it.pvColorIndex == pvColorIndex }
        else children.firstOrNull { !it.isPvBranch }
}

data class PendingMove(
    val from: Pair<Int, Int>,
    val to: Pair<Int, Int>,
    val piece: Piece,
    val captured: Piece?
)
