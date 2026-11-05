package com.tksoft.shogigui

import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tksoft.shogigui.ui.theme.ShogiGUITheme
import kotlin.math.hypot

enum class PieceType(val label: String, val promotedLabel: String? = null) {
    KING("玉"), ROOK("飛", "龍"), BISHOP("角", "馬"), 
    GOLD("金"), SILVER("銀", "全"), KNIGHT("桂", "圭"), 
    LANCE("香", "杏"), PAWN("歩", "と")
}

enum class Player {
    SENTE, GOTE
}

data class Piece(val type: PieceType, val owner: Player, val isPromoted: Boolean = false)

val shogiFont = FontFamily(Font(R.font.notoserif))
    // FontFamily(Font(R.font.aoyagireisyosimo_ttf_2_01))

@Composable
fun ShogiBoard(
    boardState: Map<Pair<Int, Int>, Piece>,
    selectedSquare: Pair<Int, Int>?,
    onSquareClick: (Int, Int) -> Unit,
    isFlipped: Boolean = false,
    modifier: Modifier = Modifier,

    lastFrom: Pair<Int, Int>? = null,
    lastTo: Pair<Int, Int>? = null,
    pvColorIndex: Int = 0,
    // 解析中に盤上へ描く矢印（候補手・本譜の次の手）。空になると（解析停止）薄くして残す。
    // 駒打ち (from == null) は DropMoveArrowOverlay 側で描く
    moveArrows: List<MoveArrow> = emptyList(),
    // 盤面9x9マス部分の画面上の位置を通知する（打つ手の矢印を持ち駒から盤面へ引くために使用）
    onBoardBoxPositioned: (LayoutCoordinates) -> Unit = {},

) {
    val boardColor = when (pvColorIndex) {
        1 -> MaterialTheme.colorScheme.primaryContainer
        2 -> MaterialTheme.colorScheme.secondaryContainer
        3 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> if (pvColorIndex > 0) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surfaceContainerLow
    }
    val highlightColor = if (pvColorIndex == 3)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
    else
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
    val selectionColor = pieceSelectionColor()
    val gridColor = MaterialTheme.colorScheme.outline
    val cellColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val kanjiNumbers = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九")
      Column(
        modifier = modifier
            .rotate(if (isFlipped) 180f else 0f)
            .background(boardColor)
            .padding(2.dp)
    ) {
        // ... (筋ラベル部分は変更なし)
        Row(modifier = Modifier.fillMaxWidth().padding(end = 18.dp)) {
            for (col in 0 until 9) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = (9 - col).toString(), fontSize = 10.sp, color = labelColor)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // 盤面本体
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    // 実物の将棋盤と同じく1マスを縦長にする（横:縦 = 11:12）
                    .aspectRatio(boardCellAspect)
                    .onGloballyPositioned { onBoardBoxPositioned(it) }
            ) {
            // 駒の字はマスの幅に比例させ、盤の大きさが変わってもマスと字の比率を保つ
            val pieceFontSize = with(LocalDensity.current) { (maxWidth / 9 * pieceFontToCellRatio).toSp() }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.5.dp, gridColor)
            ) {
                for (row in 0 until 9) {
                    Row(modifier = Modifier.weight(1f)) {
                        for (col in 0 until 9) {
                            val actualRow =
                                //if (isFlipped) 8 - row else
                                    row
                            val actualCol =
                                //if (isFlipped) 8 - col else
                                    col
                            val clickedPos = Pair(actualRow, actualCol)
                            val piece = boardState[clickedPos]

                            ///val clickedPos = Pair(row, col)
                            //val piece = boardState[clickedPos]
                            
                            val isSelected = selectedSquare == clickedPos
                            val isLastMove = clickedPos == lastFrom || clickedPos == lastTo
                            
                            val bgColor = when {
                                isSelected -> selectionColor
                                isLastMove -> highlightColor
                                else -> Color.Transparent
                            }
                            val squareInteractionSource = remember(row, col) { MutableInteractionSource() }

                            ShogiSquare(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(0.5.dp, cellColor)
                                    .background(bgColor)
                                    .clickable(
                                        interactionSource = squareInteractionSource,
                                        indication = null
                                    ) { onSquareClick(actualRow, actualCol) },
                                piece = piece,
                                isFlipped = isFlipped,
                                fontSize = pieceFontSize
                            )
                        }
                    }
                }
            }
                BoardMoveArrows(arrows = moveArrows, boardState = boardState, isFlipped = isFlipped, modifier = Modifier.fillMaxSize())
            }
            // ... (段ラベル部分は変更なし)

            // 右側の段ラベル (一から九) - 盤面の高さに完全に追従
            Column(
                modifier = Modifier
                    .width(18.dp)
                    .fillMaxHeight()
            ) {
                for (row in 0 until 9) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = kanjiNumbers[row],
                            fontSize = 10.sp,
                            color = labelColor,
                            //fontFamily = shogiFont,
                            )
                    }
                }
            }
        }
    }
}

