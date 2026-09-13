import { add, getPathOfID, move, remove, update } from '@react-querybuilder/core'
import type { RuleGroupType, RuleType } from '@react-querybuilder/core'
import type { JsonObject } from '@/api/pms/project/project-templates'

export type RuleTreeNode = RuleGroupType | RuleType
const options = { freeze: false }
const key = () => `condition_${crypto.randomUUID()}`
const object = (value: unknown): JsonObject =>
  value !== null && typeof value === 'object' && !Array.isArray(value) ? (value as JsonObject) : {}

export const isGroup = (node: RuleTreeNode): node is RuleGroupType => 'rules' in node
export const blankRule = (): RuleType => ({
  id: key(),
  field: 'CONSTANT',
  operator: '=',
  value: { value: false }
})
export const blankGroup = (): RuleGroupType => ({
  id: key(),
  combinator: 'and',
  not: false,
  rules: [blankRule()]
})

export function decodeTree(source: JsonObject): RuleGroupType {
  const node = decodeNode(source)
  return isGroup(node) ? node : { id: 'root', combinator: 'and', rules: [node] }
}

function decodeNode(source: JsonObject): RuleTreeNode {
  const id = typeof source.id === 'string' ? source.id : key()
  if (source.operator) {
    const children = Array.isArray(source.rules)
      ? source.rules.map((value) => decodeNode(object(value)))
      : []
    if (source.operator === 'NOT' && children.length === 1 && isGroup(children[0])) {
      const child = children[0]
      return { ...child, not: !child.not }
    }
    return {
      id,
      combinator: source.operator === 'ANY' ? 'or' : 'and',
      not: source.operator === 'NOT',
      rules: children
    }
  }
  return {
    id,
    field: typeof source.predicate === 'string' ? source.predicate : '',
    operator: '=',
    value: object(source.parameters)
  }
}

export function encodeTree(group: RuleGroupType): JsonObject {
  if (group.id === 'root' && !group.not && group.rules.length === 1)
    return encodeNode(group.rules[0])
  return encodeNode(group)
}

function encodeNode(node: RuleTreeNode): JsonObject {
  if (!isGroup(node)) return { id: node.id, predicate: node.field, parameters: object(node.value) }
  const group: JsonObject = {
    id: node.id,
    operator: node.combinator === 'or' ? 'ANY' : 'ALL',
    rules: node.rules.map(encodeNode)
  }
  return node.not ? { operator: 'NOT', rules: [group] } : group
}

export const appendNode = (tree: RuleGroupType, parent: string, grouped: boolean) =>
  add(tree, grouped ? blankGroup() : blankRule(), parent, options)
export const removeNode = (tree: RuleGroupType, id: string) => remove(tree, id, options)
export const changeGroup = (
  tree: RuleGroupType,
  id: string,
  values: { combinator?: string; not?: boolean }
) => update(tree, values, id, options)
export const changePredicate = (
  tree: RuleGroupType,
  id: string,
  predicate: string,
  parameters: JsonObject
) =>
  update(tree, { field: predicate, operator: '=', value: parameters }, id, {
    ...options,
    resetOnFieldChange: false
  })

export function canMoveNode(tree: RuleGroupType, id: string, parent: string): boolean {
  const source = getPathOfID(id, tree)
  const destination = getPathOfID(parent, tree)
  if (!source || !destination || !source.length) return false
  return !(
    destination.length >= source.length && source.every((part, i) => destination[i] === part)
  )
}

export function moveNode(
  tree: RuleGroupType,
  id: string,
  parent: string,
  index: number
): RuleGroupType {
  if (!canMoveNode(tree, id, parent)) return tree
  const destination = getPathOfID(parent, tree)
  return destination ? move(tree, id, [...destination, index], options) : tree
}
