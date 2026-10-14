import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import ResourceConfirmationEditor from './ResourceConfirmationEditor.vue'
import { requirementResources, surveyResources } from './resourceConfirmation'
import * as SurveyApi from '@/api/pms/engineering/site-survey/entity'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/engineering/site-survey/entity', () => ({ getSiteSurveyPage: vi.fn(), getSiteSurvey: vi.fn() }))
const flush = async () => { for (let i = 0; i < 8; i++) await nextTick() }
const render = (readOnly = false) => {
  const body = ref('<p>人工填写</p>'), projectId = ref(1)
  let state: any
  const mounted = mount(defineComponent({ setup: () => () => h(ResourceConfirmationEditor, {
    projectId: projectId.value, readOnly, requirementValues: { transmissionCurrentOptions: ['IPv6'], trafficConcurrency: '200', businessRequirement: '核心业务' },
    modelValue: body.value, 'onUpdate:modelValue': value => { body.value = value }, ref: value => { if (value) state = (value as any).$.setupState }
  }) }), {}, { Editor: passthrough, ElSelect: passthrough, ElOption: passthrough })
  return { ...mounted, body, projectId, get state() { return state } }
}
describe('persisted resource confirmation references', () => {
  beforeEach(() => { vi.clearAllMocks(); vi.mocked(SurveyApi.getSiteSurveyPage).mockResolvedValue({ list: [], total: 0 }) })
  it('quotes transmission, traffic and business rows safely and leaves empty sources empty', () => {
    const content = requirementResources({ transmissionRequirement: '<p>传输说明</p>', trafficThroughput: '1G', businessDeviceDetails: [{ deviceName: '<script>alert(1)</script>', businessName: '业务A' }] })
    expect(content).toContain('传输说明'); expect(content).toContain('1G'); expect(content).toContain('业务A')
    expect(content).not.toContain('<script>');
    const repeated = requirementResources({ transmissionCurrentOptions: ['IPv6', 'IPv6'], businessDeviceDetails: [{ deviceName: '唯一设备' }, { deviceName: '唯一设备' }] })
    expect(repeated.match(/IPv6/g)).toHaveLength(1); expect(repeated.match(/唯一设备/g)).toHaveLength(1);
     expect(requirementResources({})).toBe('')
    expect(surveyResources({ projectId: 1, name: '工勘A', powerSupply: '双路供电', cabinet: '机柜A' })).toContain('双路供电')
  })
  it('automatically quotes the latest confirmed record and merges both sources without duplicates', async () => {
    vi.mocked(SurveyApi.getSiteSurveyPage).mockResolvedValue({ list: [{ id: 11, name: '最新工勘', status: 1 }], total: 1 })
    vi.mocked(SurveyApi.getSiteSurvey).mockResolvedValue({ id: 11, projectId: 1, name: '最新工勘', status: 1, powerSupply: '双路供电' })
    const mounted = render()
    try {
      await flush()
      expect(SurveyApi.getSiteSurveyPage).toHaveBeenCalledWith({ projectId: 1, status: 1, pageNo: 1, pageSize: 1 })
      await mounted.state.referenceResources(); await flush()
      expect(SurveyApi.getSiteSurvey).toHaveBeenCalledWith(11)
      expect(mounted.body.value).toContain('最新工勘'); expect(mounted.body.value).toContain('双路供电')
      expect(mounted.body.value).toContain('IPv6'); expect(mounted.body.value).toContain('核心业务')
      expect(mounted.body.value).not.toContain('<p>人工填写</p>')
      const first = mounted.body.value
      await mounted.state.referenceResources(); await flush()
      expect(mounted.body.value).toBe(first)
    } finally { mounted.app.unmount() }
  })
  it('can quote requirement content when no confirmed survey exists', async () => {
    const mounted = render()
    try {
      await flush(); await mounted.state.referenceResources(); await flush()
      expect(mounted.body.value).toContain('IPv6')
      expect(SurveyApi.getSiteSurvey).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })
  it('requeries the latest confirmed survey at click time and blocks a record whose status changed', async () => {
    vi.mocked(SurveyApi.getSiteSurveyPage)
      .mockResolvedValueOnce({ list: [{ id: 10, status: 1 }], total: 1 })
      .mockResolvedValueOnce({ list: [{ id: 12, status: 1 }], total: 1 })
    vi.mocked(SurveyApi.getSiteSurvey).mockResolvedValue({ id: 12, projectId: 1, name: '已归档工勘', status: 3, conclusion: '不应引用' })
    const mounted = render()
    try {
      await flush(); await mounted.state.referenceResources()
      expect(SurveyApi.getSiteSurvey).toHaveBeenCalledWith(12)
      expect(mounted.state.error).toContain('已确认状态不一致')
      expect(mounted.body.value).toBe('<p>人工填写</p>')
    } finally { mounted.app.unmount() }
  })
  it('preserves body on failure and rejects a survey from another project', async () => {
    vi.mocked(SurveyApi.getSiteSurvey).mockRejectedValueOnce(new Error('403'))
      .mockResolvedValueOnce({ id: 11, projectId: 2, name: '其他项目', status: 1, conclusion: '不能引用' })
    vi.mocked(SurveyApi.getSiteSurveyPage).mockResolvedValue({ list: [{ id: 11, status: 1 }], total: 1 })
    const mounted = render()
    try {
      await flush()
      await mounted.state.referenceResources(); expect(mounted.state.error).toContain('引用失败')
      await mounted.state.loadSurvey()
      await mounted.state.referenceResources(); expect(mounted.state.error).toContain('不一致')
      expect(mounted.body.value).toBe('<p>人工填写</p>')
    } finally { mounted.app.unmount() }
  })
  it('discards a late survey response when the project changes', async () => {
    let resolve!: (value: any) => void
    vi.mocked(SurveyApi.getSiteSurvey).mockReturnValueOnce(new Promise(done => { resolve = done }))
    vi.mocked(SurveyApi.getSiteSurveyPage).mockResolvedValue({ list: [{ id: 11, status: 1 }], total: 1 })
    const mounted = render()
    try {
      await flush()
      const referencing = mounted.state.referenceResources()
      await flush()
      mounted.projectId.value = 2; await flush()
      resolve({ id: 11, projectId: 1, name: '过期工勘', status: 1, conclusion: '过期内容' }); await referencing
      expect(mounted.body.value).toBe('<p>人工填写</p>')
    } finally { mounted.app.unmount() }
  })
  it('does not load sources or replace body in read-only versions', async () => {
    const mounted = render(true)
    try {
      await flush(); await mounted.state.referenceResources()
      expect(SurveyApi.getSiteSurveyPage).not.toHaveBeenCalled(); expect(SurveyApi.getSiteSurvey).not.toHaveBeenCalled()
      expect(mounted.body.value).toBe('<p>人工填写</p>')
    } finally { mounted.app.unmount() }
  })
})
