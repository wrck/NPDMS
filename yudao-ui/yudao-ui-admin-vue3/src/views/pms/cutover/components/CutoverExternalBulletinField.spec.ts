import { describe, it, expect, vi, beforeEach } from 'vitest'
import { defineComponent, h, inject, nextTick, provide, reactive } from 'vue'
import { mount, passthrough, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Field from './CutoverExternalBulletinField.vue'
import type { CutoverExternalFeedback, CutoverExternalItemState } from '@/api/pms/cutover'

const { confirm } = vi.hoisted(() => ({ confirm: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessageBox: { confirm } }))
vi.mock('@/utils/formatTime', () => ({ formatDate: (value: unknown) => String(value) }))
const table = defineComponent({ props: ['data'], setup(props, { slots }) {
  provide('rows', () => props.data || [])
  return () => h('table', slots.default?.())
} })
const column = defineComponent({ props: ['prop', 'label'], setup(props, { slots }) {
  const rows = inject<() => Record<string, unknown>[]>('rows', () => [])
  return () => h('section', { 'data-column': props.label }, rows().map(row =>
    h('div', slots.default ? slots.default({ row }) : String(row[props.prop] ?? ''))))
} })
const input = defineComponent({ inheritAttrs: false, setup(_, { attrs }) { return () => h('textarea', attrs) } })
const select = defineComponent({ inheritAttrs: false, setup(_, { attrs, slots }) { return () => h('select', attrs, slots.default?.()) } })
const all = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(all)]
const state = (overrides = {}): CutoverExternalItemState => ({ itemId: '2', stableItemKey: 'BULLETIN', manualFallback: false, resultVersion: 1, interfaceCode: 'ITR_BULLETINS', providerAvailable: true,
  contextCurrent: true, resultId: '10', feedbackSaved: false, resultSourceCode: 'EXTERNAL',
  selectedQueryIds: ['1'], queries: [{ id: '1', deviceId: '100', versionContext: 'CURRENT', softwareVersion: 'R1',
    productModel: 'FW', succeeded: true, returnedRowCount: 0, startTime: 1788220800000, finishTime: null, failureCode: null, failureDetail: null }], bulletins: [], ...overrides })
const bulletin = { queryId: '1', sourceBulletinRowId: '20', number: 'B-01', subject: '原始主题',
  description: '原始描述', solution: '原始方案', involved: null, measures: null }
function render(overrides: Record<string, unknown> = {}) {
  const props = reactive({ state: state(), modelValue: [] as CutoverExternalFeedback[], readonly: false, busy: false, versionDirty: false, ...overrides })
  const load = vi.fn(), save = vi.fn(), update = vi.fn()
  const host = defineComponent({ setup: () => () => h(Field, { ...props,
    onLoad: load, onSave: save, 'onUpdate:modelValue': (value: CutoverExternalFeedback[]) => { props.modelValue = value; update(value) } }) })
  const mounted = mount(host, {}, { ElTable: table, ElTableColumn: column, ElInput: input, ElSelect: select,
    ElOption: passthrough, ElCollapse: passthrough, ElCollapseItem: passthrough })
  const button = (name: string) => all(mounted.root).find(n => n.type === 'button' && textOf(n).trim() === name)!
  return { ...mounted, props, load, save, update, button }
}
async function click(node: TestNode) {
  if (!node.props?.disabled) await (node.props?.onClick as () => unknown)()
  await nextTick()
}
beforeEach(() => confirm.mockReset())
describe('外部公告核查交互', () => {
  it('成功零条仍须显式确认，挂载不会自动保存', async () => {
    const r = render()
    expect(textOf(r.root)).toContain('查询成功，无公告（0 条）')
    expect(r.save).not.toHaveBeenCalled()
    await click(r.button('确认已核查并保存反馈'))
    expect(r.save).toHaveBeenCalledOnce()
    r.app.unmount()
  })
  it('失败不能伪装为零条，也不能保存', async () => {
    const r = render({ state: state({ queries: [{ id: '1', deviceId: '100', succeeded: false,
      returnedRowCount: 0, failureCode: 'TIMEOUT', failureDetail: '来源超时' }] }) })
    expect(textOf(r.root)).toContain('查询失败：来源超时')
    expect(textOf(r.root)).not.toContain('无公告（0 条）')
    expect(r.button('保存本项反馈').props?.disabled).toBe(true)
    await click(r.button('保存本项反馈'))
    expect(r.save).not.toHaveBeenCalled()
    r.app.unmount()
  })
  it('来源四列只读，核查两列更新反馈且不修改来源', async () => {
    const r = render({ state: state({ bulletins: [bulletin], queries: [{ id: '1', deviceId: '100', succeeded: true, returnedRowCount: 1 }] }) })
    for (const label of ['公告编号（原始）', '主题（原始）', '问题描述（原始）', '解决方案（原始）']) {
      const cell = all(r.root).find(n => n.props?.['data-column'] === label)!
      expect(all(cell).some(n => ['select', 'textarea'].includes(n.type))).toBe(false)
    }
    const selection = all(r.root).find(n => n.props?.['aria-label'] === '公告B-01是否涉及')!
    ;(selection.props?.['onUpdate:modelValue'] as (v: boolean) => void)(true)
    await nextTick()
    const measures = all(r.root).find(n => n.props?.['aria-label'] === '公告B-01防范措施')!
    ;(measures.props?.['onUpdate:modelValue'] as (v: string) => void)('样例核查措施')
    await nextTick()
    expect(r.update).toHaveBeenLastCalledWith([{ sourceBulletinRowId: '20', involved: true, measures: '样例核查措施' }])
    expect(bulletin.involved).toBe(null)
    await click(r.button('保存本项反馈'))
    expect(r.save).toHaveBeenCalledOnce()
    r.app.unmount()
  })
  it.each([{ versionDirty: true }, { state: state({ contextCurrent: false }) }])('版本变更或来源过期禁止反馈保存: %j', async overrides => {
    const r = render({ ...overrides, state: state({ ...((overrides as { state?: object }).state || {}),
      bulletins: [bulletin], queries: [{ id: '1', deviceId: '100', succeeded: true, returnedRowCount: 1 }] }) })
    const selection = all(r.root).find(n => n.props?.['aria-label'] === '公告B-01是否涉及')!
    const measures = all(r.root).find(n => n.props?.['aria-label'] === '公告B-01防范措施')!
    expect(selection.props?.disabled).toBe(true)
    expect(measures.props?.disabled).toBe(true)
    ;(selection.props?.['onUpdate:modelValue'] as (v: boolean) => void)(true)
    ;(measures.props?.['onUpdate:modelValue'] as (v: string) => void)('过期反馈')
    await nextTick()
    expect(r.update).not.toHaveBeenCalled()
    expect(r.button('保存本项反馈').props?.disabled).toBe(true)
    await click(r.button('保存本项反馈'))
    expect(r.save).not.toHaveBeenCalled()
    r.app.unmount()
  })
  it.each([{ state: state({ feedbackSaved: true }) }, { modelValue: [{ sourceBulletinRowId: '20', involved: true, measures: '本地修改' }] }])('刷新已有反馈或本地改动先确认，取消不加载: %j', async overrides => {
    const r = render(overrides)
    confirm.mockRejectedValueOnce('cancel')
    await click(r.button('重新加载外部公告'))
    expect(confirm).toHaveBeenCalledOnce()
    expect(r.load).not.toHaveBeenCalled()
    confirm.mockResolvedValueOnce('confirm')
    await click(r.button('重新加载外部公告'))
    expect(r.load).toHaveBeenCalledOnce()
    r.app.unmount()
  })
})
