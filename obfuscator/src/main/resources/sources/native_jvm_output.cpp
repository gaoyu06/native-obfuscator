#include "native_jvm.hpp"
#include "native_jvm_output.hpp"
#include "sdk/native_primitives.hpp"
#include "sdk/native_strings.hpp"
#include "string_pool.hpp"

$includes

namespace native_jvm {

    typedef void (* reg_method)(JNIEnv *,jclass);

    reg_method reg_methods[$class_count];

    void register_for_class(JNIEnv *env, jclass, jint id, jclass clazz) {
        reg_methods[id](env, clazz);
    }

    bool prepare_lib(JNIEnv *env) {
        utils::init_utils(env);
        if (env->ExceptionCheck())
            return false;

        if (!native_obfuscator::sdk::register_natives(env))
            return false;

        if (!native_obfuscator::sdk::register_native_strings(env))
            return false;

        char* string_pool = string_pool::get_pool();

$register_code

        if (env->ExceptionCheck())
            return false;

        char method_name[] = "registerNativesForClass";
        char method_desc[] = "(ILjava/lang/Class;)V";
        JNINativeMethod loader_methods[] = {
            { (char *) method_name, (char *) method_desc, (void *)&register_for_class }
        };
        jclass loader_class = env->FindClass("$native_dir/Loader");
        if (loader_class == nullptr || env->ExceptionCheck())
            return false;
        jint result = env->RegisterNatives(loader_class, loader_methods, 1);
        env->DeleteLocalRef(loader_class);
        return result == JNI_OK && !env->ExceptionCheck();
    }
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **)&env, JNI_VERSION_1_8) != JNI_OK || env == nullptr)
        return JNI_ERR;
    if (!native_jvm::prepare_lib(env))
        return JNI_ERR;
    return JNI_VERSION_1_8;
}