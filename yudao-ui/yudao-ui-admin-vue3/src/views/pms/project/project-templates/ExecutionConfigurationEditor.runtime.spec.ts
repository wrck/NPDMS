import { defineComponent, h, nextTick, reactive } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import Editor from './ExecutionConfigurationEditor.vue'
import { emptyDesignerDocument } from '@/api/pms/project/project-templates'
import type { NodeExecutionConfiguration } from '@/api/pms/project/project-templates/execution'
import { getBusinessResultCatalog } from '@/api/pms/project/project-templates/execution'
import { getBusinessOperationCatalog } from '@/api/pms/project/project-templates/operations'
import { mount, passthrough, type TestNode } from '../../platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/api/pms/project/project-templates/execution', () => ({ getBusinessResultCatalog: vi.fn() }))
vi.mock('@/api/pms/project/project-templates/operations', () => ({ getBusinessOperationCatalog: vi.fn() }))
vi.mock('./RuleSlotEditor.vue', () => ({ default: { render: () => null } }))
const result = { ownerContext: 'SOL', entityType: 'SITE_SURVEY', resultType: 'SURVEY_CONFIRMED', currentLookup: true,
  exactLookup: false, historicalLookup: false, inventory: true, changes: true, commitBarrier: true }
beforeEach(() => {
  vi.mocked(getBusinessResultCatalog).mockResolvedValue([result])
  vi.mocked(getBusinessOperationCatalog).mockResolvedValue([{ operationCode: 'SOL.SITE_SURVEY.CONFIRM', operationVersion: 1,
    permissionCode: 'pms:sol-site-survey:update', label: '确认', ownerAction: 'CONFIRM', runtimeAvailable: true, checkpoints: ['PRE', 'POST'] }])
})
const find = (node: TestNode, label: string): TestNode | undefined => node.props?.['aria-label'] === label
  ? node : node.children.map(child => find(child, label)).find(Boolean)
const setup = () => {
  const state = reactive({ configuration: {} as NodeExecutionConfiguration, readonly: false })
  const page = mount(defineComponent({ setup: () => () => h(Editor, {
    modelValue: state.configuration, document: emptyDesignerDocument(), readonly: state.readonly,
    binding: { type: 'BUSINESS_COMPONENT', targetContextCode: 'SOL', targetObjectType: 'SITE_SURVEY', componentKey: 'SOL_SITE_SURVEY', parameters: {} },
    'onUpdate:modelValue': value => { state.configuration = value }
  }) }), {}, Object.fromEntries(['ElSelect', 'ElOption', 'ElDivider', 'ElCheckbox', 'ElInput'].map(name => [name, passthrough])))
  const select = (label: string, value: string) => {
    const node = find(page.root, label); expect(node).toBeDefined()
    ;(node!.props!['onUpdate:modelValue'] as (value: string) => void)(value)
  }
  return { ...page, state, select }
}
const settle = async () => { await Promise.resolve(); await Promise.resolve(); await nextTick() }

it('keeps operation, subscription and page configuration independent and omits technical versions', async () => {
  const page = setup()
  try {
    await settle()
    page.select('添加结果订阅', JSON.stringify([result.ownerContext, result.entityType, result.resultType])); await nextTick()
    expect(page.state.configuration.subscriptions).toHaveLength(1)
    expect(page.state.configuration.operations).toBeUndefined()
    expect(page.state.configuration.presentation).toBeUndefined()
    page.select('按权限选择业务动作', 'SOL.SITE_SURVEY.CONFIRM'); await nextTick()
    expect(page.state.configuration.operations?.[0]).toMatchObject({ permissionCode: 'pms:sol-site-survey:update', pre: { mode: 'NONE' }, post: { mode: 'NONE' } })
    expect(page.state.configuration.operations?.[0]).not.toHaveProperty('operationVersion')
    page.select('业务页面地址', '/pms/delivery-business/site-survey'); await nextTick()
    expect(page.state.configuration.presentation?.pageUrl).toBe('/pms/delivery-business/site-survey')
    expect(page.state.configuration.subscriptions).toHaveLength(1)
  } finally { page.app.unmount() }
})

it('does not select an ambiguous operation version or mutate a readonly configuration', async () => {
  const one = (await getBusinessOperationCatalog('SOL', 'SITE_SURVEY'))[0]
  vi.mocked(getBusinessOperationCatalog).mockResolvedValue([one, { ...one, operationVersion: 2 }])
  const page = setup()
  try {
    await settle(); page.select('按权限选择业务动作', one.operationCode)
    expect(page.state.configuration.operations).toBeUndefined()
    page.state.readonly = true; await nextTick()
    page.select('业务页面地址', '/pms/delivery-business/site-survey')
    expect(page.state.configuration).toEqual({})
  } finally { page.app.unmount() }
})
