<template>
  <section class="cutover-task-workbench creation-detail" data-testid="cutover-create-detail">
    <header class="creation-topbar">
      <el-button link :disabled="locked" data-testid="close-cutover-create" @click="emit('close')"><Icon icon="ep:back" />返回任务列表</el-button>
      <h1>新建割接任务</h1>
      <span>填写项目及设备信息，创建后继续割接等级确认</span>
    </header>
    <el-alert v-if="unknownResult" title="创建结果尚待确认。请使用下方按钮重试确认，避免重复创建。" type="warning" :closable="false" show-icon />
    <el-alert v-if="optionsError" :title="optionsError" type="error" :closable="false" show-icon><el-button link @click="loadOptions">重试加载</el-button></el-alert>
    <section v-loading="projectLoading" class="cut-card" aria-labelledby="create-project-title">
      <header class="cut-card__header"><h2 id="create-project-title"><Icon icon="ep:location" />项目信息</h2><small>选择项目或在下方输入设备序列号后自动回填</small></header>
      <div class="cut-card__body">
        <el-form label-position="top">
          <el-form-item label="项目名称" required>
            <el-select :model-value="projectId" :disabled="locked || !!props.projectId" filterable remote clearable
              :remote-method="searchProjects" :loading="projectOptionsLoading" placeholder="输入项目名称或编号查找"
              data-testid="create-project-select" class="w-full" @update:model-value="changeProject">
              <el-option v-for="option in projectOptions" :key="String(option.id)" :value="String(option.id)"
                :label="`${option.name}（${option.code}）`" />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="cut-form-grid">
          <div v-for="field in projectFields" :key="field.label" class="cut-form-field"><label>{{ field.label }}</label><span class="cut-form-value">{{ field.value || '—' }}</span></div>
        </div>
        <el-alert v-if="projectError" :title="projectError" type="error" :closable="false" class="mt-12px" />
      </div>
    </section>
    <section class="cut-card" aria-labelledby="create-devices-title">
      <header class="cut-card__header"><h2 id="create-devices-title"><Icon icon="ep:monitor" />设备序列号表</h2><small>已选择 {{ selectedDevices.length }} 台设备</small></header>
      <div class="cut-card__body">
        <div class="device-toolbar">
          <el-input v-model="serialText" :disabled="locked || resolving" placeholder="输入设备序列号；多个序列号可用逗号分隔"
            aria-label="设备序列号" data-testid="create-serials" @keyup.enter="resolveSerials" />
          <el-button :disabled="locked || !serialText.trim()" :loading="resolving" data-testid="resolve-create-serials" @click="resolveSerials">查询并添加</el-button>
          <el-button :disabled="locked || !projectId || projectLoading || !!projectError" data-testid="choose-project-devices" @click="openDevicePicker">选择项目已发货设备</el-button>
        </div>
        <p class="cut-help">未选择项目时可通过序列号查找项目。创建前可增删设备，创建后设备范围只读。</p>
        <el-table :data="selectedDevices" row-key="deviceId" empty-text="请添加本次割接设备" data-testid="create-device-table">
          <el-table-column type="index" label="序号" width="65" />
          <el-table-column prop="serialNumber" label="序列号" min-width="220" />
          <el-table-column prop="productCode" label="产品编码" min-width="180" />
          <el-table-column prop="productModel" label="产品型号" min-width="180" />
          <el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="danger" :disabled="locked"
            :data-testid="`remove-create-device-${row.deviceId}`" @click="removeDevice(row.deviceId)">移除</el-button></template></el-table-column>
        </el-table>
      </div>
    </section>
    <section class="cut-card" aria-labelledby="create-task-title">
      <header class="cut-card__header"><h2 id="create-task-title"><Icon icon="ep:document" />割接任务信息</h2></header>
      <div class="cut-card__body">
        <el-form label-position="top" :disabled="locked">
          <div class="task-form-grid">
            <el-form-item label="任务名称" required><el-input v-model="form.taskName" data-testid="create-task-name" maxlength="128" /></el-form-item>
            <el-form-item label="割接配置" required><el-select v-model="form.configurationCode" data-testid="create-configuration" class="w-full" placeholder="请选择割接配置">
              <el-option v-for="choice in configurationChoices" :key="String(choice.revisionId)" :value="choice.code" :label="`${choice.name}（版本 ${choice.revisionNo}）`" />
            </el-select></el-form-item>
            <el-form-item label="割接类型" required><el-select v-model="form.cutoverType" data-testid="create-cutover-type" class="w-full" placeholder="请选择割接类型">
              <el-option v-for="item in selectedConfiguration?.cutoverTypes || []" :key="item.value" :value="item.value" :label="item.label" />
            </el-select></el-form-item>
            <el-form-item label="组网模式"><el-select v-model="form.networkMode" data-testid="create-network-mode" clearable class="w-full" placeholder="请选择组网模式">
              <el-option v-for="item in selectedConfiguration?.networkModes || []" :key="item.value" :value="item.value" :label="item.label" />
            </el-select></el-form-item>
            <el-form-item label="计划割接时间" required><el-date-picker v-model="form.scheduledTime" data-testid="create-scheduled-time"
              type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" class="!w-full" /></el-form-item>
          </div>
          <el-form-item label="割接背景" required><el-input v-model="form.background" data-testid="create-background" type="textarea" :rows="3" maxlength="4000" show-word-limit /></el-form-item>
        </el-form>
      </div>
    </section>
    <footer class="cut-actions cut-actions--sticky">
      <el-button :disabled="submitting || unknownResult" @click="emit('close')">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!canSubmit" data-testid="submit-create-detail" @click="submit">
        {{ unknownResult ? '重试确认创建结果' : '创建并进入等级确认' }}
      </el-button>
    </footer>
    <el-dialog v-model="devicePickerVisible" title="选择项目已发货设备" width="min(960px, 94vw)" append-to-body align-center>
      <div class="device-toolbar"><el-input v-model="deviceQuery.keyword" placeholder="按序列号、产品编码或型号查询" aria-label="查找项目设备" @keyup.enter="searchDevices" />
        <el-button :loading="devicePickerLoading" @click="searchDevices">查询</el-button></div>
      <el-alert v-if="devicePickerError" :title="devicePickerError" type="error" :closable="false" />
      <el-table v-loading="devicePickerLoading" :data="deviceChoices" row-key="deviceId" empty-text="当前项目没有匹配的已发货设备">
        <el-table-column prop="serialNumber" label="序列号" min-width="210" /><el-table-column prop="productCode" label="产品编码" min-width="150" />
        <el-table-column prop="productModel" label="产品型号" min-width="180" />
        <el-table-column label="操作" width="85"><template #default="{ row }"><el-button link type="primary" :disabled="isSelected(row.deviceId)"
          :data-testid="`add-project-device-${row.deviceId}`" @click="addDevices([row])">{{ isSelected(row.deviceId) ? '已添加' : '添加' }}</el-button></template></el-table-column>
      </el-table>
      <Pagination v-model:page="deviceQuery.pageNo" v-model:limit="deviceQuery.pageSize" :total="deviceTotal" @pagination="loadDevices" />
      <template #footer><el-button type="primary" @click="devicePickerVisible = false">完成</el-button></template>
    </el-dialog>
    <el-dialog v-model="candidateVisible" title="选择本次割接项目" width="min(640px, 94vw)" append-to-body align-center>
      <el-alert title="输入的序列号涉及多个项目。请选择本次项目，仅添加该项目下列出的设备。" type="warning" :closable="false" />
      <el-radio-group v-model="candidateProjectId" class="candidate-list" data-testid="create-project-candidates">
        <el-radio v-for="candidate in pendingResolution?.context.candidates || []" :key="String(candidate.project.projectId)"
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
import { getDictLabel, DICT_TYPE } from '@/utils/dict'
import * as CutoverApi from '@/api/pms/cutover'
import type { CreationDevice, CreationProjectContext, CreationProjectOption, ResolvedCreationDevices, WireLong } from '@/api/pms/cutover'
import { buildCreateRequest, newIntentKey, cutoverPlanRecoveryAction, parseSerials } from '../shared/cutoverTaskInteraction'
import { useOperationClient } from '@/components/BusinessView/operationHost'
import { requireOperationResult, resultRecord } from '@/components/BusinessView/operationClient'

