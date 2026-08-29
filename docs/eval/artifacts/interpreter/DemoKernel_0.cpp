#include "../native_jvm.hpp"
#include "../native_jvm_interp.hpp"
#include "../string_pool.hpp"
#include "DemoKernel_0.hpp"

// DemoKernel
namespace native_jvm::classes::__ngen_DemoKernel_0 {

    char *string_pool;

    jstring cstrings[1];
    std::mutex cclasses_mtx[1];
    jclass cclasses[1];
    jmethodID cmethods[1];

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
    jint JNICALL __ngen_native_mix3(JNIEnv *env, jclass clazz, jint arg0, jint arg1) {
        jobject classloader = utils::get_classloader_from_class(env, clazz);
        if (env->ExceptionCheck()) { return (jint) 0; }
        if (classloader == nullptr) { env->FatalError(((char *)(string_pool + 25LL))); return (jint) 0; }
    
        jobject lookup = nullptr;
        jvalue cstack0 = {}, cstack1 = {}, cstack2 = {}, cstack3 = {};
        jvalue clocal0 = {}, clocal1 = {}, clocal2 = {}, clocal3 = {};
        std::unordered_set<jobject> refs;
    
        clocal0.i = arg0;
        clocal1.i = arg1;
    
        // LABEL L1; Stack: 0
        L1: if (env->ExceptionCheck()) { return (jint) 0; }
        // New stack: 0
        // Line 18; Stack: 0
        // New stack: 0
        // ILOAD 0; Stack: 0
        cstack0.i = clocal0.i;
        // New stack: 1
        // LDC -1640531527; Stack: 1
        cstack1.i = -1640531527;
        // New stack: 2
        // IXOR; Stack: 2
        cstack0.i = cstack0.i ^ cstack1.i;
        // New stack: 1
        // ISTORE 2; Stack: 1
        clocal2.i = cstack0.i;
        // New stack: 0
        // LABEL L2; Stack: 0
        L2: if (env->ExceptionCheck()) { return (jint) 0; }
        // New stack: 0
        // Line 19; Stack: 0
        // New stack: 0
        // ICONST_0; Stack: 0
        cstack0.i = 0;
        // New stack: 1
        // ISTORE 3; Stack: 1
        clocal3.i = cstack0.i;
        // New stack: 0
        // LABEL L3; Stack: 0
        L3: if (env->ExceptionCheck()) { return (jint) 0; }
        // New stack: 0
        // FRAME APPEND L: [1, 1] S: null; Stack: 0
        utils::clear_refs(env, refs);
        // New stack: 0
        // ILOAD 3; Stack: 0
        cstack0.i = clocal3.i;
        // New stack: 1
        // ILOAD 1; Stack: 1
        cstack1.i = clocal1.i;
        // New stack: 2
        // IF_ICMPGE L4; Stack: 2
        if (cstack0.i >= cstack1.i) goto L4;
        // New stack: 0
        // LABEL L5; Stack: 0
        L5: if (env->ExceptionCheck()) { return (jint) 0; }
        // New stack: 0
        // Line 20; Stack: 0
        // New stack: 0
        // ILOAD 2; Stack: 0
        cstack0.i = clocal2.i;
        // New stack: 1
        // ILOAD 2; Stack: 1
        cstack1.i = clocal2.i;
        // New stack: 2
        // BIPUSH 6; Stack: 2
        cstack2.i = (jint) 6;
        // New stack: 3
        // ISHL; Stack: 3
        cstack1.i = cstack1.i << (0x1f & cstack2.i);
        // New stack: 2
        // ILOAD 2; Stack: 2
        cstack2.i = clocal2.i;
        // New stack: 3
        // ICONST_2; Stack: 3
        cstack3.i = 2;
        // New stack: 4
        // IUSHR; Stack: 4
        cstack2.i = (jint) (((uint32_t) cstack2.i) >> (((uint32_t) cstack3.i) & 0x1f));
        // New stack: 3
        // IADD; Stack: 3
        cstack1.i = cstack1.i + cstack2.i;
        // New stack: 2
        // IADD; Stack: 2
        cstack0.i = cstack0.i + cstack1.i;
        // New stack: 1
        // ISTORE 2; Stack: 1
        clocal2.i = cstack0.i;
        // New stack: 0
        // LABEL L6; Stack: 0
        L6: if (env->ExceptionCheck()) { return (jint) 0; }
        // New stack: 0
        // Line 21; Stack: 0
        // New stack: 0
        // ILOAD 2; Stack: 0
        cstack0.i = clocal2.i;
        // New stack: 1
        // ILOAD 2; Stack: 1
        cstack1.i = clocal2.i;
        // New stack: 2
        // LDC -2048144777; Stack: 2
        cstack2.i = -2048144777;
        // New stack: 3
        // IMUL; Stack: 3
        cstack1.i = cstack1.i * cstack2.i;
        // New stack: 2
        // IXOR; Stack: 2
        cstack0.i = cstack0.i ^ cstack1.i;
        // New stack: 1
        // ISTORE 2; Stack: 1
        clocal2.i = cstack0.i;
        // New stack: 0
        // LABEL L7; Stack: 0
        L7: if (env->ExceptionCheck()) { return (jint) 0; }
        // New stack: 0
        // Line 22; Stack: 0
        // New stack: 0
        // ILOAD 2; Stack: 0
        cstack0.i = clocal2.i;
        // New stack: 1
        // BIPUSH 13; Stack: 1
        cstack1.i = (jint) 13;
        // New stack: 2
        // INVOKESTATIC java/lang/Integer.rotateLeft(II)I; Stack: 2
        if (!cclasses[0] || env->IsSameObject(cclasses[0], NULL)) { cclasses_mtx[0].lock(); if (!cclasses[0] || env->IsSameObject(cclasses[0], NULL)) { if (jclass clazz = utils::find_class_wo_static(env, classloader, (cstrings[0]))) { cclasses[0] = (jclass) env->NewWeakGlobalRef(clazz); env->DeleteLocalRef(clazz); } } cclasses_mtx[0].unlock(); if (env->ExceptionCheck()) { return (jint) 0; } } if (!cmethods[0]) { cmethods[0] = env->GetStaticMethodID((cclasses[0]), ((char *)(string_pool + 45LL)), ((char *)(string_pool + 4LL))); if (env->ExceptionCheck()) { return (jint) 0; }  } cstack0.i = env->CallStaticIntMethod((cclasses[0]), (cmethods[0]), cstack0.i, cstack1.i); 
        if (env->ExceptionCheck()) { return (jint) 0; }
        // New stack: 1
        // ISTORE 2; Stack: 1
        clocal2.i = cstack0.i;
        // New stack: 0
        // LABEL L8; Stack: 0
        L8: if (env->ExceptionCheck()) { return (jint) 0; }
        // New stack: 0
        // Line 19; Stack: 0
        // New stack: 0
        // IINC 3 1; Stack: 0
        clocal3.i += 1;
        // New stack: 0
        // GOTO L3; Stack: 0
        goto L3;
        // New stack: 0
        // LABEL L4; Stack: 0
        L4: if (env->ExceptionCheck()) { return (jint) 0; }
        // New stack: 0
        // Line 24; Stack: 0
        // New stack: 0
        // FRAME SAME L: null S: null; Stack: 0
        utils::clear_refs(env, refs);
        // New stack: 0
        // ILOAD 2; Stack: 0
        cstack0.i = clocal2.i;
        // New stack: 1
        // IRETURN; Stack: 1
        return (jint) cstack0.i;
        // New stack: 0
        return (jint) 0;
    }
    
