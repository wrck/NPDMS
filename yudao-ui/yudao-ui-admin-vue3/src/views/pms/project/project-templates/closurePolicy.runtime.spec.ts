import { defineComponent, h, nextTick, reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getSimpleUserList } from '@/api/system/user'
import type { TemplateDesignerDocument } from '@/api/pms/project/project-templates'
import TemplateClosurePolicyEditor from './TemplateClosurePolicyEditor.vue'
import { cloneContent, emptyContent } from './editorModel'
import { mount, textOf, type TestNode } from '../../platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/api/system/user', () => ({ getSimpleUserList: vi.fn() }))
const control = (name: string) => defineComponent({ inheritAttrs: false, setup(_, { attrs, slots }) { return () => h(name, attrs, slots.default?.()) } })
const controls = { ElCheckbox: control('checkbox'), ElSelect: control('select'), ElOption: control('option') }
const all = (node: TestNode, type: string): TestNode[] => [...(node.type === type ? [node] : []), ...node.children.flatMap((child) => all(child, type))]
const change = async (node: TestNode, value: unknown) => { (node.props!['onUpdate:modelValue'] as Function)(value); await nextTick() }
const setup = (content = reactive(emptyContent()), readonly = false) => ({ content, ...mount(TemplateClosurePolicyEditor, { content, readonly }, controls) })
beforeEach(() => vi.clearAllMocks())

describe('Designer V2 minimal NORMAL closure policy', () => {
  it('keeps an unconfigured Designer without closure capability', () => {
    const { content, root } = setup()
    expect(content.closurePolicy).toBeUndefined()
    expect(textOf(root)).toContain('未启用不代表条件满足')
    expect(getSimpleUserList).not.toHaveBeenCalled()
  })

  it('writes exactly the approved fixed rule into DesignerDocument', async () => {
    const { content, root } = setup()
    await change(all(root, 'checkbox')[0], true)
    expect(content.closurePolicy).toEqual({
      closureType: 'NORMAL', ruleRevision: 1, requireTerminalStage: true,
      requireAllTasksDone: true, revalidateBusinessFacts: true,
      processDefinitionKey: 'PMS_MINIMAL_NORMAL_CLOSURE', reviewerUserId: ''
    })
    expect(textOf(root)).toContain('当前主责服务经理')
    expect(textOf(root)).toContain('显式材料审核人')
  })

  it('preserves decimal string reviewer IDs through Designer cloning', async () => {
    const { content, root } = setup()
    await change(all(root, 'checkbox')[0], true)
    await change(all(root, 'select')[0], '9223372036854775807')
    const copy = cloneContent(content)
    expect(copy.closurePolicy?.reviewerUserId).toBe('9223372036854775807')
    expect(copy.stages).toEqual([])
    expect(copy.closurePolicy).not.toBe(content.closurePolicy)
  })

  it('disabling writes explicit null and reenabling does not reuse the reviewer', async () => {
    const { content, root } = setup()
    await change(all(root, 'checkbox')[0], true)
    await change(all(root, 'select')[0], 27)
    await change(all(root, 'checkbox')[0], false)
    expect(content.closurePolicy).toBeNull()
    await change(all(root, 'checkbox')[0], true)
    expect(content.closurePolicy?.reviewerUserId).toBe('')
  })

  it('uses user identities only for selection and excludes disabled users', async () => {
    vi.mocked(getSimpleUserList).mockResolvedValue([
      { id: 27, nickname: '材料审核候选', status: 0 }, { id: 28, nickname: '停用人员', status: 1 }
    ] as Awaited<ReturnType<typeof getSimpleUserList>>)
    const { root, content } = setup()
    await change(all(root, 'checkbox')[0], true)
    await (all(root, 'select')[0].props!.onVisibleChange as Function)(true)
    await nextTick()
    expect(all(root, 'option').map((option) => option.props!.value)).toEqual([27])
    expect(content.closurePolicy?.reviewerUserId).toBe('')
  })

  it('retains the selected reviewer when lookup fails', async () => {
    vi.mocked(getSimpleUserList).mockRejectedValue(new Error('服务不可用'))
    const { root, content } = setup()
    await change(all(root, 'checkbox')[0], true)
    await change(all(root, 'select')[0], '9007199254740993')
    await (all(root, 'select')[0].props!.onVisibleChange as Function)(true)
    await nextTick()
    expect(content.closurePolicy?.reviewerUserId).toBe('9007199254740993')
    expect(textOf(root)).toContain('已选审核人保持不变')
  })

  it('read-only Designer cannot be changed by emitted control events', async () => {
    const editable = setup()
    await change(all(editable.root, 'checkbox')[0], true)
    await change(all(editable.root, 'select')[0], '27')
    editable.app.unmount()
    const content = reactive(cloneContent(editable.content)) as TemplateDesignerDocument
    const before = JSON.stringify(content)
    const { root } = setup(content, true)
    await change(all(root, 'checkbox')[0], false)
    await change(all(root, 'select')[0], 28)
    expect(JSON.stringify(content)).toBe(before)
    expect(all(root, 'checkbox')[0].props!.disabled).toBe(true)
  })
})
