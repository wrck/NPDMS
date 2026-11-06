# NPDMS Electron desktop reconstruction

This is a new implementation based on repository commit `d4f6918d67c3f5b32559d7eba6deb6e9a847858a`. It is not a recovery of unavailable incremental work, and is not yet a completed desktop release.

## Current checkpoint

- Sandboxed Electron shell with a narrow, sender-verified IPC bridge.
- Full NPDMS online platform opens separately with no preload or Node privileges, restricted to the chosen HTTPS origin, in a memory-only browser session.
- New windows, embedded webviews, permission requests and unattended downloads are denied. Certificate validation and the Chromium sandbox stay enabled.
- Offline business access is closed until the authoritative server lease, allowed operation set, expiration and device-key lifecycle are defined. No business role, state transition or endpoint is invented here.
- DAC remains an independent service with its own identity and operation tickets. It is not replaced by the shell and is not yet bundled in this checkpoint.
- Main-process encrypted offline foundation now provides scope-bound AES-256-GCM snapshots, ordered durable operation queue, stable retry IDs, explicit conflict handling and crash recovery. It is not wired to business APIs and stays disabled without the missing authoritative policy. See `src/offline/README.md`.

## Checks

`npm test` runs Node-built-in tests without installing dependencies. `npm run check` checks JavaScript syntax. `npm ci` installs the locked Node dependencies. Electron 45.2.3 requires its separate native-binary installation step (`npx install-electron`) before `npm start` can start the development shell. Electron's official stable release was verified at https://github.com/electron/electron/releases/tag/v45.2.3.

Locked Node dependency installation, 17 Node tests and JavaScript syntax checks passed in the cloud workspace. The native Electron binary installation, Electron execution, packaging, actual Linux installation and real offline business acceptance have **not** passed at this checkpoint. Do not add `--no-sandbox`, ignore certificate errors, or call unpacking an installation test. The reconstruction must continue with a permitted Linux desktop environment.

## Remaining integration

1. Build and package the existing full Vue application from the fixed baseline.
2. Integrate approved working-set and operation contracts with encrypted storage, expiry, replay, idempotency and conflict recovery.
3. Bundle the baseline DAC Java service and JRE, preserving its protocol, identity, tickets, loopback restrictions and process lifecycle.
4. Produce reproducible Linux installers, install them on a real Linux desktop, and test windows and online/offline business flows.

Source publication is limited to the dedicated reconstruction branch. The existing repository baseline and branches remain unchanged.
