<template>
  <span v-if="fileFact">已提交附件（第 {{ fileFact.versionNo }} 版）</span>
  <ol v-else-if="Array.isArray(answer)" class="answer-rows"><li v-for="(row, index) in answer" :key="index"><CutoverFrozenAnswer :value="row" :schema="rowSchema" /></li></ol>
  <dl v-else-if="answer && typeof answer === 'object'" class="answer-fields"><div v-for="(field, key) in answer" :key="key"><dt>{{ fieldSchema(String(key))?.label || fieldSchema(String(key))?.name || labels[String(key)] || key }}</dt><dd><CutoverFrozenAnswer :value="field" :schema="fieldSchema(String(key))" /></dd></div></dl>
  <span v-else class="answer-text">{{ scalarLabel }}</span>
</template>
<script setup lang="ts">
import type { FieldSchema } from './CutoverSchemaInput.vue'
const props = defineProps<{ snapshot?: string; value?: unknown; schema?: FieldSchema }>()
const answer = computed<any>(() => {
  let result = props.value
  if (props.snapshot) {
    try {
      const snapshot = JSON.parse(props.snapshot)
      result = snapshot?.analysis ?? snapshot
      if (result !== null && typeof result === 'object' && Object.prototype.hasOwnProperty.call(result, 'value')) result = (result as Record<string, unknown>).value
    } catch { return props.snapshot }
  }
  // TABLE 的 rows 仅是存储包装；列描述属于每一行，而不是包装字段。
  const rows = result !== null && typeof result === 'object' && 'rows' in result ? result.rows : undefined
  return props.schema?.columns?.length && Array.isArray(rows) ? rows : result
})
const rowSchema = computed(() => props.schema?.columns?.length ? { fields: props.schema.columns } : props.schema)
const fieldSchema = (key: string) => props.schema?.fields?.find(field => field.code === key)
const scalarLabel = computed(() => {
  const value = answer.value
  const option = props.schema?.options?.find(option => (typeof option === 'string' ? option : option.value) === value)
  if (option) return typeof option === 'string' ? option : option.label
  return typeof value === 'boolean' ? (value ? '是' : '否') : value === 'YES' ? '是' : value === 'NO' ? '否' : value ?? '—'
})
const fileFact = computed(() => answer.value && !Array.isArray(answer.value) && typeof answer.value === 'object' && 'artifactId' in answer.value ? answer.value : null)
const labels: Record<string, string> = {
  executed: '是否已执行', specialNote: '特殊情况备注', remark: '备注', notes: '备注', description: '说明',
  solvesOnlineIssue: '是否解决网上问题', issueTicketNo: '问题工单号', issueHandler: '问题处理二线工程师',
  repeatCutover: '是否再次割接', firstCutoverOwner: '上次割接负责人', backgroundDescription: '割接背景表述',
  deviceType: '设备类型', serialNumber: '序列号', deviceName: '设备名称', location: '部署位置',
  localDevice: '本端设备', localInterface: '本端接口', remoteInterface: '对端接口', interfaceType: '接口类型', cableModel: '线缆型号',
  interface: '接口', ip: 'IP地址', mask: '子网掩码', vrrpIp: 'VRRP地址', gateway: '网关',
  concurrentConnections: '并发连接数', newConnections: '新建连接数', cpuUsage: 'CPU使用率', memoryUsage: '内存使用率',
  hasBulletins: '是否有相关技术公告', queryBasis: '版本与查询依据', bulletins: '技术公告', number: '公告编号',
  subject: '主题', solution: '解决方案', involved: '本次是否涉及', measures: '采取措施',
  normal: '检查是否正常', passed: '是否通过', resultCode: '检查结果', factDescription: '事实说明', file: '附件',
  majorCutover: '本次是否为重大割接项目', spareReference: '备件外部申请引用或人工核验说明',
  collectionTaskId: '采集任务编号', deviceId: '采集设备编号', commandTemplateId: '采集命令模板编号',
  technicalStatus: '采集技术状态编码', resultReferenceId: '采集结果引用编号', resultVersion: '采集结果版本',
  failureCode: '采集失败编码', originalFailedCollectionTaskId: '原失败采集任务编号',
  originalFailedResultVersion: '原失败采集结果版本', originalFailureCode: '原采集失败编码'
}
</script>
<style scoped>
.answer-fields { margin: 0; }
.answer-fields > div { display: flex; align-items: flex-start; gap: 10px; padding: 2px 0; }
.answer-fields dt { flex: 0 0 132px; color: #888; font-size: 12px; }
.answer-fields dd { min-width: 0; margin: 0; overflow-wrap: anywhere; }
.answer-rows { margin: 0; padding-left: 18px; }
.answer-rows > li + li { margin-top: 10px; }
.answer-text { white-space: pre-wrap; overflow-wrap: anywhere; }
@media (width <= 767px) { .answer-fields > div { display: block; } }
</style>
