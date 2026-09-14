<template>
  <el-dialog
    v-model="visible"
    title="执行历史与规则结果"
    width="min(1100px, 96vw)"
    destroy-on-close
  >
    <p>只读取各轮冻结版本和已记录结果；不会重新求值或推进项目。办理说明按办理权限展示。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-button :loading="loading" @click="load">刷新执行历史</el-button>
    <div v-loading="loading">
      <section v-for="plan in history?.plans ?? []" :key="plan.id" class="plan-history">
        <h4
          >项目计划 v{{ plan.revisionNo }} ·
          {{ plan.status === 'EFFECTIVE' ? '当前有效' : '历史版本' }}</h4
        >
        <p
          >生效：{{ formatDate(plan.effectiveAt)
          }}<span v-if="plan.closedAt"> · 项目收口：{{ formatDate(plan.closedAt) }}</span></p
        >
        <template v-if="plan.closure">
          <p
            >收口规则：{{ outcomeLabel(plan.closure.outcome) }} ·
            {{ plan.closure.reasonCode || '无异常' }}</p
          >
          <el-table :data="plan.closure.conditions" border>
            <el-table-column prop="path" label="条件位置" />
            <el-table-column prop="component" label="执行组件" />
            <el-table-column label="条件结果"
              ><template #default="{ row }">{{
                outcomeLabel(row.outcome)
              }}</template></el-table-column
            >
            <el-table-column prop="reasonCode" label="原因" />
          </el-table>
        </template>
      </section>
      <el-table
        :data="history?.rounds ?? []"
        row-key="id"
        empty-text="暂无执行轮次"
        class="mt-16px"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="round-evidence">
              <p
                >计划版本：v{{ row.planRevisionNo ?? '未知' }} · 执行编号：{{ row.id }} ·
                {{ row.current ? '当前轮次' : '历史轮次' }}</p
              >
              <p
                >准入：{{ row.admittedAt ? formatDate(row.admittedAt) : '未准入' }} · 开始：{{
                  row.startedAt ? formatDate(row.startedAt) : '未开始'
                }}
                · 结束：{{ row.endedAt ? formatDate(row.endedAt) : '未结束' }}</p
              >
              <p
                >提交：{{ row.submittedAt ? formatDate(row.submittedAt) : '未提交'
                }}<span v-if="row.submittedBy"> · 提交人编号：{{ row.submittedBy }}</span></p
              >
              <p v-if="row.canViewSubmissionNote" class="submission-note"
                >办理说明：{{ row.submissionNote || '无手工说明' }}</p
              >
              <p v-else>办理说明需要办理权限。</p>
              <section v-for="evaluation in row.evaluations" :key="evaluation.purpose">
                <h4
                  >{{ evaluation.purpose === 'completion' ? '完成' : '退出' }} ·
                  {{ evaluation.name }} · {{ outcomeLabel(evaluation.result.outcome) }}</h4
                >
                <el-table :data="evaluation.result.conditions" border>
                  <el-table-column prop="path" label="条件位置" />
                  <el-table-column prop="component" label="执行组件" />
                  <el-table-column label="条件结果"
                    ><template #default="{ row: condition }">{{
                      outcomeLabel(condition.outcome)
                    }}</template></el-table-column
                  >
                  <el-table-column prop="reasonCode" label="原因" />
                </el-table>
                <p v-if="evaluation.result.steps.length"
                  >执行步骤：{{ evaluation.result.steps.join(' → ') }}</p
                >
              </section>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="nodeCode" label="节点编码" />
        <el-table-column prop="name" label="冻结名称" />
        <el-table-column prop="roundNo" label="轮次" width="80" />
        <el-table-column label="状态" width="110"
          ><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column
        >
        <el-table-column label="提交时间" min-width="175"
          ><template #default="{ row }">{{
            row.submittedAt ? formatDate(row.submittedAt) : '未提交'
          }}</template></el-table-column
        >
        <el-table-column label="结束时间" min-width="175"
          ><template #default="{ row }">{{
            row.endedAt ? formatDate(row.endedAt) : '未结束'
          }}</template></el-table-column
        >
      </el-table>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { formatDate } from '@/utils/formatTime'
import {
  getExecutionHistory,
  type ExecutionHistory
} from '@/api/pms/project/projects/nodeExecutions'
const props = defineProps<{ projectId: number }>()
const visible = defineModel<boolean>({ default: false })
const history = ref<ExecutionHistory>()
const error = ref('')
const loading = ref(false)
let generation = 0
const outcomeLabel = (value: string) =>
  ({ MATCHED: '满足', NOT_MATCHED: '不满足', UNKNOWN: '未知' })[value] ?? value
const statusLabel = (value: string) =>
  ({ PENDING: '等待准入', ACTIVE: '进行中', DONE: '已完成', TERMINATED: '已终止' })[value] ?? value
const load = async () => {
  const request = ++generation
  history.value = undefined
  error.value = ''
  loading.value = true
  try {
    const result = await getExecutionHistory(props.projectId)
    if (request === generation) history.value = result
  } catch {
    if (request === generation) error.value = '执行历史加载失败，请重试。'
  } finally {
    if (request === generation) loading.value = false
  }
}
watch(
  () => [visible.value, props.projectId],
  () => {
    if (visible.value) void load()
    else {
      generation++
      history.value = undefined
      loading.value = false
    }
  }
)
</script>

<style scoped>
.plan-history {
  margin-top: 16px;
}
.round-evidence {
  padding: 12px 24px;
}
.submission-note {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
