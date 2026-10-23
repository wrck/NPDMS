<template>
  <section class="customer-section inspection-history-panel">
    <div class="customer-section-heading">
      <h3>巡检归档历史</h3>
      <span>保留原任务及报告、处理和闭环来源引用</span>
    </div>
    <el-alert
      v-if="!customerId"
      title="保存客户后可查看巡检归档历史。"
      type="info"
      :closable="false"
      show-icon
    />
    <template v-else-if="active">
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        show-icon
        class="mb-16px"
      >
        <template #default
          ><el-button link type="primary" :disabled="loading" @click="loadHistory"
            >重新加载</el-button
          ></template
        >
      </el-alert>
      <el-table
        v-loading="loading"
        :data="rows"
        :row-key="(row: CustomerInspectionHistory) => String(row.id)"
        :empty-text="
          error ? '归档历史加载失败，请重新加载' : loading ? '正在加载归档历史' : '暂无巡检归档历史'
        "
      >
        <el-table-column type="expand" width="48">
          <template #default="{ row }">
            <div class="reference-details">
              <h4>归档来源引用</h4>
              <el-descriptions :column="smallScreen ? 1 : 2" border class="mb-16px">
                <el-descriptions-item label="原任务 ID">{{ row.taskId }}</el-descriptions-item>
                <el-descriptions-item label="报告 ID">{{ row.reportId }}</el-descriptions-item>
              </el-descriptions>
              <el-table :data="row.references" :row-key="referenceKey" empty-text="暂无来源引用">
                <el-table-column label="来源类型" min-width="130"
                  ><template #default="{ row: reference }">{{
                    kindLabel(reference.kind)
                  }}</template></el-table-column
                >
                <el-table-column prop="sourceId" label="来源记录 ID" min-width="190" />
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="taskCode" label="任务编号" min-width="190" />
        <el-table-column prop="taskName" label="任务名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="projectName" label="项目" min-width="180" show-overflow-tooltip />
        <el-table-column label="归档时间" min-width="170"
          ><template #default="{ row }">{{
            row.archivedAt ? formatDate(row.archivedAt) : '未提供'
          }}</template></el-table-column
        >
        <el-table-column prop="taskVersion" label="归档任务版本" min-width="120" />
        <el-table-column label="引用数量" width="100"
          ><template #default="{ row }">{{ row.references.length }}</template></el-table-column
        >
        <el-table-column label="操作" width="110" :fixed="smallScreen ? false : 'right'"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="openTask(row.taskId)"
              >查看原任务</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <Pagination
        v-model:page="query.pageNo"
        v-model:limit="query.pageSize"
        :total="total"
        @pagination="loadHistory"
      />
      <p class="history-note">来源记录保持只读，查看原巡检任务仍按当前任务权限核验。</p>
    </template>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useMediaQuery } from '@vueuse/core'
import {
  getCustomerInspectionHistory,
  type CustomerInspectionHistory
} from '@/api/pms/customer/inspection-history'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'CustomerInspectionHistoryPanel' })
const props = withDefaults(defineProps<{ customerId?: string | number; active?: boolean }>(), {
  active: true
})
const router = useRouter()
const smallScreen = useMediaQuery('(max-width: 767px)')
const query = reactive({ pageNo: 1, pageSize: 10 })
const rows = ref<CustomerInspectionHistory[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
let sequence = 0
const kinds: Record<string, string> = {
  DEVICE: '设备',
  EXECUTION: '执行记录',
  RESULT: '判定结果',
  REPORT: '巡检报告',
  ANNOTATION: '问题标注',
  ISSUE: '问题待办',
  REMEDIATION: '处理与闭环记录',
  EVIDENCE: '处理证据'
}
const kindLabel = (kind: string) => kinds[kind] || kind
const referenceKey = (reference: CustomerInspectionHistory['references'][number]) =>
  `${reference.kind}:${reference.sourceId}`
const openTask = (taskId: string | number) =>
  router.push(`/pms/inspection/tasks/${encodeURIComponent(String(taskId))}`)
const loadHistory = async () => {
  if (!props.customerId || !props.active) return
  const current = ++sequence
  const customerId = String(props.customerId)
  loading.value = true
  error.value = ''
  try {
    const page = await getCustomerInspectionHistory(customerId, { ...query })
    if (current !== sequence || customerId !== String(props.customerId) || !props.active) return
    if (
      !page ||
      !Array.isArray(page.list) ||
      !page.list.every((row) => Array.isArray(row.references))
    )
      throw new Error('归档历史返回内容不完整')
    rows.value = page.list
    total.value = page.total
  } catch {
    if (current !== sequence || customerId !== String(props.customerId) || !props.active) return
    rows.value = []
    total.value = 0
    error.value = '巡检归档历史加载失败，请重新加载。'
  } finally {
    if (current === sequence) loading.value = false
  }
}
watch(
  () => [String(props.customerId ?? ''), props.active],
  () => {
    sequence++
    rows.value = []
    total.value = 0
    error.value = ''
    loading.value = false
    query.pageNo = 1
    if (props.customerId && props.active) void loadHistory()
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  sequence++
})
</script>

<style scoped>
.inspection-history-panel {
  min-width: 0;
}
.customer-section-heading {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 12px;
}
.customer-section-heading h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 24px;
  color: var(--el-text-color-primary);
}
.customer-section-heading span,
.history-note {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 20px;
}
.reference-details {
  padding: 16px;
}
.reference-details h4 {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
}
.history-note {
  margin: 12px 0 0;
}
@media (max-width: 767px) {
  .customer-section-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }
}
</style>
