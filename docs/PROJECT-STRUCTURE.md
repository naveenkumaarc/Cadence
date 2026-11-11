# Project structure

`app/` contains the Android application and all user-facing surfaces. `core/` contains reusable Java logic that can run without Android UI. `buildSrc/` contains build-time bytecode instrumentation.

`probe/` is a JVM diagnostic target for catalog and extractor behavior. `carprobe/` is a minimal Android media-browser client for validating the Android Auto-compatible browse tree. `examples/` contains sample import data. `tools/` contains connected-device checks and repository transfer helpers. `vendor/sources/` retains corresponding third-party source required by the project license.
