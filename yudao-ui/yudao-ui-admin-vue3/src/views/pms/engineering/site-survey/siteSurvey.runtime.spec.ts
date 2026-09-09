import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { findByTestId, mount, passthrough, tableColumn, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Page from './index.vue'

const api = vi.hoisted(() => ({ getSiteSurveyPage: vi.fn(), getSiteSurvey: vi.fn(), createSiteSurvey: vi.fn(), updateSiteSurvey: vi.fn(), deleteSiteSurvey: vi.fn(), confirmSiteSurvey: vi.fn(), rejectSiteSurvey: vi.fn(), archiveSiteSurvey: vi.fn() }))
const messages = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn(), info: vi.fn(), confirm: vi.fn(), delConfirm: vi.fn() }))
vi.mock('@/api/pms/engineering/site-survey', () => api)
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => messages }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: { PMS_SITE_SURVEY_STATUS: 'survey' }, getIntDictOptions: () => [] }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => ({ value: false }) }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))
const flush = async () => { for (let i = 0; i < 6; i++) { await nextTick(); await Promise.resolve() } }
const apps: { unmount(): void }[] = []
const walk = (node: TestNode, test: (node: TestNode) => boolean): TestNode | undefined => {
  if (test(node)) return node
  for (const child of node.children) { const match = walk(child, test); if (match) return match }
}
const formStub = defineComponent({ inheritAttrs: false, setup: (_, { attrs, slots, expose }) => {
  expose({ validate: vi.fn().mockResolvedValue(true) })
  return () => h('form', attrs, slots.default?.())
} })
const dialogStub = defineComponent({ inheritAttrs: false, setup: (_, { attrs, slots }) => () => h('dialog', attrs, [slots.default?.(), slots.footer?.()]) })
const render = async () => {
  const components = Object.fromEntries(['ElInput','ElSelect','ElOption','ElRow','ElCol','ElDatePicker','ElTable','ElTableColumn','PmsEntitySelect','PmsLocationSelector','Editor','DictTag'].map(key => [key,passthrough]))
  const view = mount(Page, {}, { ...components, ElTableColumn: tableColumn, ElForm: formStub, Dialog: dialogStub })
  apps.push(view.app)
  await flush()
  return view
}
const set = async (node: TestNode, value: unknown) => { await (node.props?.['onUpdate:modelValue'] as Function)(value); await flush() }
const click = async (node: TestNode) => { await (node.props?.onClick as Function)(); await flush() }
beforeEach(() => {
  vi.clearAllMocks()
  api.getSiteSurveyPage.mockResolvedValue({list:[],total:0})
  api.createSiteSurvey.mockResolvedValue(701)
  api.getSiteSurvey.mockResolvedValue({id:701,projectId:11,code:'SURVEY-TEST',name:'工勘',status:0,version:0})
  messages.confirm.mockResolvedValue(true)
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

describe('原工勘页面直接复用', () => {
  it('保留原查询列表、完整编辑字段和地点选择，不使用项目工作台替代', async () => {
    const view = await render()
    await click(findByTestId(view.root,'original-survey-add')!)
    for (const key of ['code','name','powerSupply','cabinet','networkPort','fiber','module','cable','ground','constructionResource','remark']) {
      expect(findByTestId(view.root,`original-survey-${key}`)).toBeDefined()
    }
    expect(textOf(view.root)).toContain('新增工勘')
    expect(textOf(view.root)).not.toContain('逐项分工、固定表单')
  })
  it('整张表单保存原实体字段，成功后真实接口回读', async () => {
    const view = await render()
    await click(findByTestId(view.root,'original-survey-add')!)
    const form = walk(view.root,n=>n.type==='form' && !!n.props?.rules)!.props!.model as any
    Object.assign(form,{projectId:11,code:'SURVEY-TEST',name:'工勘',locationMaintenance:{fallbackLocation:'机房待维护'}})
    await set(findByTestId(view.root,'original-survey-powerSupply')!,'双路交流')
    await set(findByTestId(view.root,'original-survey-cabinet')!,'机柜A02')
    await click(findByTestId(view.root,'original-survey-save')!)
    expect(api.createSiteSurvey).toHaveBeenCalledWith(expect.objectContaining({powerSupply:'双路交流',cabinet:'机柜A02',location:'机房待维护'}))
    const payload=api.createSiteSurvey.mock.calls[0][0]
    expect(payload).not.toHaveProperty('surveyResult')
    expect(payload).not.toHaveProperty('entityValueJson')
    expect(payload).not.toHaveProperty('status')
    expect(api.getSiteSurvey).toHaveBeenCalledWith(701)
  })
  it('保存失败保留所有业务字段供重试', async () => {
    api.createSiteSurvey.mockRejectedValue(new Error('denied'))
    const view = await render()
    await click(findByTestId(view.root,'original-survey-add')!)
    const form = walk(view.root,n=>n.type==='form' && !!n.props?.rules)!.props!.model as any
    Object.assign(form,{projectId:11,code:'SURVEY-TEST',name:'工勘',locationMaintenance:{fallbackLocation:'机房'}})
    await set(findByTestId(view.root,'original-survey-fiber')!,'单模光纤已核对')
    await click(findByTestId(view.root,'original-survey-save')!)
    expect(form.fiber).toBe('单模光纤已核对')
    expect(textOf(view.root)).toContain('保存失败，填写内容已保留')
    expect(api.getSiteSurvey).not.toHaveBeenCalled()
  })
  it('已保存但回读失败不会再次创建记录', async () => {
    api.getSiteSurvey.mockRejectedValue(new Error('offline'))
    const view=await render()
    await click(findByTestId(view.root,'original-survey-add')!)
    const form=walk(view.root,n=>n.type==='form' && !!n.props?.rules)!.props!.model as any
    Object.assign(form,{projectId:11,code:'SURVEY-TEST',name:'工勘',locationMaintenance:{fallbackLocation:'机房'}})
    await click(findByTestId(view.root,'original-survey-save')!)
    expect(textOf(view.root)).toContain('重新读取已保存记录')
    expect(api.createSiteSurvey).toHaveBeenCalledTimes(1)
  })
})
