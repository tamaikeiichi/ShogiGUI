package com.tksoft.shogigui

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

    private val kifuFileNameRegex = Regex("^\\d+\\.json$")

    private fun historyDir(context: Context): File =
        File(context.filesDir, HISTORY_DIR).also { if (!it.exists()) it.mkdirs() }

    private fun indexFile(context: Context) = File(historyDir(context), INDEX_FILE)
    private fun kifuFile(context: Context, id: String) = File(historyDir(context), "$id.json")

    private fun computeDisplayDate(gameDate: String?, savedAt: Long): String =
        if (gameDate != null) formatGameDate(gameDate) else formatDate(savedAt)

    // 書き込み中のプロセス強制終了（アプリ更新時の再起動等）でファイルが
    // 中途半端な内容になり全履歴が読めなくなるのを避けるため、一時ファイル経由で置き換える
    private fun writeTextAtomic(file: File, content: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(file)) {
            file.writeText(content)
            tmp.delete()
        }
    }

    fun loadIndex(context: Context): List<KifuHistoryEntry> {
        val file = indexFile(context)
        val parsed: List<KifuHistoryEntry> = if (!file.exists()) emptyList() else try {
            val arr = JSONArray(file.readText())
            // 1件でも解析に失敗した場合に全履歴が消えて見えることがないよう、
            // エントリ単位で読み飛ばす（将来のフォーマット変更にも耐性を持たせる）
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val obj = arr.getJSONObject(i)
                    val gameDate = obj.optString("gameDate").takeIf { it.isNotBlank() && it != "null" }
                    val savedAt = obj.optLong("savedAt", 0L)
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
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }

        return reconcileWithOrphanFiles(context, parsed)
    }

    // index.json の破損・欠落や、保存処理の途中終了で index に登録され損ねた
    // 対局ファイル（本体の棋譜データ自体は無事）を見つけて復旧する
    private fun reconcileWithOrphanFiles(context: Context, entries: List<KifuHistoryEntry>): List<KifuHistoryEntry> {
        val dir = historyDir(context)
        val knownIds = entries.map { it.id }.toSet()
        val orphanFiles = dir.listFiles { f -> f.isFile && f.name != INDEX_FILE && kifuFileNameRegex.matches(f.name) }
            ?.filter { it.nameWithoutExtension !in knownIds }
            ?: emptyList()
        if (orphanFiles.isEmpty()) return entries

        val recovered = orphanFiles.mapNotNull { f ->
            try {
                val root = jsonToKifuTree(JSONObject(f.readText()))
                val id = f.nameWithoutExtension
                val savedAt = id.toLongOrNull() ?: f.lastModified()
                KifuHistoryEntry(
                    id = id, senteName = "先手", goteName = "後手", gameResult = "",
                    savedAt = savedAt, moveCount = countMainLineMoves(root),
                    gameDate = null, displayDate = formatDate(savedAt)
                )
            } catch (e: Exception) { null }
        }
        if (recovered.isEmpty()) return entries

        val merged = (entries + recovered).sortedByDescending { it.savedAt }
        saveIndex(context, merged)
        return merged
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
        writeTextAtomic(indexFile(context), arr.toString())
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
        writeTextAtomic(kifuFile(context, id), kifuTreeToJson(rootNode).toString())

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
            while (entries.size > MAX_ENTRIES) entries.removeAt(entries.lastIndex)
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

    // 過去の棋譜すべてを、対局ごとに1つの標準CSAファイルとしてZIPへ書き出す。
    // 分岐・読み筋は保存されず本譜のみとなる（CSAが単一の指し手列しか表現できないため）。
    fun exportAllToUri(context: Context, uri: Uri): Int {
        var count = 0
        context.contentResolver.openOutputStream(uri)?.let { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                loadIndex(context).forEach { entry ->
                    val root = loadKifu(context, entry.id) ?: return@forEach
                    val csa = exportMainLineToCsa(root, entry.senteName, entry.goteName, entry.gameResult, entry.gameDate)
                    zip.putNextEntry(ZipEntry("${entry.id}.csa"))
                    zip.write(csa.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    count++
                }
            }
        }
        return count
    }

    data class KifuImportResult(val imported: Int, val skipped: Int)

    // exportAllToUri で書き出したZIP（対局ごとのCSAファイル群）を読み込み、
    // 過去の棋譜として追加する。1ファイルごとに独立して解析するため、
    // 一部が壊れていても他の対局は読み込める。標準CSAファイルなので、
    // 他ソフトで作成されたCSAをまとめたZIPも同様に取り込める。
    fun importFromUri(context: Context, uri: Uri): KifuImportResult {
        val entries = loadIndex(context).toMutableList()
        val knownIds = entries.map { it.id }.toMutableSet()
        var imported = 0
        var skipped = 0
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var zipEntry: ZipEntry? = zip.nextEntry
                while (zipEntry != null) {
                    val name = zipEntry.name
                    if (zipEntry.isDirectory || !name.endsWith(".csa", ignoreCase = true)) {
                        zip.closeEntry(); zipEntry = zip.nextEntry; continue
                    }
                    val bytes = zip.readBytes()
                    try {
                        val text = decodeCsaBytes(bytes)
                        val freshRoot = KifuNode(createInitialBoard(), emptyMap(), emptyMap(), Player.SENTE)
                        val tree = parseCsa(text, freshRoot, {}) ?: throw IllegalArgumentException("no moves")

                        val names = extractPlayerNames(text)
                        val gameResult = extractGameResult(text) ?: ""
                        val gameDate = extractGameDate(text)

                        var id = File(name).nameWithoutExtension.takeIf { it.toLongOrNull() != null }
                            ?: "${System.currentTimeMillis()}_${(0..999999).random()}"
                        if (id in knownIds) id = "${System.currentTimeMillis()}_${(0..999999).random()}"
                        knownIds.add(id)

                        val savedAt = id.toLongOrNull() ?: System.currentTimeMillis()
                        writeTextAtomic(kifuFile(context, id), kifuTreeToJson(freshRoot).toString())
                        entries.add(KifuHistoryEntry(
                            id = id,
                            senteName = names.sente ?: "先手",
                            goteName = names.gote ?: "後手",
                            gameResult = gameResult,
                            savedAt = savedAt,
                            moveCount = countMainLineMoves(freshRoot),
                            gameDate = gameDate,
                            displayDate = computeDisplayDate(gameDate, savedAt)
                        ))
                        imported++
                    } catch (e: Exception) { skipped++ }
                    zip.closeEntry()
                    zipEntry = zip.nextEntry
                }
            }
        }
        if (imported == 0) return KifuImportResult(0, skipped)

        val merged = entries.sortedByDescending { it.savedAt }.toMutableList()
        if (merged.size > MAX_ENTRIES) {
            merged.subList(MAX_ENTRIES, merged.size).forEach { kifuFile(context, it.id).delete() }
            while (merged.size > MAX_ENTRIES) merged.removeAt(merged.lastIndex)
        }
        saveIndex(context, merged)
        return KifuImportResult(imported, skipped)
    }

    // 標準CSAはShift_JISで配布されることも多いため、UTF-8として文字化けする場合は
    // Shift_JISとして読み直す（自アプリの書き出しは常にUTF-8）。
    private fun decodeCsaBytes(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        if (!utf8.contains('�')) return utf8
        return try { String(bytes, charset("Shift_JIS")) } catch (e: Exception) { utf8 }
    }

    fun formatDate(timestamp: Long): String = dateFmt.format(Date(timestamp))

    fun formatGameDate(raw: String): String =
        try { dateFmtShort.format(dateFmtWithSec.parse(raw)!!) }
        catch (_: Exception) {
            try { dateFmtShort.format(dateFmt.parse(raw)!!) }
            catch (_: Exception) { raw.take(10) }
        }
}
