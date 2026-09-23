import { describe, expect, it } from 'vitest'
import { surveyPreparationBranches, type SurveyPreparationBranch } from './surveyPreparationBranches'
import { extractSurveyValues } from './siteSurveyForm'

describe('preparation branches reuse the survey form bindings', () => {
  const branches: SurveyPreparationBranch[] = [
    { title: 'CRM', when: { survey_fit: false } },
    { title: '领料', when: { survey_factory: true, survey_rails: true } },
    { title: '外采', when: { survey_factory: true, survey_rails: false } }
  ]
  it.each([
    [true, true, true, ['领料']], [true, true, false, ['外采']],
    [true, false, true, []], [true, false, false, []],
    [false, true, true, ['CRM', '领料']], [false, true, false, ['CRM', '外采']],
    [false, false, true, ['CRM']], [false, false, false, ['CRM']]
  ])('routes fit=%s, factory=%s, rails=%s from one survey', (fit, factory, rails, expected) => {
    const values = extractSurveyValues({
      projectId: 9, code: 'SURVEY', name: '工勘',
      fieldBindings: { survey_fit: 'a', survey_factory: 'b', survey_rails: 'c' },
      businessValues: { a: fit, b: factory, c: rails }
    })
    expect(surveyPreparationBranches(branches, values).filter(b => b.state === 'MATCHED').map(b => b.title)).toEqual(expected)
  })
  it('does not interpret missing answers as false or satisfy empty rules', () => {
    expect(surveyPreparationBranches(branches, { survey_factory: true }).map(b => b.state)).toEqual(['UNKNOWN', 'UNKNOWN', 'UNKNOWN'])
    expect(surveyPreparationBranches([{ title: '未配置', when: {} }], {})[0].state).toBe('UNKNOWN')
  })
  it('accepts a configured renamed field without application field-name changes', () => {
    expect(surveyPreparationBranches([{ title: '新字段', when: { configured_elsewhere: false } }],
      { configured_elsewhere: false })[0].state).toBe('MATCHED')
    expect(surveyPreparationBranches(branches, { survey_fit: 'false' })[0].state).toBe('NOT_MATCHED')
  })
})
