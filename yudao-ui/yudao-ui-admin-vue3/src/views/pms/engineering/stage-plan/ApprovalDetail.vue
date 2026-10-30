<template>
  <section v-loading="loading" class="plan-approval">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-else-if="batch">
      <h3>施工计划审批内容</h3>
      <dl class="plan-facts">
        <div><dt>计划批次</dt><dd>#{{ batch.id }}</dd></div>
        <div><dt>项目编号</dt><dd>{{ batch.projectId }}</dd></div>
        <div><dt>本版本工期</dt><dd>{{ batch.baselineStart }} 至 {{ batch.baselineEnd }}</dd></div>
      </dl>
      <p class="plan-reason">调整原因：{{ batch.remark || '无' }}</p>
      <el-table :data="batch.items" row-key="id">
        <el-table-column prop="phaseName" label="阶段" min-width="140" />
        <el-table-column prop="planStart" label="计划开始" width="130" />
        <el-table-column prop="planEnd" label="计划结束" width="130" />
        <el-table-column prop="remark" label="备注" min-width="140" />
      </el-table>
      <h4>任务安排</h4>
      <el-table :data="batch.tasks || []" row-key="taskId">
        <el-table-column prop="stageCode" label="阶段编码" width="100" />
        <el-table-column prop="name" label="任务" min-width="160" />
        <el-table-column prop="planStart" label="计划开始" width="130" />
        <el-table-column prop="planEnd" label="计划结束" width="130" />
        <el-table-column prop="acceptanceTime" label="计划验收时间" width="140" />
      </el-table>
    </template>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { getStagePlanBatch, type StagePlanBatchVO } from '@/api/pms/engineering/stage-plan'

// BPM businessKey is the batch identity, never a project identity or a request for the latest plan.
const props = defineProps<{ id: string | number }>()
const batch = ref<StagePlanBatchVO>()
const loading = ref(false)
const error = ref('')
let sequence = 0
watch(() => props.id, async (id) => {
  const current = ++sequence
  batch.value = undefined
  error.value = ''
  const batchId = Number(id)
  if (!Number.isSafeInteger(batchId) || batchId <= 0) { error.value = '计划批次编号无效'; return }
  loading.value = true
  try {
    const value = await getStagePlanBatch(batchId)
    if (current === sequence) batch.value = value
  } catch {
    if (current === sequence) error.value = '无法读取本次审批的计划内容，请确认项目权限后重试'
  } finally {
    if (current === sequence) loading.value = false
  }
}, { immediate: true })
</script>

<style scoped>
.plan-approval { min-width: 0; }
.plan-facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 16px; }
.plan-facts dt { color: var(--el-text-color-secondary); }
.plan-facts dd { margin: 8px 0 0; }
.plan-reason { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
