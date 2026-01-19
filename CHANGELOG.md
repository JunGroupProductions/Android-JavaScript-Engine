# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Complete package rebranding from original Quack library to JSEngine
- Apache 2.0 license compliance with proper attribution to original author (Koushik Dutta)
- LICENSE, NOTICE, and THIRD_PARTY_LICENSES files bundled in AAR at `assets/licenses/`
- Maven publishing configuration for `com.hyprmx.jsengine:jsengine`
- 16KB page size support for modern Android devices
- Dual JavaScript engine support (QuickJS ES2020 / Duktape ES5.1)
- Comprehensive documentation (CLAUDE.md, README.md, REFACTORING_PLAN.md)
- ProGuard/R8 consumer rules bundled in AAR for automatic obfuscation support
- Sources JAR included in Maven publication

### Changed
- Package name: `com.koushikdutta.quack` → `com.hyprmx.jsengine.jsengine`
- Module names: `quack-*` → `jsengine-*` (jsengine-java, jsengine-jni, jsengine-android)
- Class prefixes: `Quack*` → `JSEngine*` (QuackContext → JSEngineContext, etc.)
- Native library name: `libquack.so` → `libjsengine.so`
- Maven coordinates: `com.hyprmx.jsengine:jsengine:X.Y.Z`
- Updated build configuration: AGP 8.8.0, NDK 28.2.13676358, Gradle 8.10.2
- Target Android SDK: API 21-35
- All public APIs cleaned of legacy "quack" references
  - `JavaScriptObject.quackContext` → `JavaScriptObject.jsEngineContext`
  - Internal method names: `quackGet` → `proxyGet`, `quackHas` → `proxyHas`, etc.
- JNI signatures updated to match new package structure
- Copyright attribution: Retained original copyright (Koushik Dutta 2015) with HyprMX modifications

### Technical Details
- **Build Tools**: AGP 8.8.0, Gradle 8.10.2, Kotlin 1.6.21
- **NDK Version**: 28.2.13676358
- **Min SDK**: 21
- **Target SDK**: 35
- **Supported Architectures**: arm64-v8a, armeabi-v7a, x86, x86_64
- **16KB Page Alignment**: Configured via CMake linker options
- **License**: Apache License 2.0

### Attribution
This project is a derivative work of [Quack](https://github.com/koush/quack) by Koushik Dutta, licensed under Apache License 2.0. All modifications are documented in the NOTICE file.

Third-party components:
- QuickJS JavaScript Engine (MIT License) - Copyright 2017-2021 Fabrice Bellard & Charlie Gordon
- Duktape JavaScript Engine (MIT License) - Various contributors