const props = defineProps<{ projectId?: WireLong }>()
const emit = defineEmits<{ close: []; created: [taskId: WireLong] }>()
const message = useMessage()
const operationClient = useOperationClient()
const projectId = ref('')
const projectOptions = ref<CreationProjectOption[]>([])
const projectOptionsLoading = ref(false)
const projectContext = ref<CreationProjectContext | null>(null)
const projectLoading = ref(false)
const projectError = ref('')
const selectedDevices = ref<CreationDevice[]>([])
const serialText = ref('')
const resolving = ref(false)
const submitting = ref(false)
const unknownResult = ref(false)
const locked = computed(() => submitting.value || unknownResult.value)
const configurationChoices = ref<CutoverApi.IntakeOption[]>([])
const optionsError = ref('')
const form = reactive({ configurationCode: '', taskName: '', background: '', cutoverType: '', networkMode: null as string | null, scheduledTime: '' })
const selectedConfiguration = computed(() => configurationChoices.value.find(choice => choice.code === form.configurationCode))
watch(() => form.configurationCode, () => { form.cutoverType = ''; form.networkMode = null })
const canSubmit = computed(() => Boolean(projectContext.value && !projectError.value && !projectLoading.value && !resolving.value
  && selectedDevices.value.length && form.configurationCode && form.taskName.trim() && form.background.trim() && form.cutoverType && form.scheduledTime))
