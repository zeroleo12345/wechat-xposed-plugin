cmake_minimum_required(VERSION 3.4.1)

set(CURRENT_DIR ${CMAKE_SOURCE_DIR})

# note: 在Gradle Console输出信息: E:/cygwin64/home/zlx/android-study/Xposed.6510
message("CURRENT_DIR:" ${CMAKE_SOURCE_DIR})

include_directories(src/include)

# note 日志类
find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# note 这里openssl需要 zlib库，加载系统的
# find_library( z-lib z )

# note: 这里配置指定目录libs

#set(SOURCES src/cpp/native-lib.cpp )
add_library( # Sets the name of the library.
             native-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             ${CURRENT_DIR}/src/cpp/native-lib.cpp )

# note: 链接!
target_link_libraries( # Specifies the target library.
                       native-lib

                       # Links the target library to the log library
                       # included in the NDK.
                        ${log-lib})