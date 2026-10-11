import { expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import Form from './OutsourceRequestFormDialog.vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('../attachment/NativeAttachments.vue', () => ({ default: { render: () => null } }))
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/api/system/user', () => ({ __v_isRef: false, getUserPage: vi.fn() }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getStrDictOptions: () => [] }))
it('preserves required field validation through the shared dialog interface', async () => {
  const validate = vi.fn().mockRejectedValue(new Error('missing code'))
  const testForm = defineComponent({ setup(_, { expose, slots }) {
    expose({ validate }); return () => h('form', slots.default?.())
  } })
  const m = mount(Form, { modelValue: true, saving: false, form: { projectId: 7, code: '', name: 'survey', workContent: 'work', applicantUserId: 8, applyTime: '2026-10-10 02:00:00' } }, { ElForm: testForm, ...Object.fromEntries(['ElInput', 'PmsEntitySelect', 'ElCol', 'ElRow', 'ElOption', 'ElSelect', 'ElInputNumber', 'ElDatePicker', 'Editor', 'UploadFile'].map(name => [name, passthrough])) })
  try {
    await expect((m.vm as any).validate()).rejects.toThrow('missing code')
    expect(validate).toHaveBeenCalledOnce()
  } finally { m.app.unmount() }
})
