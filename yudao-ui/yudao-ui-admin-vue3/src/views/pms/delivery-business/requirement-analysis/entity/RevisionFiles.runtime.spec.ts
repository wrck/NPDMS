import { defineComponent, h, nextTick } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import RevisionFiles from './RevisionFiles.vue'
import { stableCommandIntent } from '@/views/pms/platform/dynamic-form/components/dynamicFormRuntime'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/components/PmsFileArtifact', () => ({
  PmsFileReferenceList: defineComponent(() => () => h('span', 'files')),
  PmsFileUploader: defineComponent(() => () => h('span', 'upload'))
}))

const insecureCrypto = () => {
  const getRandomValues = crypto.getRandomValues.bind(crypto)
  vi.stubGlobal('crypto', { getRandomValues })
}
afterEach(() => vi.unstubAllGlobals())

describe('requirement analysis without secure-context randomUUID', () => {
  it('mounts attachment fields on HTTP without crashing the page', async () => {
    insecureCrypto()
    const view = mount(RevisionFiles, { revisionId: '11', fieldKey: 'BACKGROUND__ATTACHMENTS' })
    await nextTick()
    expect(textOf(view.root)).toContain('附件')
    view.app.unmount()
  })

  it('keeps retry identity stable and creates another key after clearing', () => {
    insecureCrypto()
    const values = new Map<string, string>()
    vi.stubGlobal('sessionStorage', {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value),
      removeItem: (key: string) => values.delete(key)
    })
    const first = stableCommandIntent('requirement-save:11', { version: 1 })
    expect(first.key).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
    expect(stableCommandIntent('requirement-save:11', { version: 1 }).key).toBe(first.key)
    first.clear()
    expect(stableCommandIntent('requirement-save:11', { version: 1 }).key).not.toBe(first.key)
  })
})
