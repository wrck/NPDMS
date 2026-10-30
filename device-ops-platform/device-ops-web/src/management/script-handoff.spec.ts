import { describe, expect, it } from 'vitest'
import { consumeScript, offerScript, revokeScript } from './script-handoff'

describe('explicit in-memory script handoff', () => {
  it('revokes only its own failed navigation offer', () => {
    const first = offerScript({ content: 'first', scriptKey: 's', scriptVersion: '1' })
    const second = offerScript({ content: 'second', scriptKey: 's', scriptVersion: '2' })
    revokeScript(first)
    expect(consumeScript()?.content).toBe('second')
    const third = offerScript({ content: 'third', scriptKey: 's', scriptVersion: '3' })
    revokeScript(third)
    expect(consumeScript()).toBeUndefined()
    revokeScript(second)
  })
  it('consumes a copy exactly once without persisting script content', () => {
    const script = { content: 'show version', scriptKey: 'inspect', scriptVersion: '1' }
    offerScript(script)
    script.content = 'mutated'
    expect(consumeScript()).toEqual({ content: 'show version', scriptKey: 'inspect', scriptVersion: '1' })
    expect(consumeScript()).toBeUndefined()
    expect(localStorage.length).toBe(0)
  })
})
