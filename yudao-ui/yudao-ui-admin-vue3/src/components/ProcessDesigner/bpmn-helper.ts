/**
 * bpmn-js 流程设计器辅助函数与低代码 moddle 扩展。
 *
 * <p>借鉴 Appian Process Modeler / Camunda bpmn-js：封装 BpmnModeler /
 * BpmnViewer 的创建、BPMN XML 导入导出、画布元素创建，以及 UserTask 节点的
 * 低代码专属属性（表单绑定、审批人、超时、回调微流、SLA）的读写。</p>
 *
 * <p>低代码专属属性以 <code>&lt;lowcode:config .../&gt;</code> 扩展元素的形式
 * 存储在 UserTask 的 extensionElements 中，通过自定义 moddle 描述符声明，
 * 保证 BPMN XML 序列化/反序列化时不丢失，并与 Flowable/Camunda 兼容。</p>
 */
import BpmnModeler from 'bpmn-js/lib/Modeler'
import BpmnViewer from 'bpmn-js/lib/Viewer'
import type { ModdleElement } from 'bpmn-js/lib/model/Types'
import camundaModdleDescriptor from 'camunda-bpmn-moddle/resources/camunda.json'

/** bpmn-js 与 diagram-js 关键服务的最小可用类型视图（moddle 元素本身为 any） */
export interface BpmnCanvasService {
  readonly container: HTMLElement
  addMarker(elementId: string, marker: string): void
  removeMarker(elementId: string, marker: string): void
  zoomToFit?(opts?: Record<string, unknown>): void
  defaultLayer?: unknown
  _svg?: SVGElement
  viewbox(): { x: number; y: number; width: number; height: number; scale: number }
}

export interface BpmnElementFactory {
  createShape(attrs: Record<string, unknown>): ModdleElement
  createRoot(attrs: Record<string, unknown>): ModdleElement
}

export interface BpmnCreateService {
  start(event: MouseEvent | DragEvent, shape: ModdleElement, context?: Record<string, unknown>): void
}

export interface BpmnModelingService {
  createShape(shape: ModdleElement, position: { x: number; y: number }, parent: ModdleElement, opts?: Record<string, unknown>): ModdleElement
  updateProperties(element: ModdleElement, props: Record<string, unknown>): void
  updateModdleProperties(element: ModdleElement, moddleElement: ModdleElement, props: Record<string, unknown>): void
}

export interface BpmnElementRegistry {
  get(id: string): ModdleElement | undefined
  getAll(): ModdleElement[]
  filter(filter: (element: ModdleElement) => boolean): ModdleElement[]
}

export interface BpmnSelectionService {
  get(): ModdleElement[]
  select(elements: ModdleElement[] | string[]): void
}

export interface BpmnEventBus {
  on(events: string | string[], callback: (event: Record<string, unknown>) => void): void
  off(events: string | string[], callback?: (...args: unknown[]) => unknown): void
}

/** 低代码 moddle 扩展命名空间前缀 */
export const LOWCODE_PREFIX = 'lowcode'

/** 低代码 moddle 描述符：定义 lowcode:config 扩展元素及其属性 */
export const lowCodeModdleDescriptor = {
  name: 'LowCode',
  uri: 'http://lowcode.plat/schema/bpmn',
  prefix: LOWCODE_PREFIX,
  xml: { tagAlias: 'lowerCase' },
  associations: [],
  types: [
    {
      name: 'Config',
      superClass: ['Element'],
      properties: [
        { name: 'formCode', isAttr: true, type: 'String' },
        { name: 'assignee', isAttr: true, type: 'String' },
        { name: 'candidateUsers', isAttr: true, type: 'String' },
        { name: 'candidateGroups', isAttr: true, type: 'String' },
        { name: 'timeoutDuration', isAttr: true, type: 'String' },
        { name: 'timeoutUnit', isAttr: true, type: 'String' },
        { name: 'timeoutHandler', isAttr: true, type: 'String' },
        { name: 'onCompleteMicroflow', isAttr: true, type: 'String' },
        { name: 'onCreateMicroflow', isAttr: true, type: 'String' },
        { name: 'onAssignMicroflow', isAttr: true, type: 'String' },
        { name: 'slaDuration', isAttr: true, type: 'String' },
        { name: 'slaUnit', isAttr: true, type: 'String' },
        { name: 'slaEscalationMicroflow', isAttr: true, type: 'String' }
      ]
    }
  ]
}

