import { describe, expect, it } from 'vitest'
import { closureDiagramOrder } from './normalClosureDiagram'
describe('normal closure display layout', () => {
  const nodes = [
    { id: 'end', type: 'endEvent' },
    { id: 'material', type: 'userTask' },
    { id: 'start', type: 'startEvent' },
    { id: 'manager', type: 'userTask' }
  ]
  const flows = [
    { id: 'a', source: 'start', target: 'manager' },
    { id: 'b', source: 'manager', target: 'material' },
    { id: 'c', source: 'material', target: 'end' }
  ]
  it('uses actual sequence flows rather than XML element order', () => {
    expect(closureDiagramOrder(nodes, flows).map((item) => item.id)).toEqual([
      'start',
      'manager',
      'material',
      'end'
    ])
  })
  it('does not invent a path for branches, cycles, disconnected or unsupported nodes', () => {
    for (const value of [
      flows.slice(0, 2),
      [...flows, { id: 'x', source: 'start', target: 'end' }],
      [flows[0], { ...flows[1], target: 'start' }, flows[2]]
    ])
      expect(closureDiagramOrder(nodes, value)).toEqual([])
    expect(closureDiagramOrder([...nodes, { id: 'unknown', type: 'serviceTask' }], flows)).toEqual(
      []
    )
  })
})
