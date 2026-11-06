'use strict';
const assert = require('node:assert/strict');
const { expectedOverlayProvenance } = require('./frontend-overlays.cjs');

function verifyOverlayProvenance(provenance) {
  assert.ok(provenance && typeof provenance === 'object' && !Array.isArray(provenance), 'Desktop overlay provenance missing');
  assert.deepEqual(Object.keys(provenance).sort(), ['overlayVersion', 'sourceTreeSha256', 'sources', 'unchangedContracts'], 'Unexpected desktop overlay provenance');
  assert.match(provenance.sourceTreeSha256, /^[a-f0-9]{64}$/, 'Invalid desktop source tree hash');
  const { sourceTreeSha256, ...declared } = provenance;
  assert.deepEqual(declared, expectedOverlayProvenance(), 'Desktop overlay provenance differs from checked source');
  return { overlayVersion: provenance.overlayVersion, sourceTreeSha256 };
}
module.exports = { verifyOverlayProvenance };
