package com.example.shogigui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import org.json.JSONArray
import org.json.JSONObject
import com.example.shogigui.ui.theme.ShogiGUITheme

// 棋譜の1局面を管理するノードクラス
class KifuNode(
    val board: Map<Pair<Int, Int>, Piece>,
    val senteHand: Map<PieceType, Int>,
    val goteHand: Map<PieceType, Int>,
    val currentPlayer: Player,
    val moveLabel: String = "開始局面",
    val parent: KifuNode? = null,
    val lastFrom: Pair<Int, Int>? = null,
    val lastTo: Pair<Int, Int>? = null
) {
    val children = mutableStateListOf<KifuNode>()
    val moveCount: Int = (parent?.moveCount ?: -1) + 1
}

class MainActivity : ComponentActivity() {

    // Activityのフィールドとして保持
    private var rootNode: KifuNode? = null
    private var savedSenteName: String = "先手"
    private var savedGoteName: String = "後手"

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 開発中は毎回上書きコピーするようにして、確実に最新のファイルを届ける（バックグラウンド推奨だが一旦ここでも最小化）
//        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
//            copyAssetsToFileDir("nn.bin", "eval")
//        }
        // Activityライフサイクルで一度だけ読み込む
        if (rootNode == null) {
            val prefs = getSharedPreferences("kifu_prefs", MODE_PRIVATE)
            val saved = getSharedPreferences("kifu_prefs", MODE_PRIVATE)
                .getString("current_tree", null)
            savedSenteName = prefs.getString("sente_name", "先手") ?: "先手"
            savedGoteName = prefs.getString("gote_name", "後手") ?: "後手"
            rootNode = if (saved != null) {
                try { jsonToKifuTree(JSONObject(saved)) }
                catch (e: Exception) {
                    KifuNode(createInitialBoard(), emptyMap(), emptyMap(), Player.SENTE)
                }
            } else {
                KifuNode(createInitialBoard(), emptyMap(), emptyMap(), Player.SENTE)
            }
        }


