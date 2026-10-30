import { requireOperationResult, resultRecord, type Intent } from '@/components/BusinessView/operationClient'
import { sameBusinessViewId } from '@/api/pms/platform/business-view/ids'

export function siteSurveyResult(action: 'CREATE' | 'UPDATE' | 'DELETE' | 'CONFIRM' | 'REJECT' | 'ARCHIVE'): NonNullable<Intent['validateResult']> {
  const codes = { CREATE: 'SURVEY_DRAFT_SAVED', UPDATE: 'SURVEY_DRAFT_SAVED', DELETE: 'SURVEY_DELETED',
    CONFIRM: 'SURVEY_CONFIRMED', REJECT: 'SURVEY_REJECTED', ARCHIVE: 'SURVEY_ARCHIVED' }
  return (result, command) => {
    const body = result.response
    requireOperationResult(result.ownerContext === 'SOL' && result.objectType === 'SITE_SURVEY'
      && result.resultCode === codes[action] && result.revisionId == null && resultRecord(body))
    requireOperationResult(sameBusinessViewId(body.id, result.objectId) && body.version === result.objectVersion
      && body.deleted === (action === 'DELETE'))
    if (action !== 'CREATE') requireOperationResult(sameBusinessViewId(command.objectId, result.objectId))
  }
}
