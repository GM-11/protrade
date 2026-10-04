# Pulled in by each project with a guarded add_subdirectory, like ../proto.

include(FetchContent)
FetchContent_Declare(
    nlohmann_json
    URL https://github.com/nlohmann/json/releases/download/v3.11.3/json.tar.xz
    URL_HASH
    SHA256=d6c65aca6b1ed68e7a182f4757257b107ae403032760ed6ef121c9d55e81757d
    DOWNLOAD_EXTRACT_TIMESTAMP TRUE
)
FetchContent_MakeAvailable(nlohmann_json)

add_library(instrument_config STATIC src/instruments.cpp)
target_include_directories(instrument_config PUBLIC include)
target_link_libraries(instrument_config PRIVATE nlohmann_json::nlohmann_json)
target_compile_features(instrument_config PUBLIC cxx_std_20)
target_compile_options(instrument_config PRIVATE -Wall -Wextra -Wpedantic)
target_compile_definitions(instrument_config PRIVATE
                           "EXCHANGE_DEFAULT_INSTRUMENTS=\"${CMAKE_CURRENT_SOURCE_DIR}/instruments.json\"")
