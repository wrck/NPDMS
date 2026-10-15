import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { mount, passthrough, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import type { Definition } from '@/api/pms/integration'
import ProductCatalogSetup from './ProductCatalogSetup.vue'

const api = vi.hoisted(() => ({ getDppmsProductTemplate: vi.fn(), configureDppmsProductDefinition: vi.fn() }))
const message = vi.hoisted(() => ({ error: vi.fn(), success: vi.fn() }))
vi.mock('@/api/pms/integration', () => api)
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))
const apps: Array<{ unmount: () => void }> = []
const find = (node: TestNode, test: (node: TestNode) => boolean): TestNode | undefined =>
  test(node) ? node : node.children.map(child => find(child, test)).find(Boolean)
const render = () => {
  const definition = { connectionId: '20001', sources: [{ parameters: {
    productCode0: 'P001', productCode1: 'P002', migrationDefinitionRevisionId: 'old-revision'
  } }] } as unknown as Definition
  const updated = vi.fn()
  const view = mount(ProductCatalogSetup, { modelValue: definition, 'onUpdate:modelValue': updated }, { ElInput: passthrough })
  apps.push(view.app)
  return { ...view, updated }
}
beforeEach(() => { vi.clearAllMocks(); api.getDppmsProductTemplate.mockResolvedValue({ connectionId: '20001', sources: [] }) })
afterEach(() => apps.splice(0).forEach(app => app.unmount()))
it('requests the template directly without publishing a property definition or retaining its revision', async () => {
  const view = render()
  expect(textOf(view.root)).not.toContain('目录属性定义')
  const button = find(view.root, node => node.type === 'button')!
  await (button.props!.onClick as () => Promise<void>)()
  await nextTick()
  expect(api.getDppmsProductTemplate).toHaveBeenCalledTimes(1)
  expect(api.getDppmsProductTemplate).toHaveBeenCalledWith({ connectionId: '20001', productCodes: ['P001', 'P002'] })
  expect(api.configureDppmsProductDefinition).not.toHaveBeenCalled()
  expect(view.updated).toHaveBeenCalledWith({ connectionId: '20001', sources: [] })
})
it('rejects empty or duplicate product codes before any API request', async () => {
  const view = render()
  const input = find(view.root, node => typeof node.props?.['onUpdate:modelValue'] === 'function')!
  const update = input.props!['onUpdate:modelValue'] as (value: string) => void
  const button = find(view.root, node => node.type === 'button')!
  update('')
  await (button.props!.onClick as () => Promise<void>)()
  update('P001\nP001')
  await (button.props!.onClick as () => Promise<void>)()
  expect(message.error).toHaveBeenCalledTimes(2)
  expect(api.getDppmsProductTemplate).not.toHaveBeenCalled()
  expect(api.configureDppmsProductDefinition).not.toHaveBeenCalled()
})
