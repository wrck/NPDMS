vi.mock('@/api/system/user', () => ({
  getSimpleUserList: vi.fn(),
  getUserPage: vi.fn(),
  getUserList: vi.fn()
}))
import { afterEach, expect, it, vi } from 'vitest'
import Core from './index.vue'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('./SystemUserDropdown.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return {
    default: defineComponent({
      props: { modelValue: [String, Number, Array], disabled: Boolean, multiple: Boolean },
      emits: ['update:modelValue', 'change', 'loaded'],
      setup:
        (props, { emit }) =>
        () =>
          h(
            'button',
            {
              onClick: () => {
                if (!props.disabled) {
                  emit('update:modelValue', '2115387679367467008')
                  emit('change', { id: '2115387679367467008', nickname: '长编号' })
                }
              }
            },
            '启用用户'
          )
    })
  }
})
vi.mock('./SystemUserDialogInput.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return {
    default: defineComponent({
      props: { modelValue: [String, Number, Array], multiple: Boolean },
      emits: ['update:modelValue', 'change'],
      setup:
        (_, { emit }) =>
        () =>
          h(
            'button',
            {
              onClick: () => {
                emit('update:modelValue', [3, 4])
                emit('change', [{ id: 3 }, { id: 4 }])
              }
            },
            '管理用户'
          )
    })
  }
})
vi.mock('./SystemUserTransferDialog.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return {
    default: defineComponent({
      emits: ['confirm'],
      setup: (_, { emit, expose }) => {
        expose({ open: (id: number, rows: any[]) => emit('confirm', id, rows) })
        return () => h('section', '流程多选')
      }
    })
  }
})
const apps: any[] = []
afterEach(() => apps.splice(0).forEach((app) => app.unmount()))
it('routes the ordinary compatibility entry through the kernel without coercing long identifiers', () => {
  const update = vi.fn(),
    change = vi.fn()
  const page = mount(Core as any, {
    modelValue: '1',
    'onUpdate:modelValue': update,
    onChange: change
  })
  apps.push(page.app)
  ;(page.root.children[0].props!.onClick as () => void)()
  expect(update).toHaveBeenCalledWith('2115387679367467008')
  expect(change).toHaveBeenCalledWith({ id: '2115387679367467008', nickname: '长编号' })
})
it('preserves management multi-selection events instead of using active candidates', async () => {
  const update = vi.fn()
  const page = mount(Core as any, {
    presentation: 'dialog',
    multiple: true,
    'onUpdate:modelValue': update
  })
  apps.push(page.app)
  await vi.dynamicImportSettled()
  expect(textOf(page.root)).toContain('管理用户')
  ;(page.root.children[0].props!.onClick as () => void)()
  expect(update).toHaveBeenCalledWith([3, 4])
})
it('retains the workflow open/confirm contract and its activity identifier', async () => {
  const confirm = vi.fn()
  const page = mount(Core as any, { presentation: 'transfer', onConfirm: confirm })
  apps.push(page.app)
  const opening = (page.vm as any).open(19, [{ id: 5, nickname: '用户' }])
  await vi.dynamicImportSettled()
  await opening
  expect(confirm).toHaveBeenCalledWith(19, [{ id: 5, nickname: '用户' }])
})
