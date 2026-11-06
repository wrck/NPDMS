# Linux DAC payload status

The payload is incomplete. Do not publish it as a working DAC installation.

- Source: `wrck/NPDMS` commit `d4f6918d67c3f5b32559d7eba6deb6e9a847858a`; original source unchanged.
- Present in ignored `resources/dac/`: official Eclipse Temurin Linux x64 JRE 25.0.1+8 and all 11 files of parser release `device-command-output-1.4.0`.
- Missing: `device-ops-server.jar`. The application must remain fail-closed when it is absent.
- JRE archive SHA-256: `126c7f59b91df97b2e930b0a456422ce29af7f2385117e1c04297eba962b0b1c`. All runtime symlinks were verified to resolve inside the extracted JRE and dereferenced for packaging. Licenses retained.

## Build evidence

An isolated Linux equivalent used the exact pinned JDK 25.0.1+8 and Maven 3.8.6, an exclusive process lock, separate Maven repository/output paths, full reactor tests and the baseline source-fingerprint implementation. The original PowerShell toolchain is Windows-only; original deployment freezing additionally depends on an untracked verifier absent from this baseline. No substitute deployment verification is claimed.

The Java reactor failed at the SSH adapter: 294 tests ran, 293 passed and one failed (`concurrentSessionsKeepHostKeyPolicyAndOutputIsolated`, only two sessions overlapped). Later reactor modules did not run; no server JAR was produced. Tests were not skipped or relaxed. Source fingerprints before/after matched: `ad382947a6303b1f3807e544c0ee759a28dcf24dbb384b121ed57b46b4b1f0c7`.

The separate DAC web UI also cannot build from the fixed commit: `ScriptArtifactEditor.vue` imports the missing `src/data/common-commands` module. Its test run had 100 passing tests, 24 passing suites and two failed import suites. This does not affect the independently built main NPDMS Vue application.

No production Java service or Electron application was started. The failed reactor has exited, and further Java/network build work is halted. A complete payload and runtime acceptance are not claimed.
