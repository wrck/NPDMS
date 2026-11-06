# Windows installed-service adapter candidate

This adapter reuses the existing `NpdmsDac` Windows service installed by the original
native DAC installer. It does **not** install, replace, stop or uninstall that service.
The Windows machine needs the native DAC runtime and .NET Framework 4.6.1 or later.
The native configuration's backend and web origin must both match the HTTPS origin
entered in the desktop shell. Different origins fail closed; the adapter never rewrites
the protected configuration or exports its DPAPI master key.

`ServiceBridge.cs` is compiled together with the original `ClientConfiguration.cs`.
It accepts only `snapshot` and `start`, verifies service executable/account, file and
registry/program/public-file ACLs, rejects redirected final paths and hard links, and reads only non-secret configuration
and endpoint metadata. Private data-root ACLs are not queried. SCM, retained query-only process handles,
full image paths, process creation order and the IPv4 listener owner establish the runtime trust boundary. SCM/WinSW retain Java lifecycle, original `FileShare.None` lock,
DPAPI storage and existing outbox behavior. Electron pins protected endpoint identity,
verifies a fresh Ed25519 status, and reuses the existing signed same-connection bridge.
Restarted or replaced metadata invalidates the connection. Disconnect/quit leaves the
shared service running; administrators manage its lifecycle through existing tools.

Build the helper with `scripts/build-windows-service-bridge.ps1 -OutputDirectory <new-dir>`.
On Windows the script uses the system .NET Framework compiler and executes ACL tests.
On Linux explicit .NET SDK/Roslyn and .NET Framework reference paths allow compile-only
checks; `bridge-manifest.json` records that Windows tests were not executed. It retains
the repository worktree build lock. Package only the helper and manifest in
`resources/windows-service/`. Never label a Linux crosscompile as Windows acceptance.

This is an installation/integration candidate, not a frozen release. Original native
MSI packaging still requires the missing `verify-deployed-repairs.py` and its Windows
validation chain. No substitute frozen manifest is generated. The command preset
catalogue remains unavailable; full offline business and Windows synthetic trial are
not enabled. Actual Windows ACL/SCM/DPAPI, signed connection, install, uninstall, update,
and UI acceptance must be completed on a Windows test machine before release.

## Standard-user access blocker found after the first candidate

The original `SecureDirectory(DataRoot, false)` intentionally grants only SYSTEM,
Administrators and LocalService access to the data root. Users can read the separate
public configuration file and runtime subtree, but do not receive READ_CONTROL on
the data root. This bridge also validates ancestor ACLs, so an ordinary account may
be denied before attachment. Node tests use an injected native host and do not prove
this ACL path works. The initial candidate is therefore **not standard-user ready**.
That initial blocker is superseded in the source by the SCM/process/listener boundary below.
The bridge still reports `DAC_WINDOWS_ACCESS_DENIED` if its query-only rights are unavailable.
Do not elevate the entire Electron app to conceal a failed check.

`scripts/test-windows-service-access.ps1 -Helper <compiled-helper>` is a read-only
Windows check under the intended user's token. It invokes only `snapshot`, never
starts/stops services, changes ACLs, reads a key or modifies configuration. It fails
if native access or metadata checks fail. Successful execution is only an access
check, not signed bridge or business acceptance.

## Current least-privilege boundary

The authenticated source of service ownership is Windows SCM (`SERVICE_QUERY_STATUS`),
not a readable private-root ACL. The helper opens only the existing public configuration
and endpoint files, checks each open handle's owner/DACL, single-link identity and resolved
final path, and parses the same handle without reopening the path. Registry and installed
program directory ACL checks remain. No data-root/credential ACL is broadened.

Before returning non-null metadata it requires all of:

1. The fixed service is RUNNING with its own service process, with the original protected
   executable path and LocalService account checked.
2. Toolhelp process ancestry is exactly WinSW → SdpDacHost → Java. Retained process handles
   request only `PROCESS_QUERY_LIMITED_INFORMATION`; full image paths, alive state and
   creation order are checked, including a second snapshot after handles are held.
3. Exactly one IPv4 listener for the candidate port binds `127.0.0.1` and belongs to that
   Java PID. Wildcard/external listeners, wrong PIDs and duplicates are rejected.
4. SCM and live process identities are checked again after the TCP table read.
5. Electron requires the native `ownershipVerified` result and still verifies fresh
   signed status and same-connection business requests. The flag never substitutes for
   cryptographic verification.

No query failure falls back to PID names, HTTP-only trust, debug privileges, elevation,
or a skipped ACL check. If Windows denies query-only process access, that remains a
blocker requiring a Windows-tested service-side verification mechanism. Linux decision
checks and crosscompilation do not prove these APIs work under a standard Windows token.

API contracts: [SCM status](https://learn.microsoft.com/en-us/windows/win32/api/winsvc/nf-winsvc-queryservicestatusex),
[process snapshot](https://learn.microsoft.com/en-us/windows/win32/api/tlhelp32/ns-tlhelp32-processentry32w),
[TCP ownership](https://learn.microsoft.com/en-us/windows/win32/api/iphlpapi/nf-iphlpapi-getextendedtcptable),
[resolved handle path](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-getfinalpathnamebyhandlew).

## Independent native-install inputs

`stage-windows-dac-candidate.py` assembles verified existing native compile outputs,
DAC JAR, parser release, WinSW 2.12.0 and the official checksum-verified Windows x64
Temurin JRE 25.0.1+8. This full JRE is a candidate input, not a claim of equivalence to
the original Windows jlink image. The script never installs services or produces a
frozen MSI manifest; the original Windows installation/upgrade/DPAPI tests remain required.
