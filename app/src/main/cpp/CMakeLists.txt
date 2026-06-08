cmake_minimum_required(VERSION 3.22.1)
project("shogigui")

# ===== 既存エンジン: やねうら王 + 水匠5 =====
file(GLOB_RECURSE ENGINE_SOURCES "YaneuraOu/*.cpp")
add_library(yaneuraou SHARED ${ENGINE_SOURCES} native-lib.cpp)

if(${ANDROID_ABI} STREQUAL "x86_64")
    target_compile_definitions(yaneuraou PRIVATE USE_SSE42 COMPILED_ARCH="x86_64")
    message(STATUS "Compiling yaneuraou for x86_64 (SSE4.2 enabled)")
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_definitions(yaneuraou PRIVATE NO_SSE COMPILED_ARCH="arm64-v8a")
    message(STATUS "Compiling yaneuraou for arm64-v8a (No SSE)")
else()
    target_compile_definitions(yaneuraou PRIVATE COMPILED_ARCH="${ANDROID_ABI}")
endif()

target_compile_definitions(yaneuraou PRIVATE
    YANEURAOU_ENGINE_NNUE
    ENGINE_NAME="Suisho5_Android"
    ENGINE_VERSION="2024.1.0"
    TARGET_CPU="Android"
    NDEBUG
    NO_ASSUME_VLA
    USE_LARGE_PAGES
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
# main.cpp は不要（shared libraryなのでmain()は呼ばれない。ただし除外してもよい）
list(REMOVE_ITEM AOBA_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/AobaNNUE/source/main.cpp")

add_library(aobannue SHARED ${AOBA_SOURCES} native-lib-aoba.cpp)

if(${ANDROID_ABI} STREQUAL "x86_64")
    target_compile_definitions(aobannue PRIVATE USE_SSE42 COMPILED_ARCH="x86_64")
    message(STATUS "Compiling aobannue for x86_64 (SSE4.2 enabled)")
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_definitions(aobannue PRIVATE NO_SSE COMPILED_ARCH="arm64-v8a")
    message(STATUS "Compiling aobannue for arm64-v8a (No SSE)")
else()
    target_compile_definitions(aobannue PRIVATE COMPILED_ARCH="${ANDROID_ABI}")
endif()

target_compile_definitions(aobannue PRIVATE
    YANEURAOU_ENGINE_NNUE
    YANEURAOU_ENGINE_NNUE_HALFKP_768X2_16_64
    ENGINE_NAME_FROM_MAKEFILE=AobaNNUE
    ENGINE_VERSION="AobaNNUE_Android"
    TARGET_CPU="Android"
    NDEBUG
    NO_ASSUME_VLA
    USE_LARGE_PAGES
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
