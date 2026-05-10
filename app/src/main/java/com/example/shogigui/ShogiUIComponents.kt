package com.example.shogigui

import android.R.attr.tag
import android.util.Log
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun PvInfoCard(rank: Int, pvText: String, onTap: () -> Unit) {
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
        PlayerInfoContent(name = playerName, mark = mark, isFlipped = isFlipped)
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
            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 10.dp)) {
                    val width = size.width; val height = size.height; val centerY = height / 2f
                    val stepX = width / maxIndex.toFloat().coerceAtLeast(1f)
                    drawLine(color = Color.Gray.copy(alpha = 0.2f),
                        start = androidx.compose.ui.geometry.Offset(0f, centerY),
                        end = androidx.compose.ui.geometry.Offset(width, centerY),
                        strokeWidth = 1f)
                    evalHistory.forEach { (moveCount, score) ->
                        if (moveCount <= maxIndex) {
                            val x = moveCount * stepX; val normalized = (score.toFloat() / 2000f).coerceIn(-1f, 1f)
                            val y = centerY - (normalized * centerY)
                            Log.d("evalHistory_debug", "手数=$moveCount score=$score y=$y")
                            drawLine(color = if (score >= 0)
                                Color.Red.copy(alpha = 0.4f)
                            else Color.Blue.copy(alpha = 0.4f),
                                start = androidx.compose.ui.geometry.Offset(x, centerY),
                                end = androidx.compose.ui.geometry.Offset(x, y),
                                strokeWidth = stepX.coerceAtLeast(2f))
                            //分岐で色を変えたいけど，うまくいかない
//                        }
//                    }
//                    currentPath.forEachIndexed { index, node ->
//                        if (node.isPvBranch && index <= maxIndex) {
//                            val x = index.toFloat() * stepX
//                            val dotColor = when (node.pvColorIndex) {
//                                1 -> pvColor1; 2 -> pvColor2; 3 -> pvColor3; else -> pvColorElse
//                            }
//                            drawCircle(color = dotColor.copy(alpha = 0.7f), radius = 4f,
//                                center = androidx.compose.ui.geometry.Offset(x, centerY))
                        }
                    }
                }
                Slider(
                    value = currentIndex.toFloat(),
                    onValueChange = { v -> onNodeChange(currentPath[v.toInt().coerceIn(0, maxIndex)]) },
                    valueRange = 0f..maxIndex.toFloat().coerceAtLeast(1f),
                    steps = (maxIndex - 1).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth().alpha(0.45f))
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
fun PlayerInfoContent(name: String, mark: String, isFlipped: Boolean = false) {
    val defaultFontSize = MaterialTheme.typography.titleMedium.fontSize
    var fontSize by remember(name) { mutableStateOf(defaultFontSize) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if(((mark == "▲") && !isFlipped) || ((mark == "△") && isFlipped)) Arrangement.End else Arrangement.Start
    ) {
        Text(text = "$mark ", style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Bold, maxLines = 1)
        Text(text = name, style = MaterialTheme.typography.titleMedium.copy(fontSize = fontSize), maxLines = 1, softWrap = false, onTextLayout = { if (it.hasVisualOverflow && fontSize > 8.sp) fontSize *= 0.9f })
    }
}

@Composable
fun Modifier.repeatingClickable(enabled: Boolean = true, initialDelay: Long = 500L, delay: Long = 100L, onClick: () -> Unit): Modifier {
    val currentOnClick by rememberUpdatedState(onClick); val scope = rememberCoroutineScope()
    return if (!enabled) this else this.pointerInput(enabled) {
        detectTapGestures(onPress = { val job = scope.launch { delay(initialDelay); while (true) { currentOnClick(); delay(delay) } }; tryAwaitRelease(); job.cancel() }, onTap = { currentOnClick() })
    }
}
