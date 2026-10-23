import { afterEach, describe, expect, it } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { mount, passthrough, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import QuestionnaireTable from './QuestionnaireTable.vue'
const apps: Array<{ unmount: () => void }> = []
afterEach(() => apps.splice(0).forEach(app => app.unmount()))
const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
const render = () => {
  const child = ref<any>()
  const options = [{ code: 'excellent', label: '很满意', score: 100 }, { code: 'good', label: '满意', score: 80 }]
  const definition = { schemaVersion: 1, questions: ['总体工程质量', '设备安装规范', '工程交付及时性', '功能完善性'].map((title, index) => ({ code: `q${index}`, title, type: 'RATING' as const, required: true, options })) }
  const answers: Record<string, string | string[]> = {}
  const { app, root } = mount(defineComponent({ setup: () => () => h(QuestionnaireTable, { ref: child, definition, answers }) }), {}, { ElInput: passthrough, ElRadio: passthrough, ElRadioGroup: passthrough, ElCheckbox: passthrough, ElCheckboxGroup: passthrough })
  apps.push(app)
  return { child, answers, root }
}
describe('matrix questionnaire interaction', () => {
  it('merges adjacent categories and saves frozen option codes from radio changes', () => {
    const { root, answers } = render()
    const cells = nodes(root).filter(node => node.props?.class === 'category-cell')
    expect(cells.map(node => node.props?.rowspan)).toEqual([3, 1])
    const radios = nodes(root).filter(node => node.type === 'input')
    expect(radios[0].props?.name).toBe(radios[1].props?.name)
    expect(radios[0].props?.name).not.toBe(radios[2].props?.name)
    const chooseFirst = radios[0].props?.onChange as () => void
    const chooseSecond = radios[1].props?.onChange as () => void
    chooseFirst()
    expect(answers.q0).toBe('excellent')
    chooseSecond()
    expect(answers.q0).toBe('good')
  })
  it('rejects missing required answers and values outside the frozen options', async () => {
    const { child, answers } = render()
    await expect(child.value.validate()).rejects.toThrow('总体工程质量')
    Object.assign(answers, { q0: 'forged', q1: 'good', q2: 'good', q3: 'good' })
    await expect(child.value.validate()).rejects.toThrow('选项无效')
    answers.q0 = 'excellent'
    await expect(child.value.validate()).resolves.toBeUndefined()
  })
})
