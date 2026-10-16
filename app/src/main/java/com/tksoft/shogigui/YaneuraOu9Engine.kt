package com.tksoft.shogigui

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * やねうら王 V9系 (cpp/YaneuraOu9) + SFNN評価関数のエンジン。
 * 評価関数の architecture ごとに別の .so (native-lib-yaneuraou9.cpp) になっているので、
 * native メソッドはサブクラス (JNI関数名のクラス部分) で宣言する。
 *
 * @param evalDirName filesDir 以下で nn.bin / progress.bin / eval_options.txt を置くディレクトリ名
 * @param assetPrefix assets 内のファイル名の接頭辞 (例: "tsuyou11micro_" → "tsuyou11micro_nn.bin")
 */
abstract class YaneuraOu9Engine(
    val evalDirName: String,
    val assetPrefix: String,
) : UsiEngineInterface {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile override var onOutputReceived: ((String) -> Unit)? = null

    companion object {
        /** 評価関数ディレクトリに置くファイル (assets では assetPrefix を付けて格納) */
        val EVAL_FILES = listOf("nn.bin", "progress.bin", "eval_options.txt")

        // プロセス内で動作中のインスタンスを追跡し、重複起動を防ぐ
        // (micro と mini は別の .so なので、それぞれのクラスごとに1つまで)
        private val activeInstances = java.util.concurrent.ConcurrentHashMap<Class<*>, YaneuraOu9Engine>()
    }

    protected abstract fun nativeStart()
    protected abstract fun nativeSendCommand(command: String)
    protected abstract fun nativeStop()
    protected abstract fun nativeSetWorkDir(path: String)

    /** filesDir 以下に評価関数ファイルをコピーする (IO スレッドで呼ぶこと) */
    fun copyEvalFiles(filesDir: java.io.File, assets: android.content.res.AssetManager) {
        for (name in EVAL_FILES) {
            copyAssetsToFileDir(assetPrefix + name, evalDirName, filesDir, assets, targetName = name)
        }
    }

    override fun start(workDir: String) {
        android.util.Log.d("EngineDebug", "${javaClass.simpleName}.start() called on $this workDir=$workDir")
        // 別インスタンスが既に動いている場合（Activity再生成など）は先に停止
        val prev = activeInstances[javaClass]
        if (prev != null && prev !== this) {
            android.util.Log.d("EngineDebug", "${javaClass.simpleName}.start() stopping prev instance $prev")
            prev.nativeStop()
            activeInstances.remove(javaClass, prev)
        }
        activeInstances[javaClass] = this
        executor.execute {
            try {
                if (workDir.isNotEmpty()) nativeSetWorkDir(workDir)
                // "usi" は native-lib-yaneuraou9.cpp 内で事前投入済み
                nativeStart()
                android.util.Log.d("EngineDebug", "${javaClass.simpleName} nativeStart() returned for $this")
            } catch (e: Exception) {
                mainHandler.post { onOutputReceived?.invoke("Error: " + e.message) }
            } finally {
                activeInstances.remove(javaClass, this)
            }
        }
    }

    private val commandQueue = java.util.concurrent.LinkedBlockingQueue<String>()

    init {
        Thread({
            while (true) {
                val command = commandQueue.take()
                try {
                    nativeSendCommand(command)
                } catch (_: Exception) {}
            }
        }, "YO9-Command-Thread").also { it.isDaemon = true }.start()
    }

    override fun sendCommand(command: String) {
        commandQueue.put(command)
    }

    override fun stop() {
        android.util.Log.d("EngineDebug", "${javaClass.simpleName}.stop() called on $this")
        nativeStop()
        activeInstances.remove(javaClass, this)
    }

    fun onOutput(line: String) {
        if (line.startsWith("info") || line == "usiok" || line == "readyok" || line.startsWith("bestmove")) {
            mainHandler.post { onOutputReceived?.invoke(line) }
        } else {
            onOutputReceived?.invoke(line)
        }
    }
}

/** つようら王11micro (SFNN_ka2_2048_8_64_progress8) */
class TsuyouMicroEngine : YaneuraOu9Engine("tsuyou11micro_eval", "tsuyou11micro_") {
    companion object {
        init {
            System.loadLibrary("tsuyou11micro")
        }
    }

    override external fun nativeStart()
    override external fun nativeSendCommand(command: String)
    override external fun nativeStop()
    override external fun nativeSetWorkDir(path: String)
}

/** つようら王11mini (SFNN_ka2_4096_8_64_progress8) */
class TsuyouMiniEngine : YaneuraOu9Engine("tsuyou11mini_eval", "tsuyou11mini_") {
    companion object {
        init {
            System.loadLibrary("tsuyou11mini")
        }
    }

    override external fun nativeStart()
    override external fun nativeSendCommand(command: String)
    override external fun nativeStop()
    override external fun nativeSetWorkDir(path: String)
}
