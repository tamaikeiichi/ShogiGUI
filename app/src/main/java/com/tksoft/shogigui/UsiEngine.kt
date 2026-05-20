package com.tksoft.shogigui

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
        executor.execute {
            try {
                if (workDir.isNotEmpty()) nativeSetWorkDir(workDir)
                //nativeSendCommand("usi")
                // 必要なコマンドを全部先に積んでおく
                nativeSendCommand("usi")
                nativeSendCommand("setoption name Threads value 4")
                nativeSendCommand("setoption name USI_Hash value 256")
                nativeSendCommand("setoption name BookFile value no_book")
                nativeSendCommand("isready")
                nativeStart()
            } catch (e: Exception) {
                mainHandler.post { onOutputReceived?.invoke("Error: " + e.message) }
            }
        }//, "USI-Engine-Thread").start()
    }

    private val commandQueue = java.util.concurrent.LinkedBlockingQueue<String>()

    init {
        executor.execute {
            while (true) {
                val command = commandQueue.take()
                try {
                    android.util.Log.d("ShogiJNI", "sendCommand: $command")
                    nativeSendCommand(command)
                } catch (e: Exception) {}
            }
        }
    }

    fun sendCommand(command: String) {
        commandQueue.put(command)
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
