package com.example.shogigui

import android.R.attr.tag
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

) {
    val boardColor = Color(0xFFE7CB6F)
    val highlightColor = Color(0xFF6F8BE7).copy(alpha = 0.6f) // 指し手の強調色（山吹色系）
    val selectionColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
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
                    Text(text = (9 - col).toString(), fontSize = 10.sp, color = Color.DarkGray)
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.0f)
                    .border(1.5.dp, Color.Black)
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
                                    .border(0.5.dp, Color.Black)
                                    .background(bgColor)
                                    .clickable { onSquareClick(actualRow, actualCol) },
                                piece = piece,
                                isFlipped = isFlipped
                            )
                        }
                    }
                }
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
                            color = Color.DarkGray,
                            //fontFamily = shogiFont,
                            )
                    }
                }
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
    modifier: Modifier = Modifier
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
            Text(text = "なし", fontSize = 14.sp, color = Color.Gray)
        }
    }
}



@Preview(showBackground = true)
@Composable
fun ShogiBoardPreview() {
    val sampleBoard = mutableMapOf<Pair<Int, Int>, Piece>().apply {
        // 主要な駒の配置
        put(Pair(8, 4), Piece(PieceType.KING, Player.SENTE))
        put(Pair(0, 4), Piece(PieceType.KING, Player.GOTE))
        put(Pair(7, 7), Piece(PieceType.ROOK, Player.SENTE))
        put(Pair(1, 1), Piece(PieceType.ROOK, Player.GOTE))
        put(Pair(7, 1), Piece(PieceType.BISHOP, Player.SENTE))
        put(Pair(1, 7), Piece(PieceType.BISHOP, Player.GOTE))
        
        // 歩
        for (i in 0 until 9) {
            put(Pair(6, i), Piece(PieceType.PAWN, Player.SENTE))
            put(Pair(2, i), Piece(PieceType.PAWN, Player.GOTE))
        }
        
        // 成駒のサンプル
        put(Pair(4, 4), Piece(PieceType.PAWN, Player.SENTE, isPromoted = true))
    }

    ShogiBoard(
        boardState = sampleBoard,
        selectedSquare = Pair(6, 4),
        onSquareClick = { _, _ -> }
    )
}

@Composable
fun PieceView(piece: Piece, isFlipped: Boolean = false) {
    val darkRed = Color(0xFF800000)

    val label = if (piece.isPromoted) piece.type.promotedLabel ?: piece.type.label else piece.type.label
    val color = if (piece.isPromoted) darkRed else Color.Black

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

