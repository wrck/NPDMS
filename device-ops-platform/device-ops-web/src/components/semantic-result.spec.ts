import ElementPlus from 'element-plus'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import SemanticEntityView from '@/components/SemanticEntityView.vue'
import SemanticResultPanel from '@/components/SemanticResultPanel.vue'
import type { CollectionSemanticResult, GenericStructureStatus, GenericUnit } from '@/types/parser'
import { flattenSemanticEvidence } from '@/utils/semantic-result'

function createUnit(sectionIndex: number, status: GenericStructureStatus = 'STRUCTURED'): GenericUnit {
  return {
    commandIndex: sectionIndex,
    parentCommandIndex: 3,
    sectionIndex,
    nestingDepth: 1,
    commandText: `nested command ${sectionIndex}`,
    sourceLineStart: sectionIndex * 2,
    sourceLineEnd: sectionIndex * 2 + 1,
    structureStatus: status,
    sections: status === 'EMPTY' ? [] : [{
      sectionIndex: 1,
      type: 'text',
      startLine: sectionIndex * 2,
      endLine: sectionIndex * 2,
      rawLines: [`value ${sectionIndex}`],
      warnings: [],
      lines: [{ lineNumber: sectionIndex * 2, value: `value ${sectionIndex}` }]
    }],
    warnings: status === 'PARTIAL' ? [{ code: 'UNPARSED_LINE', lineNumber: sectionIndex * 2 }] : [],
    omittedLineCount: status === 'LIMITED' ? 7 : undefined
  }
}

function createGenericResult(withGenericContent = true): CollectionSemanticResult {
  const nestedUnits = Array.from({ length: 50 }, (_, index) =>
    createUnit(index + 1, index < 12 ? 'EMPTY' : index === 12 ? 'PARTIAL' : index === 13 ? 'LIMITED' : 'STRUCTURED'))
  return {
    targetId: 1,
    taskId: 'task-generic',
    state: 'SUCCEEDED',
    releaseId: 'release-1.3',
    coordinate: { logType: 'device-command-output', releaseVersion: '1.3.0', engineVersion: '1.3.0',
      ruleVersion: '1.3.0', projectionVersion: '1.3.0' },
    result: {
      resultId: 'result-generic', taskId: 'task-generic', releaseId: 'release-1.3',
      createdAt: '2026-08-29T00:00:00Z', coordinate: { logType: 'device-command-output',
        releaseVersion: '1.3.0', engineVersion: '1.3.0', ruleVersion: '1.3.0', projectionVersion: '1.3.0' },
      contextSnapshot: {},
      semanticResult: {
        schemaVersion: '1.1.0', parserVersion: '1.3.0', ruleVersion: '1.3.0', projectionVersion: '1.3.0',
        snapshot: {}, projections: {}, quality: {},
        observations: [{ commandIndex: 3, status: 'UNPARSED', confidence: 0,
          matchedRuleIds: [], warnings: [] }],
        nestedObservations: nestedUnits.map(unit => ({
          parentCommandIndex: 3,
          sectionIndex: unit.sectionIndex!,
          nestingDepth: 1,
          commandText: unit.commandText,
          status: 'UNPARSED' as const,
          confidence: 0,
          sourceLineStart: unit.sourceLineStart,
          sourceLineEnd: unit.sourceLineEnd,
          matchedRuleIds: [],
          warnings: []
        })),
        ...(withGenericContent ? { genericContent: { units: [{
          commandIndex: 3, parentCommandIndex: null, sectionIndex: null, nestingDepth: 0,
          commandText: 'show tech', sourceLineStart: 1, sourceLineEnd: 120,
          structureStatus: 'STRUCTURED' as const, sections: [], warnings: []
        }, ...nestedUnits] } } : {})
      }
    }
  }
}

