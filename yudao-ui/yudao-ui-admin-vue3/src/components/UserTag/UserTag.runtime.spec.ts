import { expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import UserTag from './index.vue'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const users = vi.hoisted(() => ({ getSimpleUser: vi.fn() }))
vi.mock('@/api/system/user', () => users)

it('resolves a long user ID without converting it to an imprecise number', async () => {
  const id = '2112914651700375582'
  users.getSimpleUser.mockResolvedValueOnce({ nickname: '测试人员' })
  const page = mount(UserTag, { userId: id })
  try {
    for (let i = 0; i < 5; i++) await nextTick()
    expect(users.getSimpleUser).toHaveBeenCalledWith(id)
    expect(textOf(page.root)).toContain('测试人员')
  } finally { page.app.unmount() }
})
