import { nextTick } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import TemplateMatchPreview from './TemplateMatchPreview.vue'
import { matchPreview, type MatchRespVO } from '@/api/pms/project/project-templates'
import { getRuleFields } from '@/api/pms/project/project-templates/rules'
import { mount, passthrough, textOf, type TestNode } from '../../platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/project/project-templates', () => ({ matchPreview: vi.fn(), emptyDesignerDocument: vi.fn(), cloneDesignerDocument: vi.fn() }))
vi.mock('@/api/pms/project/project-templates/rules', () => ({ getRuleFields: vi.fn() }))
const find = (node: TestNode, test: (node: TestNode) => boolean): TestNode | undefined =>
  test(node) ? node : node.children.map(child => find(child, test)).find(Boolean)
const input = (root: TestNode, label: string) => find(root, node => node.props?.['aria-label'] === label)!
const update = (node: TestNode, value: unknown) => (node.props!['onUpdate:modelValue'] as (value: unknown) => void)(value)
const flush = async () => { await Promise.resolve(); await nextTick() }
const submit = async (root: TestNode) => {
  const form = find(root, node => typeof node.props?.onSubmit === 'function')!
  await (form.props!.onSubmit as (event: object) => Promise<void>)({ preventDefault() {} })
  await flush()
}
const setup = () => mount(TemplateMatchPreview, {}, {
  ElSelect: passthrough, ElInput: passthrough, ElOption: passthrough, ElInputNumber: passthrough, ElResult: passthrough
})
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(getRuleFields).mockResolvedValue([
    { code: 'project.projectName', label: '项目名称', valueType: 'TEXT', availableAtCreation: true },
    { code: 'project.isChild', label: '是否子项目', valueType: 'BOOLEAN', availableAtCreation: true },
    { code: 'project.customerCode', label: '客户编码', valueType: 'TEXT', availableAtCreation: true },
    { code: 'project.lifecycleStatus', label: '运行状态', valueType: 'TEXT', availableAtCreation: false }
  ])
  vi.mocked(matchPreview).mockResolvedValue({ outcome: 'NO_MATCH', conflicts: [], evaluations: [] })
})
it('uses the catalog and preserves absent, explicit null, false and text values', async () => {
  const view = setup(); await flush()
  expect(textOf(view.root)).not.toContain('运行状态')
  update(input(view.root, '试算字段'), ['project.projectName', 'project.isChild', 'project.customerCode']); await flush()
  await submit(view.root)
  expect(matchPreview).toHaveBeenLastCalledWith({ facts: {} })
  update(input(view.root, '项目名称输入状态'), 'VALUE')
  update(input(view.root, '是否子项目输入状态'), 'VALUE')
  update(input(view.root, '客户编码输入状态'), 'NULL'); await flush()
  update(input(view.root, '项目名称'), '现场工勘')
  update(input(view.root, '是否子项目'), false); await flush()
  await submit(view.root)
  expect(matchPreview).toHaveBeenLastCalledWith({ facts: {
    'project.projectName': '现场工勘', 'project.isChild': false, 'project.customerCode': null
  } })
  view.app.unmount()
})
it('discards pending results after changing facts', async () => {
  let resolve!: (result: MatchRespVO) => void
  vi.mocked(matchPreview).mockImplementation(() => new Promise(done => { resolve = done }))
  const view = setup(); await flush()
  const pending = submit(view.root)
  update(input(view.root, '试算字段'), ['project.customerCode']); await flush()
  update(input(view.root, '客户编码输入状态'), 'NULL'); await flush()
  resolve({ outcome: 'NO_MATCH', conflicts: ['旧结果'], evaluations: [] }); await pending
  expect(textOf(view.root)).not.toContain('旧结果')
  view.app.unmount()
})
it('field directory failures are visible and retryable', async () => {
  vi.mocked(getRuleFields).mockRejectedValueOnce(new Error('目录不可用'))
  const view = setup(); await flush()
  expect(textOf(view.root)).toContain('目录不可用')
  const retry = find(view.root, node => node.type === 'button' && textOf(node).includes('读取字段目录'))!
  await (retry.props!.onClick as () => Promise<void>)(); await flush()
  expect(getRuleFields).toHaveBeenCalledTimes(2)
  expect(textOf(view.root)).not.toContain('目录不可用')
  view.app.unmount()
})
