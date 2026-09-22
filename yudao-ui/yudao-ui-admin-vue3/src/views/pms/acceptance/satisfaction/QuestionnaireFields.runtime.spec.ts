import { afterEach, describe, expect, it } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import QuestionnaireFields from './QuestionnaireFields.vue'

const mounted: Array<{ unmount: () => void }> = []
afterEach(() => mounted.splice(0).forEach((app) => app.unmount()))
const render = (frozenQuestions?: string) => {
  const child = ref<any>()
  const wrapper = defineComponent({ setup: () => () => h(QuestionnaireFields, { frozenQuestions, ref: child }) })
  const { app } = mount(wrapper, {}, { ElRadioGroup: passthrough, ElRadio: passthrough, ElCheckboxGroup: passthrough, ElCheckbox: passthrough, ElInput: passthrough })
  mounted.push(app)
  return child
}
describe('frozen questionnaire entry', () => {
  it('blocks missing frozen questions instead of submitting an empty answer set', () => {
    expect(() => render().value.snapshot()).toThrow('问卷内容不可用')
  })
  it('validates required answers and preserves option codes in the submission contract', async () => {
    const child = render(JSON.stringify({ schemaVersion: 1, questions: [
      { code: 'quality', title: '工程质量', type: 'RATING', required: true, options: [{ code: 'excellent', label: '很满意' }] },
      { code: 'comment', title: '建议', type: 'TEXT', required: false }
    ] }))
    expect(() => child.value.snapshot()).toThrow('工程质量')
    child.value.$.setupState.answers.quality = 'excellent'
    await nextTick()
    expect(JSON.parse(child.value.snapshot())).toEqual({ answers: [{ questionCode: 'quality', value: 'excellent' }] })
  })
  it('blocks answers outside the frozen multiple selection limits', () => {
    const child = render(JSON.stringify({ schemaVersion: 1, questions: [
      { code: 'items', title: '改进项', type: 'MULTIPLE_CHOICE', required: true, minSelections: 1, maxSelections: 1, options: [] }
    ] }))
    child.value.$.setupState.answers.items = ['a', 'b']
    expect(() => child.value.snapshot()).toThrow('选择数量')
  })
})
