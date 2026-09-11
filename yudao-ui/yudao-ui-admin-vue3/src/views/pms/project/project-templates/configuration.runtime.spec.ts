import { computed, defineComponent, h, inject, nextTick, provide, reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as Definitions from '@/api/pms/project/project-templates/definitions'
import * as Views from '@/api/pms/platform/business-view'
import * as Templates from '@/api/pms/project/project-templates'
import StageRelationsEditor from './StageRelationsEditor.vue'
import DefinitionSelect from './DefinitionSelect.vue'
import BusinessViewSelect from './BusinessViewSelect.vue'
import DefinitionForm from './DefinitionForm.vue'
import DefinitionLibrary from './DefinitionLibrary.vue'
import TemplateContentEditor from './TemplateContentEditor.vue'
import AdvancedTemplateContentEditor from './AdvancedTemplateContentEditor.vue'
import { hasPermission } from '@/directives/permission/hasPermi'
import { getTemplateSelection, getRevision } from '@/api/pms/platform/dynamic-form'
import RuleEditor from './RuleEditor.vue'
import TemplatePage from './index.vue'
import { cloneContent, emptyContent, graphIssues, relationsFor } from './editorModel'
import { createRenderer, type Component } from 'vue'
import { passthrough, textOf, type TestNode } from '../../platform/dynamic-form/components/runtimeTestHarness'
// This suite needs real anchor-aware movement when switching filtered graph rows / rule branches.
const renderer = createRenderer<TestNode, TestNode>({
  patchProp: (node, key, _old, value) => { (node.props ??= {})[key] = value },
  insert: (node, parent, anchor) => {
    if (node.parent) node.parent.children = node.parent.children.filter((child) => child !== node)
    node.parent = parent
    const index = anchor ? parent.children.indexOf(anchor) : -1
    if (index < 0) parent.children.push(node)
    else parent.children.splice(index, 0, node)
  },
  remove: (node) => { if (node.parent) node.parent.children = node.parent.children.filter((child) => child !== node) },
  createElement: (type) => ({ type, children: [] }), createText: (text) => ({ type: '#text', text, children: [] }),
  createComment: (text) => ({ type: '#comment', text, children: [] }), setText: (node, text) => { node.text = text },
  setElementText: (node, text) => { node.children = [{ type: '#text', text, children: [], parent: node }] },
  parentNode: (node) => node.parent ?? null,
  nextSibling: (node) => node.parent?.children[(node.parent?.children.indexOf(node) ?? -1) + 1] ?? null,
  insertStaticContent: (text, parent, anchor) => {
    const node: TestNode = { type: '#static', text, children: [], parent }
    const index = anchor ? parent.children.indexOf(anchor) : -1
    if (index < 0) parent.children.push(node); else parent.children.splice(index, 0, node)
    return [node, node]
  }
})
const mount = (component: Component, props: Record<string, unknown>, components: Record<string, Component>) => {
  const root: TestNode = { type: 'root', children: [] }
  const app = renderer.createApp(component, props)
  for (const name of ['ElAlert', 'ElTag', 'ElForm', 'ElFormItem', 'ContentWrap', 'Pagination']) app.component(name, passthrough)
  app.component('ElButton', control('button'))
  for (const [name, value] of Object.entries(components)) app.component(name, value)
  app.directive('hasPermi', {}); app.directive('loading', {})
  app.mount(root)
  return { root, app }
}

vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/directives/permission/hasPermi', () => ({ hasPermission: vi.fn(() => true) }))
vi.mock('@/api/pms/platform/dynamic-form', () => ({ getTemplateSelection: vi.fn(async () => ({ list: [], total: 0 })), getRevision: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ confirm: vi.fn(async () => true), success: vi.fn() }) }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getStrDictOptions: () => [] }))
vi.mock('@/utils/formatTime', () => ({ dateFormatter: () => '', formatDate: () => '' }))
vi.mock('@/api/pms/project/project-templates/definitions', async (original) => ({
  ...await original<typeof Definitions>(), __v_isRef: false, getDefinitionPage: vi.fn(), getDefinition: vi.fn(), createDefinition: vi.fn(), updateDefinition: vi.fn(),
  validateDefinition: vi.fn(), publishDefinition: vi.fn(), copyDefinition: vi.fn(), disableDefinition: vi.fn()
}))
vi.mock('@/api/pms/platform/business-view', async (original) => ({
  ...await original<typeof Views>(), getBusinessViewPage: vi.fn(), getBusinessView: vi.fn(), getBusinessViewComponents: vi.fn(), createBusinessView: vi.fn(), publishBusinessView: vi.fn()
}))
vi.mock('@/api/pms/project/project-templates', async (original) => ({
  ...await original<typeof Templates>(), getProjectTemplatePage: vi.fn(), getProjectTemplate: vi.fn(), validateProjectTemplate: vi.fn(), publishProjectTemplate: vi.fn(), getProjectTemplateRevision: vi.fn(), updateProjectTemplate: vi.fn()
}))
const table = defineComponent({
  props: ['data'], setup(props, { slots }) { provide('rows', computed(() => props.data ?? [])); return () => h('table', slots.default?.()) }
})
const column = defineComponent({
  props: ['prop', 'type'], setup(props, { slots }) {
    const rows = inject<any>('rows')
    return () => h('column', rows.value.map((row: any, index: number) => slots.default?.({ row, $index: index }) ?? String(row[props.prop] ?? '')))
  }
})
const control = (name: string) => defineComponent({ inheritAttrs: false, setup(_, { attrs, slots }) { return () => h(name, attrs, slots.default?.()) } })
const dialog = defineComponent({ props: ['modelValue'], setup(props, { slots }) { return () => props.modelValue ? h('dialog', [slots.default?.(), slots.footer?.()]) : null } })
const tabPane = defineComponent({ props: ['name'], setup(_, { slots }) { return () => h('tab', slots.default?.()) } })
const options = {
  ElTable: table, ElTableColumn: column, ElInput: control('input'), ElInputNumber: control('number'), ElSelect: control('select'), ElOption: control('option'),
  ElRadioGroup: control('radio'), ElRadioButton: control('radio-option'), ElRadio: control('radio-option'), ElCheckbox: control('checkbox'), ElTabs: control('tabs'), ElTabPane: tabPane,
  ElDrawer: dialog, Dialog: dialog, ElDescriptions: passthrough, ElDescriptionsItem: passthrough, ElCollapse: passthrough, ElCollapseItem: passthrough,
  ElDivider: passthrough, ElRow: passthrough, ElCol: passthrough, ElResult: passthrough, ElEmpty: passthrough
}
const all = (node: TestNode, type: string): TestNode[] => [...(node.type === type ? [node] : []), ...node.children.flatMap((child) => all(child, type))]
const tick = async () => { for (let i = 0; i < 12; i++) { await Promise.resolve(); await nextTick() } }
const click = async (root: TestNode, label: string) => {
  const node = all(root, 'button').find((node) => textOf(node) === label)
  expect(node, label).toBeTruthy()
  await (node!.props!.onClick as Function)()
  await tick()
}
const update = async (node: TestNode, value: unknown) => {
  const handlers = node.props!['onUpdate:modelValue']
  for (const handler of Array.isArray(handlers) ? handlers : [handlers]) (handler as Function)(value)
  await tick()
}
const definition = (id = 5, kind: Definitions.DefinitionKind = 'WORK_BINDING'): Definitions.DefinitionRevision => ({
  id, definitionKind: kind, definitionCode: `DEF_${id}`, revisionNo: 2, revisionState: 'PUBLISHED', schemaVersion: 1,
  payload: { bindingType: 'TASK_NATIVE', instanceResolutionStrategy: 'REFERENCE_EXISTING', contextMapping: {} }, references: [], version: 3
})
const view = (id: Views.BusinessViewId = 20, source: Views.BusinessViewSource = 'PAGE'): Views.BusinessViewRegistrationVO => ({
  id, viewKey: `VIEW_${id}`, entityType: 'REQUIREMENT_ANALYSIS', ownerContext: 'SOL', viewSource: source,
  componentKey: 'PROJ_REQUIREMENT_ANALYSIS', componentVersion: '1', revisionNo: 2, status: 'PUBLISHED', version: 4,
  contextSchema: { projectId: 'positive' }, supportedActions: ['VIEW'], allowedActions: [], queryProviderKey: 'Q', commandProviderKey: 'C', permissionProviderKey: 'P'
})
const graph = (): Templates.TemplateDefinitionContent => ({ ...emptyContent(), stages: [
  { stageCode: 'S0', name: '开始', start: true, terminal: false }, { stageCode: 'S4', name: '收口', start: false, terminal: true }
], transitions: [{ transitionCode: 'E', fromStageCode: 'S0', toStageCode: 'S4', priority: 0, default: false, revisionNo: 1 }] })
const template = (): Templates.ProjectTemplateDetailVO => ({ id: 1, code: 'TPL', name: '模板', status: 'ACTIVE', version: 2, draftContent: graph(), revisions: [{ id: 1, templateId: 1, revisionNo: 1, status: 'PUBLISHED' }] })
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(hasPermission).mockReturnValue(true)
  vi.mocked(Views.getBusinessViewComponents).mockResolvedValue([view()])
  vi.mocked(Definitions.getDefinitionPage).mockResolvedValue({ list: [definition()], total: 1 })
  vi.mocked(Definitions.getDefinition).mockImplementation(async (id) => definition(id))
  vi.mocked(Definitions.validateDefinition).mockResolvedValue({ valid: true, issues: [] })
  vi.mocked(Views.getBusinessViewPage).mockResolvedValue({ list: [view()], total: 1 })
  vi.mocked(Views.getBusinessView).mockImplementation(async (id) => view(id))
  vi.mocked(Templates.getProjectTemplatePage).mockResolvedValue({ list: [template()], total: 1 })
  vi.mocked(Templates.getProjectTemplate).mockResolvedValue(template())
  vi.mocked(Templates.validateProjectTemplate).mockResolvedValue({ valid: true, issues: [] })
})

