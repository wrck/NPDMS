import type { BusinessId } from '@/api/pms/platform/business'
import type * as DurationApi from '@/api/pms/engineering/construction-plan'
import type * as ReviewApi from '@/api/pms/engineering/solution-review'
import type * as StagePlanApi from '@/api/pms/engineering/stage-plan'
import type { NativeBusinessBridge } from './nativeBusinessBridge'
import { nativeId } from './nativeBusinessBridge'
import type { NativeValues } from './nativeBusinessContext'

export const nativeSolutionApi = (bridge: NativeBusinessBridge) => ({
  getSolutionPage: (query: NativeValues) => bridge.page(query),
  getSolution: async (id: BusinessId) => (await bridge.read(id)).value,
  createSolution: (data: NativeValues) => bridge.create(data),
  updateSolution: (data: NativeValues) => bridge.update(data),
  deleteSolution: (id: BusinessId) => bridge.remove(id)
})
export const nativeSolutionReviewApi = (bridge: NativeBusinessBridge, original: typeof ReviewApi) => ({
  start: async (data: Parameters<typeof original.start>[0]) => {
    await bridge.command('review-submit', data.solutionId, { processDefinitionId: data.processDefinitionId }, data.expectedVersion)
    return original.read(data.projectId, data.solutionId)
  },
  refresh: async (projectId: BusinessId, id: BusinessId) => {
    await bridge.command('review-refresh', id)
    return original.read(projectId, id)
  },
  revise: async (_projectId: BusinessId, id: BusinessId) => {
    const { receipt } = await bridge.command('copy-version', id)
    if (!receipt.entityRef) throw new Error('新方案身份读取失败，请确认操作回执')
    return nativeId(receipt.entityRef.entityId)
  }
})
export const nativeStagePlanApi = (bridge: NativeBusinessBridge, original?: typeof StagePlanApi,
  initialContext?: (projectId: BusinessId) => Record<string, unknown> | undefined) => {
  const get = async (id: BusinessId) => {
    const { row, form } = await bridge.read(id)
    const plan = form.context?.plan as NativeValues | undefined
    if (!plan || String(plan.id) !== String(id) || plan.version !== row.concurrencyBasis) throw new Error('计划版本已变化，请刷新')
    return plan
  }
  return {
    ...(original ? { getOverdueStages: async (projectId: number) => {
      const context = initialContext?.(projectId)
      if (context && Object.hasOwn(context, 'overdue')) {
        if (context.overdueFailed) throw new Error('逾期阶段读取失败')
        return context.overdue as Awaited<ReturnType<typeof original.getOverdueStages>>
      }
      return original.getOverdueStages(projectId)
    } } : {}),
    getStagePlanBatchPage: (query: NativeValues) => bridge.page(query),
    getStagePlanBatch: get,
    createStagePlanBatch: async (projectId: BusinessId) => get(await bridge.create({ projectId })),
    autoEstimateStagePlanBatch: async (id: BusinessId) => { await bridge.command('estimate', id); return get(id) },
    updateStagePlanItems: async (data: NativeValues) => {
      await bridge.update({ ...data, $business: { items: data.items, ...(data.tasks ? { tasks: data.tasks } : {}) } })
      return get(data.id)
    },
    submitStagePlanBatch: async (id: BusinessId, approverUserId: BusinessId, adjustmentReason?: string) => {
      await bridge.command('submit', id, { approverUserId, adjustmentReason })
      return get(id)
    }
  }
}
export const nativeDurationApi = (bridge: NativeBusinessBridge, original: typeof DurationApi) => {
  const get = async (id: number): Promise<DurationApi.ConstructionPlanVO> => {
    const { row, form } = await bridge.read(id)
    const plan = form.context?.duration as DurationApi.ConstructionPlanVO | undefined
    if (!plan || String(plan.planId) !== String(id) || plan.planVersion !== row.concurrencyBasis) throw new Error('工期版本已变化，请刷新')
    return plan
  }
  return {
    getPlan: get,
    getByProjectId: async (projectId: number) => {
      const page = await bridge.page({ projectId, pageNo: 1, pageSize: 1 })
      return page.list[0] ? get(page.list[0].id) : null
    },
    createInitial: async (data: DurationApi.CreateConstructionPlanReqVO, key: string) => {
      const id = await bridge.create({ ...data, $business: { expectedProjectVersion: data.expectedProjectVersion } }, key)
      return get(id as number)
    },
    createChange: async (id: number, data: DurationApi.CreateDurationChangeReqVO, version: number, key: string) => {
      await bridge.command('change-create', id, { content: data }, version, key)
      const drafts = (await original.getChanges(id, { pageSize: 100 })).items.filter(item => item.status === 'DRAFT')
      if (drafts.length !== 1) throw new Error('工期草稿身份尚未确认，请刷新后继续')
      return original.getChange(id, drafts[0].changeId)
    },
    patchChange: async (id: number, changeId: number, content: DurationApi.PatchDurationChangeReqVO, changeVersion: number) => {
      await bridge.command('change-save', id, { changeId, changeVersion, content })
      return original.getChange(id, changeId)
    },
    submitChange: async (id: number, changeId: number, projectVersion: number, changeVersion: number, key: string) => {
      await bridge.command('change-submit', id, { changeId, changeVersion, projectVersion }, undefined, key)
      const [plan, change] = await Promise.all([get(id), original.getChange(id, changeId)])
      return { changeId, status: change.status, processInstanceId: change.processInstanceId!, changeVersion: change.version, planVersion: plan.planVersion }
    }
  }
}
