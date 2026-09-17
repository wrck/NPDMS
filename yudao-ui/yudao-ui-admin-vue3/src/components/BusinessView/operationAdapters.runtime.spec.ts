import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/config/axios'
import * as Survey from '@/api/pms/engineering/site-survey/entity'
import * as Analysis from '@/api/pms/engineering/requirement-analysis/entity'
import * as Report from '@/api/pms/acceptance/acceptance-report'
import { OperationClient, routeSelection } from './operationClient'

vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))
vi.mock('@/config/axios/service', () => ({ service: vi.fn() }))
const vector = { task: { projectId: 1, taskId: 2, executionContractId: 3, contractVersion: 1,
  planVersionId: 4, executionId: 5 }, stage: null }
function clientFor(code: string) {
  const submit = vi.fn().mockResolvedValue({ ownerContext: 'OWNER', objectType: 'ENTITY', objectId: '8',
    objectVersion: 4, businessFactVersion: 'v4', resultCode: 'SAVED', response: { id: 8 }, replayed: false })
  const client = new OperationClient({ projectId: 1, nodeKind: 'TASK', nodeId: 2 }, vector, {
    inspect: vi.fn().mockResolvedValue({ node: { projectId: 1, kind: 'TASK', id: 2, status: 'ACTIVE' }, execution: vector,
      ownerFactVersion: 'observed-v3', actions: [{ operationCode: code, operationVersion: 1, allowed: true }] }),
    submit, newKey: () => 'intent'
  })
  return { client, submit, execution: routeSelection(vector, client) as never }
}
beforeEach(() => { vi.clearAllMocks(); vi.mocked(request.get).mockResolvedValue({ id: 8, projectId: 1, version: 3 } as never) })
describe('all registered workbench operations use the controlled route', () => {
  for (const action of ['CREATE', 'UPDATE', 'DELETE', 'CONFIRM', 'REJECT', 'ARCHIVE']) {
    it(`routes survey ${action} with object-specific version and no nested execution`, async () => {
      const code = `SOL.SITE_SURVEY.${action}`, route = clientFor(code)
      const data = { projectId: 1, code: 's', name: 'survey', version: 3, execution: route.execution }
      if (action === 'CREATE') await Survey.createSiteSurvey(data)
      else if (action === 'UPDATE') await Survey.updateSiteSurvey({ ...data, id: 8 })
      else {
        const actions = { DELETE: Survey.deleteSiteSurvey, CONFIRM: Survey.confirmSiteSurvey,
          REJECT: Survey.rejectSiteSurvey, ARCHIVE: Survey.archiveSiteSurvey }
        await actions[action as keyof typeof actions](8, route.execution)
      }
      expect(route.submit).toHaveBeenCalledOnce()
      const [selected, command] = route.submit.mock.calls[0]
      expect(selected).toBe(code)
      expect(command.objectId).toBe(action === 'CREATE' ? undefined : '8')
      expect(command.input).not.toHaveProperty('execution')
      expect(request.post).not.toHaveBeenCalled(); expect(request.put).not.toHaveBeenCalled(); expect(request.delete).not.toHaveBeenCalled()
    })
  }
  for (const action of ['CREATE', 'SAVE', 'COMPLETE', 'COPY']) {
    it(`routes analysis ${action} by revision identity, not entity identity`, async () => {
      const route = clientFor(`SOL.REQUIREMENT_ANALYSIS.${action}`)
      const revision = { ref: { entity: { entityId: 77 }, revisionId: 8 }, version: 3 } as Analysis.Revision
      if (action === 'CREATE') await Analysis.create(1, 'key', route.execution)
      else if (action === 'SAVE') await Analysis.save(revision, { values: { name: 'x' }, expectedExtensionVersion: 0, execution: route.execution }, 'key')
      else if (action === 'COMPLETE') await Analysis.complete(revision, 'key', route.execution)
      else await Analysis.copy(revision, 'key', route.execution, 'revision')
      expect(route.submit.mock.calls[0][1].objectId).toBe(action === 'CREATE' ? undefined : '8')
      expect(route.submit.mock.calls[0][2]).toBe('key')
      expect(route.submit.mock.calls[0][1].input).not.toHaveProperty('execution')
      expect(request.post).not.toHaveBeenCalled(); expect(request.put).not.toHaveBeenCalled()
    })
  }
  for (const action of ['CREATE_DRAFT', 'UPDATE_DRAFT', 'PUBLISH', 'REVOKE']) {
    it(`routes report ${action} with activity and report identities separated`, async () => {
      const route = clientFor(`ACC.ACCEPTANCE_REPORT.${action}`)
      const activity = { id: 8, projectId: 1, version: 3, currentReportVersionId: 88 } as Report.AcceptanceActivityVO
      const report = { id: 89, reportVersionNo: 2 }
      let result: Report.ReportCommandResult
      if (action === 'CREATE_DRAFT') result = await Report.createDraft(8, { acceptorName: 'x' }, 3, route.client)
      else if (action === 'UPDATE_DRAFT') result = await Report.updateDraft(8, 89, { expectedReportVersionNo: 2 }, 3, route.client)
      else if (action === 'PUBLISH') result = await Report.publishVersion(activity, report, 'key', route.client)
      else result = await Report.revokeCurrentVersion(activity, report, 'key', route.client)
      expect(route.submit.mock.calls[0][1].objectId).toBe('8')
      expect(result.activityVersion).toBe(4)
      if (action === 'PUBLISH' || action === 'UPDATE_DRAFT') expect(route.submit.mock.calls[0][1].input.reportVersionId).toBe(89)
    })
  }
  it('keeps an unmarked standalone request on the original API', async () => {
    const data = { projectId: 1, name: 'x', code: 's' }
    await Survey.createSiteSurvey(data)
    expect(request.post).toHaveBeenCalledWith({ url: '/api/v1/pms/site-surveys/create', data })
  })
  it('never falls back to the original API when the controlled command fails', async () => {
    const route = clientFor('SOL.SITE_SURVEY.UPDATE')
    route.submit.mockRejectedValue(new Error('POST_NOT_MATCHED'))
    await expect(Survey.updateSiteSurvey({ id: 8, projectId: 1, name: 'x', code: 's', version: 3, execution: route.execution })).rejects.toThrow('POST_NOT_MATCHED')
    expect(request.put).not.toHaveBeenCalled()
  })
})
