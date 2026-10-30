import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import { expect, it, vi } from 'vitest'
import { emptyDesignerDocument } from '@/api/pms/project/project-templates'
import Checklist from './NodeDeliverableChecklist.vue'
import { createDeliveryNode } from './templateCanvasModel'
import { mount, passthrough, textOf, type TestNode } from '../../platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/config/axios', () => ({ default: {} }))

/** 输入框内容经 modelValue 绑定而非默认插槽；渲染出值供 textOf 断言。 */
const input = defineComponent({
  inheritAttrs: false,
  props: { modelValue: { type: [String, Number], default: '' } },
  setup: (props, { attrs, slots }) => () => h('section', attrs, [String(props.modelValue ?? ''), slots.default?.()])
})

const findByAria = (node: TestNode, prefix: string): TestNode | undefined =>
  typeof node.props?.['aria-label'] === 'string' && (node.props['aria-label'] as string).startsWith(prefix)
    ? node
    : node.children.map((child) => findByAria(child, prefix)).find(Boolean)

const flush = async () => {
  for (let i = 0; i < 4; i++) {
    await Promise.resolve()
    await nextTick()
  }
}

const buildDoc = () => {
  const doc = reactive(emptyDesignerDocument())
  createDeliveryNode(doc, 'STAGE', undefined, 'prep', { x: 0, y: 0 })
  createDeliveryNode(doc, 'STAGE', undefined, 'close', { x: 400, y: 0 })
  const survey = createDeliveryNode(doc, 'TASK', 'STG1', 'survey', { x: 0, y: 90 })
  doc.deliverables.push(
    { nodeKey: 'deliverable:plan', code: 'PLAN', name: '施工计划', stageCode: 'STG1', required: true },
    { nodeKey: 'deliverable:report', code: 'SURVEY_REPORT', name: '勘察报告', stageCode: 'STG1', taskCode: survey.code, required: false },
    { nodeKey: 'deliverable:close', code: 'CLOSE_DOC', name: '闭环文档', stageCode: 'STG2', required: false }
  )
  return { doc, survey }
}

const mountChecklist = (
  doc: ReturnType<typeof buildDoc>['doc'],
  props: { taskCode?: string; readonly?: boolean } = {}
) => {
  const child = ref<any>()
  const view = mount(
    defineComponent({
      setup: () => () => h(Checklist, { document: doc, stageCode: 'STG1', ...props, ref: child })
    }),
    {},
    { ElInput: input, ElSelect: passthrough, ElOption: passthrough, ElSwitch: passthrough }
  )
  return { ...view, doc, state: () => child.value.$.setupState }
}

const setup = (props: { readonly?: boolean } = {}) => {
  const { doc, survey } = buildDoc()
  return { ...mountChecklist(doc, props), survey }
}

const setupTask = (props: { readonly?: boolean } = {}) => {
  const { doc, survey } = buildDoc()
  return { ...mountChecklist(doc, { ...props, taskCode: survey.code }), survey }
}

it('shows stage-owned and task-bound deliverables of the selected stage only, with task reassignment', async () => {
  const view = setup()
  const text = textOf(view.root)
  expect(text).toContain('维护本阶段交付的成果物')
  expect(text).toContain('施工计划')
  expect(text).toContain('勘察报告')
  expect(text).toContain('必选')
  expect(text).toContain('可选')
  expect(text).not.toContain('闭环文档')
  const select = findByAria(view.root, '交付件归属任务 PLAN')!
  const reassign = select.props!['onUpdate:modelValue'] as (value: string) => void
  reassign(view.survey.code)
  expect(view.doc.deliverables[0].taskCode).toBe(view.survey.code)
  reassign('')
  expect(view.doc.deliverables[0].taskCode).toBeUndefined()
})

it('scopes the task view to the task deliverables without ownership editing', async () => {
  const view = setupTask()
  const text = textOf(view.root)
  expect(text).toContain('维护本任务交付的成果物')
  expect(text).toContain('勘察报告')
  expect(text).not.toContain('施工计划')
  expect(text).not.toContain('闭环文档')
  expect(findByAria(view.root, '交付件归属任务')).toBeUndefined()
})

it('adds rows bound to the stage or to the edited task', async () => {
  const stage = setup()
  stage.state().addRow()
  await flush()
  expect(stage.doc.deliverables.at(-1)).toMatchObject({ code: 'DEL1', name: '新交付件', stageCode: 'STG1', required: false })
  expect(stage.doc.deliverables.at(-1)!.taskCode).toBeUndefined()
  expect(textOf(stage.root)).toContain('新交付件')

  const task = setupTask()
  task.state().addRow()
  await flush()
  expect(task.doc.deliverables.at(-1)).toMatchObject({ code: 'DEL1', stageCode: 'STG1', taskCode: task.survey.code })
})

it('switches only the selected draft group to document presence and keeps readonly definitions intact', () => {
  const view = setup()
  const otherStage = JSON.stringify(view.doc.deliverables[2])
  view.doc.deliverables[0].configuration = { confirmationRule: { predicate: 'TASK', parameters: { refCode: view.survey.code } } }
  view.state().useDocumentPresence()
  expect(view.doc.deliverables[0].configuration).toMatchObject({ minimumQuantity: 1, allowedSources: ['UPLOAD'], confirmationRule: { predicate: 'CONSTANT', parameters: { value: true } } })
  expect(JSON.stringify(view.doc.deliverables[2])).toBe(otherStage)
  const frozen = setup({ readonly: true })
  const before = JSON.stringify(frozen.doc)
  frozen.state().useDocumentPresence()
  expect(JSON.stringify(frozen.doc)).toBe(before)
})

it('validates the deliverable code against format and existing node codes', async () => {
  const view = setup()
  const plan = view.doc.deliverables[0]
  view.state().applyCode(plan, '1BAD')
  expect(plan.code).toBe('PLAN')
  await flush()
  expect(textOf(view.root)).toContain('编码须为唯一的字母开头标识')
  view.state().applyCode(plan, view.survey.code)
  expect(plan.code).toBe('PLAN')
  view.state().applyCode(plan, 'PLAN_V2')
  await flush()
  expect(plan.code).toBe('PLAN_V2')
  expect(textOf(view.root)).not.toContain('编码须为唯一的字母开头标识')
})

it('removes only the edited row', async () => {
  const view = setup()
  const report = view.doc.deliverables[1]
  view.state().removeRow(report)
  expect(view.doc.deliverables.map((node: { code: string }) => node.code)).toEqual(['PLAN', 'CLOSE_DOC'])
  await flush()
  expect(textOf(view.root)).not.toContain('勘察报告')
  expect(textOf(view.root)).toContain('施工计划')
})

it('forbids writes in read-only mode and hides editing controls', async () => {
  const view = setup({ readonly: true })
  const before = JSON.stringify(view.doc)
  view.state().addRow()
  view.state().applyCode(view.doc.deliverables[0], 'CHANGED')
  view.state().removeRow(view.doc.deliverables[0])
  expect(JSON.stringify(view.doc)).toBe(before)
  const text = textOf(view.root)
  expect(text).not.toContain('添加交付件')
  expect(text).not.toContain('删除')
})
