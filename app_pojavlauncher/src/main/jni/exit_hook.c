//
// Created by maks on 15.01.2025.
//

#include <jni.h>
#include <unistd.h>
#include <stdbool.h>
#include <bytehook.h>
#include <dlfcn.h>
#include <android/log.h>
#include <stdlib.h>
#include <errno.h>
#include <pthread.h>
#include "stdio_is.h"

static _Atomic bool exit_tripped = false;

typedef void (*exit_func)(int);

static void custom_exit(int code) {
    // If the exit was already done (meaning it is recursive or from a different thread), pass the call through
    if(exit_tripped) {
        BYTEHOOK_CALL_PREV(custom_exit, exit_func, code);
        BYTEHOOK_POP_STACK();
        return;
    }
    exit_tripped = true;
    // Perform a nominal exit, as we expect.
    nominal_exit(code, false);
    BYTEHOOK_POP_STACK();
}

// Hooks for chmod and fchmod that always return success.
// This allows older Android versions to work with Java NIO zipfs inside of the Pojav folder.
typedef int (*chmod_func)(const char*, mode_t);
typedef int (*fchmod_func)(int, mode_t);

#define TEMPLATE_HOOK(X, Y, Z, W) static int X(Y, mode_t mode) { \
    int result = BYTEHOOK_CALL_PREV(X, Z, W, mode); \
    if(result != 0) errno = 0; \
    BYTEHOOK_POP_STACK(); \
    return 0; \
} \

TEMPLATE_HOOK(custom_chmod, const char* filename, chmod_func, filename)
TEMPLATE_HOOK(custom_fchmod, int fd, fchmod_func, fd)

#undef TEMPLATE_HOOK

static void custom_atexit() {
    // Same as custom_exit, but without the code or the exit passthrough.
    if(exit_tripped) {
        return;
    }
    exit_tripped = true;
    nominal_exit(0, false);
}

typedef bytehook_stub_t (*bytehook_hook_all_t)(const char *callee_path_name, const char *sym_name, void *new_func,
                                               bytehook_hooked_t hooked, void *hooked_arg);

static void create_chmod_hooks(bytehook_hook_all_t bytehook_hook_all_p) {
    bytehook_stub_t stub_chmod = bytehook_hook_all_p(NULL, "chmod", &custom_chmod, NULL, NULL);
    bytehook_stub_t stub_fchmod = bytehook_hook_all_p(NULL, "fchmod", &custom_fchmod, NULL, NULL);
    __android_log_print(ANDROID_LOG_INFO, "chmod_hook", "Successfully initialized chmod hooks, stubs: %p %p", stub_chmod, stub_fchmod);
}

static void create_hooks(bytehook_hook_all_t bytehook_hook_all_p) {
    bytehook_stub_t stub_exit = bytehook_hook_all_p(NULL, "exit", &custom_exit, NULL, NULL);
    __android_log_print(ANDROID_LOG_INFO, "exit_hook", "Successfully initialized exit hook, stub: %p", stub_exit);
    // TODO: figure out proper android version where these should apply
    create_chmod_hooks(bytehook_hook_all_p);
}

static bool init_hooks() {
    void* bytehook_handle = dlopen("libbytehook.so", RTLD_NOW);
    if(bytehook_handle == NULL) {
        goto dlerror;
    }

    bytehook_hook_all_t bytehook_hook_all_p;
    int (*bytehook_init_p)(int mode, bool debug);

    bytehook_hook_all_p = dlsym(bytehook_handle, "bytehook_hook_all");
    bytehook_init_p = dlsym(bytehook_handle, "bytehook_init");

    if(bytehook_hook_all_p == NULL || bytehook_init_p == NULL) {
        goto dlerror;
    }
    int bhook_status = bytehook_init_p(BYTEHOOK_MODE_AUTOMATIC, false);
    if(bhook_status == BYTEHOOK_STATUS_CODE_OK) {
        create_hooks(bytehook_hook_all_p);
        return true;
    } else {
        __android_log_print(ANDROID_LOG_INFO, "exit_hook", "bytehook_init failed (%i)", bhook_status);
        dlclose(bytehook_handle);
        return false;
    }

    dlerror:
    if(bytehook_handle != NULL) dlclose(bytehook_handle);
    __android_log_print(ANDROID_LOG_ERROR, "exit_hook", "Failed to load hook library: %s", dlerror());
    return false;
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_initializeHooks(JNIEnv *env, jclass clazz) {
    bool hooks_ready = init_hooks();
    if(!hooks_ready){
        // If we can't hook, register atexit(). This won't report a proper error code,
        // but it will prevent a SIGSEGV or a SIGABRT from the depths of Dalvik that happens
        // on exit().
        atexit(custom_atexit);
    }
}