describe('PM-03 explicit graph and exact selections', () => {
  it('edits predecessor and successor from the same array, including default and priority', async () => {
    const content = reactive(graph())
    const mounted = mount(StageRelationsEditor, { content }, options)
    await tick()
    await update(all(mounted.root, 'select')[0], 'S4')
    await update(all(mounted.root, 'radio')[0], 'to')
    await update(all(mounted.root, 'checkbox')[0], true)
    await update(all(mounted.root, 'number')[0], 7)
    expect(relationsFor(content, 'S0', 'from')[0]).toBe(relationsFor(content, 'S4', 'to')[0])
    expect(content.transitions?.[0]).toMatchObject({ default: true, priority: 7 })
    await update(all(mounted.root, 'select')[0], 'S0')
    await update(all(mounted.root, 'radio')[0], 'from')
    expect(all(mounted.root, 'checkbox')[0].props?.modelValue).toBe(true)
    expect(all(mounted.root, 'number')[0].props?.modelValue).toBe(7)
    mounted.app.unmount()
  })
  it('keeps missing historical graph readable and adds edges only on explicit action', async () => {
    const history = { ...emptyContent(), stages: [{ stageCode: 'S0', name: '旧', sortOrder: 100 }] }
    const content = reactive(cloneContent(history))
    const mounted = mount(StageRelationsEditor, { content }, options)
    await tick()
    expect(content.transitions).toBeUndefined()
    expect(textOf(mounted.root)).toContain('未提供关系图')
    expect(history.stages).toHaveLength(1)
    await click(mounted.root, '新增关系')
    // A unique transition code is pre-generated from the known endpoint (here: none selected),
    // so an explicit edge never relies on the user inventing a code before saving.
    expect(content.transitions).toEqual([{ transitionCode: 'TR_EDGE_1', fromStageCode: '', toStageCode: '', priority: 0, default: false, revisionNo: 1 }])
    expect(content.stages).toHaveLength(1)
    expect(history).not.toHaveProperty('transitions')
    mounted.app.unmount()
  })
  it('reports cycle, dangling, duplicate defaults and invalid default condition rather than silently fixing them', () => {
    const content = graph()
    content.transitions!.push({ transitionCode: 'BACK', fromStageCode: 'S4', toStageCode: 'S0', priority: 0, default: false, revisionNo: 1 }, { transitionCode: 'BAD', fromStageCode: 'S0', toStageCode: 'missing', priority: 1, default: true, conditionRuleRevisionId: 10, revisionNo: 1 })
    content.transitions![0].default = true
    const codes = graphIssues(content).map((issue) => issue.code)
    expect(codes).toEqual(expect.arrayContaining(['CYCLE', 'DANGLING_EDGE', 'MULTIPLE_DEFAULTS', 'DEFAULT_HAS_CONDITION']))
    expect(content.transitions).toHaveLength(3)
  })
  it('paginates exact kind selection, excludes draft/disabled revisions, and emits a numeric exact reference', async () => {
    vi.mocked(Definitions.getDefinitionPage).mockResolvedValueOnce({ list: [definition(1), { ...definition(2), disabledAt: 'today' }], total: 3 }).mockResolvedValueOnce({ list: [definition(3)], total: 3 })
    const onUpdate = vi.fn()
    const mounted = mount(DefinitionSelect, { kind: 'WORK_BINDING', modelValue: 99, 'onUpdate:modelValue': onUpdate }, options)
    await tick()
    expect(Definitions.getDefinitionPage).toHaveBeenCalledTimes(2)
    expect(all(mounted.root, 'option').map((row) => row.props?.value)).toEqual([1, 3, 99])
    expect(all(mounted.root, 'option').find((row) => row.props?.value === 99)?.props).toHaveProperty('disabled')
    await update(all(mounted.root, 'select')[0], 3)
    expect(onUpdate).toHaveBeenCalledWith(3)
    mounted.app.unmount()
  })
  it('selects registered PAGE and DYNAMIC_FORM revisions without replacing a selection on filtering', async () => {
    vi.mocked(Views.getBusinessViewPage).mockResolvedValue({ list: [view(20), { ...view(21, 'DYNAMIC_FORM'), dynamicFormRevisionId: 55 }, { ...view(22), disabledAt: 'today' }], total: 3 })
    const onUpdate = vi.fn()
    const mounted = mount(BusinessViewSelect, { modelValue: 20, 'onUpdate:modelValue': onUpdate }, options)
    await tick()
    await update(all(mounted.root, 'select')[0], 'DYNAMIC_FORM')
    expect(onUpdate).not.toHaveBeenCalled()
    const choices = all(mounted.root, 'option').filter((row) => typeof row.props?.value === 'number')
    expect(choices.map((row) => row.props?.value)).toEqual([21, 20])
    await update(all(mounted.root, 'select')[1], 21)
    expect(onUpdate).toHaveBeenCalledWith(21)
    mounted.app.unmount()
  })
  it('preserves adjacent Snowflake view IDs through selection, filtering and payload serialization', async () => {
    const first = '9223372036854775806'
    const second = '9223372036854775807'
    vi.mocked(Views.getBusinessViewPage).mockResolvedValue({ list: [view(first), { ...view(second, 'DYNAMIC_FORM'), dynamicFormRevisionId: first }], total: 2 })
    const model = reactive<Definitions.DefinitionSave>({ definitionCode: 'BIG_BIND', definitionKind: 'WORK_BINDING', schemaVersion: 1, payload: { bindingType: 'BUSINESS_COMPONENT', instanceResolutionStrategy: 'REFERENCE_EXISTING', contextMapping: {}, businessViewRevisionId: first }, references: [] })
    const mounted = mount(DefinitionForm, { model }, options)
    await tick()
    expect(Views.getBusinessView).toHaveBeenCalledWith(first)
    const selectView = () => all(mounted.root, 'select').find((node) => node.props?.placeholder === '选择已发布、未停用的精确视图')!
    expect(all(selectView(), 'option').map((node) => node.props?.value)).toEqual([first, second])
    await update(selectView(), second)
    expect(model.payload.businessViewRevisionId).toBe(second)
    expect(Views.getBusinessView).toHaveBeenLastCalledWith(second)
    const filter = all(mounted.root, 'select').find((node) => node.props?.placeholder === '全部来源')!
    await update(filter, 'PAGE')
    expect(model.payload.businessViewRevisionId).toBe(second)
    expect(all(selectView(), 'option').find((node) => node.props?.value === second)?.props).toHaveProperty('disabled')
    expect(JSON.parse(JSON.stringify(model)).payload.businessViewRevisionId).toBe(second)
    await update(selectView(), first)
    expect(model.payload.businessViewRevisionId).toBe(first)
    mounted.app.unmount()
  })
  it('matches safe numeric and string representations without rewriting the model on load', async () => {
    vi.mocked(Views.getBusinessViewPage).mockResolvedValue({ list: [view('20')], total: 1 })
    const onUpdate = vi.fn()
    const onSelected = vi.fn()
    const mounted = mount(BusinessViewSelect, { modelValue: 20, 'onUpdate:modelValue': onUpdate, onSelected }, options)
    await tick()
    const selector = all(mounted.root, 'select')[1]
    expect(selector.props?.['model-value']).toBe('20')
    expect(all(selector, 'option')).toHaveLength(1)
    expect(onUpdate).not.toHaveBeenCalled()
    await update(selector, 20)
    expect(onUpdate).toHaveBeenCalledWith('20')
    expect(onSelected).toHaveBeenCalledWith(expect.objectContaining({ id: '20' }))
    await update(selector, '')
    expect(onUpdate).toHaveBeenLastCalledWith(undefined)
    mounted.app.unmount()
  })
  it('writes Stage/Task default binding/policy/rule slots through real definition selectors', async () => {
    vi.mocked(Definitions.getDefinitionPage).mockImplementation(async (query) => ({ list: [definition(5, query.definitionKind)], total: 1 }))
    const model = reactive<Definitions.DefinitionSave>({ definitionCode: 'TASK', definitionKind: 'TASK', schemaVersion: 1, payload: { name: '任务', workBinding: 'workBinding', permissionPolicy: 'permissionPolicy', completionRule: 'completionRule' }, references: [] })
    const mounted = mount(DefinitionForm, { model }, options)
    await tick()
    const selects = all(mounted.root, 'select')
    for (let i = 0; i < 3; i++) await update(selects[i], 5)
    expect(model.references).toEqual([{ referenceKey: 'workBinding', targetRevisionId: 5 }, { referenceKey: 'permissionPolicy', targetRevisionId: 5 }, { referenceKey: 'completionRule', targetRevisionId: 5 }])
    expect(model.payload.workBinding).toBe('workBinding')
    mounted.app.unmount()
  })
  it('applies an exact task definition and its three default references without inventing stages', async () => {
    const task = { ...definition(12, 'TASK'), payload: { name: '真实任务', workBinding: 'workBinding', permissionPolicy: 'permissionPolicy', completionRule: 'completionRule' }, references: [{ referenceKey: 'workBinding', targetRevisionId: 5 }, { referenceKey: 'permissionPolicy', targetRevisionId: 6 }, { referenceKey: 'completionRule', targetRevisionId: 7 }] }
    vi.mocked(Definitions.getDefinitionPage).mockImplementation(async (query) => ({ list: query.definitionKind === 'TASK' ? [task] : [], total: query.definitionKind === 'TASK' ? 1 : 0 }))
    const content = reactive(emptyContent())
    const mounted = mount(AdvancedTemplateContentEditor, { content }, options)
    await tick()
    await click(mounted.root, '新增任务')
    const selector = all(mounted.root, 'select').find((node) => node.props?.placeholder === '选择已发布任务修订')!
    await update(selector, 12)
    expect(content.tasks[0]).toMatchObject({ definitionRevisionId: 12, name: '真实任务', workBindingRevisionId: 5, permissionPolicyRevisionId: 6, completionRuleRevisionId: 7 })
    expect(content.stages).toEqual([])
    expect(content.transitions).toBeUndefined()
    mounted.app.unmount()
  })
  it('selects a registered view inside a real binding form and derives only its Owner metadata', async () => {
    const model = reactive<Definitions.DefinitionSave>({ definitionCode: 'BIND', definitionKind: 'WORK_BINDING', schemaVersion: 1, payload: { bindingType: 'BUSINESS_COMPONENT', instanceResolutionStrategy: 'REFERENCE_EXISTING', contextMapping: {} }, references: [] })
    const mounted = mount(DefinitionForm, { model }, options)
    await tick()
    const selector = all(mounted.root, 'select').find((node) => node.props?.placeholder === '选择已发布、未停用的精确视图')!
    await update(selector, 20)
    expect(model.payload).toMatchObject({ businessViewRevisionId: 20, targetContextCode: 'SOL', targetObjectType: 'REQUIREMENT_ANALYSIS' })
    expect(model.payload).not.toHaveProperty('commandProviderKey')
    expect(model.payload).not.toHaveProperty('targetObjectKey')
    mounted.app.unmount()
  })
  it('edits required deliverable quantity and controlled confirmation rules without duplicating template fields', async () => {
    const model = reactive<Definitions.DefinitionSave>({ definitionCode: 'DELIVERY', definitionKind: 'DELIVERABLE', schemaVersion: 1, payload: { scope: 'TASK', required: true, minimumQuantity: 1, allowedSources: ['FILE'], outputType: 'FILE', deliverableType: 'REPORT', confirmationRule: { predicate: 'DELIVERABLE', parameters: { refCode: 'REPORT' } } }, references: [] })
    const mounted = mount(DefinitionForm, { model }, options)
    await tick()
    expect(all(mounted.root, 'number')[0].props?.min).toBe(1)
    await update(all(mounted.root, 'number')[0], 3)
    expect(model.payload.minimumQuantity).toBe(3)
    expect(textOf(mounted.root)).toContain('必传不可用零数量绕过')
    mounted.app.unmount()
  })
  it('builds registered predicate combinations without an arbitrary JSON editor', async () => {
    const value = reactive({ rule: { predicate: 'TASK_NATIVE_STATUS', parameters: { requiredStatus: 'DONE' } } as Definitions.JsonObject })
    const wrapper = defineComponent({ setup: () => () => h(RuleEditor, { modelValue: value.rule, 'onUpdate:modelValue': (rule) => { value.rule = rule } }) })
    const mounted = mount(wrapper, {}, options)
    await tick()
    await update(all(mounted.root, 'select')[0], 'ALL')
    await update(all(mounted.root, 'select')[2], 'TASK')
    await update(all(mounted.root, 'input')[0], 'TASK_REVIEW')
    expect(value.rule).toEqual({ operator: 'ALL', rules: [{ predicate: 'TASK', parameters: { refCode: 'TASK_REVIEW' } }] })
    await click(mounted.root, '新增条件')
    expect(value.rule.rules).toHaveLength(2)
    mounted.app.unmount()
  })
})

