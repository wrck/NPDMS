import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as DynamicFormApi from '@/api/pms/platform/dynamic-form'
import TemplatePage from './template/index.vue'
import InstancePage from './instance/index.vue'
import {
  findByTestId,
  mount,
  passthrough,
  tableColumn,
  textOf
} from './components/runtimeTestHarness'

vi.mock('@/api/pms/platform/dynamic-form', () => ({
  getTemplatePage: vi.fn(),
  getInstancePage: vi.fn(),
  getTemplateSelection: vi.fn()
}))
vi.mock('./components/registerDynamicFormComponents', () => ({
  registerDynamicFormComponents: vi.fn()
}))
vi.mock('@vueuse/core', async () => {
  const { computed } = await import('vue')
  return { useMediaQuery: () => computed(() => globalThis.innerWidth <= 767) }
})
vi.mock('./template/DynamicFormTemplateEditor.vue', () => ({
  default: defineComponent({ setup: () => () => h('div') })
}))
vi.mock('./instance/DynamicFormInstanceForm.vue', () => ({
  default: defineComponent({ setup: () => () => h('div') })
}))

describe('F-PLT-002 responsive menu pages', () => {
  beforeEach(() => {
    vi.mocked(DynamicFormApi.getTemplatePage).mockResolvedValue({ list: [], total: 0 })
    vi.mocked(DynamicFormApi.getInstancePage).mockResolvedValue({ list: [], total: 0 })
  })

  it.each([320, 768, 1024, 1440])(
    'mounts template and instance loading/empty/action surfaces at %ipx',
    async (width) => {
      Object.defineProperty(globalThis, 'innerWidth', { configurable: true, value: width })
      const common = {
        ElTable: passthrough,
        ElTableColumn: tableColumn,
        ElInput: passthrough,
        ElSelect: passthrough,
        ElOption: passthrough
      }
      const templates = mount(TemplatePage, {}, common)
      const instances = mount(InstancePage, {}, common)
      await Promise.resolve()
      await nextTick()
      expect(textOf(templates.root)).toContain('新建模板')
      expect(textOf(instances.root)).toContain('新建实例')
      expect(textOf(instances.root)).toContain('模板停用仅阻止新建实例')
      expect(
        !!findByTestId(
          templates.root,
          width <= 767 ? 'template-mobile-list' : 'template-desktop-list'
        )
      ).toBe(true)
      expect(
        !!findByTestId(
          instances.root,
          width <= 767 ? 'instance-mobile-list' : 'instance-desktop-list'
        )
      ).toBe(true)
      templates.app.unmount()
      instances.app.unmount()
    }
  )
})
