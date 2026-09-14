package com.tksoft.shogigui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

@Composable
fun SliderControlSection(
    currentNode: KifuNode,
    currentPath: List<KifuNode>,
    evalHistory: Map<Int, Int> = emptyMap(),
    onNodeChange: (KifuNode) -> Unit
) {
    val currentIndex = currentPath.indexOf(currentNode).coerceAtLeast(0)
    val maxIndex = (currentPath.size - 1).coerceAtLeast(0)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
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
                    contentDescription = "前へ",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)) }

            val pvColor1 = MaterialTheme.colorScheme.primary
            val pvColor2 = MaterialTheme.colorScheme.secondary
            val pvColor3 = MaterialTheme.colorScheme.tertiary
            val pvColorElse = MaterialTheme.colorScheme.outline
            val activeTrackColor = MaterialTheme.colorScheme.primary
            val inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    val width = size.width; val height = size.height; val centerY = height / 2f
                    // 実際の Slider (M3 1.4.0) の thumb 配置ロジックに合わせる。
                    // Slider.kt の SliderLayout 内では、
                    //   thumbOffsetX = if (steps>0 && 先頭/末尾の目盛りでない)
                    //       (trackWidth - 2*cornerSize) * fraction + cornerSize
                    //     else
                    //       trackWidth * fraction
                    // という「先頭・末尾だけ特別扱い」の式で thumb 位置を決めている
                    // (cornerSize はトラックの角丸半径 = TrackHeight(16.dp)/2 = 8.dp)。
                    // 単純な線形補間 (旧実装) だと、先頭/末尾とその隣の目盛りとの間隔だけ
                    // 広くなってしまい、実際の Slider の目盛り位置とずれる。
                    val thumbWidthPx = 4.dp.toPx() // SliderTokens.HandleWidth
                    val thumbHalfPx = thumbWidthPx / 2f
                    val trackWidthPx = (width - thumbWidthPx).coerceAtLeast(0f)
                    val trackCornerPx = 8.dp.toPx() // SliderTokens.InactiveTrackHeight(16.dp) / 2
                    val valueRangeEnd = maxIndex.toFloat().coerceAtLeast(1f)
                    fun xForIndex(idx: Int): Float {
                        val fraction = (idx / valueRangeEnd).coerceIn(0f, 1f)
                        val thumbOffsetX = if (idx != 0 && idx != maxIndex) {
                            (trackWidthPx - 2f * trackCornerPx) * fraction + trackCornerPx
                        } else {
                            trackWidthPx * fraction
                        }
                        return thumbOffsetX + thumbHalfPx
                    }
                    // バーの太さは見た目上のもので、位置計算には使わない
                    val stroke = (width / (maxIndex + 1).toFloat().coerceAtLeast(1f)).coerceAtLeast(2f)

                    // 評価値バー
                    evalHistory.forEach { (moveCount, score) ->
                        if (moveCount <= maxIndex) {
                            val x = xForIndex(moveCount)
                            val isMate = score > 10000 || score < -10000
                            val normalized = (score.toFloat() / 2000f).coerceIn(-1f, 1f)
                            val y = centerY - (normalized * centerY)
                            val barColor = when {
                                isMate && score > 0 -> senteMateColor
                                isMate && score < 0 -> goteMateColor
                                score >= 0 -> senteBarColor
                                else -> goteBarColor
                            }
                            drawLine(color = barColor,
                                start = androidx.compose.ui.geometry.Offset(x, centerY),
                                end = androidx.compose.ui.geometry.Offset(x, y),
                                strokeWidth = stroke)
                        }
                    }

                    // PV分岐ドット
                    currentPath.forEachIndexed { index, node ->
                        if (node.isPvBranch && index <= maxIndex) {
                            val x = xForIndex(index)
                            val dotColor = when (node.pvColorIndex) {
                                1 -> pvColor1; 2 -> pvColor2; 3 -> pvColor3; else -> pvColorElse
                            }
                            drawCircle(dotColor.copy(alpha = 0.8f), radius = 5f,
                                center = androidx.compose.ui.geometry.Offset(x, centerY))
                        }
                    }
                }
                Slider(
                    value = currentIndex.toFloat(),
                    onValueChange = { v -> onNodeChange(currentPath[v.toInt().coerceIn(0, maxIndex)]) },
                    valueRange = 0f..maxIndex.toFloat().coerceAtLeast(1f),
                    steps = (maxIndex - 1).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                        activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.07f),
                        inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.07f),
                        thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        ))
            }

            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer)
                    .repeatingClickable(enabled = currentIndex < maxIndex) { if (currentIndex < maxIndex) onNodeChange(currentPath[currentIndex + 1]) },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "次へ", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp)) }
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
fun SliderControlSectionPreview() {
    val emptyBoard = emptyMap<Pair<Int, Int>, Piece>()
    val emptyHand = emptyMap<PieceType, Int>()
    val root = KifuNode(emptyBoard, emptyHand, emptyHand, Player.SENTE, "開始局面")
    val n1 = KifuNode(emptyBoard, emptyHand, emptyHand, Player.GOTE, "▲7六歩", root)
    val n2 = KifuNode(emptyBoard, emptyHand, emptyHand, Player.SENTE, "△3四歩", n1)
    val n3 = KifuNode(emptyBoard, emptyHand, emptyHand, Player.GOTE, "▲2六歩", n2, isPvBranch = true, pvColorIndex = 1)
    val n4 = KifuNode(emptyBoard, emptyHand, emptyHand, Player.SENTE, "△8四歩", n3, isPvBranch = true, pvColorIndex = 2)
    val path = listOf(root, n1, n2, n3, n4)
    val evalHistory = mapOf(0 to 0, 1 to 30, 2 to -50, 3 to 120, 4 to -200)
    ShogiGUITheme {
        SliderControlSection(currentNode = n2, currentPath = path, evalHistory = evalHistory) {}
    }
}

@Composable
fun Modifier.repeatingClickable(enabled: Boolean = true, initialDelay: Long = 500L, delay: Long = 100L, onClick: () -> Unit): Modifier {
    val currentOnClick by rememberUpdatedState(onClick); val scope = rememberCoroutineScope()
    return if (!enabled) this else this.pointerInput(enabled) {
        detectTapGestures(onPress = { val job = scope.launch { delay(initialDelay); while (true) { currentOnClick(); delay(delay) } }; tryAwaitRelease(); job.cancel() }, onTap = { currentOnClick() })
    }
}
