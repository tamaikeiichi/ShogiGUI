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
    val displayDate: String = "",
    // 削除した時刻。null でなければゴミ箱に入っていて、TRASH_RETENTION_MS 後に完全に削除される
    val deletedAt: Long? = null
)

object KifuHistoryManager {
    private const val MAX_ENTRIES = 1000
    private const val HISTORY_DIR = "kifu_history"
    private const val INDEX_FILE = "index.json"
    const val TRASH_RETENTION_MS = 24L * 60 * 60 * 1000

    // index.json の読み書きは UI からの操作と自動保存が別スレッドで重なるので、まとめて排他する
    private val lock = Any()
    // 指し手のたびの自動保存はメインスレッドを止めないよう、順番を保って裏で書く
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()

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

    fun loadIndex(context: Context): List<KifuHistoryEntry> = synchronized(lock) {
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
                        displayDate = computeDisplayDate(gameDate, savedAt),
                        deletedAt = obj.optLong("deletedAt", 0L).takeIf { it > 0L }
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }

        val reconciled = reconcileWithOrphanFiles(context, parsed)
        val deduped = dedupeById(reconciled)
        // ゴミ箱に入れてから一定時間たったものは完全に削除する
        val now = System.currentTimeMillis()
        val (expired, kept) = deduped.partition { it.deletedAt != null && now - it.deletedAt >= TRASH_RETENTION_MS }
        expired.forEach { kifuFile(context, it.id).delete() }
        if (kept.size != reconciled.size) saveIndex(context, kept)
        return kept
    }

    // 過去に存在した保存処理の不具合により、同一idのエントリが
    // 「正しい対局者名」版と「先手／後手（既定名）」版の二重で
    // index.json に残っているケースを解消する。実名を持つ方を優先して残す。
    private fun dedupeById(entries: List<KifuHistoryEntry>): List<KifuHistoryEntry> {
        val seen = LinkedHashMap<String, KifuHistoryEntry>()
        for (e in entries) {
            val existing = seen[e.id]
            if (existing == null || (isDefaultNamed(existing) && !isDefaultNamed(e))) {
                seen[e.id] = e
            }
        }
        return seen.values.toList()
    }

    private fun isDefaultNamed(e: KifuHistoryEntry) = e.senteName == "先手" && e.goteName == "後手"

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
                e.deletedAt?.let { put("deletedAt", it) }
            })
        }
        writeTextAtomic(indexFile(context), arr.toString())
    }

    /** 新しく履歴に登録する棋譜の id。保存日時を兼ねる */
    fun newId(): String = System.currentTimeMillis().toString()

    /**
     * 棋譜を履歴に保存する。同じ id があれば内容と対局者名などを置き換えて一覧の先頭へ移し、無ければ新しく登録する。
     * 棋譜の木は呼び出し側 (メインスレッド) で書き換わるので、JSON と手数は呼び出し側で作って渡す
     */
    fun saveOrUpdateAsync(
        context: Context,
        id: String,
        treeJson: String,
        senteName: String,
        goteName: String,
        gameResult: String,
        gameDate: String?,
        moveCount: Int
    ) {
        val appContext = context.applicationContext
        writer.execute {
            try {
                saveOrUpdate(appContext, id, treeJson, senteName, goteName, gameResult, gameDate, moveCount)
            } catch (_: Exception) {}
        }
    }

    private fun saveOrUpdate(
        context: Context, id: String, treeJson: String, senteName: String, goteName: String,
        gameResult: String, gameDate: String?, moveCount: Int
    ) = synchronized(lock) {
        // 先に index を読み込んでおく。書き込み後に loadIndex すると、
        // まだ index に登録されていないこのファイルを reconcileWithOrphanFiles が
        // 孤児ファイルとみなし、名前が「先手」「後手」の重複エントリを作ってしまう。
        val entries = loadIndex(context).toMutableList()
        writeTextAtomic(kifuFile(context, id), treeJson)

        val idx = entries.indexOfFirst { it.id == id }
        val existing = if (idx >= 0) entries.removeAt(idx) else null
        val savedAt = existing?.savedAt ?: id.toLongOrNull() ?: System.currentTimeMillis()
        entries.add(0, KifuHistoryEntry(
            id = id, senteName = senteName, goteName = goteName,
            gameResult = gameResult, savedAt = savedAt,
            moveCount = moveCount,
            gameDate = gameDate,
            displayDate = computeDisplayDate(gameDate, savedAt),
            deletedAt = existing?.deletedAt
        ))

        if (entries.size > MAX_ENTRIES) {
            entries.subList(MAX_ENTRIES, entries.size).forEach { kifuFile(context, it.id).delete() }
            while (entries.size > MAX_ENTRIES) entries.removeAt(entries.lastIndex)
        }
        saveIndex(context, entries)
    }

    /** ゴミ箱に入れる (deleted = true) / ゴミ箱から戻す (false) */
    fun setDeleted(context: Context, id: String, deleted: Boolean): Unit = synchronized(lock) {
        val entries = loadIndex(context).toMutableList()
        val idx = entries.indexOfFirst { it.id == id }
        if (idx < 0) return
        entries[idx] = entries[idx].copy(deletedAt = if (deleted) System.currentTimeMillis() else null)
        saveIndex(context, entries)
    }

    fun bumpToTop(context: Context, id: String): Unit = synchronized(lock) {
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

    /**
     * 初期局面と本譜の指し手が同じ履歴を探す。表示中の棋譜がどの履歴か (history_id) を記録していなかった版から
     * 更新したとき、同じ棋譜を二重に登録しないよう引き継ぐのに使う
     */
    fun findSameMainLine(context: Context, root: KifuNode): KifuHistoryEntry? {
        val target = mainLine(root)
        return loadIndex(context).asSequence()
            .filter { it.deletedAt == null && it.moveCount == target.size - 1 }
            .firstOrNull { e ->
                val other = loadKifu(context, e.id)?.let { mainLine(it) } ?: return@firstOrNull false
                other.first().board == target.first().board &&
                    other.map { it.moveLabel } == target.map { it.moveLabel } &&
                    other.last().board == target.last().board
            }
    }

    private fun mainLine(root: KifuNode): List<KifuNode> {
        val nodes = mutableListOf(root)
        while (true) nodes.add(nodes.last().children.firstOrNull { !it.isPvBranch } ?: break)
        return nodes
    }

    fun countMainLineMoves(root: KifuNode): Int {
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
                loadIndex(context).filter { it.deletedAt == null }.forEach { entry ->
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
    fun importFromUri(context: Context, uri: Uri): KifuImportResult = synchronized(lock) {
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
