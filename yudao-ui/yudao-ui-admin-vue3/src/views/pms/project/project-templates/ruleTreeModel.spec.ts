import { describe, expect, it } from 'vitest'
import { appendNode, decodeTree, encodeTree, moveNode, canMoveNode, isGroup } from './ruleTreeModel'

describe('condition tree native query operations', () => {
  it('does not grow nested groups or duplicate ids on repeated NOT round trips', () => {
    const tree = decodeTree({
      id: 'negated',
      operator: 'NOT',
      rules: [{ id: 'leaf', predicate: 'CONSTANT', parameters: { value: true } }]
    })
    let encoded = encodeTree(tree)
    const initial = JSON.stringify(encoded)
    for (let i = 0; i < 10; i++) encoded = encodeTree(decodeTree(encoded))
    expect(JSON.stringify(encoded)).toBe(initial)
  })
  it('preserves literal values and boolean meaning without converting a table', () => {
    const expression = {
      operator: 'ANY',
      rules: [
        {
          predicate: 'FIELD',
          parameters: {
            fieldCode: 'project.projectName',
            valueType: 'TEXT',
            operator: '=',
            value: "O'Reilly 中文"
          }
        },
        { predicate: 'CONSTANT', parameters: { value: false } }
      ]
    }
    const result = encodeTree(decodeTree(expression))
    expect(result.operator).toBe('ANY')
    expect(JSON.stringify(result)).toContain("O'Reilly 中文")
  })
  it('moves rules across nested groups through the mature query core', () => {
    let tree = decodeTree({
      id: 'root-group',
      operator: 'ALL',
      rules: [
        { id: 'one', predicate: 'CONSTANT', parameters: { value: true } },
        {
          id: 'two',
          operator: 'ANY',
          rules: [{ id: 'three', predicate: 'CONSTANT', parameters: { value: false } }]
        }
      ]
    })
    tree = moveNode(tree, 'one', 'two', 1)
    expect(tree.rules).toHaveLength(1)
    const group = tree.rules[0]
    expect(isGroup(group) && group.rules.map((rule) => rule.id)).toEqual(['three', 'one'])
    expect(canMoveNode(tree, 'two', 'two')).toBe(false)
  })
  it('creates independent ids for new groups and conditions', () => {
    const root = decodeTree({ predicate: 'CONSTANT', parameters: { value: false } })
    const next = appendNode(root, 'root', true)
    expect(next.rules).toHaveLength(2)
    expect(root.rules).toHaveLength(1)
    expect(next.rules[0].id).not.toBe(next.rules[1].id)
  })
})
