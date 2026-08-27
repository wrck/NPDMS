import { createRenderer, defineComponent, h, type Component } from 'vue'

export interface TestNode {
  type: string
  text?: string
  props?: Record<string, unknown>
  style?: Record<string, unknown>
  children: TestNode[]
  parent?: TestNode
}

export const renderer = createRenderer<TestNode, TestNode>({
  patchProp: (node, key, _previous, value) => {
    node.props ||= {}
    node.props[key] = value
  },
  insert: (child, parent) => {
    child.parent = parent
    parent.children.push(child)
  },
  remove: (child) => {
    if (child.parent) child.parent.children = child.parent.children.filter((item) => item !== child)
  },
  createElement: (type) => ({ type, style: {}, children: [] }),
  createText: (text) => ({ type: '#text', text, children: [] }),
  createComment: (text) => ({ type: '#comment', text, children: [] }),
  setText: (node, text) => {
    node.text = text
  },
  setElementText: (node, text) => {
    node.children = [{ type: '#text', text, children: [] }]
  },
  parentNode: (node) => node.parent || null,
  nextSibling: (node) => {
    if (!node?.parent) return null
    const index = node.parent.children.indexOf(node)
    return index >= 0 ? (node.parent.children[index + 1] ?? null) : null
  },
  querySelector: () => null,
  setScopeId: () => undefined,
  cloneNode: (node) => ({ ...node, children: [...node.children] }),
  insertStaticContent: (content, parent) => {
    const node: TestNode = { type: '#static', text: content, children: [], parent }
    parent.children.push(node)
    return [node, node]
  }
})

export const passthrough = defineComponent({
  inheritAttrs: false,
  setup(_, { attrs, slots }) {
    return () =>
      h('section', attrs, [
        typeof attrs.title === 'string' ? attrs.title : undefined,
        typeof attrs.description === 'string' ? attrs.description : undefined,
        slots.default?.()
      ])
  }
})

export const tableColumn = defineComponent({ setup: () => () => h('span') })

export const button = defineComponent({
  inheritAttrs: false,
  setup(_, { attrs, slots }) {
    return () => h('button', attrs, slots.default?.())
  }
})

export const textOf = (node: TestNode): string =>
  `${node.text || ''}${node.children.map(textOf).join('')}`

export const findByTestId = (node: TestNode, id: string): TestNode | undefined => {
  if (node.props?.['data-testid'] === id) return node
  for (const child of node.children) {
    const found = findByTestId(child, id)
    if (found) return found
  }
}

export const mount = (
  component: Component,
  props: Record<string, unknown> = {},
  components: Record<string, Component> = {}
) => {
  const root: TestNode = { type: 'root', children: [] }
  const app = renderer.createApp(component, props)
  app.provide(Symbol.for('v-scx'), { modules: new Set<string>() })
  for (const name of [
    'ElDrawer',
    'ElAlert',
    'ElTag',
    'ElEmpty',
    'ElCard',
    'ElForm',
    'ElFormItem',
    'ContentWrap',
    'Pagination',
    'Dialog',
    'Icon'
  ])
    if (!components[name]) app.component(name, passthrough)
  app.component('ElButton', button)
  app.directive('hasPermi', {})
  app.directive('loading', {})
  Object.entries(components).forEach(([name, value]) => app.component(name, value))
  const vm = app.mount(root)
  return { root, app, vm }
}
