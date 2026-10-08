<template>
  <ContentWrap class="schedule-workspace" v-loading="loading" :body-style="{ padding: '20px' }">
    <header class="schedule-heading">
      <div
        ><h3>施工计划</h3><p>从工期要求倒排阶段，在阶段内安排任务，确认后提交服务经理审核。</p></div
      >
      <div class="schedule-actions">
        <!-- 视图级操作收口到工作区底部吸附操作栏（businessActionBar 协议）；独立页签（计划进度）原地渲染 -->
        <Teleport :to="barTarget || 'body'" :disabled="!barTarget">
          <el-button :disabled="dirty || acting" @click="load()">刷新</el-button>
          <el-button :disabled="acting" @click="durationVisible = true">{{
            durationPlan ? '管理工期' : '录入工期'
          }}</el-button>
        </Teleport>
      </div>
    </header>
    <dl class="schedule-facts">
      <div
        ><dt>签约方式</dt><dd>{{ signingLabel }}</dd
        ><small>{{ project.contractNo || '尚未关联合同' }}</small></div
      >
      <div
        ><dt>工勘要求结束日期</dt><dd>{{ project.projectEndDate || '未登记' }}</dd
        ><small>来自工前准备</small></div
      >
      <div
        ><dt>当前生效工期</dt
        ><dd>{{ durationPlan ? `${durationPlan.currentRevision.durationDays} 天` : '未录入' }}</dd
        ><small>{{
          durationPlan
            ? `${durationPlan.currentRevision.startDate} 至 ${durationPlan.currentRevision.endDate}`
            : '录入后可制定施工计划'
        }}</small></div
      >
      <div
        ><dt>本版倒排截止日期</dt><dd>{{ calculationInput.anchorEnd || '尚未推算' }}</dd
        ><small>{{
          calculationInput.signingMethod === 'DIRECT_SIGN'
            ? '来自回款节点计划验收时间'
            : calculationInput.anchorEnd
              ? '来自本版工期要求'
              : '创建计划后读取权威输入'
        }}</small></div
      >
    </dl>
    <el-alert
      v-if="errorText"
      :title="errorText"
      type="error"
      :closable="false"
      class="schedule-notice"
    />
    <el-alert
      v-if="tightSchedule"
      title="当前生效计划工期不足3个日历月，请落实计划并跟进发货。"
      type="warning"
      :closable="false"
      class="schedule-notice"
    />
    <el-alert
      v-if="remainingTimeHint"
      :title="remainingTimeHint"
      type="info"
      :closable="false"
      class="schedule-notice"
    />
    <div class="schedule-toolbar">
      <el-select
        :model-value="selectedId"
        aria-label="施工计划版本"
        placeholder="选择计划版本"
        :disabled="acting || loading"
        @change="selectBatch"
      >
        <el-option
          v-for="item in batches"
          :key="item.id"
          :value="item.id!"
          :label="`计划 #${item.id} · ${versionLabel(item)}`"
        />
      </el-select>
      <Teleport :to="barTarget || 'body'" :disabled="!barTarget">
        <el-button
          v-hasPermi="['pms:imp-stage-plan:create']"
          :disabled="dirty || acting || loading || hasActiveDraft || !durationPlan"
          @click="createDraft"
          >{{ currentEffective ? '新建调整版本' : '制定施工计划' }}</el-button
        >
        <el-button
          v-if="currentEffective && batch?.id !== currentEffective.id"
          :disabled="acting || loading"
          @click="showComparison"
          >与生效计划对比</el-button
        >
      </Teleport>
      <el-tag
        v-if="batch"
        :type="batch.status === 2 ? 'success' : batch.status === 1 ? 'warning' : 'info'"
        >{{ versionLabel(batch) }}</el-tag
      >
      <span v-if="dirty" class="dirty-label">有未保存调整</span>
    </div>
    <template v-if="batch">
      <el-alert
        v-if="batch.status === 3"
        :title="`审批未通过：${batch.rejectReason || '请调整后重新提交'}`"
        type="warning"
        :closable="false"
        class="schedule-notice"
      />
      <el-alert
        v-if="staleDuration"
        title="工期已变更，此计划仍引用旧工期。请在草稿中重新倒排，确认阶段和任务日期后提交审核。"
        type="warning"
        :closable="false"
        class="schedule-notice"
      />
      <div class="plan-caption"
        ><span
          >本版计划区间
          <strong
            >{{ batch.baselineStart || '未绑定' }} 至 {{ batch.baselineEnd || '未绑定' }}</strong
          ></span
        ><span v-if="batch.effectiveAt">生效于 {{ formatDate(batch.effectiveAt) }}</span
        ><span v-else-if="batch.submittedAt">提交于 {{ formatDate(batch.submittedAt) }}</span></div
      >
      <div v-if="editable" class="planning-progress"
        ><span>{{ plannedTaskCount }} / {{ batch.tasks?.length || 0 }} 项任务已安排</span
        ><el-progress :percentage="taskProgress" :show-text="false" /><span>{{
          taskIssues.length ? `${taskIssues.length} 项待处理` : '任务日期已就绪'
        }}</span></div
      >
      <el-alert
        v-if="overdueError"
        title="超期状态加载失败，请刷新重试；此处不代表没有超期。"
        type="warning"
        :closable="false"
        class="schedule-notice"
      />
      <SchedulePlanningTable
        :items="batch.items"
        :tasks="batch.tasks || []"
        :plan="batch"
        :editable="editable"
        :navigation-disabled="dirty || acting"
        :overdue-by-stage="overdueByStage"
        @open-task="openTask"
      />
      <el-form v-if="editable" label-position="top" class="adjustment-form">
        <el-form-item label="调整原因"
          ><el-input
            v-model="batch.remark"
            type="textarea"
            :rows="2"
            placeholder="说明调整依据，随计划一并提交审核"
        /></el-form-item>
        <div class="schedule-actions plan-footer">
          <Teleport :to="barTarget || 'body'" :disabled="!barTarget">
            <el-button
              :disabled="acting || dirty"
              @click="estimate"
              v-hasPermi="['pms:imp-stage-plan:update']"
              >重新倒排</el-button
            >
            <span class="footer-note">{{
              dirty ? '日期已调整，请先保存' : '审批通过后，阶段和任务计划统一生效'
            }}</span>
            <el-button
              :disabled="acting || !dirty"
              @click="save"
              v-hasPermi="['pms:imp-stage-plan:update']"
              >保存调整</el-button
            >
            <el-button
              type="primary"
              :disabled="acting || dirty || staleDuration"
              @click="openSubmit"
              v-hasPermi="['pms:imp-stage-plan:submit']"
              >提交审核</el-button
            >
          </Teleport>
        </div>
      </el-form>
      <Teleport :to="barTarget || 'body'" :disabled="!barTarget">
        <el-button
          v-if="batch.bpmProcessInstanceId"
          link
          type="primary"
          @click="
            router.push({
              name: 'BpmProcessInstanceDetail',
              query: { id: batch.bpmProcessInstanceId }
            })
          "
          >查看审批进度</el-button
        >
      </Teleport>
    </template>
    <el-empty
      v-else-if="!loading && !errorText"
      :description="
        durationPlan
          ? '尚未制定施工计划，创建后将带入项目实际阶段和任务。'
          : initialDurationHint(project) || '先录入项目工期，再倒排阶段并安排任务。'
      "
    >
      <Teleport :to="barTarget || 'body'" :disabled="!barTarget">
        <el-button v-if="!durationPlan" type="primary" :disabled="!!initialDurationHint(project)" @click="durationVisible = true"
          >录入项目工期</el-button
        >
        <el-button
          v-else
          v-hasPermi="['pms:imp-stage-plan:create']"
          type="primary"
          :disabled="acting"
          @click="createDraft"
          >制定施工计划</el-button
        >
      </Teleport>
    </el-empty>
  </ContentWrap>
  <el-drawer
    v-model="durationVisible"
    title="项目工期"
    size="min(960px, 100%)"
    :before-close="closeDuration"
  >
    <ProjectDurationPanel ref="durationRef" :project="project" @changed="durationChanged" />
  </el-drawer>
  <el-dialog v-model="submitVisible" title="提交施工计划审核" width="min(480px, 94vw)">
    <p>提交当前阶段及任务的全部计划日期，由本项目服务经理审核。</p>
    <el-alert v-if="approverError" :title="approverError" type="error" :closable="false" />
    <el-form label-position="top"
      ><el-form-item label="服务经理审批人" required
        ><el-select
          v-model="approverId"
          :loading="approversLoading"
          placeholder="选择本项目服务经理"
          style="width: 100%"
          ><el-option
            v-for="person in approvers"
            :key="person.userId"
            :value="person.userId"
            :label="person.memberName || `用户 ${person.userId}`" /></el-select></el-form-item
    ></el-form>
    <template #footer
      ><el-button @click="submitVisible = false">取消</el-button
      ><el-button type="primary" :loading="acting" :disabled="!approverId || dirty" @click="submit"
        >发起审批</el-button
      ></template
    >
  </el-dialog>
  <el-dialog v-model="comparisonVisible" title="与当前生效计划对比" width="min(960px, 94vw)">
    <el-table
      v-loading="comparisonLoading"
      :data="comparisonRows"
      empty-text="阶段和任务的计划日期没有变化"
    >
      <el-table-column prop="kind" label="类型" width="70" />
      <el-table-column prop="name" label="阶段 / 任务" min-width="180" />
      <el-table-column prop="before" label="当前生效计划" min-width="220" />
      <el-table-column prop="after" label="所选计划" min-width="220" />
      <el-table-column prop="change" label="变化" width="110" />
    </el-table>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { shortPlan, deadlineHint, taskPlanIssue, stageItemIssue, fitTaskPlanIntoStage, compareSchedules } from './schedulePresentation'
import { useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { useMessage } from '@/hooks/web/useMessage'
import { checkPermi } from '@/utils/permission'
import { formatDate } from '@/utils/formatTime'
import { DICT_TYPE, getDictLabel } from '@/utils/dict'
import * as MemberApi from '@/api/pms/project/unified-members'
import * as DurationApi from '@/api/pms/engineering/construction-plan'
import * as PlanApi from '@/api/pms/engineering/stage-plan'
import * as TaskApi from '@/api/pms/project/task-workbench'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import ProjectDurationPanel from './ProjectDurationPanel.vue'
import SchedulePlanningTable from './SchedulePlanningTable.vue'
import { useBusinessActionBar } from '@/components/BusinessView/businessActionBar'
import { initialDurationHint } from './durationEntry'

const props = defineProps<{ project: ProjectMasterVO }>()
const { barTarget } = useBusinessActionBar()
const emit = defineEmits<{
  'open-task': [task: TaskApi.TaskNode, stageCode: string]
  changed: []
}>()
const router = useRouter(),
  message = useMessage()
const loading = ref(false),
  acting = ref(false),
  errorText = ref('')
const durationVisible = ref(false),
  submitVisible = ref(false),
  approverId = ref<number>()
const approvers = ref<MemberApi.MemberRecord[]>([]),
  approversLoading = ref(false),
  approverError = ref('')
const comparisonVisible = ref(false),
  comparisonLoading = ref(false)
const comparisonRows = ref<ReturnType<typeof compareSchedules>>([])
const durationRef = ref<InstanceType<typeof ProjectDurationPanel>>()
const durationPlan = ref<DurationApi.ConstructionPlanVO | null>(null)
const batches = ref<PlanApi.StagePlanBatchVO[]>([]),
  batch = ref<PlanApi.StagePlanBatchVO>()
const selectedId = ref<number>(),
  saved = ref('')
let sequence = 0
const dirty = computed(() => !!batch.value && JSON.stringify(batch.value) !== saved.value)
const editable = computed(
  () =>
    !!batch.value &&
    [0, 3].includes(batch.value.status!) &&
    checkPermi(['pms:imp-stage-plan:update'])
)
const hasActiveDraft = computed(() =>
  batches.value.some((item) => item.status === 0 || item.status === 1)
)
const currentEffective = computed(
  () =>
    batches.value.filter((item) => item.status === 2).sort((a, b) => (b.id || 0) - (a.id || 0))[0]
)
const signingLabel = computed(
  () =>
    getDictLabel(DICT_TYPE.PMS_SIGNING_METHOD, props.project.signingMethod) ||
    props.project.signingMethod ||
    '未登记'
)
const staleDuration = computed(
  () =>
    !!batch.value &&
    !!durationPlan.value &&
    batch.value.durationRevisionId !== durationPlan.value.currentRevision.revisionId
)
const tightSchedule = computed(() => shortPlan(currentEffective.value))
const remainingTimeHint = computed(() => deadlineHint(batch.value?.baselineEnd))
const taskIssues = computed(() =>
  (batch.value?.tasks || [])
    .map((task) => ({
      task,
      issue: taskPlanIssue(
        task,
        batch.value?.items.find((item) => item.phaseCode === task.stageCode)
      )
    }))
    .filter((value) => value.issue)
)
const plannedTaskCount = computed(() => (batch.value?.tasks?.length || 0) - taskIssues.value.length)
const taskProgress = computed(() =>
  batch.value?.tasks?.length
    ? Math.round((plannedTaskCount.value / batch.value.tasks.length) * 100)
    : 100
)
const overdue = ref<PlanApi.StagePlanOverdueRowVO[]>([])
const overdueError = ref(false)
const calculationInput = computed(() => {
  try {
    return JSON.parse(batch.value?.inputSnapshot || '{}')
  } catch {
    return {}
  }
})
const statusLabels: Record<number, string> = { 0: '草稿', 1: '审批中', 2: '已生效', 3: '已驳回' }
const statusLabel = (status?: number) => statusLabels[status ?? -1] || '未知状态'
const versionLabel = (value: PlanApi.StagePlanBatchVO) =>
  value.status === 2
    ? value.id === currentEffective.value?.id
      ? '当前生效'
      : '历史已生效'
    : statusLabel(value.status)
const overdueByStage = computed(() =>
  Object.fromEntries(
    overdue.value
      .filter((row) => row.batchId === batch.value?.id && row.phaseId != null)
      .map((row) => [row.phaseId!, row.overdueDays || 0])
  )
)
const openTask = (task: PlanApi.StagePlanTaskVO) =>
  emit(
    'open-task',
    {
      taskId: task.taskId,
      stageCode: task.stageCode,
      placeholder: false,
      treeDepth: 0
    },
    task.stageCode
  )
const apply = (value: PlanApi.StagePlanBatchVO) => {
  batch.value = value
  selectedId.value = value.id
  saved.value = JSON.stringify(value)
}
// 任务计划日期为空时默认沿用所属阶段日期，避免逐项重复填写；阶段计划时间更新时，
// 不满足阶段区间的任务自动调整进区间（尽量平移保持天数，超长收紧为整个区间）。
// 默认回填与自动调整不计为用户调整。生效与审批中的计划（status 1/2）按原样展示，不回填。
watch(
  () =>
    batch.value && (batch.value.status === 0 || batch.value.status === 3) && editable.value
      ? batch.value.items
          .map((item) => `${item.phaseCode}:${item.planStart}~${item.planEnd}`)
          .join('|')
      : '',
  () => {
    const value = batch.value
    if (!value || (value.status !== 0 && value.status !== 3) || !editable.value) return
    const wasDirty = JSON.stringify(value) !== saved.value
    let changed = false
    for (const task of value.tasks || []) {
      const stage = value.items.find((item) => item.phaseCode === task.stageCode)
      if (!stage?.planStart || !stage.planEnd) continue
      if (!task.planStart || !task.planEnd) {
        if (!task.planStart) task.planStart = stage.planStart
        if (!task.planEnd) task.planEnd = stage.planEnd
        changed = true
        continue
      }
      if (fitTaskPlanIntoStage(task, stage)) changed = true
    }
    if (changed && !wasDirty) saved.value = JSON.stringify(value)
  }
)
const guard = async () => {
  if (acting.value || durationRef.value?.isDirty()) return false
  if (!dirty.value) return true
  try {
    await message.confirm('计划调整尚未保存，是否放弃后继续？')
    if (saved.value) apply(JSON.parse(saved.value))
    return true
  } catch {
    return false
  }
}
const selectBatch = async (id: number) => {
  if (!(await guard())) return
  const current = ++sequence
  loading.value = true
  errorText.value = ''
  try {
    const value = await PlanApi.getStagePlanBatch(id)
    if (current === sequence) apply(value)
  } catch {
    if (current === sequence) errorText.value = '计划版本加载失败，请重试。'
  } finally {
    if (current === sequence) loading.value = false
  }
}
const load = async (preferred?: number) => {
  const current = ++sequence,
    projectId = props.project.id
  if (!projectId) return
  loading.value = true
  errorText.value = ''
  try {
    const [duration, page] = await Promise.all([
      DurationApi.getByProjectId(projectId),
      PlanApi.getStagePlanBatchPage({ projectId, pageNo: 1, pageSize: 100 })
    ])
    if (current !== sequence) return
    durationPlan.value = duration
    const versions: PlanApi.StagePlanBatchVO[] = [...(page.list || [])]
    for (let pageNo = 2; versions.length < Number(page.total || 0); pageNo++) {
      const next = await PlanApi.getStagePlanBatchPage({ projectId, pageNo, pageSize: 100 })
      if (current !== sequence) return
      if (!next.list?.length) break
      versions.push(...next.list)
    }
    batches.value = versions
    overdueError.value = false
    try {
      const rows = await PlanApi.getOverdueStages(projectId)
      if (current !== sequence) return
      overdue.value = rows || []
    } catch {
      if (current === sequence) {
        overdue.value = []
        overdueError.value = true
      }
    }
    const id =
      preferred ||
      batches.value.find((item) => item.status === 0 || item.status === 3)?.id ||
      batches.value[0]?.id
    const value = id ? await PlanApi.getStagePlanBatch(id) : undefined
    if (current !== sequence) return
    const effectiveId = currentEffective.value?.id
    const effective =
      effectiveId === id
        ? value
        : effectiveId
          ? await PlanApi.getStagePlanBatch(effectiveId)
          : undefined
    if (current !== sequence) return
    if (effective)
      batches.value = batches.value.map((item) => (item.id === effective.id ? effective : item))
    if (value) apply(value)
    else {
      batch.value = undefined
      selectedId.value = undefined
      saved.value = ''
    }
  } catch {
    if (current === sequence) errorText.value = '施工计划加载失败，请重试。'
  } finally {
    if (current === sequence) loading.value = false
  }
}
const command = async (action: () => Promise<PlanApi.StagePlanBatchVO>, success: string) => {
  if (acting.value) return false
  const projectId = props.project.id
  acting.value = true
  try {
    const result = await action()
    if (projectId !== props.project.id) return false
    apply(result)
    message.success(success)
    emit('changed')
    await load(result.id)
    return true
  } catch {
    errorText.value = '操作未完成，请核对错误提示后重试；当前调整已保留。'
    return false
  } finally {
    acting.value = false
  }
}
const createDraft = () =>
  command(() => PlanApi.createStagePlanBatch(props.project.id!), '计划草稿已创建')
const estimate = async () => {
  try {
    await message.confirm(
      '将按最新工期、阶段占比和回款验收时间重新倒排，替换本草稿的阶段日期。已安排任务会保留，请核对是否仍在阶段范围内。'
    )
  } catch {
    return
  }
  await command(
    () => PlanApi.autoEstimateStagePlanBatch(batch.value!.id!),
    '倒排已完成，请核对阶段与任务安排'
  )
}
const openSubmit = async () => {
  const projectId = props.project.id!
  submitVisible.value = true
  approverId.value = undefined
  approvers.value = []
  approverError.value = ''
  approversLoading.value = true
  try {
    const members: MemberApi.MemberRecord[] = []
    for (let pageNo = 1; ; pageNo++) {
      const page = await MemberApi.getMemberPage(projectId, {
        state: 'CURRENT',
        role: 'SERVICE_MANAGER',
        pageNo,
        pageSize: 100
      })
      if (projectId !== props.project.id) return
      members.push(...page.list)
      if (members.length >= page.total || !page.list.length) break
    }
    approvers.value = [
      ...new Map(
        members
          .filter((person) => MemberApi.logicalMemberRole(person.memberRole) === 'SERVICE_MANAGER')
          .map((person) => [person.userId, person])
      ).values()
    ]
    if (approvers.value.length === 1) approverId.value = approvers.value[0].userId
    else if (!approvers.value.length)
      approverError.value = '本项目尚无有效服务经理，请先在项目成员中完成指派。'
  } catch {
    approverError.value = '服务经理加载失败，请关闭后重试。'
  } finally {
    approversLoading.value = false
  }
}
const showComparison = async () => {
  if (!currentEffective.value?.id || !batch.value) return
  const projectId = props.project.id,
    selected = JSON.parse(JSON.stringify(batch.value))
  comparisonVisible.value = true
  comparisonLoading.value = true
  comparisonRows.value = []
  try {
    const current = await PlanApi.getStagePlanBatch(currentEffective.value.id)
    if (projectId === props.project.id) comparisonRows.value = compareSchedules(current, selected)
  } catch {
    comparisonVisible.value = false
    message.error('生效计划加载失败，未能完成版本对比')
  } finally {
    comparisonLoading.value = false
  }
}
const save = async () => {
  const value = batch.value
  if (!value || !editable.value) return
  await command(
    () =>
      PlanApi.updateStagePlanItems({
        id: value.id!,
        version: value.version!,
        remark: value.remark,
        tasks: value.tasks,
        items: value.items.map((item) => ({
          id: item.id!,
          planStart: item.planStart!,
          planEnd: item.planEnd!,
          remark: item.remark
        }))
      }),
    '计划调整已保存'
  )
}
// 保存不做校验，避免填写中的计划日期丢失；完整性检查集中在提交审核时进行。
// 提交预检与安排检查列同一套规则，文案与后端提交校验一致，提交前即提示不等审批退回；
// 阶段次序按模板冻结路径准入约束判定（串行边允许当天衔接，并行分支允许重叠）。
const submit = async () => {
  const value = batch.value
  if (!value || !approverId.value || dirty.value) return
  if (!value.remark?.trim()) return message.warning('提交审核前请填写调整原因')
  for (const item of value.items) {
    const issue = stageItemIssue(item, value)
    if (issue) return message.warning(`${item.phaseName || '阶段'} ${issue}`)
  }
  if (taskIssues.value.length)
    return message.warning(`${taskIssues.value[0].task.name}：${taskIssues.value[0].issue}`)
  if (
    await command(
      () => PlanApi.submitStagePlanBatch(value.id!, approverId.value!),
      '计划已提交审核'
    )
  )
    submitVisible.value = false
}
const durationChanged = async () => {
  durationPlan.value = await DurationApi.getByProjectId(props.project.id!)
  emit('changed')
  if (!dirty.value) await load(selectedId.value)
}
const closeDuration = (done: () => void) => {
  if (durationRef.value?.isDirty()) {
    message.warning('请先保存或关闭工期编辑窗口')
    return
  }
  done()
}
watch(
  () => props.project.id,
  () => {
    batch.value = undefined
    batches.value = []
    saved.value = ''
    selectedId.value = undefined
    submitVisible.value = false
    comparisonVisible.value = false
    durationVisible.value = false
    approvers.value = []
    approverId.value = undefined
    durationPlan.value = null
    overdue.value = []
    void load()
  },
  { immediate: true }
)
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (dirty.value || acting.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => {
  ++sequence
  window.removeEventListener('beforeunload', beforeUnload)
})
onBeforeRouteLeave(guard)
onBeforeRouteUpdate((to, from) => to.query.projectId === from.query.projectId || guard())
defineExpose({
  requestLeave: guard,
  isDirty: () => dirty.value || acting.value || !!durationRef.value?.isDirty()
})
</script>

<style scoped lang="scss">
.schedule-workspace {
  font-family: 'Segoe UI', 'Microsoft YaHei', 'PingFang SC', sans-serif;
}

.schedule-heading,
.schedule-toolbar,
.schedule-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.schedule-heading {
  justify-content: space-between;
  margin-bottom: 16px;
}

.schedule-heading h3 {
  margin: 0 0 8px;
  font-size: 16px;
}

.schedule-heading p {
  color: var(--el-text-color-secondary);
}

.schedule-heading p {
  margin: 0;
  line-height: 1.6;
}

.schedule-facts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr));
  gap: 20px;
  padding: 18px 0;
  margin: 0 0 20px;
  border-top: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.schedule-facts dt {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.schedule-facts dd {
  margin: 8px 0 0;
  font-size: 15px;
  font-weight: 600;
}

.schedule-facts small {
  display: block;
  margin-top: 8px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.schedule-toolbar {
  margin-bottom: 16px;
}

.schedule-toolbar :deep(.el-select) {
  width: 260px;
}

.schedule-notice {
  margin-bottom: 16px;
}

.plan-caption {
  display: flex;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.plan-caption strong {
  margin-left: 8px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.planning-progress {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.planning-progress :deep(.el-progress) {
  width: 160px;
}

.adjustment-form {
  margin-top: 24px;
}

.plan-footer {
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.footer-note {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  flex: 1;
}

.dirty-label {
  color: var(--el-color-warning);
}

@media (width <= 767px) {
  .schedule-actions {
    gap: 8px;
  }

  .footer-note {
    flex-basis: 100%;
  }

  .schedule-facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
