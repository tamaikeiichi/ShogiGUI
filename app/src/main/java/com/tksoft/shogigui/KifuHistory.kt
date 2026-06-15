package com.tksoft.shogigui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class KifuHistoryEntry(
    val id: String,
    val senteName: String,
    val goteName: String,
    val gameResult: String,
    val savedAt: Long,
    val moveCount: Int,
    val gameDate: String? = null,
    val displayDate: String = ""
)

object KifuHistoryManager {
    private const val MAX_ENTRIES = 1000
    private const val HISTORY_DIR = "kifu_history"
    private const val INDEX_FILE = "index.json"

    private val dateFmt get() = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN)
    private val dateFmtWithSec get() = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN)
    private val dateFmtShort get() = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN)

    private fun historyDir(context: Context): File =
        File(context.filesDir, HISTORY_DIR).also { if (!it.exists()) it.mkdirs() }

    private fun indexFile(context: Context) = File(historyDir(context), INDEX_FILE)
    private fun kifuFile(context: Context, id: String) = File(historyDir(context), "$id.json")

    private fun computeDisplayDate(gameDate: String?, savedAt: Long): String =
        if (gameDate != null) formatGameDate(gameDate) else formatDate(savedAt)

    fun loadIndex(context: Context): List<KifuHistoryEntry> {
        val file = indexFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val gameDate = obj.optString("gameDate").takeIf { it.isNotBlank() && it != "null" }
                val savedAt = obj.getLong("savedAt")
                KifuHistoryEntry(
                    id = obj.getString("id"),
                    senteName = obj.optString("senteName", "先手"),
                    goteName = obj.optString("goteName", "後手"),
                    gameResult = obj.optString("gameResult", ""),
                    savedAt = savedAt,
                    moveCount = obj.optInt("moveCount", 0),
                    gameDate = gameDate,
                    displayDate = computeDisplayDate(gameDate, savedAt)
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun saveIndex(context: Context, entries: List<KifuHistoryEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("senteName", e.senteName)
                put("goteName", e.goteName)
                put("gameResult", e.gameResult)
                put("savedAt", e.savedAt)
                put("moveCount", e.moveCount)
                e.gameDate?.let { put("gameDate", it) }
            })
        }
        indexFile(context).writeText(arr.toString())
    }

    fun saveKifu(
        context: Context,
        rootNode: KifuNode,
        senteName: String,
        goteName: String,
        gameResult: String,
        gameDate: String? = null
    ) {
        val now = System.currentTimeMillis()
        val id = now.toString()
        kifuFile(context, id).writeText(kifuTreeToJson(rootNode).toString())

        val entries = loadIndex(context).toMutableList()
        entries.add(0, KifuHistoryEntry(
            id = id, senteName = senteName, goteName = goteName,
            gameResult = gameResult, savedAt = now,
            moveCount = countMainLineMoves(rootNode),
            gameDate = gameDate,
            displayDate = computeDisplayDate(gameDate, now)
        ))

        if (entries.size > MAX_ENTRIES) {
            entries.subList(MAX_ENTRIES, entries.size).forEach { kifuFile(context, it.id).delete() }
            while (entries.size > MAX_ENTRIES) entries.removeLast()
        }
        saveIndex(context, entries)
    }

    fun bumpToTop(context: Context, id: String) {
        val entries = loadIndex(context).toMutableList()
        val idx = entries.indexOfFirst { it.id == id }
        if (idx <= 0) return
        entries.add(0, entries.removeAt(idx))
        saveIndex(context, entries)
    }

    fun loadKifu(context: Context, id: String): KifuNode? {
        return try {
            val file = kifuFile(context, id)
            if (!file.exists()) null else jsonToKifuTree(JSONObject(file.readText()))
        } catch (e: Exception) { null }
    }

    private fun countMainLineMoves(root: KifuNode): Int {
        var count = 0; var node = root
        while (true) { node = node.children.firstOrNull { !it.isPvBranch } ?: break; count++ }
        return count
    }

    fun formatDate(timestamp: Long): String = dateFmt.format(Date(timestamp))

    fun formatGameDate(raw: String): String =
        try { dateFmtShort.format(dateFmtWithSec.parse(raw)!!) }
        catch (_: Exception) {
            try { dateFmtShort.format(dateFmt.parse(raw)!!) }
            catch (_: Exception) { raw.take(10) }
        }
}
