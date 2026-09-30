<template>
  <section class="stage-gates" :aria-label="title" :aria-busy="loading">
    <div class="gate-heading">
      <span>{{ title }}</span>
      <el-button :loading="loading" :disabled="loading || isBusy()" @click="refresh">刷新门禁结果</el-button>
    </div>
    <p v-if="loading" role="status">正在读取本轮门禁结果…</p>
    <el-alert v-else-if="error" :title="error" type="warning" :closable="false" show-icon />
    <template v-else-if="workbench">
      <p class="hint">第 {{ workbench.executionRound }} 轮 · 当前规则结果；刷新不会推进阶段或发起审批。</p>
      <p v-if="!workbench.gates.length" role="status">当前阶段未配置门禁。</p>

      <!-- 准入/准出条件：与交付件清单同款分节行格式；每条引用的满足结果来自按下标对齐的规则条件诊断 -->
      <template v-for="group in gateGroups" :key="group.type">
        <template v-if="group.gates.length">
          <div class="section-title">{{ group.title }}</div>
          <article v-for="gate in group.gates" :key="gate.gateId" class="gate-block" :aria-label="gate.name">
            <div class="gate-line">
              <span class="gate-name">{{ gate.name }}</span>
              <el-tag size="small" :type="gate.evaluation.outcome === 'MATCHED' ? 'success' : 'warning'">
                {{ label(gate.evaluation.outcome) }}
              </el-tag>
            </div>
            <p v-if="gate.evaluation.reasonCode" class="hint">原因：{{ reasonLabel(gate.evaluation.reasonCode) }}</p>
            <div v-if="stateRows(gate).length" class="deliverable-lines">
              <div v-for="row in stateRows(gate)" :key="row.reference.gateReferenceId" class="deliverable-line">
                <span class="ref-type">{{ refTypeLabel(row.reference.refType) }}</span>
                <span class="deliverable-name">{{ referenceName(row.reference) }}</span>
                <el-tag v-if="row.outcome" size="small" :type="row.outcome === 'MATCHED' ? 'success' : 'warning'">
                  {{ label(row.outcome) }}
                </el-tag>
                <span v-if="row.reference.refVersion" class="ref-version">冻结版本 {{ row.reference.refVersion }}</span>
              </div>
            </div>
            <ul v-if="residualConditions(gate).length" class="cond-list">
              <li v-for="condition in residualConditions(gate)" :key="condition.key">
                规则条件{{ label(condition.outcome) }}<span v-if="condition.reasonCode">（{{ reasonLabel(condition.reasonCode) }}）</span>
              </li>
            </ul>
          </article>
        </template>
      </template>

      <!-- 交付件：门禁引用的交付件 + 任务上下文追加的当前任务交付件，同一行格式；有实例身份时给提交与查看入口 -->
      <template v-if="deliverableRows.length || taskDeliverables">
        <div class="section-title">交付件</div>
        <div v-if="deliverableRows.length" class="deliverable-lines">
          <div v-for="row in deliverableRows" :key="row.key" class="deliverable-line">
            <el-tag v-if="row.outcome" size="small" :type="row.outcome === 'MATCHED' ? 'success' : 'warning'">
              {{ label(row.outcome) }}
            </el-tag>
            <span class="deliverable-name">{{ row.name }}</span>
            <span class="deliverable-code">{{ row.code }}</span>
            <dict-tag v-if="row.status" :type="DICT_TYPE.PMS_PROJECT_DELIVERABLE_STATUS" :value="row.status" />
            <el-button v-if="row.id && !stagePending" link type="primary" @click="openDeliverable(row.id!)">提交与查看</el-button>
          </div>
        </div>
        <template v-if="taskDeliverables">
          <div class="sub-title">当前任务交付件</div>
          <p v-if="!taskDeliverables.length" class="hint">当前任务未绑定交付件；任务完成按状态机与冻结规则判定。</p>
          <div v-else class="deliverable-lines">
            <div v-for="item in taskDeliverables" :key="item.id ?? item.deliverableCode" class="deliverable-line">
              <el-tag size="small" :type="item.required ? 'danger' : 'info'">{{ item.required ? '必选' : '可选' }}</el-tag>
              <span class="deliverable-name">{{ item.name }}</span>
              <dict-tag :type="DICT_TYPE.PMS_PROJECT_DELIVERABLE_STATUS" :value="item.status ?? ''" />
              <el-button v-if="item.id && !stagePending" link type="primary" @click="openDeliverable(item.id)">提交与查看</el-button>
            </div>
          </div>
        </template>
      </template>

      <!-- 审批：按当前计划冻结的流程版本办理；发起/办理入口由 StageGateProcessPanel 按权限呈现，
           未进入阶段的准入审批发起照常放开（可用性由后端 canStart 决定） -->
      <template v-if="approvalRefs.length">
        <div class="section-title">审批</div>
        <StageGateProcessPanel v-for="reference in approvalRefs" :key="reference.gateReferenceId" ref="processPanels"
          :workbench="workbench" :reference="reference" :editing="editingId === reference.gateReferenceId"
          :disabled="loading" @edit="openForm(reference.gateReferenceId)" @changed="handleSubmitted" />
      </template>
    </template>
  </section>
  <ProjectDeliverableDialog ref="deliverableRef" :project-id="projectId" @changed="handleDeliverableChanged" />
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import {
  getStageGateWorkbench,
  type StageGateReference,
  type StageGateWorkbench
} from '@/api/pms/project/stage-gates'
import type { RuleConditionDiagnostic } from '@/api/pms/project/project-templates/rules'
import type { ProjectInstancesVO } from '@/api/pms/project/projects'
import { DICT_TYPE } from '@/utils/dict'
import StageGateProcessPanel from './StageGateProcessPanel.vue'
import ProjectDeliverableDialog from './ProjectDeliverableDialog.vue'