describe('semantic result evidence', () => {
  it('flattens scalar and list facts with source metadata', () => {
    const rows = flattenSemanticEvidence({
      entities: {
        device: { identity: { model: { semanticKey: 'device.identity.model', value: 'MODEL-X', confidence: 0.9,
          source: { commandIndex: 1, commandText: 'show tech', lineStart: 4, lineEnd: 4,
            nestingDepth: 1, nestedCommandText: 'show version', sectionIndex: 2 } } } },
        modules: [{ semanticKey: 'device.hardware.modules', value: { slot: 1 }, source: { commandIndex: 1 } }]
      }
    })

    expect(rows).toHaveLength(2)
    expect(rows[0]).toMatchObject({ semanticKey: 'device.identity.model', commandText: 'show tech',
      lineStart: 4, nestingDepth: 1, nestedCommandText: 'show version', sectionIndex: 2 })
    expect(rows[1]?.value).toEqual({ slot: 1 })
  })

  it('renders object arrays as expandable semantic entries', () => {
    const wrapper = mount(SemanticEntityView, {
      props: {
        projections: {
          runningConfiguration: { stanzas: [
            { header: 'interface eth0', startLine: 10, endLine: 11, lines: ['interface eth0', ' address 10.0.0.1'] },
            { header: 'interface eth1', startLine: 12, endLine: 12, lines: ['interface eth1'] }
          ] },
          technicalDiagnostics: { sections: [
            { command: 'show version', startLine: 2, endLine: 5, lines: ['Model: VPN-X'] },
            { command: 'mystery query', startLine: 6, endLine: 7, lines: ['opaque result'] }
          ] }
        }
      },
      global: { plugins: [ElementPlus] }
    })

    expect(wrapper.text()).toContain('2 项')
    expect(wrapper.text()).toContain('interface eth0 · 行 10–11')
    expect(wrapper.text()).toContain('show version · 行 2–5')
    expect(wrapper.text()).toContain('Model: VPN-X')
  })

  it('shows nested observations and preserves complete arrays in raw JSON', async () => {
    const projections = {
      technicalDiagnostics: { sections: [
        { command: 'show version', startLine: 2, endLine: 5, lines: ['Model: VPN-X'] },
        { command: 'mystery query', startLine: 6, endLine: 7, lines: ['opaque result'] }
      ] }
    }
    const result = {
      targetId: 1,
      taskId: 'task-1',
      state: 'SUCCEEDED',
      releaseId: 'release-1',
      coordinate: { logType: 'device-command-output', releaseVersion: '1.2.0', engineVersion: '1.2.0',
        ruleVersion: '1.2.0', projectionVersion: '1.2.0' },
      result: {
        resultId: 'result-1', taskId: 'task-1', releaseId: 'release-1', createdAt: '2026-08-29T00:00:00Z',
        coordinate: { logType: 'device-command-output', releaseVersion: '1.2.0', engineVersion: '1.2.0',
          ruleVersion: '1.2.0', projectionVersion: '1.2.0' }, contextSnapshot: {},
        semanticResult: { schemaVersion: '1.0.0', parserVersion: '1.2.0', ruleVersion: '1.2.0',
          projectionVersion: '1.2.0', snapshot: {}, projections, quality: {}, observations: [],
          nestedObservations: [{ parentCommandIndex: 3, sectionIndex: 1, nestingDepth: 1,
            commandText: 'show version', blockRole: 'DEVICE_VERSION', status: 'OBSERVED', confidence: 0.85,
            sourceLineStart: 2, sourceLineEnd: 5, matchedRuleIds: ['version'], warnings: [] }] }
      }
    } satisfies CollectionSemanticResult
    const wrapper = mount(SemanticResultPanel, {
      props: { results: [result] },
      global: { plugins: [ElementPlus] }
    })

    expect(wrapper.text()).toContain('内嵌命令块 1 项')
    expect(wrapper.text()).toContain('父命令行 2–5')
    const rawTab = wrapper.findAll('.el-tabs__item').find(item => item.text() === '原始 JSON')
    await rawTab?.trigger('click')
    expect(wrapper.find('.semantic-result__raw').text()).toContain('"sections"')
    expect(wrapper.find('.semantic-result__raw').text()).toContain('"mystery query"')
  })

  it('shows generic structure first only for 1.3 results and preserves raw content', async () => {
    const wrapper = mount(SemanticResultPanel, {
      props: { results: [createGenericResult()] },
      global: { plugins: [ElementPlus] }
    })

    expect(wrapper.findAll('.el-tabs__item').map(item => item.text())).toEqual([
      '通用结构', '实体视图', '字段证据', '原始 JSON'
    ])
    expect(wrapper.text()).toContain('show tech')
    expect(wrapper.text()).toContain('已结构化、未语义映射')

    const topUnit = wrapper.find('[data-unit-key="unit:3"] .el-collapse-item__header')
    await topUnit.trigger('click')
    expect(wrapper.findAll('[data-nested-unit]').length).toBe(50)
    expect(wrapper.findAll('[data-structure-status="EMPTY"]').length).toBe(12)
    expect(wrapper.text()).toContain('省略 7 行')
    expect(wrapper.text()).toContain('UNPARSED_LINE · 行 26')

    const rawTab = wrapper.findAll('.el-tabs__item').find(item => item.text() === '原始 JSON')
    await rawTab?.trigger('click')
    expect(wrapper.find('.semantic-result__raw').text()).toContain('"genericContent"')
  })

  it('keeps the legacy three-tab layout when generic content is absent', () => {
    const wrapper = mount(SemanticResultPanel, {
      props: { results: [createGenericResult(false)] },
      global: { plugins: [ElementPlus] }
    })

    expect(wrapper.findAll('.el-tabs__item').map(item => item.text())).toEqual([
      '实体视图', '字段证据', '原始 JSON'
    ])
  })
})
