# config

Settings shared by every service. Each service reads them once at startup and never changes them while running.

## instruments.json

The instrument list: everything that can be traded, and where it lives.

| field | meaning |
|---|---|
| `version` | bump it on every change, so two copies of the file can be told apart |
| `partitions` | number of sequencer lanes (= engine workers) |
| `instruments[].id` | the SymbolId on the wire and in the engine (never 0, unique) |
| `instruments[].ticker` | what people type and see: 1-12 of `A-Z0-9`, starting with a letter, unique |
| `instruments[].partition` | the lane / worker that owns it, below `partitions` |
| `instruments[].band_bps` | circuit-breaker band in basis points (1000 = 10%), 1-9999 |

Unknown fields are errors (a typo like `partiton` must not be silently ignored).

Read by:
- `sequencer`: symbol → partition routing.
- `order_book_engine`'s `engine_node` and `exchange_server`: which Books to build, on which worker.

Location: this file by default (path fixed at build time). Override with
`EXCHANGE_INSTRUMENTS=/path/to/instruments.json`, which is how a deployment would mount it
(e.g. a Kubernetes ConfigMap).

Changing it: edit, bump `version`, restart the services. The sequencer and every engine_node must run the same
version; an engine that receives a symbol it does not own stops with "config mismatch" rather than guess.

Later this file is exported from a database table; the services do not change.
