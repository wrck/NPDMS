<template>
  <section class="cutover-task-workbench creation-detail" data-testid="cutover-create-detail">
    <header class="creation-topbar">
      <el-button link :disabled="locked" data-testid="close-cutover-create" @click="requestClose"><Icon icon="ep:back" />返回任务列表</el-button>
      <h1>新建割接任务</h1>
      <span>填写项目及设备信息，直接完成割接等级评估问卷</span>
    </header>
    <CutoverWorkbenchSteps :steps="creationSteps" selected="P2" />
    <el-alert v-if="unknownResult" :title="createdTaskId ? '任务已创建，问卷尚未确认保存。请重试保存问卷，或打开已创建任务继续办理。' : '创建结果尚待确认。请使用下方按钮重试确认，避免重复创建。'" type="warning" :closable="false" show-icon />
    <el-alert v-if="optionsError" :title="optionsError" type="error" :closable="false" show-icon><el-button link @click="loadOptions">重试加载</el-button></el-alert>
    <el-form ref="formRef" :model="validationModel" :rules="rules" label-position="top" :disabled="locked" @submit.prevent>
    <ProjectInformationCard
      :project="projectContext?.project" :customer-service-level="projectContext?.customerServiceLevel"
      :loading="projectLoading" :error="projectOptionsError || projectError" hint="选择项目或在下方输入设备序列号后自动回填">
          <el-form-item label="项目名称" prop="projectId" required>
            <el-select
              :model-value="selectedProjectId" :disabled="locked || !!props.projectId" filterable remote clearable
              :remote-method="searchProjects" :loading="projectOptionsLoading" placeholder="输入项目名称或编号查找"
              data-testid="create-project-select" class="w-full" @update:model-value="changeProject">
              <el-option
                v-for="option in projectOptions" :key="String(option.id)" :value="String(option.id)"
                :label="`${option.name}（${option.code}）`" />
            </el-select>
          </el-form-item>
    </ProjectInformationCard>
    <el-form-item prop="deviceIds" class="create-device-field">
    <ProjectDeviceSelection
      v-model="selectedDevices" :project-id="selectedProjectId" :disabled="locked"
      :project-loading="projectLoading" :project-error="projectError" empty-text="请添加本次割接设备">
      <template #serial-query>
        <el-input
          v-model="serialText" :disabled="locked || resolving" placeholder="输入设备序列号；多个序列号可用逗号分隔"
          aria-label="设备序列号" data-testid="create-serials" @keyup.enter="resolveSerials" />
        <el-button :disabled="locked || !serialText.trim()" :loading="resolving" data-testid="resolve-create-serials" @click="resolveSerials">查询并添加</el-button>
      </template>
      <template #hint>未选择项目时可通过序列号查找项目。创建前可增删设备，创建后设备范围只读。</template>
    </ProjectDeviceSelection>
    </el-form-item>
    </el-form>
    <CutoverAssessmentPanel
      :model="assessmentModel" :customer-service-level="projectContext?.customerServiceLevel"
      :editable="!locked && props.createAllowed" :submittable="false" :saving="submitting" :submitting="false" hide-actions />
    <footer class="cut-actions cut-actions--sticky">
      <el-button :disabled="submitting || unknownResult" @click="requestClose">返回任务列表</el-button>
      <el-button type="primary" :loading="submitting" :disabled="submitting || (!unknownResult && (!props.createAllowed || projectLoading || resolving))" data-testid="submit-create-detail" @click="submit">
        {{ createdTaskId ? '重试保存问卷' : unknownResult ? '重试原请求' : '保存' }}
      </el-button>
      <el-button v-if="createdTaskId && !submitting" @click="openCreatedTask">打开已创建任务</el-button>
    </footer>
    <el-dialog v-model="candidateVisible" title="选择本次割接项目" width="min(640px, 94vw)" append-to-body align-center>
      <el-alert title="输入的序列号涉及多个项目。请选择本次项目，仅添加该项目下列出的设备。" type="warning" :closable="false" />
      <el-radio-group v-model="candidateProjectId" class="candidate-list" data-testid="create-project-candidates">
        <el-radio
          v-for="candidate in pendingResolution?.context.candidates || []" :key="String(candidate.project.projectId)"
          :value="String(candidate.project.projectId)"><span>{{ candidate.project.projectName }}（{{ candidate.project.projectCode }}）</span>
          <small>{{ candidate.devices.map(device => device.serialNumber).join('、') }}</small></el-radio>
      </el-radio-group>
      <template #footer><el-button @click="candidateVisible = false">取消</el-button><el-button type="primary" data-testid="confirm-create-project" :disabled="!candidateProjectId" @click="confirmCandidate">选择并回填</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import '../shared/cutover.css'
