<template>
  <ContentWrap v-loading="loading">
    <div class="stage-status-header">
      <div>
        <h3>阶段状态</h3>
        <p>阶段按各自准入条件激活，可以同时进行；刷新只读取状态。</p>
      </div>
      <div>
        <el-button v-hasPermi="['pms:project-plan:manage']" @click="planVisible = true"
          >项目计划</el-button
        >
        <el-button @click="historyVisible = true">执行历史与规则结果</el-button>
        <el-button v-hasPermi="['pms:project-plan:rework']" @click="reworkVisible = true">选择性返工</el-button>
        <el-button :loading="loading" @click="refresh">刷新阶段状态</el-button>
      </div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-else :data="stages" row-key="stageCode" empty-text="尚无阶段实例">
      <el-table-column prop="stageCode" label="阶段编码" />
      <el-table-column prop="name" label="阶段名称" />
      <el-table-column label="阶段办理">
        <template #default="{ row }">
          <el-button link type="primary" @click="openWorkbench(row.stageCode)">查看／办理</el-button>
        </template>
      </el-table-column>
      <el-table-column label="状态">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="本轮办理">
        <template #default="{ row }">
          <el-button
            v-if="executionFor(row.stageCode)?.canSubmit"
            link
            type="primary"
            @click="openSubmission(row.stageCode)"
            >提交办理结果</el-button
          >
          <span v-else-if="executionFor(row.stageCode)"
            >第 {{ executionFor(row.stageCode)?.roundNo }} 轮</span
          >
        </template>
      </el-table-column>
    </el-table>
    <el-dialog
      v-model="submissionVisible"
      title="提交本轮阶段办理结果"
      width="min(560px, 94vw)"
      :close-on-click-modal="false"
    >
      <p
        >{{ selected?.name }} · 第
        {{ selected?.roundNo }} 轮。提交后由冻结规则判断完成与退出，不覆盖历史轮次。</p
      >
      <el-alert v-if="submissionError" :title="submissionError" type="error" :closable="false" />
      <el-input
        v-model="note"
        type="textarea"
        :rows="4"
        :maxlength="2000"
        :disabled="submitting"
        placeholder="说明本轮实际完成的工作"
        aria-label="本轮办理说明"
      />
      <template #footer
        ><el-button :disabled="submitting" @click="submissionVisible = false">取消</el-button
        ><el-button type="primary" :loading="submitting" :disabled="!note.trim()" @click="submit"
          >提交本轮结果</el-button
        ></template
      >
    </el-dialog>
    <ProjectNodeWorkbenchDrawer
      v-model="workbenchVisible"
      :project="project"
      :selection="workbenchSelection"
      @changed="refresh"
    />
    <ProjectExecutionHistory v-model="historyVisible" :project-id="projectId" />
    <ProjectPlanEditor v-model="planVisible" :project-id="projectId" @changed="refresh" />
    <ProjectReworkPanel v-model="reworkVisible" :project-id="projectId" @changed="refresh" />
  </ContentWrap>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { getProjectInstances, type ProjectInstancesVO, type ProjectMasterVO } from '@/api/pms/project/projects'
import {
  getNodeExecutions,
  submitStageExecution,
  type NodeExecution
} from '@/api/pms/project/projects/nodeExecutions'
import { useMessage } from '@/hooks/web/useMessage'
import ProjectExecutionHistory from './ProjectExecutionHistory.vue'
import ProjectPlanEditor from './ProjectPlanEditor.vue'
import ProjectReworkPanel from './ProjectReworkPanel.vue'
import ProjectNodeWorkbenchDrawer from './ProjectNodeWorkbenchDrawer.vue'
import type { ProjectFlowSelection } from './project-flow'

const props = defineProps<{ projectId: number; project: ProjectMasterVO }>()
const emit = defineEmits<{ changed: [] }>()
const stages = ref<ProjectInstancesVO['stages']>([])
const loading = ref(false)
const error = ref('')
const executions = ref<NodeExecution[]>([])
const submissionVisible = ref(false)
const historyVisible = ref(false)
const planVisible = ref(false)
const reworkVisible = ref(false)
const submissionError = ref('')
const submitting = ref(false)
const selected = ref<NodeExecution>()
const note = ref('')
const workbenchVisible = ref(false)
const workbenchSelection = ref<ProjectFlowSelection>()
const openWorkbench = (stageCode: string) => {
  if (!stages.value.some((stage) => stage.stageCode === stageCode)) return
  workbenchSelection.value = { kind: 'stage', stageCode }
  workbenchVisible.value = true
}
const message = useMessage()
let submissionKey = ''
watch(note, () => {
  if (!submitting.value) submissionKey = crypto.randomUUID()
})
const executionFor = (code: string) =>
  executions.value.find((item) => item.nodeKind === 'STAGE' && item.nodeCode === code)
const openSubmission = (code: string) => {
  const execution = executionFor(code)
  if (!execution?.canSubmit) return
  selected.value = execution
  submissionError.value = ''
  note.value = ''
  submissionKey = crypto.randomUUID()
  submissionVisible.value = true
}
const submit = async () => {
  if (!selected.value || !note.value.trim() || submitting.value) return
  submitting.value = true
  submissionError.value = ''
  try {
    await submitStageExecution(props.projectId, selected.value, note.value.trim(), submissionKey)
    submissionVisible.value = false
    message.success('本轮办理结果已提交')
    await refresh()
  } catch {
    submissionError.value =
      '提交未成功。请保留本轮说明重试；如版本已变化，请关闭窗口并刷新阶段状态。'
  } finally {
    submitting.value = false
  }
}
let requestSequence = 0
const statusLabel = (status: string) =>
  ({ PENDING: '等待准入', ACTIVE: '进行中', DONE: '已完成', TERMINATED: '已终止' })[status] ??
  status
const load = async () => {
  const request = ++requestSequence
  loading.value = true
  error.value = ''
  try {
    const [result, currentExecutions] = await Promise.all([
      getProjectInstances(props.projectId),
      getNodeExecutions(props.projectId)
    ])
    if (request === requestSequence) {
      stages.value = result.stages
      executions.value = currentExecutions
    }
  } catch {
    if (request === requestSequence) error.value = '阶段状态加载失败，请刷新重试。'
  } finally {
    if (request === requestSequence) loading.value = false
  }
}
const refresh = async () => {
  await load()
  if (!error.value) emit('changed')
}
watch([() => props.projectId, () => props.project.version], load, { immediate: true })
</script>

<style scoped>
.stage-status-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.stage-status-header h3 {
  margin: 0;
}
.stage-status-header p {
  color: var(--el-text-color-secondary);
}
</style>
