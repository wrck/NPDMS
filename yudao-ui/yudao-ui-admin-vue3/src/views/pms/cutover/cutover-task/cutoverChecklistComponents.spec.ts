import { defineComponent, h, nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import CutoverChecklistField from './components/CutoverChecklistField.vue'
import {
  findByTestId,
  mount,
  passthrough
} from '../../platform/dynamic-form/components/runtimeTestHarness'

const fileApi = vi.hoisted(() => ({ getArtifact: vi.fn(), getVersions: vi.fn() }))
vi.mock('@/api/pms/platform/file', () => fileApi)
vi.mock('@/components/PmsFileArtifact', () => ({
  PmsFileUploader: defineComponent({
    inheritAttrs: false,
    emits: ['completed'],
    setup(_, { attrs, emit }) {
      return () => h('button', {
        ...attrs,
        onClick: () => emit('completed', {
          artifactId: '9007199254740993', versionNo: 4, referenceKey: 'manual-proof'
        })
      }, 'upload')
    }
  })
}))

const controls = {
  ElInput: passthrough,
  ElSelect: passthrough,
  ElOption: passthrough,
  ElTag: passthrough
}
const item = {
  itemId: '9007199254740995',
  stableItemKey: 'risk-check',
  itemTypeCode: 'RISK',
  itemName: '风险核查',
  itemDescription: '确认现场风险',
  interfaceFormatCode: 'TEXT',
  interfaceSchemaSnapshot: null,
  workModeCode: 'DIRECT' as const,
  required: true,
  sourceCode: 'SYSTEM_MATCHED' as const,
  applicable: true,
  sortOrder: 10,
  currentResult: null
}

describe('F-CUT-003 mounted checklist field', () => {
  it('emits direct answer and freezes PLT public fact without converting Snowflake ids', async () => {
    fileApi.getArtifact.mockResolvedValue({
      artifactVersion: 2,
      reference: { scopeVersion: 17, referenceVersion: 3 }
    })
    fileApi.getVersions.mockResolvedValue({
      items: [{ versionNo: 4, availabilityVersion: 5 }]
    })
    const direct: unknown[][] = []
    const manual: unknown[][] = []
    const mounted = mount(CutoverChecklistField, {
      item,
      directValue: '',
      readonly: false,
      onDirect: (...args: unknown[]) => direct.push(args),
      onManual: (...args: unknown[]) => manual.push(args)
    }, controls)

    const input = findByTestId(mounted.root, 'checklist-input')!
    await (input.props?.['onUpdate:modelValue'] as (value: string) => void)('已核查')
    const uploader = findByTestId(mounted.root, 'manual-uploader')!
    await (uploader.props?.onClick as () => Promise<void>)()
    await nextTick()

    expect(direct).toEqual([['risk-check', '已核查']])
    expect(manual[0][0]).toBe('risk-check')
    expect(manual[0][1]).toMatchObject({
      artifactId: '9007199254740993',
      scopeVersion: 17,
      fileFactVersion: { artifactVersion: 2, referenceVersion: 3, availabilityVersion: 5 }
    })
    expect(typeof (manual[0][1] as { artifactId: unknown }).artifactId).toBe('string')
    mounted.app.unmount()
  })
})
