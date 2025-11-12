cmake_minimum_required(VERSION 3.4.1)

project(jsengine)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

file(GLOB common_SRC
    "../../../../jsengine-jni/src/main/jni/*.h"
    "../../../../jsengine-jni/src/main/jni/*.cpp"
)
file(GLOB_RECURSE duktape_SRC
    "../../../../jsengine-jni/src/main/jni/duktape-jni/*.h"
    "../../../../jsengine-jni/src/main/jni/duktape-jni/*.c"
    "../../../../jsengine-jni/src/main/jni/duktape-jni/*.cpp"

    "../../../../jsengine-jni/src/main/jni/duktape/*.h"
    "../../../../jsengine-jni/src/main/jni/duktape/*.c"
    "../../../../jsengine-jni/src/main/jni/duktape/*.cpp"
)

add_definitions(-DCONFIG_VERSION="2019-10-27")
add_definitions(-DCONFIG_DISABLE_STACK_CHECK)
add_definitions(-DCONFIG_DISABLE_WORKER)

file(GLOB quickjs_SRC
    "../../../../jsengine-jni/src/main/jni/quickjs-jni/*.h"
    "../../../../jsengine-jni/src/main/jni/quickjs-jni/*.c"
    "../../../../jsengine-jni/src/main/jni/quickjs-jni/*.cpp"
    "../../../../../../quickjs/*.h"
    "../../../../../quickjs/quickjs.c"
    "../../../../../quickjs/libbf.c"
    "../../../../../quickjs/quickjs-libc.c"
    "../../../../../quickjs/quickjs-debugger.c"
    "../../../../../quickjs/quickjs-debugger-transport-unix.c"
    "../../../../../quickjs/libunicode.c"
    "../../../../../quickjs/libregexp.c"
    "../../../../../quickjs/cutils.c"
    "../../../../../quickjs/dtoa.c"
)

add_library(jsengine SHARED ${common_SRC} ${quickjs_SRC} ${duktape_SRC})

target_link_libraries(jsengine)

target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,max-page-size=16384")
