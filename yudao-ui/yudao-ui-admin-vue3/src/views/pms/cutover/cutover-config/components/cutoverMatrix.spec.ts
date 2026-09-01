import { describe, expect, it } from 'vitest'
import type { CutoverConfiguration } from '@/api/pms/cutover/cutover-config'
import {
  DUAL_BASELINES,
  MATRIX_LEVEL_CODES,
  applyBulkBinding,
  ensureCutoverBackgroundSchema,
  projectRiskMatrix,
  projectSurveyMatrix,
  validationTarget
} from './cutoverMatrix'

const fixtureConfiguration = (): CutoverConfiguration => ({
  configurationCode: 'CUTOVER-V1',
  configurationName: '割接配置',
  dictionarySnapshot: {},
  dimensions: [],
  planTemplateSections: [],
  items: [
    {
      stableItemKey: 'RISK-1',
      itemType: 'RISK',
      businessCategoryCode: 'SYSTEM_LOG',
      itemName: '系统日志',
      interfaceFormat: 'BOOLEAN',
      interfaceSchema: {},
      feedbackFormat: 'BOOLEAN_REMARK',
      required: true,
      workMode: 'MANUAL',
      enabled: true,
      sortOrder: 10
    },
    {
      stableItemKey: 'DUAL-1',
      itemType: 'DUAL_MACHINE_CHECK',
      businessCategoryCode: 'VSM',
      itemName: 'VSM检查',
      interfaceFormat: 'BOOLEAN',
      interfaceSchema: {},
      feedbackFormat: 'BOOLEAN_REMARK',
      required: true,
      workMode: 'MANUAL',
      subtableCode: 'VSM',
      enabled: true,
      sortOrder: 20
    },
    {
      stableItemKey: 'SURVEY-1',
      itemType: 'BUSINESS_SURVEY',
      businessCategoryCode: 'CUTOVER_BACKGROUND',
      itemName: '割接背景',
      interfaceFormat: 'FORM',
      interfaceSchema: {},
      feedbackFormat: 'OBJECT',
      required: true,
      workMode: 'MANUAL',
      enabled: true,
      sortOrder: 30
    }
  ],
  bindingRules: [
    {
      stableRuleKey: 'RULE-1',
      stableItemKey: 'RISK-1',
      dimensionConditions: { CUTOVER_LEVEL: ['A'] },
      priority: 10,
      requiredResult: false,
      enabled: true
    },
    {
      stableRuleKey: 'RULE-2',
      stableItemKey: 'SURVEY-1',
      dimensionConditions: {},
      priority: 10,
      requiredResult: true,
      enabled: true
    }
  ]
})

describe('F-CUT-001 risk and survey matrix projections', () => {
  it('projects both views without cloning aggregate ownership', () => {
    const config = fixtureConfiguration()
    const risk = projectRiskMatrix(config)
    const survey = projectSurveyMatrix(config)

    expect(risk.items.map((item) => item.stableItemKey)).toEqual(['RISK-1', 'DUAL-1'])
    expect(risk.rules.map((rule) => rule.stableRuleKey)).toEqual(['RULE-1'])
    expect(survey.items.map((item) => item.stableItemKey)).toEqual(['SURVEY-1'])
    expect(survey.rules.map((rule) => rule.stableRuleKey)).toEqual(['RULE-2'])
    expect(risk.items[0]).toBe(config.items[0])
    expect(survey.rules[0]).toBe(config.bindingRules[1])
  })

  it('bulk edits selected bindings on the same aggregate only', () => {
    const config = fixtureConfiguration()
    applyBulkBinding(config, {
      ruleKeys: ['RULE-1'],
      cutoverTypeCodes: ['VERSION_UPGRADE'],
      networkModeCodes: ['VSM'],
      deviceTypeCodes: ['FW'],
      levelCodes: ['A', 'B'],
      requiredResult: true,
      priority: 30,
      enabled: false
    })

    expect(config.bindingRules[0]).toMatchObject({
      dimensionConditions: {
        CUTOVER_TYPE: ['VERSION_UPGRADE'],
        NETWORK_MODE: ['VSM'],
        DEVICE_TYPE: ['FW'],
        CUTOVER_LEVEL: ['A', 'B']
      },
      requiredResult: true,
      priority: 30,
      enabled: false
    })
    expect(config.bindingRules[1].requiredResult).toBe(true)
  })

  it('keeps the formal five-group baseline at 97 items', () => {
    expect(DUAL_BASELINES).toEqual({
      VSM: 17,
      SILENT_DUAL: 25,
      DRP_DUAL: 23,
      NORMAL_DUAL: 24,
      CLUSTER: 8
    })
    expect(Object.values(DUAL_BASELINES).reduce((sum, count) => sum + count, 0)).toBe(97)
  })

  it('limits P3 matrix conditions to A, B and C levels', () => {
    expect(MATRIX_LEVEL_CODES).toEqual(['A', 'B', 'C'])
  })

  it('materializes the six background fields and two dependency groups', () => {
    const schema: Record<string, unknown> = {}
    ensureCutoverBackgroundSchema(schema)

    expect(schema.fields).toEqual([
      { code: 'solvesOnlineIssue' },
      {
        code: 'issueTicketNo',
        visibleWhen: { field: 'solvesOnlineIssue', equals: true }
      },
      {
        code: 'issueHandler',
        visibleWhen: { field: 'solvesOnlineIssue', equals: true }
      },
      { code: 'repeatCutover' },
      {
        code: 'firstCutoverOwner',
        visibleWhen: { field: 'repeatCutover', equals: true }
      },
      { code: 'backgroundDescription' }
    ])
  })

  it('routes the leading grouped validation error to its owner tab', () => {
    expect(validationTarget([{ location: 'base.items[0]', message: '基础错误' }])).toBe(
      'validation'
    )
    expect(validationTarget([{ location: 'risk.dualCounts.VSM', message: '风险错误' }])).toBe(
      'risk'
    )
    expect(validationTarget([{ location: 'survey.categories.X', message: '调研错误' }])).toBe(
      'survey'
    )
  })
})