import { useMessage } from '@/hooks/web/useMessage'
import type { FormInstance, FormRules } from 'element-plus'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import ProjectInformationCard from '@/components/ProjectInformationCard/index.vue'
import CutoverWorkbenchSteps from '../components/CutoverWorkbenchSteps.vue'
import CutoverAssessmentPanel from '../components/CutoverAssessmentPanel.vue'
import { useProjectInformation } from '@/components/ProjectInformationCard/useProjectInformation'
import { useProjectOptions } from '@/components/ProjectInformationCard/useProjectOptions'
import ProjectDeviceSelection from '@/components/ProjectDeviceSelection/index.vue'
import * as CutoverApi from '@/api/pms/cutover'
import type { CreationDevice, ResolvedCreationDevices, WireLong } from '@/api/pms/cutover'
import { buildCreateRequest, newIntentKey, cutoverPlanRecoveryAction, parseSerials } from '../shared/cutoverTaskInteraction'
import { useOperationClient } from '@/components/BusinessView/operationHost'
import { requireOperationResult, resultRecord } from '@/components/BusinessView/operationClient'

const props = withDefaults(defineProps<{ projectId?: WireLong; operationObjectType?: 'CUTOVER_TASK' | 'CUTOVER_APPROVAL'; createAllowed?: boolean }>(), { operationObjectType: 'CUTOVER_TASK', createAllowed: true })
const emit = defineEmits<{ close: []; created: [taskId: WireLong] }>()
const message = useMessage()
const operationClient = useOperationClient()
const selectedProjectId = ref('')
const { options: projectOptions, loading: projectOptionsLoading, error: projectOptionsError, search: searchProjects } =
  useProjectOptions(() => selectedProjectId.value)
const { context: projectContext, loading: projectLoading, error: projectError, load: loadProjectInformation } = useProjectInformation()
const selectedDevices = ref<CreationDevice[]>([])
const serialText = ref('')
const resolving = ref(false)
const submitting = ref(false)
const unknownResult = ref(false)
const locked = computed(() => submitting.value || unknownResult.value)
const configurationChoices = ref<CutoverApi.IntakeOption[]>([])
const optionsError = ref('')

const creationSteps: CutoverApi.CutoverTaskDetail['workbenchSteps'] = (['P2', 'P3', 'P4', 'P5', 'P6'] as const)
  .map(stage => ({ stage, label: '', state: stage === 'P2' ? 'CURRENT' : 'FUTURE', isCurrent: stage === 'P2', isAccessible: false }))
const assessmentModel = reactive<{ answers: CutoverApi.AssessmentAnswers; manualGrade: CutoverApi.ManualGrade | null }>({
  answers: { businessImportanceLevel: null, operationComplexityLevel: null, hiddenRiskLevel: null, sparePartApplied: null }, manualGrade: null
})
const assessmentDirty = computed(() => !!assessmentModel.manualGrade || Object.values(assessmentModel.answers).some(value => value !== null))
const createdTaskId = ref<WireLong | null>(null)
let pendingAssessment: Parameters<typeof CutoverApi.saveCutoverAssessment>[0] | null = null
const formRef = ref<FormInstance>()

const creationCompleted = ref(false)
const validationModel = computed(() => ({ projectId: selectedProjectId.value, deviceIds: selectedDevices.value.map(device => String(device.deviceId)) }))
const rules: FormRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  deviceIds: [{ type: 'array', required: true, min: 1, message: '请选择至少一台割接设备', trigger: 'change' }],

}
const createDirty = computed(() => Boolean((!props.projectId && selectedProjectId.value) || selectedDevices.value.length || serialText.value.trim()
  || assessmentDirty.value))
