import { requireOperationResult, resultRecord, type Intent } from '@/components/BusinessView/operationClient'
import { sameBusinessViewId } from '@/api/pms/platform/business-view/ids'

export function acceptanceReportResult(action: 'CREATE_DRAFT' | 'UPDATE_DRAFT' | 'PUBLISH' | 'REVOKE'): NonNullable<Intent['validateResult']> {
  return (result, command) => {
    const body = result.response
    requireOperationResult(result.ownerContext === 'ACC' && result.objectType === 'ACCEPTANCE' && resultRecord(body))
    requireOperationResult(sameBusinessViewId(command.objectId, result.objectId) && sameBusinessViewId(body.acceptanceId, result.objectId)
      && sameBusinessViewId(body.reportVersionId, result.revisionId) && Number.isInteger(body.reportVersionNo)
      && Number(body.reportVersionNo) > 0 && typeof body.replayed === 'boolean'
      && body.reportStatus === (action === 'PUBLISH' ? 'EFFECTIVE' : action === 'REVOKE' ? 'REVOKED' : 'DRAFT')
      && result.resultCode === (action === 'PUBLISH' ? 'REPORT_VERSION_PUBLISHED' : action === 'REVOKE' ? 'REPORT_VERSION_REVOKED' : 'REPORT_DRAFT_SAVED'))
    if (action === 'UPDATE_DRAFT' || action === 'PUBLISH')
      requireOperationResult(sameBusinessViewId(body.reportVersionId, command.input.reportVersionId))
    if (action === 'REVOKE') requireOperationResult(sameBusinessViewId(body.reportVersionId, command.input.expectedCurrentReportVersionId))
  }
}