describe('PM-03 business delivery interactions', () => {
  const chooseStage = async (root: TestNode, code: string) => {
    const button = all(root, 'button').find((node) => node.props?.class?.toString().includes('stage-button') && textOf(node).startsWith(code))!
    await (button.props!.onClick as Function)(); await tick()
  }
  const taskRevision = (): Definitions.DefinitionRevision => ({ ...definition(12, 'TASK'), payload: { name: '施工计划制定与审批', workBinding: 'b', permissionPolicy: 'p', completionRule: 'c' }, references: [{ referenceKey: 'b', targetRevisionId: 5 }, { referenceKey: 'p', targetRevisionId: 6 }, { referenceKey: 'c', targetRevisionId: 7 }] })
  const setupContract = () => {
    const rows = new Map<number, Definitions.DefinitionRevision>([[12, taskRevision()], [5, definition(5)], [6, { ...definition(6, 'PERMISSION_POLICY'), payload: { requiredActions: ['VIEW'] } }], [7, { ...definition(7, 'COMPLETION_RULE'), payload: { predicate: 'TASK_NATIVE_STATUS', parameters: { requiredStatus: 'DONE' } } }]])
    vi.mocked(Definitions.getDefinitionPage).mockImplementation(async (query) => ({ list: query.definitionKind === 'TASK' ? [taskRevision()] : [], total: query.definitionKind === 'TASK' ? 1 : 0 }))
    vi.mocked(Definitions.getDefinition).mockImplementation(async (id) => structuredClone(rows.get(id)!))
    vi.mocked(Definitions.createDefinition).mockImplementation(async (body) => { const id = 100 + rows.size; rows.set(id, { ...body, id, revisionNo: 1, revisionState: 'DRAFT', version: 1 }); return id })
    vi.mocked(Definitions.publishDefinition).mockImplementation(async (id) => { rows.get(id)!.revisionState = 'PUBLISHED'; return id })
    return rows
  }
  it('keeps S0 task-free, preserves historical tasks until explicit removal, and hides technical fields', async () => {
    const content = reactive(graph())
    content.tasks.push({ taskCode: 'OLD', name: '历史项目创建', stageCode: 'S0' })
    const mounted = mount(TemplateContentEditor, { content }, options); await tick()
    expect(textOf(mounted.root)).toContain('项目基本操作')
    expect(all(mounted.root, 'button').some((node) => textOf(node) === '新增任务')).toBe(false)
    expect(all(mounted.root, 'pre')).toHaveLength(0)
    expect(textOf(mounted.root)).not.toContain('BPM流程Key')
    expect(content.tasks).toHaveLength(1)
    await click(mounted.root, '移除历史任务'); expect(content.tasks).toHaveLength(0)
    expect(content.transitions).toHaveLength(1); mounted.app.unmount()
  })
  it('adds a real merged task from a named published selection without automatically adding attachments', async () => {
    setupContract()
    const content = reactive(graph())
    const mounted = mount(TemplateContentEditor, { content }, options); await tick()
    await chooseStage(mounted.root, 'S4'); await click(mounted.root, '新增任务')
    const selector = all(mounted.root, 'select').find((node) => node.props?.placeholder === '选择已发布任务修订')!
    expect(all(selector, 'option')[0].props?.label).toContain('施工计划制定与审批')
    await update(selector, 12)
    expect(content.tasks).toHaveLength(1)
    expect(content.tasks[0]).toMatchObject({ name: '施工计划制定与审批', stageCode: 'S4', permissionPolicyRevisionId: 6, completionRuleRevisionId: 7 })
    expect(content.deliverables).toEqual([]); expect(content.milestones).toEqual([])
    expect(all(mounted.root, 'pre')).toHaveLength(0)
    expect(textOf(mounted.root)).not.toContain('WORK_BINDING')
    expect(all(mounted.root, 'input').some((node) => String(node.props?.placeholder).includes('instanceId'))).toBe(false)
    mounted.app.unmount()
  })
  it('selects a page, retains edits after whole draft failure, resumes definitions and saves exact references last', async () => {
    setupContract()
    const data = template(); data.draftContent!.tasks.push({ taskCode: 'REAL', name: '需求分析', stageCode: 'S4', definitionRevisionId: 12, bindingConfig: 'old', workBindingTypeCode: 'TASK_NATIVE' })
    vi.mocked(Templates.getProjectTemplate).mockResolvedValue(data)
    const bigView = { ...view('9223372036854775807'), contextSchema: { required: ['project'] } }
    vi.mocked(getTemplateSelection).mockResolvedValue({ list: [{ templateName: '需求分析标准表单', currentPublishedRevisionId: 55, currentPublishedRevisionNo: 2 } as any], total: 1 })
    vi.mocked(getRevision).mockResolvedValue({ revisionId: 55, templateId: 50, revisionNo: 2, revisionVersion: 3, status: 'PUBLISHED' } as any)
    vi.mocked(Views.getBusinessViewPage).mockResolvedValue({ list: [bigView], total: 1 }); vi.mocked(Views.getBusinessView).mockResolvedValue(bigView)
    vi.mocked(Templates.updateProjectTemplate).mockRejectedValueOnce(new Error('草稿保存网络中断')).mockResolvedValueOnce(true)
    const mounted = mount(TemplatePage, {}, options); await tick(); await click(mounted.root, '编辑'); await chooseStage(mounted.root, 'S4')
    const selector = all(mounted.root, 'select').find((node) => node.props?.placeholder === '保留当前办理方式，或选择已发布页面 / 表单')!
    await update(selector, bigView.id)
    await update(all(mounted.root, 'select').find(node => node.props?.placeholder === '选择已发布需求分析表单')!, 55)
    expect(textOf(mounted.root)).toContain('完成规则待对接')
    expect(textOf(mounted.root)).toContain('需求分析')
    await click(mounted.root, '保存草稿')
    expect(Definitions.createDefinition).toHaveBeenCalledTimes(2)
    expect(data.draftContent!.tasks[0].bindingConfig).toBe('old')
    expect(all(mounted.root, 'select').find((node) => node.props?.placeholder === '保留当前办理方式，或选择已发布页面 / 表单')?.props?.['model-value']).toBe(bigView.id)
    await click(mounted.root, '保存草稿')
    expect(Definitions.createDefinition).toHaveBeenCalledTimes(2)
    expect(Templates.updateProjectTemplate).toHaveBeenCalledTimes(2)
    const saved = vi.mocked(Templates.updateProjectTemplate).mock.calls[1][1].content!.tasks[0]
    expect(saved).toMatchObject({ workBindingRevisionId: 104, definitionRevisionId: 105, permissionPolicyRevisionId: 6, completionRuleRevisionId: 7 })
    expect(JSON.parse(saved.bindingConfig!)).toMatchObject({ schemaVersion: 2, dynamicFormTemplateRevisionId: 55 })
    expect(vi.mocked(Definitions.publishDefinition).mock.invocationCallOrder.at(-1)!).toBeLessThan(vi.mocked(Templates.updateProjectTemplate).mock.invocationCallOrder[0])
    expect(Templates.publishProjectTemplate).not.toHaveBeenCalled(); mounted.app.unmount()
  })
  it('offers only real component registration with permission, lets users select forms by name', async () => {
    setupContract()
    const formComponent = { ...view(), viewSource: 'DYNAMIC_FORM' as const, componentKey: 'PLATFORM_DYNAMIC_FORM', ownerContext: 'PLATFORM', entityType: 'DYNAMIC_FORM_INSTANCE', contextSchema: { required: ['project', 'instanceId'] } }
    vi.mocked(Views.getBusinessViewPage).mockResolvedValue({ list: [], total: 0 }); vi.mocked(Views.getBusinessViewComponents).mockResolvedValue([formComponent])
    vi.mocked(getTemplateSelection).mockResolvedValue({ list: [{ templateName: '现场调查表', currentPublishedRevisionId: 55, currentPublishedRevisionNo: 2 } as any], total: 1 })
    const content = reactive(graph()); content.tasks.push({ taskCode: 'REAL', name: '现场调查', stageCode: 'S4', definitionRevisionId: 12 })
    const mounted = mount(TemplateContentEditor, { content }, options); await tick(); await chooseStage(mounted.root, 'S4')
    await click(mounted.root, '使用其他已接入页面或表单')
    await update(all(mounted.root, 'select').find((node) => node.props?.placeholder === '选择实际已接入的功能')!, 'PLATFORM_DYNAMIC_FORM@1')
    const forms = all(mounted.root, 'select').find((node) => node.props?.placeholder === '按名称选择已发布表单')!
    expect(all(forms, 'option')[0].props?.label).toContain('现场调查表')
    await update(forms, 55); await click(mounted.root, '用于此任务')
    expect(textOf(mounted.root)).toContain('业务表单')
    expect(all(mounted.root, 'option').find((node) => node.props?.value === 'CREATE_ON_FIRST_ACTION')?.props).toHaveProperty('disabled')
    expect(Views.createBusinessView).not.toHaveBeenCalled(); mounted.app.unmount()
    vi.mocked(hasPermission).mockImplementation((permissions) => !permissions.includes('pms:business-view:manage'))
    const denied = mount(TemplateContentEditor, { content }, options); await tick(); await chooseStage(denied.root, 'S4')
    expect(all(denied.root, 'button').some((node) => textOf(node) === '使用其他已接入页面或表单')).toBe(false)
    denied.app.unmount()
  })
})

