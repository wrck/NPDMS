# Encrypted offline foundation (new reconstruction)

This is a reusable main-process component, not an NPDMS API integration or approval
of any offline business operation. No endpoints, backend roles, lease durations,
key policy, conflict rules or PRD requirements are invented here. It is disabled
unless the caller supplies every required trust dependency.

## Required integration contract

`new OfflineStore({file, binding, grant, key, verifyGrant, authorize, clock})`

- `binding`: exact nonempty `tenantId`, `userId`, `datasetId` strings.
- `grant`: the same binding, integer `expiresAt` in milliseconds and nonempty
  `keyId`. Additional verified grant fields may be included. The trusted host must
  validate issuer, signature, audience, issuance time, permitted dataset and
  actions, and the server-approved TTL in `verifyGrant(grant, binding)`. Return
  literal `true` only after all checks. A renderer must never supply this callback
  or its own trusted grant. There is no built-in grant verification scheme.
- `key`: a 32-byte Buffer from a trusted key manager. Never persist it with the
  vault or send it to the renderer. The module copies it and zeroes the copy on
  close. OS key storage, rotation, key recovery, destruction and identity logout
  remain host integration requirements. JS cannot guarantee memory erasure.
- `authorize({binding, grant, action, operation, now})`: synchronous trusted policy
  returning literal `true` for allowed actions. Actions are `open`, `read`,
  `readQueue`, `writeWorkingSet`, `enqueue`, `persist`, `resolveConflict`, `sync`,
  and `send`. Deny unknown actions. Enforce allowed operation schemas, payload
  classification, exact scope and business rules here. Callbacks must not have
  reentrant side effects. Tests' allow-all policies are synthetic fixtures only.
- `clock`: trusted epoch-millisecond time (defaults to Date.now). Expiry is checked
  before all reads/writes/sends and after transport. A detected backward jump
  blocks access. Production must supply a trusted time/lease strategy; the local
  clock and encrypted snapshots alone cannot prevent offline clock manipulation
  or replay of an older authentic file. A server lease/revocation contract and
  rollback-resistant anchor are required for those guarantees.

## Operations

`read()` / `setWorkingSet(json)` access a JSON working set. `enqueue(operation,
optionalWorkingSet)` atomically saves an operation and optional working-set
change, returning a UUID idempotency key. `pending()` returns a detached copy.
Bounded technical limits: 8 MiB decrypted snapshot and 1000 queued operations.
There are no default enabled business operations.

`await syncOne(transport)` sends only the queue head. The injected transport gets
`{binding, idempotencyKey, operation}` and returns `{state}` with one of these
**adapter-local** outcomes (not assumed server statuses):

- `accepted`: remove the operation after durable server acknowledgement.
- `retry`: retain the operation for exponential retry (1 to 60 seconds).
- `conflict` or `rejected`: block the ordered queue until explicit resolution.
- `unauthorized`: persist revocation and lock this vault, including on reopen.

Thrown transport errors are treated as uncertain delivery and safely retain the
operation; their message/body is never logged or persisted. Crash and lost-response
retries use the same idempotency key and payload. The server must implement durable
idempotency scoped to identity/dataset and return its original outcome on retries;
client UUIDs alone cannot provide exactly-once execution. The server must also
recheck authorization when committing a request already in flight at lease expiry.
Unknown adapter results never acknowledge operations. Transport must impose its
own timeout and validate the authenticated response. Requests cannot be cancelled
by this module once dispatched. No transport response body enters the working set.

`resolve(id)` explicitly discards a blocked operation; `resolve(id, replacement)`
replaces it with a newly authorized operation and **new** idempotency key. No
automatic merge or destructive server action is implemented. Host UI must clearly
present the consequence and require an explicit decision. Reconciliation of the
working set after server acceptance/conflict requires the real backend contract.

`close()` zeroes the held key and releases ownership. Expired grants cannot read,
write, resolve or sync. There is no automatic renewal. Revocation remains locked;
a trusted reauthorization/migration/recovery workflow is intentionally not invented.

## Persistence and operating requirements

AES-256-GCM with a new random 96-bit nonce per snapshot authenticates schema,
tenant, user, dataset and key ID as additional data. Only ciphertext, IV, tag and
format version are stored in the vault and temporary files. Atomic same-directory
rename follows file fsync; POSIX additionally fsyncs the parent directory. Windows
power-loss semantics require platform validation. Parent directories and files
request private permissions (0700/0600); Windows ACL enforcement is a host task.

Use a private local, non-symlink, non-shared directory under application ownership.
Do not use network shares. An exclusive lock file prevents concurrent instances.
Call `recoverAbandonedLock(file)` only under an exclusive **application startup
lock** (e.g. Electron single-instance ownership), on the same host/PID namespace.
It removes only a lock whose recorded PID demonstrably no longer exists. Live or
reused PIDs, malformed locks and uncertain ownership remain blocked for operator
recovery. It must not race another process starting this store. Lock files contain
only a process ID, never keys or records.

On crash, the last committed snapshot remains authoritative; orphan temporary
files are not promoted. Retained inflight operations retry using their stable ID.
Do not automatically delete a corrupt vault, a lock with unknown ownership, or
orphan ciphertext. Backups/restoration and retention need an approved policy.
On uncertain persistence failure the instance is poisoned and must be closed and
reopened before further use. A filesystem attacker can delete or roll back files;
authenticated encryption detects alteration, not rollback/deletion. This module
is not a replacement for device access controls or server policy enforcement.

## Verification

Run `node --test test/offline-*.test.cjs` from the desktop package. Tests exercise
synthetic policies only; no NPDMS server, production keys, OS keychain or packaged
Electron/Windows filesystem guarantees have been tested by these unit tests.
