/**
 * bpmn-js 相关模块的类型声明补充。
 *
 * <p>bpmn-js 自 Modeler/Viewer 起自带 .d.ts，但部分资源（camunda moddle
 * 描述符 JSON）与第三方属性面板缺少类型，此处统一补充 ambient 声明，
 * 避免类型检查报错。</p>
 */

/** Camunda BPMN moddle 描述符（JSON 资源） */
declare module 'camunda-bpmn-moddle/resources/camunda.json' {
  const descriptor: {
    name: string
    uri: string
    prefix: string
    xml: { tagAlias: string }
    associations: unknown[]
    types: unknown[]
  }
  export default descriptor
}

/** bpmn-js-properties-panel（已安装但本项目采用自研 Element Plus 属性面板，仅声明以备使用） */
declare module 'bpmn-js-properties-panel' {
  const BpmnPropertiesPanel: {
    new (options: {
      container: HTMLElement
      modeler: unknown
      moddle?: unknown
      layout?: unknown
    }): { attachTo: (node: HTMLElement) => void; detach: () => void }
  }
  export default BpmnPropertiesPanel
}
