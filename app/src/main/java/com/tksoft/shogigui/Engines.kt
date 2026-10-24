package com.tksoft.shogigui

import android.content.Context

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
fun engineEvalName(id: String): String = when (id) {
    "aoba" -> "AobaNNUE"
    "tsuyou11micro" -> "TsuyouraOu11micro"
    "tsuyou11mini" -> "TsuyouraOu11mini"
    else -> "Suisho5"
}

/**
 * USI の "id name" から探索部の名前とバージョンを取り出す。
 * やねうら王は "YaneuraOu NNUE 9.80git 64Android" (名前 評価関数の種類 バージョン bit数+CPU) の形で返すので、
 * 名前とバージョンだけにする
 */
fun searchEngineName(idName: String): String {
    val tokens = idName.trim().split(Regex("\\s+")).toMutableList()
    if (tokens.size >= 3 && Regex("^(32|64)").containsMatchIn(tokens.last())) tokens.removeAt(tokens.lastIndex)
    return if (tokens.size >= 2) "${tokens.first()} ${tokens.last()}" else tokens.joinToString(" ")
}

/**
 * 評価関数と探索部の名前を並べた表示名。探索部はエンジンから "id name" を受け取るまで分からないので、それまでは評価関数名だけ
 * @param withCaptions 「評価関数」「探索部」の見出しを付けるか
 */
fun engineLabel(context: Context, id: String, idName: String?, withCaptions: Boolean = true): String = when {
    idName.isNullOrBlank() ->
        if (withCaptions) context.getString(R.string.engine_label_eval_only, engineEvalName(id)) else engineEvalName(id)
    withCaptions -> context.getString(R.string.engine_label, engineEvalName(id), searchEngineName(idName))
    else -> context.getString(R.string.engine_label_plain, engineEvalName(id), searchEngineName(idName))
}

/** 評価関数に付属するバナー画像 (準備完了ポップアップの背景に使う)。無ければ null */
fun engineBannerRes(id: String): Int? = when (id) {
    "tsuyou11micro" -> R.drawable.banner_tsuyou11micro
    "tsuyou11mini" -> R.drawable.banner_tsuyou11mini
    else -> null
}