const projectFields = computed(() => {
  const value = projectContext.value?.project
  return [
    { label: '项目编号', value: value?.projectCode }, { label: '客户名称', value: value?.customerName },
    { label: '项目经理', value: value?.projectManagerName }, { label: '服务经理', value: value?.serviceManagerNames },
    { label: '所属行业', value: value?.industryName }, { label: '办事处', value: value?.departmentName },
    { label: '项目级别', value: value?.majorProjectLevel ? getDictLabel(DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL, value.majorProjectLevel) || value.majorProjectLevel : '' },
    { label: '客户服务等级', value: projectContext.value ? projectContext.value.customerServiceLevel || '三级普通（默认）' : '' },
    { label: '销售', value: value?.salesNames }
  ]
})
let projectSequence = 0
let projectSearchSequence = 0
let deviceSequence = 0
const searchProjects = async (keyword = '') => {
  const sequence = ++projectSearchSequence
  projectOptionsLoading.value = true
  try {
    const options = await CutoverApi.searchCreationProjects(keyword)
    if (sequence !== projectSearchSequence) return
    const selected = projectOptions.value.find(option => String(option.id) === projectId.value)
    projectOptions.value = selected && !options.some(option => String(option.id) === projectId.value) ? [selected, ...options] : options
  } catch {
    if (sequence === projectSearchSequence) message.error('项目列表加载失败，请重新输入名称查询。')
  } finally { if (sequence === projectSearchSequence) projectOptionsLoading.value = false }
}
const changeProject = async (value: WireLong | null | undefined) => {
  const next = value ? String(value) : ''
  if (locked.value || props.projectId && next !== String(props.projectId)) return false
  if (next === projectId.value && projectContext.value && !projectError.value) return true
  if (selectedDevices.value.length && next !== projectId.value) {
    try { await message.confirm('切换项目会移除当前已选设备，请确认后重新选择。') } catch { return false }
  }
  const sequence = ++projectSequence
  ++deviceSequence
  projectId.value = next
  projectContext.value = null
  selectedDevices.value = []
  projectError.value = ''
  devicePickerVisible.value = false
  if (!next) { projectLoading.value = false; return true }
  projectLoading.value = true
  try {
    const value = await CutoverApi.getCreationProject(next)
    if (sequence !== projectSequence) return false
    projectContext.value = value
    if (!projectOptions.value.some(option => String(option.id) === next)) {
      projectOptions.value.unshift({ id: value.project.projectId, code: value.project.projectCode, name: value.project.projectName, customerName: value.project.customerName })
    }
    return true
  } catch {
    if (sequence === projectSequence) projectError.value = '项目信息加载失败，请重新选择或查询项目。'
    return false
  } finally { if (sequence === projectSequence) projectLoading.value = false }
}
const isSelected = (id: WireLong) => selectedDevices.value.some(device => String(device.deviceId) === String(id))
const addDevices = (devices: CreationDevice[]) => {
  if (locked.value) return
  const existing = new Set(selectedDevices.value.map(device => String(device.deviceId)))
  selectedDevices.value = [...selectedDevices.value, ...devices.filter(device => !existing.has(String(device.deviceId)))]
}
const removeDevice = (id: WireLong) => {
  if (!locked.value) selectedDevices.value = selectedDevices.value.filter(device => String(device.deviceId) !== String(id))
}
const pendingResolution = ref<ResolvedCreationDevices | null>(null)
const candidateVisible = ref(false)
const candidateProjectId = ref('')
const applyResolution = async (result: ResolvedCreationDevices, targetProject: string) => {
  const candidate = result.context.candidates.find(value => String(value.project.projectId) === targetProject)
  if (!candidate) return
  if (projectId.value !== targetProject && !await changeProject(targetProject)) return
  if (!projectContext.value && !await changeProject(targetProject)) return
  const ids = new Set(candidate.devices.map(device => String(device.deviceId)))
  addDevices(result.devices.filter(device => ids.has(String(device.deviceId))))
  serialText.value = ''
}
const resolveSerials = async () => {
  if (locked.value || resolving.value || !serialText.value.trim()) return
  const startProject = projectId.value
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
const devicePickerVisible = ref(false)
const devicePickerLoading = ref(false)
const devicePickerError = ref('')
const deviceChoices = ref<CreationDevice[]>([])
const deviceTotal = ref(0)
const deviceQuery = reactive({ keyword: '', pageNo: 1, pageSize: 20 })
const loadDevices = async () => {
  if (!projectId.value) return
  const sequence = ++deviceSequence
  devicePickerLoading.value = true
  devicePickerError.value = ''
  try {
    const page = await CutoverApi.getCreationProjectDevices(projectId.value, { ...deviceQuery })
    if (sequence !== deviceSequence) return
    deviceChoices.value = page.list
    deviceTotal.value = Number(page.total)
  } catch {
    if (sequence === deviceSequence) { deviceChoices.value = []; deviceTotal.value = 0; devicePickerError.value = '设备列表加载失败，请重试查询。' }
  } finally { if (sequence === deviceSequence) devicePickerLoading.value = false }
}
const searchDevices = async () => { deviceQuery.pageNo = 1; await loadDevices() }
const openDevicePicker = async () => {
  deviceChoices.value = []
  Object.assign(deviceQuery, { keyword: '', pageNo: 1, pageSize: 20 })
  devicePickerVisible.value = true
  await loadDevices()
}
let pendingCreate: { key: string; data: CutoverApi.CreateCutoverTaskRequest; input: typeof form & { serialNumbers: string[] } } | null = null
const submit = async () => {
  if (!canSubmit.value || submitting.value) return
  submitting.value = true
  try {
    if (!pendingCreate) {
      const serials = selectedDevices.value.map(device => device.serialNumber)
      const result = await CutoverApi.resolveCreationDevices(serials)
      const candidate = result.context.candidates.find(value => String(value.project.projectId) === projectId.value)
      if (!candidate || result.context.candidates.length !== 1 || candidate.devices.length !== selectedDevices.value.length) {
        message.warning('设备归属已变化，请重新选择本项目设备。')
        return
      }
      pendingCreate = { key: newIntentKey(), data: buildCreateRequest(candidate, form), input: { ...form, serialNumbers: serials } }
    }
    let taskId: WireLong
    if (props.projectId) {
      if (!operationClient.value) throw new Error('项目执行上下文尚未就绪，请重新打开任务')
      const result = await operationClient.value.execute({ operationCode: 'CUT.CUTOVER_TASK.CREATE', input: pendingCreate.input, key: pendingCreate.key,
        validateResult: value => {
          requireOperationResult(value.ownerContext === 'CUT' && value.objectType === 'CUTOVER_TASK' && resultRecord(value.response))
          requireOperationResult(String(value.response.taskId) === value.objectId)
        } })
      taskId = result.objectId
    } else {
      taskId = (await CutoverApi.createCutoverTask({ data: pendingCreate.data, idempotencyKey: pendingCreate.key })).taskId
    }
    pendingCreate = null
    unknownResult.value = false
    message.success('割接任务已创建，请继续确认割接等级')
    emit('created', taskId)
  } catch (error) {
    unknownResult.value = !!pendingCreate && cutoverPlanRecoveryAction(error) === 'RETRY_SAME_KEY'
    if (!unknownResult.value) pendingCreate = null
    const response = (error as any)?.response?.data
    message.error(response?.data?.recoveryAction === 'OPEN_EXISTING_TASK' ? '设备已有活动割接任务，请返回任务列表继续办理。'
      : typeof response?.msg === 'string' ? response.msg : error instanceof Error ? error.message : '创建失败，请重试。')
  } finally { submitting.value = false }
}
const loadOptions = async () => {
  optionsError.value = ''
  try {
    const choices = await CutoverApi.getCreationOptions()
    configurationChoices.value = choices
    if (choices.length === 1) form.configurationCode = choices[0].code
  } catch { optionsError.value = '割接配置加载失败，请重试。' }
}
onMounted(() => { void loadOptions(); if (props.projectId) void changeProject(props.projectId); else void searchProjects() })
onBeforeUnmount(() => { ++projectSequence; ++projectSearchSequence; ++deviceSequence })
</script>

<style scoped>
.creation-topbar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; padding: 12px 16px; margin-bottom: 16px; border-radius: 8px; background: var(--el-bg-color); }
.creation-topbar h1 { margin: 0; font-size: 16px; font-weight: 600; }
.creation-topbar > span { color: var(--el-text-color-secondary); font-size: 12px; }
.device-toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
.device-toolbar .el-input { flex: 1; min-width: 180px; }
.task-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 20px; }
.candidate-list { display: grid; gap: 12px; width: 100%; }
.candidate-list .el-radio { height: auto; margin: 0; padding: 12px 0; }
.candidate-list :deep(.el-radio__label) { display: grid; gap: 4px; white-space: normal; }
.candidate-list small { color: var(--el-text-color-secondary); }
@media (width <= 767px) { .device-toolbar { flex-wrap: wrap; } .task-form-grid { grid-template-columns: 1fr; } }
</style>
