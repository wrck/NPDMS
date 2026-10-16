import { expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive } from 'vue'
import Panel from './InspectionAnnotationPanel.vue'
import {
  mount,
  passthrough,
  tableColumn
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/utils/dict', () => ({ getStrDictOptions: () => [] }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => false }))
vi.mock('@/utils/formatTime', () => ({ formatDate: () => '' }))
vi.mock('@/api/system/user', () => ({ getSimpleUserList: vi.fn() }))

const render = () => {
  const task = reactive({
    id: '10',
    version: '1',
    currentReportId: '20',
    statusCode: 'ANNOTATION_PENDING',
    reports: [{ id: '20', statusCode: 'AVAILABLE', resultIds: ['31', '32'] }],
    results: [
      { id: '31', outcome: 'ABNORMAL' },
      { id: '32', outcome: 'ABNORMAL' }
    ],
    annotations: [],
    devices: []
  })
  const annotate = vi.fn()
  const page = mount(
    Panel,
    { task, disabled: false, onAnnotate: annotate },
    {
      ElDescriptions: passthrough,
      ElDescriptionsItem: passthrough,
      ElTable: defineComponent({
        setup(_, { expose, slots }) {
          expose({ toggleRowExpansion: vi.fn() })
          return () => h('section', slots.default?.())
        }
      }),
      ElTableColumn: tableColumn,
      ElCollapse: passthrough,
      ElCollapseItem: passthrough
    }
  )
  return { ...page, task, annotate, state: (page.vm as any).$.setupState }
}

it('preserves expanded and collapsed rows when an annotation save refreshes task data', async () => {
  const page = render()
  try {
    page.state.expandedResultIds = ['31']
    page.state.openEditor(page.task.results[0])
    await nextTick()
    page.state.form.kind = 'IGNORE'
    page.state.form.reason = 'reviewed evidence'
    page.state.formRef = { validate: async () => true, clearValidate: vi.fn() }
    await page.state.submitAnnotation()
    expect(page.annotate).toHaveBeenCalledWith(
      expect.objectContaining({ resultId: '31', kind: 'IGNORE' })
    )
    page.task.version = '2'
    page.task.results = page.task.results.map((row) => ({ ...row }))
    await nextTick()
    expect(page.state.expandedResultIds).toEqual(['31'])
    expect(page.state.selectedResultId).toBe('')
    expect(page.state.dirty).toBe(false)
    page.state.expandedResultIds = []
    page.task.version = '3'
    await nextTick()
    expect(page.state.expandedResultIds).toEqual([])
  } finally {
    page.app.unmount()
  }
})

it('clears expansion when switching to another report or task', async () => {
  const page = render()
  try {
    page.state.expandedResultIds = ['31']
    page.task.currentReportId = '21'
    await nextTick()
    expect(page.state.expandedResultIds).toEqual([])
    page.state.expandedResultIds = ['32']
    page.task.id = '11'
    await nextTick()
    expect(page.state.expandedResultIds).toEqual([])
  } finally {
    page.app.unmount()
  }
})
