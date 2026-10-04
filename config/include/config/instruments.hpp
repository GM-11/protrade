#pragma once

// The instrument list: everything that can be traded, and where it lives.
//
// One file (trading-project/config/instruments.json) is read at startup by
// every service that needs it: the sequencer (symbol -> partition) and the
// engine (which Books to build, on which worker). Nobody keeps a private copy.
// Each service reads it once and never changes it while running: routing
// tables are read without locks, and moving a symbol between partitions
// mid-session would split its numbered history.
//
// Later the file is exported from a database table; the services do not change.

#include <cstdint>
#include <stdexcept>
#include <string>
#include <string_view>
#include <vector>

namespace config {

struct Instrument {
    std::uint32_t id;        // the SymbolId used on the wire and in the engine; never 0
    std::string ticker;      // what people type and see, e.g. "MOOG"
    std::uint32_t partition; // the sequencer lane, and the engine worker, that owns it
    std::int64_t band_bps;   // circuit-breaker band, basis points (1000 = 10%)
};

struct InstrumentConfig {
    std::uint64_t version = 0;    // bump on every change, so services can tell two files apart
    std::uint32_t partitions = 0; // number of lanes (= engine workers); every partition is below this
    std::vector<Instrument> instruments;

    const Instrument *find(std::uint32_t id) const;        // nullptr if absent
    const Instrument *find(std::string_view ticker) const; // exact match; nullptr if absent
};

// Every problem with the file: unreadable, not JSON, a missing/unknown/invalid
// field, duplicates. The message says which file and which entry.
class ConfigError : public std::runtime_error {
  public:
    using std::runtime_error::runtime_error;
};

// Parses and validates. `source` only labels error messages.
InstrumentConfig parse_instruments(std::string_view json_text, std::string_view source = "<string>");

// Reads the file at `path`, then parse_instruments().
InstrumentConfig load_instruments(const std::string &path);

// Where to read from: the EXCHANGE_INSTRUMENTS environment variable if set and
// not empty, otherwise this repo's config/instruments.json (path fixed at build
// time).
std::string instruments_path();

} // namespace config
