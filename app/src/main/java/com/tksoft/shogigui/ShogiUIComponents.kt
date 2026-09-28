package com.tksoft.shogigui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.tksoft.shogigui.ui.theme.ShogiGUITheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 読み筋のランクに応じたカラーパレット
@Composable
fun getPvColor(rank: Int): Color {
    return when (rank) {
        1 -> MaterialTheme.colorScheme.primaryContainer
        2 -> MaterialTheme.colorScheme.secondaryContainer
        3 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
fun PvInfoCard(rank: Int, pvText: String, currentMoveLabel: String, onTap: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onTap() },
        colors = CardDefaults.cardColors(
            containerColor = getPvColor(rank)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            val annotated = buildAnnotatedString {
                if (currentMoveLabel.isNotEmpty() && currentMoveLabel != "開始局面" && pvText.contains(currentMoveLabel)) {
                    var start = 0
                    while (true) {
                        val idx = pvText.indexOf(currentMoveLabel, start)
                        if (idx < 0) { append(pvText.substring(start)); break }
                        append(pvText.substring(start, idx))
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(currentMoveLabel) }
                        start = idx + currentMoveLabel.length
                    }
                } else {
                    append(pvText)
                }
            }
            Text(
                text = annotated,
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
    handOnTop: Boolean = false,
    gameResult: String = "",
    remainingMs: Long? = null,
    onNameClick: () -> Unit = {},
    onPiecePositioned: (Player, PieceType, androidx.compose.ui.layout.LayoutCoordinates) -> Unit = { _, _, _ -> },
    onSelected: (Pair<Player, PieceType>?) -> Unit
) {
    val player = if (mark == "▲") Player.SENTE else Player.GOTE
    val handView: @Composable () -> Unit = {
        HandView(
            hand = hand,
            player = player,
            selectedPieceType = selectedHandPiece?.takeIf { it.first == player }?.second,
            onPieceClick = { type -> if (isActive) onSelected(Pair(player, type)) },
            isFlipped = isFlipped,
            onPiecePositioned = { type, coords -> onPiecePositioned(player, type, coords) }
        )
    }
    val nameColor = if (mark == "▲") senteNameColor else goteNameColor
    val nameView: @Composable () -> Unit = {
        PlayerInfoContent(name = playerName, mark = mark, isFlipped = isFlipped, gameResult = gameResult, nameColor = nameColor, remainingMs = remainingMs, onNameClick = onNameClick)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (handOnTop) { handView(); nameView() } else { nameView(); handView() }
    }
}

// 棋譜ツリーのレイアウト結果。nodes[i] を (col[i]=手数, row[i]=行) に置き、parentIdx[i] と線で結ぶ
private class KifuTreeLayout(
    val nodes: List<KifuNode>,
    val col: IntArray,
    val row: IntArray,
    val parentIdx: IntArray,
    val indexOf: Map<KifuNode, Int>,
    val maxCol: Int,
    val rowCount: Int
)

// 本譜（continuationChild を辿る列）を 0 行目に左から右へ一直線に並べ、
// 分岐はそれぞれ新しい行を割り当てて下へ並べる（樹形図）
private fun layoutKifuTree(root: KifuNode): KifuTreeLayout {
    val nodes = ArrayList<KifuNode>()
    val cols = ArrayList<Int>(); val rows = ArrayList<Int>(); val parents = ArrayList<Int>()
    val indexOf = HashMap<KifuNode, Int>()
    var nextRow = 0
    fun layRow(start: KifuNode) {
        val row = nextRow++
        val pending = ArrayList<KifuNode>()
        var n: KifuNode? = start
        while (n != null) {
            indexOf[n] = nodes.size
            nodes.add(n); cols.add(n.moveCount - root.moveCount); rows.add(row)
            parents.add(n.parent?.let { indexOf[it] } ?: -1)
            // 同じ行に続ける子以外（手作業の手・別候補の読み筋）は新しい行へ
            val next = n.continuationChild()
            n.children.forEach { if (it !== next) pending.add(it) }
            n = next
        }
        // 分岐点が右（後）にある枝ほど親の行の近くに置く。枝とその子孫の行は連続させる。
        // こうすると、親から遠い行へ下りる斜め線が通る列には、間の行の線がまだ始まっていないので交差しない
        pending.asReversed().forEach { layRow(it) }
    }
    layRow(root)
    return KifuTreeLayout(
        nodes, cols.toIntArray(), rows.toIntArray(), parents.toIntArray(), indexOf,
        cols.maxOrNull() ?: 0, nextRow
    )
}

// 2本指以上のピンチだけを検出する（指の並びの向きによらず、指の間隔の変化を倍率とする）。
// Initial パスで子より先に受け取り、2本指の間はイベントを消費して子のドラッグ・タップを止める。
// 1本指の操作には触れないので、子のタップやスクロールはそのまま動く
private suspend fun PointerInputScope.detectPinchGestures(onZoom: (zoom: Float) -> Unit) {
    // 指の間隔（重心からの平均距離）がこれ未満のときは倍率を変えない（誤差で暴れないように）
    val minSpan = 12.dp.toPx()
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pts = event.changes.filter { it.pressed && it.previousPressed }
            if (pts.size >= 2) {
                val n = pts.size.toFloat()
                val cur = Offset(pts.sumOf { it.position.x.toDouble() }.toFloat() / n, pts.sumOf { it.position.y.toDouble() }.toFloat() / n)
                val prev = Offset(pts.sumOf { it.previousPosition.x.toDouble() }.toFloat() / n, pts.sumOf { it.previousPosition.y.toDouble() }.toFloat() / n)
                val span = pts.map { (it.position - cur).getDistance() }.average().toFloat()
                val pSpan = pts.map { (it.previousPosition - prev).getDistance() }.average().toFloat()
                if (span > minSpan && pSpan > minSpan) onZoom(span / pSpan)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
fun KifuTreeSection(
    currentNode: KifuNode,
    currentPath: List<KifuNode>,
    onNodeChange: (KifuNode) -> Unit
) {
    val currentIndex = currentPath.indexOf(currentNode).coerceAtLeast(0)
    val maxIndex = (currentPath.size - 1).coerceAtLeast(0)
    // 樹形図の縦方向の拡大縮小（KifuTreeView が登録する）
    val zoomHandler = remember { mutableStateOf<((Float) -> Unit)?>(null) }

    Column(
        // ピンチは樹形図だけでなくセクション全体（手数表示・矢印ボタン含む）で受ける。
        // 樹形図は高さが小さく、斜めや縦のピンチだと2本目の指が樹形図の外に出てしまうため
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            .pointerInput(Unit) { detectPinchGestures { zoom -> zoomHandler.value?.invoke(zoom) } },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.move_counter, currentNode.moveCount, maxIndex),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .repeatingClickable(enabled = currentIndex > 0) { if (currentIndex > 0) onNodeChange(currentPath[currentIndex - 1]) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.nav_previous),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)) }

            KifuTreeView(
                currentNode = currentNode,
                currentPath = currentPath,
                onNodeChange = onNodeChange,
                zoomHandler = zoomHandler,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer)
                    .repeatingClickable(enabled = currentIndex < maxIndex) { if (currentIndex < maxIndex) onNodeChange(currentPath[currentIndex + 1]) },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.nav_next), tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp)) }
        }
    }
}

