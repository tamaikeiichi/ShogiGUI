cmake_minimum_required(VERSION 3.22.1)
project("shogigui")

# エンジンはアプリとは別のプロセスで動かす実行ファイルとしてビルドする（USI を標準入出力でやり取りする）。
# エンジンを切り替えるときはプロセスごと終わらせるので、評価関数などのメモリが確実に解放される。
# APK に同梱して nativeLibraryDir に展開させるため、ファイル名は lib<名前>.so にする
function(set_engine_output_name target)
    set_target_properties(${target} PROPERTIES OUTPUT_NAME "lib${target}.so")
endfunction()

# ===== 既存エンジン: やねうら王 + 水匠5 =====
file(GLOB_RECURSE ENGINE_SOURCES "YaneuraOu/*.cpp")
add_executable(yaneuraou ${ENGINE_SOURCES})
set_engine_output_name(yaneuraou)

if(${ANDROID_ABI} STREQUAL "x86_64")
    target_compile_definitions(yaneuraou PRIVATE USE_SSE42 COMPILED_ARCH="x86_64")
    message(STATUS "Compiling yaneuraou for x86_64 (SSE4.2 enabled)")
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
    # IS_64BIT は x86_64 以外では自動で定義されず、無いと 32bit 向けの実装になる
    target_compile_definitions(yaneuraou PRIVATE IS_64BIT USE_NEON COMPILED_ARCH="arm64-v8a")
    message(STATUS "Compiling yaneuraou for arm64-v8a (NEON)")
else()
    target_compile_definitions(yaneuraou PRIVATE COMPILED_ARCH="${ANDROID_ABI}")
endif()

target_compile_definitions(yaneuraou PRIVATE
    # "usi" に返す id name は探索部の名前とバージョン (YaneuraOu 8.30git) のままにする。
    # 評価関数の名前はアプリ側 (Engines.kt) で持つ
    YANEURAOU_ENGINE_NNUE
    TARGET_CPU="Android"
    NDEBUG
    NO_ASSUME_VLA
    USE_LARGE_PAGES
    # 探索スレッドを 8MB スタックで作る (thread_win32_osx.h の NativeThread)。
    # 未定義だと std::thread になり、Android の既定 (約1MB) では深い探索でスタックオーバーフローする
    USE_PTHREADS
)

target_compile_options(yaneuraou PRIVATE
    -std=c++17
    -O3
    -ffast-math
    -flto
    -fvisibility=hidden
)