describe('PM-03 management rejection and historical interactions', () => {
  it('does not publish an invalid definition or mutate published/disabled payloads', async () => {
    const row = { ...definition(), revisionState: 'DRAFT' as const }
    vi.mocked(Definitions.getDefinition).mockResolvedValue(row)
    vi.mocked(Definitions.validateDefinition).mockResolvedValue({ valid: false, issues: [{ field: 'payload.businessViewRevisionId', code: 'DISABLED', message: '引用视图已停用' }] })
    const mounted = mount(DefinitionLibrary, {}, options)
    await tick()
    await click(mounted.root, '查看 / 管理')
    await click(mounted.root, '发布定义')
    expect(Definitions.publishDefinition).not.toHaveBeenCalled()
    expect(textOf(mounted.root)).toContain('引用视图已停用')
    mounted.app.unmount()
  })
  it('keeps a failed publish as a visible failure, requires refresh after stale version, and never auto-retries', async () => {
    vi.mocked(Definitions.getDefinition).mockResolvedValue({ ...definition(), revisionState: 'DRAFT' })
    vi.mocked(Definitions.publishDefinition).mockRejectedValue({ status: 412, message: '版本冲突' })
    const mounted = mount(DefinitionLibrary, {}, options)
    await tick()
    await click(mounted.root, '查看 / 管理')
    await click(mounted.root, '发布定义')
    expect(Definitions.publishDefinition).toHaveBeenCalledWith(5, 3, expect.any(String))
    expect(Definitions.publishDefinition).toHaveBeenCalledTimes(1)
    expect(textOf(mounted.root)).toContain('未自动覆盖编辑内容')
    expect(all(mounted.root, 'button').find((node) => textOf(node) === '发布定义')?.props?.disabled).toBe(true)
    mounted.app.unmount()
  })
  it('saves definition drafts with exact version and retains the same idempotency key after transport failure', async () => {
    vi.mocked(Definitions.getDefinition).mockResolvedValue({ ...definition(), revisionState: 'DRAFT' })
    vi.mocked(Definitions.updateDefinition).mockRejectedValueOnce(new Error('网络中断')).mockResolvedValueOnce(5)
    const mounted = mount(DefinitionLibrary, {}, options)
    await tick()
    await click(mounted.root, '查看 / 管理')
    await click(mounted.root, '保存定义草稿')
    expect(textOf(mounted.root)).toContain('网络中断')
    const first = vi.mocked(Definitions.updateDefinition).mock.calls[0]
    expect(first.slice(0, 2)).toEqual([5, 3])
    await click(mounted.root, '保存定义草稿')
    const second = vi.mocked(Definitions.updateDefinition).mock.calls[1]
    expect(second[3]).toBe(first[3])
    expect(second[2].payload).toEqual({ bindingType: 'TASK_NATIVE', instanceResolutionStrategy: 'REFERENCE_EXISTING', contextMapping: {} })
    mounted.app.unmount()
  })
  it('creates a typed definition before validation and disables only a published precise revision', async () => {
    vi.mocked(Definitions.createDefinition).mockResolvedValue(10)
    vi.mocked(Definitions.disableDefinition).mockResolvedValue(10)
    const mounted = mount(DefinitionLibrary, {}, options)
    await tick()
    await click(mounted.root, '新增定义')
    const selects = all(mounted.root, 'select')
    await update(selects[2], 'PERMISSION_POLICY')
    const inputs = all(mounted.root, 'input')
    await update(inputs.find((node) => node.props?.placeholder === '创建后不可修改；复制保留身份')!, 'POLICY')
    const actions = all(mounted.root, 'select').find((node) => node.props?.placeholder === '输入既有Owner操作编码并确认')!
    await update(actions, ['VIEW'])
    await click(mounted.root, '保存定义草稿')
    expect(Definitions.createDefinition).toHaveBeenCalledWith({ definitionKind: 'PERMISSION_POLICY', definitionCode: 'POLICY', schemaVersion: 1, payload: { requiredActions: ['VIEW'] }, references: [] }, expect.any(String))
    await click(mounted.root, '停用定义')
    expect(Definitions.disableDefinition).toHaveBeenCalledWith(10, 3, expect.any(String))
    mounted.app.unmount()
  })
  it('copies a revision with no body and reloads the new precise revision', async () => {
    vi.mocked(Definitions.copyDefinition).mockResolvedValue(9)
    const mounted = mount(DefinitionLibrary, {}, options)
    await tick()
    await click(mounted.root, '查看 / 管理')
    expect(all(mounted.root, 'button').find((node) => textOf(node) === '保存定义草稿')).toBeUndefined()
    await click(mounted.root, '复制下一修订')
    expect(Definitions.copyDefinition).toHaveBeenCalledWith(5, 3, expect.any(String))
    expect(Definitions.getDefinition).toHaveBeenCalledWith(9)
    mounted.app.unmount()
  })
  it('saves concurrent identity and draft edits together rather than trapping or discarding either change', async () => {
    const mounted = mount(TemplatePage, {}, options)
    await tick()
    await click(mounted.root, '编辑')
    const title = all(mounted.root, 'input').find((node) => node.props?.modelValue === '模板')!
    await update(title, '修改后的模板')
    await update(all(mounted.root, 'select').find((node) => node.props?.placeholder === '不限')!, 'DIRECT')
    await click(mounted.root, '保存草稿')
    expect(Templates.updateProjectTemplate).toHaveBeenCalledWith(1, expect.objectContaining({ name: '修改后的模板', content: expect.objectContaining({ signingMethod: 'DIRECT', tasks: [] }) }))
    mounted.app.unmount()
  })
  it('blocks template publish after precheck passes but server dependency becomes invalid', async () => {
    vi.mocked(Templates.publishProjectTemplate).mockRejectedValue({ message: '规则修订已失效' })
    const mounted = mount(TemplatePage, {}, options)
    await tick()
    expect(all(mounted.root, 'button').some((node) => textOf(node) === '发布')).toBe(false)
    await click(mounted.root, '编辑')
    await click(mounted.root, '发布')
    expect(Templates.validateProjectTemplate).toHaveBeenCalledWith(1)
    expect(Templates.publishProjectTemplate).toHaveBeenCalledTimes(1)
    expect(textOf(mounted.root)).toContain('规则修订已失效')
    expect(Templates.updateProjectTemplate).not.toHaveBeenCalled()
    mounted.app.unmount()
  })
  it('reads historical missing-graph snapshot without modifying it or triggering publish', async () => {
    const historical = { ...emptyContent(), processDefinitionVersion: 'legacy-v', stages: [{ stageCode: 'S0', name: '历史开始', sortOrder: 20 }] }
    vi.mocked(Templates.getProjectTemplateRevision).mockResolvedValue({ ...template().revisions[0], processDefinitionKey: 'flow', processDefinitionVersion: 'legacy-v', content: historical })
    const mounted = mount(TemplatePage, {}, options)
    await tick()
    await click(mounted.root, '编辑')
    await click(mounted.root, '查看快照')
    expect(textOf(mounted.root)).toContain('历史版本 legacy-v，仅展示')
    for (const collapse of all(mounted.root, 'div').filter((node) => node.props?.['onUpdate:modelValue'])) await update(collapse, ['flow'])
    expect(historical).not.toHaveProperty('transitions')
    expect(Templates.publishProjectTemplate).not.toHaveBeenCalled()
    expect(Templates.updateProjectTemplate).not.toHaveBeenCalled()
    mounted.app.unmount()
  })
})
