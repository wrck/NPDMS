import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

describe('installation form contract', () => {
  const source = readFileSync(new URL('./index.vue', import.meta.url), 'utf8')

  it('omits an unselected installation time instead of submitting an empty string', () => {
    assert.match(source, /installTime: undefined/)
    assert.doesNotMatch(source, /installTime: ''/)
  })

  it('references an existing location without mutable fields', () => {
    assert.match(source, /id: row\.siteLocationId,\s*expectedVersion: row\.siteLocationVersion/)
    assert.doesNotMatch(
      source,
      /id: row\.siteLocationId,\s*expectedVersion: row\.siteLocationVersion,\s*(code|name|locationType|treeSort):/
    )
  })
})