let approvedLeave = false
const mayLeaveCreate = async () => {
  if (creationCompleted.value) return true
  if (approvedLeave) { approvedLeave = false; return true }
  if (submitting.value || unknownResult.value) {
    message.warning(submitting.value ? '正在保存，请稍候再离开。' : '创建结果尚待确认，请重试原请求确认后再离开。')
    return false
  }
  if (!createDirty.value) return true
  try { await message.confirm('割接任务及等级评估问卷尚未保存，离开后将清空本次填写内容。', '确认离开'); return true }
  catch { return false }
}
const requestClose = async () => {
  if (!await mayLeaveCreate()) return
  approvedLeave = true
  emit('close')
}
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (!creationCompleted.value && (submitting.value || unknownResult.value || createDirty.value)) {
    event.preventDefault(); event.returnValue = ''
  }
}
onBeforeRouteLeave(mayLeaveCreate)
onBeforeRouteUpdate((to, from) => from.query.create === '1' && to.query.create !== '1' ? mayLeaveCreate() : true)
defineExpose({ mayLeave: mayLeaveCreate })
const canSubmit = computed(() => Boolean((props.createAllowed || unknownResult.value) && projectContext.value && !projectError.value && !projectLoading.value && !resolving.value
  && selectedDevices.value.length && configurationChoices.value.length === 1 && !optionsError.value))
