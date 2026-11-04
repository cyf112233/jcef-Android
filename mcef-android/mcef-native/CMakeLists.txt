cmake_minimum_required(VERSION 3.22)
project(mcef C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

add_library(mcef SHARED
    src/mcef_client.c
    src/jni_bridge.c
    src/hoststarter.c)

target_include_directories(mcef PRIVATE src)

# 只依赖 liblog，libcef 在插件 APK 里，和这里没有链接关系
find_library(log-lib log)
target_link_libraries(mcef ${log-lib})

target_compile_options(mcef PRIVATE -Wall -Wextra -Wno-unused-parameter -fvisibility=hidden)
target_link_options(mcef PRIVATE "-Wl,--no-undefined")
