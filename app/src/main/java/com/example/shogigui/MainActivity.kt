package com.example.shogigui

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import com.example.shogigui.ui.theme.ShogiGUITheme
import kotlinx.coroutines.delay
import android.util.Log
import androidx.compose.ui.unit.DpOffset

class MainActivity : ComponentActivity() {

    private var rootNode: KifuNode? = null
    private var savedSenteName: String = "先手"
    private var savedGoteName: String = "後手"

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("kifu_prefs", MODE_PRIVATE)
        if (rootNode == null) {
            val saved = prefs.getString("current_tree", null)
            savedSenteName = prefs.getString("sente_name", "先手") ?: "先手"
            savedGoteName = prefs.getString("gote_name", "後手") ?: "後手"
            rootNode = if (saved != null) {
                try { jsonToKifuTree(JSONObject(saved)) }
                catch (e: Exception) { KifuNode(createInitialBoard(), emptyMap(), emptyMap(), Player.SENTE) }
            } else {
                KifuNode(createInitialBoard(), emptyMap(), emptyMap(), Player.SENTE)
            }
        }

        setContent {
            ShogiGUITheme {
                val initialNode by remember { mutableStateOf(rootNode!!) }
                var currentNode by remember { mutableStateOf(initialNode) }
                
                val saveKifu = { node: KifuNode ->
                    val root = getRootNode(node)
                    prefs.edit().putString("current_tree", kifuTreeToJson(root).toString()).apply()
                }

                // --- 状態管理 ---
                val currentPath = remember(currentNode, initialNode) {
                    val path = mutableListOf<KifuNode>()
                    var p: KifuNode? = currentNode
                    while (p != null) { path.add(0, p); p = p.parent }
                    var c = currentNode.children.firstOrNull()
                    while (c != null) { path.add(c); c = c.children.firstOrNull() }
                    path
                }

                val boardState = currentNode.board
                val senteHand = currentNode.senteHand
                val goteHand = currentNode.goteHand
                val currentPlayer = currentNode.currentPlayer

                var isBoardFlipped by remember { mutableStateOf(false) }
                var isAutoAnalysis by remember { mutableStateOf(false) }
                val pvList = remember { mutableStateMapOf<Int, String>() }
                val pvUsiList = remember { mutableStateMapOf<Int, List<String>>() }
                var pinnedPvList by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
                var pinnedPvUsiList by remember { mutableStateOf<Map<Int, List<String>>>(emptyMap()) }
                val evalHistory = remember { mutableStateMapOf<Int, Int>() }

                var bestmoveReceived by remember { mutableStateOf(false) }

                var selectedSquare by remember { mutableStateOf<Pair<Int, Int>?>(null) }
                var selectedHandPiece by remember { mutableStateOf<Pair<Player, PieceType>?>(null) }
                var promotionPendingBy by remember { mutableStateOf<PendingMove?>(null) }
                
                var showMenu by remember { mutableStateOf(false) }
                var isAnalysisMode by remember { mutableStateOf(false) }
                var isEngineReady by remember { mutableStateOf(false) }
                var isPvStale by remember { mutableStateOf(false) }

                var analysisTimeMs by remember { mutableLongStateOf(prefs.getLong("analysis_time", 1000L)) }
                var multiPvCount by remember { mutableIntStateOf(prefs.getInt("multi_pv", 3)) }
                var threadCount by remember { mutableIntStateOf(prefs.getInt("thread_count", 4)) }
                var showSettingsDialog by remember { mutableStateOf(false) }

                var senteName by remember { mutableStateOf(savedSenteName) }
                var goteName by remember { mutableStateOf(savedGoteName) }
                
                val engine = remember { UsiEngine() }
                var engineOutput by remember { mutableStateOf("エンジン待機中...") }

                val processOutput = { rawLine: String, capturedBoard: Map<Pair<Int, Int>, Piece>, capturedTurn: Player, capturedMoveCount: Int ->
                    Log.d("callback_used", "手数=$capturedMoveCount turn=$capturedTurn line=$rawLine")
                    val line = rawLine.trim()
                    when {
                        line == "usiok" -> {
                            engine.sendCommand("setoption name Threads value $threadCount")
                            engine.sendCommand("setoption name MultiPV value $multiPvCount")
                            engine.sendCommand("isready")
                        }
                        line == "readyok" -> isEngineReady = true
                        line.startsWith("info") -> {
                            if (isPvStale) { pvList.clear(); isPvStale = false }
                            val rank = Regex("multipv (\\d+)").find(line)?.groupValues?.get(1)?.toInt() ?: 1
                            Regex("""pv (.+)$""").find(line)?.let { match ->
                                pvUsiList[rank] = match.groupValues[1].trim().split(" ")
                            }
                            val parsed = parseInfo(line, capturedBoard, capturedTurn)

                            if (parsed.contains("評価") || parsed.contains("読み筋")) {
                                pvList[rank] = parsed
                                engineOutput = pvList.toSortedMap().values.joinToString("\n---\n")
                                if (rank == 1 && parsed.contains("評価")) {
                                    val scoreLine = parsed.lines().find { it.startsWith("評価:") }
                                    val score = scoreLine?.substringAfter("評価:")?.trim()
                                        ?.split(" ")?.firstOrNull()?.replace("+", "")?.toIntOrNull()
                                    if (score != null) evalHistory[capturedMoveCount] = score  // ← 変更
                                }
                            }
                        }
                        line.startsWith("bestmove") -> {
                            bestmoveReceived = true
                        }
                    }
                }




                LaunchedEffect(Unit) {
                    delay(1000)
                    // usiok/readyok を受け取るための初期コールバック
                    engine.onOutputReceived = { rawLine ->
                        runOnUiThread { processOutput(rawLine, emptyMap(), Player.SENTE, 0) }
                    }

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        copyAssetsToFileDir("nn.bin", "eval", filesDir, assets)
                    }
                    engine.start(filesDir.absolutePath)
                }

                LaunchedEffect(isAutoAnalysis, isAnalysisMode, isEngineReady, currentNode) {
                    if (!(isAnalysisMode || isAutoAnalysis) || !isEngineReady) return@LaunchedEffect
                    //engine.sendCommand("stop")  // ← 先にstopを送る
                    val node = currentNode  // ← var不要、currentNodeをそのまま使う

                    val capturedBoard = node.board
                    val capturedTurn = node.currentPlayer
                    val capturedMoveCount = node.moveCount

                    engine.onOutputReceived = { rawLine ->
                        runOnUiThread { processOutput(rawLine, capturedBoard, capturedTurn, capturedMoveCount) }
                    }
                    Log.d("callback_set", "手数=$capturedMoveCount turn=$capturedTurn")
                    engine.sendCommand("stop")
                    bestmoveReceived = false  // リセット
                    engine.sendCommand("stop")
                    // bestmove が返るまで待つ
//                    var waited = 0
//                    while (!bestmoveReceived && waited < 100) {
//                        delay(50)
//                        waited += 50
//                        Log.d("waiting_bestmove", "waited=$waited bestmoveReceived=$bestmoveReceived")
//                    }
                    Log.d("waiting_bestmove", "done waiting bestmoveReceived=$bestmoveReceived")
                    //delay(100)
                    pvList.clear()
                    pvUsiList.clear()

                    val posCmd = buildPositionCommand(initialNode, node)
                        ?: boardToSfen(node.board, node.currentPlayer, node.senteHand, node.goteHand)
                            .takeIf { it.isNotEmpty() }?.let { "position sfen $it" }
                    if (posCmd != null) {
                        engine.sendCommand(posCmd)
                        Log.d("posCmd", posCmd)
                        engine.sendCommand("go movetime $analysisTimeMs")
                    }

                    // 自動解析の場合のみ次の手へ進む
                    if (isAutoAnalysis) {
                        delay(analysisTimeMs + 100)
                        val next = node.children.firstOrNull()
                        if (next == null) isAutoAnalysis = false
                        else currentNode = next
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                SliderControlSection(currentNode, currentPath, evalHistory) { currentNode = it }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val clipboard = LocalClipboardManager.current
                                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.Menu, "メニュー") }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false },
                                        offset = DpOffset(x = 0.dp, y = 0.dp),
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("リセット") },
                                            onClick = { currentNode = initialNode; initialNode.children.clear(); prefs.edit().remove("current_tree").apply(); showMenu = false })
                                        DropdownMenuItem(
                                            text = { Text("現局面から最後まで解析") },
                                            onClick = { isAutoAnalysis = true; showMenu = false })
                                        DropdownMenuItem(
                                            text = { Text("設定") },
                                            onClick = { showSettingsDialog = true; showMenu = false })
                                    }
                                    OutlinedButton(onClick = {
                                        clipboard.getText()?.text?.let { text ->
                                            val freshRoot = KifuNode(createInitialBoard(), emptyMap(), emptyMap(), Player.SENTE)
                                            val newNode = parseKif(text, freshRoot, saveKifu)
                                            pinnedPvList = emptyMap(); pinnedPvUsiList = emptyMap(); evalHistory.clear()
                                            if (newNode != null) { currentNode = newNode; saveKifu(freshRoot) }
                                        }
                                    }, modifier = Modifier.weight(0.3f).height(72.dp),
                                        shape = MaterialTheme.shapes.extraLarge) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.ContentPaste, "読込"); Text("読込", style = MaterialTheme.typography.labelSmall) }
                                    }

                                    OutlinedButton(onClick = {
                                        if (isEngineReady) {
                                            if (isAnalysisMode || isAutoAnalysis) { isAnalysisMode = false; isAutoAnalysis = false; engine.sendCommand("stop") }
                                            else { pinnedPvList = emptyMap(); pinnedPvUsiList = emptyMap(); isAnalysisMode = true }
                                        }
                                    }, modifier = Modifier.weight(0.3f).height(72.dp),
                                        shape = MaterialTheme.shapes.extraLarge,
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (isAnalysisMode || isAutoAnalysis) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent)) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(painterResource(if (isAnalysisMode || isAutoAnalysis) R.drawable.stop_circle_24px else R.drawable.network_intelligence_24px), "解析"); Text(if (isAnalysisMode || isAutoAnalysis) "停止" else "解析", style = MaterialTheme.typography.labelSmall) }
                                    }

                                    OutlinedButton(onClick = { isBoardFlipped = !isBoardFlipped },
                                        modifier = Modifier.weight(0.3f).height(72.dp),
                                        shape = MaterialTheme.shapes.extraLarge
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(painterResource(R.drawable.rotate_right_24px), "反転"); Text("反転", style = MaterialTheme.typography.labelSmall) }
                                    }

                                    OutlinedButton(onClick = {
                                        fun clearPv(n: KifuNode) { n.children.removeIf { it.isPvBranch }; n.children.forEach { clearPv(it) } }
                                        clearPv(initialNode); currentNode = initialNode; pinnedPvList = emptyMap()
                                    },
                                        modifier = Modifier.weight(0.3f).height(72.dp),
                                        shape = MaterialTheme.shapes.extraLarge
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(painterResource(R.drawable.undo_24px), "本譜"); Text("本譜", style = MaterialTheme.typography.labelSmall) }
                                    }


                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    val isWide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840
                    if (isWide) {
                        Row(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column(modifier = Modifier.weight(0.5f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                                (if (
                                    pinnedPvList.isNotEmpty()
                                    ) pinnedPvList
                                else pvList.toMap()).entries
                                    .sortedByDescending { extractScore(it.value, currentPlayer) }
                                    .forEach { (rank, pvText) ->
                                    val alpha = if (isPvStale && pinnedPvList.isEmpty()) 0.5f else 1f
                                    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
                                        PvInfoCard(rank, pvText) {
                                            if (pinnedPvList.isEmpty()) { pinnedPvList = pvList.toMap(); pinnedPvUsiList = pvUsiList.toMap(); isAnalysisMode = false; engine.sendCommand("stop") }
                                            val usi = pinnedPvUsiList[rank] ?: return@PvInfoCard
                                            var p = currentNode
                                            usi.forEach { m ->
                                                val b = applyUsiMove(m, p.board, p.currentPlayer)
                                                val l = formatUsiMove(m, p.board)
                                                val n = KifuNode(b, p.senteHand, p.goteHand, if(p.currentPlayer == Player.SENTE) Player.GOTE else Player.SENTE, l, p, isPvBranch = true)
                                                p.children.add(n); p = n
                                            }
                                            currentNode = currentNode.children.lastOrNull { it.isPvBranch } ?: currentNode
                                        }
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(0.5f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val topP = if (isBoardFlipped) Player.SENTE else Player.GOTE; val botP = if (isBoardFlipped) Player.GOTE else Player.SENTE
                                PlayerStatusSection(if(topP==Player.SENTE) senteName else goteName, if(topP==Player.SENTE) "▲" else "△", currentPlayer==topP, if(topP==Player.SENTE) senteHand else goteHand, selectedHandPiece, currentPlayer, isBoardFlipped) { selectedHandPiece = it; selectedSquare = null }
                                ShogiBoard(boardState, selectedSquare, { r, c -> 
                                    handleSquareClick(r, c, boardState, currentPlayer, selectedSquare, selectedHandPiece, currentNode, saveKifu) { s, h, n, p ->
                                        selectedSquare = s; selectedHandPiece = h
                                        if(n != null) currentNode = n
                                        if(p != null) promotionPendingBy = p
                                    }
                                }, isBoardFlipped, Modifier.sizeIn(maxWidth = 500.dp, maxHeight = 500.dp), currentNode.lastFrom, currentNode.lastTo, currentNode.isPvBranch)
                                PlayerStatusSection(if(botP==Player.SENTE) senteName else goteName, if(botP==Player.SENTE) "▲" else "△", currentPlayer==botP, if(botP==Player.SENTE) senteHand else goteHand, selectedHandPiece, currentPlayer, isBoardFlipped) { selectedHandPiece = it; selectedSquare = null }
                            }
                        }
                    }
                    else {
                        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                (if (pinnedPvList.isNotEmpty()) pinnedPvList else pvList.toMap()).entries
                                    .sortedByDescending { extractScore(it.value, currentPlayer) }
                                    .forEach { (rank, pvText) ->
                                    val alpha = if (isPvStale && pinnedPvList.isEmpty()) 0.5f else 1f
                                    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
                                        PvInfoCard(rank, pvText) {
                                            if (pinnedPvList.isEmpty()) { pinnedPvList = pvList.toMap(); pinnedPvUsiList = pvUsiList.toMap(); isAnalysisMode = false; engine.sendCommand("stop") }
                                            val usi = pinnedPvUsiList[rank] ?: return@PvInfoCard
                                            var p = currentNode
                                            usi.forEach { m ->
                                                val b = applyUsiMove(m, p.board, p.currentPlayer)
                                                val l = formatUsiMove(m, p.board)
                                                val n = KifuNode(b, p.senteHand, p.goteHand, if(p.currentPlayer == Player.SENTE) Player.GOTE else Player.SENTE, l, p, isPvBranch = true)
                                                p.children.add(n); p = n
                                            }
                                            currentNode = currentNode.children.lastOrNull { it.isPvBranch } ?: currentNode
                                        }
                                    }
                                }
                            }
                            val topP = if (isBoardFlipped) Player.SENTE else Player.GOTE; val botP = if (isBoardFlipped) Player.GOTE else Player.SENTE
                            PlayerStatusSection(if(topP==Player.SENTE) senteName else goteName, if(topP==Player.SENTE) "▲" else "△", currentPlayer==topP, if(topP==Player.SENTE) senteHand else goteHand, selectedHandPiece, currentPlayer, isBoardFlipped) { selectedHandPiece = it; selectedSquare = null }
                            ShogiBoard(boardState, selectedSquare, { r, c -> 
                                handleSquareClick(r, c, boardState, currentPlayer, selectedSquare, selectedHandPiece, currentNode, saveKifu) { s, h, n, p ->
                                    selectedSquare = s; selectedHandPiece = h
                                    if(n != null) currentNode = n
                                    if(p != null) promotionPendingBy = p
                                }
                            }, isBoardFlipped, Modifier.padding(16.dp), currentNode.lastFrom, currentNode.lastTo, currentNode.isPvBranch)
                            PlayerStatusSection(if(botP==Player.SENTE) senteName else goteName, if(botP==Player.SENTE) "▲" else "△", currentPlayer==botP, if(botP==Player.SENTE) senteHand else goteHand, selectedHandPiece, currentPlayer, isBoardFlipped) { selectedHandPiece = it; selectedSquare = null }
                        }
                    }
                }

                if (showSettingsDialog) {
                    AlertDialog(onDismissRequest = { showSettingsDialog = false }, title = { Text("設定") },
                        text = {
                            Column {
                                Text("思考時間: ${analysisTimeMs}ms"); Slider(value = analysisTimeMs.toFloat(), onValueChange = { analysisTimeMs = it.toLong() }, valueRange = 100f..5000f)
                                Text("候補手: $multiPvCount"); Slider(value = multiPvCount.toFloat(),
                                onValueChange = { multiPvCount = it.toInt() },
                                valueRange = 1f..3f,
                                    steps = 1)
                                Text("スレッド数: $threadCount"); Slider(value = threadCount.toFloat(),
                                onValueChange = { threadCount = it.toInt() },
                                valueRange = 1f..8f,
                                    steps = 6)
                            }
                        },
                        confirmButton = { 
                            TextButton(onClick = { 
                                prefs.edit().putLong("analysis_time", analysisTimeMs)
                                    .putInt("multi_pv", multiPvCount)
                                    .putInt("thread_count", threadCount)
                                    .apply()
                                if(isEngineReady){ 
                                    engine.sendCommand("setoption name MultiPV value $multiPvCount")
                                    engine.sendCommand("setoption name Threads value $threadCount")
                                }
                                showSettingsDialog = false 
                            }) { Text("保存") } 
                        }
                    )
                }

                promotionPendingBy?.let { move ->
                    AlertDialog(onDismissRequest = {}, title = { Text("成り") }, text = { Text("成りますか？") },
                        confirmButton = { 
                            TextButton(onClick = { 
                                val usi = "${9 - move.from.second}${('a' + move.from.first)}${9 - move.to.second}${('a' + move.to.first)}+"
                                val label = formatUsiMove(usi, boardState)
                                executeMove(move.from, move.to, move.piece, move.captured, true, currentNode, label, true, saveKifu) { 
                                    currentNode = it 
                                }
                                promotionPendingBy = null 
                            }) { Text("成る") } 
                        },
                        dismissButton = { 
                            TextButton(onClick = { 
                                val usi = "${9 - move.from.second}${('a' + move.from.first)}${9 - move.to.second}${('a' + move.to.first)}"
                                val label = formatUsiMove(usi, boardState)
                                executeMove(move.from, move.to, move.piece, move.captured, false, currentNode, label, true, saveKifu) { 
                                    currentNode = it 
                                }
                                promotionPendingBy = null 
                            }) { Text("不成") } 
                        }
                    )
                }
            }
        }
    }

    private fun getRootNode(node: KifuNode): KifuNode {
        var p = node; while (p.parent != null) p = p.parent!!; return p
    }

    private fun handleSquareClick(
        row: Int, col: Int, boardState: Map<Pair<Int, Int>, Piece>, currentPlayer: Player,
        selectedSquare: Pair<Int, Int>?, selectedHandPiece: Pair<Player, PieceType>?, currentNode: KifuNode,
        onSaveRequested: (KifuNode) -> Unit,
        onUpdate: (Pair<Int, Int>?, Pair<Player, PieceType>?, KifuNode?, PendingMove?) -> Unit
    ) {
        val clickedPos = Pair(row, col)
        if (selectedHandPiece != null) {
            if (boardState[clickedPos] == null && selectedHandPiece.first == currentPlayer) {
                val (player, type) = selectedHandPiece
                val moveLabel = "${9 - col}${rowToKanji('a' + row)}${type.label}打"
                executeMove(null, clickedPos, Piece(type, player), null, false, currentNode, moveLabel, true, onSaveRequested) { onUpdate(null, null, it, null) }
            }
        } else {
            val currentSelected = selectedSquare
            if (currentSelected == null) {
                val piece = boardState[clickedPos]
                if (piece != null && piece.owner == currentPlayer) onUpdate(clickedPos, null, null, null)
            } else {
                if (currentSelected == clickedPos) onUpdate(null, null, null, null)
                else {
                    val movingPiece = boardState[currentSelected]
                    val targetPiece = boardState[clickedPos]
                    if (movingPiece != null && movingPiece.owner == currentPlayer && movingPiece.owner != targetPiece?.owner) {
                        if (isValidMovePattern(currentSelected, clickedPos, movingPiece, boardState)) {
                            val canPromote = !movingPiece.isPromoted && movingPiece.type.promotedLabel != null
                            val isSenteZone = clickedPos.first <= 2 || currentSelected.first <= 2
                            val isGoteZone = clickedPos.first >= 6 || currentSelected.first >= 6
                            val enteringZone = if (movingPiece.owner == Player.SENTE) isSenteZone else isGoteZone
                            if (canPromote && enteringZone) onUpdate(null, null, null, PendingMove(currentSelected, clickedPos, movingPiece, targetPiece))
                            else {
                                val moveLabel = formatUsiMove("${9-currentSelected.second}${('a'+currentSelected.first)}${9-clickedPos.second}${('a'+clickedPos.first)}", boardState)
                                executeMove(currentSelected, clickedPos, movingPiece, targetPiece, false, currentNode, moveLabel, true, onSaveRequested) { onUpdate(null, null, it, null) }
                            }
                        }
                    } else if (targetPiece?.owner == movingPiece?.owner) onUpdate(clickedPos, null, null, null)
                }
            }
        }
    }
}
