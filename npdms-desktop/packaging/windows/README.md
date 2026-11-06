# Windows installed-service adapter candidate

This adapter reuses the existing `NpdmsDac` Windows service installed by the original
native DAC installer. It does **not** install, replace, stop or uninstall that service.
The Windows machine needs the native DAC runtime and .NET Framework 4.6.1 or later.
The native configuration's backend and web origin must both match the HTTPS origin
entered in the desktop shell. Different origins fail closed; the adapter never rewrites
the protected configuration or exports its DPAPI master key.

`ServiceBridge.cs` is compiled together with the original `ClientConfiguration.cs`.
It accepts only `snapshot` and `start`, verifies service executable/account, file and
registry ACLs, rejects reparse points/hard links, and reads only non-secret configuration
and endpoint metadata. SCM/WinSW retain Java lifecycle, original `FileShare.None` lock,
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
The bridge now reports `DAC_WINDOWS_ACCESS_DENIED` distinctly. No permissions are
broadened and no ancestor checks are removed. Do not run the entire Electron app
elevated to conceal this integration issue. A Windows-tested privileged validation
broker or another equally strong native trust boundary is still required.

`scripts/test-windows-service-access.ps1 -Helper <compiled-helper>` is a read-only
Windows check under the intended user's token. It invokes only `snapshot`, never
starts/stops services, changes ACLs, reads a key or modifies configuration. It fails
if native access or metadata checks fail. Successful execution is only an access
check, not signed bridge or business acceptance.
