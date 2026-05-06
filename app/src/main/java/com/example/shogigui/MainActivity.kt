package com.example.shogigui

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import org.json.JSONArray
import org.json.JSONObject
import com.example.shogigui.ui.theme.ShogiGUITheme
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context
import android.content.SharedPreferences
import android.content.Context.MODE_PRIVATE
import androidx.compose.foundation.layout.Arrangement
import kotlin.collections.sortedByDescending

// 棋譜の1局面を管理するノードクラス
//class KifuNode(
//    val board: Map<Pair<Int, Int>, Piece>,
//    val senteHand: Map<PieceType, Int>,
//    val goteHand: Map<PieceType, Int>,
//    val currentPlayer: Player,
//    val moveLabel: String = "開始局面",
//    val parent: KifuNode? = null,
//    val lastFrom: Pair<Int, Int>? = null,
//    val lastTo: Pair<Int, Int>? = null,
//    val isPvBranch: Boolean = false  // 読み筋（一時的）かどうか
//) {
//    val children = mutableStateListOf<KifuNode>()
//    val moveCount: Int = (parent?.moveCount ?: -1) + 1
//}

class MainActivity : ComponentActivity() {

    // Activityのフィールドとして保持
    private var rootNode: KifuNode? = null
    private var savedSenteName: String = "先手"
    private var savedGoteName: String = "後手"

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("kifu_prefs", MODE_PRIVATE)  // ← ここで一度取得

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
                try {
                    jsonToKifuTree(JSONObject(saved))
                } catch (e: Exception) {
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
                // クリップボードから読み込んだ棋譜の本手順ノード列（index == moveCount）
                var mainLineNodes by remember {
                    val line = mutableListOf<KifuNode>()
                    var n: KifuNode? = initialNode
                    while (n != null) {
                        line.add(n); n = n.children.firstOrNull { !it.isPvBranch }
                    }
                    mutableStateOf<List<KifuNode>>(line)
                }
                // 現在のパス（スライダー用）を計算
                val currentPath = remember(currentNode, initialNode) {
                    val path = mutableListOf<KifuNode>()
                    // ルートから現在地まで
                    var p: KifuNode? = currentNode
                    while (p != null) {
                        path.add(0, p); p = p.parent
                    }
                    // 現在地から本譜（最初の子供）を辿る
                    var c = currentNode.children.firstOrNull()
                    while (c != null) {
                        path.add(c); c = c.children.firstOrNull()
                    }
                    path
                }

                // 現在の局面情報を取得
                val boardState = currentNode.board
                val senteHand = currentNode.senteHand
                val goteHand = currentNode.goteHand
                val currentPlayer = currentNode.currentPlayer

                var isBoardFlipped by remember { mutableStateOf(false) }
                var isAutoAnalysis by remember { mutableStateOf(false) }
                // MainActivity 内に定義を追加
                val pvList = remember { mutableStateMapOf<Int, String>() }
                val pvUsiList = remember { mutableStateMapOf<Int, List<String>>() }

                var selectedSquare by remember { mutableStateOf<Pair<Int, Int>?>(null) }
                var selectedHandPiece by remember { mutableStateOf<Pair<Player, PieceType>?>(null) }
                var promotionPendingBy by remember { mutableStateOf<PendingMove?>(null) }

                var showMenu by remember { mutableStateOf(false) }
                val clipboardManager = LocalClipboardManager.current
                var isAnalysisMode by remember { mutableStateOf(false) }
                var isEngineReady by remember { mutableStateOf(false) }
                var isAnalyzing by remember { mutableStateOf(false) }
                var isOutputExpanded by remember { mutableStateOf(false) }

                var pvBranchRoot by remember { mutableStateOf<KifuNode?>(null) }

                // エンジン設定の状態
                var analysisTimeMs by remember { mutableLongStateOf(prefs.getLong("analysis_time", 1000L)) }
                var multiPvCount by remember { mutableIntStateOf(prefs.getInt("multi_pv", 3)) }
                var threadCount by remember { mutableIntStateOf(prefs.getInt("thread_count", 4)) }
                var showSettingsDialog by remember { mutableStateOf(false) }

                // 対局者名
                var senteName by remember { mutableStateOf(savedSenteName) }
                var goteName by remember { mutableStateOf(savedGoteName) }

                // エンジンのインスタンスを保持
                val engine = remember { UsiEngine() }

                val filesDirPath = filesDir  // ← Activityのコンテキストで取得
                val myFilesDir = filesDir
                val myAssets = assets
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
                    } catch (e: Exception) {
                    }

