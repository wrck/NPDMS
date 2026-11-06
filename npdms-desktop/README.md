# NPDMS Electron desktop reconstruction

This is a new implementation based on repository commit `d4f6918d67c3f5b32559d7eba6deb6e9a847858a`. It is not a recovery of unavailable incremental work, and is not yet a completed desktop release.

## Current checkpoint

- Sandboxed Electron shell with a narrow, sender-verified IPC bridge.
- The full baseline Vue application has been built and verified: 2,825 files / 34,657,884 bytes. Its window uses no preload or Node privileges, a memory-only session and a selected HTTPS origin. Packaged resources are served locally; only existing backend API namespaces proxy to that selected server. Real application navigation/login is not yet tested.
- New windows, embedded webviews, permission requests and unattended downloads are denied. Certificate validation and the Chromium sandbox stay enabled.
- Offline business access is closed until the authoritative server lease, allowed operation set, expiration and device-key lifecycle are defined. No business role, state transition or endpoint is invented here.
- DAC remains an independent service with its own identity and operation tickets. Protocol-v3 Ed25519 discovery validation and controlled Java process-supervision foundations are implemented and tested with fake children. Production DAC assembly and process wiring are still incomplete. See `src/dac/README.md`.
- Main-process encrypted offline foundation now provides scope-bound AES-256-GCM snapshots, ordered durable operation queue, stable retry IDs, explicit conflict handling and crash recovery. It is not wired to business APIs and stays disabled without the missing authoritative policy. See `src/offline/README.md`.

## Checks

`npm test` runs Node-built-in tests without installing dependencies. `npm run check` checks JavaScript syntax. `npm ci` installs the locked Node dependencies. Electron 45.2.3 requires its separate native-binary installation step (`npm run install:runtime`) before `npm start` can start the development shell. Electron's official stable release was verified at https://github.com/electron/electron/releases/tag/v45.2.3.

Locked Node dependencies and the official checksum-verified Electron 45.2.3 Linux binary were installed in the cloud workspace. The 69 Node tests and JavaScript syntax checks passed. Electron execution was not attempted because the cloud executor had previously denied process-singleton socket creation. Packaging, actual Linux installation and real offline business acceptance have **not** passed at this checkpoint. Do not add `--no-sandbox`, ignore certificate errors, or call unpacking an installation test. The reconstruction must continue with a permitted Linux desktop environment.

## Linux key storage

`src/key-store.cjs` implements an Electron safeStorage adapter backed by Linux Secret Service or KWallet. It rejects unavailable encryption and the insecure `basic_text` backend. Key files are encrypted, privately permissioned, scope-bound and created atomically without overwriting existing keys. The adapter has synthetic tests but has not been exercised against a real Linux desktop keyring. Neither DAC key provisioning nor offline data access is enabled automatically.

## Remaining integration

1. Validate the built full Vue application against a real NPDMS backend and Linux desktop; test all remaining resource/CSP compatibility.
2. Integrate approved working-set and operation contracts with encrypted storage, expiry, replay, idempotency and conflict recovery.
3. Bundle the baseline DAC Java service and JRE, preserving its protocol, identity, tickets, loopback restrictions and process lifecycle.
4. Produce reproducible Linux installers, install them on a real Linux desktop, and test windows and online/offline business flows.

Source publication is limited to the dedicated reconstruction branch. The existing repository baseline and branches remain unchanged.

Detailed verification and remaining acceptance requirements: [ACCEPTANCE.md](ACCEPTANCE.md).

## Development installer and synthetic trial

`npm run package:linux -- --development` builds an explicitly labelled development Debian package. `npm run inspect:linux -- artifacts/<file>.deb` inspects its archive; neither command installs or starts it. Production payload mode refuses missing Vue/DAC resources. See `packaging/README.md`.

`src/offline/trial.cjs` provides a no-network synthetic trial using fixed test records and a TEST-ONLY 15-minute lease. This does not establish production offline authorization or NPDMS synchronization. See `src/offline/TRIAL.md`.

## Rebuild the real Vue payload

Use a clean checkout at the fixed baseline (not another branch), then `npm run build:frontend -- --source /absolute/baseline-repository`. The command uses the original pnpm lock, rejects local .env files, builds with relative backend URLs and stages the real application. `npm run verify:frontend` checks all recorded hashes/sizes and serves real entry/history/assets through the exact desktop handler without network. Generated Vue/DAC binaries are ignored by Git and must be rebuilt or separately retained.

The local synthetic trial now has a visible shell workflow, backed by the OS-vault adapter. Main-process and DOM tests cover the narrow IPC surface, repeated opening, explicit conflict resolution and expiry. DOM tests are not a real Electron window test.
