/*
 * @author igdianov
 * 基于 activiti-bpmn-moddle
 * */
import flowableExtension from './flowableExtension'

export default {
  __init__: ['FlowableModdleExtension'],
  FlowableModdleExtension: ['type', flowableExtension]
}