// 盤上に描く指し手の矢印。from == null は駒打ち（持ち駒から引くので DropMoveArrowOverlay 側で描く）。
// fillColor == null は塗りつぶしなし（輪郭だけ）の矢印。
// rankLabel は候補手の順位で、移動先のマスに fillColor の数字で表示する
data class MoveArrow(
    val from: Pair<Int, Int>?,
    val to: Pair<Int, Int>,
    val dropPieceType: PieceType?,
    val fillColor: Color?,
    val edgeColor: Color,
    val rankLabel: String? = null,
    // 候補手が実際の手と一致するとき、順位の数字を囲う色
    val rankRingColor: Color? = null
)

fun moveArrowFromUsi(usiMove: String, fillColor: Color?, edgeColor: Color, rankLabel: String? = null, rankRingColor: Color? = null): MoveArrow? {
    val (from, to) = usiMoveSquares(usiMove) ?: return null
    return MoveArrow(from, to, usiDropPieceType(usiMove), fillColor, edgeColor, rankLabel, rankRingColor)
}

// 実際の手と一致した候補手の番号を囲う色。対局者名の色に合わせる
@Composable
fun actualMoveRingColor(mover: Player): Color = playerNameColor(mover == Player.SENTE)

// 候補手の順位の数字を移動先のマスの中央に描く。駒の上でも読めるよう、数字の背後に
// 半透明でふちをぼかした円（放射グラデーション）を敷く。同じマスへの候補が複数あれば
// 横に並べる。盤上の手の数字と駒打ちの数字は別レイヤーで描くので、同じマスの全候補数
// (total) と各数字の枠番号 (slot) で位置を決め、重ならないようにする。
// 並びは画面上で左から「盤上の手（順位順）」→「駒打ち（持ち駒の並び順）」。
// upsideDown: 盤ごと 180° 回転して表示しているとき、数字だけ正立させる
private class RankLabel(val center: Offset, val arrow: MoveArrow, val slot: Int, val total: Int)

private fun MoveArrow.hasRankLabel() = rankLabel != null && fillColor != null

// 各マスの数字のうち盤上の手（from != null）の個数
private fun boardLabelCounts(arrows: List<MoveArrow>): Map<Pair<Int, Int>, Int> =
    arrows.filter { it.hasRankLabel() && it.from != null }.groupingBy { it.to }.eachCount()

// 各マスの数字の総数（盤上の手＋駒打ち）
private fun totalLabelCounts(arrows: List<MoveArrow>): Map<Pair<Int, Int>, Int> =
    arrows.filter { it.hasRankLabel() }.groupingBy { it.to }.eachCount()

private fun DrawScope.drawRankLabels(
    labels: List<RankLabel>, cell: Float, measurer: TextMeasurer,
    backdropColor: Color, alpha: Float, upsideDown: Boolean
) {
    labels.filter { it.arrow.hasRankLabel() }
        .forEach { label ->
            val center = label.center
            val arrow = label.arrow
            val step = cell * 0.42f
            run {
                // 回転表示時は並び順も左右反転して、画面上で左から順に並ぶようにする
                val shift = (label.slot - (label.total - 1) / 2f) * step * (if (upsideDown) -1 else 1)
                val c = Offset(center.x + shift, center.y)
                drawRankBadge(c, cell, arrow.rankLabel!!, arrow.edgeColor, backdropColor, measurer, alpha, upsideDown, arrow.rankRingColor)
            }
        }
}

