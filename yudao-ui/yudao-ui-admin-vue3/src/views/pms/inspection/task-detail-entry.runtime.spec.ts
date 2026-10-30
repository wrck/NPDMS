import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { nextTick, reactive } from 'vue'
import Entry from './task-detail-entry.vue'
import {
  findByTestId,
  mount,
  textOf
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const state = vi.hoisted(() => ({
  route: { params: { taskId: '10' }, query: {} as Record<string, string> },
  canQuery: true,
  getTask: vi.fn()
}))
vi.mock('vue-router', () => ({ useRoute: () => state.route }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => state.canQuery }))
vi.mock('@/api/pms/inspection/tasks', () => ({ getTask: state.getTask }))
vi.mock('./task-detail/index.vue', async () => {
  const { h } = await import('vue')
  return { default: { setup: () => () => h('div', { 'data-testid': 'legacy-detail' }, '原任务详情') } }
})
vi.mock('./umc-task-detail/index.vue', async () => {
  const { h } = await import('vue')
  return { default: { setup: () => () => h('div', { 'data-testid': 'umc-detail' }, '巡检任务详情') } }
})

const apps: Array<{ unmount: () => void }> = []
const flush = async () => { for (let i = 0; i < 8; i++) await nextTick() }
const task = (providerCode?: string) => ({
  currentAttemptId: providerCode ? '20' : undefined,
  attempts: providerCode ? [{ id: '20', providerCode }] : [],
  selections: []
})
const render = async () => {
  const view = mount(Entry)
  apps.push(view.app)
  await flush()
  return view
}
beforeEach(() => {
  vi.clearAllMocks()
  state.canQuery = true
  state.route = reactive({ params: { taskId: '10' }, query: {} })
  state.getTask.mockResolvedValue(task('UMC'))
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

it('uses the persisted UMC provider and ignores obsolete engine query parameters', async () => {
  state.route.query.engine = 'platform'
  const view = await render()
  expect(findByTestId(view.root, 'umc-detail')).toBeDefined()
  expect(findByTestId(view.root, 'legacy-detail')).toBeUndefined()
  expect(textOf(view.root)).not.toMatch(/切换|进入UMC|查看原平台/)
})

it('preserves the original page for a platform attempt even when a URL requests UMC', async () => {
  state.route.query.engine = 'umc'
  state.getTask.mockResolvedValue(task('PLATFORM'))
  const view = await render()
  expect(findByTestId(view.root, 'legacy-detail')).toBeDefined()
  expect(findByTestId(view.root, 'umc-detail')).toBeUndefined()
})

it('keeps an existing rule preparation on its original workflow', async () => {
  state.getTask.mockResolvedValue({ ...task(), selections: [{ deviceId: '30', ruleRevisionId: '40' }] })
  const view = await render()
  expect(findByTestId(view.root, 'legacy-detail')).toBeDefined()
})

it('opens new unconfigured preparations in the copied UMC workflow', async () => {
  state.getTask.mockResolvedValue(task())
  const view = await render()
  expect(findByTestId(view.root, 'umc-detail')).toBeDefined()
})

it('does not guess a provider when task data is unavailable or its current attempt is missing', async () => {
  state.getTask.mockRejectedValueOnce(new Error('unavailable'))
  const view = await render()
  expect(textOf(view.root)).toContain('重新加载')
  expect(findByTestId(view.root, 'legacy-detail')).toBeUndefined()
  expect(findByTestId(view.root, 'umc-detail')).toBeUndefined()
  state.getTask.mockResolvedValueOnce({ ...task(), currentAttemptId: 'missing' })
  await (view.vm as any).$.setupState.load()
  expect(findByTestId(view.root, 'legacy-detail')).toBeUndefined()
  expect(findByTestId(view.root, 'umc-detail')).toBeUndefined()
})

it('preserves issue-only access without adding a whole-task permission requirement', async () => {
  state.canQuery = false
  state.route.query.issueId = '50'
  const view = await render()
  expect(state.getTask).not.toHaveBeenCalled()
  expect(findByTestId(view.root, 'legacy-detail')).toBeDefined()
})

it('retains the existing issue fallback when whole-task scope is denied', async () => {
  state.route.query.issueId = '50'
  state.getTask.mockRejectedValueOnce(new Error('forbidden'))
  const view = await render()
  expect(findByTestId(view.root, 'legacy-detail')).toBeDefined()
})

it('ignores a late source response after navigating to another task', async () => {
  let finishOld!: (value: ReturnType<typeof task>) => void
  state.getTask.mockReturnValueOnce(new Promise(resolve => { finishOld = resolve }))
  const view = await render()
  state.route.params.taskId = '11'
  await flush()
  expect(findByTestId(view.root, 'umc-detail')).toBeDefined()
  finishOld(task('PLATFORM'))
  await flush()
  expect(findByTestId(view.root, 'umc-detail')).toBeDefined()
  expect(findByTestId(view.root, 'legacy-detail')).toBeUndefined()
})
