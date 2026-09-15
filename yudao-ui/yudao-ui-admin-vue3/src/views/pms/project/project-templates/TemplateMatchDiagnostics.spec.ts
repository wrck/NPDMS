import { createSSRApp, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { describe, expect, it } from 'vitest'
import type { TemplateMatchEvaluation } from '@/api/pms/project/project-templates'
import TemplateMatchDiagnostics from './TemplateMatchDiagnostics.vue'

const evaluation = (outcome: 'MATCHED' | 'NOT_MATCHED' | 'UNKNOWN'): TemplateMatchEvaluation => ({
  templateId: '993009245200', templateRevisionId: '993009245201', ruleName: '工前准备适用条件',
  result: {
    kind: 'CONDITION', ruleVersionRef: 'tenant:7:template-revision:993009245201:match:applicable', outcome,
    reasonCode: outcome === 'UNKNOWN' ? 'FACT_UNAVAILABLE' : undefined,
    conditions: [{ key: 'condition0', path: 'rule.rules[0]', component: 'pmsRulePrepare', outcome,
      reasonCode: outcome === 'UNKNOWN' ? 'MATCH_FIELD_UNAVAILABLE' : undefined }],
    steps: [], diagnostics: []
  }
})
const render = (evaluations: TemplateMatchEvaluation[]) => renderToString(createSSRApp({
  render: () => h(TemplateMatchDiagnostics, { evaluations })
}))

describe('template matching diagnostics', () => {
  it('keeps unknown distinct from not matched and shows condition location', async () => {
    const html = await render([evaluation('UNKNOWN')])
    expect(html).toContain('未知')
    expect(html).not.toContain('不满足')
    expect(html).toContain('MATCH_FIELD_UNAVAILABLE')
    expect(html).toContain('rule.rules[0]')
    expect(html).toContain('pmsRulePrepare')
    expect(html).toContain('993009245201')
  })
  it('renders satisfied and unsatisfied results independently with escaped rule names', async () => {
    const first = evaluation('MATCHED')
    first.ruleName = '<script>untrusted</script>'
    const second = evaluation('NOT_MATCHED')
    second.templateRevisionId = '993009245202'
    const html = await render([first, second])
    expect(html).toContain('满足')
    expect(html).toContain('不满足')
    expect(html).toContain('&lt;script&gt;')
    expect(html).not.toContain('<script>')
    expect(html).toContain('<details')
    expect(html).not.toContain(' open')
  })
  it('does not display an empty diagnostics panel', async () => {
    expect(await render([])).not.toContain('<details')
  })
})
