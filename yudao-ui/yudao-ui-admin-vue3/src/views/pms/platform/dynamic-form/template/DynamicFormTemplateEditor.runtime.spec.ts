import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as DynamicFormApi from '@/api/pms/platform/dynamic-form'
import { useFormCreateDesigner } from '@/components/FormCreate'
import DynamicFormTemplateEditor from './DynamicFormTemplateEditor.vue'
import { mount, passthrough, textOf } from '../components/runtimeTestHarness'

vi.mock('@/api/pms/platform/dynamic-form', () => ({ getRevision: vi.fn(), patchRevision: vi.fn() }))
vi.mock('@/components/FormCreate', () => ({ useFormCreateDesigner: vi.fn() }))
vi.mock('../components/registerDynamicFormComponents', () => ({
  registerDynamicFormComponents: vi.fn()
}))
vi.mock('@vueuse/core', async () => {
  const { ref } = await import('vue')
  return { useMediaQuery: () => ref(false) }
})

const revision = (status: 'DRAFT' | 'PUBLISHED') =>
  ({
    revisionId: 9,
    templateId: 2,
    revisionNo: 4,
    status,
    formConfJson: {},
    formRulesJson: [],
    engineCode: 'FORM_CREATE_ELEMENT_PLUS',
    designerVersion: '3.4.0',
    rendererVersion: '3.2.38',
    revisionVersion: 5,
    allowedActions: status === 'DRAFT' ? ['PATCH_REVISION'] : []
  }) as any

describe('F-PLT-002 template revision editor', () => {
  beforeEach(() => vi.clearAllMocks())
  it('renders the full designer branch for a writable DRAFT and preview-only identity for PUBLISHED', async () => {
    let receivedConfig: Record<string, unknown> = {}
    const addComponent = vi.fn()
    const Designer = defineComponent({
      props: { config: { type: Object, required: true } },
      setup(props, { expose }) {
        receivedConfig = props.config
        expose({
          addComponent,
          appendMenuItem: vi.fn(),
          setOption: vi.fn(),
          setRule: vi.fn(),
          getOption: () => ({}),
          getRule: () => []
        })
        return () => h('div', 'full-designer')
      }
    })
    const FormCreate = defineComponent({ setup: () => () => h('div', 'preview-renderer') })
    vi.mocked(DynamicFormApi.getRevision).mockResolvedValueOnce(revision('DRAFT'))
    const draft = mount(
      DynamicFormTemplateEditor,
      { modelValue: true, revisionId: 9 },
      {
        ElDrawer: passthrough,
        ElAlert: passthrough,
        'fc-designer': Designer,
        'form-create': FormCreate
      }
    )
    await Promise.resolve()
    await nextTick()
    await Promise.resolve()
    await nextTick()
    expect(textOf(draft.root)).toContain('保存草稿')
    expect(textOf(draft.root)).toContain('高信任配置')
    expect(receivedConfig).toMatchObject({
      showControl: true,
      showEventForm: true,
      showValidateForm: true,
      showFormConfig: true,
      showDevice: true
    })
    expect(useFormCreateDesigner).toHaveBeenCalled()
    expect(addComponent).toHaveBeenCalledWith(expect.objectContaining({ name: 'PmsFileArtifact' }))
    draft.app.unmount()

    vi.mocked(DynamicFormApi.getRevision).mockResolvedValueOnce(revision('PUBLISHED'))
    const published = mount(
      DynamicFormTemplateEditor,
      { modelValue: true, revisionId: 9 },
      {
        ElDrawer: passthrough,
        ElAlert: passthrough,
        'fc-designer': Designer,
        'form-create': FormCreate
      }
    )
    await Promise.resolve()
    await nextTick()
    await Promise.resolve()
    await nextTick()
    expect(textOf(published.root)).toContain('preview-renderer')
    expect(textOf(published.root)).not.toContain('保存草稿')
    published.app.unmount()
  })
})
