import { reactive } from 'vue'

// Only this isolated browser entry resolves the production API imports to these in-memory fixtures.
export const calls = reactive<{ name: string; projectId?: number; taskId?: number }[]>([])
const record = (name: string, projectId?: number, taskId?: number) =>
  calls.push({ name, projectId, taskId })
export const listTasks = async (projectId?: number) => {
  record('listTasks', projectId)
  if (projectId === 1) await new Promise((done) => setTimeout(done, 400))
  const id = projectId ?? 9
  return [
    {
      id: id * 10 + 1,
      projectId: id,
      revisionNo: 1,
      assignedToUserId: 71,
      status: 'PENDING_COLLECTION',
      questionnaireStatus: 'ACTIVE',
      version: 1
    }
  ]
}
export const listResults = async (projectId?: number) => {
  record('listResults', projectId)
  if (projectId === 1) await new Promise((done) => setTimeout(done, 400))
  const id = projectId ?? 9
  return [
    {
      resultId: id * 100 + 1,
      projectId: id,
      taskRevisionNo: 1,
      score: 80,
      threshold: 60,
      passed: true,
      resultStatus: 'EFFECTIVE',
      archiveStatus: 'ARCHIVED'
    }
  ]
}
export const listTemplates = async () => {
  record('listTemplates')
  return []
}
export const assignTask = async (task: { id: number; projectId: number }) => {
  record('assignTask', task.projectId, task.id)
}
export const useMessage = () => ({
  confirm: async () => undefined,
  success: () => undefined,
  warning: () => undefined
})
export const getTenantId = () => 1
export const Qrcode = { template: '<div>二维码测试占位</div>' }

const notInThisTest = () => {
  throw new Error('This fixture does not implement that business command')
}
export const createGrant = notInThisTest
export const reserveAssistedResponse = notInThisTest
export const initializeAssistedFile = notInThisTest
export const completeAssistedFile = notInThisTest
export const submitAssisted = notInThisTest
export const recollect = notInThisTest
export const invalidateResult = notInThisTest
export const requestResultExport = notInThisTest
export const getExportTask = notInThisTest
export const retryExportTask = notInThisTest
export const getExportAccessTicket = notInThisTest
export const getResultDownload = notInThisTest
export const createAccessTicket = notInThisTest