let projectSequence = 0
const changeProject = async (value: WireLong | null | undefined) => {
  const next = value ? String(value) : ''
  if (locked.value || props.projectId && next !== String(props.projectId)) return false
  if (next === selectedProjectId.value && projectContext.value && !projectError.value) return true
  if (selectedDevices.value.length && next !== selectedProjectId.value) {
    try { await message.confirm('切换项目会移除当前已选设备，请确认后重新选择。') } catch { return false }
  }
  ++projectSequence
  selectedProjectId.value = next
  selectedDevices.value = []
  const context = await loadProjectInformation(next)
  if (!context) return !next
  if (!projectOptions.value.some(option => String(option.id) === next)) {
    projectOptions.value.unshift({ id: context.project.projectId, code: context.project.projectCode, name: context.project.projectName, customerName: context.project.customerName })
  }
  return true
}
const addDevices = (devices: CreationDevice[]) => {
  if (locked.value) return
  const existing = new Set(selectedDevices.value.map(device => String(device.deviceId)))
  selectedDevices.value = [...selectedDevices.value, ...devices.filter(device => !existing.has(String(device.deviceId)))]
}
const pendingResolution = ref<ResolvedCreationDevices | null>(null)
const candidateVisible = ref(false)
const candidateProjectId = ref('')
const applyResolution = async (result: ResolvedCreationDevices, targetProject: string) => {
  const candidate = result.context.candidates.find(value => String(value.project.projectId) === targetProject)
  if (!candidate) return
  if (selectedProjectId.value !== targetProject && !await changeProject(targetProject)) return
  if (!projectContext.value && !await changeProject(targetProject)) return
  const ids = new Set(candidate.devices.map(device => String(device.deviceId)))
  addDevices(result.devices.filter(device => ids.has(String(device.deviceId))))
  serialText.value = ''
}
const resolveSerials = async () => {
  if (locked.value || resolving.value || !serialText.value.trim()) return
  const startProject = selectedProjectId.value
  const sequence = projectSequence
  resolving.value = true
  try {
    const result = await CutoverApi.resolveCreationDevices(parseSerials(serialText.value))
    if (sequence !== projectSequence) return
    if (startProject) {
      if (result.context.candidates.length !== 1 || String(result.context.candidates[0].project.projectId) !== startProject) {
        message.warning('输入的设备不全部属于当前项目，未添加任何设备。')
        return
      }
      await applyResolution(result, startProject)
    } else if (result.context.candidates.length === 1) {
      await applyResolution(result, String(result.context.candidates[0].project.projectId))
    } else {
      pendingResolution.value = result
      candidateProjectId.value = ''
      candidateVisible.value = true
    }
  } catch (error) {
    message.error((error as any)?.response?.data?.msg || (error instanceof Error ? error.message : '序列号查询失败，请重试。'))
  } finally { resolving.value = false }
}
const confirmCandidate = async () => {
  if (!pendingResolution.value || !candidateProjectId.value) return
  await applyResolution(pendingResolution.value, candidateProjectId.value)
  candidateVisible.value = false
  pendingResolution.value = null
}
let pendingCreate: { key: string; data: CutoverApi.CreateCutoverTaskRequest; input: Pick<CutoverApi.CreateCutoverTaskRequest, 'serialNumbers'> } | null = null
const submit = async () => {
  if (submitting.value || resolving.value || projectLoading.value) return
  if (!pendingCreate && (!(await formRef.value?.validate().catch(() => false)) || !canSubmit.value)) return
  submitting.value = true
  try {
    if (!pendingCreate) {
      if (!props.createAllowed) return
      const serials = selectedDevices.value.map(device => device.serialNumber)
      const result = await CutoverApi.resolveCreationDevices(serials)
      const candidate = result.context.candidates.find(value => String(value.project.projectId) === selectedProjectId.value)
      if (!candidate || result.context.candidates.length !== 1 || candidate.devices.length !== selectedDevices.value.length) {
        message.warning('设备归属已变化，请重新选择本项目设备。')
        return
      }
      const data = buildCreateRequest(candidate)
      const { serialNumbers } = data
      pendingCreate = { key: newIntentKey(), data, input: { serialNumbers } }
    }
    let taskId: WireLong
    if (createdTaskId.value) {
      taskId = createdTaskId.value
    } else if (props.projectId) {
      if (!operationClient.value) throw new Error('项目执行上下文尚未就绪，请重新打开任务')
      const result = await operationClient.value.execute({ operationCode: `CUT.${props.operationObjectType}.CREATE`, input: pendingCreate.input, key: pendingCreate.key,
        validateResult: value => {
          requireOperationResult(value.ownerContext === 'CUT' && value.objectType === props.operationObjectType && resultRecord(value.response))
          requireOperationResult(String(value.response.taskId) === value.objectId)
        } })
      taskId = result.objectId
    } else {
      taskId = (await CutoverApi.createCutoverTask({ data: pendingCreate.data, idempotencyKey: pendingCreate.key })).taskId
    }
    createdTaskId.value = taskId
    if (assessmentDirty.value) {
      if (!pendingAssessment) {
        const detail = await CutoverApi.getCutoverTaskDetail(taskId)
        if (!detail.allowedActions.includes('SAVE_ASSESSMENT')) throw new Error('当前任务不允许保存问卷，请打开已创建任务核查。')
        pendingAssessment = { taskId, taskVersion: detail.task.version, assessmentVersion: detail.assessment?.rowVersion || 0,
          data: JSON.parse(JSON.stringify(assessmentModel)), idempotencyKey: newIntentKey() }
      }
      await CutoverApi.saveCutoverAssessment(pendingAssessment)
    }
    pendingCreate = null
    pendingAssessment = null
    unknownResult.value = false
    creationCompleted.value = true
    message.success(assessmentDirty.value ? '割接任务及等级评估问卷已保存' : '割接任务已创建，请继续确认割接等级')
    emit('created', taskId)
  } catch (error) {
    unknownResult.value = !!createdTaskId.value || (!!pendingCreate && cutoverPlanRecoveryAction(error) === 'RETRY_SAME_KEY')
    if (!unknownResult.value) pendingCreate = null
    const response = (error as any)?.response?.data
    message.error(response?.data?.recoveryAction === 'OPEN_EXISTING_TASK' ? '设备已有活动割接任务，请返回任务列表继续办理。'
      : typeof response?.msg === 'string' ? response.msg : error instanceof Error ? error.message : '创建失败，请重试。')
  } finally { submitting.value = false }
}
const openCreatedTask = async () => {
  if (!createdTaskId.value || submitting.value) return
  try { await message.confirm('本次问卷尚未确认保存。打开已创建任务后，请核查问卷并继续填写。', '打开已创建任务') }
  catch { return }
  creationCompleted.value = true
  emit('created', createdTaskId.value)
}
const loadOptions = async () => {
  optionsError.value = ''
  try {
    const choices = await CutoverApi.getCreationOptions()
    configurationChoices.value = choices
    if (choices.length !== 1) optionsError.value = choices.length ? '存在多个有效已发布割接配置，请先明确配置后再创建任务。' : '当前无有效已发布割接配置，请先发布配置。'
  } catch { optionsError.value = '割接配置加载失败，请重试。' }
}
onMounted(() => { window.addEventListener('beforeunload', beforeUnload); void loadOptions(); if (props.projectId) void changeProject(props.projectId); else void searchProjects() })
onBeforeUnmount(() => { ++projectSequence })
</script>

<style scoped>
.create-device-field { display: block; }
.create-device-field :deep(.el-form-item__content) { display: block; }
.creation-topbar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; padding: 12px 16px; margin-bottom: 16px; border-radius: 8px; background: var(--el-bg-color); }
.creation-topbar h1 { margin: 0; font-size: 16px; font-weight: 600; }
.creation-topbar > span { color: var(--el-text-color-secondary); font-size: 12px; }
.candidate-list { display: grid; gap: 12px; width: 100%; }
.candidate-list .el-radio { height: auto; margin: 0; padding: 12px 0; }
.candidate-list :deep(.el-radio__label) { display: grid; gap: 4px; white-space: normal; }
.candidate-list small { color: var(--el-text-color-secondary); }
</style>
