import { describe, expect, it } from 'vitest'
import { buildRecordName } from './recordNaming'

describe('记录命名规范', () => {
  it('按 项目编码_项目名称_业务类型_版本 生成', () => {
    expect(
      buildRecordName({ projectCode: 'PJT2026000001', projectName: '某某项目', typeLabel: '实施方案', version: 'V2' })
    ).toBe('PJT2026000001_某某项目_实施方案_V2')
  })
  it('无版本时使用年月日时分秒', () => {
    expect(
      buildRecordName({ projectCode: 'C', projectName: 'P', typeLabel: '接口规划' }, new Date(2026, 8, 29, 14, 30, 25))
    ).toBe('C_P_接口规划_20260929143025')
  })
  it('缺失项目编码或名称的段跳过', () => {
    expect(
      buildRecordName({ projectName: 'P', typeLabel: '实施方案' }, new Date(2026, 8, 29, 9, 5, 3))
    ).toBe('P_实施方案_20260929090503')
    expect(buildRecordName({ typeLabel: '实施方案' }, new Date(2026, 8, 29, 9, 5, 3))).toBe('实施方案_20260929090503')
  })
  it('版本空白视同无版本，数值版本可用', () => {
    expect(
      buildRecordName({ projectCode: 'C', projectName: 'P', typeLabel: '实施方案', version: '   ' }, new Date(2026, 8, 29, 23, 5, 3))
    ).toBe('C_P_实施方案_20260929230503')
    expect(
      buildRecordName({ projectCode: 'C', projectName: 'P', typeLabel: '实施方案', version: 3 }, new Date(2026, 8, 29, 9, 5, 3))
    ).toBe('C_P_实施方案_3')
  })
})
