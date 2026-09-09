import { describe, expect, it, vi } from 'vitest'
import { reactive, nextTick } from 'vue'
import config from './business-template.json'
import { decodeDynamicForm } from '@/views/pms/platform/dynamic-form/components/dynamicFormCodec'
import { extractSurveyValues, mergeSurveyValues, surveyFields } from './siteSurveyForm'
import { surveyProcurementRoute } from './surveyBusinessForm'
import SurveyProcurementLinks from './SurveyProcurementLinks.vue'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

describe('complete site survey business form', () => {
  it('round trips real business choices and preserves original notes and whole-survey outsourcing', () => {
    const survey = { projectId: 1, code: 'S', name: 'survey', outsourceRequired: true, powerSupply: '原文本',
      formExtraValues: { extra_powerTypes: ['AC', 'DC'], extra_powerEnvironments: ['CN'], extra_cabinetReady: false, extra_selectedMaterials: [] } }
    expect(mergeSurveyValues(survey, extractSurveyValues(survey))).toMatchObject(survey)
    const schema = decodeDynamicForm(config.formConfJson, config.formRulesJson as any)
    expect(schema.rule.filter(rule => rule.field).map(rule => rule.field)).toEqual(expect.arrayContaining([...surveyFields, 'extra_materialMatches']))
  })
  it('shows procurement shortcuts only for an explicit yes, preserving false/unknown distinction', async () => {
    const survey = reactive({ projectId: 1, code: 'S', name: 'survey', formExtraValues: {} as Record<string, any> })
    const app = mount(SurveyProcurementLinks, { getSurvey: () => survey, launch: vi.fn() })
    expect(textOf(app.root)).not.toContain('发起领料')
    survey.formExtraValues.extra_railTrayRequired = false; await nextTick()
    expect(textOf(app.root)).not.toContain('发起领料')
    survey.formExtraValues.extra_railTrayRequired = true; await nextTick()
    expect(textOf(app.root)).toContain('发起领料申请')
    app.app.unmount()
  })
  it('carries only source identities into existing workflow pages, not invented material data', () => {
    expect(surveyProcurementRoute('exchange', 12, 'SN-1')).toEqual({ path: '/pms/engineering/procurement/eng-material-exch', query: { surveyId: '12', deviceSn: 'SN-1' } })
  })
})
