import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const panel = read('./ProjectStageGatePanel.vue')
const detail = read('../index.vue')
const api = read('../../../../../api/pms/project/projects/index.ts')

describe('F-PROJ-008 project stage gate workspace', () => {
  it('connects the project workspace to readiness and adjacent stage advance APIs', () => {
    expect(detail).toContain("{ key: 'stage-gates', label: '阶段门禁'")
    expect(detail).toContain('<ProjectStageGatePanel')
    expect(api).toContain('/stage-advance-readiness')
    expect(api).toContain('/actions/advance-stage')
    expect(panel).toContain('readiness.value?.advanceAllowed')
  })

  it('starts Flowable with latest definition by default or an explicit native definition id', () => {
    expect(api).toContain('/process-definitions')
    expect(api).toContain('/actions/start-process')
    expect(panel).toContain('默认：最新生效定义')
    expect(panel).toContain("selected !== '__LATEST__' ? selected : undefined")
    expect(panel).toContain('definition.processDefinitionId')
    expect(panel).not.toContain('processDefinitionVersion')
  })

  it('renders ordered owner facts and lets the server decide available actions', () => {
    expect(panel).toContain('readiness.gates')
    expect(panel).toContain('gate.references')
    expect(panel).toContain("reference.allowedActions.includes('START_PROCESS')")
    expect(panel).toContain('reference.fact.providerKey')
    expect(panel).toContain('reference.fact.factVersion')
  })
})
