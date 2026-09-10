import { expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Detail from './detail.vue'
import * as ReportApi from '@/api/pms/project/acceptance-report'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@vueuse/core', () => ({ useMediaQuery: () => ({ value: false }) }))
vi.mock('@/api/pms/project/acceptance-report', () => ({ getActivity: vi.fn(), getReportVersions: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('./ReportDraftEditor.vue', () => ({ default: { setup: (_: unknown, { expose }: any) => { expose({ isDirty: () => false, requestLeave: async () => true, discardChanges: () => true }); return () => null } } }))
vi.mock('./ReportVersionHistoryDrawer.vue', () => ({ default: { setup: (_: unknown, { expose }: any) => { expose({ close: vi.fn() }); return () => null } } }))

it('does not let a slow previous detail request replace the newly opened activity', async () => {
  let finishOld!: (value: any) => void
  vi.mocked(ReportApi.getActivity).mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve }))
    .mockResolvedValueOnce({ id: 2, projectId: 42, activityStatus: 'PENDING', acceptanceType: 'FINAL' } as any)
  vi.mocked(ReportApi.getReportVersions).mockResolvedValue([])
  const mounted = mount(Detail, {}, { ElSkeleton: passthrough, ElDescriptions: passthrough, ElDescriptionsItem: passthrough })
  try {
    const first = (mounted.vm as any).open(1)
    for (let index = 0; index < 6; index++) { await Promise.resolve(); await nextTick() }
    expect(ReportApi.getActivity).toHaveBeenCalledWith(1)
    await (mounted.vm as any).open(2)
    finishOld({ id: 1, projectId: 41 })
    await first
    expect((mounted.vm as any).$.setupState.activity.id).toBe(2)
    expect((mounted.vm as any).discardChanges()).toBe(true)
    expect((mounted.vm as any).$.setupState.activity).toBeUndefined()
    expect((mounted.vm as any).$.setupState.visible).toBe(false)
  } finally { mounted.app.unmount() }
})
