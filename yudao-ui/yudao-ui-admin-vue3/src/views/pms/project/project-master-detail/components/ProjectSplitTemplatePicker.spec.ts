import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProjectSplitTemplatePicker from './ProjectSplitTemplatePicker.vue'
import { getChildTemplateOptions } from '@/api/pms/project/project-splits/workspace'
import { mount, passthrough, textOf } from '../../../platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/project/project-splits/workspace', () => ({ getChildTemplateOptions: vi.fn() }))
const flush = async () => { for (let index = 0; index < 8; index++) { await Promise.resolve(); await nextTick() } }
const option = (revisionId: string, name: string) => ({ revisionId, name, revisionNo: 3, templateId: revisionId, selectable: true, recommended: true })
const components = { ElDialog: passthrough, ElPagination: passthrough, ElInput: passthrough }

describe('restored split template selection', () => {
  beforeEach(() => vi.resetAllMocks())

  it('restores the exact revision label across pages without changing the selection', async () => {
    vi.mocked(getChildTemplateOptions).mockResolvedValueOnce({ list: [option('other', '其他模板')], total: 21 })
      .mockResolvedValueOnce({ list: [option('9007199254740993', '已保存的模板')], total: 21 })
    const change = vi.fn()
    const view = mount(ProjectSplitTemplatePicker, { parentProjectId: 100, revisionId: '9007199254740993', departmentCode: 'HZ', onChange: change }, components)
    await flush()
    expect(textOf(view.root)).toContain('已保存的模板')
    expect(textOf(view.root)).toContain('发布版本 v3')
    expect(getChildTemplateOptions).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 2, departmentCode: 'HZ' }))
    expect(change).not.toHaveBeenCalled()
    view.app.unmount()
  })

  it('does not replace the current label with a late response for a previous revision', async () => {
    let resolveOld!: (value: { list: ReturnType<typeof option>[]; total: number }) => void
    vi.mocked(getChildTemplateOptions).mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve }))
      .mockResolvedValueOnce({ list: [option('new', '新模板')], total: 1 })
    const revisionId = ref('old')
    const view = mount(defineComponent({ setup: () => () => h(ProjectSplitTemplatePicker, { parentProjectId: 100, revisionId: revisionId.value }) }), {}, components)
    revisionId.value = 'new'; await flush()
    resolveOld({ list: [option('old', '旧模板')], total: 1 }); await flush()
    expect(textOf(view.root)).toContain('新模板')
    expect(textOf(view.root)).not.toContain('旧模板')
    view.app.unmount()
  })
})
