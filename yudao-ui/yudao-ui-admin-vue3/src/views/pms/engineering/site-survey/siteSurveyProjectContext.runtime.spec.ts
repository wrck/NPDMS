import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import SurveyPage from './index.vue'

const api = vi.hoisted(() => ({ getSiteSurveyPage: vi.fn(), getSiteSurvey: vi.fn(), getDefaultFormSchema: vi.fn(), deleteSiteSurvey: vi.fn() }))
const message = vi.hoisted(() => ({ warning: vi.fn(), success: vi.fn(), confirm: vi.fn(), delConfirm: vi.fn() }))
vi.mock('@/api/pms/engineering/site-survey', () => api)
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/api/system/user', () => ({ getSimpleUserList: async () => [] }))
vi.mock('@/api/pms/platform/dynamic-form', () => ({}))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getIntDictOptions: () => [] }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn() }) }))
vi.mock('./SiteSurveyDynamicForm.vue', () => ({ default: { render: () => null } }))

const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 4; i++) { await nextTick(); await Promise.resolve() } }
const render = (projectId?: number) => {
  const components = Object.fromEntries(['ElTable', 'ElRow', 'ElCol', 'ElInput', 'ElSelect', 'ElOption', 'ElDatePicker', 'ElCheckbox', 'ElPagination', 'PmsEntitySelect', 'PmsLocationSelector', 'Editor', 'DictTag'].map(name => [name, passthrough]))
  const mounted = mount(SurveyPage, { projectId }, { ...components, ElTableColumn: tableColumn })
  apps.push(mounted.app)
  return (mounted.vm as any).$.setupState
}
beforeEach(() => {
  vi.clearAllMocks()
  api.getSiteSurveyPage.mockResolvedValue({ list: [], total: 0 })
  api.getDefaultFormSchema.mockResolvedValue({ revisionId: 9, revisionVersion: 1, formRulesJson: [] })
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

describe('shared project and standalone survey context', () => {
  it('pins list and new-form project, even if the filter state is changed', async () => {
    const state = render(7)
    await flush()
    expect(api.getSiteSurveyPage).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: 7 }))
    state.query.projectId = 8
    await state.load()
    expect(api.getSiteSurveyPage).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: 7 }))
    await state.openForm()
    expect(state.form.projectId).toBe(7)
    expect(state.form.formRevisionId).toBe(9)
  })
  it('does not open a shortcut record from another project', async () => {
    const state = render(7)
    api.getSiteSurvey.mockResolvedValue({ id: 12, projectId: 8, status: 0 })
    await state.openForm({ id: 12 })
    expect(state.formVisible).toBe(false)
    expect(message.warning).toHaveBeenCalledWith('该工勘不属于当前项目')
  })
  it('retains standalone project selection and blocks linked-source deletion', async () => {
    const state = render()
    state.query.projectId = 8
    await state.load()
    expect(api.getSiteSurveyPage).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: 8 }))
    await state.remove({ id: 12, outsourceRequestId: 99 })
    expect(message.delConfirm).not.toHaveBeenCalled()
    expect(api.deleteSiteSurvey).not.toHaveBeenCalled()
  })
})
