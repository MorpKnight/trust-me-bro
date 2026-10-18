# NHentai extension source

This directory contains the `src/all/nhentai` module for the NHentai source. It preserves the `en` and `all` source variants, including the legacy source ID for `all`, and ports the historical implementation to `KeiSource` 1.6 and the current source DSL.

The module belongs inside a compatible `keiyoushi/extensions-source` build tree. It is not a standalone Gradle project: the extension plugin, core source classes, and `randomua` library are provided by that build. After placing this module at `src/all/nhentai`, use the parent repository's Gradle tasks, such as `:src:all:nhentai:assembleRelease`.

The historical implementation was taken from [Xyndra/keiyoushi-extensions-source at d90363b](https://github.com/Xyndra/keiyoushi-extensions-source/tree/d90363bf565df34433d2572bad6eec3529a1f418/src/all/nhentai). This port is licensed under Apache-2.0; see [LICENSE](LICENSE).

## Published repository

This module declares the `en` and `all` sources together, so they are distributed in one APK. The hosted repo includes the APK and indexes for current Mihon and legacy-compatible clients:

- Mihon 0.20+: <https://pub-4516beba67d347eeabaa848f3f129375.r2.dev/repo.json>
- Tachimanga and clients that use the legacy JSON index: <https://pub-4516beba67d347eeabaa848f3f129375.r2.dev/index.min.json>
- APK: <https://pub-4516beba67d347eeabaa848f3f129375.r2.dev/apk/tachiyomi-all.nhentai-v1.6.55.apk>

The v2 repository index pins the APK signing certificate fingerprint. This initial APK uses the local Android debug signing key; future releases must keep using that same key to update an existing installation. The key is stored on this computer, outside this project, so keep it available or set up a dedicated release key before moving builds to another machine.

The `r2.dev` endpoint is intended for development traffic. Connect a custom domain to the bucket before treating this as a production repository.
