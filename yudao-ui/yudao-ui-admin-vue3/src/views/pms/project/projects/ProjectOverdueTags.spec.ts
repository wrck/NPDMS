import { describe, expect, it } from 'vitest'
import { createSSRApp, defineComponent, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import ProjectOverdueTags from './ProjectOverdueTags.vue'
import type { ProjectMasterVO, ProjectOverdueNode } from '@/api/pms/project/projects'

const node: ProjectOverdueNode = {
  nodeKind: 'STAGE', nodeId: '9912345678901234567', nodeName: '施工准备',
  workOverdue: true, acceptanceOverdue: true
}
async function render(project: ProjectMasterVO) {
  const app = createSSRApp(ProjectOverdueTags, { project })
  const container = defineComponent({ setup: (_, { slots }) => () => h('span', slots.default?.()) })
  for (const name of ['ElSpace', 'ElTooltip', 'ElTag']) app.component(name, container)
  return renderToString(app)
}
describe('project overdue status', () => {
  it('shows both current overdue categories independently', async () => {
    const html = await render({ overdueNodes: [node] })
    expect(html).toContain('工期超期')
    expect(html).toContain('验收超期')
    expect(html).not.toContain('历史超期（已完成）')
  })
  it('keeps completed lateness visible without presenting it as current overdue', async () => {
    const html = await render({ overdueNodes: [], completedOverdueNodes: [{ ...node, completedAt: Date.now() }] })
    expect(html).toContain('历史超期（已完成）')
    expect(html).not.toContain('工期超期')
    expect(html).not.toContain('验收超期')
  })
  it('does not fabricate flags for unrecorded dates or old server responses', async () => {
    const html = await render({})
    expect(html).toContain('—')
    expect(html).not.toContain('超期')
  })
})
