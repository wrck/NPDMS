import { computed, onBeforeUnmount, ref, type Ref } from 'vue'
import { getContractCreationSource, type ContractCreationSourceRespVO } from '@/api/pms/commerce'
import type { SelectedContract } from './contractSelection'

interface SourceForm {
  contractId?: number; contractNo: string; salesOrderId?: number; sourceFingerprint?: string
  projectName: string; customerCode: string; customerName: string
  orderOfficeCompanyId?: number; orderOfficeDepartmentId?: number
}
interface Organization { id?: number; code?: string }

/** Both creation pages consume the same linked headers and ignore superseded responses. */
export const useProjectCreationSource = (form: SourceForm, companies: Ref<Organization[]>, departments: Ref<Organization[]>) => {
  const creationSource = ref<ContractCreationSourceRespVO | null>(null)
  const creationSourceLoading = ref(false)
  const sourceError = ref('')
  let generation = 0
  const clearFields = () => {
    form.salesOrderId = undefined; form.sourceFingerprint = undefined
    form.projectName = ''
    form.orderOfficeCompanyId = undefined; form.orderOfficeDepartmentId = undefined
  }
  const resetSource = () => {
    ++generation; creationSource.value = null; creationSourceLoading.value = false; sourceError.value = ''
  }
  const loadSource = async (salesOrderId?: number) => {
    const contractId = form.contractId
    const current = ++generation
    clearFields(); sourceError.value = ''; creationSourceLoading.value = !!contractId
    if (!contractId) { creationSource.value = null; return }
    try {
      const source = await getContractCreationSource(contractId, salesOrderId)
      if (current !== generation) return
      creationSource.value = source
      const resolved = source.resolved
      if (!resolved.salesOrderId) return
      form.salesOrderId = resolved.salesOrderId
      form.sourceFingerprint = source.sourceFingerprint
      form.projectName = resolved.projectName ?? ''
      form.contractNo = source.contract.contractNo
      form.orderOfficeCompanyId = companies.value.find(item => item.code === resolved.companyCode)?.id
      const execution = source.executionOrders.find(item => item.id === resolved.executionOrderId)
      form.orderOfficeDepartmentId = departments.value.find(item => item.code === execution?.departmentCode)?.id
    } catch (error: any) {
      if (current !== generation) return
      sourceError.value = error?.response?.data?.msg || error?.message || '来源加载失败，请重新选择合同或重试'
    } finally {
      if (current === generation) creationSourceLoading.value = false
    }
  }
  const selectContract = async (id: unknown, contract?: SelectedContract) => {
    resetSource(); clearFields()
    const selected = contract && String(contract.id) === String(id) ? contract : undefined
    form.contractId = selected?.id; form.contractNo = selected?.contractNo ?? ''
    await loadSource()
  }
  const sourceReady = computed(() => !form.contractId || (!creationSourceLoading.value && !sourceError.value
    && !!form.salesOrderId && !!form.sourceFingerprint))
  onBeforeUnmount(() => { ++generation })
  return { creationSource, creationSourceLoading, sourceError, sourceReady, selectContract,
    selectSalesOrder: loadSource, resetSource }
}
