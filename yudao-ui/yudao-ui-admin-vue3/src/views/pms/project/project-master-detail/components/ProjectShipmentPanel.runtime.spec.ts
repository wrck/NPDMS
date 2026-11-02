import { beforeEach, expect, it, vi } from 'vitest'
import Panel from './ProjectShipmentPanel.vue'
import { mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@/api/pms/asset/project-shipment', () => ({ getProjectShipments: api.get }))
const components = { ElInput: { render: () => null }, ElTable: { render: () => null }, ElTableColumn: { render: () => null } }
beforeEach(() => { api.get.mockReset() })
it('keeps repeated serial shipment records and project scope', async () => {
  api.get.mockResolvedValue({ list: [{ id: 1, deviceSn: 'SN' }, { id: 2, deviceSn: 'SN' }], total: 2 })
  const m = mount(Panel, { projectId: 86648, active: true }, components)
  try {
    await vi.waitFor(() => expect((m.vm as any).$.setupState.rows).toHaveLength(2))
    expect(api.get).toHaveBeenCalledWith({ projectId: 86648, sn: undefined, pageNo: 1, pageSize: 20 })
    expect((m.vm as any).$.setupState.total).toBe(2)
  } finally { m.app.unmount() }
})
it('reports query failure without stale records or count', async () => {
  api.get.mockRejectedValue(new Error('forbidden'))
  const m = mount(Panel, { projectId: 86648, active: true }, components)
  try {
    await vi.waitFor(() => expect((m.vm as any).$.setupState.error).toBe(true))
    expect((m.vm as any).$.setupState.rows).toEqual([])
    expect((m.vm as any).$.setupState.total).toBe(0)
    expect((m.vm as any).$.setupState.loading).toBe(false)
  } finally { m.app.unmount() }
})
it('inactive panel does not query', async () => {
  const m = mount(Panel, { projectId: 86648, active: false }, components)
  try { expect(api.get).not.toHaveBeenCalled() } finally { m.app.unmount() }
})
