/*
 * @author igdianov
 * 基于 activiti-bpmn-moddle
 * */

import activitiExtension from './activitiExtension'

export default {
  __init__: ['ActivitiModdleExtension'],
  ActivitiModdleExtension: ['type', activitiExtension]
}
