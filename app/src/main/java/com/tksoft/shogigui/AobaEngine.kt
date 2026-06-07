package com.tksoft.shogigui

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

class AobaEngine : UsiEngineInterface {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override var onOutputReceived: ((String) -> Unit)? = null

    companion object {
        init {
            System.loadLibrary("aobannue")
        }
    }

    private external fun nativeStart()
    private external fun nativeSendCommand(command: String)
    private external fun nativeStop()
    private external fun nativeSetWorkDir(path: String)

    override fun start(workDir: String) {
        executor.execute {
            try {
                if (workDir.isNotEmpty()) nativeSetWorkDir(workDir)
                // "usi" は native-lib-aoba.cpp 内で事前投入済み
                nativeStart()
            } catch (e: Exception) {
                mainHandler.post { onOutputReceived?.invoke("Error: " + e.message) }
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
        }, "Aoba-Command-Thread").start()
    }

    override fun sendCommand(command: String) {
        commandQueue.put(command)
    }

    override fun stop() {
        nativeStop()
    }

    fun onOutput(line: String) {
        if (line.startsWith("info") || line == "usiok" || line == "readyok" || line.startsWith("bestmove")) {
            mainHandler.post { onOutputReceived?.invoke(line) }
        } else {
            onOutputReceived?.invoke(line)
        }
    }
}
