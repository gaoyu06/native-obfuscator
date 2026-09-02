#include "native_jvm.hpp"
#include <algorithm>
#include <cstdint>
#include <new>

namespace native_jvm::utils {

    jclass boolean_array_class;
    jclass byte_array_class;
    jclass char_array_class;
    jclass short_array_class;
    jclass int_array_class;
    jclass long_array_class;
    jclass float_array_class;
    jclass double_array_class;
    jclass object_array_class;
    jmethodID string_intern_method;
    jclass class_class;
    jmethodID get_classloader_method;
    jclass object_class;
    jmethodID get_class_method;
    jclass classloader_class;
    jmethodID load_class_method;
    jclass no_class_def_found_class;
    jmethodID ncdf_init_method;
    jclass throwable_class;
    jmethodID get_message_method;
    jmethodID init_cause_method;
    jclass methodhandles_lookup_class;
    jmethodID lookup_init_method;
#ifdef USE_HOTSPOT
    jclass methodhandle_natives_class;
    jmethodID link_call_site_method;
    bool is_jvm11_link_call_site;
#endif

    void init_utils(JNIEnv *env) {
        jclass clazz = env->FindClass("[Z");
        if (env->ExceptionCheck())
            return;
        boolean_array_class = (jclass) env->NewGlobalRef(clazz);
        env->DeleteLocalRef(clazz);

        const char *primitive_array_names[] = {
            "[B", "[C", "[S", "[I", "[J", "[F", "[D", "[Ljava/lang/Object;"
        };
        jclass *primitive_array_slots[] = {
            &byte_array_class, &char_array_class, &short_array_class,
            &int_array_class, &long_array_class, &float_array_class,
            &double_array_class, &object_array_class
        };
        for (int i = 0; i < 8; i++) {
            jclass found = env->FindClass(primitive_array_names[i]);
            if (env->ExceptionCheck()) {
                return;
            }
            *primitive_array_slots[i] = (jclass) env->NewGlobalRef(found);
            env->DeleteLocalRef(found);
        }

        jclass string_clazz = env->FindClass("java/lang/String");
        if (env->ExceptionCheck())
            return;
        string_intern_method = env->GetMethodID(string_clazz, "intern", "()Ljava/lang/String;");
        if (env->ExceptionCheck())
            return;
        env->DeleteLocalRef(string_clazz);

        jclass _class_class = env->FindClass("java/lang/Class");
        if (env->ExceptionCheck())
            return;
        class_class = (jclass) env->NewGlobalRef(_class_class);
        env->DeleteLocalRef(_class_class);

        get_classloader_method = env->GetMethodID(class_class, "getClassLoader", "()Ljava/lang/ClassLoader;");
        if (env->ExceptionCheck())
            return;

        jclass _object_class = env->FindClass("java/lang/Object");
        if (env->ExceptionCheck())
            return;
        object_class = (jclass) env->NewGlobalRef(_object_class);
        env->DeleteLocalRef(_object_class);

        get_class_method = env->GetMethodID(object_class, "getClass", "()Ljava/lang/Class;");
        if (env->ExceptionCheck())
            return;

        jclass _classloader_class = env->FindClass("java/lang/ClassLoader");
        if (env->ExceptionCheck())
            return;
        classloader_class = (jclass) env->NewGlobalRef(_classloader_class);
        env->DeleteLocalRef(_classloader_class);

        load_class_method = env->GetMethodID(classloader_class, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
        if (env->ExceptionCheck())
            return;

        jclass _no_class_def_found_class = env->FindClass("java/lang/NoClassDefFoundError");
        if (env->ExceptionCheck())
            return;
        no_class_def_found_class = (jclass) env->NewGlobalRef(_no_class_def_found_class);
        env->DeleteLocalRef(_no_class_def_found_class);

        ncdf_init_method = env->GetMethodID(no_class_def_found_class, "<init>", "(Ljava/lang/String;)V");
        if (env->ExceptionCheck())
            return;

        jclass _throwable_class = env->FindClass("java/lang/Throwable");
        if (env->ExceptionCheck())
            return;
        throwable_class = (jclass) env->NewGlobalRef(_throwable_class);
        env->DeleteLocalRef(_throwable_class);

        get_message_method = env->GetMethodID(throwable_class, "getMessage", "()Ljava/lang/String;");
        if (env->ExceptionCheck())
            return;

        init_cause_method = env->GetMethodID(throwable_class, "initCause",
                                            "(Ljava/lang/Throwable;)Ljava/lang/Throwable;");
        if (env->ExceptionCheck())
            return;

        jclass _methodhandles_lookup_class = env->FindClass("java/lang/invoke/MethodHandles$Lookup");
        if (env->ExceptionCheck())
            return;
        methodhandles_lookup_class = (jclass) env->NewGlobalRef(_methodhandles_lookup_class);
        env->DeleteLocalRef(_methodhandles_lookup_class);

        lookup_init_method = env->GetMethodID(methodhandles_lookup_class, "<init>", "(Ljava/lang/Class;)V");
        if (env->ExceptionCheck())
            return;

#ifdef USE_HOTSPOT
        jclass _methodhandle_natives_class = env->FindClass("java/lang/invoke/MethodHandleNatives");
        if (env->ExceptionCheck())
            return;
        methodhandle_natives_class = (jclass) env->NewGlobalRef(_methodhandle_natives_class);
        env->DeleteLocalRef(_methodhandle_natives_class);

        link_call_site_method = env->GetStaticMethodID(methodhandle_natives_class, "linkCallSite",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/invoke/MemberName;");
        is_jvm11_link_call_site = false;
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            link_call_site_method = env->GetStaticMethodID(methodhandle_natives_class, "linkCallSite",
                "(Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/invoke/MemberName;");
            is_jvm11_link_call_site = true;
            if (env->ExceptionCheck())
                return;
        }
#endif
    }

#ifdef USE_HOTSPOT
    jobject link_call_site(JNIEnv *env, jobject caller_obj, jobject bootstrap_method_obj,
        jobject name_obj, jobject type_obj, jobject static_arguments, jobject appendix_result) {
        if (is_jvm11_link_call_site) {
            return env->CallStaticObjectMethod(methodhandle_natives_class, link_call_site_method, caller_obj, 0,
                bootstrap_method_obj, name_obj, type_obj, static_arguments, appendix_result);
        }
        return env->CallStaticObjectMethod(methodhandle_natives_class, link_call_site_method, caller_obj,
            bootstrap_method_obj, name_obj, type_obj, static_arguments, appendix_result);
    }
#endif

    template <>
    jarray create_array_value<1>(JNIEnv *env, jint size) {
        return env->NewBooleanArray(size);
    }

    template <>
    jarray create_array_value<2>(JNIEnv *env, jint size) {
        return env->NewCharArray(size);
    }

    template <>
    jarray create_array_value<3>(JNIEnv *env, jint size) {
        return env->NewByteArray(size);
    }

    template <>
    jarray create_array_value<4>(JNIEnv *env, jint size) {
        return env->NewShortArray(size);
    }

    template <>
    jarray create_array_value<5>(JNIEnv *env, jint size) {
        return env->NewIntArray(size);
    }

    template <>
    jarray create_array_value<6>(JNIEnv *env, jint size) {
        return env->NewFloatArray(size);
    }

    template <>
    jarray create_array_value<7>(JNIEnv *env, jint size) {
        return env->NewLongArray(size);
    }

    template <>
    jarray create_array_value<8>(JNIEnv *env, jint size) {
        return env->NewDoubleArray(size);
    }

    jobjectArray create_multidim_array(JNIEnv *env, jobject classloader, jint count, jint required_count,
        const char *class_name, int line, std::initializer_list<jint> sizes, int dim_index) {
        if (required_count == 0) {
            env->FatalError("required_count == 0");
            return nullptr;
        }
        jint current_size = sizes.begin()[dim_index];
        if (current_size < 0) {
            throw_re(env, "java/lang/NegativeArraySizeException", "MULTIANEWARRAY size < 0", line);
            return nullptr;
        }
        jobjectArray result_array = nullptr;
        if (count == 1) {
            std::string renamed_class_name(class_name);
            std::replace(renamed_class_name.begin(), renamed_class_name.end(), '/', '.');
            jstring renamed_class_name_string = env->NewStringUTF(renamed_class_name.c_str());
            jclass clazz = find_class_wo_static(env, classloader, renamed_class_name_string);
            env->DeleteLocalRef(renamed_class_name_string);
            if (env->ExceptionCheck()) {
                return nullptr;
            }
            result_array = env->NewObjectArray(current_size, clazz, nullptr);
            if (env->ExceptionCheck()) {
                return nullptr;
            }
            return result_array;
        }
        std::string clazz_name = std::string(count - 1, '[') + "L" + std::string(class_name) + ";";
        if (jclass clazz = env->FindClass(clazz_name.c_str())) {
            result_array = env->NewObjectArray(current_size, clazz, nullptr);
            if (env->ExceptionCheck()) {
                return nullptr;
            }
            env->DeleteLocalRef(clazz);
        } else {
            return nullptr;
        }

        if (required_count == 1) {
            return result_array;
        }

        for (jint i = 0; i < current_size; i++) {
            jobjectArray inner_array = create_multidim_array(env, classloader, count - 1, required_count - 1,
                class_name, line, sizes, dim_index + 1);
            if (env->ExceptionCheck()) {
                env->DeleteLocalRef(result_array);
                return nullptr;
            }
            env->SetObjectArrayElement(result_array, i, inner_array);
            env->DeleteLocalRef(inner_array);
            if (env->ExceptionCheck()) {
                env->DeleteLocalRef(result_array);
                return nullptr;
            }
        }
        return result_array;
    }

    jclass find_class_wo_static(JNIEnv *env, jobject classloader, jstring class_name_string) {
        jclass clazz = (jclass) env->CallObjectMethod(
            classloader,
            load_class_method,
            class_name_string
        );
        if (env->ExceptionCheck()) {
            jthrowable exception = env->ExceptionOccurred();
            env->ExceptionClear();
            jobject details = env->CallObjectMethod(
                exception,
                get_message_method
            );
            if (env->ExceptionCheck()) {
                env->DeleteLocalRef(exception);
                return nullptr;
            }
            jobject new_exception = env->NewObject(no_class_def_found_class,
                ncdf_init_method,
                details);
            if (env->ExceptionCheck()) {
                env->DeleteLocalRef(exception);
                env->DeleteLocalRef(details);
                return nullptr;
            }
            env->CallVoidMethod(new_exception, init_cause_method, exception);
            if (env->ExceptionCheck()) {
                env->DeleteLocalRef(new_exception);
                env->DeleteLocalRef(exception);
                env->DeleteLocalRef(details);
                return nullptr;
            }
            env->Throw((jthrowable) new_exception);
            env->DeleteLocalRef(exception);
            env->DeleteLocalRef(details);
            return nullptr;
        }
        return clazz;
    }

    void throw_re(JNIEnv *env, const char *exception_class, const char *error, int line) {
        jclass exception_class_ptr = env->FindClass(exception_class);
        if (env->ExceptionCheck()) {
            return;
        }
        env->ThrowNew(exception_class_ptr, ("\"" + std::string(error) + "\" on " + std::to_string(line)).c_str());
        env->DeleteLocalRef(exception_class_ptr);
    }

    void bastore(JNIEnv *env, jarray array, jint index, jint value) {
        if (env->IsInstanceOf(array, boolean_array_class))
            env->SetBooleanArrayRegion((jbooleanArray) array, index, 1, (jboolean*) (&value));
        else
            env->SetByteArrayRegion((jbyteArray) array, index, 1, (jbyte*) (&value));
    }

    jbyte baload(JNIEnv *env, jarray array, jint index) {
        jbyte ret_value;
        if (env->IsInstanceOf(array, boolean_array_class))
            env->GetBooleanArrayRegion((jbooleanArray) array, index, 1, (jboolean*) (&ret_value));
        else
            env->GetByteArrayRegion((jbyteArray) array, index, 1, (jbyte*) (&ret_value));
        return ret_value;
    }

    bool string_hash_code(JNIEnv *env, jstring value, jint *out) {
        const jsize size = env->GetStringLength(value);
        if (env->ExceptionCheck()) {
            return false;
        }
        if (size == 0) {
            *out = 0;
            return true;
        }
        const jchar *chars = env->GetStringCritical(value, nullptr);
        if (chars == nullptr) {
            return false;
        }
        std::uint32_t hash = 0;
        for (jsize index = 0; index < size; ++index) {
            hash = hash * 31U + static_cast<std::uint32_t>(chars[index]);
        }
        env->ReleaseStringCritical(value, chars);
        *out = static_cast<jint>(hash);
        return true;
    }

    bool string_char_at(JNIEnv *env, jstring value, jint index, jint *out) {
        const jsize size = env->GetStringLength(value);
        if (env->ExceptionCheck()) {
            return false;
        }
        if (index < 0 || index >= size) {
            throw_re(env, "java/lang/StringIndexOutOfBoundsException",
                    "String.charAt", 0);
            return false;
        }
        jchar ch = 0;
        env->GetStringRegion(value, index, 1, &ch);
        *out = static_cast<jint>(ch);
        return true;
    }

    static bool arraycopy_bounds(jsize src_len, jsize dest_len, jint src_pos,
            jint dest_pos, jint length) {
        if (src_pos < 0 || dest_pos < 0 || length < 0) {
            return false;
        }
        return static_cast<jlong>(src_pos) + length <= src_len
                && static_cast<jlong>(dest_pos) + length <= dest_len;
    }

    enum arraycopy_kind {
        ARRAYCOPY_NONE = 0,
        ARRAYCOPY_BOOLEAN,
        ARRAYCOPY_BYTE,
        ARRAYCOPY_CHAR,
        ARRAYCOPY_SHORT,
        ARRAYCOPY_INT,
        ARRAYCOPY_LONG,
        ARRAYCOPY_FLOAT,
        ARRAYCOPY_DOUBLE,
        ARRAYCOPY_OBJECT
    };

    static arraycopy_kind classify_array(JNIEnv *env, jobject value) {
        if (env->IsInstanceOf(value, boolean_array_class)) {
            return ARRAYCOPY_BOOLEAN;
        }
        if (env->IsInstanceOf(value, byte_array_class)) {
            return ARRAYCOPY_BYTE;
        }
        if (env->IsInstanceOf(value, char_array_class)) {
            return ARRAYCOPY_CHAR;
        }
        if (env->IsInstanceOf(value, short_array_class)) {
            return ARRAYCOPY_SHORT;
        }
        if (env->IsInstanceOf(value, int_array_class)) {
            return ARRAYCOPY_INT;
        }
        if (env->IsInstanceOf(value, long_array_class)) {
            return ARRAYCOPY_LONG;
        }
        if (env->IsInstanceOf(value, float_array_class)) {
            return ARRAYCOPY_FLOAT;
        }
        if (env->IsInstanceOf(value, double_array_class)) {
            return ARRAYCOPY_DOUBLE;
        }
        if (env->IsInstanceOf(value, object_array_class)) {
            return ARRAYCOPY_OBJECT;
        }
        return ARRAYCOPY_NONE;
    }

    static std::size_t primitive_element_size(arraycopy_kind kind) {
        switch (kind) {
            case ARRAYCOPY_BOOLEAN:
            case ARRAYCOPY_BYTE:
                return 1;
            case ARRAYCOPY_CHAR:
            case ARRAYCOPY_SHORT:
                return 2;
            case ARRAYCOPY_INT:
            case ARRAYCOPY_FLOAT:
                return 4;
            case ARRAYCOPY_LONG:
            case ARRAYCOPY_DOUBLE:
                return 8;
            default:
                return 0;
        }
    }

    static bool arraycopy_primitive(JNIEnv *env, jobject src, jint src_pos,
            jobject dest, jint dest_pos, jint length, std::size_t element_size) {
        const jboolean same = env->IsSameObject(src, dest);
        if (env->ExceptionCheck()) {
            return false;
        }
        const std::size_t bytes =
                static_cast<std::size_t>(length) * element_size;
        if (same) {
            void *data = env->GetPrimitiveArrayCritical(
                    static_cast<jarray>(src), nullptr);
            if (data == nullptr) {
                return false;
            }
            std::memmove(
                    static_cast<char *>(data)
                            + static_cast<std::size_t>(dest_pos) * element_size,
                    static_cast<char *>(data)
                            + static_cast<std::size_t>(src_pos) * element_size,
                    bytes);
            env->ReleasePrimitiveArrayCritical(static_cast<jarray>(src), data, 0);
            return true;
        }
        char *buffer = bytes == 0 ? nullptr : new (std::nothrow) char[bytes];
        if (bytes != 0 && buffer == nullptr) {
            throw_re(env, "java/lang/OutOfMemoryError", "arraycopy", 0);
            return false;
        }
        void *src_data = env->GetPrimitiveArrayCritical(
                static_cast<jarray>(src), nullptr);
        if (src_data == nullptr) {
            delete[] buffer;
            return false;
        }
        std::memcpy(
                buffer,
                static_cast<char *>(src_data)
                        + static_cast<std::size_t>(src_pos) * element_size,
                bytes);
        env->ReleasePrimitiveArrayCritical(
                static_cast<jarray>(src), src_data, JNI_ABORT);
        void *dest_data = env->GetPrimitiveArrayCritical(
                static_cast<jarray>(dest), nullptr);
        if (dest_data == nullptr) {
            delete[] buffer;
            return false;
        }
        std::memcpy(
                static_cast<char *>(dest_data)
                        + static_cast<std::size_t>(dest_pos) * element_size,
                buffer,
                bytes);
        env->ReleasePrimitiveArrayCritical(
                static_cast<jarray>(dest), dest_data, 0);
        delete[] buffer;
        return true;
    }

    static bool arraycopy_objects(JNIEnv *env, jobject src, jint src_pos,
            jobject dest, jint dest_pos, jint length) {
        const bool reverse = env->IsSameObject(src, dest) && src_pos < dest_pos;
        if (reverse) {
            for (jint i = length - 1; i >= 0; --i) {
                jobject value = env->GetObjectArrayElement(
                        static_cast<jobjectArray>(src), src_pos + i);
                if (env->ExceptionCheck()) {
                    return false;
                }
                env->SetObjectArrayElement(static_cast<jobjectArray>(dest),
                        dest_pos + i, value);
                env->DeleteLocalRef(value);
                if (env->ExceptionCheck()) {
                    return false;
                }
            }
            return true;
        }
        for (jint i = 0; i < length; ++i) {
            jobject value = env->GetObjectArrayElement(
                    static_cast<jobjectArray>(src), src_pos + i);
            if (env->ExceptionCheck()) {
                return false;
            }
            env->SetObjectArrayElement(static_cast<jobjectArray>(dest),
                    dest_pos + i, value);
            env->DeleteLocalRef(value);
            if (env->ExceptionCheck()) {
                return false;
            }
        }
        return true;
    }

    bool arraycopy(JNIEnv *env, jobject src, jint src_pos, jobject dest,
            jint dest_pos, jint length) {
        if (src == nullptr || dest == nullptr) {
            throw_re(env, "java/lang/NullPointerException", "arraycopy", 0);
            return false;
        }
        const arraycopy_kind src_kind = classify_array(env, src);
        const arraycopy_kind dest_kind = classify_array(env, dest);
        if (src_kind == ARRAYCOPY_NONE || dest_kind == ARRAYCOPY_NONE
                || src_kind != dest_kind) {
            throw_re(env, "java/lang/ArrayStoreException", "arraycopy", 0);
            return false;
        }
        const jsize src_len = env->GetArrayLength(static_cast<jarray>(src));
        if (env->ExceptionCheck()) {
            return false;
        }
        const jsize dest_len = env->GetArrayLength(static_cast<jarray>(dest));
        if (env->ExceptionCheck()) {
            return false;
        }
        if (!arraycopy_bounds(src_len, dest_len, src_pos, dest_pos, length)) {
            throw_re(env, "java/lang/ArrayIndexOutOfBoundsException",
                    "arraycopy", 0);
            return false;
        }
        if (length == 0) {
            return true;
        }
        if (src_kind == ARRAYCOPY_OBJECT) {
            return arraycopy_objects(env, src, src_pos, dest, dest_pos, length);
        }
        return arraycopy_primitive(env, src, src_pos, dest, dest_pos, length,
                primitive_element_size(src_kind));
    }

    jint bit_count_i(jint value) {
        std::uint32_t bits = static_cast<std::uint32_t>(value);
        bits = bits - ((bits >> 1) & 0x55555555u);
        bits = (bits & 0x33333333u) + ((bits >> 2) & 0x33333333u);
        bits = (bits + (bits >> 4)) & 0x0f0f0f0fu;
        return static_cast<jint>((bits * 0x01010101u) >> 24);
    }

    jint bit_count_j(jlong value) {
        std::uint64_t bits = static_cast<std::uint64_t>(value);
        bits = bits - ((bits >> 1) & 0x5555555555555555ULL);
        bits = (bits & 0x3333333333333333ULL) + ((bits >> 2) & 0x3333333333333333ULL);
        bits = (bits + (bits >> 4)) & 0x0f0f0f0f0f0f0f0fULL;
        return static_cast<jint>((bits * 0x0101010101010101ULL) >> 56);
    }

    jint cf_opaque_true(JNIEnv *env) {
        volatile jint seed = env == nullptr ? 0 : 1;
        return seed | 1;
    }

    jint number_of_leading_zeros_i(jint value) {
        std::uint32_t bits = static_cast<std::uint32_t>(value);
        if (bits == 0) {
            return 32;
        }
        jint n = 1;
        if ((bits >> 16) == 0) { n += 16; bits <<= 16; }
        if ((bits >> 24) == 0) { n += 8; bits <<= 8; }
        if ((bits >> 28) == 0) { n += 4; bits <<= 4; }
        if ((bits >> 30) == 0) { n += 2; bits <<= 2; }
        n -= static_cast<jint>(bits >> 31);
        return n;
    }

    jint number_of_leading_zeros_j(jlong value) {
        std::uint64_t bits = static_cast<std::uint64_t>(value);
        if (bits == 0) {
            return 64;
        }
        jint n = 1;
        if ((bits >> 32) == 0) { n += 32; bits <<= 32; }
        if ((bits >> 48) == 0) { n += 16; bits <<= 16; }
        if ((bits >> 56) == 0) { n += 8; bits <<= 8; }
        if ((bits >> 60) == 0) { n += 4; bits <<= 4; }
        if ((bits >> 62) == 0) { n += 2; bits <<= 2; }
        n -= static_cast<jint>(bits >> 63);
        return n;
    }

    jclass get_class_from_object(JNIEnv *env, jobject object) {
        if (object == nullptr) {
            return nullptr;
        }
        return env->GetObjectClass(object);
    }

    jobject get_classloader_from_class(JNIEnv *env, jclass clazz) {
        jobject result_classloader = env->CallObjectMethod(clazz, get_classloader_method);
        if (env->ExceptionCheck()) {
            return nullptr;
        }
        return result_classloader;
    }

    jobject get_lookup(JNIEnv *env, jclass clazz) {
        jobject lookup = env->NewObject(methodhandles_lookup_class, lookup_init_method, clazz);
        if (env->ExceptionCheck()) {
            return nullptr;
        }
        return lookup;
    }

    void clear_refs(JNIEnv *env, std::unordered_set<jobject> &refs) {
        for (jobject ref : refs)
            if (env->GetObjectRefType(ref) == JNILocalRefType)
                env->DeleteLocalRef(ref);
        refs.clear();
    }

    jstring get_interned(JNIEnv *env, jstring value) {
        jstring result = (jstring) env->CallObjectMethod(value, string_intern_method);
        if (env->ExceptionCheck())
            return nullptr;
        return result;
    }
}