target_link_options(yaneuraou PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(yaneuraou android log)

# ===== 2つ目のエンジン: AobaNNUE =====
file(GLOB_RECURSE AOBA_SOURCES "AobaNNUE/source/*.cpp")
add_executable(aobannue ${AOBA_SOURCES})
set_engine_output_name(aobannue)

if(${ANDROID_ABI} STREQUAL "x86_64")
    target_compile_definitions(aobannue PRIVATE USE_SSE42 COMPILED_ARCH="x86_64")
    message(STATUS "Compiling aobannue for x86_64 (SSE4.2 enabled)")
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_definitions(aobannue PRIVATE IS_64BIT USE_NEON COMPILED_ARCH="arm64-v8a")
    message(STATUS "Compiling aobannue for arm64-v8a (NEON)")
else()
    target_compile_definitions(aobannue PRIVATE COMPILED_ARCH="${ANDROID_ABI}")
endif()

target_compile_definitions(aobannue PRIVATE
    YANEURAOU_ENGINE_NNUE
    YANEURAOU_ENGINE_NNUE_HALFKP_768X2_16_64
    TARGET_CPU="Android"
    NDEBUG
    NO_ASSUME_VLA
    USE_LARGE_PAGES
    # 探索スレッドを 8MB スタックで作る (thread_win32_osx.h の NativeThread)。
    # 未定義だと std::thread になり、Android の既定 (約1MB) では深い探索でスタックオーバーフローする
    USE_PTHREADS
)

target_compile_options(aobannue PRIVATE
    -std=c++17
    -O3
    -ffast-math
    -flto
    -fvisibility=hidden
)

target_link_options(aobannue PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(aobannue android log)

# ===== 3つ目以降のエンジン: やねうら王 V9系 + つようら王11 (SFNN) =====
# 評価関数の architecture はコンパイル時に固定されるため、評価関数ごとに別の実行ファイルを作る。
# ソース一覧は YaneuraOu9/source/Makefile の NNUE エディション (SFNN_*) に合わせている。
# architecture header は Makefile 同様 nnue_arch_gen.py で事前生成して
# YaneuraOu9/source/eval/nnue/architectures/ に置いておくこと。
set(YO9_DIR "${CMAKE_CURRENT_SOURCE_DIR}/YaneuraOu9/source")
set(YO9_SOURCES
    types.cpp bitboard.cpp misc.cpp movegen.cpp position.cpp usi.cpp usioption.cpp
    thread.cpp tt.cpp movepick.cpp timeman.cpp memory.cpp engine.cpp search.cpp
    score.cpp benchmark.cpp tune.cpp
    book/book.cpp book/apery_book.cpp book/policybook.cpp book/makebook.cpp book/makebook2025.cpp
    extra/bitop.cpp extra/long_effect.cpp extra/sfen_packer.cpp
    mate/mate.cpp mate/mate1ply_without_effect.cpp mate/mate1ply_with_effect.cpp mate/mate_solver.cpp
    eval/evaluate_bona_piece.cpp eval/evaluate.cpp eval/evaluate_io.cpp
    eval/evaluate_mir_inv_tools.cpp eval/material/evaluate_material.cpp
    testcmd/unit_test.cpp testcmd/mate_test_cmd.cpp testcmd/normal_test_cmd.cpp
    eval/nnue/evaluate_nnue.cpp eval/nnue/nnue_test_command.cpp
    eval/nnue/features/k.cpp eval/nnue/features/p.cpp eval/nnue/features/a2.cpp
    eval/nnue/features/half_kp.cpp eval/nnue/features/half_ka1.cpp
    eval/nnue/features/half_ka_hm1.cpp eval/nnue/features/half_ka2.cpp
    eval/nnue/features/half_ka_hm2.cpp eval/nnue/features/half_kp_vm.cpp
    eval/nnue/features/half_relative_kp.cpp eval/nnue/features/half_kpe9.cpp
    eval/nnue/features/pe9.cpp
    engine/yaneuraou-engine/yaneuraou-search.cpp
    main.cpp
)
list(TRANSFORM YO9_SOURCES PREPEND "${YO9_DIR}/")

# lib_name    : 実行ファイルの名前 (lib<lib_name>.so として同梱される)
# nnue_arch   : SFNN architecture 名 (= architectures/<nnue_arch>.h)
function(add_yaneuraou9_engine lib_name nnue_arch)
    add_executable(${lib_name} ${YO9_SOURCES})
    set_engine_output_name(${lib_name})

    if(${ANDROID_ABI} STREQUAL "x86_64")
        target_compile_definitions(${lib_name} PRIVATE IS_64BIT USE_SSE42 COMPILED_ARCH="x86_64")
        target_compile_options(${lib_name} PRIVATE -msse4.2 -mpopcnt)
    elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
        # Makefile の TARGET_CPU=ARMV8 相当 (全 arm64 端末で動く NEON 版)
        target_compile_definitions(${lib_name} PRIVATE IS_64BIT USE_NEON=8 COMPILED_ARCH="arm64-v8a")
    else()
        target_compile_definitions(${lib_name} PRIVATE NO_SSE COMPILED_ARCH="${ANDROID_ABI}")
    endif()

    target_compile_definitions(${lib_name} PRIVATE
        YANEURAOU_ENGINE_NNUE
        SFNNwoPSQT
        NNUE_ARCHITECTURE_HEADER="architectures/${nnue_arch}.h"
        TARGET_CPU="Android"
        NDEBUG
        NO_EXCEPTIONS
        UNICODE
        # 探索スレッドを 8MB スタックで作る (thread_win32_osx.h の NativeThread)。
        # 未定義だと std::thread になり、Android の既定 (約1MB) では深い探索でスタックオーバーフローする
        USE_PTHREADS
    )

    target_compile_options(${lib_name} PRIVATE
        -std=c++17
        -O3
        -ffast-math
        -flto
        -fvisibility=hidden
        -Wno-unused-parameter
    )

    target_link_options(${lib_name} PRIVATE "-Wl,-z,max-page-size=16384" -flto)
    target_link_libraries(${lib_name} android log)
endfunction()

add_yaneuraou9_engine(tsuyou11micro SFNN_ka2_2048_8_64_progress8)
add_yaneuraou9_engine(tsuyou11mini  SFNN_ka2_4096_8_64_progress8)
