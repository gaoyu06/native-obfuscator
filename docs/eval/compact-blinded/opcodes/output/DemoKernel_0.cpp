#include "../native_jvm.hpp"
#include "../native_jvm_interp.hpp"
#include "../string_pool.hpp"
#include "DemoKernel_0.hpp"

// DemoKernel
namespace native_jvm::classes::__ngen_DemoKernel_0 {

    char *string_pool;


    static const std::uint8_t __ngen_b_0_1[] = {
        0x31, 0x00, 0x00, 0x31, 0x01, 0x00, 0x6b, 0x67
    };
    static const native_jvm::interp::method_desc __ngen_d_0_1 = { native_jvm::interp::ISA_VERSION, 2, 2, __ngen_b_0_1, static_cast<std::uint32_t>(sizeof(__ngen_b_0_1)) };
    
    jint JNICALL __ngen_i_0_1(JNIEnv *env, jclass clazz, jint arg0, jint arg1) {
        (void) clazz;
        std::int32_t interp_stack[2] = {};
        std::int32_t interp_locals[2] = {};
        interp_locals[0] = static_cast<std::int32_t>(arg0);
        interp_locals[1] = static_cast<std::int32_t>(arg1);
        native_jvm::interp::frame interp_frame = { interp_locals, interp_stack };
        std::int32_t interp_result = 0;
        if (!native_jvm::interp::execute_i(__ngen_d_0_1, interp_frame, &interp_result)) {
            env->FatalError("invalid native_jvm interpreter stream");
            return (jint) 0;
        }
        return static_cast<jint>(interp_result);
    }
    
    static const std::uint8_t __ngen_b_0_2[] = {
        0xa7, 0x00, 0x00, 0x00, 0x00, 0xd4, 0x01, 0x00, 0xa7, 0x00, 0x00, 0x00, 0x00, 0xd4, 0x02, 0x00,
        0x31, 0x02, 0x00, 0x31, 0x00, 0x00, 0x83, 0x36, 0x00, 0x00, 0x00, 0x31, 0x01, 0x00, 0x31, 0x02,
        0x00, 0x6b, 0xd4, 0x01, 0x00, 0x31, 0x02, 0x00, 0xa7, 0x01, 0x00, 0x00, 0x00, 0x6b, 0xd4, 0x02,
        0x00, 0xae, 0x10, 0x00, 0x00, 0x00, 0x31, 0x01, 0x00, 0x67
    };
    static const native_jvm::interp::method_desc __ngen_d_0_2 = { native_jvm::interp::ISA_VERSION, 4, 3, __ngen_b_0_2, static_cast<std::uint32_t>(sizeof(__ngen_b_0_2)) };
    
    jint JNICALL __ngen_i_0_2(JNIEnv *env, jclass clazz, jint arg0) {
        (void) clazz;
        std::int32_t interp_stack[4] = {};
        std::int32_t interp_locals[3] = {};
        interp_locals[0] = static_cast<std::int32_t>(arg0);
        native_jvm::interp::frame interp_frame = { interp_locals, interp_stack };
        std::int32_t interp_result = 0;
        if (!native_jvm::interp::execute_i(__ngen_d_0_2, interp_frame, &interp_result)) {
            env->FatalError("invalid native_jvm interpreter stream");
            return (jint) 0;
        }
        return static_cast<jint>(interp_result);
    }
    
    static const std::uint8_t __ngen_b_0_3[] = {
        0x31, 0x00, 0x00, 0xa7, 0xb9, 0x79, 0x37, 0x9e, 0xf8, 0xd4, 0x02, 0x00, 0xa7, 0x00, 0x00, 0x00,
        0x00, 0xd4, 0x03, 0x00, 0x31, 0x03, 0x00, 0x31, 0x01, 0x00, 0x83, 0x66, 0x00, 0x00, 0x00, 0x31,
        0x02, 0x00, 0x31, 0x02, 0x00, 0xa7, 0x06, 0x00, 0x00, 0x00, 0x21, 0x31, 0x02, 0x00, 0xa7, 0x02,
        0x00, 0x00, 0x00, 0x95, 0x6b, 0x6b, 0xd4, 0x02, 0x00, 0x31, 0x02, 0x00, 0x31, 0x02, 0x00, 0xa7,
        0x77, 0xca, 0xeb, 0x85, 0x3c, 0xf8, 0xd4, 0x02, 0x00, 0x31, 0x02, 0x00, 0xa7, 0x0d, 0x00, 0x00,
        0x00, 0xca, 0xd4, 0x02, 0x00, 0x31, 0x03, 0x00, 0xa7, 0x01, 0x00, 0x00, 0x00, 0x6b, 0xd4, 0x03,
        0x00, 0xae, 0x14, 0x00, 0x00, 0x00, 0x31, 0x02, 0x00, 0x67
    };
    static const native_jvm::interp::method_desc __ngen_d_0_3 = { native_jvm::interp::ISA_VERSION, 6, 4, __ngen_b_0_3, static_cast<std::uint32_t>(sizeof(__ngen_b_0_3)) };
    
    jint JNICALL __ngen_i_0_3(JNIEnv *env, jclass clazz, jint arg0, jint arg1) {
        (void) clazz;
        std::int32_t interp_stack[6] = {};
        std::int32_t interp_locals[4] = {};
        interp_locals[0] = static_cast<std::int32_t>(arg0);
        interp_locals[1] = static_cast<std::int32_t>(arg1);
        native_jvm::interp::frame interp_frame = { interp_locals, interp_stack };
        std::int32_t interp_result = 0;
        if (!native_jvm::interp::execute_i(__ngen_d_0_3, interp_frame, &interp_result)) {
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
            { ((char *)(string_pool + 0LL)), ((char *)(string_pool + 4LL)), (void *)&__ngen_i_0_1 },
            { ((char *)(string_pool + 10LL)), ((char *)(string_pool + 16LL)), (void *)&__ngen_i_0_2 },
            { ((char *)(string_pool + 21LL)), ((char *)(string_pool + 4LL)), (void *)&__ngen_i_0_3 },
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