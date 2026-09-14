import { nextTick, reactive } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import type { DefinitionSave } from '@/api/pms/project/project-templates/definitions'
import DefinitionForm from './DefinitionForm.vue'
import {
  mount, passthrough, type TestNode
} from '../../platform/dynamic-form/components/runtimeTestHarness'

vi.mock('./DefinitionSelect.vue', () => ({ default: { render: () => null } }))
vi.mock('./BusinessViewSelect.vue', () => ({ default: { render: () => null } }))
vi.mock('./RuleDecisionDesigner.vue', () => ({ default: { render: () => null } }))

const controls = {
  ElSelect: passthrough, ElOption: passthrough, ElInput: passthrough,
  ElRadioGroup: passthrough, ElRadio: passthrough
}
const find = (node: TestNode, predicate: (node: TestNode) => boolean): TestNode | undefined =>
  predicate(node) ? node : node.children.map((child) => find(child, predicate)).find(Boolean)
const change = async (node: TestNode, value: string) => {
  ;(node.props!['onUpdate:modelValue'] as (value: string) => void)(value)
  await nextTick()
}

describe('optional stage asset editing', () => {
  it('retains a custom stage code after editing and reopening the serialized definition', async () => {
    const model = reactive<DefinitionSave>({
      definitionKind: 'STAGE', definitionCode: 'PREPARATION', schemaVersion: 1,
      payload: { name: '工前准备', stageCode: 'S0', start: true, terminal: false },
      references: []
    })
    const mounted = mount(DefinitionForm, { model }, controls)
    const input = find(mounted.root, (node) => node.props?.placeholder === '输入自定义编码，或选择 S0～S6 预设')!
    expect(input.props).toMatchObject({ filterable: '', 'allow-create': '' })
    await change(input, 'PREP_WORK')
    expect(model.payload.stageCode).toBe('PREP_WORK')
    const reopened = reactive<DefinitionSave>(JSON.parse(JSON.stringify(model)))
    mounted.app.unmount()
    const restored = mount(DefinitionForm, { model: reopened }, controls)
    expect(find(restored.root, (node) => node.props?.modelValue === 'PREP_WORK')).toBeDefined()
    expect(find(restored.root, (node) => node.props?.value === 'S6')).toBeDefined()
    restored.app.unmount()
  })

  it('keeps a custom stage completion reference when switching between custom and preset values', async () => {
    const model = reactive<DefinitionSave>({
      definitionKind: 'GATE', definitionCode: 'READY', schemaVersion: 1,
      payload: { gateType: 'ENTRY', references: [{ refType: 'STATE', refCode: 'S0_COMPLETED' }] },
      references: []
    })
    const mounted = mount(DefinitionForm, { model }, controls)
    const input = find(mounted.root, (node) => node.props?.placeholder === '阶段编码_COMPLETED，例如 PREP_WORK_COMPLETED')!
    expect(input.props).toMatchObject({ filterable: '', 'allow-create': '' })
    await change(input, 'PREP_WORK_COMPLETED')
    expect(model.payload.references).toEqual([{ refType: 'STATE', refCode: 'PREP_WORK_COMPLETED' }])
    await change(input, 'S6_COMPLETED')
    expect(model.payload.references[0].refCode).toBe('S6_COMPLETED')
    await change(input, 'Discovery.v2_COMPLETED')
    const restored = mount(DefinitionForm, { model: JSON.parse(JSON.stringify(model)), disabled: true }, controls)
    expect(find(restored.root, (node) => node.props?.modelValue === 'Discovery.v2_COMPLETED')).toBeDefined()
    expect(find(restored.root, (node) => node.props?.disabled === true)).toBeDefined()
    mounted.app.unmount()
    restored.app.unmount()
  })
})
