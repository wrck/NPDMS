import { vi } from 'vitest'

// The mounted-node harness needs the browser unload event contract.
vi.stubGlobal('window', new EventTarget())

// Owner services and router are external to the mounted CUT components. Each
// business test still supplies its own server allowedActions and response facts.
vi.mock('@/utils/permission', () => ({ checkPermi: vi.fn(() => true) }))
vi.mock('vue-router', () => ({
  onBeforeRouteLeave: vi.fn(), onBeforeRouteUpdate: vi.fn(),
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() })
}))
vi.mock('@/api/pms/project/projects', () => ({
  getProjectPage: vi.fn(async () => ({ list: [], total: 0 })),
  getProject: vi.fn(async (id) => ({ id })),
  getProjectMembers: vi.fn(async () => [])
}))
vi.mock('@/api/pms/asset/device', () => ({ getCurrentDeviceSummaries: vi.fn(async () => []), getDevice: vi.fn(async () => ({})), getDevicePage: vi.fn(async () => ({ list: [], total: 0 })) }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getStrDictOptions: vi.fn(() => []), getDictLabel: vi.fn(() => '') }))
vi.mock('@/config/axios', () => ({
  default: new Proxy({}, {
    get: (_, method) => () => { throw new Error(`Unstubbed Owner request: ${String(method)}`) }
  })
}))
vi.mock('@/config/axios/service', () => ({ service: vi.fn() }))
