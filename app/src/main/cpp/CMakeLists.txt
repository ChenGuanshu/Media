
# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("native-media")

# 支持gnu++11
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11")

#指定头文件路径
include_directories(${CMAKE_SOURCE_DIR}/include)

# 定义包含源文件的目录
set(SOURCE_DIR "src")
# 使用 file(GLOB ...) 命令收集该目录下的所有 .cpp 文件
file(GLOB SOURCE_FILES "${SOURCE_DIR}/*.cpp")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
        ${SOURCE_FILES}
#        media.cpp hellocpp.cpp Utils.cpp
)

set(libs "${CMAKE_SOURCE_DIR}/libs")

function(buildlib)
    add_library(avcodec SHARED IMPORTED)
    set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION ${libs}/libavcodec.so)

    add_library(avdevice SHARED IMPORTED)
    set_target_properties(avdevice PROPERTIES IMPORTED_LOCATION ${libs}/libavdevice.so)

    add_library(avfilter SHARED IMPORTED)
    set_target_properties(avfilter PROPERTIES IMPORTED_LOCATION ${libs}/libavfilter.so)

    add_library(avformat SHARED IMPORTED)
    set_target_properties(avformat PROPERTIES IMPORTED_LOCATION ${libs}/libavformat.so)

    add_library(avutil SHARED IMPORTED)
    set_target_properties(avutil PROPERTIES IMPORTED_LOCATION ${libs}/libavutil.so)

    add_library(swresample SHARED IMPORTED)
    set_target_properties(swresample PROPERTIES IMPORTED_LOCATION ${libs}/libswresample.so)

    add_library(swscale SHARED IMPORTED)
    set_target_properties(swscale PROPERTIES IMPORTED_LOCATION ${libs}/libswscale.so)
endfunction()

buildlib()
set(ffmpeg avcodec avdevice avfilter avformat avutil swresample swscale)

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(
        ${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        android
        log
        ${ffmpeg}
)
