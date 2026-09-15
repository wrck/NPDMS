import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import DecisionTableEditor from './DecisionTableEditor.vue'
import { newDecisionTable } from './decisionTableModel'
import { mount, passthrough, textOf, type TestNode } from '../../platform/dynamic-form/components/runtimeTestHarness'

interface Instance {
  changed: () => void
  open: ReturnType<typeof vi.fn>
  destroy: ReturnType<typeof vi.fn>
}
const dmn = vi.hoisted(() => ({
  instances: [] as Instance[],
  importXML: vi.fn<(xml: string) => Promise<void>>(),
  saveXML: vi.fn<() => Promise<{ xml: string }>>()
}))
vi.mock('@/api/pms/project/project-templates/rules', () => ({ getRuleFields: async () => [] }))
vi.mock('dmn-js/lib/Modeler', () => ({ default: class {
  changed = () => {}
  open = vi.fn(async () => undefined)
  destroy = vi.fn()
  constructor() { dmn.instances.push(this) }
  importXML(xml: string) { return dmn.importXML(xml) }
  saveXML() { return dmn.saveXML() }
  getViews() { return [{ type: 'decisionTable', element: { id: 'decision', decisionLogic: {
    input: [{ label: '输入', inputExpression: { text: 'inputValue' } }],
    output: [{ name: 'result', typeRef: 'boolean' }]
  } } }] }
  on(_event: string, listener: (event: { viewer: { on: (event: string, callback: () => void) => void } }) => void) {
    listener({ viewer: { on: (_event, callback) => { this.changed = callback } } })
  }
} }))
vi.mock('dmn-js/lib/NavigatedViewer', async () => await import('dmn-js/lib/Modeler'))
const tick = async () => { for (let i = 0; i < 24; i++) { await Promise.resolve(); await nextTick() } }
const find = (node: TestNode): TestNode | undefined =>
  typeof node.props?.['onUpdate:modelValue'] === 'function' ? node : node.children.map(find).find(Boolean)
const setup = async (readonly = false) => {
  const state = reactive({ model: newDecisionTable(), readonly })
  const editor = ref<InstanceType<typeof DecisionTableEditor>>()
  const update = vi.fn(value => { state.model = value })
  const outputs = vi.fn()
  const page = mount(defineComponent({ setup: () => () => h(DecisionTableEditor, {
    ref: editor, modelValue: state.model, readonly: state.readonly,
    'onUpdate:modelValue': update, onOutputs: outputs
  }) }), {}, { ElSelect: passthrough, ElOption: passthrough })
  await tick()
  return { ...page, state, editor, update, outputs }
}
beforeEach(() => {
  dmn.instances.length = 0
  dmn.importXML.mockReset().mockResolvedValue(undefined)
  dmn.saveXML.mockReset().mockResolvedValue({ xml: 'edited-xml' })
})

it('does not save or change field bindings while viewing a frozen table', async () => {
  const page = await setup(true)
  try {
    await page.editor.value!.flush()
    ;(find(page.root)!.props!['onUpdate:modelValue'] as (field: string) => void)('project.type')
    dmn.instances[0].changed(); await tick()
    expect(dmn.saveXML).not.toHaveBeenCalled()
    expect(page.update).not.toHaveBeenCalled()
    expect(page.outputs).toHaveBeenCalledWith([{ name: 'result', type: 'boolean' }])
  } finally { page.app.unmount() }
})

it('retains the manager after its own XML and binding updates', async () => {
  const page = await setup()
  try {
    await page.editor.value!.flush(); await tick()
    expect(page.state.model.xml).toBe('edited-xml')
    ;(find(page.root)!.props!['onUpdate:modelValue'] as (field: string) => void)('project.type')
    await tick()
    expect(page.state.model.inputFields.inputValue).toBe('project.type')
    expect(dmn.instances).toHaveLength(1)
    expect(dmn.instances[0].destroy).not.toHaveBeenCalled()
  } finally { page.app.unmount() }
})

it.each(['replace', 'readonly', 'unmount'] as const)('discards queued XML writes after %s', async change => {
  const page = await setup()
  let complete!: (value: { xml: string }) => void
  dmn.saveXML.mockImplementationOnce(() => new Promise(resolve => { complete = resolve }))
  try {
    dmn.instances[0].changed(); dmn.instances[0].changed(); await tick()
    expect(dmn.saveXML).toHaveBeenCalledTimes(1)
    const saving = page.editor.value!.flush()
    if (change === 'replace') page.state.model = newDecisionTable()
    if (change === 'readonly') page.state.readonly = true
    if (change === 'unmount') page.app.unmount()
    await tick()
    complete({ xml: 'old-unsaved-content' }); await saving; await tick()
    expect(page.update).not.toHaveBeenCalled()
    expect(dmn.saveXML).toHaveBeenCalledTimes(1)
    expect(page.state.model.xml).not.toBe('old-unsaved-content')
  } finally { if (change !== 'unmount') page.app.unmount() }
})

it.each(['resolve', 'reject'] as const)('ignores an older import that finishes with %s after table replacement', async outcome => {
  let resolve!: () => void, reject!: (error: Error) => void
  dmn.importXML.mockImplementationOnce(() => new Promise<void>((yes, no) => { resolve = yes; reject = no }))
  const page = await setup()
  try {
    const first = dmn.instances[0]
    page.state.model = newDecisionTable(); await tick()
    expect(dmn.instances).toHaveLength(2)
    if (outcome === 'resolve') resolve()
    else reject(new Error('old import failed'))
    await tick()
    expect(first.destroy).toHaveBeenCalledTimes(1)
    expect(first.open).not.toHaveBeenCalled()
    expect(dmn.instances[1].open).toHaveBeenCalledTimes(1)
    expect(page.outputs).toHaveBeenCalledTimes(1)
    expect(textOf(page.root)).not.toContain('old import failed')
  } finally { page.app.unmount() }
})
