import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
vi.mock('@/api/pms/project/projects', () => ({ getProject: vi.fn() }))
import { getProject } from '@/api/pms/project/projects'
import SurveyProjectEndDate from './SurveyProjectEndDate.vue'
import { mount, findByTestId, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

describe('survey supplies the project finish date', () => {
  it('reads only the project concurrency context and keeps the survey requirement as input', async () => {
    vi.mocked(getProject).mockResolvedValue({ id: 7, version: 3, projectEndDate: undefined })
    const version = vi.fn(), changed = vi.fn(), update = vi.fn()
    const picker = defineComponent({
      emits: ['update:modelValue'],
      setup(_, { emit }) { return () => h('button', { 'data-testid': 'choose-end', onClick: () => emit('update:modelValue', '2026-12-31') }, 'choose') }
    })
    const mounted = mount(SurveyProjectEndDate, { modelValue: '2026-11-30', getSurvey: () => ({ projectId: 7 }),
      updateProjectVersion: version, markDateChanged: changed, 'onUpdate:modelValue': update }, { ElDatePicker: picker })
    await nextTick(); await nextTick()
    expect(version).toHaveBeenCalledWith(3, true)
    expect(update).not.toHaveBeenCalled()
    expect(textOf(mounted.root)).toContain('保存到项目结束日期')
    expect(textOf(mounted.root)).not.toContain('带入当前结束日期')
    ;(findByTestId(mounted.root, 'choose-end')!.props!.onClick as Function)()
    expect(changed).toHaveBeenCalledOnce()
    expect(update).toHaveBeenCalledWith('2026-12-31')
    mounted.app.unmount()
  })
})