/** moddle 扩展映射（注入 BpmnModeler/BpmnViewer 的 moddleExtensions） */
export const moddleExtensions = {
  camunda: camundaModdleDescriptor,
  lowcode: lowCodeModdleDescriptor
}

/**
 * 创建一个 BpmnModeler 实例。
 *
 * @param container 画布容器 DOM 节点
 * @param readonly  是否只读（只读时退化为 Viewer，由调用方自行处理）
 */
export function createModeler(container: HTMLElement): BpmnModeler {
  return new BpmnModeler({
    container,
    moddleExtensions,
    keyboard: { bindTo: window }
  })
}

/** 创建一个 BpmnViewer 实例（只读预览） */
export function createViewer(container: HTMLElement): BpmnViewer {
  return new BpmnViewer({
    container,
    moddleExtensions
  })
}

/** 创建一张空白 BPMN 图（含一个开始事件） */
export async function createNewDiagram(modeler: BpmnModeler): Promise<void> {
  await modeler.createDiagram()
}

/** 导入 BPMN XML 到 modeler/viewer */
export async function importXml(
  modeler: BpmnModeler | BpmnViewer,
  xml: string
): Promise<void> {
  const result = await modeler.importXML(xml)
  if (result.warnings?.length) {
    // 仅警告，不阻断
    console.warn('[bpmn] import warnings:', result.warnings)
  }
}

/** 导出当前画布的 BPMN XML（格式化） */
export async function exportXml(
  modeler: BpmnModeler | BpmnViewer
): Promise<string> {
  const { xml } = await modeler.saveXML({ format: true })
  if (!xml) {
    throw new Error('导出 BPMN XML 失败：结果为空')
  }
  return xml
}

/** 导出当前画布的 SVG */
export async function exportSvg(
  modeler: BpmnModeler | BpmnViewer
): Promise<string> {
  const { svg } = await modeler.saveSVG()
  return svg
}

/** 获取或创建 businessObject 的 extensionElements */
export function getOrCreateExtensionElements(
  businessObject: ModdleElement,
  moddle: ModdleElement
): ModdleElement {
  let extElements = businessObject.get('extensionElements')
  if (!extElements) {
    extElements = moddle.create('bpmn:ExtensionElements', { values: [] })
    businessObject.set('extensionElements', extElements)
  }
  if (!extElements.get('values')) {
    extElements.set('values', [])
  }
  return extElements
}

/**
 * 获取（或按需创建）businessObject 上的 lowcode:config 扩展元素。
 *
 * @param businessObject BPMN 元素的 businessObject
 * @param moddle         moddle 实例（来自 modeler.get('moddle')）
 * @param create         为 true 时若不存在则创建
 */
export function getLowCodeConfig(
  businessObject: ModdleElement,
  moddle: ModdleElement,
  create = false
): ModdleElement | undefined {
  if (!businessObject) return undefined
  const extElements = businessObject.get('extensionElements')
  if (!extElements) {
    if (!create) return undefined
    const newExt = getOrCreateExtensionElements(businessObject, moddle)
    const config = moddle.create('lowcode:Config')
    newExt.get('values').push(config)
    config.$parent = newExt
    return config
  }
  const values = extElements.get('values') || []
  const existing = values.find(
    (v: ModdleElement) => v.$type === 'lowcode:Config'
  )
  if (existing) return existing
  if (!create) return undefined
  const config = moddle.create('lowcode:Config')
  values.push(config)
  config.$parent = extElements
  return config
}