        setContent {
            ShogiGUITheme {
                // 棋譜ツリーの状態管理
                var initialNode by remember { mutableStateOf(rootNode!!) }
                var currentNode by remember { mutableStateOf(initialNode) }
                // 現在のパス（スライダー用）を計算
                val currentPath = remember(currentNode, initialNode) {
                    val path = mutableListOf<KifuNode>()
                    // ルートから現在地まで
                    var p: KifuNode? = currentNode
                    while (p != null) { path.add(0, p); p = p.parent }
                    // 現在地から本譜（最初の子供）を辿る
                    var c = currentNode.children.firstOrNull()
                    while (c != null) { path.add(c); c = c.children.firstOrNull() }
                    path
                }

                // 現在の局面情報を取得
                val boardState = currentNode.board
                val senteHand = currentNode.senteHand
                val goteHand = currentNode.goteHand
                val currentPlayer = currentNode.currentPlayer

                // MainActivity 内に定義を追加
                var pvList by remember { mutableStateOf(mutableMapOf<Int, String>()) }

                var selectedSquare by remember { mutableStateOf<Pair<Int, Int>?>(null) }
                var selectedHandPiece by remember { mutableStateOf<Pair<Player, PieceType>?>(null) }
                var promotionPendingBy by remember { mutableStateOf<PendingMove?>(null) }
                
                var showMenu by remember { mutableStateOf(false) }
                val clipboardManager = LocalClipboardManager.current
                var isAnalysisMode by remember { mutableStateOf(false) }
                var isEngineReady by remember { mutableStateOf(false) }
                var isAnalyzing by remember { mutableStateOf(false) }
                
                // 対局者名
                var senteName by remember { mutableStateOf(savedSenteName) }
                var goteName by remember { mutableStateOf(savedGoteName) }
                
                // エンジンのインスタンスを保持
                val engine = remember { UsiEngine() }
                
                // エンジンからの出力を表示するための状態
                var engineOutput by remember { mutableStateOf("エンジン待機中...") }
                var engineLog by remember { mutableStateOf(listOf("エンジン待機中...")) }

                // エンジンからの出力を処理するロジック
                val processOutput = { rawLine: String ->
                    val line = rawLine.trim()
                    
                    // 外部ストレージ領域にログを保存 (PCから見える場所)
                    try {
                        val logFile = java.io.File(getExternalFilesDir(null), "debug_engine.txt")
                        logFile.appendText("${java.util.Date()}: $line\n")
                    } catch (e: Exception) {}

                    when {
                        line.contains("JNI:") -> {
                            engineOutput = line
                            engineLog = (engineLog + line).takeLast(50)
                        }
                        line == "usiok" -> {
                            val msg = "初期化完了 (usiok)"
                            engineOutput = msg
                            engineLog = (engineLog + msg).takeLast(50)
                            // ここで本気を出すためのオプションを設定（メモリを128MBに調整）
                            engine.sendCommand("setoption name Threads value 4")
                            engine.sendCommand("setoption name MultiPV value 3")
                            engine.sendCommand("setoption name USI_Hash value 128")
                            engine.sendCommand("setoption name BookFile value no_book")
                            engine.sendCommand("isready")
                        }
                        line == "readyok" -> {
                            isEngineReady = true
                            val msg = "準備完了。解析を開始します..."
                            engineOutput = msg
                            engineLog = (engineLog + msg).takeLast(50)
                            // 自動開始は LaunchedEffect 側に任せるため、ここでは何もしない
                        }
                        line.startsWith("info") -> {
                            // lineから "multipv X" を抽出
                            val multipvMatch = Regex("multipv (\\d+)").find(line)
                            val rank = multipvMatch?.groupValues?.get(1)?.toInt() ?: 1

                            val parsed = parseInfo(line, boardState, currentPlayer)
                            if (parsed.contains("評価") || parsed.contains("読み筋")) {
                                // 順位（rank）をキーにして保存
                                pvList[rank] = parsed
                                // リストを順位順に並べて結合し、表示用テキストを作る
                                engineOutput = pvList.toSortedMap().values.joinToString("\n---\n")
                            }
//                            if (parsed.contains("深さ") || parsed.contains("評価") || !isEngineReady) {
//                                engineOutput = parsed
//                                val logMsg = parsed.replace("\n", "  ")
//                                engineLog = (engineLog + logMsg).takeLast(50)
//                            } else {
//                                engineLog = (engineLog + line).takeLast(50)
//                            }
                        }
                        line.startsWith("bestmove") -> {
                            isAnalysisMode = false
                            isAnalyzing = false // 解析が終わったことを記録

                            val move = line.substringAfter("bestmove ").substringBefore(" ")
                            if (move != "ponder" && move != "(none)") {
                                val playerLabel = if (currentPlayer == Player.SENTE) "▲" else "△"
                                val formattedMove = formatUsiMove(move, boardState)
                                val finalMsg = "最善手: $playerLabel$formattedMove"
                                engineOutput = engineOutput + "\n" + finalMsg
                                engineLog = (engineLog + finalMsg).takeLast(50)
                            } else {
                                engineLog = (engineLog + line).takeLast(50)
                            }
                        }
                        else -> {
                            // option も含め、準備中はすべて表示して安心させる
                            if (!line.startsWith("option")) {
                                engineLog = (engineLog + line).takeLast(50)
                            }
                        }
                    }
                }

                // 常に最新の盤面状態を関数に反映させる
                SideEffect {
                    engine.onOutputReceived = { rawLine ->
                        // エンジン（バックグラウンド）からの通知を、確実にメインスレッドで処理する
                        runOnUiThread {
                            processOutput(rawLine)
                        }
                    }
                }

                // 盤面変化を監視してエンジンに通知（自動追従）

// LaunchedEffect の代わりに snapshotFlow を使う
                LaunchedEffect(isAnalysisMode, isEngineReady) {
                    if (isAnalysisMode && isEngineReady) {
                        snapshotFlow {
                            Triple(currentNode.board, currentNode.currentPlayer,
                                currentNode.senteHand to currentNode.goteHand)
                        }.collect { (board, player, hands) ->
                            engine.sendCommand("stop")
                            pvList.clear() // ★ここ：前の局面の次善手を消す
                            kotlinx.coroutines.delay(100)
                            val sfen = boardToSfen(board, player, hands.first, hands.second)
                            if (sfen.isNotEmpty()) {
                                engine.sendCommand("position sfen $sfen")
                                engine.sendCommand("go movetime 1000")
                                isAnalyzing = true
                            }
                        }
                    }
                }

                // アプリ起動時にエンジンを自動的に準備
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1000)
                    try {
                        // 評価関数ファイルのコピー
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            copyAssetsToFileDir("nn.bin", "eval")
                        }
                        
                        // 作業ディレクトリを filesDir に設定。
                        // これにより、エンジンが eval/nn.bin を正しく見つけられます。
                        engine.start(filesDir.absolutePath)
                    } catch (e: Exception) {
                        engineOutput = "エンジン起動失敗: ${e.message}"
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {

                    },
                    bottomBar = {
                        BottomAppBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {


                                // 読込ボタン
                                OutlinedButton(
                                    onClick = {
                                        val text = clipboardManager.getText()?.text
                                        if (text != null) {
                                            // 対局者名の抽出
                                            text.lines().forEach { line ->
                                                when {
                                                    line.startsWith("先手：") || line.startsWith("下手：") || line.startsWith("N+") ->
                                                        senteName = line.substringAfter("：").substringAfter("+").trim()
                                                    line.startsWith("後手：") || line.startsWith("上手：") || line.startsWith("N-") ->
                                                        goteName = line.substringAfter("：").substringAfter("-").trim()
                                                }
                                            }
                                            // 新しいルートノードを作成してリセット
                                            val freshRoot = KifuNode(createInitialBoard(), emptyMap(), emptyMap(), Player.SENTE)
                                            rootNode = freshRoot

                                            // CSA, KIF, USI を自動判別
                                            val newNode = when {
                                                text.lines().any { it.matches(Regex("^[+-]\\d{4}[A-Z]{2}.*")) } ->
                                                    parseCsa(text, freshRoot)
                                                text.contains("手数----") || text.contains("手合割") ||
                                                        text.contains("▲") || text.contains("△") ->
                                                    parseKif(text, freshRoot)
                                                text.contains("moves") ->
                                                    parseKifu(text, freshRoot)
                                                else ->
                                                    parseKif(text, freshRoot) ?: parseKifu(text, freshRoot)
                                            }
                                            if (newNode != null) {
                                                initialNode = freshRoot
                                                currentNode = newNode
                                                saveKifuTree(freshRoot)
                                            } else {
                                                initialNode = freshRoot
                                                currentNode = freshRoot
                                            }
                                            getSharedPreferences("kifu_prefs", MODE_PRIVATE).edit()
                                                .putString("sente_name", senteName)
                                                .putString("gote_name", goteName)
                                                .apply()
                                        }

                                    },
                                    modifier = Modifier
                                        .weight(0.4f)
                                        .height(72.dp),
                                    shape = MaterialTheme.shapes.extraLarge,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste, // クリップボードからの読込を象徴
                                            contentDescription = "棋譜読込",
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            text = "読込",
                                            style = MaterialTheme.typography.labelSmall // アイコンの下に小さく文字を添える
                                        )
                                    }
                                }

                                // 解析ボタン
                                Button(
                                    onClick = {
                                        if (!isEngineReady) return@Button
                                        if (isAnalysisMode) {
                                            isAnalysisMode = false
                                            engine.sendCommand("stop")
                                        } else {
                                            isAnalysisMode = true
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(0.6f)
                                        .height(72.dp)
                                        .graphicsLayer {
                                            if (!isEngineReady) this.alpha = alpha
                                        },
                                    shape = MaterialTheme.shapes.extraLarge,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = when {
                                            !isEngineReady -> MaterialTheme.colorScheme.surfaceVariant
                                            isAnalysisMode -> MaterialTheme.colorScheme.tertiaryContainer
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                ) {
                                    if (!isEngineReady) {
                                        LoadingIndicator(
                                            modifier = Modifier.padding(end = 16.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "エンジン準備中",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = if (isAnalysisMode) "停止" else "解析",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = if (isAnalysisMode)
                                                MaterialTheme.colorScheme.onTertiaryContainer
                                            else MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                                // メニューボタン
                                Box {
                                    IconButton(
                                        onClick = { showMenu = true },
                                        modifier = Modifier
                                            .height(72.dp)
                                            .size(56.dp)
                                    ) {
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
                                                currentNode = initialNode
                                                initialNode.children.clear()
                                                selectedSquare = null
                                                selectedHandPiece = null
                                                getSharedPreferences("kifu_prefs", MODE_PRIVATE).edit().remove("current_tree").apply()
                                                showMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("クリップボードから読み込み") },
                                            onClick = {
                                                val text = clipboardManager.getText()?.text
                                                if (text != null) {
                                                    val freshRoot2 = KifuNode(createInitialBoard(), emptyMap(), emptyMap(), Player.SENTE)
                                                    rootNode = freshRoot2
                                                    val newNode = parseKifu(text, freshRoot2)
                                                    if (newNode != null) {
                                                        initialNode = freshRoot2
                                                        currentNode = newNode
                                                        saveKifuTree(freshRoot2)
                                                    }
                                                }
                                                showMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("エンジンの設定") },
                                            onClick = { showMenu = false }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("解析停止") },
                                            onClick = {
                                                isAnalysisMode = false
                                                engine.sendCommand("stop")
                                                showMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        // エンジンの解析情報を表示
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            colors = CardDefaults.cardColors(
                                // 解析中（isAnalysisMode）かどうかに合わせて色を変えると分かりやすいです
                                containerColor = if (isAnalysisMode)
                                    MaterialTheme.colorScheme.tertiaryContainer
                                else
                                    MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            // 履歴全体を常に表示
                            Text(
                                text = engineOutput,
                                    //engineLog.joinToString("\n"),
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                minLines = 1,
                                maxLines = 4
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
                            lastFrom = currentNode.lastFrom,
                            lastTo = currentNode.lastTo,
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
                                            val moveLabel = "${9 - col}${rowToKanji('a' + row)}${type.label}打"
                                            executeMove(null, clickedPos, Piece(type, player), null, false, currentNode, moveLabel) { nextNode ->
                                                currentNode = nextNode
                                            }
                                            selectedHandPiece = null
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
                                                        val moveLabel = formatUsiMove("${9-currentSelected.second}${('a'+currentSelected.first)}${9-clickedPos.second}${('a'+clickedPos.first)}+", boardState)
                                                        executeMove(currentSelected, clickedPos, movingPiece, targetPiece, true, currentNode, moveLabel) { nextNode ->
                                                            currentNode = nextNode
                                                        }
                                                    } else if (canPromote && enteringZone) {
                                                        promotionPendingBy = PendingMove(currentSelected, clickedPos, movingPiece, targetPiece)
                                                    } else {
                                                        val moveLabel = formatUsiMove("${9-currentSelected.second}${('a'+currentSelected.first)}${9-clickedPos.second}${('a'+clickedPos.first)}", boardState)
                                                        executeMove(currentSelected, clickedPos, movingPiece, targetPiece, false, currentNode, moveLabel) { nextNode ->
                                                            currentNode = nextNode
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

                        // 局面操作スライダー
                        Column(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                            val turnMark = if (currentNode.moveCount > 0) {
                                if (currentNode.parent?.currentPlayer == Player.SENTE) "▲" else "△"
                            } else ""
                            Text(
                                text = "${currentNode.moveCount}手目: $turnMark${currentNode.moveLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 一手戻るボタン
                                FilledTonalIconButton(
                                    onClick = {
                                        currentNode.parent?.let { currentNode = it }
                                    },
                                    enabled = currentNode.parent != null,
                                    modifier = Modifier.size(48.dp),
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "一手戻る"
                                    )
                                }

                                Slider(
                                    value = currentNode.moveCount.toFloat(),
                                    onValueChange = { pos ->
                                        val targetIndex = pos.toInt()
                                        currentPath.getOrNull(targetIndex)?.let { currentNode = it }
                                    },
                                    valueRange = 0f..(currentPath.size - 1).coerceAtLeast(1).toFloat(),
                                    steps = if (currentPath.size > 2) currentPath.size - 2 else 0,
                                    modifier = Modifier.weight(1f)
                                )

                                // 一手進むボタン
                                FilledTonalIconButton(
                                    onClick = {
                                        currentNode.children.firstOrNull()?.let { currentNode = it }
                                    },
                                    enabled = currentNode.children.isNotEmpty(),
                                    modifier = Modifier.size(48.dp),
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "一手進む"
                                    )
                                }
                            }
                        }
                        
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 先手情報
                            var sentePulsed by remember { mutableStateOf(false) }

                            LaunchedEffect(currentPlayer) {
                                if (currentPlayer == Player.SENTE) {
                                    sentePulsed = true
                                    kotlinx.coroutines.delay(200)  // 次のcurrentPlayer変化でキャンセルされる
                                    sentePulsed = false
                                } else {
                                    sentePulsed = false  // 即座に戻す
                                }
                            }
                            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                            FilledTonalIconButton(
                                onClick = {},
                                shapes = IconButtonDefaults.shapes(
                                    shape = if (!sentePulsed)
                                        MaterialTheme.shapes.small
                                    else
                                        MaterialTheme.shapes.largeIncreased       // 手番でないとき: 角ばった
                                ),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (currentPlayer == Player.SENTE)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (currentPlayer == Player.SENTE)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 8.dp)
                                    .height(48.dp)
                            ) {
                                PlayerInfoContent(senteName, "先手▲")
                            }

                            // 後手情報
                            var gotePulsed by remember { mutableStateOf(false) }

                            LaunchedEffect(currentPlayer) {
                                if (currentPlayer == Player.GOTE) {
                                    gotePulsed = true
                                    kotlinx.coroutines.delay(200)
                                    gotePulsed = false
                                } else {
                                    gotePulsed = false
                                }
                            }
                            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                            FilledTonalIconButton(
                                onClick = {},
                                shapes = IconButtonDefaults.shapes(
                                    shape = if (gotePulsed)
                                        MaterialTheme.shapes.small
                                    else
                                        MaterialTheme.shapes.largeIncreased       // 手番でないとき: 角ばった
                                ),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (currentPlayer == Player.GOTE)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (currentPlayer == Player.GOTE)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 8.dp)
                                    .height(48.dp)
                            ) {
                                PlayerInfoContent(goteName, "後手△")
                            }
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
                                    val label = formatUsiMove("${9-move.from.second}${('a'+move.from.first)}${9-move.to.second}${('a'+move.to.first)}+", boardState)
                                    executeMove(move.from, move.to, move.piece, move.captured, true, currentNode, label) { nextNode ->
                                        currentNode = nextNode
                                    }
                                    promotionPendingBy = null
                                }) { Text("成る") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    val label = formatUsiMove("${9-move.from.second}${('a'+move.from.first)}${9-move.to.second}${('a'+move.to.first)}", boardState)
                                    executeMove(move.from, move.to, move.piece, move.captured, false, currentNode, label) { nextNode ->
                                        currentNode = nextNode
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

    // USIのinfo行を解析して読みやすい文字列にする
    private fun parseInfo(line: String, currentBoard: Map<Pair<Int, Int>, Piece>, turn: Player): String {
        // 正規表現や複数の空白に対応するために正規化
        val parts = line.split(Regex("\\s+"))
        var depth = ""
        var score = ""
        var pv = ""

        var i = 0
        while (i < parts.size) {
            when (parts[i]) {
                "depth" -> if (i + 1 < parts.size) depth = parts[++i]
                "score" -> {
                    if (i + 2 < parts.size) {
                        val type = parts[++i]
                        val value = parts[++i]
                        score = when (type) {
                            "cp" -> {
                                val v = value.toIntOrNull() ?: 0
                                val sign = if (v > 0) "+" else ""
                                "評価: $sign$v"
                            }
                            "mate" -> "詰み: $value"
                            else -> ""
                        }
                    }
                }
                "pv" -> {
                    val pvMoves = parts.drop(i + 1)
                    if (pvMoves.isNotEmpty()) {
                        val formattedMoves = mutableListOf<String>()
                        var tempTurn = turn
                        var tempBoard = currentBoard  // 現在の盤面からスタート

                        pvMoves.take(6).forEach { moveStr ->
                            val symbol = if (tempTurn == Player.SENTE) "▲" else "△"
                            val formatted = formatUsiMove(moveStr, tempBoard)  // 現在の盤面で駒名を特定
                            formattedMoves.add("$symbol$formatted")

                            tempBoard = applyUsiMove(moveStr, tempBoard)  // 盤面を1手進める
                            tempTurn = if (tempTurn == Player.SENTE) Player.GOTE else Player.SENTE
                        }
                        pv = "読み筋: " + formattedMoves.joinToString(" ")
                    }
                    break
                }
            }
            i++
        }

        val result = mutableListOf<String>()
        if (depth.isNotEmpty()) result.add("深さ: $depth")
        if (score.isNotEmpty()) result.add(score)
        if (pv.isNotEmpty()) result.add(pv)

        return result.joinToString("\n")
    }

    // USI形式の指し手(7g7f)を日本語座標(7七銀)に変換
    private fun formatUsiMove(usiMove: String, board: Map<Pair<Int, Int>, Piece>? = null): String {
        if (usiMove.length < 4) return usiMove

        fun colToNum(c: Char) = c.toString()
        fun rowToKanji(c: Char) = when(c) {
            'a' -> "一"; 'b' -> "二"; 'c' -> "三"; 'd' -> "四"; 'e' -> "五"
            'f' -> "六"; 'g' -> "七"; 'h' -> "八"; 'i' -> "九"
            else -> c.toString()
        }

        return try {
            if (usiMove[1] == '*') {
                // 持ち駒を打つ (P*5e → 5五歩打)
                val piece = when(usiMove[0]) {
                    'P' -> "歩"; 'L' -> "香"; 'N' -> "桂"; 'S' -> "銀"
                    'G' -> "金"; 'B' -> "角"; 'R' -> "飛"
                    else -> usiMove[0].toString()
                }
                "${colToNum(usiMove[2])}${rowToKanji(usiMove[3])}${piece}打"
            } else {
                // 通常移動 (2g2f → 2六)
                val fromCol = usiMove[0] - '0'
                val fromRow = usiMove[1] - 'a'
                val piece = board?.get(Pair(fromRow, 9 - fromCol))
                val pieceLabel = piece?.let {
                    if (it.isPromoted) it.type.promotedLabel ?: it.type.label else it.type.label
                } ?: ""

                val toCol = colToNum(usiMove[2])
                val toRow = rowToKanji(usiMove[3])
                val prom = if (usiMove.endsWith("+")) "成" else ""

                "${toCol}${toRow}${pieceLabel}${prom}"
            }
        } catch (e: Exception) {
            usiMove
        }
    }

    // Assetsから評価関数をコピー
    private fun copyAssetsToFileDir(filename: String, subDir: String = "") {
        val targetDir = if (subDir.isNotEmpty()) {
            val dir = java.io.File(filesDir, subDir)
            if (!dir.exists()) dir.mkdirs()
            dir
        } else {
            filesDir
        }
        
        val file = java.io.File(targetDir, filename)
        // 開発中は毎回上書きコピーするようにして、確実に最新のファイルを届ける
        // if (file.exists()) return 

        try {
            assets.open(filename).use { inputStream ->
                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            android.util.Log.d("ShogiGUI", "File copied to: ${file.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("ShogiGUI", "Copy failed: ${e.message}")
        }
    }

    // 盤面状態をSFEN形式に変換
    private fun boardToSfen(
        board: Map<Pair<Int, Int>, Piece>,
        turn: Player,
        senteHand: Map<PieceType, Int>,
        goteHand: Map<PieceType, Int>
    ): String {
        val sfen = StringBuilder()
        
        // 1. 盤面
        for (row in 0 until 9) {
            var emptyCount = 0
            for (col in 0 until 9) {
                val piece = board[Pair(row, col)]
                if (piece == null) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        sfen.append(emptyCount)
                        emptyCount = 0
                    }
                    val char = when (piece.type) {
                        PieceType.KING -> 'k'
                        PieceType.ROOK -> 'r'
                        PieceType.BISHOP -> 'b'
                        PieceType.GOLD -> 'g'
                        PieceType.SILVER -> 's'
                        PieceType.KNIGHT -> 'n'
                        PieceType.LANCE -> 'l'
                        PieceType.PAWN -> 'p'
                    }
                    val sfenChar = if (piece.owner == Player.SENTE) char.uppercaseChar() else char
                    if (piece.isPromoted) sfen.append('+')
                    sfen.append(sfenChar)
                }
            }
            if (emptyCount > 0) sfen.append(emptyCount)
            if (row < 8) sfen.append('/')
        }
        
        // 2. 手番
        sfen.append(if (turn == Player.SENTE) " b " else " w ")
        
        // 3. 持ち駒
        if (senteHand.isEmpty() && goteHand.isEmpty()) {
            sfen.append("-")
        } else {
            fun appendHand(hand: Map<PieceType, Int>, isSente: Boolean) {
                val types = listOf(
                    PieceType.ROOK to 'R', PieceType.BISHOP to 'B',
                    PieceType.GOLD to 'G', PieceType.SILVER to 'S', PieceType.KNIGHT to 'N',
                    PieceType.LANCE to 'L', PieceType.PAWN to 'P'
                )
                for ((type, char) in types) {
                    val count = hand[type] ?: 0
                    if (count > 0) {
                        if (count > 1) sfen.append(count)
                        sfen.append(if (isSente) char else char.lowercaseChar())
                    }
                }
            }
            appendHand(senteHand, true)
            appendHand(goteHand, false)
        }
        
        // 4. 手数（ここでは1固定）
        sfen.append(" 1")
        val result = sfen.toString()
        android.util.Log.d("ShogiSFEN", "SFEN: $result")
        // 簡易バリデーション
        val handPart = result.substringAfterLast(" ").substringBefore(" ")
        android.util.Log.d("ShogiSFEN", "Hand: $handPart")
        return sfen.toString()
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

    // 移動実行ヘルパー（棋譜ツリー対応）
    private fun executeMove(
        from: Pair<Int, Int>?,
        to: Pair<Int, Int>,
        piece: Piece,
        captured: Piece?,
        promote: Boolean,
        parentNode: KifuNode,
        label: String,
        onUpdate: (KifuNode) -> Unit
    ) {
        val existing = parentNode.children.find { it.moveLabel == label }
        if (existing != null) {
            onUpdate(existing)
            return
        }

        val newBoard = parentNode.board.toMutableMap()
        var newSenteHand = parentNode.senteHand
        var newGoteHand = parentNode.goteHand

        if (from == null) {
            android.util.Log.d("executeMove", "打ち: ${piece.type.label} owner=${piece.owner}")
            // 持ち駒を打つ → 持ち駒を減らす
            if (piece.owner == Player.SENTE) {
                newSenteHand = newSenteHand.toMutableMap().apply {
                    val count = this[piece.type] ?: 0
                    if (count > 1) this[piece.type] = count - 1 else remove(piece.type)
                }
            } else {
                newGoteHand = newGoteHand.toMutableMap().apply {
                    val count = this[piece.type] ?: 0
                    if (count > 1) this[piece.type] = count - 1 else remove(piece.type)
                }
            }
        }

        if (captured != null) {
            // 駒を取る → 持ち駒を増やす（王将は除く）
            val typeToAdd = captured.type
            if (typeToAdd != PieceType.KING) {
                if (piece.owner == Player.SENTE) {
                    newSenteHand = newSenteHand.toMutableMap().apply {
                        this[typeToAdd] = (this[typeToAdd] ?: 0) + 1
                    }
                } else {
                    newGoteHand = newGoteHand.toMutableMap().apply {
                        this[typeToAdd] = (this[typeToAdd] ?: 0) + 1
                    }
                }
            }
        }

        if (from != null) newBoard.remove(from)
        newBoard[to] = piece.copy(isPromoted = promote || piece.isPromoted)

        val nextPlayer = if (parentNode.currentPlayer == Player.SENTE) Player.GOTE else Player.SENTE
        val newNode = KifuNode(newBoard, newSenteHand, newGoteHand, nextPlayer, label, parentNode, from, to)

        parentNode.children.add(newNode)
        saveKifuTree(getRootNode(parentNode))
        onUpdate(newNode)
    }

    private fun getRootNode(node: KifuNode): KifuNode {
        var p = node
        while (p.parent != null) p = p.parent!!
        return p
    }

    private fun saveKifuTree(root: KifuNode) {
        val json = kifuTreeToJson(root)
        getSharedPreferences("kifu_prefs", MODE_PRIVATE).edit().putString("current_tree", json.toString()).apply()
    }

    private fun kifuTreeToJson(node: KifuNode): JSONObject {
        val json = JSONObject()
        json.put("turn", node.currentPlayer.name)
        json.put("label", node.moveLabel)
        
        // 盤面
        val boardJson = JSONObject()
        node.board.forEach { (pos, piece) ->
            val pJson = JSONObject()
            pJson.put("t", piece.type.name)
            pJson.put("o", piece.owner.name)
            pJson.put("p", piece.isPromoted)
            boardJson.put("${pos.first},${pos.second}", pJson)
        }
        json.put("board", boardJson)

        // 持ち駒
        val senteHandJson = JSONObject()
        node.senteHand.forEach { (t, c) -> senteHandJson.put(t.name, c) }
        json.put("senteHand", senteHandJson)

        val goteHandJson = JSONObject()
        node.goteHand.forEach { (t, c) -> goteHandJson.put(t.name, c) }
        json.put("goteHand", goteHandJson)

        // ハイライト
        json.put("lastFrom", if (node.lastFrom != null) "${node.lastFrom.first},${node.lastFrom.second}" else null)
        json.put("lastTo", if (node.lastTo != null) "${node.lastTo.first},${node.lastTo.second}" else null)

        // 子要素 (再帰)
        val childrenJson = JSONArray()
        node.children.forEach { childrenJson.put(kifuTreeToJson(it)) }
        json.put("children", childrenJson)
        
        return json
    }

    private fun jsonToKifuTree(json: JSONObject, parent: KifuNode? = null): KifuNode {
        val board = mutableMapOf<Pair<Int, Int>, Piece>()
        val boardJson = json.getJSONObject("board")
        boardJson.keys().forEach { key ->
            val pos = key.split(",").let { Pair(it[0].toInt(), it[1].toInt()) }
            val pJson = boardJson.getJSONObject(key)
            board[pos] = Piece(
                PieceType.valueOf(pJson.getString("t")),
                Player.valueOf(pJson.getString("o")),
                pJson.getBoolean("p")
            )
        }

        val senteHand = mutableMapOf<PieceType, Int>()
        val sHandJson = json.getJSONObject("senteHand")
        sHandJson.keys().forEach { senteHand[PieceType.valueOf(it)] = sHandJson.getInt(it) }

        val goteHand = mutableMapOf<PieceType, Int>()
        val gHandJson = json.getJSONObject("goteHand")
        gHandJson.keys().forEach { goteHand[PieceType.valueOf(it)] = gHandJson.getInt(it) }

        val lastFrom = json.optString("lastFrom", null)?.takeIf { it != "null" && it.isNotEmpty() }?.split(",")?.let { Pair(it[0].toInt(), it[1].toInt()) }
        val lastTo = json.optString("lastTo", null)?.takeIf { it != "null" && it.isNotEmpty() }?.split(",")?.let { Pair(it[0].toInt(), it[1].toInt()) }

        val node = KifuNode(
            board, senteHand, goteHand, 
            Player.valueOf(json.getString("turn")),
            json.getString("label"),
            parent,
            lastFrom,
            lastTo
        )

        val childrenJson = json.getJSONArray("children")
        for (i in 0 until childrenJson.length()) {
            node.children.add(jsonToKifuTree(childrenJson.getJSONObject(i), node))
        }

        return node
    }

    // KIF形式（通常版および簡易版）を解析
    private fun parseKif(text: String, root: KifuNode): KifuNode? {
        var tempNode = root
        var lastToPos: Pair<Int, Int>? = null

        text.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("*") || line.startsWith("#")) return@forEach
            val exitWords = listOf("中断", "投了", "持将棋", "千日手", "切れ負け", "詰み", "まで")
            if (exitWords.any { line.contains(it) }) return@forEach

            // 手数行にマッチ: "   1 ７六歩(77)   (00:01/00:00:01)"
            val lineMatch = Regex("""^\d+\s+(.+?)(?:\s*\(\d+:\d+/[\d:]+\))?\s*$""")
                .find(line) ?: return@forEach
            val movePart = lineMatch.groupValues[1].trim()
            if (movePart.isEmpty()) return@forEach

            try {
                // 同〇〇 の判定
                val isSame = movePart.startsWith("同")

                val toPos = if (isSame) {
                    lastToPos ?: return@forEach
                } else {
                    val colChar = movePart[0]
                    val rowChar = movePart[1]
                    val col = "１２３４５６７８９".indexOf(colChar).takeIf { it != -1 } ?: return@forEach
                    val row = "一二三四五六七八九".indexOf(rowChar).takeIf { it != -1 } ?: return@forEach
                    Pair(row, 8 - col)
                }
                lastToPos = toPos

                val isDrop = movePart.contains("打")

                // 元マス (col行) を抽出
                val fromMatch = Regex("""\((\d)(\d)\)""").find(movePart)
                val fromPos = when {
                    isDrop -> Pair(-1, -1)
                    fromMatch != null -> {
                        val fCol = 9 - fromMatch.groupValues[1].toInt()
                        val fRow = fromMatch.groupValues[2].toInt() - 1
                        Pair(fRow, fCol)
                    }
                    else -> return@forEach
                }

                val piece = if (isDrop) {
                    // 駒名を抽出
                    val pieceStr = movePart
                        .replace(Regex("^同[　 ]?"), "")
                        .replace(Regex("^[１-９][一二三四五六七八九]"), "")
                        .replace("打", "").trim()
                    val type = PieceType.entries.find { pieceStr.contains(it.label) } ?: return@forEach
                    Piece(type, tempNode.currentPlayer)
                } else {
                    tempNode.board[fromPos] ?: return@forEach
                }

                // 成り判定: 元の駒が未成で、movePart に「成」が含まれ、かつ駒名の一部でない
                val promote = !piece.isPromoted &&
                        piece.type.promotedLabel != null &&
                        movePart.contains("成") &&
                        !movePart.contains("成${piece.type.label}") // 「成銀」等の成駒名は除く

                val label = if (isDrop) {
                    "${9 - toPos.second}${rowToKanji('a' + toPos.first)}${piece.type.label}打"
                } else {
                    formatUsiMove(
                        "${9 - fromPos.second}${('a' + fromPos.first)}${9 - toPos.second}${('a' + toPos.first)}${if (promote) "+" else ""}",
                        tempNode.board
                    )
                }

                executeMove(
                    if (isDrop) null else fromPos,
                    toPos, piece, tempNode.board[toPos],
                    promote, tempNode, label
                ) { tempNode = it }
                android.util.Log.d("parseKif",
                    "手目=${tempNode.moveCount} label=${tempNode.moveLabel} 先手持ち駒=${tempNode.senteHand} 後手持ち駒=${tempNode.goteHand}")
            } catch (e: Exception) {
                android.util.Log.e("parseKif", "Error: ${e.message}, line: $line")
            }
        }
        return if (tempNode != root) tempNode else null
    }

    // 指定された場所へ指定された駒を動かせる元のマスを探す
    private fun findSourceSquare(to: Pair<Int, Int>, label: String, node: KifuNode): Pair<Int, Int>? {
        if (label.contains("打")) return Pair(-1, -1) // 駒打ちフラグ
        
        val type = PieceType.entries.find { label.contains(it.label) || (it.promotedLabel != null && label.contains(it.promotedLabel)) } ?: return null
        
        // 盤面上の自分の駒をしらみつぶしに探す
        for ((pos, piece) in node.board) {
            if (piece.owner == node.currentPlayer) {
                // 駒の種類が一致（成駒も考慮）
                val pieceMatches = if (label.contains("成") && !piece.isPromoted) {
                    piece.type == type // 成る前の種類が一致
                } else {
                    val currentLabel = if (piece.isPromoted) piece.type.promotedLabel ?: piece.type.label else piece.type.label
                    label.contains(currentLabel)
                }

                if (pieceMatches) {
                    if (isValidMovePattern(pos, to, piece, node.board)) {
                        return pos
                    }
                }
            }
        }
        
        // どこにも見つからなければ、それは駒打ちの可能性がある（「打」と書いていなくても）
        if (node.currentPlayer == Player.SENTE && node.senteHand.containsKey(type)) return Pair(-1, -1)
        if (node.currentPlayer == Player.GOTE && node.goteHand.containsKey(type)) return Pair(-1, -1)
        
        return null
    }

    // CSA形式の棋譜を解析してツリーを構築
    private fun parseCsa(text: String, root: KifuNode): KifuNode? {
        val lines = text.lines()
        var tempNode = root
        
        // CSAの指し手は +2726FU や -8384FU のような形式
        val moveRegex = Regex("^[+-](\\d{2})(\\d{2})([A-Z]{2})")
        
        lines.forEach { line ->
            val match = moveRegex.find(line.trim())
            if (match != null) {
                try {
                    val fromStr = match.groupValues[1]
                    val toStr = match.groupValues[2]
                    val pieceStr = match.groupValues[3]
                    
                    // 座標変換 (CSA 1-9 -> 0-8)
                    val fromCol = if (fromStr == "00") null else 9 - (fromStr[0] - '0')
                    val fromRow = if (fromStr == "00") null else (fromStr[1] - '0') - 1
                    
                    val toCol = 9 - (toStr[0] - '0')
                    val toRow = (toStr[1] - '0') - 1
                    
                    val (type, isPromoted) = when (pieceStr) {
                        "FU" -> PieceType.PAWN to false
                        "KY" -> PieceType.LANCE to false
                        "KE" -> PieceType.KNIGHT to false
                        "GI" -> PieceType.SILVER to false
                        "KI" -> PieceType.GOLD to false
                        "KA" -> PieceType.BISHOP to false
                        "HI" -> PieceType.ROOK to false
                        "OU" -> PieceType.KING to false
                        "TO" -> PieceType.PAWN to true
                        "NY" -> PieceType.LANCE to true
                        "NK" -> PieceType.KNIGHT to true
                        "NG" -> PieceType.SILVER to true
                        "UM" -> PieceType.BISHOP to true
                        "RY" -> PieceType.ROOK to true
                        else -> return@forEach
                    }

                    val movingPiece = if (fromStr == "00") {
                        Piece(type, tempNode.currentPlayer)
                    } else {
                        tempNode.board[Pair(fromRow!!, fromCol!!)] ?: return@forEach
                    }
                    
                    val captured = tempNode.board[Pair(toRow, toCol)]
                    val label = if (fromStr == "00") {
                        "${toStr[0]}${rowToKanji('a' + toRow)}${type.label}打"
                    } else {
                        formatUsiMove("${fromStr[0]}${('a' + fromRow!!)}${toStr[0]}${('a' + toRow)}${if (isPromoted && !movingPiece.isPromoted) "+" else ""}", tempNode.board)
                    }
                    
                    executeMove(
                        if (fromStr == "00") null else Pair(fromRow!!, fromCol!!),
                        Pair(toRow, toCol),
                        movingPiece,
                        captured,
                        isPromoted && !movingPiece.isPromoted,
                        tempNode,
                        label
                    ) {
                        tempNode = it
                    }
                } catch (e: Exception) {}
            }
        }
        return if (tempNode != root) tempNode else null
    }

    // USI形式の棋譜を解析してツリーを構築
    private fun parseKifu(text: String, root: KifuNode): KifuNode? {
        // "position startpos moves 7g7f 3c3d..." などの形式に対応
        val movesPart = if (text.contains("moves")) text.substringAfter("moves").trim() else text.trim()
        val moves = movesPart.split(Regex("\\s+")).filter { it.length >= 4 }
        
        var tempNode = root
        moves.forEach { moveStr ->
            try {
                if (moveStr[1] == '*') {
                    // 駒打ち
                    val type = when(moveStr[0]) {
                        'P' -> PieceType.PAWN; 'L' -> PieceType.LANCE; 'N' -> PieceType.KNIGHT
                        'S' -> PieceType.SILVER; 'G' -> PieceType.GOLD; 'B' -> PieceType.BISHOP; 'R' -> PieceType.ROOK
                        else -> return@forEach
                    }
                    val toCol = 9 - (moveStr[2] - '0')
                    val toRow = moveStr[3] - 'a'
                    val label = "${moveStr[2]}${rowToKanji(moveStr[3])}${type.label}打"
                    
                    executeMove(null, Pair(toRow, toCol), Piece(type, tempNode.currentPlayer), null, false, tempNode, label) {
                        tempNode = it
                    }
                    // parseKif の executeMove 呼び出し後に追加
                    android.util.Log.d("parseKif",
                        "手目=${tempNode.moveCount} 先手持ち駒=${tempNode.senteHand} 後手持ち駒=${tempNode.goteHand}")
                } else {
                    // 通常移動
                    val fromCol = 9 - (moveStr[0] - '0')
                    val fromRow = moveStr[1] - 'a'
                    val toCol = 9 - (moveStr[2] - '0')
                    val toRow = moveStr[3] - 'a'
                    val promote = moveStr.endsWith("+")
                    
                    val piece = tempNode.board[Pair(fromRow, fromCol)] ?: return@forEach
                    val captured = tempNode.board[Pair(toRow, toCol)]
                    val label = formatUsiMove(moveStr, tempNode.board)
                    
                    executeMove(Pair(fromRow, fromCol), Pair(toRow, toCol), piece, captured, promote, tempNode, label) {
                        tempNode = it
                    }
                    // parseKif の executeMove 呼び出し後に追加
                    android.util.Log.d("parseKif",
                        "手目=${tempNode.moveCount} 先手持ち駒=${tempNode.senteHand} 後手持ち駒=${tempNode.goteHand}")
                }
            } catch (e: Exception) {}
        }
        return if (tempNode != root) tempNode else null
    }

    private fun rowToKanji(c: Char) = when(c) {
        'a' -> "一"; 'b' -> "二"; 'c' -> "三"; 'd' -> "四"; 'e' -> "五"
        'f' -> "六"; 'g' -> "七"; 'h' -> "八"; 'i' -> "九"
        else -> c.toString()
    }

    // USI形式の1手を盤面に適用して新しい盤面を返す
    private fun applyUsiMove(
        usiMove: String,
        board: Map<Pair<Int, Int>, Piece>
    ): Map<Pair<Int, Int>, Piece> {
        if (usiMove.length < 4) return board
        val newBoard = board.toMutableMap()

        return try {
            if (usiMove[1] == '*') {
                // 持ち駒を打つ (P*5e)
                val type = when(usiMove[0]) {
                    'P' -> PieceType.PAWN; 'L' -> PieceType.LANCE; 'N' -> PieceType.KNIGHT
                    'S' -> PieceType.SILVER; 'G' -> PieceType.GOLD
                    'B' -> PieceType.BISHOP; 'R' -> PieceType.ROOK
                    else -> return board
                }
                val toCol = usiMove[2] - '0'
                val toRow = usiMove[3] - 'a'
                // 手番は盤面から推定できないのでSENTEとして仮置き（表示用なので問題なし）
                newBoard[Pair(toRow, 9 - toCol)] = Piece(type, Player.SENTE)
                newBoard
            } else {
                // 通常移動 (2g2f)
                val fromCol = usiMove[0] - '0'
                val fromRow = usiMove[1] - 'a'
                val toCol = usiMove[2] - '0'
                val toRow = usiMove[3] - 'a'
                val promote = usiMove.endsWith("+")

                val fromPos = Pair(fromRow, 9 - fromCol)
                val toPos = Pair(toRow, 9 - toCol)
                val piece = newBoard[fromPos] ?: return board

                newBoard.remove(fromPos)
                newBoard[toPos] = piece.copy(isPromoted = piece.isPromoted || promote)
                newBoard
            }
        } catch (e: Exception) {
            board
        }
    }
}

@Composable
fun PlayerInfoContent(name: String, mark: String) {
    // コンテキスト外に出すために、まずデフォルトのサイズを取得する
    val defaultFontSize = MaterialTheme.typography.titleMedium.fontSize
    var fontSize by remember(name) { mutableStateOf(defaultFontSize) }

    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$mark ",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = fontSize),
            maxLines = 1,
            softWrap = false,
            onTextLayout = { textLayoutResult ->
                // 表示がはみ出している場合、サイズを小さくして再描画を促す
                if (textLayoutResult.hasVisualOverflow && fontSize > 8.sp) {
                    fontSize *= 0.9f
                }
            }
        )
    }
}

data class PendingMove(
    val from: Pair<Int, Int>,
    val to: Pair<Int, Int>,
    val piece: Piece,
    val captured: Piece?
)
