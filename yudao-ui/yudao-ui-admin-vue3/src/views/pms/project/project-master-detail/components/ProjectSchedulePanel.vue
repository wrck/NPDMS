<template>
  <ContentWrap class="schedule-workspace" v-loading="loading" :body-style="{ padding: '20px' }">
    <header class="schedule-heading">
      <div><h3>项目施工计划</h3><p>查看工期要求，安排阶段与任务，提交审核后作为执行基线。</p></div>
      <div class="schedule-actions">
        <el-button :disabled="dirty || acting" @click="load()">刷新</el-button>
        <el-button @click="durationVisible = true">{{ durationPlan ? '工期与变更记录' : '录入项目工期' }}</el-button>
      </div>
    </header>
    <dl class="schedule-facts">
      <div><dt>工勘要求结束日期</dt><dd>{{ project.projectEndDate || '未登记' }}</dd></div>
      <div><dt>当前生效工期</dt><dd>{{ durationPlan ? `${durationPlan.currentRevision.startDate} 至 ${durationPlan.currentRevision.endDate}` : '未录入' }}</dd></div>
      <div><dt>工期天数</dt><dd>{{ durationPlan ? `${durationPlan.currentRevision.durationDays} 天` : '—' }}</dd></div>
    </dl>
    <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" class="schedule-notice" />
    <el-alert v-if="tightSchedule" title="当前计划工期不足3个日历月，请落实阶段和任务安排，并与客户确认发货及实施时间。" type="warning" :closable="false" class="schedule-notice" />
    <div class="schedule-toolbar">
      <el-select :model-value="selectedId" aria-label="施工计划版本" placeholder="选择计划版本" :disabled="acting" @change="selectBatch">
        <el-option v-for="item in batches" :key="item.id" :value="item.id!" :label="`计划 #${item.id} · ${statusLabel(item.status)}`" />
      </el-select>
      <el-button v-hasPermi="['pms:imp-stage-plan:create']" :disabled="dirty || acting || hasActiveDraft" @click="createDraft">新建计划草稿</el-button>
      <el-tag v-if="batch">{{ statusLabel(batch.status) }}</el-tag>
      <span v-if="dirty" class="dirty-label">有未保存调整</span>
    </div>
    <template v-if="batch">
      <el-alert v-if="batch.status === 3" :title="`审批未通过：${batch.rejectReason || '请调整后重新提交'}`" type="warning" :closable="false" class="schedule-notice" />
      <p class="baseline-caption">本版本工期：{{ batch.baselineStart || '未绑定' }} 至 {{ batch.baselineEnd || '未绑定' }}。生效版本只读；修改需新建草稿并审批。</p>
      <el-table :data="batch.items" row-key="id" data-testid="schedule-stage-task-table">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="stage-tasks">
              <el-table :data="stageTasks(row.phaseCode)" row-key="taskId" size="small" empty-text="本阶段暂无任务">
                <el-table-column label="阶段任务" min-width="200"><template #default="{ row: task }"><span :style="{ paddingLeft: `${taskDepth(task) * 16}px` }">{{ task.name }}</span></template></el-table-column>
                <el-table-column label="计划开始" width="170"><template #default="{ row: task }"><el-date-picker v-if="editable" v-model="task.planStart" type="date" value-format="YYYY-MM-DD" class="schedule-date" aria-label="任务计划开始" /><span v-else>{{ task.planStart || '未安排' }}</span></template></el-table-column>
                <el-table-column label="计划结束" width="170"><template #default="{ row: task }"><el-date-picker v-if="editable" v-model="task.planEnd" type="date" value-format="YYYY-MM-DD" class="schedule-date" aria-label="任务计划结束" /><span v-else>{{ task.planEnd || '未安排' }}</span></template></el-table-column>
                <el-table-column prop="acceptanceTime" label="计划验收时间" width="130"><template #default="{ row: task }">{{ task.acceptanceTime || '未登记' }}</template></el-table-column>
                <el-table-column label="安排检查" min-width="160"><template #default="{ row: task }">{{ taskCheck(task, row) }}</template></el-table-column>
                <el-table-column width="110"><template #default="{ row: task }"><el-button link type="primary" :disabled="dirty" @click="emit('open-task', { taskId: task.taskId, stageCode: task.stageCode, placeholder: false, treeDepth: 0 }, row.phaseCode)">任务详情</el-button></template></el-table-column>
              </el-table>
              <small>任务可在阶段范围内并行安排；任务日期与阶段日期一起审批生效。</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="项目阶段" min-width="170"><template #default="{ row }"><strong>{{ row.phaseName }}</strong><div class="stage-code">{{ row.phaseCode }}</div></template></el-table-column>
        <el-table-column label="计划开始" width="170"><template #default="{ row }"><el-date-picker v-if="editable" v-model="row.planStart" type="date" value-format="YYYY-MM-DD" class="schedule-date" aria-label="阶段计划开始" /><span v-else>{{ row.planStart || '未安排' }}</span></template></el-table-column>
        <el-table-column label="计划结束" width="170"><template #default="{ row }"><el-date-picker v-if="editable" v-model="row.planEnd" type="date" value-format="YYYY-MM-DD" class="schedule-date" aria-label="阶段计划结束" /><span v-else>{{ row.planEnd || '未安排' }}</span></template></el-table-column>
        <el-table-column label="冻结阶段占比" width="115"><template #default="{ row }">{{ calculationStages[row.phaseCode]?.percentage != null ? `${calculationStages[row.phaseCode].percentage}%` : '未推算' }}</template></el-table-column>
        <el-table-column label="计划验收日期" width="140"><template #default="{ row }">{{ calculationStages[row.phaseCode]?.acceptanceTime?.slice(0, 10) || '未登记' }}</template></el-table-column>
        <el-table-column label="备注" min-width="160"><template #default="{ row }"><el-input v-if="editable" v-model="row.remark" aria-label="阶段备注" /><span v-else>{{ row.remark || '—' }}</span></template></el-table-column>
      </el-table>
      <el-form v-if="editable" label-position="top" class="adjustment-form">
        <el-form-item label="调整原因"><el-input v-model="batch.remark" type="textarea" :rows="2" placeholder="说明调整依据，随计划一并提交审核" /></el-form-item>
        <div class="schedule-actions">
          <el-button :disabled="acting || dirty" @click="estimate" v-hasPermi="['pms:imp-stage-plan:update']">自动推算</el-button>
          <el-button type="primary" :disabled="acting || !dirty" @click="save" v-hasPermi="['pms:imp-stage-plan:update']">保存调整</el-button>
          <el-button :disabled="acting || dirty" @click="submitVisible = true" v-hasPermi="['pms:imp-stage-plan:submit']">提交审核</el-button>
        </div>
      </el-form>
      <el-button v-if="batch.bpmProcessInstanceId" link type="primary" @click="router.push({ name: 'BpmProcessInstanceDetail', query: { id: batch.bpmProcessInstanceId } })">查看审批进度</el-button>
    </template>
    <el-empty v-else-if="!loading && !errorText" description="暂无施工计划。录入工期后，新建计划草稿。" />
  </ContentWrap>
  <el-drawer v-model="durationVisible" title="项目工期" size="min(960px, 100%)" :before-close="closeDuration">
    <ProjectDurationPanel ref="durationRef" :project="project" @changed="durationChanged" />
  </el-drawer>
  <el-dialog v-model="submitVisible" title="提交施工计划审核" width="min(480px, 94vw)">
    <el-form label-position="top"><el-form-item label="服务经理审批人" required><PmsEntitySelect v-model="approverId" :api="UserApi.getUserPage" label-field="nickname" value-field="id" query-field="nickname" /></el-form-item></el-form>
    <template #footer><el-button @click="submitVisible = false">取消</el-button><el-button type="primary" :loading="acting" :disabled="!approverId || dirty" @click="submit">发起审批</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { useMessage } from '@/hooks/web/useMessage'
