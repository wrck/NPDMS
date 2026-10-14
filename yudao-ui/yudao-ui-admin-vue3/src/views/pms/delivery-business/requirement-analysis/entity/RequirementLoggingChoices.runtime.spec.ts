import { describe, expect, it } from 'vitest'
import RequirementLoggingChoices from './RequirementLoggingChoices.vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

describe('logging choices text storage', () => {
  const options = ['UMC', '第三方日志平台', '本机硬盘', '不留存', '其他']
  it('preserves existing free text when selecting retention options', () => {
    let result = ''
    const mounted = mount(RequirementLoggingChoices, { modelValue: '原有说明', options,
      'onUpdate:modelValue': (value: string) => { result = value } }, { ElCheckboxGroup: passthrough, ElCheckbox: passthrough })
    try {
      const state = (mounted.vm as any).$.setupState
      state.updateSelection(['UMC', '本机硬盘'])
      expect(result).toBe('UMC\n本机硬盘\n原有说明')
    } finally { mounted.app.unmount() }
  })
  it('reopens multiple selections and blocks changes in readonly mode', () => {
    let changed = false
    const mounted = mount(RequirementLoggingChoices, { modelValue: 'UMC\n其他', options, disabled: true,
      'onUpdate:modelValue': () => { changed = true } }, { ElCheckboxGroup: passthrough, ElCheckbox: passthrough })
    try {
      const state = (mounted.vm as any).$.setupState
      expect(state.selected).toEqual(['UMC', '其他'])
      state.updateSelection([])
      expect(changed).toBe(false)
    } finally { mounted.app.unmount() }
  })
})
