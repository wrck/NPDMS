// @vitest-environment happy-dom
import { afterEach, expect, it, vi } from 'vitest'
import { createApp, nextTick } from 'vue'
import { ElButton, ElOption, ElSelect, ElTag } from 'element-plus'
import Scoped from './SystemUserScoped.vue'

vi.mock('@/api/system/user/selection', () => ({
  sameSystemUserId: (left: unknown, right: unknown) => left != null && right != null && String(left) === String(right),
  systemUserSelection: {
    scoped: (source: any, request: any) => source.search(request),
    scopedResolve: async () => []
  }
}))

const apps: ReturnType<typeof createApp>[] = []
const settle = async () => {
  for (let index = 0; index < 10; index++) await nextTick()
}
afterEach(() => {
  apps.splice(0).forEach(app => app.unmount())
  document.body.replaceChildren()
  vi.useRealTimers()
})

it('keeps every typed character while the real remote select changes its loading and candidate rows', async () => {
  vi.useFakeTimers()
  const search = vi.fn().mockResolvedValue({ list: [{ id: '1', nickname: '周文成' }], total: 1 })
  const host = document.createElement('div')
  document.body.append(host)
  const app = createApp(Scoped, { scopedSource: { key: 'input-regression', search } })
  apps.push(app)
  for (const component of [ElSelect, ElOption, ElButton, ElTag]) app.component(component.name!, component)
  app.mount(host)
  const input = host.querySelector('input')!
  input.focus()
  host.querySelector<HTMLElement>('.el-select__wrapper')!.click()
  await settle()
  await vi.advanceTimersByTimeAsync(10)
  for (const value of ['z', 'zh', 'zho', 'zhou', 'zhouw', 'zhouwen', 'zhouwencheng']) {
    input.value = value
    input.dispatchEvent(new InputEvent('input', { bubbles: true, data: value.at(-1), inputType: 'insertText' }))
    await settle()
    expect(input.value).toBe(value)
    await vi.advanceTimersByTimeAsync(320)
    await settle()
    expect(input.value).toBe(value)
  }
  expect(search).toHaveBeenLastCalledWith({ keyword: 'zhouwencheng', pageNo: 1, pageSize: 20 })
})

it('keeps composition text and searches its final value after the native select processes compositionend', async () => {
  vi.useFakeTimers()
  const search = vi.fn().mockResolvedValue({ list: [], total: 0 })
  const host = document.createElement('div')
  document.body.append(host)
  const app = createApp(Scoped, { scopedSource: { key: 'composition-regression', search } })
  apps.push(app)
  for (const component of [ElSelect, ElOption, ElButton, ElTag]) app.component(component.name!, component)
  app.mount(host)
  const input = host.querySelector('input')!
  input.focus()
  host.querySelector<HTMLElement>('.el-select__wrapper')!.click()
  await settle()
  await vi.advanceTimersByTimeAsync(10)
  input.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }))
  input.value = '中文'
  input.dispatchEvent(new InputEvent('input', { bubbles: true, data: '中文', isComposing: true }))
  await settle()
  await vi.advanceTimersByTimeAsync(400)
  expect(input.value).toBe('中文')
  expect(search).toHaveBeenCalledTimes(1)
  input.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true, data: '中文' }))
  await settle()
  await vi.advanceTimersByTimeAsync(320)
  await settle()
  expect(input.value).toBe('中文')
  expect(search).toHaveBeenLastCalledWith({ keyword: '中文', pageNo: 1, pageSize: 20 })
})
