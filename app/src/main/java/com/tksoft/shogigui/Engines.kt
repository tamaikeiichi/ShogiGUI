package com.tksoft.shogigui

/** 設定画面で選べるエンジン (prefs の "selected_engine" の値, 表示名) */
val ENGINE_CHOICES = listOf(
    "suisho5" to "Suisho5",
    "aoba" to "AobaNNUE",
    "tsuyou11micro" to "TsuyouraOu11micro",
    "tsuyou11mini" to "TsuyouraOu11mini",
)

/** 新規インストール時のエンジン */
const val DEFAULT_ENGINE = "tsuyou11mini"

fun createEngine(id: String): UsiEngineInterface = when (id) {
    "aoba" -> AobaEngine()
    "tsuyou11micro" -> TsuyouMicroEngine()
    "tsuyou11mini" -> TsuyouMiniEngine()
    else -> UsiEngine()
}

/** 設定画面のタイトルに出す、評価関数と探索部の名前 */
fun engineDisplayName(id: String): String = when (id) {
    "aoba" -> "AobaNNUE"
    "tsuyou11micro" -> "TsuyouraOu11micro-YaneuraOu-v9.80git"
    "tsuyou11mini" -> "TsuyouraOu11mini-YaneuraOu-v9.80git"
    else -> "Suisho5-YaneuraOu-v7.5.0"
}

/** 評価関数に付属するバナー画像 (準備完了ポップアップの背景に使う)。無ければ null */
fun engineBannerRes(id: String): Int? = when (id) {
    "tsuyou11micro" -> R.drawable.banner_tsuyou11micro
    "tsuyou11mini" -> R.drawable.banner_tsuyou11mini
    else -> null
}
