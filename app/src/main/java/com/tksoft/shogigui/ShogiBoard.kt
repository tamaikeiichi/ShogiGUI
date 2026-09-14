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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
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
    // 解析中の候補1番目の指し手 (USI形式、例: "7g7f")。解析が中止されると null になり矢印は消える。
    bestMoveUsi: String? = null,
    // 盤面9x9マス部分の画面上の位置を通知する（打つ手の矢印を持ち駒から盤面へ引くために使用）
    onBoardBoxPositioned: (LayoutCoordinates) -> Unit = {},
    // 矢印の色。呼び出し側で「候補1番目」として表示しているPVカードと同じ色を渡す想定。
    bestMoveColor: Color = MaterialTheme.colorScheme.primary,

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
    val selectionColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.0f)
                    .onGloballyPositioned { onBoardBoxPositioned(it) }
            ) {
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

                            ShogiSquare(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(0.5.dp, cellColor)
                                    .background(bgColor)
                                    .clickable { onSquareClick(actualRow, actualCol) },
                                piece = piece,
                                isFlipped = isFlipped
                            )
                        }
                    }
                }
            }
                BestMoveArrow(bestMoveUsi = bestMoveUsi, arrowColor = bestMoveColor, modifier = Modifier.fillMaxSize())
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

// 解析中の候補1番目の指し手を示す矢印 (M3 Expressive: 太くまるいストロークとバネの弾みで表示/消去する)
@Composable
fun BestMoveArrow(
    bestMoveUsi: String?,
    modifier: Modifier = Modifier,
    arrowColor: Color = MaterialTheme.colorScheme.primary
) {
    var lastSquares by remember { mutableStateOf<Pair<Pair<Int, Int>?, Pair<Int, Int>>?>(null) }
    val squares = bestMoveUsi?.let { usiMoveSquares(it) }
    if (squares != null) lastSquares = squares

    val progress by animateFloatAsState(
        targetValue = if (squares != null) 1f else 0f,
        animationSpec = if (squares != null)
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        else
            tween(durationMillis = 150),
        label = "bestMoveArrowProgress"
    )

    val shown = lastSquares
    if (shown == null || progress <= 0f) return
    val outlineColor = MaterialTheme.colorScheme.surface
    val alpha = progress.coerceIn(0f, 1f)

    Canvas(modifier = modifier) {
        val cell = size.width / 9f
        val (from, to) = shown
        val toCenter = Offset((to.second + 0.5f) * cell, (to.first + 0.5f) * cell)

        if (from == null) {
            // 駒打ち: 持ち駒から盤面へ引く矢印は DropMoveArrowOverlay (画面全体オーバーレイ) 側で描画する
            return@Canvas
        }

        val fromCenter = Offset((from.second + 0.5f) * cell, (from.first + 0.5f) * cell)
        val dx = toCenter.x - fromCenter.x
        val dy = toCenter.y - fromCenter.y
        val dist = hypot(dx, dy)
        if (dist < 1f) return@Canvas
        val ux = dx / dist
        val uy = dy / dist

        val strokeWidth = cell * 0.20f
        val headLength = cell * 0.46f
        val headWidth = cell * 0.36f
        val startInset = cell * 0.10f
        val endInset = cell * 0.12f

        val lineStart = Offset(fromCenter.x + ux * startInset, fromCenter.y + uy * startInset)
        val headTip = Offset(toCenter.x - ux * endInset, toCenter.y - uy * endInset)
        val lineEnd = Offset(headTip.x - ux * headLength, headTip.y - uy * headLength)
        val perpX = -uy
        val perpY = ux
        val baseL = Offset(lineEnd.x + perpX * headWidth / 2f, lineEnd.y + perpY * headWidth / 2f)
        val baseR = Offset(lineEnd.x - perpX * headWidth / 2f, lineEnd.y - perpY * headWidth / 2f)
        val headPath = Path().apply {
            moveTo(headTip.x, headTip.y)
            lineTo(baseL.x, baseL.y)
            lineTo(baseR.x, baseR.y)
            close()
        }

        // 移動元マスを起点にバネで伸びるアニメーション
        scale(progress.coerceIn(0f, 1f), pivot = fromCenter) {
            val bounds = Rect(Offset.Zero, size)
            // 縁取り（駒の上でも視認できるようコントラストを確保）
            // 棒と矢じりを同じレイヤーに不透明で描いてからレイヤーごと透過させることで、
            // 重なり部分が二重に濃くならないようにする
            val outlineLayerAlpha = arrowOutlineAlpha * alpha
            drawIntoCanvas { canvas ->
                canvas.saveLayer(bounds, Paint().apply { this.alpha = outlineLayerAlpha })
                drawLine(outlineColor, lineStart, lineEnd, strokeWidth + cell * 0.06f, cap = StrokeCap.Round)
                drawPath(headPath, outlineColor, style = Stroke(width = cell * 0.05f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                canvas.restore()
            }

            // 本体
            val bodyLayerAlpha = arrowBodyAlpha * alpha
            drawIntoCanvas { canvas ->
                canvas.saveLayer(bounds, Paint().apply { this.alpha = bodyLayerAlpha })
                drawLine(arrowColor, lineStart, lineEnd, strokeWidth, cap = StrokeCap.Round)
                drawPath(headPath, arrowColor)
                canvas.restore()
            }
        }
    }
}

// 矢印の不透明度（盤面が透けて見えるよう少し薄めに設定）
private const val arrowBodyAlpha = 0.62f
private const val arrowOutlineAlpha = 0.38f

// 駒打ちの矢印: 持ち駒 (別コンポーネント) から盤面のマスへ引く。ShogiBoard の外側、画面全体を覆う
// オーバーレイとして使い、LayoutCoordinates.localPositionOf で座標系をまたいで位置を合わせる
// (isFlipped による rotate() 変換も自動的に考慮される)。
@Composable
fun DropMoveArrowOverlay(
    visible: Boolean,
    handCoordinates: LayoutCoordinates?,
    boardCoordinates: LayoutCoordinates?,
    toSquare: Pair<Int, Int>?,
    modifier: Modifier = Modifier,
    arrowColor: Color = MaterialTheme.colorScheme.primary,
    // 盤・持ち駒側のスクロール位置。このオーバーレイ自体はスクロールしない画面全体の
    // Box に置かれているため、onGloballyPositioned の再通知だけに頼ると盤や持ち駒が
    // スクロールで動いても矢印が再合成されず取り残されることがある。呼び出し側で
    // スクロール量をここに渡すことで、スクロールのたびに再合成させて
    // handCoordinates/boardCoordinates (常に最新位置を返すライブオブジェクト) から
    // 現在位置を計算し直させる。
    scrollValue: Int = 0
) {
    var ownCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val own = ownCoordinates
    val points = if (visible && toSquare != null &&
        handCoordinates != null && handCoordinates.isAttached &&
        boardCoordinates != null && boardCoordinates.isAttached &&
        own != null && own.isAttached
    ) {
        try {
            // scrollValue を実際に読むことで、Compose の strong skipping によりこの
            // Composable が「未使用パラメータ」として再合成をスキップされるのを防ぐ。
            // スクロールのたびにこのブロックを再実行させ、handCoordinates/boardCoordinates
            // (常に現在位置を返すライブオブジェクト) から矢印の位置を計算し直させる。
            @Suppress("UNUSED_EXPRESSION")
            scrollValue
            val cell = boardCoordinates.size.width / 9f
            val toLocalInBoard = Offset((toSquare.second + 0.5f) * cell, (toSquare.first + 0.5f) * cell)
            val handLocalCenter = Offset(handCoordinates.size.width / 2f, handCoordinates.size.height / 2f)
            val fromInOwn = own.localPositionOf(handCoordinates, handLocalCenter)
            val toInOwn = own.localPositionOf(boardCoordinates, toLocalInBoard)
            Triple(fromInOwn, toInOwn, cell)
        } catch (e: Exception) { null }
    } else null

    var lastPoints by remember { mutableStateOf<Triple<Offset, Offset, Float>?>(null) }
    if (points != null) lastPoints = points

    val progress by animateFloatAsState(
        targetValue = if (points != null) 1f else 0f,
        animationSpec = if (points != null)
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        else
            tween(durationMillis = 150),
        label = "dropArrowProgress"
    )

    val outlineColor = MaterialTheme.colorScheme.surface

    Canvas(modifier = modifier.onGloballyPositioned { ownCoordinates = it }) {
        val shown = lastPoints ?: return@Canvas
        if (progress <= 0f) return@Canvas
        val (fromCenter, toCenter, cell) = shown
        val alpha = progress.coerceIn(0f, 1f)

        val dx = toCenter.x - fromCenter.x
        val dy = toCenter.y - fromCenter.y
        val dist = hypot(dx, dy)
        if (dist < 1f) return@Canvas
        val ux = dx / dist
        val uy = dy / dist

        val strokeWidth = cell * 0.20f
        val headLength = cell * 0.46f
        val headWidth = cell * 0.36f
        val endInset = cell * 0.12f

        val lineStart = fromCenter
        val headTip = Offset(toCenter.x - ux * endInset, toCenter.y - uy * endInset)
        val lineEnd = Offset(headTip.x - ux * headLength, headTip.y - uy * headLength)
        val perpX = -uy
        val perpY = ux
        val baseL = Offset(lineEnd.x + perpX * headWidth / 2f, lineEnd.y + perpY * headWidth / 2f)
        val baseR = Offset(lineEnd.x - perpX * headWidth / 2f, lineEnd.y - perpY * headWidth / 2f)
        val headPath = Path().apply {
            moveTo(headTip.x, headTip.y)
            lineTo(baseL.x, baseL.y)
            lineTo(baseR.x, baseR.y)
            close()
        }

        // 持ち駒の位置を起点にバネで伸びるアニメーション
        scale(progress.coerceIn(0f, 1f), pivot = fromCenter) {
            val bounds = Rect(Offset.Zero, size)
            // 棒と矢じりを同じレイヤーに不透明で描いてからレイヤーごと透過させることで、
            // 重なり部分が二重に濃くならないようにする
            val outlineLayerAlpha = arrowOutlineAlpha * alpha
            drawIntoCanvas { canvas ->
                canvas.saveLayer(bounds, Paint().apply { this.alpha = outlineLayerAlpha })
                drawLine(outlineColor, lineStart, lineEnd, strokeWidth + cell * 0.06f, cap = StrokeCap.Round)
                drawPath(headPath, outlineColor, style = Stroke(width = cell * 0.05f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                canvas.restore()
            }

            val bodyLayerAlpha = arrowBodyAlpha * alpha
            drawIntoCanvas { canvas ->
                canvas.saveLayer(bounds, Paint().apply { this.alpha = bodyLayerAlpha })
                drawLine(arrowColor, lineStart, lineEnd, strokeWidth, cap = StrokeCap.Round)
                drawPath(headPath, arrowColor)
                canvas.restore()
            }
        }
    }
}

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
        horizontalArrangement = Arrangement.End
    ) {
        // 持ち駒を種類ごとに表示
        PieceType.entries.reversed().forEach { type ->
            val count = hand[type] ?: 0
            if (count > 0) {
                val isSelected = selectedPieceType == type
                Box(
                    contentAlignment = Alignment.TopEnd,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
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
            Text(text = "なし", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
fun PieceView(piece: Piece, isFlipped: Boolean = false) {
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
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.rotate(rotation),
        fontFamily = shogiFont
    )
}

@Composable
fun ShogiSquare(piece: Piece?,
                modifier: Modifier = Modifier,
                isFlipped: Boolean = false) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (piece != null) {
            PieceView(piece, isFlipped)
        }
    }
}

