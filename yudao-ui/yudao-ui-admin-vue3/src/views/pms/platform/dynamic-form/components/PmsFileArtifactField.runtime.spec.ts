import { defineComponent, h, nextTick, onMounted } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import PmsFileArtifactField from './PmsFileArtifactField.vue'
import { mount, passthrough, textOf } from './runtimeTestHarness'

vi.mock('@/components/PmsFileArtifact', () => ({
  PmsFileReferenceList: defineComponent({
    props: { referenceKey: { type: String, required: true } },
    emits: ['loaded'],
    setup: (props, { emit }) => {
      onMounted(() =>
        emit('loaded', {
          reference: { referenceKey: props.referenceKey, referenceVersion: 7 }
        })
      )
      return () => h('div', `file:${props.referenceKey}`)
    }
  }),
  PmsFileUploader: defineComponent({
    props: { expectedReferenceVersion: Number },
    setup: (props) => () => h('div', `uploader:${props.expectedReferenceVersion ?? 'new'}`)
  })
}))

describe('F-PLT-002 controlled file field', () => {
  it('shows authoritative references and gates mutation with server PATCH_INSTANCE action', async () => {
    const runtimeValues: string[][] = []
    const readonly = mount(
      PmsFileArtifactField,
      {
        instanceId: 1,
        templateRevisionId: 8,
        fieldKey: 'evidence',
        currentFacts: [
          {
            artifactId: 2,
            versionNo: 3,
            referenceKey: 'slot-a',
            fileFactVersion: {},
            scopeVersion: 8,
            status: 'ACTIVE'
          }
        ],
        allowedActions: [],
        'onUpdate:modelValue': (value: string[]) => runtimeValues.push(value)
      },
      { ElAlert: passthrough, ElEmpty: passthrough }
    )
    await nextTick()
    expect(textOf(readonly.root)).toContain('file:slot-a')
    expect(textOf(readonly.root)).not.toContain('uploader')
    expect(runtimeValues.at(-1)).toEqual(['slot-a'])
    readonly.app.unmount()

    const editable = mount(
      PmsFileArtifactField,
      {
        instanceId: 1,
        templateRevisionId: 8,
        fieldKey: 'evidence',
        currentFacts: [],
        allowedActions: ['PATCH_INSTANCE']
      },
      { ElAlert: passthrough, ElEmpty: passthrough }
    )
    await nextTick()
    expect(textOf(editable.root)).toContain('uploader')
    editable.app.unmount()
  })

  it('uses the authoritative reference version immediately for an existing file slot', async () => {
    const editable = mount(
      PmsFileArtifactField,
      {
        instanceId: 1,
        templateRevisionId: 8,
        fieldKey: 'evidence',
        currentFacts: [
          {
            artifactId: 2,
            versionNo: 3,
            referenceKey: 'slot-a',
            fileFactVersion: {},
            scopeVersion: 8,
            status: 'ACTIVE'
          }
        ],
        allowedActions: ['PATCH_INSTANCE']
      },
      { ElAlert: passthrough, ElEmpty: passthrough }
    )
    await nextTick()
    expect(textOf(editable.root)).toContain('uploader:7')
    editable.app.unmount()
  })
})