// 順位の数字1つ分のグラフィック（ぼかした円＋数字）。候補手カードの番号も同じものを使う。
// cell は1マスの大きさで、円の直径は cell * rankBadgeDiameter になる
internal const val rankBadgeDiameter = 0.72f

internal fun DrawScope.drawRankBadge(
    c: Offset, cell: Float, label: String, color: Color, backdropColor: Color,
    measurer: TextMeasurer, alpha: Float = 1f, upsideDown: Boolean = false, ringColor: Color? = null
) {
    val r = cell * rankBadgeDiameter / 2f
    drawCircle(
        Brush.radialGradient(
            0f to backdropColor.copy(alpha = 0.7f * alpha),
            0.5f to backdropColor.copy(alpha = 0.5f * alpha),
            1f to Color.Transparent,
            center = c, radius = r
        ),
        radius = r, center = c
    )
    if (ringColor != null) {
        drawCircle(ringColor.copy(alpha = alpha), radius = r * 0.8f, center = c, style = Stroke(width = cell * 0.06f))
    }
    val layout = measurer.measure(
        label,
        TextStyle(color = color, fontSize = (cell * 0.42f).toSp(), fontWeight = FontWeight.Bold)
    )
    val topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f)
    rotate(if (upsideDown) 180f else 0f, pivot = c) {
        drawText(layout, topLeft = topLeft, alpha = alpha)
    }
}

// 矢印を1つの多角形（軸＋矢じり）として描く。塗りと輪郭が重なって濃くならないので、
// 塗りつぶしあり・なし（輪郭のみ）のどちらも同じ形で描ける
private fun DrawScope.drawMoveArrow(
    fromCenter: Offset, toCenter: Offset, cell: Float, startInset: Float,
    fillColor: Color?, edgeColor: Color, haloColor: Color, alpha: Float, fadeTip: Boolean
) {
    val dx = toCenter.x - fromCenter.x
    val dy = toCenter.y - fromCenter.y
    val dist = hypot(dx, dy)
    if (dist < 1f) return
    val ux = dx / dist
    val uy = dy / dist
    val px = -uy
    val py = ux

    val shaftHalf = cell * 0.10f
    val headLength = cell * 0.46f
    val headHalf = cell * 0.22f
    val endInset = cell * 0.12f

    val start = Offset(fromCenter.x + ux * startInset, fromCenter.y + uy * startInset)
    val tip = Offset(toCenter.x - ux * endInset, toCenter.y - uy * endInset)
    val neck = Offset(tip.x - ux * headLength, tip.y - uy * headLength)
    val path = Path().apply {
        moveTo(start.x + px * shaftHalf, start.y + py * shaftHalf)
        lineTo(neck.x + px * shaftHalf, neck.y + py * shaftHalf)
        lineTo(neck.x + px * headHalf, neck.y + py * headHalf)
        lineTo(tip.x, tip.y)
        lineTo(neck.x - px * headHalf, neck.y - py * headHalf)
        lineTo(neck.x - px * shaftHalf, neck.y - py * shaftHalf)
        lineTo(start.x - px * shaftHalf, start.y - py * shaftHalf)
        close()
    }
    // 始点（移動元の駒の上）は透かし、先端に向かって不透明にする。
    // 1つの駒から複数の矢印が出ても駒が隠れないように。
    // 移動先のマスに順位の数字があるとき (fadeTip) は、矢じりの先も数字が読みにくくならないよう数字の円の中で透かす
    val total = hypot(tip.x - start.x, tip.y - start.y)
    val neckFraction = if (total > 0f) (1f - headLength / total).coerceIn(0f, 1f) else 0f
    val badgeEdge = cell * rankBadgeDiameter / 2f - endInset
    val tipFadeFraction = if (total > 0f) (1f - badgeEdge / total).coerceIn(neckFraction, 1f) else 1f
    fun fade(color: Color, a: Float) = Brush.linearGradient(
        0f to color.copy(alpha = color.alpha * a * arrowStartAlpha),
        neckFraction to color.copy(alpha = color.alpha * a),
        tipFadeFraction to color.copy(alpha = color.alpha * a),
        1f to color.copy(alpha = color.alpha * a * (if (fadeTip) arrowTipAlpha else 1f)),
        start = start, end = tip
    )
    val round = androidx.compose.ui.graphics.StrokeJoin.Round
    // 縁取り（駒の上でも視認できるようコントラストを確保）
    drawPath(path, fade(haloColor, arrowOutlineAlpha * alpha), style = Stroke(width = cell * 0.10f, join = round))
    if (fillColor != null) {
        drawPath(path, fade(fillColor, arrowBodyAlpha * alpha))
        drawPath(path, fade(edgeColor, arrowEdgeAlpha * alpha), style = Stroke(width = cell * 0.035f, join = round))
    } else {
        drawPath(path, fade(edgeColor, arrowEdgeAlpha * alpha), style = Stroke(width = cell * 0.06f, join = round))
    }
}

