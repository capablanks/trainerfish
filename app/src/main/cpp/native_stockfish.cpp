// native_stockfish.cpp
// JNI wrapper to run Stockfish IN-PROCESS (no exec).  Flat source layout.
//
// Kotlin side:
//   package com.tonorbe.trainerfish.engine
//   object NativeStockfish {
//       init { System.loadLibrary("trainerfish") }
//       @JvmStatic external fun start()
//       @JvmStatic external fun stop()
//       @JvmStatic external fun send(cmd: String)
//       @JvmStatic external fun poll(): String?
//   }

#include <jni.h>

#include <atomic>
#include <condition_variable>
#include <iostream>
#include <mutex>
#include <queue>
#include <streambuf>
#include <string>
#include <thread>
#include <vector>

// Provided by sf_entry.cpp (main.cpp is renamed there)
extern "C" int stockfish_main(int, char**);

// ---------------------- Thread-safe queues ----------------------
static std::queue<std::string> gInQ;
static std::queue<std::string> gOutQ;
static std::mutex              gMtx;
static std::condition_variable gCv;
static std::atomic<bool>       gRunning(false);
static std::thread             gThread;

// ---------------------- stdin redirection -----------------------
class QueueInBuf : public std::streambuf {
public:
    QueueInBuf() { setg(nullptr, nullptr, nullptr); }

protected:
    int_type underflow() override {
        if (!gRunning.load()) return traits_type::eof();

        if (gptr() >= egptr()) {
            std::unique_lock<std::mutex> lk(gMtx);
            gCv.wait(lk, [] { return !gInQ.empty() || !gRunning.load(); });
            if (!gRunning.load()) return traits_type::eof();

            // Take one full UCI line from queue, append '\n'
            const std::string& s = gInQ.front();
            buf_.assign(s.begin(), s.end());
            buf_.push_back('\n');
            gInQ.pop();
            lk.unlock();

            setg(buf_.data(), buf_.data(), buf_.data() + buf_.size());
        }
        return traits_type::to_int_type(*gptr());
    }

private:
    std::vector<char> buf_;
};

// ---------------------- stdout/stderr redirection ----------------
class QueueOutBuf : public std::streambuf {
protected:
    int_type overflow(int_type ch) override {
        if (traits_type::eq_int_type(ch, traits_type::eof())) return traits_type::not_eof(ch);
        const char c = traits_type::to_char_type(ch);
        if (c == '\r') return ch; // ignore CR
        if (c == '\n') {
            std::lock_guard<std::mutex> lk(gMtx);
            gOutQ.push(line_);
            line_.clear();
            return ch;
        }
        line_.push_back(c);
        return ch;
    }
    int sync() override { return 0; }

private:
    std::string line_;
};

// ---------------------- Engine thread ---------------------------
static void engineThread() {
    gRunning.store(true, std::memory_order_release);

    QueueInBuf  inBuf;
    QueueOutBuf outBuf;

    // Redirect only within this thread
    std::streambuf* oldIn  = std::cin.rdbuf(&inBuf);
    std::streambuf* oldOut = std::cout.rdbuf(&outBuf);
    std::streambuf* oldErr = std::cerr.rdbuf(&outBuf);

    const char* argv0 = "stockfish";
    stockfish_main(1, const_cast<char**>(&argv0));

    // Restore
    std::cin.rdbuf(oldIn);
    std::cout.rdbuf(oldOut);
    std::cerr.rdbuf(oldErr);

    gRunning.store(false, std::memory_order_release);
    gCv.notify_all(); // wake any waiters
}

// ---------------------- JNI exports -----------------------------
// package: com.tonorbe.trainerfish.engine
// class:   NativeStockfish  (object; methods are @JvmStatic external)

extern "C" JNIEXPORT void JNICALL
Java_com_tonorbe_trainerfish_engine_NativeStockfish_start(JNIEnv* /*env*/, jclass /*clazz*/) {
    if (gRunning.load()) return;
    {
        std::lock_guard<std::mutex> lk(gMtx);
        while (!gInQ.empty())  gInQ.pop();
        while (!gOutQ.empty()) gOutQ.pop();
    }
    gThread = std::thread(engineThread);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tonorbe_trainerfish_engine_NativeStockfish_stop(JNIEnv* /*env*/, jclass /*clazz*/) {
    if (!gRunning.load()) return;
    {
        std::lock_guard<std::mutex> lk(gMtx);
        // UCI-compliant stop; engineThread will exit after processing
        gInQ.emplace("quit");
    }
    gCv.notify_all();
    if (gThread.joinable()) gThread.join();
}

extern "C" JNIEXPORT void JNICALL
Java_com_tonorbe_trainerfish_engine_NativeStockfish_send(JNIEnv* env, jclass /*clazz*/, jstring jcmd) {
    const char* c = env->GetStringUTFChars(jcmd, nullptr);
    {
        std::lock_guard<std::mutex> lk(gMtx);
        gInQ.emplace(c ? c : "");
    }
    env->ReleaseStringUTFChars(jcmd, c);
    gCv.notify_all();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_tonorbe_trainerfish_engine_NativeStockfish_poll(JNIEnv* env, jclass /*clazz*/) {
    std::lock_guard<std::mutex> lk(gMtx);
    if (gOutQ.empty()) return nullptr;
    std::string s = std::move(gOutQ.front());
    gOutQ.pop();
    return env->NewStringUTF(s.c_str());
}

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* /*vm*/, void* /*reserved*/) {
    return JNI_VERSION_1_6;
}
