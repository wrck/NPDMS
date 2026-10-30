import { beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import ProjectDeviceExportButton from './ProjectDeviceExportButton.vue'
import { exportProjectDevices } from '@/api/pms/asset/projectDeviceExport'
import { checkPermi } from '@/utils/permission'
import { officeProjectReadContext } from '@/utils/officeProjectReadonly'
import download from '@/utils/download'
import { findByTestId, mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const state = vi.hoisted(() => ({ error: vi.fn() }))
vi.mock('@/api/pms/asset/projectDeviceExport', () => ({ exportProjectDevices: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: vi.fn() }))
vi.mock('@/utils/officeProjectReadonly', () => ({ officeProjectReadContext: vi.fn() }))
vi.mock('@/utils/download', () => ({ default: { excel: vi.fn() } }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => state }))

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(checkPermi).mockReturnValue(true)
  vi.mocked(exportProjectDevices).mockResolvedValue(new Blob(['xlsx']))
})
const render = (props: Record<string, unknown> = {}) => mount(ProjectDeviceExportButton, {
  projectId: '2115000000000000001', kind: 'equipment', ...props
})

it('downloads the complete server export using a precise project ID', async () => {
  const m = render()
  try {
    await (findByTestId(m.root, 'project-device-export')!.props!.onClick as () => Promise<void>)()
    expect(exportProjectDevices).toHaveBeenCalledWith({ projectId: '2115000000000000001', kind: 'equipment', filters: undefined })
    expect(download.excel).toHaveBeenCalledWith(expect.any(Blob), '项目2115000000000000001-设备清单.xlsx')
  } finally { m.app.unmount() }
})
it('exports serial query filters without page number, row data or page size', async () => {
  const filters = { sn: '001SN', name: '交换机', productCode: '02010005' }
  const m = render({ kind: 'serials', filters })
  try {
    await (findByTestId(m.root, 'project-device-export')!.props!.onClick as () => Promise<void>)()
    expect(exportProjectDevices).toHaveBeenCalledWith({ projectId: '2115000000000000001', kind: 'serials', filters })
  } finally { m.app.unmount() }
})
it.each(['pms:project-device:export', 'pms:device:query'])('hides export without %s', async (missing) => {
  vi.mocked(checkPermi).mockImplementation(values => !values.includes(missing))
  const m = render()
  try {
    expect(findByTestId(m.root, 'project-device-export')).toBeUndefined()
    await (m.vm as any).$.setupState.exportFile()
    expect(exportProjectDevices).not.toHaveBeenCalled()
  } finally { m.app.unmount() }
})
it('blocks export in the office read-only channel even when permissions exist', async () => {
  vi.mocked(officeProjectReadContext).mockReturnValue({ projectId: '10', projectName: '项目', contractNo: '' })
  const m = render()
  try {
    expect(findByTestId(m.root, 'project-device-export')).toBeUndefined()
    await (m.vm as any).$.setupState.exportFile()
    expect(exportProjectDevices).not.toHaveBeenCalled()
  } finally { m.app.unmount() }
})
it('does not download a failed response and permits retry', async () => {
  vi.mocked(exportProjectDevices).mockRejectedValueOnce(new Error('403'))
  const m = render()
  try {
    const click = () => (m.vm as any).$.setupState.exportFile()
    await click(); await nextTick()
    expect(download.excel).not.toHaveBeenCalled()
    expect(state.error).toHaveBeenCalledOnce()
    await click()
    expect(download.excel).toHaveBeenCalledOnce()
  } finally { m.app.unmount() }
})
