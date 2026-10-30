import { requireOperationResult, resultRecord, type Intent } from '@/components/BusinessView/operationClient'
import { isBusinessViewId, sameBusinessViewId } from '@/api/pms/platform/business-view/ids'

export function requirementAnalysisResult(action: 'CREATE' | 'SAVE' | 'COMPLETE' | 'COPY'): NonNullable<Intent['validateResult']> {
  return (result, command) => {
    const body = result.response
    requireOperationResult(result.ownerContext === 'SOL' && result.objectType === 'REQUIREMENT_ANALYSIS'
      && result.resultCode === (action === 'COMPLETE' ? 'REQUIREMENT_ANALYSIS_COMPLETED' : 'REQUIREMENT_ANALYSIS_DRAFT_SAVED')
      && resultRecord(body) && resultRecord(body.ref) && resultRecord(body.ref.entity))
    requireOperationResult(body.ref.entity.ownerModule === 'SOL' && body.ref.entity.entityType === 'REQUIREMENT_ANALYSIS'
      && isBusinessViewId(body.ref.entity.entityId) && sameBusinessViewId(body.ref.revisionId, result.objectId)
      && sameBusinessViewId(body.ref.revisionId, result.revisionId) && body.version === result.objectVersion
      && Number.isInteger(body.revisionNo) && Number(body.revisionNo) > 0 && typeof body.effective === 'boolean'
      && body.state === (action === 'COMPLETE' ? 'FROZEN' : 'DRAFT'))
    if (action === 'SAVE' || action === 'COMPLETE') requireOperationResult(sameBusinessViewId(command.objectId, result.objectId))
    if (action === 'COPY') requireOperationResult(sameBusinessViewId(body.sourceRevisionId, command.objectId))
  }
}
