import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import CustomerCorrectionDialog from './CustomerCorrectionDialog.vue'
import * as Correction from '@/api/pms/project/customer-selected'
import { getCustomerByCode, type CustomerDetailRespVO } from '@/api/pms/customer'
import { mount, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), put: vi.fn(), post: vi.fn() } }))
vi.mock('@/api/pms/project/customer-selected', () => ({ inspectCustomerCorrection: vi.fn(), correctProjectCustomer: vi.fn() }))
vi.mock('@/api/pms/customer', () => ({ getCustomerByCode: vi.fn(), getCustomerPage: vi.fn() }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => ref(false) }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn() }) }))
const control = defineComponent({ inheritAttrs: false, setup(_, { attrs, slots }) {
  return () => h('control', attrs, [...(slots.default?.() || []), ...(slots.footer?.() || [])])
} })
const find = (node: TestNode, predicate: (item: TestNode) => boolean): TestNode | undefined => {
  if (predicate(node)) return node
  for (const child of node.children) { const result = find(child, predicate); if (result) return result }
}
const open = async () => {
  const mounted = mount(CustomerCorrectionDialog, {}, { ElDialog: control, ElInput: control,
    ElTable: control, ElTableColumn: control, PmsEntitySelect: control })
  ;(mounted.vm as unknown as { open: (project: object) => void }).open({ id: 10, projectCode: 'P-10', projectName: '验收' })
  await nextTick(); await nextTick()
  return mounted
}
const choose = async (root: TestNode) => {
  const select = find(root, node => node.props?.['value-field'] === 'code')!
  await (select.props!.onChange as (code: string, customer: object) => Promise<void>)('NEW', { code: 'NEW', name: '新客户' })
  await nextTick()
}
const save = async (root: TestNode) => {
  const button = find(root, node => node.type === 'button' && textOf(node).includes('保存更正'))!
  await (button.props!.onClick as () => Promise<void>)()
  await nextTick()
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(getCustomerByCode).mockResolvedValue({ code: 'NEW', name: '新客户', lifecycleStatus: 'ENABLED' } as CustomerDetailRespVO)
  vi.mocked(Correction.inspectCustomerCorrection).mockResolvedValue({ projectId: 10, version: 3,
    customerCode: 'OLD', customerName: '原客户', canCorrect: true, references: [] })
  vi.mocked(Correction.correctProjectCustomer).mockResolvedValue({ projectId: 10, version: 4,
    customerId: 20, customerCode: 'NEW', customerName: '新客户', changed: true })
})
it('saves selected master identity with the inspected project version', async () => {
  const { root, app } = await open()
  await choose(root)
  await save(root)
  expect(Correction.correctProjectCustomer).toHaveBeenCalledWith(10, 3, 'NEW', '', expect.any(String))
  app.unmount()
})
it('shows blocking references without offering a bypass save action', async () => {
  const references = [{ source: 'CUSTOMER', label: '项目联系人', count: 2 }]
  vi.mocked(Correction.inspectCustomerCorrection).mockResolvedValue({ projectId: 10, version: 3, canCorrect: false, references })
  const { root, app } = await open()
  expect(find(root, node => Array.isArray(node.props?.data))?.props?.data).toEqual(references)
  expect(find(root, node => node.type === 'button' && textOf(node).includes('保存更正'))).toBeUndefined()
  expect(Correction.correctProjectCustomer).not.toHaveBeenCalled()
  app.unmount()
})
it('does not turn a failed reference check into permission to correct', async () => {
  vi.mocked(Correction.inspectCustomerCorrection).mockRejectedValue(new Error('引用查询失败'))
  const { root, app } = await open()
  expect(find(root, node => node.props?.title === '引用查询失败')).toBeTruthy()
  expect(find(root, node => node.type === 'button' && textOf(node).includes('保存更正'))).toBeUndefined()
  app.unmount()
})
it('reuses the same intent key after an uncertain response', async () => {
  vi.mocked(Correction.correctProjectCustomer).mockRejectedValueOnce(new Error('timeout'))
  const { root, app } = await open()
  await choose(root)
  await save(root); await save(root)
  const calls = vi.mocked(Correction.correctProjectCustomer).mock.calls
  expect(calls).toHaveLength(2)
  expect(calls[0][4]).toBe(calls[1][4])
  app.unmount()
})
