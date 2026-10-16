import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, provide, ref } from 'vue'
import { nativeBusinessKey, type NativeBusinessContext } from '@/components/ProjectBusiness/native/nativeBusinessContext'
import Branches from './SurveyPreparationBranches.vue'
import { mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ exchange: vi.fn(), material: vi.fn(), procurement: vi.fn(), outsource: vi.fn(), permission: vi.fn() }))
vi.mock('@/api/pms/engineering/material-exch', () => ({ getMaterialExchange: api.exchange, getMaterialExchangePage: vi.fn() }))
vi.mock('@/api/pms/engineering/material-req', () => ({ getMaterialRequisition: api.material, getMaterialRequisitionPage: vi.fn() }))
vi.mock('@/api/pms/engineering/ext-proc', () => ({ getExternalProcurement: api.procurement, getExternalProcurementPage: vi.fn() }))
vi.mock('@/api/pms/engineering/outsource', () => ({ getOutsourceRequest: api.outsource }))
vi.mock('@/utils/permission', () => ({ checkPermi: api.permission }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {} }))
vi.mock('@/views/pms/engineering/material-exch/MaterialExchangeDetailDialog.vue', () => ({ default: defineComponent({ setup: () => () => h('exchange-detail') }) }))
vi.mock('@/views/pms/engineering/material-req/MaterialRequisitionDetailDialog.vue', () => ({ default: defineComponent({ setup: () => () => h('material-detail') }) }))
vi.mock('@/views/pms/engineering/ext-proc/ExternalProcurementDetailDialog.vue', () => ({ default: defineComponent({ setup: () => () => h('procurement-detail') }) }))
vi.mock('@/views/pms/engineering/outsource/OutsourceRequestDetailDialog.vue', () => ({ default: defineComponent({ setup: () => () => h('outsource-detail') }) }))
beforeEach(() => vi.resetAllMocks())
it('loads the full record for all four details and prevents a failed request showing the previous record', async () => {
  const m = mount(Branches)
  const state = (m.vm as any).$.setupState
  try {
    for (const kind of ['exchange', 'material', 'procurement', 'outsource'] as const) {
      api[kind].mockResolvedValue({ id: 12, code: kind, remark: 'full record' })
      await state.view(kind, 12)
      expect(api[kind]).toHaveBeenCalledWith(12)
      expect(state.detailVisible).toBe(true)
      expect(state.detailKind).toBe(kind)
      expect(state.detail.remark).toBe('full record')
    }
    api.exchange.mockRejectedValue(new Error('denied'))
    await state.view('exchange', 13)
    expect(state.detailVisible).toBe(false)
    expect(state.error).toContain('详情加载失败')
  } finally { m.app.unmount() }
})
it('does not replace a newer selected application with a late response', async () => {
  const m = mount(Branches)
  const state = (m.vm as any).$.setupState
  let resolve: (record: any) => void = () => undefined
  api.exchange.mockImplementation(() => new Promise(done => { resolve = done }))
  api.outsource.mockResolvedValue({ id: 22 })
  try {
    const first = state.view('exchange', 12)
    await state.view('outsource', 22)
    resolve({ id: 12 }); await first
    expect(state.detailKind).toBe('outsource')
    expect(state.detail.id).toBe(22)
  } finally { m.app.unmount() }
})

it('reads the bound outsource application and keeps it when the requirement is unchecked', async () => {
  api.permission.mockImplementation((permissions: string[]) => permissions[0] === 'pms:res-outsource:query')
  api.outsource.mockResolvedValue({ id: 22, code: 'OS-22', status: 0 })
  const m = mount(Branches, { getSurvey: () => ({ id: 12, projectId: 7, outsourceRequired: false, outsourceRequestId: 22 }) })
  try {
    const state = (m.vm as any).$.setupState
    await state.load()
    expect(api.outsource).toHaveBeenCalledWith(22)
    expect(state.applications.outsource).toEqual([{ id: 22, code: 'OS-22', status: 0 }])
    expect(state.evaluated.find((branch: any) => branch.title === '整单转包（独立流程）').state).toBe('NOT_MATCHED')
  } finally { m.app.unmount() }
})

it('opens each hosted external detail through its own bridge without rounding linked ids', async () => {
  const child = ref(), reads = Object.fromEntries(['exchange', 'material', 'procurement', 'outsource']
    .map(kind => [kind, vi.fn().mockResolvedValue({ value: { id: '2108608319822127106', projectId: 7, version: 4, code: kind } })]))
  const m = mount(defineComponent({ setup() {
    provide(nativeBusinessKey, { getBridge: kind => ({ read: reads[kind!] }) } as unknown as NativeBusinessContext)
    return () => h(Branches, { ref: child })
  } }))
  try {
    const state = child.value.$.setupState
    for (const kind of ['exchange', 'material', 'procurement', 'outsource'] as const) {
      await state.view(kind, '2108608319822127106')
      expect(reads[kind]).toHaveBeenCalledWith('2108608319822127106')
      expect(api[kind]).not.toHaveBeenCalled()
      expect(state.detail).toMatchObject({ id: '2108608319822127106', version: 4, code: kind })
    }
  } finally { m.app.unmount() }
})
