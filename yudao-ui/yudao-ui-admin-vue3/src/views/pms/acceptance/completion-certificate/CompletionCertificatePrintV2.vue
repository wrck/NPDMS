<template>
  <WordPrintFrameV2 title="工程完工证明">
    <h1>工程完工证明</h1>
    <table class="word-print-table completion-word-table">
      <colgroup><col style="width: 17.5%" /><col style="width: 29%" /><col style="width: 21%" /><col style="width: 32.5%" /></colgroup>
      <tbody>
        <tr><th scope="row">项目名称 <span class="asterisk">*</span></th><td colspan="3">{{ certificate?.name }}</td></tr>
        <tr><th scope="row">客户单位</th><td colspan="3">{{ certificate?.customerUnit }}</td></tr>
        <tr>
          <th scope="row">合同号 <span class="asterisk">*</span></th><td>{{ certificate?.contractNo }}</td>
          <th scope="row">工程服务类型<span class="asterisk">*</span></th>
          <td class="service-type">
            <span v-for="type in ['工程实施', '工程督导']" :key="type">{{ type }}<span class="word-check" :class="{ checked: certificate?.serviceType === type }"><span v-if="certificate?.serviceType === type">√</span></span></span>
            <span v-if="certificate?.serviceType && !['工程实施', '工程督导'].includes(certificate.serviceType)">{{ certificate.serviceType }}</span>
          </td>
        </tr>
        <tr><th scope="row">迪普工程师</th><td>{{ certificate?.engineerName }}</td><th scope="row">联系方式</th><td>{{ certificate?.engineerContact }}</td></tr>
        <tr v-for="(item, index) in serviceItems" :key="item.field" class="word-service-row">
          <th v-if="index === 0" scope="rowgroup" :rowspan="serviceItems.length">工程服务内容</th>
          <td colspan="2">{{ item.label }}</td>
          <td class="service-choices">
            <span v-for="choice in ['是', '否', '不涉及']" :key="choice"><span class="word-check" :class="{ checked: certificate?.[item.field] === choice }"><span v-if="certificate?.[item.field] === choice">√</span></span>{{ choice }}</span>
            <span v-if="certificate?.[item.field] && !['是', '否', '不涉及'].includes(certificate[item.field]!)">{{ certificate[item.field] }}</span>
          </td>
        </tr>
        <tr class="devices-group">
          <th scope="row">设备名称<br />和数量</th>
          <td colspan="3" class="devices-cell">
            <table class="word-device-table">
              <caption class="sr-only">设备名称和数量</caption>
              <colgroup><col style="width: 78%" /><col style="width: 22%" /></colgroup>
              <thead><tr><th scope="col">设备名称</th><th scope="col">数量</th></tr></thead>
              <tbody><tr v-for="(device, index) in devices" :key="index"><td>{{ device?.deviceType }}</td><td class="quantity">{{ device?.quantity }}</td></tr></tbody>
            </table>
          </td>
        </tr>
        <tr><th scope="row">完工日期</th><td colspan="3" class="completion-date">{{ wordPrintDate(certificate?.completionDate) }}</td></tr>
        <tr>
          <td colspan="2" class="signature-cell">
            <div class="word-signature"><strong>甲方签章：</strong><div class="signature-image"><img v-if="modelValue?.signatureImageDataUrl" :src="modelValue.signatureImageDataUrl" alt="甲方签字" /></div><p>日期： {{ wordPrintDate(modelValue?.customerSignDate) }}</p></div>
          </td>
          <td colspan="2" class="signature-cell">
            <div class="word-signature"><strong>服务方签章：</strong><div class="signature-image"><img v-if="certificate?.vendorSignUrl" :src="certificate.vendorSignUrl" alt="服务方签字" /></div><p>日期： {{ wordPrintDate(certificate?.vendorSignDate) }}</p></div>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="word-remarks">备注：<br />1、 标注为“*”项务必填写正确；<br />2、 原件在签署日期后15天内返回工程管理部存档。</div>
  </WordPrintFrameV2>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import WordPrintFrameV2 from './WordPrintFrameV2.vue'
import { wordPrintDate } from './wordPrintFormat'
import type { ConfirmationWordPrintData } from './confirmationPrintForm'
const props = defineProps<{ modelValue?: ConfirmationWordPrintData }>()
const certificate = computed(() => props.modelValue?.certificate)
const devices = computed(() => {
  const rows = [...(certificate.value?.devices ?? [])]
  return [...rows, ...Array.from({ length: Math.max(0, 5 - rows.length) }, () => undefined)]
})
const serviceItems = [
  { field: 'itemArrival', label: '于____年___月___日完成到货验收' },
  { field: 'itemInstall', label: '完成设备硬件安装和软件调测' },
  { field: 'itemCutover', label: '完成业务上线/割接且业务测试正常' },
  { field: 'itemTraining', label: '完成产品维护现场讲解和培训' },
  { field: 'itemDocs', label: '工程文档、工程帐号和密码已移交客户，并协助客户修改帐号和密码' }
] as const
</script>

<style scoped>
h1 { margin: 0 0 4px; font-family: SimHei, '黑体', sans-serif; font-size: 29.3px; font-weight: 700; line-height: 38px; text-align: center; }
.completion-word-table { width: 154mm; max-width: 100%; margin-left: -2mm; border: 3px double #000; }
.word-print-table, .word-device-table { border-collapse: collapse; table-layout: fixed; }
.word-print-table th, .word-print-table td { box-sizing: border-box; height: 26px; border: 1px solid #000; padding: 3px 5px; text-align: left; vertical-align: middle; overflow-wrap: anywhere; white-space: pre-wrap; }
.word-print-table th { font-weight: 700; }
.word-print-table > tbody > tr > th { font-size: 14px; text-align: center; }
.word-service-row > th { font-size: 14px; white-space: nowrap; padding: 3px; }
.word-print-table tr { break-inside: avoid; }
.word-print-table .devices-group { break-inside: auto; }
.asterisk { color: #f00; }
.service-type, .service-choices { font-size: 14px; }
.service-type > span { display: inline-block; margin-right: 8px; }
.service-choices > span { display: inline-block; margin-right: 12px; white-space: nowrap; }
.service-choices > span:last-child { margin-right: 0; }
.word-check { display: inline-block; box-sizing: border-box; width: 10px; height: 10px; border: 1px solid #000; margin: 0 2px; position: relative; vertical-align: baseline; }
.word-check span { position: absolute; top: -7px; left: -1px; font-family: Arial, sans-serif; font-size: 15px; }
.word-service-row td { height: 30px; }
.word-print-table .devices-cell { padding: 0; }
.word-device-table { width: 100%; }
.word-device-table th, .word-device-table td { border: 0; border-right: 1px solid #000; border-bottom: 1px solid #000; height: 26px; }
.word-device-table th { font-size: 14px; text-align: center; }
.word-device-table tr > :last-child { border-right: 0; }
.word-device-table tbody tr:last-child > * { border-bottom: 0; }
.word-device-table .quantity, .word-print-table .completion-date { text-align: center; }
.word-print-table .signature-cell { padding: 5px; vertical-align: top; }
.word-signature { min-height: 160px; display: flex; flex-direction: column; }
.signature-image { display: flex; align-items: center; flex: 1; min-height: 100px; }
.signature-image img { max-width: 100%; width: 220px; height: 100px; object-fit: contain; }
.word-signature p { margin: 0 0 14px; font-size: 14px; }
.word-remarks { margin-top: 3px; font-size: 12px; line-height: 1.5; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
</style>
