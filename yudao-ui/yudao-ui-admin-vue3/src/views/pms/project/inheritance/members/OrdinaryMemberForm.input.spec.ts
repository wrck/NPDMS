// @vitest-environment happy-dom
import { afterEach, expect, it, vi } from 'vitest'
import { createApp, nextTick } from 'vue'
import ElementPlus from 'element-plus'
import OrdinaryMemberForm from './OrdinaryMemberForm.vue'
import * as Members from '@/api/pms/project/unified-members'

vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
vi.mock('@/api/pms/project/unified-members', async importOriginal => ({
  ...await importOriginal<typeof Members>(), getMemberCandidates: vi.fn(), saveMember: vi.fn()
}))
vi.mock('@/utils/permission', () => ({ checkPermi: vi.fn(() => true) }))
vi.mock('@/api/system/user/selection', () => ({
  sameSystemUserId: (left: unknown, right: unknown) => left != null && right != null && String(left) === String(right),
  systemUserSelection: {
    scoped: (source: any, request: any) => source.search(request),
    scopedResolve: async () => []
  }
}))
const apps: ReturnType<typeof createApp>[] = []
const settle = async () => { for (let index = 0; index < 10; index++) await nextTick() }
afterEach(() => {
  apps.splice(0).forEach(app => app.unmount())
  document.body.replaceChildren()
  vi.useRealTimers()
})

it('preserves incremental member email input through slow original-role candidate responses', async () => {
  vi.useFakeTimers()
  vi.mocked(Members.getMemberCandidates).mockImplementation(async () =>
    new Promise(resolve => setTimeout(() => resolve({ list: [{ id: 1, username: 'z01010', nickname: '张三' }], total: 1 }), 600))
  )
  const host = document.createElement('div')
  document.body.append(host)
  const app = createApp(OrdinaryMemberForm, { project: { id: 84575, version: 1 }, defaultRole: 'PROJECT_MANAGER' })
  app.use(ElementPlus)
  apps.push(app)
  app.mount(host)
  await settle()
  await vi.advanceTimersByTimeAsync(10)
  const field = host.querySelector<HTMLElement>('[aria-label="人员"]')!
  const input = field.querySelector('input')!
  input.focus()
  field.querySelector<HTMLElement>('.el-select__wrapper')!.click()
  await settle()
  for (const value of ['z', 'zh', 'zho', 'zhou', 'zhouw', 'zhouwen', 'zhouwencheng']) {
    input.value = value
    input.dispatchEvent(new InputEvent('input', { bubbles: true, data: value.at(-1), inputType: 'insertText' }))
    await settle()
    expect(input.value).toBe(value)
    await vi.advanceTimersByTimeAsync(350)
    await settle()
    expect(input.value).toBe(value)
    expect(field.querySelector('input')).toBe(input)
    expect(input.disabled).toBe(false)
  }
  await vi.advanceTimersByTimeAsync(1000)
  await settle()
  expect(input.value).toBe('zhouwencheng')
  expect(Members.getMemberCandidates).toHaveBeenLastCalledWith(84575, {
    keyword: 'zhouwencheng', pageNo: 1, pageSize: 20, projectRole: 'PROJECT_MANAGER'
  })
  expect(Members.saveMember).not.toHaveBeenCalled()
})
