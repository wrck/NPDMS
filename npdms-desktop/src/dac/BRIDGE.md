# Desktop DAC same-origin bridge

The platform window remains sandboxed, without Node or preload. Its selected HTTPS origin owns the fixed `/__npdms_desktop/dac` namespace. Main-process request routing handles it before backend/static routing; direct loopback requests and arbitrary native URI launch remain denied.

- `GET /status?port=828x&nonce=<64 lower-case hex>` returns genuine pinned, signed status from the current owned instance only.
- `GET/POST /operations?port=828x&path=<encoded path>` supports only existing desktop task reads, semantic-result reads, execution creation and saved-connection read/save method/path combinations. Original ticket, tenant and JSON semantics remain intact.
- `POST /connect` accepts only the strict baseline `{uri: "sdp-dac://connect/..."}` payload. It submits a verified fresh proof to the configured HTTPS server's existing proof endpoint, with no browser authentication or cookies. `{ok:true}` means proof ACK only. The unchanged frontend owner polling and public-key confirmation still establish pairing.

Before every request, `runtime.withOwnedContext` rereads the protected runtime file, matching actual owned child PID, pinned public key, port and instance ID. The bridge separately verifies a fresh nonce signature. For protected operations it uses the **same dedicated TCP socket** for that verification and the subsequent operation. Ticket/body headers are installed only after the exact verified socket is assigned and still current. The private HTTP Agent permits one socket creation; closure, replacement, reconnect, redirect, stop or ownership loss fail closed. There are no POST retries or unbounded credential queues.

Limits: 16 concurrent bridge calls, 10-second total timeout, 1 MiB request body, 8 MiB operation response, 8 KiB status and 64 KiB proof response. Incoming browser auth/cookie/proxy-auth is rejected. Outgoing Origin, host, ticket/tenant and JSON headers are controlled; certificate validation remains enabled. Unknown or timed-out operation outcomes stay errors, never synthetic success.

The two tracked frontend overrides retain original discovery/pinning semantics and replace only the transport URLs in `localDac.ts`. `LocalDacStatus.vue` uses explicit same-origin connection submission and removes Windows MSI installation controls. `localDacConnection.ts` remains byte-identical. Source and override hashes, overlay version and staged source-tree hash are recorded in the frontend manifest and checked by verification/packaging.

Protocol sources at baseline d4f6918d67c3f5b32559d7eba6deb6e9a847858a: Vue `components/DeviceCollection`, DAC `DesktopTicketFilter`, `DesktopCollectionController`, `DesktopStatusController`, Windows `Launcher.cs`, and integration `DacDesktopConnect{Controller,ProofController,Service,Store}.java`. The server implements a 90-second tenant/user-owned challenge with nonce hash, signature/environment checks and TTL-preserving atomic confirmation/cancellation. The signed status itself does not contain the tenant or request ID; the server challenge binds those fields.

Tests use in-memory transport and synthetic children, not real TCP, Java or Electron. Missing service JAR keeps the actual runtime closed. Real keyring, installation, TLS/backend, Vue navigation, pairing and protected-operation acceptance are still required.
