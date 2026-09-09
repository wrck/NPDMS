import { reactive } from 'vue'

export const calls = reactive<{ name: string; projectId?: number; payload?: unknown }[]>([])
let initialCreated = false
const revision = {
  revisionId: 101,
  revisionNo: 1,
  calculationBasis: 'DATE_RANGE',
  startDate: '2026-09-01',
  endDate: '2026-09-10',
  durationDays: 10
}
const change = {
  changeId: 33,
  status: 'DRAFT',
  version: 2,
  reasonType: 'CUSTOMER_DELAY',
  reasonDetail: '测试延期说明',
  customerEvidenceRequired: false,
  candidateRevision: revision
}
const plan = (id: number) => ({
  projectId: id,
  planId: id * 100,
  planVersion: 1,
  currentRevision: { ...revision, revisionNo: id },
  planRecalculationStatus: 'PENDING_RECALCULATION',
  allowedActions: ['CREATE_CHANGE']
})
export const getByProjectId = async (projectId: number) => {
  calls.push({ name: 'getByProjectId', projectId })
  if (projectId === 1 && initialCreated) {
    await new Promise((done) => setTimeout(done, 350))
    calls.push({ name: 'resolvedProject1', projectId })
  }
  return projectId === 1 && !initialCreated ? null : plan(projectId)
}
export const getChanges = async (planId: number) => ({
  items: planId === 200 ? [change] : [],
  hasMore: false
})
export const getChange = async () => change
export const createInitial = async (payload: { projectId: number }) => {
  calls.push({ name: 'createInitial', projectId: payload.projectId, payload })
  initialCreated = true
}
export const useUserStore = () => ({ getUser: { id: 17 } })
export const useRouter = () => ({
  push: (payload: unknown) => calls.push({ name: 'navigate', payload })
})
export const useMessage = () => ({
  confirm: async () => undefined,
  prompt: async () => ({ value: '测试原因' }),
  warning: () => undefined,
  success: () => undefined
})
export const getStrDictOptions = () => [{ value: 'CUSTOMER_DELAY', label: '客户延期' }]
export const PmsFileReferenceList = {
  props: ['editable'],
  template: '<p>文件引用测试占位，不访问文件服务</p>'
}
export const PmsFileUploader = {
  props: ['disabled'],
  template: '<button type="button" :disabled="disabled">文件上传测试占位</button>'
}
const notInThisTest = () => {
  throw new Error('The fixture does not implement this business action')
}
export const createChange = notInThisTest
export const patchChange = notInThisTest
export const submitChange = notInThisTest
export const cancelProcessInstanceByStartUser = notInThisTest
