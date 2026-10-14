import { vi } from 'vitest'

// Owner services and router are external to the mounted CUT components. Each
// business test still supplies its own server allowedActions and response facts.
vi.mock('@/utils/permission', () => ({ checkPermi: vi.fn(() => true) }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() })
}))
vi.mock('@/api/pms/project/projects', () => ({
  getProjectPage: vi.fn(async () => ({ list: [], total: 0 })),
  getProject: vi.fn(async (id) => ({ id })),
  getProjectMembers: vi.fn(async () => [])
}))
vi.mock('@/api/pms/asset/device', () => ({ getDevice: vi.fn(async () => ({})) }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getDictLabel: vi.fn(() => '') }))
vi.mock('@/config/axios', () => ({
  default: new Proxy({}, {
    get: (_, method) => () => { throw new Error(`Unstubbed Owner request: ${String(method)}`) }
  })
}))
vi.mock('@/config/axios/service', () => ({ service: vi.fn() }))
