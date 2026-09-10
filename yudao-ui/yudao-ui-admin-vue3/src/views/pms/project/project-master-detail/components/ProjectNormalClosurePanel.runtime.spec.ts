import { createRenderer, defineComponent, h, nextTick, reactive } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ProjectNormalClosurePanel from './ProjectNormalClosurePanel.vue'
import * as ClosureApi from '@/api/pms/project/normal-closure'
import type {
  ClosureApplication,
  ClosureOverview,
  ClosureSnapshot
} from '@/api/pms/project/normal-closure'
import {
  button as elementButton,
  passthrough,
  textOf,
  type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const controls = vi.hoisted(() => ({
  confirm: vi.fn(),
  push: vi.fn(),
  route: { fullPath: '/projects/11' }
}))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ confirm: controls.confirm }) }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: controls.push }),
  useRoute: () => controls.route
}))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => false }))
vi.mock('@/api/pms/project/normal-closure', () => ({
  getNormalClosure: vi.fn(),
  checkNormalClosure: vi.fn(),
  submitNormalClosure: vi.fn()
}))
// Fail if the Panel ever replaces the BPM entry with direct project mutations.
vi.mock('@/api/pms/project/projects', () => ({}))
const PROJECT = '2099999999999999999'
const SNAPSHOT = '2099999999999999998'
const snapshot = (patch: Partial<ClosureSnapshot> = {}): ClosureSnapshot => ({
  id: SNAPSHOT,
  projectVersion: 4,
  treeVersion: 8,
  fromStage: 'S4',
  passed: true,
  checkedAt: '2026-09-10 10:00:00',
  checkedBy: '2099999999999999997',
  sourceDigest: 'digest-1',
  ...patch
})
const application = (patch: Partial<ClosureApplication> = {}): ClosureApplication => ({
  id: '2099999999999999996',
  projectId: PROJECT,
  snapshotId: SNAPSHOT,
  status: 'IN_REVIEW',
  processInstanceId: '2099999999999999995',
  processDefinitionId: 'normal:1:workflow',
  submittedAt: '2026-09-10 10:01:00',
  serviceManagerUserId: '17',
  reviewerUserId: '18',
  ...patch
})
const overview = (patch: Partial<ClosureOverview> = {}): ClosureOverview => ({
  projectId: PROJECT,
  version: 4,
  treeVersion: 8,
  currentStage: 'S4',
  lifecycleStatus: 'ACTIVE',
  policyAvailable: true,
  checks: [{ code: 'ALL_TASKS_DONE', passed: true, reason: '全部任务已完成' }],
  latestSnapshot: snapshot(),
  allowedActions: ['CHECK', 'SUBMIT'],
  ...patch
})
const deferred = <T>() => {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
const tick = async () => {
  for (let i = 0; i < 15; i++) {
    await Promise.resolve()
    await nextTick()
  }
}
const visit = (node: TestNode, predicate: (node: TestNode) => boolean): TestNode | undefined =>
  predicate(node) ? node : node.children.map((child) => visit(child, predicate)).find(Boolean)
const button = (root: TestNode, label: string) =>
  visit(root, (node) => node.type === 'button' && textOf(node) === label)
const handler = (root: TestNode, label: string) => {
  const node = button(root, label)
  expect(node, label).toBeTruthy()
  return node!.props!.onClick as () => Promise<unknown>
}
const click = async (root: TestNode, label: string) => {
  await handler(root, label)()
  await tick()
}
// The shared append-only harness cannot remove Vue fragments correctly. Keep DOM-like
// insertBefore/move semantics here so unavailable GET assertions observe actual unmounts.
const renderer = createRenderer<TestNode, TestNode>({
  patchProp: (node, key, _old, value) => {
    ;(node.props ||= {})[key] = value
  },
  insert: (child, parent, anchor) => {
    if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1)
    child.parent = parent
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, child)
  },
  remove: (child) => {
    if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1)
    child.parent = undefined
  },
  createElement: (type) => ({ type, children: [] }),
  createText: (text) => ({ type: '#text', text, children: [] }),
  createComment: () => ({ type: '#comment', children: [] }),
  setText: (node, text) => {
    node.text = text
  },
  setElementText: (node, text) => {
    node.text = text
    node.children = []
  },
  parentNode: (node) => node.parent || null,
  nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] || null,
  setScopeId: () => undefined,
  insertStaticContent: (content, parent, anchor) => {
    const node: TestNode = { type: '#static', text: content, children: [], parent }
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
    return [node, node]
  }
})
const apps: { unmount: () => void }[] = []
const setup = async (patch: { projectId?: string; readonly?: boolean } = {}) => {
  const state = reactive({ projectId: PROJECT, readonly: false, ...patch })
  const updated = vi.fn()
  const root: TestNode = { type: 'root', children: [] }
  const app = renderer.createApp(
    defineComponent({
      setup: () => () => h(ProjectNormalClosurePanel, { ...state, onUpdated: updated })
    })
  )
  for (const name of [
    'ContentWrap',
    'ElAlert',
    'ElTag',
    'ElSkeleton',
    'ElDescriptions',
    'ElDescriptionsItem'
  ])
    app.component(name, passthrough)
  app.component('ElButton', elementButton)
  app.mount(root)
  apps.push(app)
  await tick()
  return { root, app, state, updated }
}
beforeEach(() => {
  vi.resetAllMocks()
  controls.route = reactive({ fullPath: `/projects/${PROJECT}` })
  controls.confirm.mockResolvedValue(undefined)
  vi.mocked(ClosureApi.getNormalClosure).mockImplementation(async (id) =>
    overview({ projectId: id })
  )
  vi.mocked(ClosureApi.checkNormalClosure).mockResolvedValue(snapshot())
  vi.mocked(ClosureApi.submitNormalClosure).mockResolvedValue(application())
})
afterEach(() => apps.splice(0).forEach((app) => app.unmount()))