const props = withDefaults(defineProps<{
  projectId: number
  stageCode: string
  projectVersion?: number
  /** 面板标题：项目主档侧栏按完成条件口径展示，其余用法保持阶段门禁条件 */
  title?: string
  /** 项目实例视图：用于把引用编码解析为业务名称/交付件实例；缺省时回退展示编码 */
  instances?: ProjectInstancesVO | null
  /** 任务上下文追加的当前任务交付件；传入（含空数组）即代表侧栏处于任务选中态 */
  taskDeliverables?: ProjectInstancesVO['deliverables']
}>(), { title: '阶段门禁条件' })
const emit = defineEmits<{ changed: [] }>()
// 未进入阶段（PENDING，准入未满足）：提交与查看为业务办理入口不渲染；
// 门禁结果、审批状态与审批发起入口照常展示（发起可用性由后端 canStart 决定）
const stagePending = computed(
  () => props.instances?.stages.find(stage => stage.stageCode === props.stageCode)?.status === 'PENDING'
)
const processPanels = ref<InstanceType<typeof StageGateProcessPanel>[]>([])
const editingId = ref<number | string>()
const workbench = ref<StageGateWorkbench>()
const error = ref('')
const loading = ref(false)
const deliverableRef = ref<InstanceType<typeof ProjectDeliverableDialog>>()
let requestNo = 0
const label = (outcome: string) => ({ MATCHED: '满足', NOT_MATCHED: '不满足', UNKNOWN: '未知' }[outcome] || '未知')
// 引用类型与原因码均为后端已登记的冻结取值，这里只做展示翻译，不改变判定语义
const REF_TYPE_LABELS: Record<string, string> = {
  TASK: '任务',
  DELIVERABLE: '交付件',
  MILESTONE: '里程碑',
  STATE: '阶段完成',
  APPROVAL: '审批',
  PROCESS: '审批流程'
}
const refTypeLabel = (refType: string) => REF_TYPE_LABELS[refType] || refType
const STATE_COMPLETED_SUFFIX = '_COMPLETED'
const referenceName = (reference: StageGateReference) => {
  const code = reference.refCode
  const instances = props.instances
  if (!instances || !code) return code
  switch (reference.refType) {
    case 'TASK': return instances.tasks.find(task => task.taskCode === code)?.name || code
    case 'MILESTONE': return instances.milestones.find(item => item.milestoneCode === code)?.name || code
    case 'DELIVERABLE': return instances.deliverables.find(item => item.deliverableCode === code)?.name || code
    case 'STATE': {
      if (!code.endsWith(STATE_COMPLETED_SUFFIX)) return code
      const stageCode = code.slice(0, -STATE_COMPLETED_SUFFIX.length)
      return instances.stages.find(stage => stage.stageCode === stageCode)?.name || code
    }
    default: return code
  }
}
// 门禁规则由引用列表按顺序编译（key = conditionN），按 key 对齐每条引用的满足结果
const referenceOutcome = (gate: StageGateWorkbench['gates'][number], index: number) =>
  gate.evaluation.conditions.find(condition => condition.key === `condition${index}`)?.outcome
