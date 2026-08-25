import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

describe('PmsLocationSelector contract', () => {
  const source = readFileSync(new URL('./index.vue', import.meta.url), 'utf8')

  it('supports existing, new and fallback location modes', () => {
    assert.match(source, /选择已有地点/)
    assert.match(source, /现场维护新地点/)
    assert.match(source, /UNRESOLVED/)
  })

  it('submits a nested location maintenance command', () => {
    assert.match(source, /address:/)
    assert.match(source, /site: \{ siteType: 'CUSTOMER_SITE' \}/)
    assert.match(source, /siteLocation:/)
    assert.match(source, /fallbackLocation/)
  })

  it('references an existing location without mutable fields', () => {
    assert.match(source, /id: location\.id,\s*expectedVersion: location\.version/)
    assert.doesNotMatch(
      source,
      /id: location\.id,\s*expectedVersion: location\.version,\s*(code|name|locationType|treeSort):/
    )
  })

  it('does not cap the site location tree depth', () => {
    assert.match(source, /位置树不限定层级/)
    assert.doesNotMatch(source, /maxDepth|depthLimit/)
  })
})
