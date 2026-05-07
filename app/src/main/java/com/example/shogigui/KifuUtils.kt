package com.example.shogigui

import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

// USIのinfo行を解析して読みやすい文字列にする
fun parseInfo(
    line: String,
    currentBoard: Map<Pair<Int, Int>, Piece>,
    turn: Player
): String {
    val parts = line.split(Regex("\\s+"))
    var score = ""
    var pv = ""
    var i = 0
    while (i < parts.size) {
        when (parts[i]) {
            "score" -> {
                if (i + 2 < parts.size) {
                    val type = parts[++i]
                    val value = parts[++i]
                    score = when (type) {
                        "cp" -> {
                            val rawV = value.toIntOrNull() ?: 0
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
                    var tempBoard = currentBoard
                    pvMoves.take(6).forEach { moveStr ->
                        val symbol = if (tempTurn == Player.SENTE) "▲" else "△"
                        val formatted = formatUsiMove(moveStr, tempBoard)
                        formattedMoves.add("$symbol$formatted")
                        tempBoard = applyUsiMove(moveStr, tempBoard, tempTurn)
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
    if (score.isNotEmpty()) result.add(score)
    if (pv.isNotEmpty()) result.add(pv)
    return result.joinToString("\n")
}

fun extractScore(pvText: String, turn: Player): Int {
    val mateLine = pvText.lines().find { it.contains("手詰") }
    if (mateLine != null) {
        val isSenteWin = mateLine.contains("先手勝ち")
        return if (turn == Player.SENTE) {
            if (isSenteWin) Int.MAX_VALUE else Int.MIN_VALUE
        } else {
            if (!isSenteWin) Int.MAX_VALUE else Int.MIN_VALUE
        }
    }
    val scoreLine = pvText.lines().find { it.startsWith("評価:") } ?: return 0
    val vStr = scoreLine.substringAfter("評価:").trim().split(" ")[0]
    //return vStr.replace("+", "").toIntOrNull() ?: 0
    val v = vStr.toIntOrNull() ?: 0
    return if (turn == Player.SENTE) v else -v
}

fun formatUsiMove(usiMove: String, board: Map<Pair<Int, Int>, Piece>? = null): String {
    if (usiMove.length < 4) return usiMove
    fun colToNum(c: Char) = c.toString()
    fun rowToKanji(c: Char) = when (c) {
        'a' -> "一"; 'b' -> "二"; 'c' -> "三"; 'd' -> "四"; 'e' -> "五"
        'f' -> "六"; 'g' -> "七"; 'h' -> "八"; 'i' -> "九"
        else -> c.toString()
    }
    return try {
        if (usiMove[1] == '*') {
            val piece = when (usiMove[0]) {
                'P' -> "歩"; 'L' -> "香"; 'N' -> "桂"; 'S' -> "銀"
                'G' -> "金"; 'B' -> "角"; 'R' -> "飛"
                else -> usiMove[0].toString()
            }
            "${colToNum(usiMove[2])}${rowToKanji(usiMove[3])}${piece}打"
        } else {
            val fromCol = usiMove[0] - '0'
            val fromRow = usiMove[1] - 'a'
            val piece = board?.get(Pair(fromRow, 9 - fromCol))
            val pieceLabel = piece?.let { if (it.isPromoted) it.type.promotedLabel ?: it.type.label else it.type.label } ?: ""
            val toCol = colToNum(usiMove[2])
            val toRow = rowToKanji(usiMove[3])
            val prom = if (usiMove.endsWith("+")) "成" else ""
            "${toCol}${toRow}${pieceLabel}${prom}"
        }
    } catch (e: Exception) { usiMove }
}

fun copyAssetsToFileDir(filename: String, subDir: String = "", baseDir: java.io.File, assetManager: android.content.res.AssetManager) {
    val targetDir = if (subDir.isNotEmpty()) {
        val dir = java.io.File(baseDir, subDir)
        if (!dir.exists()) dir.mkdirs()
        dir
    } else baseDir
    val file = java.io.File(targetDir, filename)
    try {
        assetManager.open(filename).use { inputStream ->
            file.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
        }
    } catch (e: Exception) { Log.e("ShogiGUI", "Copy failed: ${e.message}") }
}

fun boardToSfen(board: Map<Pair<Int, Int>, Piece>, turn: Player, senteHand: Map<PieceType, Int>, goteHand: Map<PieceType, Int>): String {
    val sfen = StringBuilder()
    for (row in 0 until 9) {
        var emptyCount = 0
        for (col in 0 until 9) {
            val piece = board[Pair(row, col)]
            if (piece == null) { emptyCount++ } else {
                if (emptyCount > 0) { sfen.append(emptyCount); emptyCount = 0 }
                val char = when (piece.type) {
                    PieceType.KING -> 'k'; PieceType.ROOK -> 'r'; PieceType.BISHOP -> 'b'
                    PieceType.GOLD -> 'g'; PieceType.SILVER -> 's'; PieceType.KNIGHT -> 'n'
                    PieceType.LANCE -> 'l'; PieceType.PAWN -> 'p'
                }
                val sfenChar = if (piece.owner == Player.SENTE) char.uppercaseChar() else char
                if (piece.isPromoted) sfen.append('+')
                sfen.append(sfenChar)
            }
        }
        if (emptyCount > 0) sfen.append(emptyCount)
        if (row < 8) sfen.append('/')
    }
    sfen.append(if (turn == Player.SENTE) " b " else " w ")
    if (senteHand.isEmpty() && goteHand.isEmpty()) { sfen.append("-") } else {
        fun appendHand(hand: Map<PieceType, Int>, isSente: Boolean) {
            val types = listOf(PieceType.ROOK to 'R', PieceType.BISHOP to 'B', PieceType.GOLD to 'G', PieceType.SILVER to 'S', PieceType.KNIGHT to 'N', PieceType.LANCE to 'L', PieceType.PAWN to 'P')
            for ((type, char) in types) {
                val count = hand[type] ?: 0
                if (count > 0) { if (count > 1) sfen.append(count); sfen.append(if (isSente) char else char.lowercaseChar()) }
            }
        }
        appendHand(senteHand, true); appendHand(goteHand, false)
    }
    sfen.append(" 1")
    return sfen.toString()
}

fun createInitialBoard(): Map<Pair<Int, Int>, Piece> {
    val board = mutableMapOf<Pair<Int, Int>, Piece>()
    val firstRowTypes = listOf(PieceType.LANCE, PieceType.KNIGHT, PieceType.SILVER, PieceType.GOLD, PieceType.KING, PieceType.GOLD, PieceType.SILVER, PieceType.KNIGHT, PieceType.LANCE)
    for (col in 0 until 9) {
        board[Pair(8, col)] = Piece(firstRowTypes[col], Player.SENTE)
        board[Pair(0, col)] = Piece(firstRowTypes[col], Player.GOTE)
    }
    board[Pair(7, 7)] = Piece(PieceType.ROOK, Player.SENTE); board[Pair(7, 1)] = Piece(PieceType.BISHOP, Player.SENTE)
    board[Pair(1, 1)] = Piece(PieceType.ROOK, Player.GOTE); board[Pair(1, 7)] = Piece(PieceType.BISHOP, Player.GOTE)
    for (col in 0 until 9) {
        board[Pair(6, col)] = Piece(PieceType.PAWN, Player.SENTE); board[Pair(2, col)] = Piece(PieceType.PAWN, Player.GOTE)
    }
    return board.toMap()
}

fun isValidMovePattern(from: Pair<Int, Int>, to: Pair<Int, Int>, piece: Piece, board: Map<Pair<Int, Int>, Piece>): Boolean {
    val r1 = from.first; val c1 = from.second; val r2 = to.first; val c2 = to.second
    val dr = r2 - r1; val dc = c2 - c1; val adr = Math.abs(dr); val adc = Math.abs(dc)
    val forward = if (piece.owner == Player.SENTE) -1 else 1
    fun isPathClear(): Boolean {
        val stepR = if (dr == 0) 0 else dr / adr; val stepC = if (dc == 0) 0 else dc / adc
        var currR = r1 + stepR; var currC = c1 + stepC
        while (currR != r2 || currC != c2) { if (board[Pair(currR, currC)] != null) return false; currR += stepR; currC += stepC }
        return true
    }
    return if (piece.isPromoted) {
        when (piece.type) {
            PieceType.ROOK -> (dr == 0 || dc == 0) && isPathClear() || (adr <= 1 && adc <= 1)
            PieceType.BISHOP -> (adr == dc) && isPathClear() || (adr <= 1 && adc <= 1)
            else -> (dr == forward && adc <= 1) || (dr == 0 && adc == 1) || (dr == -forward && dc == 0)
        }
    } else {
        when (piece.type) {
            PieceType.PAWN -> dr == forward && dc == 0
            PieceType.LANCE -> dc == 0 && dr * forward > 0 && isPathClear()
            PieceType.KNIGHT -> dr == 2 * forward && adc == 1
            PieceType.SILVER -> (dr == forward && adc <= 1) || (dr == -forward && adc == 1)
            PieceType.GOLD -> (dr == forward && adc <= 1) || (dr == 0 && adc == 1) || (dr == -forward && dc == 0)
            PieceType.KING -> adr <= 1 && adc <= 1
            PieceType.ROOK -> (dr == 0 || dc == 0) && isPathClear()
            PieceType.BISHOP -> (adr == adc) && isPathClear()
        }
    }
}

fun executeMove(from: Pair<Int, Int>?, to: Pair<Int, Int>, piece: Piece, captured: Piece?, promote: Boolean, parentNode: KifuNode, label: String, isPvBranch: Boolean = false, onSaveRequested: (KifuNode) -> Unit, onUpdate: (KifuNode) -> Unit) {
    val existing = parentNode.children.find { it.moveLabel == label }
    if (existing != null) { onUpdate(existing); return }
    val newBoard = parentNode.board.toMutableMap()
    var newSenteHand = parentNode.senteHand; var newGoteHand = parentNode.goteHand
    if (from == null) {
        if (piece.owner == Player.SENTE) {
            newSenteHand = newSenteHand.toMutableMap().apply { this[piece.type] = (this[piece.type] ?: 1) - 1 }.filterValues { it > 0 }
        } else {
            newGoteHand = newGoteHand.toMutableMap().apply { this[piece.type] = (this[piece.type] ?: 1) - 1 }.filterValues { it > 0 }
        }
    }
    if (captured != null && captured.type != PieceType.KING) {
        if (piece.owner == Player.SENTE) { newSenteHand = newSenteHand.toMutableMap().apply { this[captured.type] = (this[captured.type] ?: 0) + 1 } }
        else { newGoteHand = newGoteHand.toMutableMap().apply { this[captured.type] = (this[captured.type] ?: 0) + 1 } }
    }
    if (from != null) newBoard.remove(from)
    newBoard[to] = piece.copy(isPromoted = promote || piece.isPromoted)
    val newNode = KifuNode(newBoard, newSenteHand, newGoteHand, if (parentNode.currentPlayer == Player.SENTE) Player.GOTE else Player.SENTE, label, parentNode, from, to, isPvBranch = isPvBranch)
    parentNode.children.add(newNode)
    var root = newNode; while (root.parent != null) root = root.parent!!
    onSaveRequested(root)
    onUpdate(newNode)
}

fun kifuTreeToJson(node: KifuNode): JSONObject {
    val json = JSONObject()
    json.put("turn", node.currentPlayer.name); json.put("label", node.moveLabel)
    val boardJson = JSONObject()
    node.board.forEach { (pos, piece) ->
        val pJson = JSONObject()
        pJson.put("t", piece.type.name); pJson.put("o", piece.owner.name); pJson.put("p", piece.isPromoted)
        boardJson.put("${pos.first},${pos.second}", pJson)
    }
    json.put("board", boardJson)
    val sHandJson = JSONObject(); node.senteHand.forEach { (t, c) -> sHandJson.put(t.name, c) }; json.put("senteHand", sHandJson)
    val gHandJson = JSONObject(); node.goteHand.forEach { (t, c) -> gHandJson.put(t.name, c) }; json.put("goteHand", gHandJson)
    json.put("lastFrom", if (node.lastFrom != null) "${node.lastFrom.first},${node.lastFrom.second}" else null)
    json.put("lastTo", if (node.lastTo != null) "${node.lastTo.first},${node.lastTo.second}" else null)
    val childrenJson = JSONArray(); node.children.filter { !it.isPvBranch }.forEach { childrenJson.put(kifuTreeToJson(it)) }; json.put("children", childrenJson)
    return json
}

fun jsonToKifuTree(json: JSONObject, parent: KifuNode? = null): KifuNode {
    val board = mutableMapOf<Pair<Int, Int>, Piece>()
    val boardJson = json.getJSONObject("board")
    boardJson.keys().forEach { key ->
        val pos = key.split(",").let { Pair(it[0].toInt(), it[1].toInt()) }
        val pJson = boardJson.getJSONObject(key)
        board[pos] = Piece(PieceType.valueOf(pJson.getString("t")), Player.valueOf(pJson.getString("o")), pJson.getBoolean("p"))
    }
    val senteHand = mutableMapOf<PieceType, Int>()
    val sHandJson = json.getJSONObject("senteHand")
    sHandJson.keys().forEach { senteHand[PieceType.valueOf(it)] = sHandJson.getInt(it) }
    val goteHand = mutableMapOf<PieceType, Int>()
    val gHandJson = json.getJSONObject("goteHand")
    gHandJson.keys().forEach { goteHand[PieceType.valueOf(it)] = gHandJson.getInt(it) }
    val lastFrom = json.optString("lastFrom", null)?.takeIf { it != "null" && it.isNotEmpty() }?.split(",")?.let { Pair(it[0].toInt(), it[1].toInt()) }
    val lastTo = json.optString("lastTo", null)?.takeIf { it != "null" && it.isNotEmpty() }?.split(",")?.let { Pair(it[0].toInt(), it[1].toInt()) }
    val node = KifuNode(board, senteHand, goteHand, Player.valueOf(json.getString("turn")), json.getString("label"), parent, lastFrom, lastTo)
    val childrenJson = json.getJSONArray("children")
    for (i in 0 until childrenJson.length()) { node.children.add(jsonToKifuTree(childrenJson.getJSONObject(i), node)) }
    return node
}

fun parseKif(text: String, root: KifuNode, onSaveRequested: (KifuNode) -> Unit): KifuNode? {
    var tempNode = root; var lastToPos: Pair<Int, Int>? = null
    text.lines().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.startsWith("*") || line.startsWith("#")) return@forEach
        if (listOf("中断", "投了", "持将棋", "千日手", "切れ負け", "詰み", "まで").any { line.contains(it) }) return@forEach
        val lineMatch = Regex("""^\d+\s+(.+?)(?:\s*\(\d+:\d+/[\d:]+\))?\s*$""").find(line) ?: return@forEach
        val movePart = lineMatch.groupValues[1].trim()
        try {
            val toPos = if (movePart.startsWith("同")) lastToPos ?: return@forEach else {
                val col = "１２３４５６７８９".indexOf(movePart[0]); val row = "一二三四五六七八九".indexOf(movePart[1])
                if (col == -1 || row == -1) return@forEach
                Pair(row, 8 - col)
            }
            lastToPos = toPos
            val isDrop = movePart.contains("打")
            val fromMatch = Regex("""\((\d)(\d)\)""").find(movePart)
            val fromPos = if (isDrop) null else fromMatch?.let { Pair(it.groupValues[2].toInt() - 1, 9 - it.groupValues[1].toInt()) } ?: return@forEach
            val piece = if (isDrop) {
                val type = PieceType.entries.find { movePart.contains(it.label) } ?: return@forEach
                Piece(type, tempNode.currentPlayer)
            } else {
                tempNode.board[fromPos!!] ?: return@forEach
            }
            val promote = !piece.isPromoted && piece.type.promotedLabel != null && movePart.contains("成") && !movePart.contains("成${piece.type.label}")
            val label = if (isDrop) {
                "${9 - toPos.second}${when (toPos.first) {
                    0 -> "一"; 1 -> "二"; 2 -> "三"; 3 -> "四"; 4 -> "五"; 5 -> "六"; 6 -> "七"; 7 -> "八"; 8 -> "九"
                    else -> ""
                }}${piece.type.label}打"
            } else {
                val fp = fromPos ?: return@forEach  // ここで non-null にする
                formatUsiMove(
                    "${9 - fp.second}${('a' + fp.first)}${9 - toPos.second}${('a' + toPos.first)}${if (promote) "+" else ""}",
                    tempNode.board
                )
            }

            executeMove(fromPos, toPos, piece, tempNode.board[toPos], promote, tempNode, label, false, onSaveRequested) { tempNode = it }
        } catch (e: Exception) { Log.e("parseKif", "Error: ${e.message}, line: $line") }
    }
    return if (tempNode != root) tempNode else null
}

fun parseCsa(text: String, root: KifuNode, onSaveRequested: (KifuNode) -> Unit): KifuNode? {
    var tempNode = root; val moveRegex = Regex("^[+-](\\d{2})(\\d{2})([A-Z]{2})")
    text.lines().forEach { line ->
        moveRegex.find(line.trim())?.let { match ->
            try {
                val fromStr = match.groupValues[1]; val toStr = match.groupValues[2]; val pieceStr = match.groupValues[3]
                val fromCol = if (fromStr == "00") null else 9 - (fromStr[0] - '0'); val fromRow = if (fromStr == "00") null else (fromStr[1] - '0') - 1
                val toCol = 9 - (toStr[0] - '0'); val toRow = (toStr[1] - '0') - 1
                val (type, isPromoted) = when (pieceStr) {
                    "FU" -> PieceType.PAWN to false; "KY" -> PieceType.LANCE to false; "KE" -> PieceType.KNIGHT to false; "GI" -> PieceType.SILVER to false; "KI" -> PieceType.GOLD to false; "KA" -> PieceType.BISHOP to false; "HI" -> PieceType.ROOK to false; "OU" -> PieceType.KING to false; "TO" -> PieceType.PAWN to true; "NY" -> PieceType.LANCE to true; "NK" -> PieceType.KNIGHT to true; "NG" -> PieceType.SILVER to true; "UM" -> PieceType.BISHOP to true; "RY" -> PieceType.ROOK to true; else -> return@let
                }
                val fromPos = if (fromCol != null && fromRow != null) Pair(fromRow, fromCol) else null
                val movingPiece = if (fromPos == null) Piece(type, tempNode.currentPlayer) else tempNode.board[fromPos] ?: return@let
                val label = if (fromPos == null) "${toStr[0]}${when(toRow){0->"一";1->"二";2->"三";3->"四";4->"五";5->"六";6->"七";7->"八";8->"九";else->""}}${type.label}打" else formatUsiMove("${fromStr[0]}${('a' + fromRow!!)}${toStr[0]}${('a' + toRow)}${if (isPromoted && !movingPiece.isPromoted) "+" else ""}", tempNode.board)
                executeMove(fromPos, Pair(toRow, toCol), movingPiece, tempNode.board[Pair(toRow, toCol)], isPromoted && !movingPiece.isPromoted, tempNode, label, false, onSaveRequested) { tempNode = it }
            } catch (e: Exception) {}
        }
    }
    return if (tempNode != root) tempNode else null
}

fun parseKifu(text: String, root: KifuNode, onSaveRequested: (KifuNode) -> Unit): KifuNode? {
    val moves = (if (text.contains("moves")) text.substringAfter("moves").trim() else text.trim()).split(Regex("\\s+")).filter { it.length >= 4 }
    var tempNode = root
    moves.forEach { moveStr ->
        try {
            if (moveStr[1] == '*') {
                val type = when (moveStr[0]) { 'P' -> PieceType.PAWN; 'L' -> PieceType.LANCE; 'N' -> PieceType.KNIGHT; 'S' -> PieceType.SILVER; 'G' -> PieceType.GOLD; 'B' -> PieceType.BISHOP; 'R' -> PieceType.ROOK; else -> return@forEach }
                val toPos = Pair(moveStr[3] - 'a', 9 - (moveStr[2] - '0'))
                executeMove(null, toPos, Piece(type, tempNode.currentPlayer), null, false, tempNode, "${moveStr[2]}${when(toPos.first){0->"一";1->"二";2->"三";3->"四";4->"五";5->"六";6->"七";7->"八";8->"九";else->""}}${type.label}打", false, onSaveRequested) { tempNode = it }
            } else {
                val fromPos = Pair(moveStr[1] - 'a', 9 - (moveStr[0] - '0')); val toPos = Pair(moveStr[3] - 'a', 9 - (moveStr[2] - '0'))
                val piece = tempNode.board[fromPos] ?: return@forEach
                executeMove(fromPos, toPos, piece, tempNode.board[toPos], moveStr.endsWith("+"), tempNode, formatUsiMove(moveStr, tempNode.board), false, onSaveRequested) { tempNode = it }
            }
        } catch (e: Exception) {}
    }
    return if (tempNode != root) tempNode else null
}

fun applyUsiMove(usiMove: String, board: Map<Pair<Int, Int>, Piece>, player: Player = Player.SENTE): Map<Pair<Int, Int>, Piece> {
    if (usiMove.length < 4) return board
    val newBoard = board.toMutableMap()
    return try {
        if (usiMove[1] == '*') {
            val type = when (usiMove[0]) {
                'P' -> PieceType.PAWN; 'L' -> PieceType.LANCE; 'N' -> PieceType.KNIGHT
                'S' -> PieceType.SILVER; 'G' -> PieceType.GOLD
                'B' -> PieceType.BISHOP; 'R' -> PieceType.ROOK; else -> return board
            }
            newBoard[Pair(usiMove[3] - 'a', 9 - (usiMove[2] - '0'))] = Piece(type, player)
            newBoard
        } else {
            val fromPos = Pair(usiMove[1] - 'a', 9 - (usiMove[0] - '0'))
            val toPos = Pair(usiMove[3] - 'a', 9 - (usiMove[2] - '0'))
            val piece = newBoard[fromPos] ?: return board
            newBoard.remove(fromPos)
            newBoard[toPos] = piece.copy(isPromoted = piece.isPromoted || usiMove.endsWith("+"))
            newBoard
        }
    } catch (e: Exception) { board }
}

fun rowToKanji(c: Char) = when (c) {
    'a' -> "一"; 'b' -> "二"; 'c' -> "三"; 'd' -> "四"; 'e' -> "五"
    'f' -> "六"; 'g' -> "七"; 'h' -> "八"; 'i' -> "九"
    else -> c.toString()
}
