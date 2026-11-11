# Added for Sambung, November 2026. Builds the Codec2 vocoder (LGPL-2.1, see ../../../COPYING) and a two-function
# JNI bridge into one shared library.
cmake_minimum_required(VERSION 3.22.1)
project(sambungcodec2 C)

file(GLOB CODEC2_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/codec2/*.c)

add_library(sambungcodec2 SHARED sambung_codec2_jni.c ${CODEC2_SOURCES})
target_include_directories(sambungcodec2 PRIVATE codec2 codec2/include)
# -w: upstream C, not ours to tidy.
target_compile_options(sambungcodec2 PRIVATE -w -O2)
target_link_libraries(sambungcodec2 m)