// 手順をドットと線の樹形図で表示し、現在の手順のドットに形勢棒グラフを重ねる。
// ピンチ（zoomHandler 経由）で縦方向を縮小・ドラッグで上下スクロール・タップでその手へ移動。
@Composable
private fun KifuTreeView(
    currentNode: KifuNode,
    currentPath: List<KifuNode>,
    onNodeChange: (KifuNode) -> Unit,
    zoomHandler: MutableState<((Float) -> Unit)?>,
    modifier: Modifier = Modifier
) {
    var root = currentNode
    while (root.parent != null) root = root.parent!!
    // children (SnapshotStateList) をコンポジション中に読むので、ツリーが変われば再レイアウトされる
    val layout = layoutKifuTree(root)
    val pathSet = remember(currentPath) { currentPath.toHashSet() }

    val density = LocalDensity.current
    // 行間は棒グラフ（上下）がちょうど収まる高さ
    val baseRowPx = with(density) { 48.dp.toPx() }
    val padX = with(density) { 12.dp.toPx() }
    val minPadYPx = with(density) { 12.dp.toPx() }
    val minScaleY = 0.25f
    val maxScaleY = 1f // 縦はデフォルトより拡大しない（縮小して全体を見る用途のみ）

    val visibleRows = layout.rowCount.coerceIn(1, 2)
    val viewHeight = 48.dp * visibleRows

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var initialized by remember(root) { mutableStateOf(false) }
    var offsetY by remember(root) { mutableFloatStateOf(0f) }
    var scaleY by remember(root) { mutableFloatStateOf(1f) } // 縦方向の倍率（行間）
    fun rowPx() = baseRowPx * scaleY
    // 棒グラフの半分の高さ (±2000 で振り切れ)。隣の行の棒と重ならないよう行間の半分弱にする
    fun barHalfPx() = rowPx() * 0.46f
    // 上下の余白は棒グラフが切れない高さ（ただし表示高さの半分まで）
    fun padY() = barHalfPx().coerceAtLeast(minPadYPx).coerceAtMost(viewSize.height / 2f)

    val currentLayout by rememberUpdatedState(layout)
    val currentOnNodeChange by rememberUpdatedState(onNodeChange)
    // 横方向は拡大縮小せず、手数に合わせて常に幅いっぱいに並べる
    val offsetX = padX
    fun colPx() = (viewSize.width - 2 * padX) / currentLayout.maxCol.coerceAtLeast(1)

    fun clampOffsets() {
        val l = currentLayout
        val h = viewSize.height.toFloat()
        val contentH = (l.rowCount - 1) * rowPx()
        val padY = padY()
        offsetY = if (contentH <= h - 2 * padY) padY else offsetY.coerceIn(h - padY - contentH, padY)
    }

    // 初期表示: 本譜の行を上端に
    LaunchedEffect(root, viewSize) {
        if (viewSize.width == 0 || initialized) return@LaunchedEffect
        offsetY = padY()
        initialized = true
        clampOffsets()
    }

    // 現在の手の行が表示範囲外に出たらそこへスクロール。高さ変化 (分岐の追加) でも再クランプする
    LaunchedEffect(currentNode, initialized, viewSize, layout.rowCount) {
        if (!initialized || viewSize.width == 0) return@LaunchedEffect
        val l = currentLayout
        val idx = l.indexOf[currentNode] ?: return@LaunchedEffect
        val h = viewSize.height.toFloat()
        val y = offsetY + l.row[idx] * rowPx()
        if (y < padY() || y > h - padY()) offsetY = h / 2f - l.row[idx] * rowPx()
        clampOffsets()
    }

    // ピンチの向きによらず縦方向（行間）だけ拡大縮小。現在の手の行が動かないようにオフセットを補正
    val currentNodeState by rememberUpdatedState(currentNode)
    SideEffect {
        zoomHandler.value = handler@{ zoom ->
            if (!initialized) return@handler
            val oldY = scaleY
            val newY = (oldY * zoom).coerceIn(minScaleY, maxScaleY)
            val row = currentLayout.indexOf[currentNodeState]?.let { currentLayout.row[it] } ?: 0
            val anchorY = offsetY + row * rowPx()
            offsetY = anchorY - (anchorY - offsetY) * (newY / oldY)
            scaleY = newY
            clampOffsets()
        }
    }

    // M3 Slider のデフォルト配色 (SliderDefaults.colors)
    val activeTrackColor = MaterialTheme.colorScheme.primary
    val inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer
    val activeTickColor = MaterialTheme.colorScheme.onPrimary
    val inactiveTickColor = MaterialTheme.colorScheme.onSecondaryContainer
    val thumbColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    // 現在の手順上で、現在の手までの手 (= Slider のアクティブ側)
    val pathIndex = remember(currentPath) { HashMap<KifuNode, Int>().also { m -> currentPath.forEachIndexed { i, n -> m[n] = i } } }
    val currentPathIdx = pathIndex[currentNode] ?: -1

    // M3 Expressive: トーナルなコンテナ + やや控えめな角丸
    Canvas(
        modifier = modifier
            .height(viewHeight)
            .clip(MaterialTheme.shapes.large)
            .background(containerColor)
            .onSizeChanged { viewSize = it }
            .pointerInput(root) {
                // 1本指のドラッグで上下スクロール（2本指はセクション側のピンチが消費するので来ない）
                detectDragGestures { change, drag ->
                    if (!initialized) return@detectDragGestures
                    offsetY += drag.y
                    clampOffsets()
                    change.consume()
                }
            }
            .pointerInput(root) {
                detectTapGestures { pos ->
                    if (!initialized) return@detectTapGestures
                    val l = currentLayout
                    val colPx = colPx()
                    val rowPx = rowPx()
                    val threshold = 28.dp.toPx()
                    var best = -1; var bestD = threshold * threshold
                    for (i in l.nodes.indices) {
                        val dx = offsetX + l.col[i] * colPx - pos.x
                        val dy = offsetY + l.row[i] * rowPx - pos.y
                        val d = dx * dx + dy * dy
                        if (d < bestD) { bestD = d; best = i }
                    }
                    if (best >= 0) currentOnNodeChange(l.nodes[best])
                }
            }
    ) {
        if (!initialized) return@Canvas
        val colPx = colPx()
        val rowPx = rowPx()
        // Slider の寸法 (SliderTokens) を基に、トラックは細め (6dp)。つまみ 4x24dp・つまみとトラックの隙間 6dp・目盛り径 3dp。
        // 縦に縮小したときは行間に収まるよう縮める
        val trackH = minOf(6.dp.toPx(), rowPx * 0.25f)
        val thumbW = 4.dp.toPx()
        val thumbH = minOf(20.dp.toPx(), rowPx * 0.5f)
        val thumbGap = 6.dp.toPx()
        val tickR = minOf(1.5f.dp.toPx(), trackH / 4f)
        val left = -trackH * 2; val right = size.width + trackH * 2
        fun pos(i: Int) = Offset(offsetX + layout.col[i] * colPx, offsetY + layout.row[i] * rowPx)
        fun isActive(node: KifuNode) = (pathIndex[node] ?: Int.MAX_VALUE) <= currentPathIdx
        fun trackColor(node: KifuNode) = if (isActive(node)) activeTrackColor else inactiveTrackColor
        val cursorIdx = layout.indexOf[currentNode]
        val cut = thumbW / 2 + thumbGap // つまみの左右はトラックを切り欠く

        // トラック: 未到達側を先に、到達側を上に描く。
        // 線は端を切りっぱなしにし、各ドットの位置に円を置いて角丸・継ぎ目にする（つまみの位置は除く）
        for (pass in 0..1) {
            val wantActive = pass == 1
            for (i in layout.nodes.indices) {
                val p = layout.parentIdx[i]
                if (p < 0) continue
                val node = layout.nodes[i]
                if (isActive(node) != wantActive) continue
                var a = pos(p); var b = pos(i)
                if (b.x < left || a.x > right) continue
                val len = (b - a).getDistance()
                if (len <= 0f) continue
                val dir = (b - a) / len
                var l = len
                if (p == cursorIdx) { a += dir * cut; l -= cut }
                if (i == cursorIdx) { b -= dir * cut; l -= cut }
                if (l <= 0f) continue
                // 分岐して別の行へ下りる斜めの線はごく細く、現在の手の左右によらず常にアクティブ側の濃い色
                val diagonal = layout.row[p] != layout.row[i]
                if (diagonal) drawLine(activeTrackColor, a, b, strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                else drawLine(trackColor(node), a, b, strokeWidth = trackH, cap = StrokeCap.Butt)
            }
            for (i in layout.nodes.indices) {
                if (i == cursorIdx) continue
                val node = layout.nodes[i]
                // 根は子（最初の手）の色に合わせる
                val colorNode = if (layout.parentIdx[i] < 0) node.continuationChild() ?: node.children.firstOrNull() ?: node else node
                if (isActive(colorNode) != wantActive) continue
                val c = pos(i)
                if (c.x < left || c.x > right) continue
                // トラック端の丸みは、つまみに接していない側だけ
                val nearThumb = cursorIdx?.let { (pos(it) - c).getDistance() < cut + trackH / 2 } ?: false
                if (!nearThumb) drawCircle(trackColor(colorNode), radius = trackH / 2, center = c)
            }
        }

        // 目盛り（各手）。密集して見分けられないときは省く
        if (colPx >= tickR * 4) {
            for (i in layout.nodes.indices) {
                if (i == cursorIdx) continue
                val c = pos(i)
                if (c.x < left || c.x > right) continue
                val colorNode = if (layout.parentIdx[i] < 0) layout.nodes[i].continuationChild() ?: layout.nodes[i] else layout.nodes[i]
                if (cursorIdx != null && (pos(cursorIdx) - c).getDistance() < cut) continue
                drawCircle(if (isActive(colorNode)) activeTickColor else inactiveTickColor, radius = tickR, center = c)
            }
        }

        // 形勢棒グラフ（解析済みの全ての手）: 各手の位置を中心に上下へ伸ばす。詰みは不透明
        val barWidth = colPx.coerceAtLeast(2f)
        for (i in layout.nodes.indices) {
            val score = layout.nodes[i].evalScore ?: continue
            val c = pos(i)
            if (c.x < left || c.x > right) continue
            val isMate = score > 10000 || score < -10000
            val normalized = (score.toFloat() / 2000f).coerceIn(-1f, 1f)
            val barColor = when {
                isMate && score > 0 -> senteMateColor
                isMate && score < 0 -> goteMateColor
                score >= 0 -> senteBarColor
                else -> goteBarColor
            }
            drawLine(barColor, c, Offset(c.x, c.y - normalized * barHalfPx()), strokeWidth = barWidth)
        }

        // 現在の手: Slider のつまみ（縦長の角丸バー）
        cursorIdx?.let { i ->
            val c = pos(i)
            drawRoundRect(
                thumbColor,
                topLeft = Offset(c.x - thumbW / 2, c.y - thumbH / 2),
                size = androidx.compose.ui.geometry.Size(thumbW, thumbH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbW / 2)
            )
        }
    }
}

@Composable
fun PlayerInfoContent(name: String, mark: String, isFlipped: Boolean = false, gameResult: String = "", nameColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified, remainingMs: Long? = null, onNameClick: () -> Unit = {}) {
    val defaultFontSize = MaterialTheme.typography.titleMedium.fontSize
    var fontSize by remember(name) { mutableStateOf(defaultFontSize) }
    val isRight = ((mark == "▲") && !isFlipped) || ((mark == "△") && isFlipped)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isRight) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).clickable { onNameClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "$mark ", style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Bold, color = nameColor, maxLines = 1)
            Text(text = name, style = MaterialTheme.typography.titleMedium.copy(fontSize = fontSize), color = nameColor, maxLines = 1, softWrap = false, onTextLayout = { if (it.hasVisualOverflow && fontSize > 8.sp) fontSize *= 0.9f })
        }
        if (remainingMs != null) {
            Text(
                text = formatRemainingTime(remainingMs),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                color = if (remainingMs in 0L..59_999L) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        if (gameResult.isNotEmpty() && isRight) {
            Text(
                text = gameResult,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun KifuTreeSectionPreview() {
    val emptyBoard = emptyMap<Pair<Int, Int>, Piece>()
    val emptyHand = emptyMap<PieceType, Int>()
    val root = KifuNode(emptyBoard, emptyHand, emptyHand, Player.SENTE, "開始局面")
    val n1 = KifuNode(emptyBoard, emptyHand, emptyHand, Player.GOTE, "▲7六歩", root)
    val n2 = KifuNode(emptyBoard, emptyHand, emptyHand, Player.SENTE, "△3四歩", n1)
    val n3 = KifuNode(emptyBoard, emptyHand, emptyHand, Player.GOTE, "▲2六歩", n2, isPvBranch = true, pvColorIndex = 1)
    val n4 = KifuNode(emptyBoard, emptyHand, emptyHand, Player.SENTE, "△8四歩", n3, isPvBranch = true, pvColorIndex = 2)
    val b3 = KifuNode(emptyBoard, emptyHand, emptyHand, Player.GOTE, "▲2五歩", n2)
    root.children.add(n1); n1.children.add(n2); n2.children.add(b3); n2.children.add(n3); n3.children.add(n4)
    root.evalScore = 0; n1.evalScore = 30; n2.evalScore = -50; n3.evalScore = 1200; n4.evalScore = 30000; b3.evalScore = -800
    val path = listOf(root, n1, n2, n3, n4)
    ShogiGUITheme {
        KifuTreeSection(currentNode = n3, currentPath = path) {}
    }
}

@Composable
fun Modifier.repeatingClickable(enabled: Boolean = true, initialDelay: Long = 500L, delay: Long = 100L, onClick: () -> Unit): Modifier {
    val currentOnClick by rememberUpdatedState(onClick); val scope = rememberCoroutineScope()
    return if (!enabled) this else this.pointerInput(enabled) {
        detectTapGestures(onPress = { val job = scope.launch { delay(initialDelay); while (true) { currentOnClick(); delay(delay) } }; tryAwaitRelease(); job.cancel() }, onTap = { currentOnClick() })
    }
}
