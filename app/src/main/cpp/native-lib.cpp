#include <jni.h>
#include <string>
#include <iostream>
#include <vector>
#include <streambuf>
#include <android/log.h>
#include <unistd.h>
#include <mutex>

#include "YaneuraOu/usi.h"
#include "YaneuraOu/misc.h"
#include "YaneuraOu/bitboard.h"
#include "YaneuraOu/position.h"
#include "YaneuraOu/thread.h"
#include "YaneuraOu/tt.h"

static JavaVM* g_vm = nullptr;
static jobject g_obj = nullptr;
static jmethodID g_mid = nullptr;
static std::mutex g_mutex;

// Redirect std::cout to Kotlin callback
class UsiBuf : public std::streambuf {
protected:
    virtual int_type overflow(int_type c) override {
        if (c != EOF) {
            if (c == '\n') {
                sendToKotlin();
            } else {
                line += (char)c;
            }
        }
        return c;
    }
private:
    std::string line;
    void sendToKotlin() {
        if (line.empty()) return;

        // Logcat にも出力 (タグ: ShogiJNI)
        __android_log_print(ANDROID_LOG_DEBUG, "ShogiJNI", "Engine: %s", line.c_str());

        std::lock_guard<std::mutex> lock(g_mutex);
        if (!g_obj || !g_vm) return;

        JNIEnv* env;
        jint res = g_vm->GetEnv((void**)&env, JNI_VERSION_1_6);
        bool attached = false;
        if (res == JNI_EDETACHED) {
            if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
            attached = true;
        }

        jstring jline = env->NewStringUTF(line.c_str());
        env->CallVoidMethod(g_obj, g_mid, jline);
        env->DeleteLocalRef(jline);

        if (attached) {
            // g_vm->DetachCurrentThread(); // Optional: Detach if needed
        }
        line.clear();
    }
};

extern "C" JNIEXPORT void JNICALL
Java_com_tksoft_shogigui_UsiEngine_nativeStart(JNIEnv* env, jobject thiz) {
    env->GetJavaVM(&g_vm);
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_obj = env->NewGlobalRef(thiz);
        jclass clazz = env->GetObjectClass(thiz);
        g_mid = env->GetMethodID(clazz, "onOutput", "(Ljava/lang/String;)V");
    }

    // デバッグメッセージ用ヘルパー
    auto sendDebug = [&](const char* msg) {
        JNIEnv* myEnv;
        if (g_vm->GetEnv((void**)&myEnv, JNI_VERSION_1_6) == JNI_EDETACHED) {
            g_vm->AttachCurrentThread(&myEnv, nullptr);
        }
        jstring jmsg = myEnv->NewStringUTF(msg);
        myEnv->CallVoidMethod(g_obj, g_mid, jmsg);
        myEnv->DeleteLocalRef(jmsg);
    };

    sendDebug("info string JNI: Engine thread started");

    // Initialize YaneuraOu
    static bool initialized = false;
    if (!initialized) {
        char* argv[] = {(char*)"yaneuraou"};

        sendDebug("info string JNI: CommandLine::init start");
        CommandLine::init(1, argv);

        sendDebug("info string JNI: USI::init start");
        USI::init(Options);

        sendDebug("info string JNI: Bitboards::init start");
        Bitboards::init();

        sendDebug("info string JNI: Position::init start");
        Position::init();

        sendDebug("info string JNI: Search::init start");
        Search::init();

        initialized = true;
    }

    // Threads.set() も初回のみ呼ぶ
    static bool threads_initialized = false;
    if (!threads_initialized) {
        size_t thread_num = Options.count("Threads") ? (size_t)Options["Threads"] : 1;
        Threads.set(thread_num);
        Eval::init();
        threads_initialized = true;
    }

    static std::atomic<bool> running(false);
    if (running.exchange(true)) {
        return; // 2回目の呼び出しは即リターン
    }

    sendDebug("info string JNI: Thread setting start");
    size_t thread_num = Options.count("Threads") ? (size_t)Options["Threads"] : 1;
    Threads.set(thread_num);

    sendDebug("info string JNI: Eval::init start");
    Eval::init();
    sendDebug("info string JNI: Eval::init done");

    // エンジンがループに入る前に「usi」を予約しておく
    std_input.push("usi");

    sendDebug("info string JNI: USI::loop start");

    // Redirect cout to Kotlin (蛇口を開ける)
    UsiBuf buf;
    std::streambuf* old_cout = std::cout.rdbuf(&buf);

    char* argv_loop[] = {(char*)"yaneuraou"};
    USI::loop(1, argv_loop);

    // Cleanup
    Threads.set(0);
    std::cout.rdbuf(old_cout); // 蛇口を元に戻す

    {
        std::lock_guard<std::mutex> lock(g_mutex);
        env->DeleteGlobalRef(g_obj);
        g_obj = nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tksoft_shogigui_UsiEngine_nativeSendCommand(JNIEnv* env, jobject thiz, jstring command) {
    const char* cmd = env->GetStringUTFChars(command, nullptr);
    __android_log_print(ANDROID_LOG_DEBUG, "ShogiJNI", "sendCommand called: %s", cmd);
    std_input.push(std::string(cmd));
    __android_log_print(ANDROID_LOG_DEBUG, "ShogiJNI", "sendCommand pushed: %s", cmd);
    env->ReleaseStringUTFChars(command, cmd);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tksoft_shogigui_UsiEngine_nativeStop(JNIEnv* env, jobject thiz) {
    std_input.push("quit");
}

extern "C" JNIEXPORT void JNICALL
Java_com_tksoft_shogigui_UsiEngine_nativeSetWorkDir(JNIEnv* env, jobject thiz, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    chdir(p);
    env->ReleaseStringUTFChars(path, p);
}
