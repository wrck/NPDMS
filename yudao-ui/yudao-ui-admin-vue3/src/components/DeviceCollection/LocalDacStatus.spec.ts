import { nextTick } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import Status from './LocalDacStatus.vue'
import JSZip from 'jszip'
import { ElMessage } from 'element-plus'
import { findByTestId, mount, passthrough, textOf } from '@/views/pms/cutover/cutoverTestHarness'

const discovery = vi.hoisted(() => ({ check: vi.fn(), pair: vi.fn(), prepare: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))
vi.mock('./localDac', () => ({ discoverLocalDac: discovery.check, pairLocalDac: discovery.pair, LOCAL_DAC_START_URI: 'sdp-dac://start/' }))
vi.mock('./localDacConnection', () => ({ prepareLocalDacConnection: discovery.prepare }))
const environment = { serverUrl: 'http://10.210.0.11:59191', webOrigin: 'http://10.210.0.11:19191' }
const flush = async () => { for (let i = 0; i < 6; i++) await nextTick() }
beforeEach(() => {
  vi.useFakeTimers()
  vi.clearAllMocks()
  discovery.prepare.mockResolvedValue({href:'sdp-dac://connect/test',expiresAt:Date.now()+90000,cancel:vi.fn().mockResolvedValue(undefined),finish:vi.fn()})
  vi.stubGlobal('window', Object.assign(new EventTarget(), { isSecureContext: false, location: { origin: environment.webOrigin } }))
})
afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals(); vi.useRealTimers() })

it('discovers the DAC from the approved HTTP page even outside a secure context', async () => {
  discovery.check.mockResolvedValue({ state: 'ready' })
  const m = mount(Status, { serverUrl: environment.serverUrl }, { ElTag: passthrough, ElButton: passthrough })
  try {
    await flush()
    expect(discovery.check).toHaveBeenCalledWith(expect.objectContaining(environment))
    expect(textOf(m.root)).toContain('本机 DAC 已就绪')
    expect(textOf(m.root)).not.toContain('HTTPS')
    expect(textOf(m.root)).not.toContain('下载并安装 DAC')
    await vi.advanceTimersByTimeAsync(60000)
    expect(discovery.check).toHaveBeenCalledTimes(1)
  } finally { m.app.unmount() }
})

it('shows protocol startup on an HTTP page and discovers readiness while polling', async () => {
  discovery.check.mockResolvedValueOnce({ state: 'unavailable' }).mockResolvedValue({ state: 'ready' })
  const m = mount(Status, { serverUrl: environment.serverUrl }, { ElTag: passthrough, ElButton: passthrough })
  try {
    await flush()
    expect(textOf(m.root)).toContain('连接本机 DAC')
    expect(textOf(m.root)).toContain('下载并安装 DAC')
    expect(textOf(m.root)).not.toContain('未安装 DAC')
    const link = findByTestId(m.root, 'dac-installer-download')
    expect(link?.props?.href).toBe('/downloads/dac/SDP-DAC-1.1.8-x64.msi')
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
    expect(findByTestId(m.root, 'dac-installer-download')?.props?.href).toBe('/downloads/dac/SDP-DAC-1.1.8-x64.msi')
  } finally { m.app.unmount() }
})

it('uses the packaged immutable installer filename instead of a mutable cached URL', async () => {
  vi.stubEnv('VITE_DAC_INSTALLER_FILENAME', 'NPDMS-DAC-1.1.5-x64-0123456789ab.msi')
  discovery.check.mockResolvedValue({ state: 'incompatible' })
  const m = mount(Status, { serverUrl: environment.serverUrl }, { ElTag: passthrough, ElButton: passthrough })
  try {
    await flush()
    const link = findByTestId(m.root, 'dac-installer-download')
    expect(link?.props?.href).toBe('/downloads/dac/NPDMS-DAC-1.1.5-x64-0123456789ab.msi')
    expect(link?.props?.onClick).toBeTypeOf('function')
  } finally { m.app.unmount() }
})

