import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const panel = read('./ProjectStageGatePanel.vue')
const statusPanel = read('./ProjectStageStatusPanel.vue')
const detail = read('../index.vue')
const inheritanceDetail = read('../../inheritance/detail/index.vue')
const api = read('../../../../../api/pms/project/projects/index.ts')

describe('F-PROJ-008 project stage gate workspace', () => {
  it('refreshes the new project header, instances and navigation after progression', () => {
    expect(inheritanceDetail).toMatch(/<ProjectStageStatusPanel\s[^>]*@changed="handleStageChanged"/)
    expect(inheritanceDetail).toContain('ref="flowNavigationRef"')
    expect(inheritanceDetail).toContain('Promise.all([loadDetail(), loadInstances(), flowNavigationRef.value?.reload()])')
  })

  it('reads independent stage states without requiring a single current stage or manual advancement', () => {
    expect(detail).not.toContain("{ key: 'stage-gates', label: '阶段门禁'")
    expect(detail).toContain('<ProjectStageStatusPanel')
    expect(statusPanel).toContain('getProjectInstances(props.projectId)')
    expect(statusPanel).not.toContain('advanceProjectStage')
    expect(statusPanel).not.toContain('currentStage')
    expect(statusPanel).toContain('error.value')
  })

  it('starts Flowable from the plan pin without a runtime version selector', () => {
    expect(api).toContain('/process-definitions')
    expect(api).toContain('/actions/start-process')
    expect(panel).toContain('按当前计划冻结的流程版本办理')
    expect(panel).not.toContain('__LATEST__')
    expect(panel).not.toContain('selectedDefinitions')
    expect(panel).not.toContain('processDefinitionVersion')
  })

  it('explains S0 responsibility blockers and never describes the graph terminal as project closure', () => {
    expect(panel).toContain('guidanceLabel(readiness.guidance)')
    expect(panel).toContain('S0_PRIMARY_SERVICE_MANAGER_REQUIRED')
    expect(panel).toContain('S0_PRIMARY_PROJECT_MANAGER_REQUIRED')
    expect(panel).toContain('阶段结束不等于项目闭环')
  })

  it('renders ordered owner facts and lets the server decide available actions', () => {
    expect(panel).toContain('readiness.gates')
    expect(panel).toContain('gate.references')
    expect(panel).toContain("reference.allowedActions.includes('START_PROCESS')")
    expect(panel).toContain('reference.fact.providerKey')
    expect(panel).toContain('reference.fact.factVersion')
  })
})
