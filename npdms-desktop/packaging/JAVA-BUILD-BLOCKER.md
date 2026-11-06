# Java reactor failure: evidence and unvalidated fixture proposal

The prior reactor remains failed: 294 tests, 293 passes, one failure, later modules not completed and no service JAR. No Java build or service was rerun during this diagnosis. This document is not evidence of a fix.

At fixed baseline `d4f6918d67c3f5b32559d7eba6deb6e9a847858a`, inspect:

- `device-ops-platform/device-ops-adapter-ssh-mina/src/test/java/com/dp/plat/deviceops/adapter/ssh/mina/MinaCommandExecutionAdapterTest.java`
- The same module's `MinaCommandExecutionAdapter.java` implementation.

The failure occurred after about 5.013 seconds at the host-key arrival assertion, test line 432: only two of eight sessions overlapped. The later fingerprint-rejection and output-isolation assertions did not execute. The result proves neither correct isolation nor cross-contamination.

The embedded server's key-provider callback waits on a latch until the test releases all host keys (lines 90–96, 793–797 and 1076–1081). The test waits for all eight sessions before releasing that latch (lines 399–400 and 432–438). A bounded server I/O pool smaller than eight can therefore block its own remaining arrivals. Read-only inspection of the cached SSHD 2.19.0 dependency shows a default NIO worker count of `availableProcessors() + 1` and a fixed asynchronous-channel thread pool. A one-CPU JVM would have two workers, but the prior JVM CPU count/thread stacks were not recorded, so the exact cause remains unproven.

A second timing hazard is the three-second connection/authentication budget versus five seconds allowed for arrivals. Increasing a timeout alone would not establish correctness or address starvation. No production-code serialization or shared-output defect was demonstrated by the static review.

## Proposed review, not an applied or validated patch

Give the embedded test server an explicit I/O-worker budget sufficient for all eight intentionally blocked sessions before starting it, instead of depending on host CPU count. Preserve all eight-session overlap, four-command overlap, fingerprint-rejection, per-call output and shared-client lifetime assertions. Review handshake and rendezvous deadlines together, without skipping assertions or weakening the intended concurrency.

Only in an authorized Java validation environment: record the actual worker/CPU settings and diagnostic stacks, validate the fixture proposal with the targeted suite, then run the full reactor and existing package verification. Do not infer success from dependency metadata, compile-free edits or an increased timeout. The real JAR, DAC startup, Linux installation and business checks remain outstanding.
