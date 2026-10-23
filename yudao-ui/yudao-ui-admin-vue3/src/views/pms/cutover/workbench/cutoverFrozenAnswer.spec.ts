import { describe, expect, it } from 'vitest'
import CutoverFrozenAnswer from '../components/CutoverFrozenAnswer.vue'
import { mount, textOf } from '../cutoverTestHarness'

describe('frozen checklist answer descriptions', () => {
  it('uses the original field descriptions and retains filled conditional fields', () => {
    const page = mount(CutoverFrozenAnswer, {
      snapshot: JSON.stringify({ value: { majorCutover: false, spareReference: '11' } }),
      schema: { fields: [
        { code: 'majorCutover', label: '本次是否为重大割接项目', type: 'BOOLEAN' },
        { code: 'spareReference', label: '备件外部申请引用或人工核验说明', visibleWhen: { field: 'majorCutover', equals: true } }
      ] }
    })
    expect(textOf(page.root)).toContain('本次是否为重大割接项目')
    expect(textOf(page.root)).toContain('否')
    expect(textOf(page.root)).toContain('备件外部申请引用或人工核验说明')
    expect(textOf(page.root)).toContain('11')
    expect(textOf(page.root)).not.toContain('majorCutover')
    page.app.unmount()
  })

  it('propagates nested table schemas, custom labels and option descriptions without changing facts', () => {
    const value = { reports: { rows: [{ outcome: 'OK', count: 0, serial: '9007199254740993', legacyValue: '保留历史字段' }] } }
    const original = JSON.stringify(value)
    const page = mount(CutoverFrozenAnswer, {
      snapshot: JSON.stringify({ analysis: { value } }),
      schema: { fields: [{ code: 'reports', name: '核查报告', columns: [
        { code: 'outcome', label: '核查结论', options: [{ value: 'OK', label: '符合要求' }] },
        { code: 'count', label: '异常数量' }, { code: 'serial', label: '来源标识' }
      ] }] }
    })
    const text = textOf(page.root)
    for (const expected of ['核查报告', '核查结论', '符合要求', '异常数量', '0', '来源标识', '9007199254740993', '保留历史字段']) expect(text).toContain(expected)
    expect(text).not.toContain('outcome')
    expect(text).not.toContain('rows')
    expect(JSON.stringify(value)).toBe(original)
    page.app.unmount()
  })

  it.each([['是否符合本次部署要求'], ['检查是否正常']])('keeps item-specific normal labels: %s', (label) => {
    const page = mount(CutoverFrozenAnswer, { value: { normal: true }, schema: { fields: [{ code: 'normal', label }] } })
    expect(textOf(page.root)).toContain(label)
    expect(textOf(page.root)).toContain('是')
    page.app.unmount()
  })

  it('retains file versions, unknown values and malformed historical snapshots', () => {
    for (const [props, expected] of [
      [{ value: { artifactId: '9007199254740993', versionNo: 2 } }, '已提交附件（第 2 版）'],
      [{ value: 'RETIRED', schema: { options: [{ value: 'OK', label: '符合要求' }] } }, 'RETIRED'],
      [{ snapshot: '历史原文{' }, '历史原文{'],
      [{ value: null }, '—']
    ] as const) {
      const page = mount(CutoverFrozenAnswer, props)
      expect(textOf(page.root)).toContain(expected)
      page.app.unmount()
    }
  })

  it('describes collection provenance and manual fallback evidence without hiding failure codes', () => {
    const page = mount(CutoverFrozenAnswer, { snapshot: JSON.stringify({
      file: { artifactId: '9007199254740993', versionNo: 2 },
      originalFailedCollectionTaskId: '9007199254740995', originalFailedResultVersion: 2,
      originalFailureCode: 'CONNECT_TIMEOUT'
    }) })
    const text = textOf(page.root)
    for (const expected of ['附件', '已提交附件（第 2 版）', '原失败采集任务编号', '9007199254740995', '原失败采集结果版本', '原采集失败编码', 'CONNECT_TIMEOUT']) expect(text).toContain(expected)
    expect(text).not.toContain('originalFailed')
    page.app.unmount()
  })
})
