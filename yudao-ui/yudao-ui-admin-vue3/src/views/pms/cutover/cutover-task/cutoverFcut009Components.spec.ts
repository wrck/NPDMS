import { nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import type {
  CutoverConfiguration,
  CutoverNavigationTarget
} from '@/api/pms/cutover/cutover-config'
import CutoverConfigurationEditor from '../cutover-config/components/CutoverConfigurationEditor.vue'
import {
  findByTestId,
  mount,
  passthrough,
  tableColumn
} from '../../platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({ error: vi.fn() })
}))
vi.mock('@/utils/dict', () => ({
  DICT_TYPE: {},
  getStrDictOptions: () => []
}))

const controls = Object.fromEntries([
  'ElForm', 'ElFormItem', 'ElTabs', 'ElTabPane', 'ElRow', 'ElCol', 'ElInput',
  'ElRadioGroup', 'ElRadio', 'ElRadioButton', 'ElButton', 'ElTable', 'ElSelect',
  'ElOption', 'ElSwitch', 'ElInputNumber', 'ElCheckbox', 'ElEmpty', 'ElAlert',
  'CutoverRiskMatrixEditor', 'CutoverSurveyMatrixEditor'
].map((name) => [name, passthrough]))
controls.ElTableColumn = tableColumn

describe('F-CUT-009 configuration navigation target', () => {
  it.each([
    ['CURRENT_STAGE_WORKBENCH', 'TASK_OVERVIEW'],
    ['TASK_OVERVIEW', 'CURRENT_STAGE_WORKBENCH']
  ] as Array<[CutoverNavigationTarget, CutoverNavigationTarget]>)
  ('renders %s and saves the selected %s target without a condition editor', async (initial, selected) => {
    const model = configuration(initial)
    const updates: CutoverConfiguration[] = []
    const mounted = mount(CutoverConfigurationEditor, {
      modelValue: model,
      readonly: false,
      validationErrors: [],
      'onUpdate:modelValue': (value: CutoverConfiguration) => updates.push(value)
    }, controls)

    const target = findByTestId(mounted.root, 'post-submit-navigation-target')!
    expect(target.props?.modelValue).toBe(initial)
    await (target.props?.['onUpdate:modelValue'] as (value: CutoverNavigationTarget) => void)(selected)
    await nextTick()

    expect(model.navigationRule).toEqual({ target: selected })
    expect(JSON.stringify(model.navigationRule)).not.toContain('condition')
    expect(updates).toEqual([])
    mounted.app.unmount()
  })

  it('projects a historical null rule as the locked current-stage default', () => {
    const mounted = mount(CutoverConfigurationEditor, {
      modelValue: { ...configuration('CURRENT_STAGE_WORKBENCH'), navigationRule: null },
      readonly: true,
      validationErrors: []
    }, controls)

    expect(findByTestId(mounted.root, 'post-submit-navigation-target')?.props?.modelValue)
      .toBe('CURRENT_STAGE_WORKBENCH')
    mounted.app.unmount()
  })
})

const configuration = (target: CutoverNavigationTarget): CutoverConfiguration => ({
  configurationCode: 'CUT-DEFAULT',
  configurationName: '默认割接配置',
  navigationRule: { target },
  dictionarySnapshot: {},
  dimensions: [],
  planTemplateSections: [],
  items: [],
  bindingRules: []
})
