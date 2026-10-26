// Display coordinates only. Never send this XML to the model editor or a persistence API.
const MODEL = 'http://www.omg.org/spec/BPMN/20100524/MODEL'
const DI = 'http://www.omg.org/spec/BPMN/20100524/DI'
const DC = 'http://www.omg.org/spec/DD/20100524/DC'
const WAYPOINT = 'http://www.omg.org/spec/DD/20100524/DI'
export interface ClosureDiagramNode {
  id: string
  type: string
}
export interface ClosureDiagramFlow {
  id: string
  source: string
  target: string
}

export const closureDiagramOrder = (nodes: ClosureDiagramNode[], flows: ClosureDiagramFlow[]) => {
  const start = nodes.filter((node) => node.type === 'startEvent')
  if (
    start.length !== 1 ||
    nodes.some((node) => !['startEvent', 'userTask', 'endEvent'].includes(node.type))
  )
    return []
  const order: ClosureDiagramNode[] = []
  let node: ClosureDiagramNode | undefined = start[0]
  while (node && !order.some((item) => item.id === node!.id)) {
    order.push(node)
    const outgoing = flows.filter((flow) => flow.source === node!.id)
    if (node.type === 'endEvent') {
      return outgoing.length === 0 &&
        order.length === nodes.length &&
        flows.length === nodes.length - 1
        ? order
        : []
    }
    if (outgoing.length !== 1) return []
    node = nodes.find((item) => item.id === outgoing[0].target)
  }
  return []
}

export const normalClosureDiagram = (
  xml: string
): { xml: string; error?: string; generated?: boolean } => {
  if (!xml) return { xml }
  const doc = new DOMParser().parseFromString(xml, 'application/xml')
  const processes = Array.from(doc.getElementsByTagNameNS(MODEL, 'process'))
  const process = processes.find((item) => item.id === 'PMS_MINIMAL_NORMAL_CLOSURE')
  if (!process || doc.getElementsByTagNameNS(DI, 'BPMNShape').length) return { xml }
  const fail = () => ({
    xml: '',
    error: '该历史流程缺少可展示的图形布局，请查看审批详情或流转记录。'
  })
  if (doc.getElementsByTagName('parsererror').length || processes.length !== 1) return fail()
  const elements = Array.from(process.children).filter(
    (item) => item.namespaceURI === MODEL && item.localName !== 'extensionElements'
  )
  const nodes = elements
    .filter((item) => item.localName !== 'sequenceFlow')
    .map((item) => ({ id: item.id, type: item.localName }))
  const flows = elements
    .filter((item) => item.localName === 'sequenceFlow')
    .map((item) => ({
      id: item.id,
      source: item.getAttribute('sourceRef') || '',
      target: item.getAttribute('targetRef') || ''
    }))
  const order = closureDiagramOrder(nodes, flows)
  if (
    !order.length ||
    order.some((item) => !item.id) ||
    new Set(nodes.map((item) => item.id)).size !== nodes.length
  )
    return fail()
  // Old empty DI is presentation metadata, not process semantics; replace it in this detached document only.
  Array.from(doc.getElementsByTagNameNS(DI, 'BPMNDiagram')).forEach((item) => item.remove())
  const make = (ns: string, name: string, attrs: Record<string, string | number>) => {
    const item = doc.createElementNS(ns, name)
    Object.entries(attrs).forEach(([key, value]) => item.setAttribute(key, String(value)))
    return item
  }
  const diagram = make(DI, 'bpmndi:BPMNDiagram', { id: 'ClosureDisplayDiagram' })
  const plane = make(DI, 'bpmndi:BPMNPlane', { id: 'ClosureDisplayPlane', bpmnElement: process.id })
  diagram.appendChild(plane)
  const positions = new Map(
    order.map((node, index) => {
      const task = node.type === 'userTask'
      return [
        node.id,
        { x: 80 + index * 230, y: task ? 100 : 122, width: task ? 160 : 36, height: task ? 80 : 36 }
      ]
    })
  )
  for (const node of order) {
    const shape = make(DI, 'bpmndi:BPMNShape', {
      id: `ClosureDisplay_${node.id}`,
      bpmnElement: node.id
    })
    shape.appendChild(make(DC, 'dc:Bounds', positions.get(node.id)!))
    if (node.type !== 'userTask') {
      const position = positions.get(node.id)!
      const label = make(DI, 'bpmndi:BPMNLabel', {})
      label.appendChild(
        make(DC, 'dc:Bounds', {
          x: position.x + position.width / 2 - 70,
          y: position.y + position.height + 8,
          width: 140,
          height: 30
        })
      )
      shape.appendChild(label)
    }
    plane.appendChild(shape)
  }
  for (const flow of flows) {
    const from = positions.get(flow.source)!,
      to = positions.get(flow.target)!
    const edge = make(DI, 'bpmndi:BPMNEdge', {
      id: `ClosureDisplay_${flow.id}`,
      bpmnElement: flow.id
    })
    edge.appendChild(
      make(WAYPOINT, 'di:waypoint', { x: from.x + from.width, y: from.y + from.height / 2 })
    )
    edge.appendChild(make(WAYPOINT, 'di:waypoint', { x: to.x, y: to.y + to.height / 2 }))
    plane.appendChild(edge)
  }
  doc.documentElement.appendChild(diagram)
  return { xml: new XMLSerializer().serializeToString(doc), generated: true }
}
