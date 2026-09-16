import { expect, it, vi } from 'vitest'
import request from '@/config/axios'
import * as api from './entity'
vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))
it('routes ordinary survey reads, writes and lifecycle operations to the new business endpoint', async () => {
  const survey = { id: 31, projectId: 7, code: 'survey', name: '工勘', version: 4, extensionDefinitionRevisionId: 80, businessValues: { cabinetReady: false }, extensionValues: { extra_other: 'value' } }
  await api.getSiteSurvey(31); await api.getSiteSurveyPage({ projectId: 7 }); await api.getDefaultFormSchema(); await api.getFormSchema(50,1)
  await api.createSiteSurvey(survey); await api.updateSiteSurvey(survey)
  await api.confirmSiteSurvey(31); await api.rejectSiteSurvey(31); await api.archiveSiteSurvey(31); await api.deleteSiteSurvey(31)
  expect(request.post).toHaveBeenCalledWith({ url: '/api/v1/pms/site-surveys/create', data: survey })
  expect(request.put).toHaveBeenCalledWith({ url: '/api/v1/pms/site-surveys/update', data: survey })
  for (const method of [request.get,request.post,request.put,request.delete]) for (const [command] of vi.mocked(method).mock.calls) {
    expect(command.url).toMatch(/^\/api\/v1\/pms\/site-surveys\//)
    expect(command.url).not.toContain('revisions')
  }
})
