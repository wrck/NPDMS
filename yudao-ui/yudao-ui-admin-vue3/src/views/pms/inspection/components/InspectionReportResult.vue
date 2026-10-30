<template>
  <section class="report-result" aria-label="报告结构化结果">
    <el-button :loading="loading" @click="load">{{
      detail ? '刷新已保存结果' : '查看报告结果'
    }}</el-button>
    <p class="help-text">读取本地已保存的报告结果。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="detail">
      <el-alert v-if="detail.info" :title="detail.info" type="warning" :closable="false" />
      <article v-for="device in detail.devlist" :key="device.id" class="umc-device">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="设备序列号">{{
            device.sn || '未识别'
          }}</el-descriptions-item>
          <el-descriptions-item label="设备IP">{{
            device.deviceip || '未提供'
          }}</el-descriptions-item>
          <el-descriptions-item label="设备型号">{{
            device.devmodel || '未识别'
          }}</el-descriptions-item>
          <el-descriptions-item label="软件版本">{{
            device.version || '未识别'
          }}</el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="device.info" :title="device.info" type="warning" :closable="false" />
        <p v-if="!device.rulelist?.length">UMC未返回检测规则。</p>
        <article v-for="rule in device.rulelist || []" :key="rule.id" class="report-rule">
          <h3
            >{{ rule.name }} <el-tag>{{ resultLabel(rule) }}</el-tag></h3
          >
          <InspectionUmcRuleDetail :rule="rule" />
        </article>
      </article>
    </template>
  </section>
</template>
<script setup lang="ts">
import { ref, onBeforeUnmount, onMounted } from 'vue'
import InspectionUmcRuleDetail from './InspectionUmcRuleDetail.vue'
import type { WireLong } from '@/api/pms/inspection/tasks'
import * as Api from '@/api/pms/inspection/reportResults'
const props = defineProps<{ taskId: WireLong; reportId: WireLong }>()
const detail = ref<Api.UmcReport['detail']>()
const loading = ref(false),
  error = ref('')
let disposed = false
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const saved = await Api.detail(props.taskId, props.reportId)
    if (!disposed) detail.value = saved.detail
  } catch {
    if (!disposed) error.value = '报告结果读取失败或无权访问，请重试。'
  } finally {
    if (!disposed) loading.value = false
  }
}
const resultLabel = (row: { support?: boolean; error?: string; result?: boolean }) =>
  row.support === false
    ? '不支持'
    : row.error
      ? '执行错误'
      : row.result === true
        ? '异常'
        : row.result === false
          ? '正常'
          : '未判定'
onBeforeUnmount(() => {
  disposed = true
})
onMounted(load)
</script>
<style scoped>
.report-result {
  margin-top: 16px;
}
.umc-device {
  margin-top: 12px;
}
.report-rule {
  margin-top: 16px;
  border: 1px solid var(--el-border-color-lighter);
}
.report-rule h3 {
  margin: 0;
  padding: 12px 16px;
  background: var(--el-fill-color-light);
}
</style>
