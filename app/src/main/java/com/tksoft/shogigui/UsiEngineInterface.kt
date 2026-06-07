package com.tksoft.shogigui

interface UsiEngineInterface {
    var onOutputReceived: ((String) -> Unit)?
    fun start(workDir: String)
    fun sendCommand(command: String)
    fun stop()
}
