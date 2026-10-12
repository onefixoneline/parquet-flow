# Parquet Flow

Lightweight, high-performance in-IDE viewer for Apache Parquet files, built for JetBrains IDEs.

Open any `.parquet` (or `.pq`) file and get an instant schema overview plus a
virtualized data grid. Everything runs in-process via an embedded DuckDB
engine: no server, no telemetry, no data leaving your machine.

## Features

- **Instant open** — schema and sample rows read in milliseconds
- **Virtualized grid** — adjustable row limit keeps memory flat on huge files
- **Nested types** — `STRUCT`, `LIST`, `MAP`, `TIMESTAMP_NS`, `BLOB`, `DECIMAL`
- **Explicit NULLs** — SQL `NULL` renders as `<null>` so it is never confused
  with an empty string
- **Local only** — 100% in-process, zero network calls

## Requirements

- JetBrains IDE 2024.1 or newer (build 241+)
- JDK 17 or newer to build

## Building

```bash
./gradlew buildPlugin
```

The installable archive is written to `build/distributions/parquet-flow-1.0.0.zip`.
Install it via **Settings → Plugins → ⚙ → Install Plugin from Disk...**, then
restart the IDE.

## Running tests

```bash
./gradlew test
```

The suite includes a regression test for the DuckDB driver lookup, which fails
inside the IDE's `PluginClassLoader` unless the driver is instantiated directly.

## License

Released under the [MIT License](LICENSE).