// 解析中の指し手の矢印 (M3 Expressive: バネの弾みで表示/消去する)
// 解析を停止しても矢印自体は消さず、透明度を上げて（薄く）残す。局面が変わったとき
// (指し手が進む・棋譜を移動する等) だけ、直前の矢印は無関係になるので消す。
@Composable
fun BoardMoveArrows(
    arrows: List<MoveArrow>,
    boardState: Map<Pair<Int, Int>, Piece>,
    isFlipped: Boolean = false,
    modifier: Modifier = Modifier
) {
    var lastArrows by remember(boardState) { mutableStateOf<List<MoveArrow>>(emptyList()) }
    if (arrows.isNotEmpty()) lastArrows = arrows
    val hasArrow = lastArrows.isNotEmpty()

    // 矢印の伸びるバネアニメーション（表示/非表示の切り替え時のみ）
    val growProgress by animateFloatAsState(
        targetValue = if (hasArrow) 1f else 0f,
        animationSpec = if (hasArrow)
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        else
            tween(durationMillis = 150),
        label = "moveArrowGrow"
    )

    // 解析中はしっかり表示、解析停止後（局面はそのまま）は消さずに薄く残す
    val alpha by animateFloatAsState(
        targetValue = when {
            arrows.isNotEmpty() -> 1f
            hasArrow -> restingArrowAlpha
            else -> 0f
        },
        animationSpec = tween(durationMillis = 300),
        label = "moveArrowAlpha"
    )
    // 順位の数字は解析停止時にゆっくり（restingLabelFadeMillis かけて）薄くする
    val labelAlpha by animateFloatAsState(
        targetValue = when {
            arrows.isNotEmpty() -> 1f
            hasArrow -> restingArrowAlpha
            else -> 0f
        },
        animationSpec = tween(durationMillis = if (arrows.isEmpty() && hasArrow) restingLabelFadeMillis else 300),
        label = "moveArrowLabelAlpha"
    )

    val shown = lastArrows
    if (shown.isEmpty() || growProgress <= 0f) return
    val haloColor = MaterialTheme.colorScheme.surface
    val labelBackdropColor = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val cell = size.width / 9f
        val cellH = size.height / 9f
        val totals = totalLabelCounts(shown)
        val nextSlot = HashMap<Pair<Int, Int>, Int>()
        val labels = ArrayList<RankLabel>()
        for (arrow in shown) {
            val from = arrow.from ?: continue
            val fromCenter = Offset((from.second + 0.5f) * cell, (from.first + 0.5f) * cellH)
            val toCenter = Offset((arrow.to.second + 0.5f) * cell, (arrow.to.first + 0.5f) * cellH)
            // 移動元マスを起点にバネで伸びるアニメーション
            scale(growProgress.coerceIn(0f, 1f), pivot = fromCenter) {
                drawMoveArrow(fromCenter, toCenter, cell, cell * 0.10f, arrow.fillColor, arrow.edgeColor, haloColor, alpha, fadeTip = arrow.to in totals)
            }
            if (arrow.hasRankLabel()) {
                // 盤上の手はマスの左側の枠から順位順（shown の順）に詰める
                val slot = nextSlot.getOrDefault(arrow.to, 0)
                nextSlot[arrow.to] = slot + 1
                labels.add(RankLabel(toCenter, arrow, slot, totals[arrow.to] ?: 1))
            }
        }
        // 順位の数字は全ての矢印より上に描く
        drawRankLabels(labels, cell, measurer, labelBackdropColor, labelAlpha * growProgress.coerceIn(0f, 1f), isFlipped)
    }
}

