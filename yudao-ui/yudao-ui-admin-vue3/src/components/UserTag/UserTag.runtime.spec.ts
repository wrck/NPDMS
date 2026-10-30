import { nextTick } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import UserTag from './index.vue'
import * as UserApi from '@/api/system/user'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/system/user', () => ({ getSimpleUser: vi.fn() }))
const flush = async () => { for (let i = 0; i < 8; i++) await nextTick() }
beforeEach(() => vi.resetAllMocks())

it('looks up a Java Long user identity without rounding its final digits', async () => {
  const id = '2112914651746513004'
  vi.mocked(UserApi.getSimpleUser).mockResolvedValue({
    id: 1, username: 'z01010', nickname: '周文成', deptId: 1, postIds: [],
    email: '', mobile: '', sex: 0, avatar: '', loginIp: '', status: 0, remark: '',
    loginDate: new Date('2026-10-25T00:00:00Z'), createTime: new Date('2026-10-25T00:00:00Z')
  })
  const view = mount(UserTag, { userId: id })
  try {
    await flush()
    expect(UserApi.getSimpleUser).toHaveBeenCalledWith(id)
    expect(textOf(view.root)).toBe('周文成')
  } finally { view.app.unmount() }
})

it('retains the exact identity in its fallback when the original lookup is denied', async () => {
  const id = '2112914651746513004'
  vi.mocked(UserApi.getSimpleUser).mockRejectedValue(new Error('Forbidden'))
  const view = mount(UserTag, { userId: id })
  try {
    await flush()
    expect(UserApi.getSimpleUser).toHaveBeenCalledTimes(1)
    expect(UserApi.getSimpleUser).toHaveBeenCalledWith(id)
    expect(textOf(view.root)).toBe(`用户#${id}`)
  } finally { view.app.unmount() }
})