import { checkPermi } from '@/utils/permission'
import * as UserApi from '@/api/system/user'
import * as DurationApi from '@/api/pms/engineering/construction-plan'
import * as PlanApi from '@/api/pms/engineering/stage-plan'
import * as TaskApi from '@/api/pms/project/task-workbench'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import ProjectDurationPanel from './ProjectDurationPanel.vue'

const props = defineProps<{ project: ProjectMasterVO }>()
const emit = defineEmits<{ 'open-task': [task: TaskApi.TaskNode, stageCode: string]; changed: [] }>()
const router = useRouter(), message = useMessage()
const loading = ref(false), acting = ref(false), errorText = ref('')
const durationVisible = ref(false), submitVisible = ref(false), approverId = ref<number>()
const durationRef = ref<InstanceType<typeof ProjectDurationPanel>>()
const durationPlan = ref<DurationApi.ConstructionPlanVO | null>(null)
const batches = ref<PlanApi.StagePlanBatchVO[]>([]), batch = ref<PlanApi.StagePlanBatchVO>()
const selectedId = ref<number>(), saved = ref('')
let sequence = 0
const dirty = computed(() => !!batch.value && JSON.stringify(batch.value) !== saved.value)
const editable = computed(() => !!batch.value && [0, 3].includes(batch.value.status!) && checkPermi(['pms:imp-stage-plan:update']))
const hasActiveDraft = computed(() => batches.value.some(item => item.status === 0 || item.status === 1))
const tightSchedule = computed(() => !!batch.value?.baselineStart && !!batch.value?.baselineEnd && dayjs(batch.value.baselineEnd).isBefore(dayjs(batch.value.baselineStart).add(3, 'month')))
const statusLabels: Record<number, string> = { 0: '草稿', 1: '审批中', 2: '已生效', 3: '已驳回' }
const statusLabel = (status?: number) => statusLabels[status ?? -1] || '未知状态'
const calculationStages = computed<Record<string, { percentage?: number; acceptanceTime?: string }>>(() => {
  try {
    const snapshot = JSON.parse(batch.value?.inputSnapshot || '{}')
    return Object.fromEntries((snapshot.stages || []).map((stage: { stageCode: string; percentage?: number; acceptanceTime?: string }) => [stage.stageCode, stage]))
  } catch { return {} }
})
const stageTasks = (code: string) => {
  const tasks = batch.value?.tasks?.filter(task => task.stageCode === code) || []
  const ids = new Set(tasks.map(task => task.taskId)), seen = new Set<number>()
  const ordered: PlanApi.StagePlanTaskVO[] = []
  const visit = (task: PlanApi.StagePlanTaskVO) => {
    if (seen.has(task.taskId)) return
    seen.add(task.taskId); ordered.push(task)
    tasks.filter(child => child.parentTaskId === task.taskId).forEach(visit)
  }
  tasks.filter(task => !task.parentTaskId || !ids.has(task.parentTaskId)).forEach(visit)
  tasks.forEach(visit)
  return ordered
}
const taskDepth = (task: PlanApi.StagePlanTaskVO) => {
  const seen = new Set([task.taskId]); let depth = 0, parentId = task.parentTaskId
  while (parentId && !seen.has(parentId)) {
    const parent = batch.value?.tasks?.find(value => value.taskId === parentId && value.stageCode === task.stageCode)
    if (!parent) break
    seen.add(parentId); depth++; parentId = parent.parentTaskId
  }
  return depth
}
const apply = (value: PlanApi.StagePlanBatchVO) => { batch.value = value; selectedId.value = value.id; saved.value = JSON.stringify(value) }
const guard = async () => {
  if (acting.value || durationRef.value?.isDirty()) return false
  if (!dirty.value) return true
  try { await message.confirm('计划调整尚未保存，是否放弃后继续？'); if (saved.value) apply(JSON.parse(saved.value)); return true } catch { return false }
}
const selectBatch = async (id: number) => {
  if (!(await guard())) return
  const current = ++sequence
  loading.value = true; errorText.value = ''
  try { const value = await PlanApi.getStagePlanBatch(id); if (current === sequence) apply(value) }
  catch { if (current === sequence) errorText.value = '计划版本加载失败，请重试。' }
  finally { if (current === sequence) loading.value = false }
}
const load = async (preferred?: number) => {
  const current = ++sequence, projectId = props.project.id
  if (!projectId) return
  loading.value = true; errorText.value = ''
  try {
    const [duration, page] = await Promise.all([DurationApi.getByProjectId(projectId), PlanApi.getStagePlanBatchPage({ projectId, pageNo: 1, pageSize: 100 })])
    if (current !== sequence) return
    durationPlan.value = duration; batches.value = page.list || []
    const id = preferred || batches.value.find(item => item.status === 0 || item.status === 3)?.id || batches.value[0]?.id
    const value = id ? await PlanApi.getStagePlanBatch(id) : undefined
    if (current !== sequence) return
    if (value) apply(value)
    else { batch.value = undefined; selectedId.value = undefined; saved.value = '' }
  } catch { if (current === sequence) errorText.value = '施工计划加载失败，请重试。' }
  finally { if (current === sequence) loading.value = false }
}
const command = async (action: () => Promise<PlanApi.StagePlanBatchVO>, success: string) => {
  if (acting.value) return false
  const projectId = props.project.id
  acting.value = true
  try { const result = await action(); if (projectId !== props.project.id) return false; apply(result); message.success(success); emit('changed'); await load(result.id); return true }
  catch { errorText.value = '操作未完成，请核对错误提示后重试；当前调整已保留。'; return false }
  finally { acting.value = false }
}
const createDraft = () => command(() => PlanApi.createStagePlanBatch(props.project.id!), '计划草稿已创建')
const estimate = () => command(() => PlanApi.autoEstimateStagePlanBatch(batch.value!.id!), '计划已推算，请核对后提交审核')
const save = async () => {
  const value = batch.value
  if (!value || !editable.value) return
  if (!value.remark?.trim()) return message.warning('请填写调整原因')
  let previous: string | undefined
  for (const item of value.items) {
    if (!item.planStart || !item.planEnd || item.planEnd < item.planStart || (previous && item.planStart <= previous)) return message.warning('请检查全部阶段起止日期，阶段不能逆序或重叠')
    if ((value.baselineStart && item.planStart < value.baselineStart) || (value.baselineEnd && item.planEnd > value.baselineEnd)) return message.warning('阶段日期不能超出本版本工期')
    previous = item.planEnd
  }
  await command(() => PlanApi.updateStagePlanItems({ id: value.id!, version: value.version!, remark: value.remark, tasks: value.tasks, items: value.items.map(item => ({ id: item.id!, planStart: item.planStart!, planEnd: item.planEnd!, remark: item.remark })) }), '计划调整已保存')
}
const submit = async () => {
  if (!batch.value || !approverId.value || dirty.value) return
  if (await command(() => PlanApi.submitStagePlanBatch(batch.value!.id!, approverId.value!), '计划已提交审核')) submitVisible.value = false
}
const taskCheck = (task: PlanApi.StagePlanTaskVO, stage: PlanApi.StagePlanItemVO) => {
  if (!task.planStart || !task.planEnd) return '待安排日期'
  if (!stage.planStart || !stage.planEnd) return '阶段尚未排期'
  if (task.acceptanceTime && task.planEnd > task.acceptanceTime) return '晚于计划验收时间'
  return task.planStart < stage.planStart || task.planEnd > stage.planEnd || task.planEnd < task.planStart ? '超出阶段计划或日期逆序' : '在阶段范围内'
}
const durationChanged = async () => { durationPlan.value = await DurationApi.getByProjectId(props.project.id!); emit('changed'); if (!dirty.value) await load(selectedId.value) }
const closeDuration = (done: () => void) => { if (durationRef.value?.isDirty()) { message.warning('请先保存或关闭工期编辑窗口'); return } done() }
watch(() => props.project.id, () => { batch.value = undefined; batches.value = []; void load() }, { immediate: true })
const beforeUnload = (event: BeforeUnloadEvent) => { if (dirty.value || acting.value) { event.preventDefault(); event.returnValue = '' } }
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => { ++sequence; window.removeEventListener('beforeunload', beforeUnload) })
onBeforeRouteLeave(guard)
onBeforeRouteUpdate((to, from) => to.query.projectId === from.query.projectId || guard())
defineExpose({ requestLeave: guard, isDirty: () => dirty.value || acting.value || !!durationRef.value?.isDirty() })
</script>

