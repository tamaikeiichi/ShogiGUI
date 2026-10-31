package com.tksoft.shogigui

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * エンジンを別プロセスの実行ファイルとして起動し、USI を標準入出力でやり取りする。
 * エンジンを切り替えるときはプロセスごと終わらせるので、評価関数などのメモリが確実に解放される。
 *
 * @param id エンジンの ID (ENGINE_CHOICES の値)
 * @param executable nativeLibraryDir に展開された実行ファイル (lib<名前>.so)
 */
class ProcessUsiEngine(val id: String, private val executable: File) : UsiEngineInterface {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile override var onOutputReceived: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "EngineProcess"

        // 動作中のインスタンス。Activity が作り直されたときなどに前のプロセスを残さないよう、新しく起動する前に止める
        @Volatile private var activeInstance: ProcessUsiEngine? = null

        /**
         * エンジンのプロセスが思いがけず終わったときに onOutputReceived に渡す行。
         * "info" で始めると、探索のやり直し中に読み捨てられてしまうので使わない
         */
        const val PROCESS_EXITED = "engine process exited"
    }

    @Volatile private var process: Process? = null
    @Volatile private var stopped = false
    private val commandQueue = LinkedBlockingQueue<String>()
    // stop() で入れてコマンド送信スレッドを終わらせる印（参照で比べるので、他のコマンド文字列とは一致しない）
    private val quitCommand = String(charArrayOf())

    override fun start(workDir: String) {
        val prev = activeInstance
        if (prev != null && prev !== this) {
            // stop() は終了を待つので、呼び出し元 (メインスレッド) を止めないよう別スレッドで止める
            prev.onOutputReceived = null
            Thread { prev.stop() }.start()
        }
        activeInstance = this

        val p = try {
            ProcessBuilder(executable.absolutePath)
                .directory(File(workDir))
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            Log.e(TAG, "failed to start $executable", e)
            mainHandler.post { onOutputReceived?.invoke("Error: " + e.message) }
            return
        }
        process = p
        Log.d(TAG, "started $id exe=$executable")

        Thread({
            val writer = p.outputStream.bufferedWriter()
            try {
                while (true) {
                    val command = commandQueue.take()
                    if (command === quitCommand) break
                    Log.d(TAG, "sendCommand: $command")
                    writer.write(command)
                    writer.write("\n")
                    writer.flush()
                }
            } catch (e: Exception) {
                Log.d(TAG, "command thread ended: ${e.message}")
            }
        }, "USI-Command-$id").also { it.isDaemon = true }.start()

        Thread({
            try {
                p.inputStream.bufferedReader().forEachLine { line ->
                    mainHandler.post { onOutputReceived?.invoke(line) }
                }
            } catch (e: Exception) {
                Log.d(TAG, "output thread ended: ${e.message}")
            }
            val code = runCatching { p.waitFor() }.getOrNull()
            Log.d(TAG, "$id process exited code=$code stopped=$stopped")
            if (!stopped) mainHandler.post { onOutputReceived?.invoke(PROCESS_EXITED) }
        }, "USI-Output-$id").also { it.isDaemon = true }.start()

        // JNI 版と同じく、最初の "usi" はここで送る。オプションと "isready" は "usiok" を受けたときに送る
        sendCommand("usi")
    }

    override fun sendCommand(command: String) {
        commandQueue.put(command)
    }

    /** "quit" で終わらせ、終わらなければ強制終了する。待つことがあるのでメインスレッドでは呼ばないこと */
    override fun stop() {
        if (stopped) return
        stopped = true
        if (activeInstance === this) activeInstance = null
        commandQueue.put("quit")
        commandQueue.put(quitCommand)
        val p = process ?: return
        try {
            if (!p.waitFor(1, TimeUnit.SECONDS)) {
                p.destroy()
                if (!p.waitFor(1, TimeUnit.SECONDS)) p.destroyForcibly()
            }
        } catch (_: InterruptedException) {
            p.destroyForcibly()
        }
        Log.d(TAG, "$id stopped")
    }
}
