import { defineComponent, h, nextTick, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import RevisionFiles from './RevisionFiles.vue'
import { stableCommandIntent } from '@/views/pms/platform/dynamic-form/components/dynamicFormRuntime'
import { mount, textOf, findByTestId } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/components/PmsFileArtifact', () => ({
  PmsFileReferenceList: defineComponent(() => () => h('span', 'files')),
  PmsFileUploader: defineComponent({ setup(_, { expose }) {
    const busy = ref(false)
    expose({ isBusy: () => busy.value })
    return () => h('button', { 'data-testid': 'toggle-upload', onClick: () => { busy.value = !busy.value } }, 'upload')
  } })
}))

const insecureCrypto = () => {
  const getRandomValues = crypto.getRandomValues.bind(crypto)
  vi.stubGlobal('crypto', { getRandomValues })
}
afterEach(() => vi.unstubAllGlobals())

describe('requirement analysis without secure-context randomUUID', () => {
  it('reports upload activity until the uploader finishes and clears it on unmount', async () => {
    const changed = vi.fn()
    const view = mount(RevisionFiles, { revisionId: '11', fieldKey: 'NETWORK_TOPOLOGY__ATTACHMENTS',
      allowedActions: ['PATCH_FORM'], onUploadBusyChange: changed })
    await nextTick()
    const toggle = findByTestId(view.root, 'toggle-upload')!.props!.onClick as () => void
    toggle()
    await nextTick()
    expect(changed).toHaveBeenLastCalledWith(true)
    toggle()
    await nextTick()
    expect(changed).toHaveBeenLastCalledWith(false)
    toggle()
    await nextTick()
    view.app.unmount()
    expect(changed).toHaveBeenLastCalledWith(false)
  })

  it('exposes the required topology upload before completion while old frozen files stay read only', async () => {
    const draft = mount(RevisionFiles, { revisionId: '11', fieldKey: 'NETWORK_TOPOLOGY__ATTACHMENTS', allowedActions: ['PATCH_FORM'] })
    const history = mount(RevisionFiles, { revisionId: '12', fieldKey: 'NETWORK_TOPOLOGY__ATTACHMENTS', disabled: true })
    await nextTick()
    expect(textOf(draft.root)).not.toContain('至少上传')
    expect(textOf(draft.root)).toContain('upload')
    expect(textOf(history.root)).not.toContain('至少上传')
    expect(textOf(history.root)).not.toContain('upload')
    draft.app.unmount(); history.app.unmount()
  })

  it('mounts attachment fields on HTTP without crashing the page', async () => {
    insecureCrypto()
    const view = mount(RevisionFiles, { revisionId: '11', fieldKey: 'BACKGROUND__ATTACHMENTS' })
    await nextTick()
    expect(textOf(view.root)).not.toContain('upload')
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
