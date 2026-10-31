import { beforeEach, expect, it, vi } from 'vitest'
import { store } from '@/store'
import { useUserStore } from './user'
import { useTagsViewStore } from './tagsView'
import { getInfo, loginOut } from '@/api/login'

vi.mock('@/store', async () => ({ store: (await import('pinia')).createPinia() }))
vi.mock('@/router', () => ({ default: {} }))
vi.mock('@/utils/routerHelper', () => ({ getRawRoute: (route: unknown) => route }))
vi.mock('@/utils', () => ({ findIndex: vi.fn() }))
vi.mock('@/utils/auth', () => ({ getAccessToken: () => 'test-session', removeToken: vi.fn() }))
vi.mock('@/hooks/web/useCache', () => ({
  CACHE_KEY: { USER: 'user', ROLE_ROUTERS: 'routes' },
  useCache: () => ({ wsCache: { get: vi.fn(), set: vi.fn() } }),
  deleteUserCache: vi.fn()
}))
vi.mock('@/api/login', () => ({ getInfo: vi.fn(), loginOut: vi.fn() }))

const user = () => useUserStore(store)
const tags = () => useTagsViewStore(store)
const account = (id: number) => ({ user: { id, avatar: '', nickname: `User ${id}`, deptId: 1 }, permissions: [], roles: [], menus: [] })
const visitProject = () => {
  const route = { path: '/projects/detail', fullPath: '/projects/detail?projectId=41', name: 'ProjectDetail', meta: { title: '项目详情' } } as any
  tags().addView(route)
  tags().setSelectedTag(route)
}

beforeEach(() => {
  vi.resetAllMocks()
  user().$reset()
  tags().$reset()
  vi.mocked(getInfo).mockResolvedValue(account(11) as any)
  vi.mocked(loginOut).mockResolvedValue(undefined)
})

it('clears visited, cached and selected project tabs on logout, including affixed tabs', async () => {
  await user().setUserInfoAction()
  visitProject()
  tags().visitedViews[0].meta.affix = true
  await user().loginOut()
  expect(tags().getVisitedViews).toEqual([])
  expect(tags().getCachedViews).toEqual([])
  expect(tags().getSelectedTag).toBeUndefined()
  expect(user().getIsSetUser).toBe(false)
})

it('preserves tabs for the same account but discards them when another account is loaded', async () => {
  await user().setUserInfoAction()
  visitProject()
  await user().setUserInfoAction()
  expect(tags().getVisitedViews).toHaveLength(1)
  vi.mocked(getInfo).mockResolvedValue(account(22) as any)
  await user().setUserInfoAction()
  expect(tags().getVisitedViews).toEqual([])
  expect(tags().getCachedViews).toEqual([])
  expect(tags().getSelectedTag).toBeUndefined()
  expect(user().getUser.id).toBe(22)
})