const residualConditions = (gate: StageGateWorkbench['gates'][number]): RuleConditionDiagnostic[] =>
  gate.evaluation.conditions.filter(
    condition => !(new RegExp('^condition\\d+$').test(condition.key)
      && Number(condition.key.slice('condition'.length)) < gate.references.length)
  )
// 准入/准出分节：按门禁类型归组，非 ENTRY 一律按准出呈现（与门禁面板既有口径一致）
const gateGroups = computed(() => {
  const gates = workbench.value?.gates || []
  return [
    { type: 'ENTRY', title: '准入条件', gates: gates.filter(gate => gate.gateType === 'ENTRY') },
    { type: 'EXIT', title: '准出条件', gates: gates.filter(gate => gate.gateType !== 'ENTRY') }
  ]
})
// 准入/准出节内只放任务/里程碑/阶段完成引用；交付件与审批分别归入对应分节
const STATE_REF_TYPES = ['TASK', 'MILESTONE', 'STATE']
const stateRows = (gate: StageGateWorkbench['gates'][number]) =>
  gate.references
    .map((reference, index) => ({ reference, outcome: referenceOutcome(gate, index) }))
    .filter(row => STATE_REF_TYPES.includes(row.reference.refType))
const deliverableRows = computed(() => {
  const rows: {
    key: string
    name: string
    code: string
    status?: string
    id?: number
    outcome?: string
  }[] = []
  for (const gate of workbench.value?.gates || []) {
    gate.references.forEach((reference, index) => {
      if (reference.refType !== 'DELIVERABLE') return
      const instance = props.instances?.deliverables.find(item => item.deliverableCode === reference.refCode)
      rows.push({
        key: `${gate.gateId}-${reference.gateReferenceId}`,
        name: instance?.name || referenceName(reference),
        code: reference.refCode,
        status: instance?.status,
        id: instance?.id,
        outcome: referenceOutcome(gate, index)
      })
    })
  }
  return rows
})
const approvalRefs = computed(() =>
  (workbench.value?.gates || [])
    .flatMap(gate => gate.references.filter(reference => ['APPROVAL', 'PROCESS'].includes(reference.refType))))
