import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, onMounted, ref } from 'vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const mocks = vi.hoisted(() => ({
  inspect: vi.fn(),
  initialize: vi.fn(),
  complete: vi.fn(),
  submit: vi.fn(),
  validate: vi.fn()
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { token: 'test-token' }, query: { tenantId: '1' } })
}))
vi.mock('@/utils', () => ({ generateUUID: () => 'test-request' }))
vi.mock('@/utils/formatTime', () => ({ formatDate: () => '2026-09-20' }))
vi.mock('@/components/FormCreate/src/customerConfirmation', () => ({
  customerFormOption: {},
  signaturePngFile: () =>
    new File([new Uint8Array([137, 80, 78, 71])], '签字.png', { type: 'image/png' })
}))
vi.mock('@/api/pms/acceptance/satisfaction', () => ({
  inspectPublicQuestionnaire: mocks.inspect,
  initializeGrantFile: mocks.initialize,
  completeGrantFile: mocks.complete,
  submitPublicResponse: mocks.submit
}))
import Questionnaire from './questionnaire.vue'

const mounted: Array<{ unmount: () => void }> = []
afterEach(() => mounted.splice(0).forEach((app) => app.unmount()))
beforeEach(() => {
  vi.clearAllMocks()
  mocks.validate.mockResolvedValue(undefined)
  mocks.inspect.mockResolvedValue({
    frozenQuestions: JSON.stringify({
      schemaVersion: 1,
      questions: [
        {
          code: 'quality',
          title: '工程质量',
          type: 'RATING',
          required: true,
          options: [{ code: 'excellent', label: '很好' }]
        },
        {
          code: 'items',
          title: '改进项',
          type: 'MULTIPLE_CHOICE',
          required: false,
          minSelections: 1,
          maxSelections: 1,
          options: [
            { code: 'a', label: 'A' },
            { code: 'b', label: 'B' }
          ]
        }
      ]
    })
  })
  mocks.initialize.mockResolvedValue({
    responseId: 10,
    sessionId: 20,
    fileSlotKey: 'signature',
    fileSequence: 1,
    artifactId: 30
  })
  mocks.complete.mockResolvedValue({
    policyKey: 'SATISFACTION_SIGNATURE',
    fileSlotKey: 'signature',
    fileSequence: 1,
    fileFact: {
      artifactId: 30,
      versionNo: 1,
      referenceKey: 'ref',
      fileFactVersion: { artifactVersion: 1, referenceVersion: 1, availabilityVersion: 1 },
      scopeVersion: 1,
      sha256: 'digest'
    }
  })
  mocks.submit.mockResolvedValue({ passed: true, score: 100, threshold: 80 })
})
const render = async () => {
  const child = ref<any>()
  const wrapper = defineComponent({ setup: () => () => h(Questionnaire, { ref: child }) })
  const { app } = mount(
    wrapper,
    {},
    {
      FormCreate: defineComponent({
        emits: ['update:api'],
        setup(_, { emit }) {
          onMounted(() => emit('update:api', { validate: mocks.validate }))
          return () => h('div')
        }
      }),
      ElInput: passthrough,
      ElUpload: passthrough,
      ElSkeleton: passthrough,
      ElResult: passthrough,
      ElRadioGroup: passthrough,
      ElRadioButton: passthrough,
      ElRadio: passthrough,
      ElCheckbox: passthrough,
      ElCheckboxGroup: passthrough,
      ElTabs: passthrough,
      ElTabPane: passthrough
    }
  )
  mounted.push(app)
  await Promise.resolve()
  await nextTick()
  return child.value.$.setupState
}
describe('public questionnaire handwritten signature submission', () => {
  it('retries the exact signed payload without another upload after a submission failure', async () => {
    const page = await render()
    page.signatureValues = { signatureImageDataUrl: 'data:image/png;base64,iVBORw==' }
    page.answers.quality = 'excellent'
    mocks.submit.mockRejectedValueOnce(new Error('result generation temporarily failed'))
    await expect(page.submit()).rejects.toThrow('temporarily failed')
    await page.submit()
    expect(mocks.initialize).toHaveBeenCalledOnce()
    expect(mocks.complete).toHaveBeenCalledOnce()
    expect(mocks.submit).toHaveBeenCalledTimes(2)
    expect(mocks.submit.mock.calls[1][2]).toEqual(mocks.submit.mock.calls[0][2])
  })
  it('requires joint consent and uses one signature upload for both forms', async () => {
    const inspect = await mocks.inspect()
    inspect.completionCertificate = { id: 20, version: 3, projectId: 9, name: '本次完工证明', devices: [] }
    mocks.inspect.mockResolvedValue(inspect)
    const page = await render()
    page.signatureValues = { signatureImageDataUrl: 'data:image/png;base64,iVBORw==' }
    page.answers.quality = 'excellent'
    await page.submit()
    expect(mocks.initialize).not.toHaveBeenCalled()
    page.confirmCompletionCertificate = true
    await page.submit()
    expect(mocks.initialize).toHaveBeenCalledOnce()
    expect(mocks.complete).toHaveBeenCalledOnce()
    expect(mocks.submit.mock.calls[0][2]).toMatchObject({ confirmCompletionCertificate: true, signatureImageDataUrl: 'data:image/png;base64,iVBORw==' })
    expect(mocks.submit.mock.calls[0][2].files).toHaveLength(1)
  })
  it('reopens a submitted bundle with saved answers and signature and prevents another upload', async () => {
    const inspect = await mocks.inspect()
    mocks.inspect.mockResolvedValue({ ...inspect, completionCertificate: { id: 20, name: '完工证明', devices: [] },
      submitted: true, answerSnapshot: JSON.stringify({ answers: [{ questionCode: 'quality', value: 'excellent' }] }),
      signatureImageDataUrl: 'data:image/png;base64,iVBORw==', signedAt: 1770000000000 })
    const page = await render()
    expect(page.answers.quality).toBe('excellent')
    expect(page.signatureValues.signatureImageDataUrl).toBe('data:image/png;base64,iVBORw==')
    expect(page.submissionLocked).toBe(true)
    await page.submit()
    expect(mocks.initialize).not.toHaveBeenCalled()
    expect(mocks.submit).not.toHaveBeenCalled()
  })
  it('blocks uploads when the form validator rejects otherwise complete answers', async () => {
    const page = await render()
    page.customerContactRef = '客户'
    page.signatureValues = { signatureImageDataUrl: 'data:image/png;base64,iVBORw==' }
    page.answers.quality = 'excellent'
    mocks.validate.mockRejectedValueOnce(new Error('invalid form'))
    page.questionFormApi.validate = mocks.validate
    await page.submit()
    expect(mocks.validate).toHaveBeenCalledOnce()
    expect(mocks.initialize).not.toHaveBeenCalled()
    expect(mocks.submit).not.toHaveBeenCalled()
  })
  it('blocks uploads until required answers and selection limits are valid', async () => {
    const page = await render()
    page.customerContactRef = '客户'
    page.signatureValues = { signatureImageDataUrl: 'data:image/png;base64,iVBORw==' }
    await page.submit()
    expect(mocks.initialize).not.toHaveBeenCalled()
    page.answers.quality = 'excellent'
    page.answers.items = ['a', 'b']
    await page.submit()
    expect(mocks.initialize).not.toHaveBeenCalled()
  })
  it('saves the handwritten image through the existing controlled file grant and keeps frozen answer codes', async () => {
    const page = await render()
    page.customerContactRef = '客户'
    page.signatureValues = { signatureImageDataUrl: 'data:image/png;base64,iVBORw==' }
    page.answers.quality = 'excellent'
    await page.submit()
    expect(mocks.initialize.mock.calls[0][2]).toMatchObject({
      policyKey: 'SATISFACTION_SIGNATURE',
      declaredMediaType: 'image/png'
    })
    expect(mocks.complete.mock.calls[0][4]).toBeInstanceOf(File)
    const command = mocks.submit.mock.calls[0][2]
    expect(JSON.parse(command.answerSnapshot)).toEqual({
      answers: [{ questionCode: 'quality', value: 'excellent' }]
    })
    expect(command.files[0]).toMatchObject({ role: 'SIGNATURE', artifactId: 30, versionNo: 1 })
  })
  it('retains the existing signature-file upload path', async () => {
    const page = await render()
    page.customerContactRef = '客户'
    page.answers.quality = 'excellent'
    page.signatureMode = 'upload'
    const file = new File(['signed'], 'existing.pdf', { type: 'application/pdf' })
    page.signatureFile = file
    await page.submit()
    expect(mocks.complete.mock.calls[0][4]).toBe(file)
    expect(mocks.submit).toHaveBeenCalledOnce()
  })
})
