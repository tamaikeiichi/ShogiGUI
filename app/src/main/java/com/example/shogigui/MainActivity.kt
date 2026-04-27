package com.example.shogigui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.shogigui.ui.theme.ShogiGUITheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShogiGUITheme {
                // 初期配置の状態管理
                var boardState by remember { mutableStateOf(createInitialBoard()) }
                var selectedSquare by remember { mutableStateOf<Pair<Int, Int>?>(null) }
                var selectedHandPiece by remember { mutableStateOf<Pair<Player, PieceType>?>(null) }
                var promotionPendingBy by remember { mutableStateOf<PendingMove?>(null) }
                var senteHand by remember { mutableStateOf(mapOf<PieceType, Int>()) }
                var goteHand by remember { mutableStateOf(mapOf<PieceType, Int>()) }
                var currentPlayer by remember { mutableStateOf(Player.SENTE) }
                
                var showMenu by remember { mutableStateOf(false) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("将棋GUI") },
                            actions = {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "メニュー"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("対局開始 (リセット)") },
                                        onClick = {
                                            boardState = createInitialBoard()
                                            senteHand = emptyMap()
                                            goteHand = emptyMap()
                                            currentPlayer = Player.SENTE
                                            selectedSquare = null
                                            selectedHandPiece = null
                                            showMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("棋譜の読み込み") },
                                        onClick = { showMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("エンジンの設定") },
                                        onClick = { showMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("解析開始") },
                                        onClick = { showMenu = false }
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 後手情報
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentPlayer == Player.GOTE) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "後手",
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        // 後手の持ち駒（逆向き）
                        HandView(
                            hand = goteHand,
                            player = Player.GOTE,
                            selectedPieceType = if (selectedHandPiece?.first == Player.GOTE) selectedHandPiece?.second else null,
                            onPieceClick = { type ->
                                if (currentPlayer == Player.GOTE) {
                                    selectedSquare = null
                                    selectedHandPiece = if (selectedHandPiece == Pair(Player.GOTE, type)) null else Pair(Player.GOTE, type)
                                }
                            }
                        )
                        
                        ShogiBoard(
                            boardState = boardState,
                            selectedSquare = selectedSquare,
                            onSquareClick = { row, col ->
                                val clickedPos = Pair(row, col)
                                
                                val currentHandSelection = selectedHandPiece
                                if (currentHandSelection != null) {
                                    // 持ち駒を打つ
                                    if (boardState[clickedPos] == null && currentHandSelection.first == currentPlayer) {
                                        val (player, type) = currentHandSelection
                                        
                                        // 二歩のチェック
                                        var isNifu = false
                                        if (type == PieceType.PAWN) {
                                            for (r in 0 until 9) {
                                                val p = boardState[Pair(r, col)]
                                                if (p != null && p.owner == player && p.type == PieceType.PAWN && !p.isPromoted) {
                                                    isNifu = true
                                                    break
                                                }
                                            }
                                        }

                                        if (!isNifu) {
                                            val newBoard = boardState.toMutableMap()
                                            newBoard[clickedPos] = Piece(type, player)
                                            boardState = newBoard
                                            
                                            // 持ち駒を減らす
                                            if (player == Player.SENTE) {
                                                senteHand = senteHand.toMutableMap().apply {
                                                    val count = this[type] ?: 0
                                                    if (count > 1) this[type] = count - 1 else remove(type)
                                                }
                                            } else {
                                                goteHand = goteHand.toMutableMap().apply {
                                                    val count = this[type] ?: 0
                                                    if (count > 1) this[type] = count - 1 else remove(type)
                                                }
                                            }
                                            selectedHandPiece = null
                                            currentPlayer = if (currentPlayer == Player.SENTE) Player.GOTE else Player.SENTE
                                        }
                                    }
                                } else {
                                    val currentSelected = selectedSquare
                                    if (currentSelected == null) {
                                        // 駒を選択
                                        val piece = boardState[clickedPos]
                                        if (piece != null && piece.owner == currentPlayer) {
                                            selectedSquare = clickedPos
                                        }
                                    } else {
                                        if (currentSelected == clickedPos) {
                                            // 選択解除
                                            selectedSquare = null
                                        } else {
                                            val movingPiece = boardState[currentSelected]
                                            val targetPiece = boardState[clickedPos]

                                            if (movingPiece != null && movingPiece.owner == currentPlayer && movingPiece.owner != targetPiece?.owner) {
                                                // 駒の動きがルール通りかチェック
                                                if (isValidMovePattern(currentSelected, clickedPos, movingPiece, boardState)) {
                                                    // 成り判定
                                                    val canPromote = !movingPiece.isPromoted && movingPiece.type.promotedLabel != null
                                                    val isSenteZone = clickedPos.first <= 2 || currentSelected.first <= 2
                                                    val isGoteZone = clickedPos.first >= 6 || currentSelected.first >= 6
                                                    val enteringZone = if (movingPiece.owner == Player.SENTE) isSenteZone else isGoteZone
                                                    
                                                    // 強制成り判定 (歩・香は1段目、桂は1-2段目)
                                                    var mustPromote = false
                                                    if (movingPiece.type == PieceType.PAWN || movingPiece.type == PieceType.LANCE) {
                                                        if (movingPiece.owner == Player.SENTE && clickedPos.first == 0) mustPromote = true
                                                        if (movingPiece.owner == Player.GOTE && clickedPos.first == 8) mustPromote = true
                                                    } else if (movingPiece.type == PieceType.KNIGHT) {
                                                        if (movingPiece.owner == Player.SENTE && clickedPos.first <= 1) mustPromote = true
                                                        if (movingPiece.owner == Player.GOTE && clickedPos.first >= 7) mustPromote = true
                                                    }

                                                    if (mustPromote) {
                                                        executeMove(currentSelected, clickedPos, movingPiece, targetPiece, true, boardState, senteHand, goteHand) { nextBoard, nextSente, nextGote ->
                                                            boardState = nextBoard
                                                            senteHand = nextSente
                                                            goteHand = nextGote
                                                            currentPlayer = if (currentPlayer == Player.SENTE) Player.GOTE else Player.SENTE
                                                        }
                                                    } else if (canPromote && enteringZone) {
                                                        promotionPendingBy = PendingMove(currentSelected, clickedPos, movingPiece, targetPiece)
                                                    } else {
                                                        executeMove(currentSelected, clickedPos, movingPiece, targetPiece, false, boardState, senteHand, goteHand) { nextBoard, nextSente, nextGote ->
                                                            boardState = nextBoard
                                                            senteHand = nextSente
                                                            goteHand = nextGote
                                                            currentPlayer = if (currentPlayer == Player.SENTE) Player.GOTE else Player.SENTE
                                                        }
                                                    }
                                                    selectedSquare = null
                                                }
                                            } else if (targetPiece?.owner == movingPiece?.owner) {
                                                // 自分の別の駒を選択し直す
                                                selectedSquare = clickedPos
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.padding(16.dp)
                        )
                        
                        // 先手の持ち駒
                        HandView(
                            hand = senteHand,
                            player = Player.SENTE,
                            selectedPieceType = if (selectedHandPiece?.first == Player.SENTE) selectedHandPiece?.second else null,
                            onPieceClick = { type ->
                                if (currentPlayer == Player.SENTE) {
                                    selectedSquare = null
                                    selectedHandPiece = if (selectedHandPiece == Pair(Player.SENTE, type)) null else Pair(Player.SENTE, type)
                                }
                            }
                        )

                        // 先手情報
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentPlayer == Player.SENTE) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = "先手",
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    // 成り選択ダイアログ
                    promotionPendingBy?.let { move ->
                        AlertDialog(
                            onDismissRequest = { /* キャンセル不可 */ },
                            title = { Text("成り") },
                            text = { Text("成りますか？") },
                            confirmButton = {
                                TextButton(onClick = {
                                    executeMove(move.from, move.to, move.piece, move.captured, true, boardState, senteHand, goteHand) { nextBoard, nextSente, nextGote ->
                                        boardState = nextBoard
                                        senteHand = nextSente
                                        goteHand = nextGote
                                        currentPlayer = if (currentPlayer == Player.SENTE) Player.GOTE else Player.SENTE
                                    }
                                    promotionPendingBy = null
                                }) { Text("成る") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    executeMove(move.from, move.to, move.piece, move.captured, false, boardState, senteHand, goteHand) { nextBoard, nextSente, nextGote ->
                                        boardState = nextBoard
                                        senteHand = nextSente
                                        goteHand = nextGote
                                        currentPlayer = if (currentPlayer == Player.SENTE) Player.GOTE else Player.SENTE
                                    }
                                    promotionPendingBy = null
                                }) { Text("成らない") }
                            }
                        )
                    }
                }
            }
        }
    }

    // 初期配置を作成
    private fun createInitialBoard(): Map<Pair<Int, Int>, Piece> {
        val board = mutableMapOf<Pair<Int, Int>, Piece>()
        val firstRowTypes = listOf(
            PieceType.LANCE, PieceType.KNIGHT, PieceType.SILVER, PieceType.GOLD,
            PieceType.KING,
            PieceType.GOLD, PieceType.SILVER, PieceType.KNIGHT, PieceType.LANCE
        )
        for (col in 0 until 9) {
            board[Pair(8, col)] = Piece(firstRowTypes[col], Player.SENTE)
            board[Pair(0, col)] = Piece(firstRowTypes[col], Player.GOTE)
        }
        board[Pair(7, 7)] = Piece(PieceType.ROOK, Player.SENTE)
        board[Pair(7, 1)] = Piece(PieceType.BISHOP, Player.SENTE)
        board[Pair(1, 1)] = Piece(PieceType.ROOK, Player.GOTE)
        board[Pair(1, 7)] = Piece(PieceType.BISHOP, Player.GOTE)
        for (col in 0 until 9) {
            board[Pair(6, col)] = Piece(PieceType.PAWN, Player.SENTE)
            board[Pair(2, col)] = Piece(PieceType.PAWN, Player.GOTE)
        }
        return board.toMap()
    }

    // 駒の動きがルール通りか判定
    private fun isValidMovePattern(
        from: Pair<Int, Int>,
        to: Pair<Int, Int>,
        piece: Piece,
        board: Map<Pair<Int, Int>, Piece>
    ): Boolean {
        val r1 = from.first
        val c1 = from.second
        val r2 = to.first
        val c2 = to.second
        val dr = r2 - r1
        val dc = c2 - c1
        val adr = Math.abs(dr)
        val adc = Math.abs(dc)
        
        val forward = if (piece.owner == Player.SENTE) -1 else 1
        
        // 飛び道具の経路に駒がないかチェック
        fun isPathClear(): Boolean {
            val stepR = if (dr == 0) 0 else dr / adr
            val stepC = if (dc == 0) 0 else dc / adc
            var currR = r1 + stepR
            var currC = c1 + stepC
            while (currR != r2 || currC != c2) {
                if (board[Pair(currR, currC)] != null) return false
                currR += stepR
                currC += stepC
            }
            return true
        }

        return if (piece.isPromoted) {
            when (piece.type) {
                PieceType.ROOK -> { // 龍
                    val isRookMove = (dr == 0 || dc == 0) && isPathClear()
                    val isKingMove = adr <= 1 && adc <= 1
                    isRookMove || isKingMove
                }
                PieceType.BISHOP -> { // 馬
                    val isBishopMove = (adr == adc) && isPathClear()
                    val isKingMove = adr <= 1 && adc <= 1
                    isBishopMove || isKingMove
                }
                // 成銀、成桂、成香、と金は金と同じ動き
                else -> {
                    val isForward = dr == forward && adc <= 1
                    val isSideways = dr == 0 && adc == 1
                    val isBackward = dr == -forward && dc == 0
                    isForward || isSideways || isBackward
                }
            }
        } else {
            when (piece.type) {
                PieceType.PAWN -> dr == forward && dc == 0
                PieceType.LANCE -> dc == 0 && dr * forward > 0 && isPathClear()
                PieceType.KNIGHT -> dr == 2 * forward && adc == 1
                PieceType.SILVER -> {
                    val isForward3 = dr == forward && adc <= 1
                    val isBackDiagonal = dr == -forward && adc == 1
                    isForward3 || isBackDiagonal
                }
                PieceType.GOLD -> {
                    val isForward3 = dr == forward && adc <= 1
                    val isSideways = dr == 0 && adc == 1
                    val isBackward = dr == -forward && dc == 0
                    isForward3 || isSideways || isBackward
                }
                PieceType.KING -> adr <= 1 && adc <= 1
                PieceType.ROOK -> (dr == 0 || dc == 0) && isPathClear()
                PieceType.BISHOP -> (adr == adc) && isPathClear()
            }
        }
    }

    // 移動実行ヘルパー
    private fun executeMove(
        from: Pair<Int, Int>,
        to: Pair<Int, Int>,
        piece: Piece,
        captured: Piece?,
        promote: Boolean,
        currentBoard: Map<Pair<Int, Int>, Piece>,
        currentSenteHand: Map<PieceType, Int>,
        currentGoteHand: Map<PieceType, Int>,
        onUpdate: (Map<Pair<Int, Int>, Piece>, Map<PieceType, Int>, Map<PieceType, Int>) -> Unit
    ) {
        val newBoard = currentBoard.toMutableMap()
        var newSenteHand = currentSenteHand
        var newGoteHand = currentGoteHand

        if (captured != null) {
            // 駒を取る（成っている駒は元に戻す）
            val typeToAdd = captured.type
            if (piece.owner == Player.SENTE) {
                newSenteHand = newSenteHand.toMutableMap().apply { this[typeToAdd] = (this[typeToAdd] ?: 0) + 1 }
            } else {
                newGoteHand = newGoteHand.toMutableMap().apply { this[typeToAdd] = (this[typeToAdd] ?: 0) + 1 }
            }
        }

        newBoard.remove(from)
        newBoard[to] = piece.copy(isPromoted = promote || piece.isPromoted)
        onUpdate(newBoard, newSenteHand, newGoteHand)
    }
}

data class PendingMove(
    val from: Pair<Int, Int>,
    val to: Pair<Int, Int>,
    val piece: Piece,
    val captured: Piece?
)
