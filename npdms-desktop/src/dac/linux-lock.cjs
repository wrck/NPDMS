'use strict';
const fs = require('node:fs');
const fsp = fs.promises;
const path = require('node:path');
const cp = require('node:child_process');
const { ensurePrivateDirectory } = require('./identity.cjs');
// flock(2) attaches to the open file description, shared across inherited fd 3.
// Do NOT execute flock -u: closing the parent's FD preserves the child's lock.
function createLinuxLockHost() {
  let lease = null,
    acquiring = false;
  async function acquireLock(directory) {
    if (lease || acquiring) throw Error('DAC lifetime lock already owned');
    acquiring = true;
    let handle;
    try {
      await ensurePrivateDirectory(directory);
      const file = path.join(directory, 'service.lock');
      handle = fs.openSync(
        file,
        fs.constants.O_RDWR |
          fs.constants.O_CREAT |
          fs.constants.O_NOFOLLOW |
          fs.constants.O_NONBLOCK,
        0o600,
      );
      const st = fs.fstatSync(handle);
      if (
        !st.isFile() ||
        st.nlink !== 1 ||
        st.uid !== process.getuid() ||
        (st.mode & 0o777) !== 0o600
      )
        throw Error('Untrusted DAC lifetime lock');
      await new Promise((resolve, reject) => {
        const helper = cp.spawn(
          '/usr/bin/flock',
          ['--exclusive', '--nonblock', '--conflict-exit-code', '73', '3'],
          { stdio: ['ignore', 'ignore', 'ignore', handle], shell: false },
        );
        helper.once('error', reject);
        helper.once('exit', (code) =>
          code === 0
            ? resolve()
            : reject(
                Error(
                  code === 73
                    ? 'DAC lifetime lock occupied'
                    : 'DAC lifetime lock failed',
                ),
              ),
        );
      });
      const owned = { handle, directory };
      lease = owned;
      handle = null;
      let released = false;
      return () => {
        if (released) return;
        released = true;
        if (lease === owned) lease = null; // close synchronously so a following acquire sees the correct state.
        fs.closeSync(owned.handle);
        owned.handle = null;
      };
    } finally {
      acquiring = false;
      if (handle !== undefined && handle !== null) fs.closeSync(handle);
    }
  }
  function spawn(command, args, options) {
    if (!lease) throw Error('DAC lifetime lock required');
    if (
      options?.cwd !== lease.directory ||
      options.shell !== false ||
      !Array.isArray(options.stdio) ||
      options.stdio.length !== 3
    )
      throw Error('Invalid DAC locked launch');
    return cp.spawn(command, args, {
      ...options,
      stdio: [...options.stdio, lease.handle],
    });
  }
  return { acquireLock, spawn };
}
module.exports = { createLinuxLockHost };
