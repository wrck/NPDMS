import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, inject, nextTick, provide } from 'vue'
import { mount, passthrough, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import DeliveryScopePage from './index.vue'
import DeliveryScopeHistory from './DeliveryScopeHistoryDrawer.vue'

const api = vi.hoisted(() => ({ getDeliveryScopePage: vi.fn(), getDeliveryScopeVersion: vi.fn() }))
vi.mock('@/api/pms/commerce', () => api)
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('@vueuse/core', async () => {
  const { ref } = await import('vue')
  return { useWindowSize: () => ({ width: ref(1200) }) }
})
vi.mock('element-plus', () => ({ ElMessageBox: {} }))
vi.mock('./DeliveryScopeEditor.vue', () => ({ default: { render: () => null } }))

const Table = defineComponent({
  props: ['data'],
  setup(props, { slots }) {
    provide('scope-test-rows', () => props.data || [])
    return () => h('table', slots.default?.())
  }
})
const Column = defineComponent({
  props: ['prop', 'label', 'type'],
  setup(props, { slots }) {
    const rows = inject<() => Record<string, unknown>[]>('scope-test-rows', () => [])
    return () => props.type === 'expand' ? null : h('section', [props.label,
      ...rows().map(row => h('div', slots.default ? slots.default({ row }) : String(row[props.prop] ?? '')))])
  }
})
const components = Object.fromEntries(['ElDescriptions', 'ElDescriptionsItem', 'ElInputNumber', 'ElCheckbox']
  .map(name => [name, passthrough]))
Object.assign(components, { ElTable: Table, ElTableColumn: Column })
const apps: Array<{ unmount: () => void }> = []
afterEach(() => apps.splice(0).forEach(app => app.unmount()))
const row = (id: number, allocationConfirmed: boolean) => ({
  id, projectId: 10, orderLineId: 20, allocationVersion: id, allocationConfirmed,
  scopeStatus: 'ACTIVE', allocatedQuantity: 12, deliveredQuantity: 3, openQuantity: 9,
  departmentName: '杭州办事处', effectiveFrom: new Date(2026, 9, 9, 22).getTime(), details: []
})

describe('unconfirmed historical delivery scopes', () => {
  it('shows source quantities and the formal office, and only confirmed rows expose current actions', async () => {
    api.getDeliveryScopePage.mockResolvedValue({ list: [row(1, false), row(2, true)], total: 2 })
    api.getDeliveryScopeVersion.mockResolvedValue(2)
    const view = mount(DeliveryScopePage, { projectContext: { projectId: 10, projectVersion: 1, projectScopeVersion: 1 } }, components)
    apps.push(view.app)
    await vi.waitFor(() => expect(textOf(view.root)).toContain('未确认'))
    const text = textOf(view.root)
    expect(text).toContain('未确认')
    expect(text).toContain('已确认')
    expect(text).toContain('来源已发货数量33')
    expect(text).toContain('来源未交数量99')
    expect(text).toContain('发生时办事处杭州办事处杭州办事处')
    expect(text).toContain('2026-10-09 22:00:00')
    expect(text.match(/调整/g)).toHaveLength(1)
    expect(text.match(/释放/g)).toHaveLength(1)
  })
  it('does not present an unconfirmed open interval as a current confirmed allocation', async () => {
    api.getDeliveryScopePage.mockResolvedValue({ list: [row(1, false), row(2, true)], total: 2 })
    const view = mount(DeliveryScopeHistory, {}, components); apps.push(view.app)
    await (view.vm as unknown as { open: (scope: unknown) => Promise<void> }).open(row(1, false))
    await nextTick()
    expect(textOf(view.root)).toContain('失效时间未确认分配当前有效')
    expect(textOf(view.root)).toContain('2026-10-09 22:00:00')
    expect(api.getDeliveryScopePage).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: 10, orderLineId: 20, includeHistory: true }))
  })
})
