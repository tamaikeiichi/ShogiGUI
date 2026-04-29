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
Java_com_example_shogigui_UsiEngine_nativeStart(JNIEnv* env, jobject thiz) {
    env->GetJavaVM(&g_vm);
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_obj = env->NewGlobalRef(thiz);
        jclass clazz = env->GetObjectClass(thiz);
        g_mid = env->GetMethodID(clazz, "onOutput", "(Ljava/lang/String;)V");
    }

    // デバッグメッセージを強制送信
    {
        JNIEnv* myEnv;
        g_vm->AttachCurrentThread(&myEnv, nullptr);
        jstring msg = myEnv->NewStringUTF("info string JNI: Engine thread started");
        myEnv->CallVoidMethod(g_obj, g_mid, msg);
        myEnv->DeleteLocalRef(msg);
    }

    // Redirect cout (ここを一旦コメントアウトして、純粋な起動を試す)
    // UsiBuf buf;
    // std::streambuf* old_cout = std::cout.rdbuf(&buf);

    // Initialize YaneuraOu
    static bool initialized = false;
    if (!initialized) {
        char* argv[] = {(char*)"yaneuraou"};
        CommandLine::init(1, argv);
        USI::init(Options);
        Bitboards::init();
        Position::init();
        Search::init();
        initialized = true;
    }

    size_t thread_num = Options.count("Threads") ? (size_t)Options["Threads"] : 1;
	Threads.set(thread_num);
    Eval::init();

    char* argv[] = {(char*)"yaneuraou"};
    // Start loop (this will block until "quit")
    USI::loop(1, argv);

    // Cleanup
    Threads.set(0);
    // std::cout.rdbuf(old_cout);

    {
        std::lock_guard<std::mutex> lock(g_mutex);
        env->DeleteGlobalRef(g_obj);
        g_obj = nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_shogigui_UsiEngine_nativeSendCommand(JNIEnv* env, jobject thiz, jstring command) {
    const char* cmd = env->GetStringUTFChars(command, nullptr);
    std_input.push(std::string(cmd));
    env->ReleaseStringUTFChars(command, cmd);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_shogigui_UsiEngine_nativeStop(JNIEnv* env, jobject thiz) {
    std_input.push("quit");
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_shogigui_UsiEngine_nativeSetWorkDir(JNIEnv* env, jobject thiz, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    chdir(p);
    env->ReleaseStringUTFChars(path, p);
}