                    when {
                        line.contains("JNI:") -> {
                            engineOutput = line
                            engineLog = (engineLog + line).takeLast(50)
                        }

                        line == "usiok" -> {
                            val msg = "初期化完了 (usiok)"
                            engineOutput = msg
                            engineLog = (engineLog + msg).takeLast(50)
                            // 保存された設定値を送信
                            engine.sendCommand("setoption name Threads value $threadCount")
                            engine.sendCommand("setoption name MultiPV value $multiPvCount")
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
                            val multipvMatch = Regex("multipv (\\d+)").find(line)
                            val rank = multipvMatch?.groupValues?.get(1)?.toInt() ?: 1

                            // USI手順を抽出
                            val pvMatch = Regex("""pv (.+)$""").find(line)
                            if (pvMatch != null) {
                                val usiMoves = pvMatch.groupValues[1].trim().split(" ")
                                    .filter { it.matches(Regex("[1-9][a-i][1-9][a-i][+]?|[PLNSGBR]\\*[1-9][a-i]")) }
                                pvUsiList[rank] = usiMoves
                                android.util.Log.d("pvUsi", "rank=$rank moves=$usiMoves")
                            }

                            val parsed = parseInfo(line, boardState, currentPlayer)
                            if (parsed.contains("評価") || parsed.contains("読み筋")) {
                                pvList[rank] = parsed
                                engineOutput = pvList.toSortedMap().values.joinToString("\n---\n")
                            }
                        }

                        line.startsWith("bestmove") -> {
                            // モードは維持し、解析中フラグだけを下ろす
                            isAnalyzing = false 

                            val move = line.substringAfter("bestmove ").substringBefore(" ")
                            if (move != "ponder" && move != "(none)") {
                                val playerLabel = if (currentPlayer == Player.SENTE) "▲" else "△"
                                val formattedMove = formatUsiMove(move, boardState)
                                val finalMsg = "最善手: $playerLabel$formattedMove"
                                // engineOutput = engineOutput // 表示はPVリストに任せる
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
                            Triple(
                                currentNode.board, currentNode.currentPlayer,
                                currentNode.senteHand to currentNode.goteHand
                            )
                        }.collect { (board, player, hands) ->
                            engine.sendCommand("stop")
                            pvList.clear() // ★ここ：前の局面の次善手を消す
                            kotlinx.coroutines.delay(100)
                            val sfen = boardToSfen(board, player, hands.first, hands.second)
                            if (sfen.isNotEmpty()) {
                                engine.sendCommand("position sfen $sfen")
                                engine.sendCommand("go movetime $analysisTimeMs")
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
                            copyAssetsToFileDir("nn.bin", "eval", myFilesDir, myAssets)
                        }

                        // 作業ディレクトリを filesDir に設定。
                        // これにより、エンジンが eval/nn.bin を正しく見つけられます。
                        engine.start(filesDir.absolutePath)
                    } catch (e: Exception) {
                        engineOutput = "エンジン起動失敗: ${e.message}"
                    }
                }

                LaunchedEffect(isAutoAnalysis, isEngineReady) {
                    if (!isAutoAnalysis || !isEngineReady) return@LaunchedEffect

                    // 現在のノードから末尾まで順番に解析
                    var node = currentNode
                    while (isAutoAnalysis) {
                        val sfen = boardToSfen(
                            node.board,
                            node.currentPlayer,
                            node.senteHand,
                            node.goteHand
                        )
                        if (sfen.isNotEmpty()) {
                            engine.sendCommand("stop")
                            kotlinx.coroutines.delay(100)
                            engine.sendCommand("position sfen $sfen")
                            engine.sendCommand("go movetime 1000")
                            currentNode = node  // 現在の解析局面を表示
                            kotlinx.coroutines.delay(1200)  // 解析時間 + 余裕
                        }

                        // 次のノードへ
                        val next = node.children.firstOrNull()
                        if (next == null) {
                            // 末尾に到達
                            isAutoAnalysis = false
                            break
                        }
                        node = next
                    }
                    engine.sendCommand("stop")
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
                                                    line.startsWith("先手：") || line.startsWith("下手：") || line.startsWith(
                                                        "N+"
                                                    ) ->
                                                        senteName = line.substringAfter("：")
                                                            .substringAfter("+").trim()

                                                    line.startsWith("後手：") || line.startsWith("上手：") || line.startsWith(
                                                        "N-"
                                                    ) ->
                                                        goteName = line.substringAfter("：")
                                                            .substringAfter("-").trim()
                                                }
                                            }
                                            // 新しいルートノードを作成してリセット
                                            val freshRoot = KifuNode(
                                                createInitialBoard(),
                                                emptyMap(),
                                                emptyMap(),
                                                Player.SENTE
                                            )
                                            rootNode = freshRoot

