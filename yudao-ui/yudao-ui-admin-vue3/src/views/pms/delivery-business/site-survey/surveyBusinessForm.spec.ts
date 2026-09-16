import { describe, expect, it, vi } from 'vitest'
import { reactive, nextTick } from 'vue'
import { extractSurveyValues, mergeSurveyValues } from './siteSurveyForm'
import { surveyProcurementRoute } from './surveyBusinessForm'
import SurveyProcurementLinks from './SurveyProcurementLinks.vue'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

describe('complete site survey business form', () => {
  it('round trips real business choices and preserves original notes and whole-survey outsourcing', () => {
    const survey = { projectId: 1, code: 'S', name: 'survey', outsourceRequired: true, powerSupply: '原文本',
      businessValues: { powerTypes: ['AC', 'DC'], powerEnvironments: ['CN'], cabinetReady: false, selectedMaterials: [] }, extensionValues: {},
      fieldBindings: { power: 'powerTypes', cabinet: 'cabinetReady' }, fieldCatalog: [
        { code: 'powerTypes', type: 'TEXT_LIST', required: false }, { code: 'cabinetReady', type: 'BOOLEAN', required: false }]  }
    expect(mergeSurveyValues(survey, extractSurveyValues(survey))).toMatchObject(survey)
  })
  it('shows procurement shortcuts only for an explicit yes, preserving false/unknown distinction', async () => {
    const survey = reactive({ projectId: 1, code: 'S', name: 'survey', businessValues: {} as Record<string, any> })
    const app = mount(SurveyProcurementLinks, { getSurvey: () => survey, launch: vi.fn() })
    expect(textOf(app.root)).not.toContain('发起领料')
    survey.businessValues.railTrayRequired = false; await nextTick()
    expect(textOf(app.root)).not.toContain('发起领料')
    survey.businessValues.railTrayRequired = true; await nextTick()
    expect(textOf(app.root)).toContain('发起领料申请')
    app.app.unmount()
  })
  it('carries only source identities into existing workflow pages, not invented material data', () => {
    expect(surveyProcurementRoute('exchange', 12, 'SN-1')).toEqual({ path: '/pms/engineering/procurement/eng-material-exch', query: { surveyId: '12', deviceSn: 'SN-1' } })
  })
})
