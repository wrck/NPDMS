import { describe, expect, it } from 'vitest'
import CutoverDashboardKpis from './components/CutoverDashboardKpis.vue'
import { mount, textOf } from '../../platform/dynamic-form/components/runtimeTestHarness'

describe('F-CUT-007 dashboard KPI cards', () => {
  it('renders all four WireLong values without coercing Snowflake-sized strings', () => {
    const mounted = mount(CutoverDashboardKpis, {
      data: {
        todoCount: '9007199254740993',
        archivedCount: 12,
        approvingCount: 3,
        rejectedPendingModificationCount: 2,
        generatedAt: 1788314400000
      },
      loading: false,
      error: null
    })

    const text = textOf(mounted.root)
    expect(text).toContain('割接任务概览')
    expect(text).toContain('9007199254740993')
    expect(text).toContain('已归档12')
    expect(text).toContain('审批中3')
    expect(text).toContain('驳回待修改2')
    expect(text).toContain('2026')
    mounted.app.unmount()
  })

  it('does not project a failed read as zero counts', () => {
    const mounted = mount(CutoverDashboardKpis, {
      data: null,
      loading: false,
      error: '割接任务概览加载失败，请稍后重试'
    })

    const text = textOf(mounted.root)
    expect(text).toContain('割接任务概览加载失败，请稍后重试')
    expect(text).not.toContain('待办0')
    mounted.app.unmount()
  })
})
