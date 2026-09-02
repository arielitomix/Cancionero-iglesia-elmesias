cmake_minimum_required(VERSION 3.22.1)
project(secuenlive_soundtouch CXX)

include(FetchContent)

FetchContent_Declare(
    voicechanger_src
    URL https://github.com/neboyang/VoiceChanger/archive/c1caf1224ab8c80d917b934771d16c2ef210bfba.tar.gz
)

FetchContent_GetProperties(voicechanger_src)
if(NOT voicechanger_src_POPULATED)
    FetchContent_Populate(voicechanger_src)
endif()

add_subdirectory(
    ${voicechanger_src_SOURCE_DIR}/voicechanger/src/main/cpp
    ${CMAKE_BINARY_DIR}/soundtouch-build
)
