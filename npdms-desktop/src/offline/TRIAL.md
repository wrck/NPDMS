# Synthetic offline trial

This is an explicitly **TEST-ONLY** demonstration, not production offline policy.
It never enables NPDMS business authorization, uses real tenant/user credentials,
contacts any HTTP endpoint, or modifies a real record. Every returned data envelope
includes `synthetic: true`, `trialOnly: true`, `businessAuthorized: false`,
`externalNetwork: false`, a trial notice and the test grant label.

## Trusted main-process setup

`createTrialSession({directory, key, clock?})` from `./trial.cjs` returns the narrow
session API below. `directory` is a host-selected private directory, `key` is a
32-byte Buffer supplied by the host key vault, and `clock` optionally supplies
integer epoch milliseconds for tests. Never expose setup options or the key to a
renderer. Unknown setup properties, including credentials, endpoints, arbitrary
identity, grants and transport callbacks, are rejected. Production credentials
are neither needed nor accepted as inputs. The injected key is solely an encryption
key for synthetic local data, not a backend credential.

Fixed namespace: tenant `npdms-synthetic-trial-v1`, user `synthetic-test-user`,
dataset `synthetic-drafts-v1`. Fixtures are fixed non-sensitive test strings; users
cannot insert free text or actual case data. The underlying `OfflineStore` uses
AES-256-GCM scope-bound authenticated encryption and durable queue semantics.

A **TEST-ONLY 15-minute** grant starts when this trial directory is first created.
This duration is invented for the synthetic demonstration only and is not a claim
about any NPDMS lease policy. The authenticated lease file stores public test
metadata, not secrets; its HMAC key is domain-separated from the vault key. The
vault stores the records and queued operations only as ciphertext. Restart uses
the same expiration, not a new 15-minute period. No renewal or reset API exists.
After expiry, the host may explicitly create a separate new synthetic trial
directory if it wants another demonstration; this does not revive the old queue.

Use one session per directory, with the app's exclusive single-instance ownership.
The foundation's documented `recoverAbandonedLock` can recover a dead process
under that exclusive host lock; this trial does not automatically remove locks.
A partial/missing/tampered lease or corrupted vault remains blocked. Do not silently
reset or delete it. Existing foundational limitations concerning local clock
manipulation, file rollback, Windows durability and OS key protection still apply.

## API and result envelopes

- `status()` returns flags, `state` (`active`, `expired`, or `blocked`), `code`
  (null or a safe error code), `expiresAt`, and `pendingCount`. Count is null when
  blocked/expired; no records are read after expiry. This is suitable for an always
  visible trial banner and expiry message. Storage errors may still throw.
- `list()` returns flags, detached `drafts` and sanitized `queue` arrays. Each draft
  has `id`, `fixtureId`, fixed `title`, `synthetic: true`, and `kind: 'test-draft'`.
  Each queue entry contains `id`, `draftId`, `scenario`, `state`, `attempts`, and
  `nextAttemptAt`. No key, lease MAC, file path or authentication data is exported.
- `addDraft({fixtureId?, scenario?} = {})` accepts only fixture `draft-alpha`
  (default) or `draft-beta`, and scenario `accepted` (default), `retry`, or
  `conflict`. Returns flags, queue `id`, and the generated `draft`. Record UUIDs
  are generated locally; all content comes from fixed fixtures. Draft and queue
  insertion are a single encrypted transaction. Maximum 100 retained drafts.
- `await syncSimulated()` processes at most the ordered queue head. Returns flags,
  a queue outcome `state` and, when present, operation `id`. `accepted` succeeds
  locally; `retry` returns a simulated retry on its first attempt and acceptance
  after the durable retry deadline; `conflict` blocks until explicit resolution.
  Queue backoff, attempts, IDs and conflict state survive restart. `waiting` means
  wait until `nextAttemptAt`; `empty` means no queued operation. This is a local
  deterministic simulator, not a transport mock that can be pointed at a server.
- `resolveConflict({id, action})` requires an actually conflicted operation.
  `action: 'retryAccepted'` substitutes a synthetic accepted operation with a new
  idempotency key. `action: 'discard'` removes only its queued operation. Returns
  flags, replacement `id` (null for discard), and `action`.
- `close()` releases the store and zeroes its copied encryption key. The host owns
  the original key Buffer and should clear it when its own key lifetime ends.

Accepted/discarded test drafts remain as local synthetic records; do not label
them real synchronized records or infer business state from queue absence. There
is no server-side reconciliation. `list`, `addDraft`, `syncSimulated`, and
`resolveConflict` reject after expiry with `AUTH_EXPIRED`. Close remains available.
Overlapping mutations reject with `SYNC_BUSY`. All input errors are safe codes.
The host should map codes to plain explanations and never forward arbitrary stack
traces or key-vault error details to the renderer.

## Tests

From the desktop package: `node --test test/offline-*.test.cjs`.
Trial tests cover encrypted fixtures, restart-stable retry identifiers, persisted
conflict blocking, explicit resolution/discard, unchanged expiry after restart,
credential/endpoint/free-text rejection, lease tamper/wrong key/missing lease,
corrupt ciphertext, clock rollback, and concurrent mutation. The foundation suite
also performs a real child-process crash/recovery test. No Electron startup,
backend connection or production authorization is exercised by these tests.
