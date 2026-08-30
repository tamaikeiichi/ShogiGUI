package com.tksoft.shogigui

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

class AobaEngine : UsiEngineInterface {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile override var onOutputReceived: ((String) -> Unit)? = null

    companion object {
        // プロセス内で動作中のインスタンスを追跡し、重複起動を防ぐ
        @Volatile private var activeInstance: AobaEngine? = null

        init {
            System.loadLibrary("aobannue")
        }
    }

    private external fun nativeStart()
    private external fun nativeSendCommand(command: String)
    private external fun nativeStop()
    private external fun nativeSetWorkDir(path: String)

    override fun start(workDir: String) {
        android.util.Log.d("EngineDebug", "AobaEngine.start() called on $this workDir=$workDir thread=${Thread.currentThread().name}")
        // 別インスタンスが既に動いている場合（Activity再生成など）は先に停止
        val prev = activeInstance
        if (prev != null && prev !== this) {
            android.util.Log.d("EngineDebug", "AobaEngine.start() stopping prev instance $prev before starting $this")
            prev.nativeStop()
            android.util.Log.d("EngineDebug", "AobaEngine.start() prev.nativeStop() returned for $prev")
            activeInstance = null
        }
        activeInstance = this
        executor.execute {
            try {
                android.util.Log.d("EngineDebug", "AobaEngine executor begin for $this thread=${Thread.currentThread().name}")
                if (workDir.isNotEmpty()) nativeSetWorkDir(workDir)
                // "usi" は native-lib-aoba.cpp 内で事前投入済み
                nativeStart()
                android.util.Log.d("EngineDebug", "AobaEngine nativeStart() returned for $this")
            } catch (e: Exception) {
                mainHandler.post { onOutputReceived?.invoke("Error: " + e.message) }
            } finally {
                if (activeInstance === this) activeInstance = null
            }
        }
    }

    private val commandQueue = java.util.concurrent.LinkedBlockingQueue<String>()

    init {
        Thread({
            while (true) {
                val command = commandQueue.take()
                try {
                    android.util.Log.d("ShogiJNI_Aoba", "sendCommand: $command")
                    nativeSendCommand(command)
                } catch (e: Exception) {}
            }
        }, "Aoba-Command-Thread").also { it.isDaemon = true }.start()
    }

    override fun sendCommand(command: String) {
        commandQueue.put(command)
    }

    override fun stop() {
        android.util.Log.d("EngineDebug", "AobaEngine.stop() called on $this thread=${Thread.currentThread().name}")
        nativeStop()
        android.util.Log.d("EngineDebug", "AobaEngine.stop() nativeStop() returned for $this")
        if (activeInstance === this) activeInstance = null
    }

    fun onOutput(line: String) {
        if (line.startsWith("info") || line == "usiok" || line == "readyok" || line.startsWith("bestmove")) {
            mainHandler.post { onOutputReceived?.invoke(line) }
        } else {
            onOutputReceived?.invoke(line)
        }
    }
}
