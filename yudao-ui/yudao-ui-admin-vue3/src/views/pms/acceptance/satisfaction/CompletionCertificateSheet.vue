<template>
  <section class="completion-sheet" :aria-labelledby="showTitle !== false ? 'completion-sheet-title' : undefined" aria-label="完工证明">
    <h2 v-if="showTitle !== false" id="completion-sheet-title">完工证明</h2>
    <div class="completion-scroll" tabindex="0" aria-label="完工证明及设备明细，可横向滚动">
      <table class="completion-facts">
        <tbody>
          <tr><th>项目名称</th><td colspan="3">{{ certificate.name || '—' }}</td></tr>
          <tr><th>客户单位</th><td colspan="3">{{ certificate.customerUnit || '—' }}</td></tr>
          <tr><th>合同号</th><td>{{ certificate.contractNo || '—' }}</td><th>工程服务类型</th><td>{{ certificate.serviceType || '—' }}</td></tr>
          <tr><th>迪普工程师</th><td>{{ certificate.engineerName || '—' }}</td><th>联系方式</th><td>{{ certificate.engineerContact || '—' }}</td></tr>
          <tr v-for="(item, index) in serviceItems" :key="item.label" class="service-row"><th v-if="index === 0" :rowspan="serviceItems.length" class="service-heading">工程服务内容</th><td colspan="2">{{ item.label }}</td><td>{{ item.value || '—' }}</td></tr>
          <tr v-for="(device, index) in certificate.devices" :key="index" class="device-row completion-devices"><th v-if="index === 0" :rowspan="certificate.devices!.length" scope="rowgroup" class="devices-heading">设备名称和数量</th><td colspan="2">{{ device.deviceType || '—' }}</td><td>{{ device.quantity }}</td></tr>
          <tr v-if="!certificate.devices?.length" class="device-row completion-devices"><th class="devices-heading">设备名称和数量</th><td colspan="3" class="empty-devices">未填写设备明细</td></tr>
          <tr><th>完工时间</th><td colspan="3">{{ certificate.completionDate || '—' }}</td></tr>
        </tbody>
      </table>
    </div>
    <p v-if="showHint !== false" class="completion-hint">请核对以上完工内容；下方一次签字同时确认本证明和满意度调查表。</p>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CompletionCertificateSnapshot } from '@/api/pms/acceptance/satisfaction'
const props = defineProps<{ certificate: Omit<CompletionCertificateSnapshot, 'id' | 'version'>; showHint?: boolean; showTitle?: boolean }>()
const serviceItems = computed(() => [
  { label: '完成到货验收', value: props.certificate.itemArrival },
  { label: '完成设备硬件安装和软件调测', value: props.certificate.itemInstall },
  { label: '完成业务上线/割接且业务测试正常', value: props.certificate.itemCutover },
  { label: '完成产品维护现场讲解和培训', value: props.certificate.itemTraining },
  { label: '工程文档、帐号密码已移交并协助修改', value: props.certificate.itemDocs }
])
</script>

<style scoped>
.completion-sheet h2 { margin: 0 0 16px; font-size: 18px; }
.completion-scroll { overflow-x: auto; border: 1px solid var(--el-border-color-lighter); border-radius: 5px; }
table { width: 100%; min-width: 580px; border-collapse: collapse; font-size: 13px; }
th, td { border-right: 1px solid var(--el-border-color-lighter); border-bottom: 1px solid var(--el-border-color-lighter); padding: 12px; }
th { width: 120px; font-weight: 400; background: var(--el-fill-color-light); }
tr > :last-child { border-right: 0; }
tr:last-child > * { border-bottom: 0; }
.device-row td { overflow-wrap: anywhere; }
.device-row:nth-child(even) { background: var(--el-fill-color-lighter); }
.service-row > td:last-child, .device-row > td:last-child { text-align: center; vertical-align: middle; }
.empty-devices { color: var(--el-text-color-secondary); }
.completion-hint { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.6; }
@media (max-width: 900px) {
  .completion-facts, .completion-facts > tbody { display: block; min-width: 0; }
  .completion-facts > tbody > tr { display: grid; grid-template-columns: 88px minmax(0, 1fr); }
  .completion-facts > tbody > tr > th { width: auto; text-align: left; }
  .completion-facts > tbody > tr > * { min-width: 0; padding: 12px 10px; font-size: 14px; line-height: 1.6; }
  .completion-facts > tbody > tr > td { border-right: 0; }
  .completion-facts > tbody > .service-row { grid-template-columns: minmax(0, 1fr) 64px; }
  .service-heading { grid-column: 1 / -1; border-right: 0; font-weight: 600; }
  .service-row > td:first-of-type { border-right: 1px solid var(--el-border-color-lighter); }
  .service-row > td:last-child { display: flex; align-items: center; justify-content: center; padding-inline: 4px; }
  .completion-facts > tbody > .device-row { grid-template-columns: minmax(0, 1fr) 64px; }
  .devices-heading { grid-column: 1 / -1; border-right: 0; text-align: left; }
  .device-row > :last-child { width: auto; text-align: center; }
  .completion-facts > tbody > .device-row > td:first-of-type { border-right: 1px solid var(--el-border-color-lighter); }
  .device-row > .empty-devices { grid-column: 1 / -1; width: auto; text-align: left; }
  .completion-scroll td { overflow-wrap: anywhere; }
}
</style>
