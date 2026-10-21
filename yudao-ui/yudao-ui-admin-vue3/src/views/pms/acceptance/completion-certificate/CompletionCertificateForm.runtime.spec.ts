import { expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Form from './CompletionCertificateForm.vue'
import PmsEntitySelect from '@/components/PmsEntitySelect/index.vue'
const users = vi.hoisted(() => ({
  getUserPage: vi.fn(async () => { throw new Error('FORBIDDEN') }),
  getSimpleUserList: vi.fn(async () => [{ id: 1, nickname: '当前工程师' }, { id: 2, nickname: '另一工程师' }])
}))
vi.mock('@/api/system/user', () => users)
vi.mock('@/api/pms/project/projects', () => ({ getProjectPage: vi.fn() }))
vi.mock('@/components/FormCreate', () => ({ SignaturePad: { render: () => null } }))
it('opens the engineer picker through the enabled-user directory without requiring user administration', async () => {
  const view = mount(Form, { projectId: 41, projectName: '验收项目', disabled: false, printTemplates: [], devices: [],
    form: { projectId: 41, engineerUserId: 1, engineerName: '当前工程师' } },
  { PmsEntitySelect, ...Object.fromEntries(['ElInput', 'ElSelect', 'ElOption', 'ElRow', 'ElCol', 'ElDatePicker', 'ElRadio', 'ElRadioGroup', 'ElInputNumber'].map(name => [name, passthrough])) })
  try {
    for (let i = 0; i < 8; i++) { await nextTick(); await Promise.resolve() }
    expect(users.getSimpleUserList).toHaveBeenCalled()
    expect(users.getUserPage).not.toHaveBeenCalled()
    const result = await (view.vm as any).$.setupState.findEngineers({ nickname: '另一' })
    expect(result.list).toEqual([{ id: 2, nickname: '另一工程师' }])
  } finally { view.app.unmount() }
})
