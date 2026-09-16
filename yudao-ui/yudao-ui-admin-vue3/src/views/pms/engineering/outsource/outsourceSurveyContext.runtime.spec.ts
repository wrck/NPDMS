import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Page from './index.vue'

const mocks = vi.hoisted(() => ({
  route: { query: {} as Record<string, unknown> }, push: vi.fn(),
  getSurvey: vi.fn(), resolve: vi.fn(), create: vi.fn(), remove: vi.fn(),
  message: { success: vi.fn(), warning: vi.fn(), confirm: vi.fn(), delConfirm: vi.fn() }
}))
vi.mock('vue-router', () => ({ useRoute: () => mocks.route, useRouter: () => ({ push: mocks.push }) }))
vi.mock('@/api/pms/engineering/site-survey/entity', () => ({ getSiteSurvey: mocks.getSurvey }))
vi.mock('@/views/pms/delivery-business/site-survey/siteSurveyExecutionShortcut', () => ({ resolveSurveyExecution: mocks.resolve }))
vi.mock('@/api/pms/engineering/outsource', () => ({ getOutsourceRequestPage: async () => ({ list: [], total: 0 }), createOutsourceRequest: mocks.create, deleteOutsourceRequest: mocks.remove }))
vi.mock('@/api/pms/project/project', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/api/system/user', () => ({ __v_isRef: false, getUserPage: vi.fn() }))
vi.mock('@/utils/formatTime', () => ({ formatDate: () => '2026-09-14 12:00:00', dateFormatter: vi.fn() }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getStrDictOptions: () => [], getIntDictOptions: () => [] }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => mocks.message }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: 8 } }) }))
vi.mock('@/components/ProjectTag/index.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/UserTag/index.vue', () => ({ default: { render: () => null } }))
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 12; i++) { await nextTick(); await Promise.resolve() } }
const render = () => {
  const page = ref<any>()
  const mounted = mount(defineComponent({ setup: () => () => h(Page, { ref: page }) }), {}, { ElTableColumn: tableColumn,
    ...Object.fromEntries(['ElRow', 'ElCol', 'ElInput', 'ElSelect', 'ElOption', 'ElDatePicker', 'ElTable', 'PmsEntitySelect',
      'DictTag', 'ElInputNumber', 'Editor', 'UploadFile', 'ElDescriptions', 'ElDescriptionsItem', 'ElRadio', 'ElRadioGroup']
      .map(name => [name, passthrough])) })
  apps.push(mounted.app)
  return page.value.$.setupState
}
const selection = { task: { projectId: 9, taskId: '9007199254740994', executionId: '9007199254740995', executionVersion: 2 } }
beforeEach(() => {
  vi.clearAllMocks()
  mocks.route.query = { siteSurveyId: '9007199254740993', surveyTaskId: selection.task.taskId, surveyExecutionId: selection.task.executionId }
  mocks.getSurvey.mockResolvedValue({ id: '9007199254740993', projectId: 9, status: 0, outsourceRequired: true, name: '工勘' })
  mocks.resolve.mockResolvedValue(selection)
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))
it('opens the original application form with exact survey/round context, saves a draft and returns with that identity', async () => {
  const state = render(); await flush()
  expect(mocks.getSurvey).toHaveBeenCalledWith('9007199254740993')
  expect(state.formVisible).toBe(true)
  expect(state.form.siteSurveyExecution).toEqual(selection)
  expect(state.form.triggerRefId).toBe('9007199254740993')
  state.formRef = { validate: vi.fn() }
  await state.save()
  expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({ siteSurveyExecution: selection, triggerSource: 'SITE_SURVEY' }))
  expect(mocks.push).toHaveBeenCalledWith(expect.objectContaining({ query: {
    surveyId: '9007199254740993', surveyTaskId: selection.task.taskId, surveyExecutionId: selection.task.executionId
  } }))
})
it('refuses to open a new form when the source execution changed', async () => {
  mocks.resolve.mockRejectedValueOnce(new Error('new round'))
  const state = render(); await flush()
  expect(state.formVisible).toBe(false)
  expect(mocks.create).not.toHaveBeenCalled()
  expect(mocks.message.warning).toHaveBeenCalled()
})
it('clears a previous node selection when opening an unrelated manual application', async () => {
  const state = render(); await flush(); state.openCreate()
  expect(state.form.siteSurveyExecution).toBeUndefined()
  expect(state.form.triggerRefId).toBeUndefined()
  expect(state.form.triggerSource).toBe('MANUAL')
})
it('sends the selected execution for source-linked deletion but no context for other applications', async () => {
  mocks.route.query = {}
  const state = render(); await flush()
  await state.remove({ id: 41, projectId: 9, triggerSource: 'SITE_SURVEY' })
  expect(mocks.remove).toHaveBeenCalledWith(41, selection)
  await state.remove({ id: 42, projectId: 9, triggerSource: 'MANUAL' })
  expect(mocks.remove).toHaveBeenLastCalledWith(42, undefined)
})
