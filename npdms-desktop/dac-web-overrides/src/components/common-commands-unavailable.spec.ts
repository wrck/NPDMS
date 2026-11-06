import { createHash } from 'node:crypto'
import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ScriptArtifactEditor from './ScriptArtifactEditor.vue'
import { commonCommands, commonCommandsUnavailableMessage } from '@/data/common-commands'
import type { ScriptDraft } from '@/types/collection'

const wrappers: VueWrapper[] = []
function render(content = '') {
  const model: ScriptDraft = {
    source: 'LOCAL_MANAGED', key: '', version: 'v1', content,
    sha256: '', policy: 'EXECUTION_ONLY', parserType: 'NONE', parserConfig: ''
  }
  const wrapper = mount(ScriptArtifactEditor, {
    props: { modelValue: model }, global: { plugins: [ElementPlus] }
  })
  wrappers.push(wrapper)
  return { wrapper, model }
}
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('unavailable common-command catalogue', () => {
  it('contains no invented command entries', () => {
    expect(commonCommands).toEqual([])
    expect(Object.isFrozen(commonCommands)).toBe(true)
  })

  it('explains missing presets without injecting script content or identity', async () => {
    const { wrapper, model } = render()
    await flushPromises()
    expect(wrapper.find('[role="note"]').text()).toBe(commonCommandsUnavailableMessage)
    expect(wrapper.findAll('.common-commands__button')).toHaveLength(0)
    expect(model.content).toBe('')
    expect(model.key).toBe('')
    expect(model.policy).toBe('EXECUTION_ONLY')
  })

  it('preserves manual input and computes its exact digest', async () => {
    const { wrapper, model } = render()
    const content = 'operator-reviewed input\nsecond input line'
    await wrapper.find('textarea').setValue(content)
    await flushPromises()
    expect(model.content).toBe(content)
    expect(model.key).toBe('')
    expect(model.sha256).toBe(createHash('sha256').update(content).digest('hex'))
    expect(model.policy).toBe('EXECUTION_ONLY')
  })

  it('preserves incoming content when the editor is collapsed', async () => {
    const { wrapper, model } = render('operator-provided input')
    await wrapper.setProps({ collapsed: true })
    await flushPromises()
    expect(wrapper.find('[role="note"]').exists()).toBe(false)
    expect(model.content).toBe('operator-provided input')
    expect(model.key).toBe('')
  })
})