    // <clinit>()V
    void JNICALL __ngen_special_clinit_0_4(JNIEnv *env, jobject ignored_hidden, jclass clazz) {
        env->DeleteLocalRef(ignored_hidden);
        jobject classloader = utils::get_classloader_from_class(env, clazz);
        if (env->ExceptionCheck()) { return (void) 0; }
        if (classloader == nullptr) { env->FatalError(((char *)(string_pool + 25LL))); return (void) 0; }
    
        jobject lookup = nullptr;
        std::unordered_set<jobject> refs;
    
    
        return (void) 0;
    }
    
    
    void __ngen_register_methods(JNIEnv *env, jclass clazz) {
        string_pool = string_pool::get_pool();

        if (jstring str = env->NewStringUTF(((char *)(string_pool + 56LL)))) { if (jstring int_str = utils::get_interned(env, str)) { cstrings[0] = (jstring) env->NewGlobalRef(int_str); env->DeleteLocalRef(str); env->DeleteLocalRef(int_str); } }

        JNINativeMethod __ngen_methods[] = {
            { ((char *)(string_pool + 0LL)), ((char *)(string_pool + 4LL)), (void *)&__ngen_native_add1 },
            { ((char *)(string_pool + 10LL)), ((char *)(string_pool + 16LL)), (void *)&__ngen_native_sumTo2 },
            { ((char *)(string_pool + 21LL)), ((char *)(string_pool + 4LL)), (void *)&__ngen_native_mix3 },
        };

        if (clazz) env->RegisterNatives(clazz, __ngen_methods, sizeof(__ngen_methods) / sizeof(__ngen_methods[0]));
        if (env->ExceptionCheck()) { fprintf(stderr, "Exception occured while registering native_jvm for %s\n", ((char *)(string_pool + 74LL))); fflush(stderr); env->ExceptionDescribe(); env->ExceptionClear(); }

        {
            jclass hidden_class = env->FindClass(((char *)(string_pool + 85LL)));
            JNINativeMethod __ngen_hidden_methods[] = {
                { ((char *)(string_pool + 108LL)), ((char *)(string_pool + 128LL)), (void *)&__ngen_special_clinit_0_4 },
            };
            if (hidden_class) env->RegisterNatives(hidden_class, __ngen_hidden_methods, sizeof(__ngen_hidden_methods) / sizeof(__ngen_hidden_methods[0]));
            if (env->ExceptionCheck()) { fprintf(stderr, "Exception occured while registering native_jvm for %s\n", ((char *)(string_pool + 149LL))); fflush(stderr); env->ExceptionDescribe(); env->ExceptionClear(); }
            env->DeleteLocalRef(hidden_class);
        }
    }
}
