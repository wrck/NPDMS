<template>
  <ContentWrap v-hasPermi="['pms:integration:view']">
    <div class="mb-4">
      <el-button :disabled="busy" :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-4" />
    <el-descriptions v-if="result" :title="result.title" border class="mb-4" :column="5">
      <el-descriptions-item label="读取">{{ result.counts.read }}</el-descriptions-item>
      <el-descriptions-item label="插入">{{ result.counts.inserted }}</el-descriptions-item>
      <el-descriptions-item label="更新">{{ result.counts.updated }}</el-descriptions-item>
      <el-descriptions-item label="未变">{{ result.counts.unchanged }}</el-descriptions-item>
      <el-descriptions-item label="删除">{{ result.counts.deleted }}</el-descriptions-item>
    </el-descriptions>
    <el-table v-loading="loading" :data="rows" row-key="id" class="integration-table">
      <el-table-column prop="taskCode" label="任务编码" width="140" />
      <el-table-column label="任务名称" min-width="200">
        <template #default="{ row }">{{ names[row.taskCode] || row.taskCode }}</template>
      </el-table-column>
      <el-table-column prop="connectionId" label="连接 ID" min-width="190" />
      <el-table-column label="执行阶段" min-width="300">
        <template #default="{ row }">
          <el-select
            v-if="row.taskCode === 'BASE_P15'"
            v-model="row.inputStages"
            multiple
            clearable
            collapse-tags
            collapse-tags-tooltip
            placeholder="请选择执行阶段"
            class="w-full"
            :disabled="busy || loading || hasActiveRun"
            aria-label="P15执行阶段"
          >
            <el-option v-for="stage in p15Stages" :key="stage.value" v-bind="stage" />
          </el-select>
          <span v-else>完整流程</span>
        </template>
      </el-table-column>
      <el-table-column label="每批数量" width="190">
        <template #default="{ row }">
          <el-input-number
            v-model="row.inputBatchSize"
            :min="1"
            :max="2000"
            :precision="0"
            :disabled="busy || hasActiveRun"
            :aria-label="`${names[row.taskCode] || row.taskCode}每批数量`"
          />
        </template>
      </el-table-column>
      <el-table-column prop="maxRows" label="读取上限" width="120" />
      <el-table-column label="活动 Run" min-width="190">
        <template #default="{ row }">{{ row.activeRunId ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button
            v-hasPermi="['pms:integration:execute']"
            link
            type="primary"
            :disabled="busy || loading || hasActiveRun"
            @click="submit(row, true)"
            >预览</el-button
          >
          <el-button
            v-hasPermi="['pms:integration:execute']"
            link
            type="primary"
            :disabled="busy || loading || hasActiveRun"
            @click="submit(row, false)"
            >执行</el-button
          >
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>
</template>
<script setup lang="ts">
import * as api from '@/api/pms/integration/base-sync'
const p15Stages = [
  { value: 'ShipmentContract', label: '发货合同基础数据' },
  { value: 'Package', label: '装箱基础数据' },
  { value: 'ShipmentEvent', label: '发货历史基础数据' },
  { value: 'DeviceRelationship', label: '设备历史关系' },
  { value: 'Contract', label: '合同基础数据' },
  { value: 'ContractOrganization', label: '合同组织关系' },
  { value: 'ShipmentContractOrganization', label: '发货合同组织关系' },
  { value: 'OrderContractRelation', label: '订单—合同关系' },
  { value: 'ShipmentOrderLine', label: '发货—订单行关系' }
]
type Row = api.Registration & { inputBatchSize?: number; inputStages?: string[] }
const rows = ref<Row[]>([])
const names = ref<Record<string, string>>({})
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const result = ref<{ title: string; counts: api.SyncCounts }>()
const hasActiveRun = computed(() => rows.value.some((row) => row.activeRunId != null))
const registrations = async () => {
  const inputs = new Map(rows.value.map((row) => [String(row.id), row]))
  rows.value = (await api.getRegistrations()).map((row) => ({
    ...row,
    inputBatchSize: inputs.has(String(row.id))
      ? inputs.get(String(row.id))?.inputBatchSize
      : row.batchSize,
    inputStages:
      inputs.get(String(row.id))?.inputStages ??
      (row.taskCode === 'BASE_P15' ? p15Stages.map((stage) => stage.value) : undefined)
  }))
}
const load = async () => {
  if (busy.value || loading.value) return
  loading.value = true
  error.value = ''
  try {
    const [tasks] = await Promise.all([api.getTasks(), registrations()])
    names.value = Object.fromEntries(tasks.map((task) => [task.code, task.name]))
  } catch {
    error.value = '基础同步任务加载失败'
  } finally {
    loading.value = false
  }
}
const submit = async (row: Row, preview: boolean) => {
  if (busy.value || loading.value || hasActiveRun.value) return
  const stages = row.taskCode === 'BASE_P15' ? row.inputStages : undefined
  if (row.taskCode === 'BASE_P15' && !stages?.length) {
    error.value = 'P15请至少选择一个执行阶段'
    return
  }
  const batchSize = row.inputBatchSize ?? undefined
  if (
    batchSize !== undefined &&
    (!Number.isInteger(batchSize) || batchSize < 1 || batchSize > 2000)
  ) {
    error.value = '每批数量须为1至2000的整数'
    return
  }
  busy.value = true
  error.value = ''
  result.value = undefined
  try {
    const counts = await api.run({ preview, taskId: row.id, batchSize, stages })
    const stageSummary =
      stages && stages.length < p15Stages.length
        ? p15Stages
            .filter((stage) => stages.includes(stage.value))
            .map((stage) => stage.label)
            .join('、')
        : '全部阶段'
    result.value = {
      title: `${names.value[row.taskCode] || row.taskCode} · ${stageSummary} · ${preview ? '预览' : '执行'}`,
      counts
    }
  } catch {
    error.value = '请求未成功返回，请刷新核对任务状态后再操作'
  } finally {
    try {
      await registrations()
    } catch {
      error.value ||= '任务状态刷新失败'
    }
    busy.value = false
  }
}
onMounted(load)
</script>