// 矢印の不透明度（盤面が少し透けて見える程度）
private const val arrowBodyAlpha = 0.85f
private const val arrowEdgeAlpha = 0.9f
private const val arrowOutlineAlpha = 0.38f
// 矢印の始点の不透明度（先端を 1 としたときの比率）
private const val arrowStartAlpha = 0.25f
// 矢じりの先端の不透明度（矢印の本体に対する割合）
private const val arrowTipAlpha = 0.05f
// 解析停止後（局面は同じ）に矢印を消さず残しておくときの薄さ
private const val restingArrowAlpha = 0.35f
// 解析停止後に順位の数字を薄くするのにかける時間
private const val restingLabelFadeMillis = 2000
// 盤の1マスの縦横比（横 / 縦）。実物の将棋盤は縦が少し長い
internal const val boardCellAspect = 11f / 12f

// 駒打ちの矢印: 持ち駒 (別コンポーネント) から盤面のマスへ引く。ShogiBoard の外側、画面全体を覆う
// オーバーレイとして使い、LayoutCoordinates.localPositionOf で座標系をまたいで位置を合わせる
// (isFlipped による rotate() 変換も自動的に考慮される)。
@Composable
fun DropMoveArrowOverlay(
    // 駒打ちの矢印（from == null のもの）。空なら非表示
    arrows: List<MoveArrow>,
    // 局面。変わったら直前の矢印は無関係になるので消す（同じ間は解析停止後も薄く残す）
    boardState: Map<Pair<Int, Int>, Piece>,
    handCoordinates: (PieceType) -> LayoutCoordinates?,
    boardCoordinates: LayoutCoordinates?,
    modifier: Modifier = Modifier,
    // 盤・持ち駒側のスクロール位置。このオーバーレイ自体はスクロールしない画面全体の
    // Box に置かれているため、描画フェーズでこれを読んでスクロールのたびに再描画させる。
    // 位置の計算は（合成時ではなく）描画時に行う: 合成時点ではスクロール後のレイアウトが
    // まだ反映されておらず、古い座標で矢印が描かれてずれたまま残ってしまうため。
    scrollValue: () -> Int = { 0 }
) {
    var ownCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // 解析停止で arrows が空になっても、局面が同じ間は最後の矢印を薄くして残す。
    // 位置は描画のたびに LayoutCoordinates から計算し直すので、スクロールしても追従する
    // 「解析中かどうか」は駒打ちに限らず矢印全体で判定する。解析中は駒打ちの候補が無くなれば
    // （最善手が駒打ちから盤上の手に変わった等）駒打ちの矢印も消す。そうしないと古い矢印が順位ごと残る
    var lastArrows by remember(boardState) { mutableStateOf<List<MoveArrow>>(emptyList()) }
    // 盤上の手も含めた全候補（同じマスの数字の位置を盤上の手のレイヤーとそろえるのに使う）
    var lastAllArrows by remember(boardState) { mutableStateOf<List<MoveArrow>>(emptyList()) }
    if (arrows.isNotEmpty()) {
        lastArrows = arrows.filter { it.from == null }
        lastAllArrows = arrows
    }
    val resting = arrows.isEmpty()

    val own = ownCoordinates
    // 合成時は「どの矢印をどの持ち駒から引くか」だけを決める（座標はライブオブジェクトのまま保持）
    val targets = if (boardCoordinates != null && boardCoordinates.isAttached && own != null && own.isAttached) {
        lastArrows.mapNotNull { arrow ->
            val hand = arrow.dropPieceType?.let(handCoordinates) ?: return@mapNotNull null
            if (!hand.isAttached) return@mapNotNull null
            DropArrowTarget(hand, boardCoordinates, arrow)
        }
    } else emptyList()

    var lastTargets by remember { mutableStateOf<List<DropArrowTarget>>(emptyList()) }
    if (targets.isNotEmpty()) lastTargets = targets

    // 解析中はしっかり表示、解析停止後（局面はそのまま）は消さずに薄く残す
    val restAlpha by animateFloatAsState(
        targetValue = if (resting) restingArrowAlpha else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "dropArrowAlpha"
    )
    val restLabelAlpha by animateFloatAsState(
        targetValue = if (resting) restingArrowAlpha else 1f,
        animationSpec = tween(durationMillis = if (resting) restingLabelFadeMillis else 300),
        label = "dropArrowLabelAlpha"
    )

    val progress by animateFloatAsState(
        targetValue = if (targets.isNotEmpty()) 1f else 0f,
        animationSpec = if (targets.isNotEmpty())
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        else
            tween(durationMillis = 150),
        label = "dropArrowProgress"
    )

    val haloColor = MaterialTheme.colorScheme.surface
    val labelBackdropColor = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()

    Canvas(modifier = modifier.onGloballyPositioned { ownCoordinates = it }) {
        // 描画フェーズでスクロール量を読む → スクロールのたびに（レイアウト確定後に）再描画される
        scrollValue()
        if (progress <= 0f) return@Canvas
        val ownNow = ownCoordinates ?: return@Canvas
        if (!ownNow.isAttached) return@Canvas
        val points = lastTargets.mapNotNull { (hand, board, arrow) ->
            if (!hand.isAttached || !board.isAttached) return@mapNotNull null
            try {
                val cell = board.size.width / 9f
                val cellH = board.size.height / 9f
                val toLocalInBoard = Offset((arrow.to.second + 0.5f) * cell, (arrow.to.first + 0.5f) * cellH)
                val handLocalCenter = Offset(hand.size.width / 2f, hand.size.height / 2f)
                DropArrowPoints(ownNow.localPositionOf(hand, handLocalCenter), ownNow.localPositionOf(board, toLocalInBoard), cell, arrow)
            } catch (e: Exception) { null }
        }
        val grow = progress.coerceIn(0f, 1f)
        val alpha = grow * restAlpha
        val totals = totalLabelCounts(lastAllArrows)
        for ((fromCenter, toCenter, cell, arrow) in points) {
            // 持ち駒の位置を起点にバネで伸びるアニメーション
            scale(grow, pivot = fromCenter) {
                drawMoveArrow(fromCenter, toCenter, cell, 0f, arrow.fillColor, arrow.edgeColor, haloColor, alpha, fadeTip = arrow.to in totals)
            }
        }
        // このオーバーレイは回転していない座標系なので、数字はそのまま正立で描ける。
        // 複数の持ち駒から同じマスへ打つ候補があるとき、数字を順位順ではなく画面上の
        // 持ち駒の並び順（左→右、縦並びなら上→下）に並べ、どの駒の候補か対応がつくようにする
        val cell = points.firstOrNull()?.cell ?: return@Canvas
        // 盤上の手の数字がある場合はその右側の枠に置く
        val boardCounts = boardLabelCounts(lastAllArrows)
        val nextSlot = HashMap<Pair<Int, Int>, Int>()
        val labels = points.sortedWith(compareBy<DropArrowPoints>({ it.from.x }, { it.from.y }))
            .filter { it.arrow.hasRankLabel() }
            .map { p ->
                val sq = p.arrow.to
                val slot = nextSlot.getOrDefault(sq, boardCounts[sq] ?: 0)
                nextSlot[sq] = slot + 1
                RankLabel(p.to, p.arrow, slot, maxOf(totals[sq] ?: 1, slot + 1))
            }
        drawRankLabels(labels, cell, measurer, labelBackdropColor, grow * restLabelAlpha, upsideDown = false)
    }
}

