import type * as RequirementApi from '@/api/pms/engineering/requirement-analysis/entity'
import type { BusinessOperationReceipt } from '@/api/pms/platform/businessmodel'
import type { NativeBusinessBridge } from './nativeBusinessBridge'

/** Keep the Owner's version/workspace projections and adapt only the write protocol. */
export const nativeRequirementApi = (bridge: NativeBusinessBridge, original: typeof RequirementApi) => {
  const revision = async (receipt: BusinessOperationReceipt): Promise<RequirementApi.Revision> => {
    if (!receipt.entityRef) throw new Error('操作已完成，但回执修订重开失败：缺少修订身份')
    const view = await original.read(receipt.entityRef.entityId)
    if (String(view.revision.ref.revisionId) !== String(receipt.entityRef.entityId) ||
      view.revision.version !== receipt.newConcurrencyBasis) throw new Error('操作已完成，但回执修订重开失败：版本已变化')
    return { ...view.revision, operationReceipt: receipt }
  }
  return {
    create: async (projectId: RequirementApi.EntityId, key: string) => {
      await bridge.create({ projectId }, key)
      return revision(bridge.state.receipt.value!)
    },
    save: async (target: RequirementApi.Revision, data: RequirementApi.Patch, key: string) => {
      const receipt = await bridge.update({ ...data.values, id: target.ref.revisionId, version: target.version,
        ...(data.extensionValues && Object.keys(data.extensionValues).length ? { $extensions: {
          definitionRevisionId: data.extensionDefinitionRevisionId, expectedVersion: data.expectedExtensionVersion,
          values: data.extensionValues
        } } : {}) }, key)
      return revision(receipt)
    },
    complete: async (target: RequirementApi.Revision, key: string) =>
      revision((await bridge.command('complete', target.ref.revisionId, {}, target.version, key)).receipt),
    copy: async (target: RequirementApi.Revision, key: string, _execution?: unknown, reason?: string) =>
      revision((await bridge.command('copy', target.ref.revisionId, { reason }, target.version, key)).receipt)
  }
}