/** 读取 UserTask 的某个低代码属性 */
export function getLowCodeProperty(
  businessObject: ModdleElement,
  moddle: ModdleElement,
  name: string
): string {
  const config = getLowCodeConfig(businessObject, moddle, false)
  if (!config) return ''
  const val = config.get(name)
  return val == null ? '' : String(val)
}

/**
 * 写入 UserTask 的某个低代码属性。
 *
 * <p>首次写入时会自动创建 extensionElements 与 lowcode:config 扩展元素。</p>
 */
export function setLowCodeProperty(
  businessObject: ModdleElement,
  moddle: ModdleElement,
  name: string,
  value: string
): void {
  const config = getLowCodeConfig(businessObject, moddle, true)!
  config.set(name, value || undefined)
}

/** 调色板可拖拽元素类型枚举（type → 显示名） */
export const PALETTE_EVENTS: Array<{ type: string; label: string }> = [
  { type: 'bpmn:StartEvent', label: '开始事件' },
  { type: 'bpmn:EndEvent', label: '结束事件' },
  { type: 'bpmn:IntermediateThrowEvent', label: '中间事件' }
]

export const PALETTE_ACTIVITIES: Array<{ type: string; label: string }> = [
  { type: 'bpmn:UserTask', label: '用户任务' },
  { type: 'bpmn:ServiceTask', label: '服务任务' },
  { type: 'bpmn:ScriptTask', label: '脚本任务' }
]

export const PALETTE_GATEWAYS: Array<{ type: string; label: string }> = [
  { type: 'bpmn:ExclusiveGateway', label: '排他网关' },
  { type: 'bpmn:ParallelGateway', label: '并行网关' },
  { type: 'bpmn:InclusiveGateway', label: '包容网关' },
  { type: 'bpmn:EventBasedGateway', label: '事件网关' }
]

export const PALETTE_SWIMLANES: Array<{ type: string; label: string }> = [
  { type: 'bpmn:Participant', label: '池' },
  { type: 'bpmn:Lane', label: '泳道' }
]

/**
 * 从调色板按下鼠标时，构造对应 shape 并启动 bpmn-js 的 create 拖拽。
 *
 * <p>借鉴 bpmn-js 内置 Palette：通过 create.start(event, shape) 启动拖拽，
 * dragging 模块会在 document 级别接管 mousemove/mouseup，从而支持从外部
 * 调色板拖入画布。</p>
 *
 * @param modeler BpmnModeler 实例
 * @param event   mousedown 事件
 * @param type    BPMN 元素类型（如 bpmn:UserTask）
 */
export function startCreateFromPalette(
  modeler: BpmnModeler,
  event: MouseEvent,
  type: string
): void {
  const elementFactory = modeler.get<BpmnElementFactory>('elementFactory')
  const create = modeler.get<BpmnCreateService>('create')

  const attrs: Record<string, unknown> = { type }
  // 池默认展开
  if (type === 'bpmn:Participant') {
    attrs.isExpanded = true
  }
  const shape = elementFactory.createShape(attrs)
  create.start(event, shape)
}

/** 判断元素是否为 UserTask */
export function isUserTask(element: ModdleElement | undefined | null): boolean {
  if (!element) return false
  const bo = (element.businessObject as ModdleElement) || element
  return bo.$type === 'bpmn:UserTask'
}

/** 获取元素的 id（基于 businessObject） */
export function getElementId(element: ModdleElement | undefined | null): string {
  if (!element) return ''
  const bo = (element.businessObject as ModdleElement) || element
  return String(bo.id || element.id || '')
}

/** 获取元素的名称（基于 businessObject） */
export function getElementName(element: ModdleElement | undefined | null): string {
  if (!element) return ''
  const bo = (element.businessObject as ModdleElement) || element
  return String(bo.name || '')
}