private data class DropArrowTarget(val hand: LayoutCoordinates, val board: LayoutCoordinates, val arrow: MoveArrow)
private data class DropArrowPoints(val from: Offset, val to: Offset, val cell: Float, val arrow: MoveArrow)

@Composable
fun HandView(
    hand: Map<PieceType, Int>,
    player: Player,
    selectedPieceType: PieceType? = null,
    onPieceClick: (PieceType) -> Unit = {},
    isFlipped: Boolean = false,
    modifier: Modifier = Modifier,
    // 持ち駒の各駒種の画面上の位置を通知する（打つ手の矢印を持ち駒から引くために使用）
    onPiecePositioned: (PieceType, LayoutCoordinates) -> Unit = { _, _ -> }
) {
    val rotation = if ((player == Player.GOTE) != isFlipped) 180f else 0f
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .rotate(rotation),
        // 180度回すと左右が入れ替わるので、回した側は Start にして画面上で右寄せにする（左上はメニューボタン）
        horizontalArrangement = if (rotation == 0f) Arrangement.End else Arrangement.Start
    ) {
        // 持ち駒が無いときも行の高さを変えない（高さが変わると盤の位置がずれる）
        Text(text = " ", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = shogiFont, modifier = Modifier.width(0.dp))
        // 持ち駒を種類ごとに表示
        PieceType.entries.reversed().forEach { type ->
            val count = hand[type] ?: 0
            if (count > 0) {
                val isSelected = selectedPieceType == type
                Box(
                    contentAlignment = Alignment.TopEnd,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .background(if (isSelected) pieceSelectionColor() else Color.Transparent)
                        .clickable { onPieceClick(type) }
                        .onGloballyPositioned { onPiecePositioned(type, it) }
                ) {
                    Log.d("HandView", type.label)
                    Text(
                        text = type.label,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = shogiFont
                        //letterSpacing = 10.sp

                    )
                    if (count > 1) {
                        Text(
                            text = count.toString(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(x = 8.dp, y = (-4).dp),
                        )
                    }
                }
            }
        }
        if (hand.values.sum() == 0) {
            Text(text = stringResource(R.string.hand_none), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}



@Preview(showBackground = true, name = "初期配置")
@Composable
fun ShogiBoardPreview() {
    ShogiGUITheme {
        ShogiBoard(
            boardState = createInitialBoard(),
            selectedSquare = null,
            onSquareClick = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "選択・最終手・成駒あり")
@Composable
fun ShogiBoardPreviewHighlight() {
    val board = createInitialBoard().toMutableMap().apply {
        remove(Pair(6, 4))
        put(Pair(4, 4), Piece(PieceType.PAWN, Player.SENTE))
        put(Pair(3, 4), Piece(PieceType.PAWN, Player.SENTE, isPromoted = true))
    }
    ShogiGUITheme {
        ShogiBoard(
            boardState = board,
            selectedSquare = Pair(4, 4),
            lastFrom = Pair(6, 4),
            lastTo = Pair(4, 4),
            onSquareClick = { _, _ -> }
        )
    }
}

@Composable
fun PieceView(piece: Piece, isFlipped: Boolean = false, fontSize: TextUnit = 20.sp) {
    val label = if (piece.isPromoted) piece.type.promotedLabel ?: piece.type.label else piece.type.label
    val color = if (piece.isPromoted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    val rotation = when {
        //isFlipped && piece.owner == Player.SENTE -> 180f
        //isFlipped && piece.owner == Player.GOTE -> 0f
        piece.owner == Player.GOTE -> 180f
        else -> 0f
    }

    Text(
        text = label,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.rotate(rotation),
        fontFamily = shogiFont
    )
}

// 駒の字の大きさとマスの幅の比率。縦型レイアウト（幅411dpの端末で1マス約42dp）で 20sp だったのを基準にする
private const val pieceFontToCellRatio = 0.48f

@Composable
fun ShogiSquare(piece: Piece?,
                modifier: Modifier = Modifier,
                isFlipped: Boolean = false,
                fontSize: TextUnit = 20.sp) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (piece != null) {
            PieceView(piece, isFlipped, fontSize)
        }
    }
}

