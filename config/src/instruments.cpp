#include "config/instruments.hpp"

#include <nlohmann/json.hpp>

#include <algorithm>
#include <cstdlib>
#include <fstream>
#include <initializer_list>
#include <limits>
#include <sstream>
#include <unordered_set>

#ifndef EXCHANGE_DEFAULT_INSTRUMENTS
#error "EXCHANGE_DEFAULT_INSTRUMENTS must be defined by the build"
#endif

namespace config {
namespace {

using json = nlohmann::json;

[[noreturn]] void fail(const std::string &where, const std::string &what) { throw ConfigError(where + ": " + what); }

// A typo like "partiton" must be an error, not a silently ignored field.
void only_keys(const json &object, std::initializer_list<std::string_view> allowed, const std::string &where) {
    for (auto it = object.begin(); it != object.end(); ++it)
        if (std::find(allowed.begin(), allowed.end(), it.key()) == allowed.end())
            fail(where, "unknown field \"" + it.key() + "\"");
}

const json &field(const json &object, const char *key, const std::string &where) {
    auto it = object.find(key);
    if (it == object.end())
        fail(where, std::string("missing field \"") + key + "\"");
    return *it;
}

// A whole number in [min, max]. 1.0, "1" and true are all rejected.
std::int64_t whole_number(const json &object, const char *key, const std::string &where, std::int64_t min,
                          std::int64_t max) {
    const json &value = field(object, key, where);
    const std::string range = " between " + std::to_string(min) + " and " + std::to_string(max);
    if (!value.is_number_integer())
        fail(where, std::string("\"") + key + "\" must be a whole number" + range);
    // The JSON library stores every non-negative number as unsigned, so both ends are checked here too.
    if (value.is_number_unsigned()) {
        const auto u = value.get<std::uint64_t>();
        if (u > static_cast<std::uint64_t>(max) || static_cast<std::int64_t>(u) < min)
            fail(where, std::string("\"") + key + "\" must be" + range);
        return static_cast<std::int64_t>(u);
    }
    const auto v = value.get<std::int64_t>();
    if (v < min || v > max)
        fail(where, std::string("\"") + key + "\" must be" + range);
    return v;
}

// Tickers are what people type: 1-12 characters, A-Z and 0-9, starting with a
// letter.
bool valid_ticker(const std::string &ticker) {
    if (ticker.empty() || ticker.size() > 12)
        return false;
    if (ticker[0] < 'A' || ticker[0] > 'Z')
        return false;
    return std::all_of(ticker.begin(), ticker.end(),
                       [](char c) { return (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'); });
}

} // namespace

const Instrument *InstrumentConfig::find(std::uint32_t id) const {
    auto it = std::find_if(instruments.begin(), instruments.end(), [&](const Instrument &i) { return i.id == id; });
    return it == instruments.end() ? nullptr : &*it;
}

const Instrument *InstrumentConfig::find(std::string_view ticker) const {
    auto it =
        std::find_if(instruments.begin(), instruments.end(), [&](const Instrument &i) { return i.ticker == ticker; });
    return it == instruments.end() ? nullptr : &*it;
}

InstrumentConfig parse_instruments(std::string_view json_text, std::string_view source) {
    const std::string file(source);
    json root;
    try {
        root = json::parse(json_text.begin(), json_text.end());
    } catch (const json::parse_error &error) {
        fail(file, std::string("not valid JSON (") + error.what() + ")");
    }
    if (!root.is_object())
        fail(file, "the top level must be an object { ... }");
    only_keys(root, {"version", "partitions", "instruments"}, file);

    InstrumentConfig config;
    config.version =
        static_cast<std::uint64_t>(whole_number(root, "version", file, 1, std::numeric_limits<std::int64_t>::max()));
    config.partitions = static_cast<std::uint32_t>(whole_number(root, "partitions", file, 1, 1024));

    const json &list = field(root, "instruments", file);
    if (!list.is_array() || list.empty())
        fail(file, "\"instruments\" must be a non-empty list [ ... ]");

    std::unordered_set<std::uint32_t> ids;
    std::unordered_set<std::string> tickers;
    for (std::size_t i = 0; i < list.size(); ++i) {
        const json &entry = list[i];
        const std::string where = file + ": instruments[" + std::to_string(i) + "]";
        if (!entry.is_object())
            fail(where, "each instrument must be an object { ... }");
        only_keys(entry, {"id", "ticker", "partition", "band_bps"}, where);

        Instrument instrument;
        instrument.id =
            static_cast<std::uint32_t>(whole_number(entry, "id", where, 1, std::numeric_limits<std::uint32_t>::max()));

        const json &ticker = field(entry, "ticker", where);
        if (!ticker.is_string() || !valid_ticker(ticker.get<std::string>()))
            fail(where, "\"ticker\" must be 1-12 characters of A-Z and 0-9, starting "
                        "with a letter");
        instrument.ticker = ticker.get<std::string>();

        instrument.partition = static_cast<std::uint32_t>(
            whole_number(entry, "partition", where, 0, static_cast<std::int64_t>(config.partitions) - 1));
        instrument.band_bps = whole_number(entry, "band_bps", where, 1, 9999);

        if (!ids.insert(instrument.id).second)
            fail(where, "duplicate id " + std::to_string(instrument.id));
        if (!tickers.insert(instrument.ticker).second)
            fail(where, "duplicate ticker \"" + instrument.ticker + "\"");
        config.instruments.push_back(std::move(instrument));
    }
    return config;
}

InstrumentConfig load_instruments(const std::string &path) {
    std::ifstream in(path);
    if (!in)
        throw ConfigError(path + ": cannot open the instruments file");
    std::ostringstream text;
    text << in.rdbuf();
    return parse_instruments(text.str(), path);
}

std::string instruments_path() {
    if (const char *from_env = std::getenv("EXCHANGE_INSTRUMENTS"); from_env != nullptr && *from_env != '\0')
        return from_env;
    return EXCHANGE_DEFAULT_INSTRUMENTS;
}

} // namespace config
