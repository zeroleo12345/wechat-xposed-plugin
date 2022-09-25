
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := native-lib
LOCAL_SRC_FILES := native-lib.cpp \
                aes_cbc.cpp \
                aes_core.cpp \
                Base64.cpp \
                cbc128.cpp \
                aes_jni_wrapper.cpp \
                check_sign.cpp

# note 平台判断 https://stackoverflow.com/questions/714100/os-detecting-makefile        http://blog.csdn.net/absurd/article/details/54235823
ifeq ($(OS),Windows_NT)
    Windows=1
else
    Linux=1
endif

# note: 多个变量条件组合: https://stackoverflow.com/questions/5584872/complex-conditions-check-in-makefile
#ifdef Linux && OUTSIDE
ifneq ($(and $(Linux),$(OUTSIDE)),)
    # note: Linux Platform building APK, and use ollvm
    LOCAL_CFLAGS := -w -fvisibility=hidden   -mllvm -sub -mllvm -fla -mllvm -bcf
    #LOCAL_CFLAGS := -w -fv123=123
else
    # note: Windows Platform building APK
    LOCAL_CFLAGS := -w -fvisibility=hidden
endif



# LOCAL_SHARED_LIBRARIES := liblog libcutils
LOCAL_LDLIBS    := -llog

include $(BUILD_SHARED_LIBRARY)

# include $(BUILD_EXECUTABLE)
