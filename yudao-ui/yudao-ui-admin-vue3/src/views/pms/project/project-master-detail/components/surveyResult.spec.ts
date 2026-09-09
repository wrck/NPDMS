import { describe, expect, it } from 'vitest'
import { normalizeSurveyResult, surveyFields, surveySummary } from './surveyResult'

describe('工勘业务字段', () => {
  it('各工勘项具有明确字段，不共用现场情况文本', () => {
    expect(surveyFields('POWER').map((field) => field.key)).toEqual([
      'powerSupply',
      'powerEnvironment'
    ])
    expect(surveyFields('NETWORK_PORT').map((field) => field.key)).toEqual(['networkPort'])
    expect(surveyFields('FIBER').map((field) => field.key)).toEqual(['fiber'])
  })
  it('保留否与未知的区别，不把未填写默认变为可用', () => {
    expect(normalizeSurveyResult('CABINET', { cabinetAvailable: false })).toEqual({
      cabinetAvailable: false,
      cabinet: null
    })
    expect(normalizeSurveyResult('CABINET', {})).toEqual({ cabinetAvailable: null, cabinet: null })
  })
  it('不会将其他工勘项的字段带入保存', () => {
    expect(normalizeSurveyResult('POWER', { powerSupply: ' 交流 ', cabinet: '无机柜' })).toEqual({
      powerSupply: '交流',
      powerEnvironment: null
    })
  })
  it('原厂模块否保持原始业务含义', () => {
    expect(
      surveySummary('OPTICAL_MODULE', {
        originalOpticalModule: false,
        opticalModuleAvailable: true
      })
    ).toBe('光模块资源是否具备：是；是否使用原厂光模块：否')
  })
  it('未知项不猜测字段，空数据明确显示未填', () => {
    expect(surveyFields('UNREGISTERED')).toEqual([])
    expect(surveySummary('POWER')).toBe('尚未填写业务内容')
  })
})
