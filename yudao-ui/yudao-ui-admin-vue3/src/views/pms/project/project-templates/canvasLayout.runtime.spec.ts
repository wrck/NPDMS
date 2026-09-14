import { defineComponent, h, nextTick, reactive } from 'vue'
import { afterEach, expect, it, vi } from 'vitest'
import TemplateFlowCanvas, { type DeliveryCanvasNode } from './TemplateFlowCanvas.vue'
import {
  mount,
  textOf,
  type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const runtime = vi.hoisted(() => ({ instances: [] as any[] }))
// Keep the real official Dagre plugin/algorithm; replace only LogicFlow's DOM renderer.
vi.mock('@logicflow/core', () => ({
  default: class {
    extension: Record<string, any> = {}
    events: Record<string, Function> = {}
    data = { nodes: [] as any[], edges: [] as any[] }
    graphModel = {
      nodes: [] as any[],
      edges: [] as any[],
      gridSize: 10,
      getNodeModelById: (id: string) => this.getNodeModelById(id)
    }
    constructor(public options: any) {
      for (const Plugin of options.plugins) this.extension[Plugin.pluginName] = new Plugin()
      runtime.instances.push(this)
    }
    render(data: any) {
      this.data = structuredClone(data)
      this.graphModel.nodes = this.data.nodes.map((node) => ({
        ...node,
        width: node.properties?.width ?? 100,
        height: node.properties?.height ?? 80,
        rules: [],
        getConnectedTargetRules() {
          return this.rules
        },
        getData: () => structuredClone(node)
      }))
      this.graphModel.edges = this.data.edges.map((edge) => ({
        ...edge,
        modelType: 'polyline-edge',
        getData: () => structuredClone(edge)
      }))
      Object.values(this.extension).forEach((plugin) => plugin.render(this))
      this.data.edges.forEach((data) => this.events['edge:add']?.({ data }))
    }
    renderRawData(data: any) {
      this.render(data)
    }
    getNodeModelById(id: string) {
      return this.graphModel.nodes.find((node) => node.id === id)
    }
    getGraphRawData() {
      return this.data
    }
    on(name: string, handler: Function) {
      this.events[name] = handler
    }
    setTheme() {}
    fitView = vi.fn()
    updateEditConfig() {}
    destroy() {}
    resize() {}
  }
}))

afterEach(() => {
  runtime.instances.length = 0
  vi.unstubAllGlobals()
})
const button = (node: TestNode): TestNode | undefined =>
  node.type === 'button' && textOf(node) === '整理当前画布'
    ? node
    : node.children.map(button).find(Boolean)
const setup = (readonly = false, empty = false) => {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    }
  )
  const state = reactive({
    readonly,
    nodes: empty
      ? []
      : ([
          { key: 'A', name: '工勘', code: 'A', kind: 'TASK', x: 240, y: 160 },
          { key: 'B', name: '需求', code: 'B', kind: 'TASK', x: 240, y: 160 },
          { key: 'C', name: '跨阶段引用', code: 'C', kind: 'TASK', reference: true, x: 240, y: 160 }
        ] as DeliveryCanvasNode[])
  })
  const moves = vi.fn((key: string, point: { x: number; y: number }) => {
    Object.assign(state.nodes.find((node) => node.key === key)!, point)
  })
  const connect = vi.fn(),
    remove = vi.fn(),
    disconnect = vi.fn()
  const page = mount(
    defineComponent({
      setup: () => () =>
        h(TemplateFlowCanvas, {
          nodes: state.nodes,
          edges: empty ? [] : [{ key: 'edge:AB', from: 'A', to: 'B' }],
          mode: 'TASK',
          readonly: state.readonly,
          onMove: moves,
          onConnect: connect,
          onRemove: remove,
          onDisconnect: disconnect
        })
    })
  )
  return { ...page, state, moves, connect, remove, disconnect, flow: runtime.instances.at(-1) }
}

it('explicit layout separates overlapping nodes using Dagre and persists only positions', async () => {
  const page = setup()
  try {
    expect(page.moves).not.toHaveBeenCalled()
    const original = page.state.nodes.map(({ x, y, ...node }) => node)
    await (button(page.root)!.props!.onClick as Function)()
    await nextTick()
    expect(page.moves).toHaveBeenCalledTimes(3)
    expect(page.state.nodes.map(({ x, y, ...node }) => node)).toEqual(original)
    const [a, b, c] = page.state.nodes
    expect(b.y! - a.y!).toBeGreaterThanOrEqual(72)
    for (const [first, second] of [
      [a, b],
      [a, c],
      [b, c]
    ])
      expect(Math.abs(first.x! - second.x!) >= 172 || Math.abs(first.y! - second.y!) >= 72).toBe(
        true
      )
    expect(page.connect).not.toHaveBeenCalled()
    expect(page.remove).not.toHaveBeenCalled()
    expect(page.disconnect).not.toHaveBeenCalled()
    expect(page.flow.data.edges[0]).toMatchObject({
      id: 'edge:AB',
      sourceNodeId: 'A',
      targetNodeId: 'B'
    })
    expect(page.flow.getNodeModelById('C').rules[0].validate()).toBe(false)
    expect(page.flow.fitView).toHaveBeenCalledOnce()
  } finally {
    page.app.unmount()
  }
})

it('read-only views never offer layout and stale clicks cannot change them', async () => {
  const page = setup()
  try {
    const click = button(page.root)!.props!.onClick as Function
    page.state.readonly = true
    await nextTick()
    expect(button(page.root)).toBeUndefined()
    await click()
    expect(page.moves).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

it('does not arrange an empty graph', async () => {
  const page = setup(false, true)
  try {
    expect(button(page.root)!.props!.disabled).toBe(true)
    await (button(page.root)!.props!.onClick as Function)()
    expect(page.moves).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})

it('reports a plugin invocation failure without writing positions or dependencies', async () => {
  const page = setup()
  try {
    page.flow.extension.dagre.layout = () => {
      throw new Error('plugin failure')
    }
    await (button(page.root)!.props!.onClick as Function)()
    await nextTick()
    expect(textOf(page.root)).toContain('画布整理失败')
    expect(page.moves).not.toHaveBeenCalled()
    expect(page.connect).not.toHaveBeenCalled()
  } finally {
    page.app.unmount()
  }
})
