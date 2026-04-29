package com.example.shogigui

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

class UsiEngine(private val dummyPath: String = "") {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    var onOutputReceived: ((String) -> Unit)? = null

    companion object {
        init {
            System.loadLibrary("yaneuraou")
        }
    }

    // ネイティブ関数の宣言
    private external fun nativeStart()
    private external fun nativeSendCommand(command: String)
    private external fun nativeStop()
    private external fun nativeSetWorkDir(path: String)

    fun start(workDir: String = "") {
        // 完全に新しいスレッドで起動し、UIスレッドを絶対に邪魔しない
        Thread({
            try {
                if (workDir.isNotEmpty()) {
                    nativeSetWorkDir(workDir)
                }
                nativeStart()
            } catch (e: Exception) {
                mainHandler.post {
                    onOutputReceived?.invoke("Error: " + e.message)
                }
            }
        }, "USI-Engine-Thread").start()
    }

    fun sendCommand(command: String) {
        // コマンド送信も別スレッドで行う
        Thread({
            try {
                nativeSendCommand(command)
            } catch (e: Exception) {
                // エラー時はログのみ
            }
        }, "USI-Command-Thread").start()
    }

    fun stop() {
        nativeStop()
    }

    // C++側から呼び出されるコールバック関数
    fun onOutput(line: String) {
        // 全ての出力をメインスレッドに送るのではなく、
        // 必要な通知だけをメインスレッドで行う（負荷軽減）
        if (line.startsWith("info") || line == "usiok" || line == "readyok" || line.startsWith("bestmove")) {
            mainHandler.post {
                onOutputReceived?.invoke(line)
            }
        } else {
            // それ以外はバックグラウンドでコールバックを呼ぶ
            onOutputReceived?.invoke(line)
        }
    }
}
