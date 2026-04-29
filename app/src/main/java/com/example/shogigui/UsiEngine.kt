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
        mainHandler.post {
            onOutputReceived?.invoke(line)
        }
    }
}
