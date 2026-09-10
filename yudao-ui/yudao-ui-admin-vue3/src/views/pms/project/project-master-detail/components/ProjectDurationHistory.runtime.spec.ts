import { afterEach, expect, it, vi } from 'vitest'
import History from './ProjectDurationHistoryDrawer.vue'
import * as DurationApi from '@/api/pms/engineering/construction-plan'
import { mount, passthrough, tableColumn, findByTestId } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/engineering/construction-plan', () => ({ getRevisions: vi.fn(), getChanges: vi.fn() }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => ({ value: false }) }))
afterEach(() => vi.clearAllMocks())

it('projects unchanged effective, historical and candidate revisions into the native tables', async () => {
  const revisions = [
    { revisionId: 11, revisionNo: 2, startDate: '2026-12-01', endDate: '2026-12-31', durationDays: 31, calculationBasis: 'DATE_RANGE', current: true },
    { revisionId: 10, revisionNo: 1, startDate: '2026-12-02', endDate: '2026-12-31', durationDays: 30, calculationBasis: 'DATE_RANGE', current: false }
  ]
  const changes = [{ changeId: 33, status: 'APPROVED', candidateRevision: revisions[0], reasonDetail: '客户调整工期' }]
  vi.mocked(DurationApi.getRevisions).mockResolvedValue({ items: revisions, hasMore: false } as any)
  vi.mocked(DurationApi.getChanges).mockResolvedValue({ items: changes, hasMore: false } as any)
  const mounted = mount(History, {}, { ElTabs: passthrough, ElTabPane: passthrough, ElTable: passthrough, ElTableColumn: tableColumn })
  try {
    await (mounted.vm as any).open(7)
    expect(DurationApi.getRevisions).toHaveBeenCalledWith(7, { cursor: undefined, pageSize: 20 })
    expect(DurationApi.getChanges).toHaveBeenCalledWith(7, { cursor: undefined, pageSize: 20 })
    expect(findByTestId(mounted.root, 'duration-history-revisions')?.props?.data).toEqual(revisions)
    expect(findByTestId(mounted.root, 'duration-history-changes')?.props?.data).toEqual(changes)
  } finally { mounted.app.unmount() }
})
