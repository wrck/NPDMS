# Desktop reconstruction acceptance record

Source baseline: `d4f6918d67c3f5b32559d7eba6deb6e9a847858a`.
This records a new reconstruction, not recovery of unavailable prior work.

## Verified in the cloud workspace

- `npm ci` completed using the committed dependency lock.
- Official Electron 45.2.3 Linux x64 binary downloaded and extracted by its official installer with the package's checksums. The binary was not launched.
- `npm test`: 52 focused Node tests passed including read-only DAC probing, synthetic trial and package-archive tests. These tests use synthetic authorization policies, fake DAC processes and an in-memory keyring substitute. One offline queue test kills its own isolated Node child to verify durable crash recovery.
- `npm run check`: JavaScript syntax passed.
- All 23 source/test files at checkpoint `ebfe984339141ca32f1afc6191ad5d76a36a9fcc` were fetched again from GitHub and matched byte-for-byte and by Git blob SHA. Compare against baseline showed only additions under `npdms-desktop/`.
- No CI statuses were returned for that checkpoint. This is not a CI pass.

## Not executed or not complete

- Electron window launch, visual inspection, real application navigation and platform login.
- Packaged full Vue application and offline business adapters.
- Linux OS keyring integration and existing-key recovery.
- Real DAC Java process, bundled Java 25 runtime and parser payload.
- A 119 MB explicitly development-labelled Debian archive was built and statically inspected before Vue/main integration. This is not installation; the integrated package must be rebuilt. No real installation/uninstall/upgrade or RPM/AppImage acceptance has run.
- Real online/offline business, idempotency, conflict and authorization-expiry acceptance against NPDMS.

The cloud executor previously denied Electron's process-singleton Unix socket operation with EPERM. The reconstruction does not retry that denied operation, disable sandboxing, or switch execution tools to bypass it. A permitted Linux desktop environment must provide the GUI, OS keyring, package installation privileges and dependencies needed for actual acceptance.

## Required authoritative offline contract

The main application keeps offline business access disabled. Before enabling it, the backend/product owners must supply or approve:

1. Working-set selection, exact allowed datasets, roles and operations, and authoritative schemas/versions.
2. Authenticated offline grant issuer and verifier, audience/scope, issuance and expiry, approved TTL, renewal, revocation and trusted-time/rollback behavior.
3. Device data-key provisioning, OS vault storage, rotation, logout, recovery, backup and retention policy.
4. Authenticated synchronization API, durable idempotency scope and retention, server-version preconditions, conflict response and user-resolution rules.
5. Acceptance fixtures and expected business outcomes without bypassing current permissions, states, immutable history or audit records.

The adapter-local queue outcomes are not invented NPDMS HTTP endpoints or business statuses. Generic encrypted persistence and retry tests do not prove these missing server contracts.

## DAC constraints already established by the baseline

- DAC is the existing independent service, with separate Ed25519 installation identity, encrypted credential storage, instance identity, operation tickets and outbox.
- Every protected desktop request resolves a server ticket online. New offline collection is unavailable; already accepted work may continue and retry immutable result delivery.
- Use signed protocol-v3 discovery on loopback 8280–8289, not actuator or generic parser/collection routes.
- The configured WEB_ORIGIN is HTTP(S). A file/custom-scheme renderer must use an approved main-process bridge, preserving exact-origin and ticket checks rather than disabling them.

## Actual Linux acceptance must include

Install the final checksummed package on a supported clean Linux desktop, launch the real window with Chromium sandbox and certificate checks enabled, and validate the actual full NPDMS workflows. Exercise restart, repeated commands, interrupted login, denied permissions, failed network, expired/revoked grant, mismatched identity/scope, queued writes, lost acknowledgements, server conflicts and recovery. Verify separate DAC ownership, port collisions, clean shutdown, stable identity/data across upgrades and no plaintext credentials. Confirm the installed package and observed process match the exact committed source and payload manifest. Only those results can close this task.
