import { nextTick } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import Status from './LocalDacStatus.vue'
import { findByTestId, mount, passthrough, textOf } from '@/views/pms/cutover/cutoverTestHarness'

const discovery = vi.hoisted(() => ({ check: vi.fn() }))
vi.mock('./localDac', () => ({ discoverLocalDac: discovery.check, LOCAL_DAC_START_URI: 'npdms-dac://start/' }))
const environment = { serverUrl: 'http://10.210.0.11:59191', webOrigin: 'http://10.210.0.11:19191' }
const flush = async () => { for (let i = 0; i < 6; i++) await nextTick() }
beforeEach(() => {
  vi.useFakeTimers()
  vi.clearAllMocks()
  vi.stubGlobal('window', Object.assign(new EventTarget(), { isSecureContext: false, location: { origin: environment.webOrigin } }))
})
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers() })

it('discovers the DAC from the approved HTTP page even outside a secure context', async () => {
  discovery.check.mockResolvedValue({ state: 'ready' })
  const m = mount(Status, { serverUrl: environment.serverUrl }, { ElTag: passthrough, ElButton: passthrough })
  try {
    await flush()
    expect(discovery.check).toHaveBeenCalledWith(expect.objectContaining(environment))
    expect(textOf(m.root)).toContain('本机 DAC 已就绪')
    expect(textOf(m.root)).not.toContain('HTTPS')
    expect(textOf(m.root)).not.toContain('下载并安装 DAC')
  } finally { m.app.unmount() }
})

it('shows protocol startup on an HTTP page and discovers readiness while polling', async () => {
  discovery.check.mockResolvedValueOnce({ state: 'unavailable' }).mockResolvedValue({ state: 'ready' })
  const m = mount(Status, { serverUrl: environment.serverUrl }, { ElTag: passthrough, ElButton: passthrough })
  try {
    await flush()
    expect(textOf(m.root)).toContain('启动 DAC')
    expect(textOf(m.root)).toContain('下载并安装 DAC')
    expect(textOf(m.root)).not.toContain('未安装 DAC')
    const link = findByTestId(m.root, 'dac-installer-download')
    expect(link?.props?.href).toBe('/downloads/dac/NPDMS-DAC-1.0.5-x64.msi')
    await vi.advanceTimersByTimeAsync(1500)
    await flush()
    expect(textOf(m.root)).toContain('本机 DAC 已就绪')
  } finally { m.app.unmount() }
  const calls = discovery.check.mock.calls.length
  await vi.advanceTimersByTimeAsync(60000)
  expect(discovery.check).toHaveBeenCalledTimes(calls)
})

it('offers an installer update for an incompatible DAC', async () => {
  discovery.check.mockResolvedValue({ state: 'incompatible' })
  const m = mount(Status, { serverUrl: environment.serverUrl }, { ElTag: passthrough, ElButton: passthrough })
  try {
    await flush()
    expect(textOf(m.root)).toContain('下载更新 DAC')
    expect(findByTestId(m.root, 'dac-installer-download')?.props?.href).toBe('/downloads/dac/NPDMS-DAC-1.0.5-x64.msi')
  } finally { m.app.unmount() }
})
