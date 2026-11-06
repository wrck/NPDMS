# Linux archive packaging

This is a **Linux x86-64 development installer**, not evidence of installation,
window launch, Java readiness, business acceptance, signing, or production readiness.
The package is unsigned. Run acceptance on an authorized Linux desktop separately.

## Build and inspect

From the desktop project after `npm ci` and `npm run install:runtime`:

```sh
node --test test/package-linux.test.cjs
node scripts/package-linux.cjs --development
node scripts/inspect-linux-package.cjs artifacts/npdms-desktop_0.1.0~development.1_amd64.deb
sha256sum -c artifacts/npdms-desktop_0.1.0~development.1_amd64.deb.sha256
```

These commands never install the archive, launch Electron/Java, connect to a server,
or change host security. Outputs and checksum sidecars stay in ignored `artifacts/`.
The current Electron version must match the exact dependency pin in package.json.
`dpkg-deb` must be available. The CLI accepts no sandbox-disable flag.

Optional staging inputs:

```sh
node scripts/package-linux.cjs --development --frontend-dir /absolute/built-vue --dac-dir /absolute/dac
```

An explicit missing staging path is always an error. Default inputs are
`resources/frontend` and `resources/dac`. They are copied beneath the installed
app's `resources/`, preserving source-relative resource resolution. Do not stage
live user data, configuration secrets, private keys, or deployment credentials.

Without `--development`, assembly fails closed unless all required payloads exist:

- `frontend/index.html`, with the full built Vue static resource tree supplied.
- `dac/device-ops-server.jar`.
- Executable `dac/jre/bin/java`, with the entire compatible Linux JRE supplied.
- `dac/parser-releases/device-command-output-1.4.0/`, with the full parser release supplied.

Payload existence checks are not functional verification or a production release
approval. The generated build-info.json records `runtimeAcceptance: not-performed`.

## Installed layout and boundaries

- `/opt/npdms-desktop/`: pinned Electron runtime; its app is `resources/app/`.
- `/usr/bin/npdms-desktop`: shell launcher, argument-preserving, no security bypass.
- `/usr/share/applications/org.npdms.desktop.desktop`: desktop menu entry.
- `/usr/share/icons/hicolor/scalable/apps/org.npdms.desktop.svg`: local app icon.

Only `src/`, `resources/`, and a reduced package.json enter the app payload, plus
non-secret build metadata. Tests, project root dotfiles, npm caches and project
node_modules are not packaged. The runtime's distributed licenses remain included.
All packaged entries have root/root ownership, ordinary 0644 or 0755 permissions,
and no setuid/setgid bits. Symbolic links and special files are rejected: stage a
self-contained, dereferenced JRE if your JRE distribution normally uses symlinks.

There are no post-install scripts, background daemons, autostart entries,
telemetry additions, uninstall hooks, or deletion of user data/keys. Electron keeps
its default sandbox protections. On systems without a supported unprivileged
sandbox, launch may fail; this package does not weaken host settings to fix that.

Dependencies are declared in linux.json, including libasound/libgtk transitions
for Debian/Ubuntu t64 releases. Supported distro and library-version compatibility
still require real installation tests. This build does not register an apt source,
configure automatic updates, or sign an installer.

## Reproducibility

The builder uses sorted input copying, normalized permissions and timestamps,
root ownership, and gzip archive compression. `SOURCE_DATE_EPOCH` defaults to
1735689600; set it explicitly for a release. Identical inputs, toolchain and epoch
produce the same archive (covered by a fixture double-build test). The filename
is versioned, with `~development.1` on development packages and a clearly labeled
menu entry. Development and production packages use the same package ID and
install directory, so they are not designed for parallel installation.

The inspector checks archive ownership/modes, absence of maintainer scripts,
launcher and desktop paths, declared dependencies, mode/version agreement and
required app files. It extracts only into a temporary directory for inspection.
It does not install, execute, mutate runtime user data, or establish business
acceptance.

## Payload integrity and provenance metadata

Set `NPDMS_SOURCE_COMMIT` to the exact source commit when creating a final archive:

```sh
NPDMS_SOURCE_COMMIT=<40-hex-commit> node scripts/package-linux.cjs --development
```

An omitted commit is recorded as null. Empty, abbreviated or non-hex values fail.
The value is declared provenance, not a signature or automatic proof of a clean
Git checkout. The caller must verify the intended source revision before building.

`resources/app/build-info.json` includes a sorted inventory with SHA256 and byte
size for **every installed app file**, excluding only build-info.json itself.
It also records the frontend baseline and exact build-manifest.json SHA256;
the frontend manifest's complete asset list is checked against the staged files.
DAC completeness is explicitly recorded with missing paths, independently from
readiness, which always remains `not-tested`. A present JRE/parser tree without
the service JAR is incomplete. Production assembly requires both the verified
frontend manifest and complete DAC payload. Hashes provide integrity checks,
not code-signing or business acceptance.

The inspector recomputes every app-file hash/size and requires an exact file
list, detecting missing, altered or unexpected app files. It also checks frontend
and DAC metadata against extracted payload contents, runtime version, commit
format, and the util-linux dependency required for `flock`. Private key files,
user-data directories and development artifact/cache directories are refused in
app payload inventory. This naming check is an extra guard, not a general secret
scanner; stage only audited, non-secret build inputs.

`StartupWMClass` is intentionally omitted because no graphical session has
verified the actual X11/Wayland window identity. build-info.json records this as
unverified. No working desktop grouping behavior is claimed.


## Existing user keyring requirement

`libsecret-1-0` is declared as a standard runtime library dependency for the
Linux safeStorage Secret Service backend. This library alone does not provide a
working unlocked keyring. The signed-in graphical user session must already have
an available supported secure keyring/Secret Service provider. If secure storage
is unavailable, locked, or reports an insecure backend, the offline trial stays
disabled. There is no plaintext or other unsafe fallback.

Packaging does not install, configure, unlock, or start any keyring daemon and
contains no maintainer scripts to do so. Installing system library dependencies
later is the user's normal package-manager operation, not an action performed by
the build or inspection commands. No live keyring or safeStorage acceptance has
been performed in this cloud environment.