const openDeliverable = (id: number) => { void deliverableRef.value?.open(id) }
const handleDeliverableChanged = async () => { await load(); emit('changed') }
// 原因码中文名：仅覆盖各门禁事实提供者已登记的取值，未登记的原样展示
const REASON_LABELS: Record<string, string> = {
  TASK_NOT_FOUND: '任务不存在',
  TASK_NOT_DONE: '任务未完成',
  MILESTONE_NOT_FOUND: '里程碑不存在',
  MILESTONE_NOT_ACHIEVED: '里程碑未达成',
  STATE_CODE_UNKNOWN: '阶段状态引用无法识别',
  STATE_NOT_FOUND: '阶段不存在',
  STATE_NOT_DONE: '阶段未完成',
  DELIVERABLE_NOT_FOUND: '交付件不存在',
  NOT_COMPLETED: '未完成',
  REJECTED: '审批未通过',
  FACT_UNAVAILABLE: '业务事实暂不可用',
  BPM_ROUND_BOUNDARY_UNAVAILABLE: '审批轮次边界暂不可用',
  BPM_QUERY_UNKNOWN: '流程查询不可用',
  BPM_START_TIME_UNKNOWN: '流程开始时间未知',
  BPM_STATUS_UNKNOWN: '流程状态未知',
  BPM_PROVIDER_UNAVAILABLE: '流程服务暂不可用',
  BPM_ACTIVITY_UNAVAILABLE: '流程活动节点不可用'
}
const reasonLabel = (reasonCode: string) => REASON_LABELS[reasonCode] || reasonCode
const isBusy = () => processPanels.value.some(panel => panel.isBusy())
const requestLeave = async () => {
  if (isBusy()) return false
  for (const panel of processPanels.value) if (!await panel.requestLeave()) return false
  return true
}
const openForm = async (id: number | string) => {
  const current = workbench.value
  if (await requestLeave() && current === workbench.value) editingId.value = id
}
const load = async () => {
  const current = ++requestNo
  const { projectId, stageCode } = props
  workbench.value = undefined
  editingId.value = undefined
  error.value = ''
  loading.value = true
  try {
    const result = await getStageGateWorkbench(projectId, stageCode)
    if (current !== requestNo) return
    if (String(result.projectId) !== String(projectId) || result.stageCode !== stageCode || result.recoverableError
      || result.executionId == null || result.executionRound == null || result.planVersionId == null) {
      error.value = '当前阶段的计划或执行轮次暂不可用，请刷新后重试。'
      return
    }
    workbench.value = result
  } catch {
    if (current === requestNo) error.value = '门禁结果读取失败，请重试；不能视为条件已满足。'
  } finally {
    if (current === requestNo) loading.value = false
  }
}
const refresh = async () => { if (await requestLeave()) await load() }
const handleSubmitted = async () => { await load(); emit('changed') }
// Vue watch invalidates requests on context changes; stale results never replace another stage.
// https://vuejs.org/guide/essentials/watchers.html#side-effect-cleanup
watch(() => [props.projectId, props.stageCode, props.projectVersion], load, { immediate: true })
onBeforeRouteLeave(requestLeave)
onBeforeUnmount(() => { ++requestNo })
defineExpose({ refresh, requestLeave, isBusy })
</script>

<style scoped>
.stage-gates { margin-top: 16px; min-width: 0; }
.gate-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; }
.section-title { margin: 14px 0 4px; font-size: 13px; font-weight: 600; color: var(--el-text-color-primary); }
.sub-title { margin: 10px 0 4px; font-size: 12.5px; font-weight: 600; color: var(--el-text-color-regular); }
.gate-block { padding: 2px 0; overflow-wrap: anywhere; }
.gate-line { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.gate-name { font-size: 13px; font-weight: 600; color: var(--el-text-color-primary); overflow-wrap: anywhere; }
.deliverable-lines { display: flex; flex-direction: column; gap: 6px; margin-top: 6px; }
.deliverable-line { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.deliverable-name { font-size: 13px; }
.deliverable-code { font-family: 'JetBrains Mono', monospace; font-size: 12px; color: var(--el-text-color-secondary); }
.ref-type { flex: none; padding: 0 6px; font-size: 11px; line-height: 18px; color: var(--el-text-color-secondary); background: var(--el-fill-color-light); border-radius: 2px; }
.ref-version { color: var(--el-text-color-secondary); font-size: 11.5px; }
.cond-list { margin: 6px 0 0; padding: 0; list-style: none; }
.cond-list li { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; padding: 2px 0; font-size: 12.5px; }
</style>
