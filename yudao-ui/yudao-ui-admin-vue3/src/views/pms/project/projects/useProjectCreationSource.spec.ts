import { defineComponent, h, reactive, ref } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import { mount } from '../../platform/dynamic-form/components/runtimeTestHarness'
import { useProjectCreationSource } from './useProjectCreationSource'
import { getContractCreationSource } from '@/api/pms/commerce'
vi.mock('@/api/pms/commerce', () => ({ getContractCreationSource: vi.fn() }))
const source = (id: number): any => ({
  contract: { id, contractNo: `CT-${id}` }, sourceFingerprint: `digest-${id}`,
  orders: [{ id: id * 10, orderNo: `SO-${id}`, executionNo: `EX-${id}` }],
  executionOrders: [{ id: id * 100, executionNo: `EX-${id}`, departmentCode: 'D1' }],
  resolved: { salesOrderId: id * 10, executionOrderId: id * 100, projectName: `P-${id}`,
    customerCode: `C-${id}`, customerName: `Customer-${id}`, companyCode: 'CO1' }, lineExecutionNos: []
})
const contract = (id: number) => ({ id, contractNo: `CT-${id}`, companyCode: 'CO1', sourceVersion: 'v1', status: 'ENABLED', version: 0 })
const setup = () => {
  const form = reactive<any>({ contractNo: '', projectName: '', customerCode: '', customerName: '' })
  let state!: ReturnType<typeof useProjectCreationSource>
  const wrapper = mount(defineComponent({ setup() {
    state = useProjectCreationSource(form, ref([{ id: 1, code: 'CO1' }]), ref([{ id: 2, code: 'D1' }]))
    return () => h('div')
  } }))
  return { form, state, wrapper }
}
beforeEach(() => vi.resetAllMocks())
it('keeps the order buyer separate from the final customer selection', async () => {
  vi.mocked(getContractCreationSource).mockResolvedValue(source(1))
  const { form, state, wrapper } = setup()
  await state.selectContract(1, contract(1))
  expect(getContractCreationSource).toHaveBeenCalledWith(1, undefined)
  expect(form).toMatchObject({ salesOrderId: 10, sourceFingerprint: 'digest-1', customerCode: '', customerName: '',
    orderOfficeCompanyId: 1, orderOfficeDepartmentId: 2 })
  expect(state.creationSource.value?.resolved.customerCode).toBe('C-1')
  expect(state.sourceReady.value).toBe(true)
  wrapper.app.unmount()
})
it('ignores superseded contract responses without clearing the newer loading state', async () => {
  const pending: ((value: any) => void)[] = []
  vi.mocked(getContractCreationSource).mockImplementation(() => new Promise(resolve => pending.push(resolve)))
  const { form, state, wrapper } = setup()
  const first = state.selectContract(1, contract(1))
  const second = state.selectContract(2, contract(2))
  pending[0](source(1)); await first
  expect(form.customerCode).toBe('')
  expect(state.creationSourceLoading.value).toBe(true)
  pending[1](source(2)); await second
  expect(form.customerCode).toBe('')
  expect(form.projectName).toBe('P-2')
  expect(state.creationSource.value?.resolved.customerCode).toBe('C-2')
  wrapper.app.unmount()
})
it('clears prior facts and blocks creation after a new source request fails', async () => {
  vi.mocked(getContractCreationSource).mockResolvedValueOnce(source(1)).mockRejectedValueOnce(new Error('offline'))
  const { form, state, wrapper } = setup()
  await state.selectContract(1, contract(1))
  await state.selectSalesOrder(11)
  expect(form.customerCode).toBe('')
  expect(form.sourceFingerprint).toBeUndefined()
  expect(state.sourceReady.value).toBe(false)
  await state.selectContract(undefined)
  expect(state.creationSource.value).toBeNull()
  wrapper.app.unmount()
})

it('does not overwrite a final customer selected while the source response is pending', async () => {
  let complete!: (value: any) => void
  vi.mocked(getContractCreationSource).mockImplementation(() => new Promise(resolve => { complete = resolve }))
  const { form, state, wrapper } = setup()
  const pending = state.selectContract(1, contract(1))
  form.customerCode = 'FINAL-1'; form.customerName = 'Final customer'
  complete(source(1)); await pending
  expect(form.customerCode).toBe('FINAL-1')
  expect(form.customerName).toBe('Final customer')
  expect(state.creationSource.value?.resolved.customerCode).toBe('C-1')
  wrapper.app.unmount()
})

it('preserves an independently selected final customer across contract and order reloads', async () => {
  vi.mocked(getContractCreationSource).mockResolvedValue(source(1))
  const { form, state, wrapper } = setup()
  form.customerCode = 'FINAL-1'; form.customerName = 'Final customer'
  await state.selectContract(1, contract(1))
  await state.selectSalesOrder(10)
  expect(form.customerCode).toBe('FINAL-1')
  expect(form.customerName).toBe('Final customer')
  await state.selectContract(undefined)
  expect(form.customerCode).toBe('FINAL-1')
  expect(form.customerName).toBe('Final customer')
  wrapper.app.unmount()
})

it('preserves the final customer while rejecting a failed source refresh', async () => {
  vi.mocked(getContractCreationSource).mockRejectedValue(new Error('offline'))
  const { form, state, wrapper } = setup()
  form.customerCode = 'FINAL-1'; form.customerName = 'Final customer'
  await state.selectContract(1, contract(1))
  expect(form.customerCode).toBe('FINAL-1')
  expect(form.customerName).toBe('Final customer')
  expect(form.sourceFingerprint).toBeUndefined()
  expect(state.sourceReady.value).toBe(false)
  wrapper.app.unmount()
})
