<template>
  <section aria-label="业务采集日志" class="mt-16px" data-testid="business-collection-logs">
    <div class="flex items-center justify-between mb-12px">
      <strong>采集日志</strong>
      <el-button :loading="loading" @click="reload">刷新采集日志</el-button>
    </div>
    <p class="text-[var(--el-text-color-secondary)]"
      >日志自动回传到本记录；失败、超时或取消的日志也保留，业务结果仍需按原流程确认。</p
    >
    <el-table :data="rows" v-loading="loading" row-key="id" empty-text="暂无回传日志">
      <el-table-column
        prop="receivedAt"
        label="回传时间"
        min-width="165"
        :formatter="dateFormatter"
      />
      <el-table-column label="执行结果" min-width="120">
        <template #default="{ row }">
          <el-tag :type="success(row) ? 'success' : 'warning'">{{
            statuses[row.externalStatus] || row.externalStatus
          }}</el-tag>
          <div v-if="!success(row)" class="text-xs mt-4px">日志可能不完整</div>
        </template>
      </el-table-column>
      <el-table-column label="下发命令" min-width="190">
        <template #default="{ row }"
          ><div class="whitespace-pre-wrap break-all max-h-100px overflow-auto">{{
            row.commandText || '历史命令请查看日志'
          }}</div></template
        >
      </el-table-column>
      <el-table-column label="操作" width="135">
        <template #default="{ row }">
          <el-button link type="primary" v-hasPermi="['pms:file:download']" @click="preview(row)"
            >查看日志</el-button
          >
          <el-button link type="primary" v-hasPermi="['pms:file:download']" @click="download(row)"
            >下载</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="pageNo"
      :total="total"
      :page-size="10"
      layout="total, prev, pager, next"
      class="mt-12px"
      @current-change="reload"
    />
  </section>
  <Dialog v-model="logVisible" title="业务采集日志" width="min(1080px, 96vw)" @closed="clearLog">
    <el-alert
      v-if="logRejected"
      title="设备返回命令错误，请核对日志；收到日志不代表命令执行成功。"
      type="error"
      :closable="false"
      class="mb-12px"
    />
    <el-input v-loading="logLoading" :model-value="logText" type="textarea" :rows="22" readonly />
    <template #footer><el-button @click="logVisible = false">关闭</el-button></template>
  </Dialog>
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import * as Api from '@/api/pms/platform/deviceCollection'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
const props = defineProps<{ entry: 'configuration' | 'joint-test'; objectId: Api.Id }>()
const message = useMessage()
const rows = ref<Api.BusinessLog[]>([])
const total = ref(0)
const pageNo = ref(1)
const loading = ref(false)
const logVisible = ref(false)
const logLoading = ref(false)
const logText = ref('')
const logRejected = ref(false)
let generation = 0
let logGeneration = 0
const statuses: Record<string, string> = {
  SUCCEEDED: '执行成功',
  SUCCESS: '执行成功',
  PARTIAL_SUCCESS: '部分成功',
  FAILED: '执行失败',
  TIMED_OUT: '执行超时',
  CANCELLED: '已取消'
}
const success = (row: Api.BusinessLog) => ['SUCCEEDED', 'SUCCESS'].includes(row.externalStatus)
const clearLog = () => {
  logGeneration++
  logText.value = ''
  logLoading.value = false
}
const reload = async () => {
  const current = ++generation
  loading.value = true
  try {
    const data = await Api.businessLogs(props.entry, props.objectId, pageNo.value)
    if (current !== generation) return
    rows.value = data.list
    total.value = data.total
  } finally {
    if (current === generation) loading.value = false
  }
}
const preview = async (row: Api.BusinessLog) => {
  logRejected.value = row.failureCategory === 'COMMAND_REJECTED'
  const current = ++logGeneration
  logText.value = ''
  logVisible.value = true
  logLoading.value = true
  try {
    const url = await Api.download(props.entry, props.objectId, row.executionId)
    const response = await fetch(url, { cache: 'no-store', referrerPolicy: 'no-referrer' })
    if (!response.ok) throw new Error('LOG_READ_FAILED')
    const log = await response.json()
    if (current !== logGeneration || !logVisible.value) return
    logRejected.value ||= /(^|\n)%\s*Unknown command\.?\s*(\r?\n|$)/i.test(log.stdout ?? '')
    logText.value = [
      `执行结果：${statuses[row.externalStatus] || row.externalStatus}`,
      row.commandText ? `\n下发命令：\n${row.commandText}` : '',
      '\n设备输出：',
      typeof log.stdout === 'string' ? log.stdout : '',
      log.stderr ? `\n错误输出：\n${log.stderr}` : ''
    ].join('\n')
  } catch {
    if (current === logGeneration) {
      logVisible.value = false
      message.error('日志读取失败，请刷新后重试')
    }
  } finally {
    if (current === logGeneration) logLoading.value = false
  }
}
const download = async (row: Api.BusinessLog) => {
  const link = document.createElement('a')
  link.href = await Api.download(props.entry, props.objectId, row.executionId)
  link.target = '_blank'
  link.rel = 'noopener noreferrer'
  link.click()
}
watch(
  () => [props.entry, props.objectId],
  () => {
    rows.value = []
    total.value = 0
    pageNo.value = 1
    logVisible.value = false
    clearLog()
    void reload().catch(() => {})
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  generation++
  clearLog()
})
defineExpose({ reload })
</script>
