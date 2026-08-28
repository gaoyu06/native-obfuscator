#include "../native_jvm.hpp"
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
    jint JNICALL __ngen_native_add1(JNIEnv *env, jclass clazz, jint arg0, jint arg1) {
        jobject classloader = utils::get_classloader_from_class(env, clazz);
        if (env->ExceptionCheck()) { return (jint) 0; }
        if (classloader == nullptr) { env->FatalError(((char *)(string_pool + 10LL))); return (jint) 0; }
    
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
        // IADD; Stack: 2
        cstack0.i = cstack0.i + cstack1.i;
        // New stack: 1
        // IRETURN; Stack: 1
        return (jint) cstack0.i;
        // New stack: 0
        return (jint) 0;
    }
    
    // sumTo(I)I
    jint JNICALL __ngen_native_sumTo2(JNIEnv *env, jclass clazz, jint arg0) {
        jobject classloader = utils::get_classloader_from_class(env, clazz);
        if (env->ExceptionCheck()) { return (jint) 0; }
        if (classloader == nullptr) { env->FatalError(((char *)(string_pool + 10LL))); return (jint) 0; }
    
        jobject lookup = nullptr;
        jvalue cstack0 = {}, cstack1 = {};
        jvalue clocal0 = {}, clocal1 = {}, clocal2 = {};
        std::unordered_set<jobject> refs;
    
        clocal0.i = arg0;
    
        // ICONST_0; Stack: 0
        cstack0.i = 0;
        // New stack: 1
        // ISTORE 1; Stack: 1
        clocal1.i = cstack0.i;
        // New stack: 0
        // ICONST_0; Stack: 0
        cstack0.i = 0;
        // New stack: 1
        // ISTORE 2; Stack: 1
        clocal2.i = cstack0.i;
        // New stack: 0
        // LABEL L1; Stack: 0
        L1: if (env->ExceptionCheck()) { return (jint) 0; }
        // New stack: 0
        // FRAME APPEND L: [1, 1] S: null; Stack: 0
        utils::clear_refs(env, refs);
        // New stack: 0
        // ILOAD 2; Stack: 0
        cstack0.i = clocal2.i;
        // New stack: 1
        // ILOAD 0; Stack: 1
        cstack1.i = clocal0.i;
        // New stack: 2
        // IF_ICMPGE L2; Stack: 2
        if (cstack0.i >= cstack1.i) goto L2;
        // New stack: 0
        // ILOAD 1; Stack: 0
        cstack0.i = clocal1.i;
        // New stack: 1
        // ILOAD 2; Stack: 1
        cstack1.i = clocal2.i;
        // New stack: 2
        // IADD; Stack: 2
        cstack0.i = cstack0.i + cstack1.i;
        // New stack: 1
        // ISTORE 1; Stack: 1
        clocal1.i = cstack0.i;
        // New stack: 0
        // IINC 2 1; Stack: 0
        clocal2.i += 1;
        // New stack: 0
        // GOTO L1; Stack: 0
        goto L1;
        // New stack: 0
        // LABEL L2; Stack: 0
        L2: if (env->ExceptionCheck()) { return (jint) 0; }
        // New stack: 0
        // FRAME SAME L: null S: null; Stack: 0
        utils::clear_refs(env, refs);
        // New stack: 0
        // ILOAD 1; Stack: 0
        cstack0.i = clocal1.i;
        // New stack: 1
        // IRETURN; Stack: 1
        return (jint) cstack0.i;
        // New stack: 0
        return (jint) 0;
    }
    
    // mix(II)I
    jint JNICALL __ngen_native_mix3(JNIEnv *env, jclass clazz, jint arg0, jint arg1) {
        jobject classloader = utils::get_classloader_from_class(env, clazz);
        if (env->ExceptionCheck()) { return (jint) 0; }
        if (classloader == nullptr) { env->FatalError(((char *)(string_pool + 10LL))); return (jint) 0; }
    
        jobject lookup = nullptr;
        jvalue cstack0 = {}, cstack1 = {}, cstack2 = {}, cstack3 = {};
        jvalue clocal0 = {}, clocal1 = {}, clocal2 = {}, clocal3 = {};
        std::unordered_set<jobject> refs;
    
        clocal0.i = arg0;
        clocal1.i = arg1;
    
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
        // ICONST_0; Stack: 0
        cstack0.i = 0;
        // New stack: 1
        // ISTORE 3; Stack: 1
        clocal3.i = cstack0.i;
        // New stack: 0
        // LABEL L1; Stack: 0
        L1: if (env->ExceptionCheck()) { return (jint) 0; }
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
        // IF_ICMPGE L2; Stack: 2
        if (cstack0.i >= cstack1.i) goto L2;
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
        // IINC 3 1; Stack: 0
        clocal3.i += 1;
        // New stack: 0
        // GOTO L1; Stack: 0
        goto L1;
        // New stack: 0
        // LABEL L2; Stack: 0
        L2: if (env->ExceptionCheck()) { return (jint) 0; }
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
    
    // divide(II)I
    jint JNICALL __ngen_native_divide4(JNIEnv *env, jclass clazz, jint arg0, jint arg1) {
        jobject classloader = utils::get_classloader_from_class(env, clazz);
        if (env->ExceptionCheck()) { return (jint) 0; }
        if (classloader == nullptr) { env->FatalError(((char *)(string_pool + 10LL))); return (jint) 0; }
    
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
        if (cstack1.i == -1 && cstack0.i == ((jint) 2147483648U)) { } else { if (cstack1.i == 0) { utils::throw_re(env, ((char *)(string_pool + 63LL)), ((char *)(string_pool + 93LL)), -1); 
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
        if (classloader == nullptr) { env->FatalError(((char *)(string_pool + 10LL))); return (void) 0; }
    
        jobject lookup = nullptr;
        std::unordered_set<jobject> refs;
    
    
        return (void) 0;
    }
    
    
    void __ngen_register_methods(JNIEnv *env, jclass clazz) {
        string_pool = string_pool::get_pool();

        if (jstring str = env->NewStringUTF(((char *)(string_pool + 105LL)))) { if (jstring int_str = utils::get_interned(env, str)) { cstrings[0] = (jstring) env->NewGlobalRef(int_str); env->DeleteLocalRef(str); env->DeleteLocalRef(int_str); } }

        JNINativeMethod __ngen_methods[] = {
            { ((char *)(string_pool + 0LL)), ((char *)(string_pool + 4LL)), (void *)&__ngen_native_add1 },
            { ((char *)(string_pool + 30LL)), ((char *)(string_pool + 36LL)), (void *)&__ngen_native_sumTo2 },
            { ((char *)(string_pool + 41LL)), ((char *)(string_pool + 4LL)), (void *)&__ngen_native_mix3 },
            { ((char *)(string_pool + 56LL)), ((char *)(string_pool + 4LL)), (void *)&__ngen_native_divide4 },
        };

        if (clazz) env->RegisterNatives(clazz, __ngen_methods, sizeof(__ngen_methods) / sizeof(__ngen_methods[0]));
        if (env->ExceptionCheck()) { fprintf(stderr, "Exception occured while registering native_jvm for %s\n", ((char *)(string_pool + 123LL))); fflush(stderr); env->ExceptionDescribe(); env->ExceptionClear(); }

        {
            jclass hidden_class = env->FindClass(((char *)(string_pool + 134LL)));
            JNINativeMethod __ngen_hidden_methods[] = {
                { ((char *)(string_pool + 157LL)), ((char *)(string_pool + 177LL)), (void *)&__ngen_special_clinit_0_5 },
            };
            if (hidden_class) env->RegisterNatives(hidden_class, __ngen_hidden_methods, sizeof(__ngen_hidden_methods) / sizeof(__ngen_hidden_methods[0]));
            if (env->ExceptionCheck()) { fprintf(stderr, "Exception occured while registering native_jvm for %s\n", ((char *)(string_pool + 198LL))); fflush(stderr); env->ExceptionDescribe(); env->ExceptionClear(); }
            env->DeleteLocalRef(hidden_class);
        }
    }
}