                                            // CSA, KIF, USI を自動判別
                                            val newNode = when {
                                                text.lines()
                                                    .any { it.matches(Regex("^[+-]\\d{4}[A-Z]{2}.*")) } ->
                                                    parseCsa(text, freshRoot)

                                                text.contains("手数----") || text.contains("手合割") ||
                                                        text.contains("▲") || text.contains("△") ->
                                                    parseKif(text, freshRoot)

                                                text.contains("moves") ->
                                                    parseKifu(text, freshRoot)

                                                else ->
                                                    parseKif(text, freshRoot) ?: parseKifu(
                                                        text,
                                                        freshRoot
                                                    )
                                            }
                                            if (newNode != null) {
                                                initialNode = freshRoot
                                                currentNode = newNode
                                                saveKifuTree(freshRoot)
                                            } else {
                                                initialNode = freshRoot
                                                currentNode = freshRoot
                                            }
                                            // 本手順ノード列を更新（クリップボードから読み込んだ棋譜が本手順）
                                            val line = mutableListOf<KifuNode>()
                                            var n: KifuNode? = freshRoot
                                            while (n != null) {
                                                line.add(n); n =
                                                    n.children.firstOrNull { !it.isPvBranch }
                                            }
                                            mainLineNodes = line
                                            getSharedPreferences("kifu_prefs", MODE_PRIVATE).edit()
                                                .putString("sente_name", senteName)
                                                .putString("gote_name", goteName)
                                                .apply()
                                        }

                                    },
                                    modifier = Modifier
                                        .weight(0.3f)
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
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            modifier = Modifier.basicMarquee()// アイコンの下に小さく文字を添える
                                        )
                                    }
                                }

                                // 解析ボタン
                                OutlinedButton(
                                    onClick = {
                                        if (!isEngineReady) return@OutlinedButton
                                        if (isAnalysisMode || isAutoAnalysis) {
                                            isAnalysisMode = false
                                            isAutoAnalysis = false // 自動解析も止める
                                            engine.sendCommand("stop")
                                        } else {
                                            isAnalysisMode = true
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(0.3f)
                                        .height(72.dp)
                                        .graphicsLayer {
                                            if (!isEngineReady) this.alpha = alpha
                                        },
                                    shape = MaterialTheme.shapes.extraLarge,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = when {
                                            !isEngineReady -> Color.Transparent
                                            isAnalysisMode || isAutoAnalysis -> MaterialTheme.colorScheme.tertiaryContainer
                                            else -> Color.Transparent
                                        }
                                    )
                                ) {
                                    if (!isEngineReady) {
                                        LoadingIndicator(
                                            modifier = Modifier
                                                .size(28.dp)
                                            //.padding(end = 8.dp),
                                            ,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        if (isAnalysisMode || isAutoAnalysis) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.stop_circle_24px),
                                                    contentDescription = "停止",
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Text(
                                                    text = "停止",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    modifier = Modifier.basicMarquee()// アイコンの下に小さく文字を添える
                                                )
                                            }
                                        } else {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.network_intelligence_24px),
                                                    contentDescription = "棋譜解析",
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Text(
                                                    text = "解析",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    modifier = Modifier.basicMarquee()// アイコンの下に小さく文字を添える
                                                )
                                            }
                                        }

                                    }
                                }
                                //反転ボタン
                                OutlinedButton(
                                    modifier = Modifier
                                        .weight(0.3f)
                                        .height(72.dp),
                                    onClick = {
                                        isBoardFlipped = !isBoardFlipped
                                        showMenu = false
                                    }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.rotate_right_24px),
                                            contentDescription = "反転",
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            text = "反転",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            modifier = Modifier.basicMarquee()// アイコンの下に小さく文字を添える
                                        )
                                    }
                                }
                                //本手順に戻る
                                OutlinedButton(
                                    onClick = {
                                        // 現在の手数に対応する本手順ノードを探す
                                        val targetCount = currentNode.moveCount
                                        val targetNode = mainLineNodes.getOrElse(targetCount) {
                                            mainLineNodes.lastOrNull() ?: initialNode
                                        }

                                        // isPvBranch な枝（手動操作・読み筋）を全削除して本手順を保護
                                        fun removeNonMain(node: KifuNode) {
                                            node.children.removeIf { it.isPvBranch }
                                            node.children.forEach { removeNonMain(it) }
                                        }
                                        removeNonMain(initialNode)
                                        pvBranchRoot = null
                                        currentNode = targetNode
                                        showMenu = false
                                    },
                                    modifier = Modifier
                                        .weight(0.3f)
                                        .height(72.dp),
                                    shape = MaterialTheme.shapes.extraLarge,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.undo_24px),
                                            contentDescription = "本手順に戻る",
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            text = "本譜",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            modifier = Modifier.basicMarquee()
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
                                                getSharedPreferences(
                                                    "kifu_prefs",
                                                    MODE_PRIVATE
                                                ).edit().remove("current_tree").apply()
                                                showMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("現局面から最後まで解析") },
                                            onClick = {
                                                isAutoAnalysis = !isAutoAnalysis
                                                showMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("エンジンの設定") },
                                            onClick = {
                                                showSettingsDialog = true
                                                showMenu = false
                                            }
                                        )

                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val isWideScreen = configuration.screenWidthDp >= 840

                    if (isWideScreen) {
                        // 【広幅画面：2ペインレイアウト】
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // --- 左ペイン：解析結果 (40%幅) ---
                            Column(
                                modifier = Modifier
                                    .weight(0.5f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                pvList.entries
                                    .sortedByDescending { extractScore(it.value, currentPlayer) }
                                    .forEach { (rank, pvText) ->
                                        PvInfoCard(rank, pvText) {
                                            // ★再生に入ったら解析を止める
                                            isAnalysisMode = false
                                            engine.sendCommand("stop")

                                            val usiMoves = pvUsiList[rank] ?: return@PvInfoCard
                                            
                                            // --- 読み筋全体のツリーを構築 ---
                                            var branchPointer = pvBranchRoot ?: currentNode
                                            val branchNodes = mutableListOf<KifuNode>()
                                            
                                            usiMoves.forEach { moveStr ->
                                                try {
                                                    val fromPos = if (moveStr[1] != '*') {
                                                        Pair(moveStr[1] - 'a', 9 - (moveStr[0] - '0'))
                                                    } else null
                                                    val toPos = Pair(moveStr[3] - 'a', 9 - (moveStr[2] - '0'))
                                                    val movingPiece = if (fromPos != null) {
                                                        branchPointer.board[fromPos]
                                                    } else {
                                                        val type = when(moveStr[0]) {
                                                            'P' -> PieceType.PAWN; 'L' -> PieceType.LANCE; 'N' -> PieceType.KNIGHT
                                                            'S' -> PieceType.SILVER; 'G' -> PieceType.GOLD; 'B' -> PieceType.BISHOP; 'R' -> PieceType.ROOK
                                                            else -> null
                                                        }
                                                        if (type != null) Piece(type, branchPointer.currentPlayer) else null
                                                    }
                                                    
                                                    if (movingPiece != null) {
                                                        val label = formatUsiMove(moveStr, branchPointer.board)
                                                        val sym = if (branchPointer.currentPlayer == Player.SENTE) "▲" else "△"
                                                        
                                                        val existing = branchPointer.children.find { it.moveLabel == "$sym$label" }
                                                        if (existing != null) {
                                                            branchPointer = existing
                                                        } else {
                                                            val newBoard = applyUsiMove(moveStr, branchPointer.board, branchPointer.currentPlayer).toMutableMap()
                                                            var newSenteHand = branchPointer.senteHand
                                                            var newGoteHand = branchPointer.goteHand
                                                            
                                                            if (fromPos == null) { // 打ち
                                                                if (branchPointer.currentPlayer == Player.SENTE) {
                                                                    newSenteHand = newSenteHand.toMutableMap().apply { this[movingPiece.type] = (this[movingPiece.type] ?: 1) - 1 }.filterValues { it > 0 }
                                                                } else {
                                                                    newGoteHand = newGoteHand.toMutableMap().apply { this[movingPiece.type] = (this[movingPiece.type] ?: 1) - 1 }.filterValues { it > 0 }
                                                                }
                                                            }
                                                            val captured = branchPointer.board[toPos]
                                                            if (captured != null && captured.type != PieceType.KING) {
                                                                if (branchPointer.currentPlayer == Player.SENTE) {
                                                                    newSenteHand = newSenteHand.toMutableMap().apply { this[captured.type] = (this[captured.type] ?: 0) + 1 }
                                                                } else {
                                                                    newGoteHand = newGoteHand.toMutableMap().apply { this[captured.type] = (this[captured.type] ?: 0) + 1 }
                                                                }
                                                            }
                                                            
                                                            val next = if (branchPointer.currentPlayer == Player.SENTE) Player.GOTE else Player.SENTE
                                                            val newNode = KifuNode(newBoard, newSenteHand, newGoteHand, next, "$sym$label", branchPointer, fromPos, toPos, isPvBranch = true)
                                                            branchPointer.children.add(newNode)
                                                            branchPointer = newNode
                                                        }
                                                        branchNodes.add(branchPointer)
                                                    }
                                                } catch (e: Exception) {}
                                            }
                                            
                                            // --- タップで一手進める ---
                                            val nextInPv = branchNodes.firstOrNull { it.parent == currentNode }
                                            if (nextInPv != null) {
                                                currentNode = nextInPv
                                            } else if (branchNodes.isNotEmpty() && currentNode != branchNodes.lastOrNull()) {
                                                pvBranchRoot = currentNode
                                                currentNode = branchNodes.first()
                                            }
                                        }
                                    }
                            }

                            // --- 右ペイン：将棋盤と操作系 (60%幅) ---
                            Column(
                                modifier = Modifier
                                    .weight(0.5f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 反転状態に応じて表示順を決定
                                val topPlayer = if (isBoardFlipped) Player.SENTE else Player.GOTE
                                val bottomPlayer = if (isBoardFlipped) Player.GOTE else Player.SENTE

                                PlayerStatusSection(
                                    playerName = if (topPlayer == Player.SENTE) senteName else goteName,
                                    mark = if (topPlayer == Player.SENTE) "▲" else "△",
                                    isActive = currentPlayer == topPlayer,
                                    hand = if (topPlayer == Player.SENTE) senteHand else goteHand,
                                    selectedHandPiece = selectedHandPiece,
                                    currentPlayer = currentPlayer,
                                    isFlipped = isBoardFlipped
                                ) { selectedSquare = null; selectedHandPiece = it }

                                ShogiBoard(
                                    boardState = boardState,
                                    selectedSquare = selectedSquare,
                                    lastFrom = currentNode.lastFrom,
                                    lastTo = currentNode.lastTo,
                                    isFlipped = isBoardFlipped,
                                    onSquareClick = { r, c ->
                                        handleSquareClick(
                                            row = r,
                                            col = c,
                                            boardState = boardState,
                                            currentPlayer = currentPlayer,
                                            selectedSquare = selectedSquare,
                                            selectedHandPiece = selectedHandPiece,
                                            currentNode = currentNode,
                                            onUpdate = { square, hand, node, pending ->
                                                selectedSquare = square
                                                selectedHandPiece = hand
                                                if (node != null) currentNode = node
                                                if (pending != null) promotionPendingBy = pending
                                            }
                                        )
                                    },
                                    modifier = Modifier.sizeIn(
                                        maxWidth = 500.dp,
                                        maxHeight = 500.dp
                                    )
                                )

                                PlayerStatusSection(
                                    playerName = if (bottomPlayer == Player.SENTE) senteName else goteName,
                                    mark = if (bottomPlayer == Player.SENTE) "▲" else "△",
                                    isActive = currentPlayer == bottomPlayer,
                                    hand = if (bottomPlayer == Player.SENTE) senteHand else goteHand,
                                    selectedHandPiece = selectedHandPiece,
                                    currentPlayer = currentPlayer,
                                    isFlipped = isBoardFlipped
                                ) { selectedSquare = null; selectedHandPiece = it }

                                SliderControlSection(currentNode, currentPath) { currentNode = it }
                            }
                        }
                    } else {
                        // 【通常画面：縦1列レイアウト】
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            // 解析情報（上部）
                            Column(modifier = Modifier.padding(8.dp)) {
                                pvList.entries
                                    .sortedByDescending { extractScore(it.value, currentPlayer) }
                                    .forEach { (rank, pvText) ->
                                        PvInfoCard(rank, pvText) {
                                            // ★再生に入ったら解析を止める
                                            isAnalysisMode = false
                                            engine.sendCommand("stop")

                                            val usiMoves = pvUsiList[rank] ?: return@PvInfoCard
                                            
                                            // --- 読み筋全体のツリーを構築 ---
                                            var branchPointer = pvBranchRoot ?: currentNode
                                            val branchNodes = mutableListOf<KifuNode>()
                                            
                                            usiMoves.forEach { moveStr ->
                                                try {
                                                    val fromPos = if (moveStr[1] != '*') {
                                                        Pair(moveStr[1] - 'a', 9 - (moveStr[0] - '0'))
                                                    } else null
                                                    val toPos = Pair(moveStr[3] - 'a', 9 - (moveStr[2] - '0'))
                                                    val movingPiece = if (fromPos != null) {
                                                        branchPointer.board[fromPos]
                                                    } else {
                                                        val type = when(moveStr[0]) {
                                                            'P' -> PieceType.PAWN; 'L' -> PieceType.LANCE; 'N' -> PieceType.KNIGHT
                                                            'S' -> PieceType.SILVER; 'G' -> PieceType.GOLD; 'B' -> PieceType.BISHOP; 'R' -> PieceType.ROOK
                                                            else -> null
                                                        }
                                                        if (type != null) Piece(type, branchPointer.currentPlayer) else null
                                                    }
                                                    
                                                    if (movingPiece != null) {
                                                        val label = formatUsiMove(moveStr, branchPointer.board)
                                                        val sym = if (branchPointer.currentPlayer == Player.SENTE) "▲" else "△"
                                                        
                                                        val existing = branchPointer.children.find { it.moveLabel == "$sym$label" }
                                                        if (existing != null) {
                                                            branchPointer = existing
                                                        } else {
                                                            val newBoard = applyUsiMove(moveStr, branchPointer.board, branchPointer.currentPlayer).toMutableMap()
                                                            var newSenteHand = branchPointer.senteHand
                                                            var newGoteHand = branchPointer.goteHand
                                                            
                                                            if (fromPos == null) { // 打ち
                                                                if (branchPointer.currentPlayer == Player.SENTE) {
                                                                    newSenteHand = newSenteHand.toMutableMap().apply { this[movingPiece.type] = (this[movingPiece.type] ?: 1) - 1 }.filterValues { it > 0 }
                                                                } else {
                                                                    newGoteHand = newGoteHand.toMutableMap().apply { this[movingPiece.type] = (this[movingPiece.type] ?: 1) - 1 }.filterValues { it > 0 }
                                                                }
                                                            }
                                                            val captured = branchPointer.board[toPos]
                                                            if (captured != null && captured.type != PieceType.KING) {
                                                                if (branchPointer.currentPlayer == Player.SENTE) {
                                                                    newSenteHand = newSenteHand.toMutableMap().apply { this[captured.type] = (this[captured.type] ?: 0) + 1 }
                                                                } else {
                                                                    newGoteHand = newGoteHand.toMutableMap().apply { this[captured.type] = (this[captured.type] ?: 0) + 1 }
                                                                }
                                                            }
                                                            
                                                            val next = if (branchPointer.currentPlayer == Player.SENTE) Player.GOTE else Player.SENTE
                                                            val newNode = KifuNode(newBoard, newSenteHand, newGoteHand, next, "$sym$label", branchPointer, fromPos, toPos, isPvBranch = true)
                                                            branchPointer.children.add(newNode)
                                                            branchPointer = newNode
                                                        }
                                                        branchNodes.add(branchPointer)
                                                    }
                                                } catch (e: Exception) {}
                                            }
                                            
                                            // --- タップで一手進める ---
                                            val nextInPv = branchNodes.firstOrNull { it.parent == currentNode }
                                            if (nextInPv != null) {
                                                currentNode = nextInPv
                                            } else if (branchNodes.isNotEmpty() && currentNode != branchNodes.lastOrNull()) {
                                                pvBranchRoot = currentNode
                                                currentNode = branchNodes.first()
                                            }
                                        }
                                    }
                            }

                            // 盤面と操作（中央）
                            val topPlayer = if (isBoardFlipped) Player.SENTE else Player.GOTE
                            val bottomPlayer = if (isBoardFlipped) Player.GOTE else Player.SENTE

                            PlayerStatusSection(
                                playerName = if (topPlayer == Player.SENTE) senteName else goteName,
                                mark = if (topPlayer == Player.SENTE) "▲" else "△",
                                isActive = currentPlayer == topPlayer,
                                hand = if (topPlayer == Player.SENTE) senteHand else goteHand,
                                selectedHandPiece = selectedHandPiece,
                                currentPlayer = currentPlayer,
                                isFlipped = isBoardFlipped
                            ) { selectedSquare = null; selectedHandPiece = it }

                            ShogiBoard(
                                boardState = boardState,
                                selectedSquare = selectedSquare,
                                lastFrom = currentNode.lastFrom,
                                lastTo = currentNode.lastTo,
                                isFlipped = isBoardFlipped,
                                onSquareClick = { r, c ->
                                    handleSquareClick(
                                        row = r,
                                        col = c,
                                        boardState = boardState,
                                        currentPlayer = currentPlayer,
                                        selectedSquare = selectedSquare,
                                        selectedHandPiece = selectedHandPiece,
                                        currentNode = currentNode,
                                        onUpdate = { square, hand, node, pending ->
                                            selectedSquare = square
                                            selectedHandPiece = hand
                                            if (node != null) currentNode = node
                                            if (pending != null) promotionPendingBy = pending
                                        }
                                    )
                                },
                                modifier = Modifier.padding(16.dp)
                            )

                            PlayerStatusSection(
                                playerName = if (bottomPlayer == Player.SENTE) senteName else goteName,
                                mark = if (bottomPlayer == Player.SENTE) "▲" else "△",
                                isActive = currentPlayer == bottomPlayer,
                                hand = if (bottomPlayer == Player.SENTE) senteHand else goteHand,
                                selectedHandPiece = selectedHandPiece,
                                currentPlayer = currentPlayer,
                                isFlipped = isBoardFlipped
                            ) { selectedSquare = null; selectedHandPiece = it }

                            SliderControlSection(currentNode, currentPath) { currentNode = it }


                        }
                    }
                }

                // エンジン設定ダイアログ
                if (showSettingsDialog) {
                    AlertDialog(
                        onDismissRequest = { showSettingsDialog = false },
                        title = { Text("エンジンの設定") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column {
                                    Text("思考時間: ${analysisTimeMs}ms", style = MaterialTheme.typography.labelMedium)
                                    Slider(
                                        value = analysisTimeMs.toFloat(),
                                        onValueChange = { analysisTimeMs = it.toLong() },
                                        valueRange = 100f..10000f,
                                        steps = 98
                                    )
                                }
                                Column {
                                    Text("読み筋の数 (MultiPV): $multiPvCount", style = MaterialTheme.typography.labelMedium)
                                    Slider(
                                        value = multiPvCount.toFloat(),
                                        onValueChange = { multiPvCount = it.toInt() },
                                        valueRange = 1f..10f,
                                        steps = 8
                                    )
                                }
                                Column {
                                    Text("スレッド数: $threadCount", style = MaterialTheme.typography.labelMedium)
                                    Slider(
                                        value = threadCount.toFloat(),
                                        onValueChange = { threadCount = it.toInt() },
                                        valueRange = 1f..16f,
                                        steps = 15
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                // 設定を保存
                                prefs.edit().apply {
                                    putLong("analysis_time", analysisTimeMs)
                                    putInt("multi_pv", multiPvCount)
                                    putInt("thread_count", threadCount)
                                }.apply()
                                
                                // エンジンに即座に反映
                                if (isEngineReady) {
                                    engine.sendCommand("stop")
                                    engine.sendCommand("setoption name Threads value $threadCount")
                                    engine.sendCommand("setoption name MultiPV value $multiPvCount")
                                    engine.sendCommand("isready")
                                }
                                showSettingsDialog = false
                            }) { Text("保存") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSettingsDialog = false }) { Text("キャンセル") }
                        }
                    )
                }

                // 成り選択ダイアログ
                promotionPendingBy?.let { move ->
                    AlertDialog(
                        onDismissRequest = { /* キャンセル不可 */ },
                        title = { Text("成り") },
                        text = { Text("成りますか？") },
                        confirmButton = {
                            TextButton(onClick = {
                                val label = formatUsiMove(
                                    "${9 - move.from.second}${('a' + move.from.first)}${9 - move.to.second}${('a' + move.to.first)}+",
                                    boardState
                                )
                                executeMove(
                                    move.from,
                                    move.to,
                                    move.piece,
                                    move.captured,
                                    true,
                                    currentNode,
                                    label,
                                    isPvBranch = true
                                ) { nextNode ->
                                    currentNode = nextNode
                                }
                                promotionPendingBy = null
                            }) { Text("成る") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                val label = formatUsiMove(
                                    "${9 - move.from.second}${('a' + move.from.first)}${9 - move.to.second}${('a' + move.to.first)}",
                                    boardState
                                )
                                executeMove(
                                    move.from,
                                    move.to,
                                    move.piece,
                                    move.captured,
                                    false,
                                    currentNode,
                                    label,
                                    isPvBranch = true
                                ) { nextNode ->
                                    currentNode = nextNode
                                }
                                promotionPendingBy = null
                            }) { Text("成らない") }
                        }
                    )
                }


                // 拡大表示用のオーバーレイ
                if (isOutputExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)) // 半透明の背景
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { isOutputExpanded = false })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .fillMaxHeight(0.7f)
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                // ポップアップウィンドウ自体を半透明にする
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 16.dp,
                                            bottom = 16.dp
                                        )
                                ) {
                                    Text(
                                        text = engineOutput,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    // USIのinfo行を解析して読みやすい文字列にする
    private fun parseInfo(
        line: String,
        currentBoard: Map<Pair<Int, Int>, Piece>,
        turn: Player
    ): String {
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
                                val rawV = value.toIntOrNull() ?: 0
                                // 常に先手視点に固定 (後手番なら符号を反転)
                                val v = if (turn == Player.SENTE) rawV else -rawV
                                val sign = if (v > 0) "+" else ""

                                val status = when {
                                    v in -200..200 -> "互角"
                                    v in 201..500 -> "先手指しやすい"
                                    v in -500..-201 -> "後手指しやすい"
                                    v in 501..1000 -> "先手有利"
                                    v in -1000..-501 -> "後手有利"
                                    v in 1001..2000 -> "先手優勢"
                                    v in -2000..-1001 -> "後手優勢"
                                    v > 2000 -> "先手勝勢"
                                    v < -2000 -> "後手勝勢"
                                    else -> ""
                                }
                                "評価: $sign$v ($status)"
                            }

                            "mate" -> {
                                val v = value.toIntOrNull() ?: 0
                                // 詰みの判定も先手視点の勝ち/負けに整理
                                val winner = if (v > 0) {
                                    if (turn == Player.SENTE) "先手勝ち" else "後手勝ち"
                                } else if (v < 0) {
                                    if (turn == Player.SENTE) "後手勝ち" else "先手勝ち"
                                } else ""
                                if (winner.isNotEmpty()) "${kotlin.math.abs(v)}手詰（$winner）" else "詰み"
                            }

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

                            tempBoard = applyUsiMove(moveStr, tempBoard, tempTurn)  // 盤面を1手進める
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
        //if (depth.isNotEmpty()) result.add("深さ: $depth")
        if (score.isNotEmpty()) result.add(score)
        if (pv.isNotEmpty()) result.add(pv)

        return result.joinToString("\n")
    }

    // USI形式の指し手(7g7f)を日本語座標(7七銀)に変換
    private fun formatUsiMove(usiMove: String, board: Map<Pair<Int, Int>, Piece>? = null): String {
        if (usiMove.length < 4) return usiMove

        fun colToNum(c: Char) = c.toString()
        fun rowToKanji(c: Char) = when (c) {
            'a' -> "一"; 'b' -> "二"; 'c' -> "三"; 'd' -> "四"; 'e' -> "五"
            'f' -> "六"; 'g' -> "七"; 'h' -> "八"; 'i' -> "九"
            else -> c.toString()
        }

        return try {
            if (usiMove[1] == '*') {
                // 持ち駒を打つ (P*5e → 5五歩打)
                val piece = when (usiMove[0]) {
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
    private fun copyAssetsToFileDir(
        filename: String,
        subDir: String = "",
        baseDir: java.io.File,
        assetManager: android.content.res.AssetManager  // ← 追加
    ) {
        val targetDir = if (subDir.isNotEmpty()) {
            val dir = java.io.File(baseDir, subDir)
            if (!dir.exists()) dir.mkdirs()
            dir
        } else {
            baseDir
        }

        val file = java.io.File(targetDir, filename)
        // 開発中は毎回上書きコピーするようにして、確実に最新のファイルを届ける
        // if (file.exists()) return

        try {
            assetManager.open(filename).use { inputStream ->  // assets → assetManager
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
        isPvBranch: Boolean = false,
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
        val newNode = KifuNode(
            newBoard, newSenteHand, newGoteHand, nextPlayer, label, parentNode, from, to,
            isPvBranch = isPvBranch
        )

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
        getSharedPreferences("kifu_prefs", android.content.Context.MODE_PRIVATE).edit()
            .putString("current_tree", json.toString()).apply()
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
        json.put(
            "lastFrom",
            if (node.lastFrom != null) "${node.lastFrom.first},${node.lastFrom.second}" else null
        )
        json.put(
            "lastTo",
            if (node.lastTo != null) "${node.lastTo.first},${node.lastTo.second}" else null
        )

        // 子要素 (再帰)
        val childrenJson = JSONArray()
        node.children
            .filter { !it.isPvBranch }  // 読み筋は保存しない
            .forEach { childrenJson.put(kifuTreeToJson(it)) }
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

        val lastFrom =
            json.optString("lastFrom", null)?.takeIf { it != "null" && it.isNotEmpty() }?.split(",")
                ?.let { Pair(it[0].toInt(), it[1].toInt()) }
        val lastTo =
            json.optString("lastTo", null)?.takeIf { it != "null" && it.isNotEmpty() }?.split(",")
                ?.let { Pair(it[0].toInt(), it[1].toInt()) }

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
                    val row =
                        "一二三四五六七八九".indexOf(rowChar).takeIf { it != -1 } ?: return@forEach
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
                    val type =
                        PieceType.entries.find { pieceStr.contains(it.label) } ?: return@forEach
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
                android.util.Log.d(
                    "parseKif",
                    "手目=${tempNode.moveCount} label=${tempNode.moveLabel} 先手持ち駒=${tempNode.senteHand} 後手持ち駒=${tempNode.goteHand}"
                )
            } catch (e: Exception) {
                android.util.Log.e("parseKif", "Error: ${e.message}, line: $line")
            }
        }
        return if (tempNode != root) tempNode else null
    }

    // 指定された場所へ指定された駒を動かせる元のマスを探す
    private fun findSourceSquare(
        to: Pair<Int, Int>,
        label: String,
        node: KifuNode
    ): Pair<Int, Int>? {
        if (label.contains("打")) return Pair(-1, -1) // 駒打ちフラグ

        val type = PieceType.entries.find {
            label.contains(it.label) || (it.promotedLabel != null && label.contains(it.promotedLabel))
        } ?: return null

        // 盤面上の自分の駒をしらみつぶしに探す
        for ((pos, piece) in node.board) {
            if (piece.owner == node.currentPlayer) {
                // 駒の種類が一致（成駒も考慮）
                val pieceMatches = if (label.contains("成") && !piece.isPromoted) {
                    piece.type == type // 成る前の種類が一致
                } else {
                    val currentLabel = if (piece.isPromoted) piece.type.promotedLabel
                        ?: piece.type.label else piece.type.label
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
        if (node.currentPlayer == Player.SENTE && node.senteHand.containsKey(type)) return Pair(
            -1,
            -1
        )
        if (node.currentPlayer == Player.GOTE && node.goteHand.containsKey(type)) return Pair(
            -1,
            -1
        )

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
                        formatUsiMove(
                            "${fromStr[0]}${('a' + fromRow!!)}${toStr[0]}${('a' + toRow)}${if (isPromoted && !movingPiece.isPromoted) "+" else ""}",
                            tempNode.board
                        )
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
                } catch (e: Exception) {
                }
            }
        }
        return if (tempNode != root) tempNode else null
    }

    // USI形式の棋譜を解析してツリーを構築
    private fun parseKifu(text: String, root: KifuNode): KifuNode? {
        // "position startpos moves 7g7f 3c3d..." などの形式に対応
        val movesPart =
            if (text.contains("moves")) text.substringAfter("moves").trim() else text.trim()
        val moves = movesPart.split(Regex("\\s+")).filter { it.length >= 4 }

        var tempNode = root
        moves.forEach { moveStr ->
            try {
                if (moveStr[1] == '*') {
                    // 駒打ち
                    val type = when (moveStr[0]) {
                        'P' -> PieceType.PAWN; 'L' -> PieceType.LANCE; 'N' -> PieceType.KNIGHT
                        'S' -> PieceType.SILVER; 'G' -> PieceType.GOLD; 'B' -> PieceType.BISHOP; 'R' -> PieceType.ROOK
                        else -> return@forEach
                    }
                    val toCol = 9 - (moveStr[2] - '0')
                    val toRow = moveStr[3] - 'a'
                    val label = "${moveStr[2]}${rowToKanji(moveStr[3])}${type.label}打"

                    executeMove(
                        null,
                        Pair(toRow, toCol),
                        Piece(type, tempNode.currentPlayer),
                        null,
                        false,
                        tempNode,
                        label
                    ) {
                        tempNode = it
                    }
                    // parseKif の executeMove 呼び出し後に追加
                    android.util.Log.d(
                        "parseKif",
                        "手目=${tempNode.moveCount} 先手持ち駒=${tempNode.senteHand} 後手持ち駒=${tempNode.goteHand}"
                    )
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

                    executeMove(
                        Pair(fromRow, fromCol),
                        Pair(toRow, toCol),
                        piece,
                        captured,
                        promote,
                        tempNode,
                        label
                    ) {
                        tempNode = it
                    }
                    // parseKif の executeMove 呼び出し後に追加
                    android.util.Log.d(
                        "parseKif",
                        "手目=${tempNode.moveCount} 先手持ち駒=${tempNode.senteHand} 後手持ち駒=${tempNode.goteHand}"
                    )
                }
            } catch (e: Exception) {
            }
        }
        return if (tempNode != root) tempNode else null
    }

    private fun rowToKanji(c: Char) = when (c) {
        'a' -> "一"; 'b' -> "二"; 'c' -> "三"; 'd' -> "四"; 'e' -> "五"
        'f' -> "六"; 'g' -> "七"; 'h' -> "八"; 'i' -> "九"
        else -> c.toString()
    }

    // USI形式の1手を盤面に適用して新しい盤面を返す
    private fun applyUsiMove(
        usiMove: String,
        board: Map<Pair<Int, Int>, Piece>,
        player: Player = Player.SENTE
    ): Map<Pair<Int, Int>, Piece> {
        if (usiMove.length < 4) return board
        val newBoard = board.toMutableMap()

        return try {
            if (usiMove[1] == '*') {
                // 持ち駒を打つ (P*5e)
                val type = when (usiMove[0]) {
                    'P' -> PieceType.PAWN; 'L' -> PieceType.LANCE; 'N' -> PieceType.KNIGHT
                    'S' -> PieceType.SILVER; 'G' -> PieceType.GOLD
                    'B' -> PieceType.BISHOP; 'R' -> PieceType.ROOK
                    else -> return board
                }
                val toCol = usiMove[2] - '0'
                val toRow = usiMove[3] - 'a'
                // 手番の情報を使用して正しい向きの駒を配置
                newBoard[Pair(toRow, 9 - toCol)] = Piece(type, player)
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

    private fun handleSquareClick(
        row: Int,
        col: Int,
        boardState: Map<Pair<Int, Int>, Piece>,
        currentPlayer: Player,
        selectedSquare: Pair<Int, Int>?,
        selectedHandPiece: Pair<Player, PieceType>?,
        currentNode: KifuNode,
        onUpdate: (Pair<Int, Int>?, Pair<Player, PieceType>?, KifuNode?, PendingMove?) -> Unit
    ) {
        val clickedPos = Pair(row, col)

        if (selectedHandPiece != null) {
            // 持ち駒を打つ
            if (boardState[clickedPos] == null && selectedHandPiece.first == currentPlayer) {
                val (player, type) = selectedHandPiece

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
                    executeMove(
                        null,
                        clickedPos,
                        Piece(type, player),
                        null,
                        false,
                        currentNode,
                        moveLabel,
                        isPvBranch = true
                    ) { nextNode ->
                        onUpdate(null, null, nextNode, null)
                    }
                }
            }
        } else {
            val currentSelected = selectedSquare
            if (currentSelected == null) {
                // 駒を選択
                val piece = boardState[clickedPos]
                if (piece != null && piece.owner == currentPlayer) {
                    onUpdate(clickedPos, null, null, null)
                }
            } else {
                if (currentSelected == clickedPos) {
                    // 選択解除
                    onUpdate(null, null, null, null)
                } else {
                    val movingPiece = boardState[currentSelected]
                    val targetPiece = boardState[clickedPos]

                    if (movingPiece != null && movingPiece.owner == currentPlayer && movingPiece.owner != targetPiece?.owner) {
                        // 駒の動きがルール通りかチェック
                        if (isValidMovePattern(currentSelected, clickedPos, movingPiece, boardState)) {
                            // 成り判定
                            val canPromote =
                                !movingPiece.isPromoted && movingPiece.type.promotedLabel != null
                            val isSenteZone = clickedPos.first <= 2 || currentSelected.first <= 2
                            val isGoteZone = clickedPos.first >= 6 || currentSelected.first >= 6
                            val enteringZone =
                                if (movingPiece.owner == Player.SENTE) isSenteZone else isGoteZone

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
                                val moveLabel = formatUsiMove(
                                    "${9 - currentSelected.second}${('a' + currentSelected.first)}${9 - clickedPos.second}${('a' + clickedPos.first)}+",
                                    boardState
                                )
                                executeMove(
                                    currentSelected,
                                    clickedPos,
                                    movingPiece,
                                    targetPiece,
                                    true,
                                    currentNode,
                                    moveLabel,
                                    isPvBranch = true
                                ) { nextNode ->
                                    onUpdate(null, null, nextNode, null)
                                }
                            } else if (canPromote && enteringZone) {
                                // 成り選択用
                                onUpdate(null, null, null, PendingMove(currentSelected, clickedPos, movingPiece, targetPiece))
                            } else {
                                val moveLabel = formatUsiMove(
                                    "${9 - currentSelected.second}${('a' + currentSelected.first)}${9 - clickedPos.second}${('a' + clickedPos.first)}",
                                    boardState
                                )
                                executeMove(
                                    currentSelected,
                                    clickedPos,
                                    movingPiece,
                                    targetPiece,
                                    false,
                                    currentNode,
                                    moveLabel,
                                    isPvBranch = true
                                ) { nextNode ->
                                    onUpdate(null, null, nextNode, null)
                                }
                            }
                        }
                    } else if (targetPiece?.owner == movingPiece?.owner) {
                        // 自分の別の駒を選択し直す
                        onUpdate(clickedPos, null, null, null)
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerInfoContent(
    name: String,
    mark: String,
    isFlipped: Boolean = false
) {
    // コンテキスト外に出すために、まずデフォルトのサイズを取得する
    val defaultFontSize = MaterialTheme.typography.titleMedium.fontSize
    var fontSize by remember(name) { mutableStateOf(defaultFontSize) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            if(((mark == "▲") && !isFlipped) || ((mark == "△") && isFlipped)) {
                Arrangement.End} else {
                Arrangement.Start
            }
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

//data class PendingMove(
//    val from: Pair<Int, Int>,
//    val to: Pair<Int, Int>,
//    val piece: Piece,
//    val captured: Piece?
//)

@Composable
fun Modifier.repeatingClickable(
    enabled: Boolean = true,
    initialDelay: Long = 500L,
    delay: Long = 100L,
    onClick: () -> Unit
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val scope = rememberCoroutineScope()

    return if (!enabled) this else this.pointerInput(enabled) {
        // pointerInputスコープ内では、coroutineScope { ... } を使うか、
        // detectTapGestures のブロック内で直接 launch が使えます
        detectTapGestures(
            onPress = {
                // ここで自動的に CoroutineScope が提供されます
                val job = scope.launch {
                    //currentOnClick()
                    delay(initialDelay)
                    while (true) {
                        currentOnClick()
                        delay(delay)
                    }
                }
                tryAwaitRelease() // 指が離れるまで待機
                job.cancel()      // 離れたらキャンセル
            },
            // 単発のタップ（クリック）の処理
            onTap = {
                currentOnClick()
            }
        )
    }
}

//fun extractScore(pvText: String, turn: Player): Int {
//    // 詰みの判定
//    val mateLine = pvText.lines().find { it.contains("手詰") }
//    if (mateLine != null) {
//        val isSenteWin = mateLine.contains("先手勝ち")
//        return if (turn == Player.SENTE) {
//            if (isSenteWin) Int.MAX_VALUE else Int.MIN_VALUE
//        } else {
//            if (!isSenteWin) Int.MAX_VALUE else Int.MIN_VALUE
//        }
//    }
//
//    // 評価値の抽出
//    val scoreLine = pvText.lines().find { it.startsWith("評価:") } ?: return 0
//    // 「評価: +123 (互角)」から "+123" を取り出す
//    val vStr = scoreLine.substringAfter("評価:").trim().split(" ")[0]
//    val v = vStr.toIntOrNull() ?: 0
//
//    // 手番の人にとって良い順にするため、後手番なら符号を反転させて評価する
//    return if (turn == Player.SENTE) v else -v
//}

@Composable
fun PvInfoCard(rank: Int, pvText: String, onTap: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onTap() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = pvText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun PlayerStatusSection(
    playerName: String,
    mark: String,
    isActive: Boolean,
    hand: Map<PieceType, Int>,
    selectedHandPiece: Pair<Player, PieceType>?,
    currentPlayer: Player,
    isFlipped: Boolean = false,
    onSelected: (Pair<Player, PieceType>?) -> Unit
) {
    val player = if (mark == "▲") Player.SENTE else Player.GOTE
    Column(modifier = Modifier.fillMaxWidth()) {
        PlayerInfoContent(
            name = playerName,
            mark = mark,
            isFlipped = isFlipped
        )
        HandView(
            hand = hand,
            player = player,
            selectedPieceType = selectedHandPiece?.takeIf { it.first == player }?.second,
            onPieceClick = { type -> if (isActive) onSelected(Pair(player, type)) },
            isFlipped = isFlipped
        )
    }
}

@Composable
fun SliderControlSection(
    currentNode: KifuNode,
    currentPath: List<KifuNode>,
    onNodeChange: (KifuNode) -> Unit
) {
    val currentIndex = currentPath.indexOf(currentNode).coerceAtLeast(0)
    val maxIndex = (currentPath.size - 1).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${currentNode.moveCount}手目 / ${maxIndex}手",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .repeatingClickable(enabled = currentIndex > 0) {
                        if (currentIndex > 0) onNodeChange(currentPath[currentIndex - 1])
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "前へ",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Slider(
                value = currentIndex.toFloat(),
                onValueChange = { v ->
                    val idx = v.toInt().coerceIn(0, maxIndex)
                    onNodeChange(currentPath[idx])
                },
                valueRange = 0f..maxIndex.toFloat().coerceAtLeast(1f),
                steps = (maxIndex - 1).coerceAtLeast(0),
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .repeatingClickable(enabled = currentIndex < maxIndex) {
                        if (currentIndex < maxIndex) onNodeChange(currentPath[currentIndex + 1])
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "次へ",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
