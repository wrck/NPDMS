<template>
  <ContentWrap v-loading="loading">
    <div class="panel-header">
      <div>
        <div class="panel-title"><Icon icon="ep:guide" />阶段门禁与推进</div>
        <div class="panel-subtitle">按项目冻结模板检查当前阶段准出条件</div>
      </div>
      <el-button @click="loadReadiness"><Icon icon="ep:refresh" />刷新</el-button>
    </div>

    <template v-if="readiness">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="当前阶段">{{ readiness.currentStage }}</el-descriptions-item>
        <el-descriptions-item label="目标阶段">{{
          readiness.nextStage || '已到末阶段'
        }}</el-descriptions-item>
        <el-descriptions-item label="推进状态">
          <el-tag :type="readiness.advanceAllowed ? 'success' : 'warning'">
            {{ readiness.advanceAllowed ? '可以推进' : '尚未就绪' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="readiness.guidance"
        :title="readiness.guidance"
        :type="readiness.advanceAllowed ? 'success' : 'info'"
        :closable="false"
        show-icon
        class="guidance"
      />

      <el-empty v-if="!readiness.gates.length" description="当前阶段没有准出门禁" />
      <div v-else class="gate-list">
        <el-card v-for="gate in readiness.gates" :key="gate.gateId" shadow="never">
          <template #header>
            <div class="gate-header">
              <div>
                <strong>{{ gate.gateCode }} · {{ gate.name }}</strong>
                <span class="gate-status">{{ gate.status }}</span>
              </div>
              <el-tag :type="gate.satisfied ? 'success' : 'warning'">
                {{ gate.satisfied ? '已满足' : '待处理' }}
              </el-tag>
            </div>
          </template>

          <div
            v-for="reference in gate.references"
            :key="reference.gateReferenceId"
            class="reference"
          >
            <div class="reference-fact">
              <div>
                <el-tag size="small" effect="plain">{{ reference.refType }}</el-tag>
                <span class="reference-code">{{ reference.refCode }}</span>
              </div>
              <el-tag :type="outcomeTag(reference.fact.outcome)" size="small">
                {{ outcomeLabel(reference.fact.outcome) }}
              </el-tag>
            </div>
            <div class="fact-meta">
              Owner：{{ reference.fact.providerKey }} · 事实版本：{{
                reference.fact.factVersion || '-'
              }}
              <span v-if="reference.fact.unmetCode"> · {{ reference.fact.unmetCode }}</span>
            </div>

            <div v-if="canStartProcess(reference)" class="process-action">
              <el-select
                v-model="selectedDefinitions[reference.gateReferenceId]"
                :loading="definitionLoading[reference.gateReferenceId]"
                placeholder="默认启动最新流程定义"
                clearable
                @visible-change="(visible) => visible && loadDefinitions(reference.gateReferenceId)"
              >
                <el-option label="默认：最新生效定义" value="__LATEST__" />
                <el-option
                  v-for="definition in definitions[reference.gateReferenceId] || []"
                  :key="definition.processDefinitionId"
                  :label="`${definition.name}（${definition.processDefinitionId}）`"
                  :value="definition.processDefinitionId"
                  :disabled="!definition.selectable"
                />
              </el-select>
              <el-button
                v-hasPermi="['pms:project:update']"
                type="primary"
                :loading="startingReferenceId === reference.gateReferenceId"
                @click="startProcess(reference.gateReferenceId)"
              >
                发起审批流程
              </el-button>
            </div>
            <el-alert
              v-if="startedProcesses[reference.gateReferenceId]"
              type="success"
              :closable="false"
              show-icon
              class="process-result"
              :title="`流程已启动：${startedProcesses[reference.gateReferenceId].processInstanceId}`"
              :description="`实际定义：${startedProcesses[reference.gateReferenceId].processDefinitionId}`"
            />
          </div>
        </el-card>
      </div>

      <div class="advance-action">
        <el-alert
          v-if="readiness.currentStage === 'S4'"
          title="进入 S5 后，验收范围绑定由既有 COM/ACC 正向链同步完成。"
          type="info"
          :closable="false"
          show-icon
        />
        <el-button
          v-if="readiness.nextStage"
          v-hasPermi="['pms:project:update']"
          type="primary"
          :disabled="!readiness.advanceAllowed"
          :loading="advancing"
          @click="advanceStage"
        >
          推进至 {{ readiness.nextStage }}
        </el-button>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="暂无阶段门禁数据" />
  </ContentWrap>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import * as ProjectsApi from '@/api/pms/project/projects'
import type {
  ProjectStageAdvanceReadinessVO,
  ProjectStageGateProcessDefinitionVO,
  ProjectStageGateProcessStartVO,
  ProjectStageGateOutcome
} from '@/api/pms/project/projects'

defineOptions({ name: 'ProjectStageGatePanel' })

const props = defineProps<{ projectId: number }>()
const emit = defineEmits<{ advanced: [] }>()
const message = useMessage()

const loading = ref(false)
const advancing = ref(false)
const startingReferenceId = ref<number>()
const readiness = ref<ProjectStageAdvanceReadinessVO>()
const definitions = reactive<Record<number, ProjectStageGateProcessDefinitionVO[]>>({})
const definitionLoading = reactive<Record<number, boolean>>({})
const selectedDefinitions = reactive<Record<number, string>>({})
const startedProcesses = reactive<Record<number, ProjectStageGateProcessStartVO>>({})

const outcomeLabel = (outcome: ProjectStageGateOutcome) =>
  ({
    SATISFIED: '已满足',
    UNSATISFIED: '未满足',
    VERSION_CONFLICT: '版本冲突',
    DEPENDENCY_UNAVAILABLE: '依赖不可用'
  })[outcome]

const outcomeTag = (outcome: ProjectStageGateOutcome) =>
  outcome === 'SATISFIED' ? 'success' : outcome === 'UNSATISFIED' ? 'warning' : 'danger'

const canStartProcess = (
  reference: ProjectStageAdvanceReadinessVO['gates'][number]['references'][number]
) => reference.allowedActions.includes('START_PROCESS')

const loadReadiness = async () => {
  loading.value = true
  try {
    readiness.value = await ProjectsApi.getProjectStageAdvanceReadiness(props.projectId)
  } finally {
    loading.value = false
  }
}

const loadDefinitions = async (gateReferenceId: number) => {
  if (definitions[gateReferenceId] || definitionLoading[gateReferenceId]) return
  definitionLoading[gateReferenceId] = true
  try {
    definitions[gateReferenceId] = await ProjectsApi.getProjectStageGateProcessDefinitions(
      props.projectId,
      gateReferenceId
    )
  } finally {
    definitionLoading[gateReferenceId] = false
  }
}

const startProcess = async (gateReferenceId: number) => {
  if (!readiness.value) return
  startingReferenceId.value = gateReferenceId
  try {
    const selected = selectedDefinitions[gateReferenceId]
    startedProcesses[gateReferenceId] = await ProjectsApi.startProjectStageGateProcess(
      props.projectId,
      gateReferenceId,
      readiness.value.projectVersion,
      crypto.randomUUID(),
      selected && selected !== '__LATEST__' ? selected : undefined
    )
    message.success('审批流程已发起，请在流程工作台完成审批')
    await loadReadiness()
  } finally {
    startingReferenceId.value = undefined
  }
}

const advanceStage = async () => {
  if (!readiness.value?.advanceAllowed) return
  advancing.value = true
  try {
    const result = await ProjectsApi.advanceProjectStage(
      props.projectId,
      readiness.value,
      crypto.randomUUID()
    )
    message.success(`项目已推进至 ${result.afterStage}`)
    await loadReadiness()
    emit('advanced')
  } finally {
    advancing.value = false
  }
}

onMounted(loadReadiness)
</script>

<style scoped lang="scss">
.panel-header,
.gate-header,
.reference-fact,
.process-action,
.advance-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
}

.panel-subtitle,
.fact-meta,
.gate-status {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.guidance,
.gate-list,
.advance-action {
  margin-top: 16px;
}

.gate-list {
  display: grid;
  gap: 12px;
}

.gate-status {
  margin-left: 8px;
}

.reference + .reference {
  padding-top: 14px;
  margin-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.reference-code {
  margin-left: 8px;
  font-weight: 500;
}

.process-action {
  justify-content: flex-start;
  margin-top: 12px;
}

.process-action .el-select {
  width: min(520px, 100%);
}

.process-result {
  margin-top: 12px;
}

.advance-action {
  align-items: flex-end;
}

.advance-action .el-alert {
  flex: 1;
}

@media (max-width: 767px) {
  .process-action,
  .advance-action {
    align-items: stretch;
    flex-direction: column;
  }

  .process-action .el-select {
    width: 100%;
  }
}
</style>