it('downloads a versioned ZIP with the actual distinct API and webpage addresses and blocks duplicate clicks', async () => {
  vi.useRealTimers()
  vi.stubEnv('VITE_DAC_INSTALLER_FILENAME', 'SDP-DAC-1.1.8-x64-0123456789ab.msi')
  discovery.check.mockResolvedValue({ state: 'unavailable' })
  const bytes = new Uint8Array([0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1, 7])
  let respond!: (response: unknown) => void
  const fetcher = vi.fn(() => new Promise(resolve => { respond = resolve }))
  vi.stubGlobal('fetch', fetcher)
  const anchor = { href: '', download: '', click: vi.fn() }
  vi.stubGlobal('document', { createElement: vi.fn(() => anchor) })
  let archive!: Blob
  const createUrl = vi.spyOn(URL, 'createObjectURL').mockImplementation(blob => { archive = blob as Blob; return 'blob:installer' })
  const mounted = mount(Status, { serverUrl: environment.serverUrl }, { ElTag: passthrough, ElButton: passthrough })
  try {
    await flush()
    const click = findByTestId(mounted.root, 'dac-installer-download')!.props.onClick
    const pending = click({ preventDefault() {} })
    await click({ preventDefault() {} })
    expect(fetcher).toHaveBeenCalledTimes(1)
    respond({ ok: true, arrayBuffer: async () => bytes.buffer })
    await pending
    expect(anchor.click).toHaveBeenCalledTimes(1)
    expect(anchor.download).toBe('SDP-DAC-1.1.8-x64.zip')
    const zip = await JSZip.loadAsync(await archive.arrayBuffer())
    expect(await zip.file('dac-environment.ini')!.async('string')).toContain(`ServerUrl=${environment.serverUrl}\r\nWebOrigin=${environment.webOrigin}`)
    expect(await zip.file('SDP-DAC-1.1.8-x64.msi')!.async('uint8array')).toEqual(bytes)
    expect(ElMessage.error).not.toHaveBeenCalled()
  } finally { mounted.app.unmount(); createUrl.mockRestore() }
})

it('rejects an HTML fallback returned as HTTP 200 instead of packaging a broken installer', async () => {
  discovery.check.mockResolvedValue({ state: 'unavailable' })
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, arrayBuffer: async () => new TextEncoder().encode('<html>SPA</html>').buffer }))
  const createElement = vi.fn()
  vi.stubGlobal('document', { createElement })
  const mounted = mount(Status, { serverUrl: environment.serverUrl }, { ElTag: passthrough, ElButton: passthrough })
  try {
    await flush()
    await findByTestId(mounted.root, 'dac-installer-download')!.props.onClick({ preventDefault() {} })
    expect(ElMessage.error).toHaveBeenCalledTimes(1)
    expect(createElement).not.toHaveBeenCalled()
  } finally { mounted.app.unmount() }
})

it('prepares a native anchor without launching and enables execution only after confirmation', async () => {
  discovery.check.mockResolvedValue({state:'connection-required'})
  const ready = vi.fn()
  const cancel = vi.fn().mockResolvedValue(undefined)
  let finish: ((value: { state: string }) => void) | undefined
  const connection = { href: 'sdp-dac://connect/test', expiresAt: Date.now() + 90000, cancel,
    finish: vi.fn(() => new Promise(resolve => { finish = resolve })) }
  discovery.prepare.mockResolvedValue(connection)
  const m=mount(Status,{serverUrl:environment.serverUrl,onReady:ready},{ElTag:passthrough,ElButton:passthrough,ElInput:passthrough})
  try {
    await flush()
    expect(textOf(m.root)).toContain('连接本机 DAC')
    expect(findByTestId(m.root, 'dac-connect')?.props?.href).toBe(connection.href)
    expect(connection.finish).not.toHaveBeenCalled()
    expect(ready).toHaveBeenLastCalledWith(false)
    const state=(m.vm as any).$.setupState
    const pending = state.connect({ preventDefault: vi.fn() })
    await flush()
    expect(textOf(m.root)).toContain('正在等待本机连接确认')
    expect(ready).not.toHaveBeenCalledWith(true)
    finish?.({state:'ready'})
    await pending;await flush()
    expect(ready).toHaveBeenLastCalledWith(true)
    expect(cancel).toHaveBeenCalled()
  } finally {m.app.unmount()}
})

it('aborts confirmation on unmount and never accepts a late result', async () => {
  discovery.check.mockResolvedValue({state:'connection-required'})
  let finish: ((value: { state: string }) => void) | undefined
  let signal: AbortSignal | undefined
  const cancel = vi.fn().mockResolvedValue(undefined)
  discovery.prepare.mockResolvedValue({href:'sdp-dac://connect/test',expiresAt:Date.now()+90000,cancel,
    finish:vi.fn((input:AbortSignal)=>{signal=input;return new Promise(resolve=>{finish=resolve})})})
  const ready=vi.fn()
  const m=mount(Status,{serverUrl:environment.serverUrl,onReady:ready},{ElTag:passthrough,ElButton:passthrough})
  await flush()
  const pending=(m.vm as any).$.setupState.connect({preventDefault:vi.fn()})
  m.app.unmount()
  expect(signal?.aborted).toBe(true)
  finish?.({state:'ready'});await pending;await flush()
  expect(ready).not.toHaveBeenCalledWith(true)
  expect(cancel).toHaveBeenCalled()
})