describe('CLO-01 / CLO-02 ProjectNormalClosurePanel runtime', () => {
  it('fails closed without frozen policy even if actions and a passing snapshot are returned', async () => {
    vi.mocked(ClosureApi.getNormalClosure).mockResolvedValue(overview({ policyAvailable: false }))
    const page = await setup()
    expect(textOf(page.root)).toContain('未冻结可用的正常闭环规则')
    expect(button(page.root, '提交闭环审批')).toBeUndefined()
    expect(button(page.root, '校验闭环条件')).toBeUndefined()
    expect(ClosureApi.submitNormalClosure).not.toHaveBeenCalled()
  })

  it('checks then reads the current passing snapshot before submitting lossless string IDs', async () => {
    vi.mocked(ClosureApi.getNormalClosure)
      .mockResolvedValueOnce(
        overview({
          latestSnapshot: undefined,
          allowedActions: ['CHECK']
        })
      )
      .mockResolvedValueOnce(overview())
      .mockResolvedValueOnce(
        overview({
          latestApplication: application(),
          allowedActions: []
        })
      )
    const page = await setup()
    expect(button(page.root, '提交闭环审批')).toBeUndefined()
    await click(page.root, '校验闭环条件')
    expect(ClosureApi.checkNormalClosure).toHaveBeenCalledWith(
      expect.objectContaining({ projectId: PROJECT, version: 4, treeVersion: 8 }),
      expect.any(String)
    )
    await click(page.root, '提交闭环审批')
    expect(ClosureApi.submitNormalClosure).toHaveBeenCalledWith(
      expect.objectContaining({ projectId: PROJECT, version: 4, treeVersion: 8 }),
      SNAPSHOT,
      expect.any(String)
    )
    expect(textOf(page.root)).toContain('审批中')
    expect(page.updated).toHaveBeenCalledTimes(2)
    await click(page.root, '查看审批流程与待办')
    expect(controls.push).toHaveBeenCalledWith({
      name: 'BpmProcessInstanceDetail',
      query: { id: '2099999999999999995' }
    })
  })

  it.each(['IN_REVIEW', 'APPROVED'] as const)(
    'renders %s independently of real project lifecycle, without direct approve',
    async (status) => {
      const value = overview({ latestApplication: application({ status }) })
      vi.mocked(ClosureApi.getNormalClosure).mockResolvedValue(value)
      const page = await setup()
      expect(textOf(page.root)).toContain(status === 'APPROVED' ? '已批准' : '审批中')
      expect(textOf(page.root)).toContain('进行中')
      expect(textOf(page.root)).toContain('S4')
      expect(button(page.root, '提交闭环审批')).toBeUndefined()
      expect(button(page.root, '批准')).toBeUndefined()
      await click(page.root, '查看审批流程与待办')
      expect(value.lifecycleStatus).toBe('ACTIVE')
      expect(value.currentStage).toBe('S4')
      expect(ClosureApi.submitNormalClosure).not.toHaveBeenCalled()
      expect(page.updated).not.toHaveBeenCalled()
    }
  )

  it.each(['readonly', 'actions', 'lifecycle', 'policy'] as const)(
    'revalidates captured CHECK and SUBMIT handlers after %s revocation',
    async (change) => {
      const value = reactive(overview())
      vi.mocked(ClosureApi.getNormalClosure).mockResolvedValue(value)
      const page = await setup()
      const check = handler(page.root, '校验闭环条件'),
        submit = handler(page.root, '提交闭环审批')
      if (change === 'readonly') page.state.readonly = true
      if (change === 'actions') value.allowedActions = []
      if (change === 'lifecycle') value.lifecycleStatus = 'NORMAL_CLOSED'
      if (change === 'policy') value.policyAvailable = false
      await tick()
      await check()
      await submit()
      expect(ClosureApi.checkNormalClosure).not.toHaveBeenCalled()
      expect(ClosureApi.submitNormalClosure).not.toHaveBeenCalled()
      expect(controls.confirm).not.toHaveBeenCalled()
    }
  )

  it.each([{ passed: false }, { projectVersion: 3 }, { treeVersion: 7 }, { fromStage: 'S3' }])(
    'does not submit an invalid snapshot %j',
    async (patch) => {
      vi.mocked(ClosureApi.getNormalClosure).mockResolvedValue(
        overview({ latestSnapshot: snapshot(patch) })
      )
      const page = await setup()
      expect(button(page.root, '提交闭环审批')).toBeUndefined()
      expect(ClosureApi.submitNormalClosure).not.toHaveBeenCalled()
    }
  )

  it.each([
    'project',
    'route',
    'snapshot',
    'version',
    'treeVersion',
    'digest',
    'readonly',
    'actions',
    'lifecycle'
  ] as const)('discards a confirmation when %s changes', async (change) => {
    const value = reactive(overview())
    vi.mocked(ClosureApi.getNormalClosure).mockImplementation(async (id) =>
      id === PROJECT ? value : overview({ projectId: id })
    )
    const page = await setup(),
      confirmation = deferred<void>()
    controls.confirm.mockReturnValue(confirmation.promise)
    const pending = handler(page.root, '提交闭环审批')()
    if (change === 'project') page.state.projectId = '12'
    if (change === 'route') controls.route.fullPath = '/projects/other'
    if (change === 'snapshot') value.latestSnapshot = snapshot({ id: '99' })
    if (change === 'version') {
      value.version++
      value.latestSnapshot!.projectVersion++
    }
    if (change === 'treeVersion') {
      value.treeVersion++
      value.latestSnapshot!.treeVersion++
    }
    if (change === 'digest') value.latestSnapshot!.sourceDigest = 'new-facts'
    if (change === 'readonly') page.state.readonly = true
    if (change === 'actions') value.allowedActions = []
    if (change === 'lifecycle') value.lifecycleStatus = 'NORMAL_CLOSED'
    await tick()
    confirmation.resolve()
    await pending
    expect(ClosureApi.submitNormalClosure).not.toHaveBeenCalled()
    expect(page.updated).not.toHaveBeenCalled()
  })

  it('serializes confirmation and treats cancel as a no-op', async () => {
    const page = await setup(),
      confirmation = deferred<void>()
    controls.confirm.mockReturnValue(confirmation.promise)
    const submit = handler(page.root, '提交闭环审批'),
      check = handler(page.root, '校验闭环条件')
    const pending = submit()
    await submit()
    await check()
    expect(controls.confirm).toHaveBeenCalledTimes(1)
    confirmation.reject('cancel')
    await pending
    await tick()
    expect(ClosureApi.submitNormalClosure).not.toHaveBeenCalled()
    expect(ClosureApi.checkNormalClosure).not.toHaveBeenCalled()
    expect(page.updated).not.toHaveBeenCalled()
    expect(button(page.root, '提交闭环审批')).toBeTruthy()
  })

  it.each(['CHECK', 'SUBMIT'] as const)(
    'preserves history after failed %s, requires refresh and replays the same failed intent key',
    async (action) => {
      const value = overview(),
        before = JSON.stringify(value)
      vi.mocked(ClosureApi.getNormalClosure).mockResolvedValue(value)
      const page = await setup()
      const api =
        action === 'CHECK'
          ? vi.mocked(ClosureApi.checkNormalClosure)
          : vi.mocked(ClosureApi.submitNormalClosure)
      api.mockRejectedValue(new Error('response lost'))
      const run = handler(page.root, action === 'CHECK' ? '校验闭环条件' : '提交闭环审批')
      const submit = handler(page.root, '提交闭环审批')
      await run()
      await tick()
      expect(JSON.stringify(value)).toBe(before)
      expect(textOf(page.root)).toContain('全部任务已完成')
      expect(textOf(page.root)).toContain('刷新')
      await run()
      await submit()
      expect(api).toHaveBeenCalledTimes(1)
      expect(button(page.root, '提交闭环审批')).toBeUndefined()
      await click(page.root, '刷新')
      await run()
      expect(api).toHaveBeenCalledTimes(2)
      expect(api.mock.calls[1]).toEqual(api.mock.calls[0])
      expect(page.updated).not.toHaveBeenCalled()
    }
  )

  it('retains a successful submit key for the same payload and changes it for a new tree version', async () => {
    const page = await setup()
    await click(page.root, '提交闭环审批')
    await click(page.root, '提交闭环审批')
    const calls = vi.mocked(ClosureApi.submitNormalClosure).mock.calls
    expect(calls[1]).toEqual(calls[0])
    vi.mocked(ClosureApi.getNormalClosure).mockResolvedValue(
      overview({
        treeVersion: 9,
        latestSnapshot: snapshot({ treeVersion: 9 })
      })
    )
    await click(page.root, '刷新')
    await click(page.root, '提交闭环审批')
    expect(calls[2][2]).not.toBe(calls[0][2])
  })

  it.each(['reject', 'missing', 'wrong-project'] as const)(
    'clears old conditions and workflow data on GET %s',
    async (failure) => {
      vi.mocked(ClosureApi.getNormalClosure).mockResolvedValueOnce(
        overview({ latestApplication: application() })
      )
      const page = await setup()
      expect(button(page.root, '查看审批流程与待办')).toBeTruthy()
      if (failure === 'reject')
        vi.mocked(ClosureApi.getNormalClosure).mockRejectedValue(new Error('403'))
      else
        vi.mocked(ClosureApi.getNormalClosure).mockResolvedValue(
          failure === 'missing' ? (undefined as any) : overview({ projectId: '12' })
        )
      await click(page.root, '刷新')
      expect(textOf(page.root)).toContain('无法读取闭环条件')
      expect(textOf(page.root)).not.toContain('全部任务已完成')
      expect(button(page.root, '查看审批流程与待办')).toBeUndefined()
      expect(button(page.root, '提交闭环审批')).toBeUndefined()
      expect(page.updated).not.toHaveBeenCalled()
    }
  )

  it('blocks captured commands while GET is pending and does not use POST evidence as a fresh overview', async () => {
    const page = await setup(),
      fresh = deferred<ClosureOverview>()
    const submit = handler(page.root, '提交闭环审批'),
      check = handler(page.root, '校验闭环条件')
    vi.mocked(ClosureApi.getNormalClosure).mockReturnValue(fresh.promise)
    const pending = check()
    await tick()
    await submit()
    await check()
    expect(ClosureApi.submitNormalClosure).not.toHaveBeenCalled()
    expect(ClosureApi.checkNormalClosure).toHaveBeenCalledTimes(1)
    fresh.reject(new Error('unavailable'))
    await pending
    expect(page.updated).not.toHaveBeenCalled()
    expect(textOf(page.root)).toContain('无法读取闭环条件')
  })

  it('ignores an old GET across A → B → A', async () => {
    const old = deferred<ClosureOverview>()
    vi.mocked(ClosureApi.getNormalClosure).mockReturnValueOnce(old.promise)
    const page = await setup()
    page.state.projectId = '12'
    await tick()
    page.state.projectId = PROJECT
    await tick()
    old.resolve(overview({ checks: [{ code: 'OBSOLETE', passed: false }] }))
    await tick()
    expect(textOf(page.root)).not.toContain('OBSOLETE')
    expect(button(page.root, '提交闭环审批')).toBeTruthy()
  })

  it.each(['CHECK', 'SUBMIT'] as const)(
    'ignores late %s callbacks across project changes without clearing the new operation busy flag',
    async (action) => {
      const page = await setup(),
        old = deferred<any>(),
        fresh = deferred<any>()
      const api =
        action === 'CHECK'
          ? vi.mocked(ClosureApi.checkNormalClosure)
          : vi.mocked(ClosureApi.submitNormalClosure)
      api.mockReturnValueOnce(old.promise).mockReturnValueOnce(fresh.promise)
      const label = action === 'CHECK' ? '校验闭环条件' : '提交闭环审批'
      const first = handler(page.root, label)()
      await tick()
      page.state.projectId = '12'
      await tick()
      const second = handler(page.root, label)()
      await tick()
      old.reject(new Error('late failure'))
      await first
      await tick()
      expect(button(page.root, '刷新')!.props!.disabled).toBe(true)
      expect(textOf(page.root)).not.toContain('未成功')
      expect(textOf(page.root)).not.toContain('结果未确认')
      expect(ClosureApi.getNormalClosure).toHaveBeenCalledTimes(2)
      expect(page.updated).not.toHaveBeenCalled()
      fresh.resolve(action === 'CHECK' ? snapshot() : application())
      await second
      await tick()
      expect(page.updated).toHaveBeenCalledTimes(1)
      expect(button(page.root, '刷新')!.props!.disabled).toBe(false)
    }
  )

  it.each(['route', 'unmount', 'refresh-project'] as const)(
    'does not emit or refresh a replacement page after %s during successful submit',
    async (change) => {
      const page = await setup(),
        response = deferred<ClosureApplication>(),
        read = deferred<ClosureOverview>()
      vi.mocked(ClosureApi.submitNormalClosure).mockReturnValue(response.promise)
      const pending = handler(page.root, '提交闭环审批')()
      await tick()
      if (change === 'refresh-project') {
        vi.mocked(ClosureApi.getNormalClosure).mockReturnValueOnce(read.promise)
        response.resolve(application())
        await tick()
        page.state.projectId = '12'
        await tick()
        read.resolve(overview())
      } else {
        if (change === 'route') controls.route.fullPath = '/projects/other'
        else page.app.unmount()
        await tick()
        response.resolve(application())
      }
      await pending
      await tick()
      expect(page.updated).not.toHaveBeenCalled()
      expect(ClosureApi.getNormalClosure).toHaveBeenCalledTimes(
        change === 'unmount' ? 1 : change === 'route' ? 2 : 3
      )
    }
  )
})
