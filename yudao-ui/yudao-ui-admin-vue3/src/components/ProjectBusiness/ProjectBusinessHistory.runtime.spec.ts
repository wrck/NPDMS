import { defineComponent, h, nextTick } from 'vue'
import { expect, it, vi } from 'vitest'
import History from './ProjectBusinessHistory.vue'
import { mount, textOf, passthrough, tableColumn, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn(), onBeforeRouteUpdate: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ confirm: vi.fn(), warning: vi.fn() }) }))
const input = defineComponent({ props: ['modelValue'], emits: ['update:modelValue'], setup: (props, { emit }) => () => h('input', { value: props.modelValue, onChange: (value: unknown) => emit('update:modelValue', value) }) })
const form = defineComponent({ setup: (_, { expose, slots }) => { expose({ validate: async () => true }); return () => h('form', slots.default?.()) } })
const all = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(all)]
const flush = async () => { for (let i = 0; i < 12; i++) { await Promise.resolve(); await nextTick() } }
it('keeps edited body values when revision saving changes the busy state', async () => {
  const execute = vi.fn(async () => undefined)
  const mounted = mount(History, {
    api: { base: '/test', revisions: async () => [{ ref: { entityId: 1, revisionId: 11 }, revisionNo: 1, version: 0, state: 'DRAFT' }],
      revisionValues: async () => ({ title: { readable: true, value: 'Original' } }),
      revisionForm: async () => ({ extensions: { fields: {}, version: 0 }, definitions: [] }) },
    current: { ref: { entityId: 1 }, concurrencyBasis: 0 },
    fields: [{ code: 'title', name: 'Title', type: 'TEXT', required: true, readable: true, writable: true }],
    actions: [{ code: 'revision-save', executable: true }], busy: false, execute
  }, { ElInput: input, ElForm: form, ElTable: passthrough, ElTableColumn: tableColumn, ElSelect: passthrough, ElOption: passthrough })
  try {
    await flush()
    const title = all(mounted.root).find(node => node.type === 'input' && node.props?.value === 'Original')!
    expect(title).toBeDefined()
    ;(title.props!.onChange as Function)('Revised')
    await nextTick()
    const save = all(mounted.root).find(node => node.type === 'button' && textOf(node) === '保存修订')!
    await (save.props!.onClick as Function)()
    await flush()
    expect(execute).toHaveBeenCalledWith('revision-save', { revisionId: 11, revisionVersion: 0, values: { title: 'Revised' } })
  } finally { mounted.app.unmount() }
})
