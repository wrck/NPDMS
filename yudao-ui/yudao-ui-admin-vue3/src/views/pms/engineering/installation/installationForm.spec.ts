import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

describe('installation form contract', () => {
  const source = readFileSync(new URL('./index.vue', import.meta.url), 'utf8')

  it('omits an unselected installation time instead of submitting an empty string', () => {
    assert.match(source, /installTime: undefined/)
    assert.doesNotMatch(source, /installTime: ''/)
  })

  it('fills the site manually instead of maintaining a structured location reference', () => {
    assert.match(source, /站点未维护时手动填写站点/)
    assert.doesNotMatch(source, /PmsLocationSelector/)
    assert.doesNotMatch(source, /locationMaintenance:\s*\{/)
  })

  it('defaults the installer to the current user with a dropdown selection', () => {
    assert.match(source, /installerUserId: userStore\.getUser\.id/)
    assert.match(source, /getSimpleUserList/)
  })
})
