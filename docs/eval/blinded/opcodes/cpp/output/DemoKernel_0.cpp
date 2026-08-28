#include "../native_jvm.hpp"
#include "../native_jvm_interp.hpp"
#include "../string_pool.hpp"
#include "DemoKernel_0.hpp"

// DemoKernel
namespace native_jvm::classes::__ngen_DemoKernel_0 {

    char *string_pool;


    // add(II)I
    static const std::uint8_t __ngen_native_add1_interp_code[] = { 2, 0, 0, 2, 1, 0, 4, 19 };
    static const native_jvm::interp::method_desc __ngen_native_add1_interp_method = { native_jvm::interp::ISA_VERSION, 2, 2, __ngen_native_add1_interp_code, static_cast<std::uint32_t>(sizeof(__ngen_native_add1_interp_code)) };
    
    jint JNICALL __ngen_native_add1(JNIEnv *env, jclass clazz, jint arg0, jint arg1) {
        (void) clazz;
        std::int32_t interp_stack[2] = {};
        std::int32_t interp_locals[2] = {};
        interp_locals[0] = static_cast<std::int32_t>(arg0);
        interp_locals[1] = static_cast<std::int32_t>(arg1);
        native_jvm::interp::frame interp_frame = { interp_locals, interp_stack };
        std::int32_t interp_result = 0;
        if (!native_jvm::interp::execute_i(__ngen_native_add1_interp_method, interp_frame, &interp_result)) {
            env->FatalError("invalid native_jvm interpreter stream");
            return (jint) 0;
        }
        return static_cast<jint>(interp_result);
    }
    
    // sumTo(I)I
    static const std::uint8_t __ngen_native_sumTo2_interp_code[] = { 1, 0, 0, 0, 0, 3, 1, 0, 1, 0, 0, 0, 0, 3, 2, 0, 2, 2, 0, 2, 0, 0, 15, 54, 0, 0, 0, 2, 1, 0, 2, 2, 0, 4, 3, 1, 0, 2, 2, 0, 1, 1, 0, 0, 0, 4, 3, 2, 0, 18, 16, 0, 0, 0, 2, 1, 0, 19 };
    static const native_jvm::interp::method_desc __ngen_native_sumTo2_interp_method = { native_jvm::interp::ISA_VERSION, 4, 3, __ngen_native_sumTo2_interp_code, static_cast<std::uint32_t>(sizeof(__ngen_native_sumTo2_interp_code)) };
    
    jint JNICALL __ngen_native_sumTo2(JNIEnv *env, jclass clazz, jint arg0) {
        (void) clazz;
        std::int32_t interp_stack[4] = {};
        std::int32_t interp_locals[3] = {};
        interp_locals[0] = static_cast<std::int32_t>(arg0);
        native_jvm::interp::frame interp_frame = { interp_locals, interp_stack };
        std::int32_t interp_result = 0;
        if (!native_jvm::interp::execute_i(__ngen_native_sumTo2_interp_method, interp_frame, &interp_result)) {
            env->FatalError("invalid native_jvm interpreter stream");
            return (jint) 0;
        }
        return static_cast<jint>(interp_result);
    }
    
    // mix(II)I
    static const std::uint8_t __ngen_native_mix3_interp_code[] = { 2, 0, 0, 1, 185, 121, 55, 158, 21, 3, 2, 0, 1, 0, 0, 0, 0, 3, 3, 0, 2, 3, 0, 2, 1, 0, 15, 102, 0, 0, 0, 2, 2, 0, 2, 2, 0, 1, 6, 0, 0, 0, 22, 2, 2, 0, 1, 2, 0, 0, 0, 23, 4, 4, 3, 2, 0, 2, 2, 0, 2, 2, 0, 1, 119, 202, 235, 133, 20, 21, 3, 2, 0, 2, 2, 0, 1, 13, 0, 0, 0, 24, 3, 2, 0, 2, 3, 0, 1, 1, 0, 0, 0, 4, 3, 3, 0, 18, 20, 0, 0, 0, 2, 2, 0, 19 };
    static const native_jvm::interp::method_desc __ngen_native_mix3_interp_method = { native_jvm::interp::ISA_VERSION, 6, 4, __ngen_native_mix3_interp_code, static_cast<std::uint32_t>(sizeof(__ngen_native_mix3_interp_code)) };
    
