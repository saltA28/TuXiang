cmake_minimum_required(VERSION 3.4.1)

add_library(gl2jni SHARED
            src/main/cpp/gl_code.c)

# add lib dependencies
target_link_libraries(gl2jni
                      android
                      log
                      EGL
                      GLESv2)
