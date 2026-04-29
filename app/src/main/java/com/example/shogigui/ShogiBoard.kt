package com.example.shogigui

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

@Composable
fun ShogiBoard(
    boardState: Map<Pair<Int, Int>, Piece>,
    selectedSquare: Pair<Int, Int>?,
    onSquareClick: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val boardColor = Color(0xFFFFD9A5)
    val kanjiNumbers = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九")

    Column(
        modifier = modifier
            .background(boardColor)
            .padding(4.dp)
    ) {
        // 上側の数字ラベル (筋: 9から1) - 右側の段ラベル分(24dp)を空ける
        Row(modifier = Modifier.fillMaxWidth().padding(end = 24.dp)) {
            for (col in 0 until 9) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = (9 - col).toString(), fontSize = 10.sp, color = Color.DarkGray)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(2.dp))

        // 盤面と段ラベルを同じRowに配置し、高さを最小サイズ(IntrinsicSize.Min)で同期
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
                            val piece = boardState[Pair(row, col)]
                            val isSelected = selectedSquare == Pair(row, col)
                            ShogiSquare(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(0.5.dp, Color.Black)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent)
                                    .clickable { onSquareClick(row, col) },
                                piece = piece
                            )
                        }
                    }
                }
            }

            // 右側の段ラベル (一から九) - 盤面の高さに完全に追従
            Column(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
            ) {
                for (row in 0 until 9) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = kanjiNumbers[row], fontSize = 10.sp, color = Color.DarkGray)
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
    modifier: Modifier = Modifier
) {
    val rotation = if (player == Player.GOTE) 180f else 0f
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
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
                        .padding(horizontal = 4.dp)
                        .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { onPieceClick(type) }
                ) {
                    Text(
                        text = type.label,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (count > 1) {
                        Text(
                            text = count.toString(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(x = 8.dp, y = (-4).dp)
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

@Composable
fun ShogiSquare(piece: Piece?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (piece != null) {
            PieceView(piece)
        }
    }
}

@Composable
fun PieceView(piece: Piece) {
    val label = if (piece.isPromoted) piece.type.promotedLabel ?: piece.type.label else piece.type.label
    val color = if (piece.isPromoted) Color.Red else Color.Black
    val rotation = if (piece.owner == Player.GOTE) 180f else 0f

    Text(
        text = label,
        color = color,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.rotate(rotation)
    )
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