<style scoped lang="scss">
.schedule-heading, .schedule-toolbar, .schedule-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.schedule-heading { justify-content: space-between; margin-bottom: 24px; }
.schedule-heading h3 { margin: 0 0 8px; font-size: 16px; }
.schedule-heading p, .baseline-caption, .stage-code, .stage-tasks small { color: var(--el-text-color-secondary); }
.schedule-heading p { margin: 0; line-height: 1.6; }
.schedule-facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr)); gap: 20px; padding: 18px 0; margin: 0 0 20px; border-top: 1px solid var(--el-border-color-lighter); border-bottom: 1px solid var(--el-border-color-lighter); }
.schedule-facts dt { color: var(--el-text-color-secondary); font-size: 13px; }
.schedule-facts dd { margin: 8px 0 0; font-size: 15px; font-weight: 600; }
.schedule-toolbar { margin-bottom: 16px; }
.schedule-toolbar :deep(.el-select) { width: 260px; }
.schedule-notice { margin-bottom: 16px; }
:deep(.schedule-date) { width: 145px !important; }
.stage-tasks { padding: 16px 24px; background: var(--el-fill-color-lighter); }
.stage-tasks small { display: block; margin-top: 12px; }
.stage-code { margin-top: 5px; font-size: 12px; }
.adjustment-form { margin-top: 24px; }
.dirty-label { color: var(--el-color-warning); }
@media (width <= 767px) { .stage-tasks { padding: 12px; } .schedule-actions { gap: 8px; } }
</style>
