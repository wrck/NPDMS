import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Wrapper from './FormCreateUserSelect.vue'
import { useApiSelect } from '@/components/FormCreate/src/components/useApiSelect'
import {
  mount,
  passthrough,
  textOf,
  type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const source = vi.hoisted(() => ({ schemaRequest: vi.fn() }))
vi.mock('@/api/system/user/selection', () => ({ systemUserSelection: source }))
vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
vi.mock('@/utils/auth', () => ({ getCurrentUserId: () => '2115387679367467008' }))
vi.mock('@/utils', () => ({ jsonParse: JSON.parse }))
const apps: any[] = []
const flush = async () => {
  for (let i = 0; i < 12; i++) await nextTick()
}
const flatten = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(flatten)]
const render = (props: Record<string, unknown> = {}) => {
  const update = vi.fn()
  const page = mount(
    useApiSelect({
      name: 'UserSelect',
      labelField: 'nickname',
      valueField: 'id',
      url: '/system/user/simple-list',
      userSelection: true
    }),
    { ...props, 'onUpdate:modelValue': update },
    {
      ElSelect: passthrough,
      ElOption: passthrough,
      ElCheckboxGroup: passthrough,
      ElCheckbox: passthrough,
      ElRadioGroup: passthrough,
      ElRadio: passthrough
    }
  )
  apps.push(page.app)
  return { ...page, update }
}
beforeEach(() => {
  source.schemaRequest.mockReset()
  source.schemaRequest.mockResolvedValue([{ id: '2115387679367467008', nickname: '用户甲' }])
})
afterEach(() => apps.splice(0).forEach((app) => app.unmount()))
it('keeps default current user and multi-value storage for existing system UserSelect schemas', async () => {
  const page = render({ defaultCurrentUser: true, multiple: true })
  await flush()
  expect(source.schemaRequest).toHaveBeenCalledWith({
    url: '/system/user/simple-list',
    method: 'GET'
  })
  expect(page.update).toHaveBeenCalledWith(['2115387679367467008'])
  expect(flatten(page.root).some((node) => node.props?.value === '2115387679367467008')).toBe(true)
})
it('retains custom scoped POST parameters and parse/return-name semantics', async () => {
  source.schemaRequest.mockResolvedValue({ entries: [{ id: 9, name: '旧姓名' }] })
  const page = render({
    url: '/api/v1/pms/scoped-users',
    method: 'POST',
    data: '{"projectId":5}',
    parseFunc: '(data)=>data.entries.map(item=>({label:item.name,value:item.name}))',
    returnType: 'name'
  })
  await flush()
  expect(source.schemaRequest).toHaveBeenCalledWith({
    url: '/api/v1/pms/scoped-users',
    method: 'POST',
    data: { projectId: 5 }
  })
  expect(flatten(page.root).some((node) => node.props?.value === '旧姓名')).toBe(true)
})
it('keeps radio name values without replacing the underlying schema semantics', async () => {
  const page = render({ selectType: 'radio', returnType: 'name' })
  await flush()
  expect(flatten(page.root).some((node) => node.props?.value === '用户甲')).toBe(true)
})
it('never fabricates user candidates when a checkbox schema receives an empty authorized result', async () => {
  source.schemaRequest.mockResolvedValue([])
  const page = render({ selectType: 'checkbox' })
  await flush()
  expect(textOf(page.root)).not.toContain('选项1')
  expect(flatten(page.root).filter((node) => node.props?.value != null)).toEqual([])
})

it('shows scoped-source failures and retries without inventing candidates or changing existing values', async () => {
  source.schemaRequest
    .mockRejectedValueOnce(new Error('403'))
    .mockResolvedValueOnce([{ id: 7, nickname: '恢复' }])
  const page = render({ modelValue: 7 })
  await flush()
  expect(textOf(page.root)).toContain('用户候选加载失败')
  expect(page.update).not.toHaveBeenCalled()
  const retry = flatten(page.root).find((node) => node.type === 'button')
  await (retry?.props?.onClick as () => Promise<void>)()
  await flush()
  expect(source.schemaRequest).toHaveBeenCalledTimes(2)
  expect(textOf(page.root)).not.toContain('用户候选加载失败')
})

it('forwards original form-create change events through the common kernel', async () => {
  const change = vi.fn()
  const page = mount(
    Wrapper,
    { onChange: change },
    { ElSelect: passthrough, ElOption: passthrough }
  )
  apps.push(page.app)
  await vi.dynamicImportSettled()
  await flush()
  const select = flatten(page.root).find((node) => typeof node.props?.onChange === 'function')
  expect(select).toBeDefined()
  ;(select!.props!.onChange as (value: any) => void)(7)
  expect(change).toHaveBeenCalledWith(7)
})

it('formats an available account for display without changing name-valued fields', async () => {
  source.schemaRequest.mockResolvedValue([{ id: 7, nickname: '张三', username: 'z007' }])
  const page = render({ returnType: 'name' })
  await flush()
  expect(flatten(page.root).some(node => node.props?.label === 'z007-张三' && node.props?.value === '张三')).toBe(true)
})
