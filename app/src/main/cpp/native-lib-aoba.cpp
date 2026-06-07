#include <jni.h>
#include <string>
#include <iostream>
#include <streambuf>
#include <android/log.h>
#include <unistd.h>
#include <mutex>
#include <condition_variable>
#include <queue>
#include <atomic>
#include <memory>

#include "AobaNNUE/source/misc.h"
#include "AobaNNUE/source/bitboard.h"
#include "AobaNNUE/source/position.h"
#include "AobaNNUE/source/types.h"

static JavaVM* g_vm = nullptr;
static jobject g_obj = nullptr;
static jmethodID g_mid = nullptr;
// ヒープ確保: exit() による global dtor でも破棄されない
static std::mutex& g_mutex = *new std::mutex();

// cout -> Kotlinコールバックへリダイレクトするバッファ
class AobaOutBuf : public std::streambuf {
protected:
    int_type overflow(int_type c) override {
        if (c != EOF) {
            if (c == '\n') sendToKotlin();
            else           line += (char)c;
        }
        return c;
    }
private:
    std::string line;
    void sendToKotlin() {
        if (line.empty()) return;
        __android_log_print(ANDROID_LOG_DEBUG, "ShogiJNI_Aoba", "Engine: %s", line.c_str());
        std::lock_guard<std::mutex> lock(g_mutex);
        if (!g_obj || !g_vm) { line.clear(); return; }
        JNIEnv* env;
        bool attached = false;
        if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
            if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) { line.clear(); return; }
            attached = true;
        }
        jstring jline = env->NewStringUTF(line.c_str());
        env->CallVoidMethod(g_obj, g_mid, jline);
        env->DeleteLocalRef(jline);
        if (attached) g_vm->DetachCurrentThread();
        line.clear();
    }
};

// cin をブロッキングキューで置き換えるバッファ
class AobaCinBuf : public std::streambuf {
public:
    void push_line(const std::string& s) {
        { std::lock_guard<std::mutex> lock(mtx_); queue_.push(s + "\n"); }
        cv_.notify_one();
    }
protected:
    int_type underflow() override {
        if (gptr() < egptr()) return traits_type::to_int_type(*gptr());
        std::unique_lock<std::mutex> lock(mtx_);
        cv_.wait(lock, [this]{ return !queue_.empty(); });
        line_ = std::move(queue_.front());
        queue_.pop();
        lock.unlock();
        if (line_.empty()) return traits_type::eof();
        setg(&line_[0], &line_[0], &line_[0] + line_.size());
        return traits_type::to_int_type(*gptr());
    }
private:
    std::string line_;
    std::mutex mtx_;
    std::condition_variable cv_;
    std::queue<std::string> queue_;
};

// shared_ptr で管理: nativeSendCommand がローカルコピーを保持している間は
// nativeStart が終了してもオブジェクトが破壊されない
static std::shared_ptr<AobaCinBuf> g_cin_buf;
// ヒープ確保: exit() による global dtor でも破棄されない
static std::mutex& g_cin_mutex = *new std::mutex();

// nativeStart の多重起動を防ぐ
static std::atomic<bool> g_running{false};

extern "C" JNIEXPORT void JNICALL
Java_com_tksoft_shogigui_AobaEngine_nativeStart(JNIEnv* env, jobject thiz) {
    if (g_running.exchange(true)) return;

    env->GetJavaVM(&g_vm);
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_obj = env->NewGlobalRef(thiz);
        jclass clazz = env->GetObjectClass(thiz);
        g_mid = env->GetMethodID(clazz, "onOutput", "(Ljava/lang/String;)V");
    }

    auto sendDebug = [&](const char* msg) {
        JNIEnv* myEnv;
        if (g_vm->GetEnv((void**)&myEnv, JNI_VERSION_1_6) == JNI_EDETACHED)
            g_vm->AttachCurrentThread(&myEnv, nullptr);
        jstring jmsg = myEnv->NewStringUTF(msg);
        myEnv->CallVoidMethod(g_obj, g_mid, jmsg);
        myEnv->DeleteLocalRef(jmsg);
    };

    sendDebug("info string JNI Aoba: Engine thread started");

    // cin をキュー経由に差し替え (ヒープ確保して shared_ptr で管理)
    auto cin_buf = std::make_shared<AobaCinBuf>();
    {
        std::lock_guard<std::mutex> lock(g_cin_mutex);
        g_cin_buf = cin_buf;
    }
    std::streambuf* old_cin = std::cin.rdbuf(cin_buf.get());

    // 最初のコマンド "usi" を事前投入
    cin_buf->push_line("usi");

    // cout -> Kotlinコールバックに差し替え
    AobaOutBuf out_buf;
    std::streambuf* old_cout = std::cout.rdbuf(&out_buf);

    static bool initialized = false;
    if (!initialized) {
        char* argv[] = {(char*)"aoba"};
        YaneuraOu::CommandLine::g.set_arg(1, argv);
        YaneuraOu::Bitboards::init();
        YaneuraOu::Position::init();
        initialized = true;
    }

    sendDebug("info string JNI Aoba: run_engine_entry start");
    YaneuraOu::run_engine_entry();  // "quit" が来るまでブロック

    // cinとcoutを先に戻す(以降の出力/入力はエンジンには届かない)
    std::cin.rdbuf(old_cin);
    std::cout.rdbuf(old_cout);

    // グローバル参照を外す。cin_buf ローカル変数が生きている間は
    // 他スレッドが push_line を呼んでも安全(shared_ptr が守る)
    {
        std::lock_guard<std::mutex> lock(g_cin_mutex);
        g_cin_buf.reset();
    }
    // cin_buf がここでスコープアウト → 他スレッドが参照を手放した後に破棄

    {
        std::lock_guard<std::mutex> lock(g_mutex);
        env->DeleteGlobalRef(g_obj);
        g_obj = nullptr;
    }

    g_running.store(false);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tksoft_shogigui_AobaEngine_nativeSendCommand(JNIEnv* env, jobject thiz, jstring command) {
    const char* cmd = env->GetStringUTFChars(command, nullptr);
    __android_log_print(ANDROID_LOG_DEBUG, "ShogiJNI_Aoba", "sendCommand: %s", cmd);
    // ローカルコピーを取ることで、nativeStart が終了しても安全に使える
    std::shared_ptr<AobaCinBuf> buf;
    {
        std::lock_guard<std::mutex> lock(g_cin_mutex);
        buf = g_cin_buf;
    }
    if (buf) buf->push_line(std::string(cmd));
    env->ReleaseStringUTFChars(command, cmd);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tksoft_shogigui_AobaEngine_nativeStop(JNIEnv* env, jobject thiz) {
    std::shared_ptr<AobaCinBuf> buf;
    {
        std::lock_guard<std::mutex> lock(g_cin_mutex);
        buf = g_cin_buf;
    }
    if (buf) buf->push_line("quit");
}

extern "C" JNIEXPORT void JNICALL
Java_com_tksoft_shogigui_AobaEngine_nativeSetWorkDir(JNIEnv* env, jobject thiz, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    chdir(p);
    env->ReleaseStringUTFChars(path, p);
}
