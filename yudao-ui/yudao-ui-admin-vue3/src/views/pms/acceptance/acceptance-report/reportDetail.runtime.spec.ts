vi.mock('@/components/BusinessView/operationHost', async () => { const { shallowRef } = await import('vue'); return { useOperationClient: () => shallowRef(), useOperationCheck: () => async () => true } })
import { expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Detail from './detail.vue'
import * as ReportApi from '@/api/pms/acceptance/acceptance-report'
import { mount, passthrough, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/components/PmsFileArtifact/PmsFileVersionReferenceList.vue', () => ({ default: { inheritAttrs: false, props: ['artifactId', 'versionNo'], setup: (props: any) => () => `文件 ${props.artifactId} V${props.versionNo}` } }))

vi.mock('@vueuse/core', () => ({ useMediaQuery: () => ({ value: false }) }))
vi.mock('@/api/pms/acceptance/acceptance-report', () => ({ getActivity: vi.fn(), getReportVersions: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ warning: vi.fn(), confirm: vi.fn() }) }))
vi.mock('./ReportDraftEditor.vue', () => ({ default: { setup: (_: unknown, { expose }: any) => { expose({ isDirty: () => false, requestLeave: async () => true, discardChanges: () => true }); return () => null } } }))
vi.mock('./ReportVersionHistoryDrawer.vue', () => ({ default: { setup: (_: unknown, { expose }: any) => { expose({ close: vi.fn() }); return () => null } } }))

it('does not let a slow previous detail request replace the newly opened activity', async () => {
  let finishOld!: (value: any) => void
  vi.mocked(ReportApi.getActivity).mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve }))
    .mockResolvedValueOnce({ id: 2, projectId: 42, activityStatus: 'PENDING', acceptanceType: 'FINAL' } as any)
  vi.mocked(ReportApi.getReportVersions).mockResolvedValue([])
  const mounted = mount(Detail, {}, { ElSkeleton: passthrough, ElRow: passthrough, ElCol: passthrough })
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

it.each(['DRAFT', 'EFFECTIVE'])('shows %s report files directly and distinguishes saved drafts from effective completion', async status => {
  vi.mocked(ReportApi.getActivity).mockResolvedValue({ id: 12, projectId: 7, activityStatus: status === 'DRAFT' ? 'PENDING' : 'COMPLETED', acceptanceType: 'PRELIMINARY' } as any)
  vi.mocked(ReportApi.getReportVersions).mockResolvedValue([{ id: 31, acceptanceId: 12, reportVersionNo: 1, reportStatus: status,
    archiveStatus: status === 'EFFECTIVE' ? 'PENDING_COMPENSATION' : undefined,
    attachments: [{ sequence: 1, artifactId: '2112882539907608577', versionNo: 1, referenceKey: 'file-key' }] }] as any)
  const mounted = mount(Detail, {}, { ElSkeleton: passthrough, ElRow: passthrough, ElCol: passthrough })
  try {
    await (mounted.vm as any).open(12, 7)
    await nextTick()
    expect(textOf(mounted.root)).toContain('文件 2112882539907608577 V1')
    expect(textOf(mounted.root)).not.toContain('尚未上传交付件')
    if (status === 'DRAFT') {
      expect(textOf(mounted.root)).toContain('已上传的草稿交付件 V1')
      expect(textOf(mounted.root)).toContain('尚未生效')
      expect(textOf(mounted.root)).not.toContain('当前有效报告 V1')
    }
  } finally { mounted.app.unmount() }
})