    jint JNICALL __ngen_native_mix3(JNIEnv *env, jclass clazz, jint arg0, jint arg1) {
        (void) clazz;
        std::int32_t interp_stack[6] = {};
        std::int32_t interp_locals[4] = {};
        interp_locals[0] = static_cast<std::int32_t>(arg0);
        interp_locals[1] = static_cast<std::int32_t>(arg1);
        native_jvm::interp::frame interp_frame = { interp_locals, interp_stack };
        std::int32_t interp_result = 0;
        if (!native_jvm::interp::execute_i(__ngen_native_mix3_interp_method, interp_frame, &interp_result)) {
            env->FatalError("invalid native_jvm interpreter stream");
            return (jint) 0;
        }
        return static_cast<jint>(interp_result);
    }
    
    // divide(II)I
    jint JNICALL __ngen_native_divide4(JNIEnv *env, jclass clazz, jint arg0, jint arg1) {
        jobject classloader = utils::get_classloader_from_class(env, clazz);
        if (env->ExceptionCheck()) { return (jint) 0; }
        if (classloader == nullptr) { env->FatalError(((char *)(string_pool + 32LL))); return (jint) 0; }
    
        jobject lookup = nullptr;
        jvalue cstack0 = {}, cstack1 = {};
        jvalue clocal0 = {}, clocal1 = {};
        std::unordered_set<jobject> refs;
    
        clocal0.i = arg0;
        clocal1.i = arg1;
    
        // ILOAD 0; Stack: 0
        cstack0.i = clocal0.i;
        // New stack: 1
        // ILOAD 1; Stack: 1
        cstack1.i = clocal1.i;
        // New stack: 2
        // IDIV; Stack: 2
        if (cstack1.i == -1 && cstack0.i == ((jint) 2147483648U)) { } else { if (cstack1.i == 0) { utils::throw_re(env, ((char *)(string_pool + 52LL)), ((char *)(string_pool + 82LL)), -1); 
        if (env->ExceptionCheck()) { return (jint) 0; } } else { cstack0.i = cstack0.i / cstack1.i; } }
        // New stack: 1
        // IRETURN; Stack: 1
        return (jint) cstack0.i;
        // New stack: 0
        return (jint) 0;
    }
    
    // <clinit>()V
    void JNICALL __ngen_special_clinit_0_5(JNIEnv *env, jobject ignored_hidden, jclass clazz) {
        env->DeleteLocalRef(ignored_hidden);
        jobject classloader = utils::get_classloader_from_class(env, clazz);
        if (env->ExceptionCheck()) { return (void) 0; }
        if (classloader == nullptr) { env->FatalError(((char *)(string_pool + 32LL))); return (void) 0; }
    
        jobject lookup = nullptr;
        std::unordered_set<jobject> refs;
    
    
        return (void) 0;
    }
    
    
    void __ngen_register_methods(JNIEnv *env, jclass clazz) {
        string_pool = string_pool::get_pool();

        JNINativeMethod __ngen_methods[] = {
            { ((char *)(string_pool + 0LL)), ((char *)(string_pool + 4LL)), (void *)&__ngen_native_add1 },
            { ((char *)(string_pool + 10LL)), ((char *)(string_pool + 16LL)), (void *)&__ngen_native_sumTo2 },
            { ((char *)(string_pool + 21LL)), ((char *)(string_pool + 4LL)), (void *)&__ngen_native_mix3 },
            { ((char *)(string_pool + 25LL)), ((char *)(string_pool + 4LL)), (void *)&__ngen_native_divide4 },
        };

        if (clazz) env->RegisterNatives(clazz, __ngen_methods, sizeof(__ngen_methods) / sizeof(__ngen_methods[0]));
        if (env->ExceptionCheck()) { fprintf(stderr, "Exception occured while registering native_jvm for %s\n", ((char *)(string_pool + 94LL))); fflush(stderr); env->ExceptionDescribe(); env->ExceptionClear(); }

        {
            jclass hidden_class = env->FindClass(((char *)(string_pool + 105LL)));
            JNINativeMethod __ngen_hidden_methods[] = {
                { ((char *)(string_pool + 128LL)), ((char *)(string_pool + 148LL)), (void *)&__ngen_special_clinit_0_5 },
            };
            if (hidden_class) env->RegisterNatives(hidden_class, __ngen_hidden_methods, sizeof(__ngen_hidden_methods) / sizeof(__ngen_hidden_methods[0]));
            if (env->ExceptionCheck()) { fprintf(stderr, "Exception occured while registering native_jvm for %s\n", ((char *)(string_pool + 169LL))); fflush(stderr); env->ExceptionDescribe(); env->ExceptionClear(); }
            env->DeleteLocalRef(hidden_class);
        }
    }
}