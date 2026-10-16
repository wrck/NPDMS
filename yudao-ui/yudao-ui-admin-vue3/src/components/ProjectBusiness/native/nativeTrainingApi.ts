import type { BusinessId } from '@/api/pms/platform/business'
import type { TrainingIssueVO } from '@/api/pms/engineering/training'
import type { NativeBusinessBridge } from './nativeBusinessBridge'
import type { NativeValues } from './nativeBusinessContext'

export const nativeTrainingApi = (bridge: NativeBusinessBridge) => {
  const input = (data: NativeValues) => ({ ...data,
    trainingTypes: Array.isArray(data.trainingTypes) ? data.trainingTypes.join(',') : data.trainingTypes })
  return {
    getTrainingPage: (query: NativeValues) => bridge.page(query),
    getTraining: async (id: BusinessId) => (await bridge.read(id)).value,
    createTraining: (data: NativeValues) => bridge.create(input(data)),
    updateTraining: (data: NativeValues) => bridge.update(input(data)),
    deleteTraining: (id: BusinessId) => bridge.remove(id),
    voidTraining: (id: BusinessId) => bridge.command('void', id),
    issueTraining: async (id: BusinessId): Promise<TrainingIssueVO> => {
      const { result } = await bridge.command('issue', id)
      const issuance = (result as { issuance?: TrainingIssueVO } | undefined)?.issuance
      if (!issuance) throw new Error('外发操作已执行，当前回执不包含确认链接；请刷新核对记录。')
      return issuance
    }
  }
}
