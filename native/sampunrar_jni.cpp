// sampunrar_jni.cpp — ekstraksi RAR5 selektif untuk SAMP PC.
//
// Dipakai di dua tempat:
//   1. build host (SAMPUNRAR_HOST_TEST) -> alat uji di PC, tanpa JNI
//   2. APK -> JNI, dipanggil RarNative.java
//
// Kenapa selective: paket 4,7 GB di Drive isinya RAR bersarang. HP cuma perlu
// file sesuai tier (EXTREME 1,55 GB), jadi entri lain di-SKIP, bukan diekstrak.
//
// Lisensi UnRAR: kode unrar/ berasal dari RARLAB (lihat unrar/license.txt) —
// boleh dipakai untuk mengekstrak RAR, tidak boleh untuk membuat kompresor RAR.

#include <cstdio>
#include <cstring>
#include <string>
#include <vector>
#include <sys/stat.h>
#include <sys/types.h>

#include "dll.hpp"

#ifdef SAMPUNRAR_HOST_TEST
typedef void (*ProgressFn)(void *ctx, const char *name, long long done);
#else
#include <jni.h>
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "sampunrar", __VA_ARGS__)
#endif

namespace {

struct Ctx {
    const std::vector<std::string> *wanted;   // suffix yang dicari
    std::string outDir;
    long long bytes = 0;
    int files = 0;
    bool *matchFlags = nullptr;               // paralel dengan wanted
    ProgressFn progress = nullptr;
    void *user = nullptr;

#ifndef SAMPUNRAR_HOST_TEST
    JNIEnv *env = nullptr;
    jobject callback = nullptr;
    jmethodID onData = nullptr;
    long long lastReport = 0;
#endif
};

// unrar minta password? tidak ada yang terenkripsi, tapi tetap sediakan.
int CALLBACK callback(UINT msg, LPARAM userData, LPARAM p1, LPARAM p2) {
    Ctx *c = reinterpret_cast<Ctx *>(userData);
    if (msg == UCM_PROCESSDATA && c) {
        c->bytes += p2;
        long long done = c->bytes;
#ifndef SAMPUNRAR_HOST_TEST
        // lapor tiap ~2 MB biar UI tidak kebanjiran panggilan
        if (c->env && c->callback && done - c->lastReport > (2 << 20)) {
            c->lastReport = done;
            jstring jn = c->env->NewStringUTF("");
            c->env->CallVoidMethod(c->callback, c->onData, jn, done, (jlong)0);
            c->env->DeleteLocalRef(jn);
            if (c->env->ExceptionCheck()) c->env->ExceptionClear();
        }
#else
        if (c->progress) c->progress(c->user, "", done);
#endif
    }
    return 1;
}

std::string baseName(const std::string &s) {
    size_t p = s.find_last_of('/');
    return p == std::string::npos ? s : s.substr(p + 1);
}

bool endsWith(const std::string &s, const std::string &suffix) {
    return s.size() >= suffix.size() &&
           s.compare(s.size() - suffix.size(), suffix.size(), suffix) == 0;
}

void mkdirs(const std::string &path) {
    std::string cur;
    for (size_t i = 0; i < path.size(); i++) {
        cur.push_back(path[i]);
        if (path[i] == '/' && cur.size() > 1) mkdir(cur.c_str(), 0771);
    }
    mkdir(path.c_str(), 0771);
}

} // namespace

/**
 * @return jumlah file diekstrak; ctx.bytes berisi total byte.
 */
static int extractSelected(const char *rarPath, const char *outDir,
                           const std::vector<std::string> &wanted, Ctx &ctx) {
    ctx.wanted = &wanted;
    ctx.outDir = outDir;

    RAROpenArchiveDataEx openInfo;
    memset(&openInfo, 0, sizeof(openInfo));
    openInfo.ArcName = const_cast<char *>(rarPath);
    openInfo.OpenMode = RAR_OM_EXTRACT;

    HANDLE h = RAROpenArchiveEx(&openInfo);
    if (!h || openInfo.OpenResult != 0) {
        if (h) RARCloseArchive(h);
        return -1;
    }
    RARSetCallback(h, callback, (LPARAM)&ctx);

    RARHeaderDataEx header;
    int rc;
    for (;;) {
        // wajib: RedirName/Reserved harus 0 sebelum tiap RARReadHeaderEx
        memset(&header, 0, sizeof(header));
        if ((rc = RARReadHeaderEx(h, &header)) != 0) break;
        std::string name(header.FileName);
        int matched = -1;

        std::string dest;
        if ((header.Flags & RHDF_DIRECTORY) == 0) {
            for (size_t i = 0; i < wanted.size(); i++) {
                if (wanted[i].empty()) continue;
                if (wanted[i][0] == '*') {
                    // wildcard ekstensi, mis. "*.rar" -> ambil nama file saja
                    if (endsWith(name, wanted[i].substr(1))) {
                        matched = (int)i;
                        dest = baseName(name);
                        break;
                    }
                } else if (endsWith(name, wanted[i])) {
                    // nama tujuan = suffix-nya (buang awalan "GTA SAN ANDREAS FIXED/")
                    matched = (int)i;
                    dest = wanted[i];
                    break;
                }
            }
        }

        if (matched >= 0) {
            std::string full = ctx.outDir + "/" + dest;
            size_t slash = full.find_last_of('/');
            if (slash != std::string::npos) mkdirs(full.substr(0, slash));

            // unrar: kalau DestName diisi, dia dipakai sebagai PATH LENGKAP
            // (ExtrPath diabaikan) -> beri path absolut.
            int op = RARProcessFile(h, RAR_EXTRACT, nullptr,
                                    const_cast<char *>(full.c_str()));
            if (op == 0) {
                ctx.files++;
                if (ctx.matchFlags) ctx.matchFlags[matched] = true;
            }
        } else {
            RARProcessFile(h, RAR_SKIP, nullptr, nullptr);
        }
    }

    RARCloseArchive(h);
    return ctx.files;
}

#ifndef SAMPUNRAR_HOST_TEST
// ------------------------------------------------------------------ JNI -----

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_sampwrap_app_RarNative_extractNative(
        JNIEnv *env, jclass, jstring jRar, jstring jOut, jobjectArray jWanted,
        jobject cb) {

    const char *rar = env->GetStringUTFChars(jRar, nullptr);
    const char *out = env->GetStringUTFChars(jOut, nullptr);

    std::vector<std::string> wanted;
    jsize n = env->GetArrayLength(jWanted);
    for (jsize i = 0; i < n; i++) {
        jstring s = (jstring)env->GetObjectArrayElement(jWanted, i);
        const char *u = env->GetStringUTFChars(s, nullptr);
        wanted.push_back(std::string(u));
        env->ReleaseStringUTFChars(s, u);
        env->DeleteLocalRef(s);
    }

    Ctx ctx;
    if (cb) {
        jclass cls = env->GetObjectClass(cb);
        jmethodID mid = env->GetMethodID(cls, "onData", "(Ljava/lang/String;JJ)V");
        ctx.env = env;
        ctx.callback = cb;
        ctx.onData = mid;
    }

    int files = extractSelected(rar, out, wanted, ctx);

    env->ReleaseStringUTFChars(jRar, rar);
    env->ReleaseStringUTFChars(jOut, out);

    jlongArray res = env->NewLongArray(2);
    jlong vals[2] = {(jlong)files, (jlong)ctx.bytes};
    env->SetLongArrayRegion(res, 0, 2, vals);
    return res;
}
#endif
