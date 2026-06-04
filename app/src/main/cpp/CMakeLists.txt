cmake_minimum_required(VERSION 3.22.1)
project("shogigui")

# やねうら王のソースファイルをすべて取得
file(GLOB_RECURSE ENGINE_SOURCES "YaneuraOu/*.cpp")

# 共有ライブラリ(SHARED)として定義
add_library(yaneuraou SHARED ${ENGINE_SOURCES} native-lib.cpp)

# コンパイルオプションとマクロ定義
# CPUアーキテクチャに応じた最適化フラグの設定
if(${ANDROID_ABI} STREQUAL "x86_64")
    target_compile_definitions(yaneuraou PRIVATE USE_SSE42 COMPILED_ARCH="x86_64")
    message(STATUS "Compiling for x86_64 (SSE4.2 enabled)")
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
    # ARM向けの定義（やねうら王の仕様に合わせる）
    target_compile_definitions(yaneuraou PRIVATE NO_SSE COMPILED_ARCH="arm64-v8a")
    message(STATUS "Compiling for arm64-v8a (No SSE)")
else()
    target_compile_definitions(yaneuraou PRIVATE COMPILED_ARCH="${ANDROID_ABI}")
    message(STATUS "Compiling for unknown ABI: ${ANDROID_ABI}")
endif()

target_compile_definitions(yaneuraou PRIVATE
    YANEURAOU_ENGINE_NNUE
    ENGINE_NAME="Suisho5_Android"
    ENGINE_VERSION="2024.1.0"
    TARGET_CPU="Android"
    # その他共通フラグ
    NDEBUG
    NO_ASSUME_VLA
    USE_LARGE_PAGES
)

target_compile_options(yaneuraou PRIVATE
    -std=c++17
    -O3
    -ffast-math
    -flto
)

# 16 KB ページサイズ対応
target_link_options(yaneuraou PRIVATE "-Wl,-z,max-page-size=16384")

target_link_libraries(yaneuraou android log)
