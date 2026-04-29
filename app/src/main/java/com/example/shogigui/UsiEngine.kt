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
        // エンジン本体は専用の独立したスレッドで動かす
        Thread {
            try {
                if (workDir.isNotEmpty()) {
                    nativeSetWorkDir(workDir)
                }
                nativeStart()
            } catch (e: Exception) {
                onOutput("Error: " + e.message)
            }
        }.start()
    }

    fun sendCommand(command: String) {
        // コマンド送信は即座に行う（エンジンがループしていても割り込めるようにする）
        nativeSendCommand(command)
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
