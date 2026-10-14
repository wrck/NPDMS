<template>
  <section v-loading="loading" class="checklist-panel" aria-label="调研及风险考察">
    <el-alert title="按任务的割接类型、组网模式、设备类型及等级匹配清单。同页填写调研、风险和双机检查结果，提交后带入割接方案。" type="info" :closable="false" show-icon />
    <el-alert v-if="gapMessage" :title="gapMessage" type="warning" :closable="false" show-icon />
    <section class="cut-card">
      <header class="cut-card__header"><h3><Icon icon="ep:search" />割接信息采集</h3><small v-if="checklist">第 {{ checklist.checklistVersion }} 版，{{ checklist.status === 'DRAFT' ? '填写中' : '已提交' }}</small></header>
      <div class="cut-card__body">
        <div class="matching-grid">
          <div class="cut-form-field"><label>割接类型</label><span class="cut-form-value">{{ cutoverTypeLabel }}</span></div>
          <div class="cut-form-field"><label>高可靠性组网模式</label><span class="cut-form-value">{{ networkModeLabel }}</span></div>
          <div class="cut-form-field"><label for="checklist-device-types">设备类型<span class="cut-required"> *</span></label>
            <el-select v-if="matchingOptions" id="checklist-device-types" v-model="deviceTypeCodes" multiple placeholder="请选择设备类型" :disabled="(!(canGenerate && !checklist) && !canRematch) || loading || generating || rematching || saving || submitting || externalBusy">
              <el-option v-for="option in matchingOptions.deviceTypes" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </div>
          <div class="cut-form-field"><label>割接等级</label><span class="cut-form-value">{{ detail.task.manualGrade || '—' }}级</span></div>
        </div>
        <div class="matching-footer">
          <p v-if="checklist" class="cut-help">已匹配设备类型：{{ frozenTypeLabels || '未记录' }}，共 {{ applicableItems.length }} 项</p>
          <el-button v-if="!checklist && canGenerate" type="primary" v-hasPermi="['pms:cutover-task:save-checklist']" :loading="generating" :disabled="!deviceTypeCodes.length || loading" @click="generate">生成清单</el-button>
          <el-button v-if="canRematch" v-hasPermi="['pms:cutover-task:save-checklist']" :loading="rematching" :disabled="!deviceTypeCodes.length || loading || saving || submitting || externalBusy" @click="rematch">重新匹配清单</el-button>
        </div>
        <el-alert v-if="checklist && selectionChanged" title="设备类型选择尚未生效，请重新匹配后再提交。" type="warning" :closable="false" show-icon />
      </div>
    </section>
    <template v-if="checklist">
      <section v-if="externalState?.devices.length" class="cut-card">
        <header class="cut-card__header"><h3>设备软件版本</h3><small>技术公告按每台设备的版本查询</small></header>
        <div class="cut-card__body">
          <el-table :data="deviceVersions" border>
            <el-table-column label="设备序列号" prop="sn" min-width="160" />
            <el-table-column label="设备型号" min-width="180"><template #default="{ row }"><el-input v-model="row.productModel" :disabled="readonly || !canSave || externalBusy" aria-label="设备型号" /></template></el-table-column>
            <el-table-column label="当前软件版本" min-width="180"><template #default="{ row }"><el-input v-model="row.currentSoftwareVersion" :disabled="readonly || !canSave || externalBusy" aria-label="当前软件版本" /></template></el-table-column>
            <el-table-column v-if="detail.task.cutoverType === 'VERSION_UPGRADE'" label="目标软件版本" min-width="180"><template #default="{ row }"><el-input v-model="row.targetSoftwareVersion" :disabled="readonly || !canSave || externalBusy" aria-label="目标软件版本" /></template></el-table-column>
          </el-table>
          <p v-if="deviceVersionsDirty" class="cut-help">版本尚未保存。保存后，旧查询结果会保留，但须按新版本重新加载并核查。</p>
          <el-button v-if="!readonly && canSave" class="mt-12px" :disabled="!deviceVersionsDirty || saving || submitting" :loading="externalBusy" @click="saveVersions">保存设备版本</el-button>
        </div>
      </section>
      <div class="checklist-search"><span class="cut-muted">按类型分区填写</span><el-input v-model="search" clearable placeholder="查找采集项" class="!w-240px"><template #prefix><Icon icon="ep:search" /></template></el-input></div>
      <section v-for="group in itemSections" :key="group.code" class="cut-card">
        <header class="cut-card__header"><h3><Icon :icon="group.icon" />{{ group.label }}</h3><small>{{ group.items.length }} 项</small></header>
        <div class="cut-card__body cut-card__body--table">
          <el-table :data="group.items" :empty-text="search ? '本区暂无匹配的采集项' : '当前条件未匹配此类采集项'" row-key="stableItemKey">
            <el-table-column type="index" label="序号" width="58" />
            <el-table-column label="采集项及核查说明" min-width="285">
              <template #default="{ row }"><span>{{ row.itemName }}</span><span v-if="row.required" class="cut-required"> *</span><p v-if="row.itemDescription" class="item-description">{{ row.itemDescription }}</p><small class="cut-muted">{{ row.sourceCode === 'CUSTOM' ? '自定义项' : '配置匹配项' }}</small></template>
            </el-table-column>
            <el-table-column label="检查结果／反馈内容" min-width="560">
              <template #default="{ row }">
                <CutoverChecklistField :item="row" :direct-value="answers[row.stableItemKey] || ''" :readonly="readonly" :table-row="true"
                  :allow-save="canSave && !externalBusy && !saving && !submitting" :allow-collection="canRequestCollection && !externalBusy" :devices="detail.devices" @direct="setAnswer" @manual="saveManual"
                  :external-state="externalState?.items.find(item => item.stableItemKey === row.stableItemKey)" :external-feedback="externalFeedbacks[row.stableItemKey]"
                  :external-busy="externalBusy || saving || submitting" :version-dirty="deviceVersionsDirty"
                  @external-feedback="setExternalFeedback" @external-load="loadExternal" @external-save="saveExternalFeedback"
                  @collection="requestCollection" @remove="removeCustom" @refresh="load" />
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div v-if="!readonly && canSave && ['BUSINESS_SURVEY', 'RISK'].includes(group.code)" class="group-actions"><el-button @click="openCustom(group.code)"><Icon icon="ep:plus" />增加自定义{{ group.code === 'RISK' ? '考察' : '调研' }}项</el-button></div>
      </section>
      <footer class="cut-actions cut-actions--sticky">
        <el-button v-if="canExport" data-testid="checklist-export" v-hasPermi="['pms:cutover-task:query']" :loading="exporting" @click="exportChecklist"><Icon icon="ep:download" />下载清单</el-button>
        <el-button v-if="!readonly && canSave" data-testid="checklist-save" v-hasPermi="['pms:cutover-task:save-checklist']" :loading="saving" :disabled="rematching || submitting || externalBusy" @click="save">保存</el-button>
        <el-button v-if="!readonly && canSubmit" data-testid="checklist-submit" type="primary" v-hasPermi="['pms:cutover-task:submit-checklist']" :loading="submitting" :disabled="selectionChanged || !frozenDeviceTypeCodes.length || rematching || saving || externalBusy" @click="submit">下一步：割接方案</el-button>
      </footer>
    </template>
    <el-empty v-else-if="!loading" description="选择设备类型并生成调研及风险考察清单" />
    <el-dialog v-model="customVisible" :title="customItem.itemTypeCode === 'RISK' ? '新增自定义风险考察项' : '新增自定义调研项'" width="min(520px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="采集项名称" required><el-input v-model="customItem.itemName" data-testid="custom-item-name" maxlength="200" /></el-form-item>
        <el-form-item label="核查说明"><el-input v-model="customItem.itemDescription" type="textarea" :rows="3" /></el-form-item>
        <el-form-item><el-checkbox v-model="customItem.required">本项必填</el-checkbox></el-form-item>
      </el-form>
      <template #footer><el-button @click="customVisible = false">取消</el-button><el-button data-testid="add-custom-item" type="primary" :disabled="!customItem.itemName.trim()" @click="addCustom">添加</el-button></template>
    </el-dialog>
  </section>
</template>
<script setup lang="ts">
import { useMessage } from '@/hooks/web/useMessage'
import download from '@/utils/download'
import * as CutoverApi from '@/api/pms/cutover/workbench'
import type {
  ChecklistFileHandle,
  CutoverChecklistMatchingOptions,
  CutoverChecklistView,
  CutoverExternalState,
  CutoverExternalFeedback,
  CutoverChecklistDeviceVersion,
  CutoverNavigationTarget,
  CutoverTaskDetail
} from '@/api/pms/cutover/workbench'
import {
  createCutoverPlanWriteBarrier,
  decodeChecklistDirectAnswer,
  encodeChecklistDirectAnswer,
  newIntentKey
} from '../cutoverTaskInteraction'
import CutoverChecklistField from './CutoverChecklistField.vue'

const props = defineProps<{
  detail: CutoverTaskDetail
  refreshWorkspace?: () => Promise<void>
  refreshDraft?: () => Promise<void>
  readonly?: boolean
}>()
const emit = defineEmits<{ navigate: [target: CutoverNavigationTarget] }>()
const message = useMessage()
const checklist = ref<CutoverChecklistView | null>(null)
const externalState = ref<CutoverExternalState | null>(null)
const deviceVersions = ref<CutoverChecklistDeviceVersion[]>([])
const savedDeviceVersions = ref('')
const externalBusy = ref(false)
const externalFeedbacks = reactive<Record<string, CutoverExternalFeedback[]>>({})
const savedExternalFeedbacks = reactive<Record<string, string>>({})
const deviceVersionsDirty = computed(() => JSON.stringify(deviceVersions.value) !== savedDeviceVersions.value)
const matchingOptions = ref<CutoverChecklistMatchingOptions | null>(null)
const deviceTypeCodes = ref<string[]>([])
const frozenDeviceTypeCodes = ref<string[]>([])
const rematching = ref(false)
const savedAnswers = ref('')
const answers = reactive<Record<string, string>>({})
const loading = ref(false)
const generating = ref(false)
const saving = ref(false)
const submitting = ref(false)
const exporting = ref(false)
const submitBarrier = createCutoverPlanWriteBarrier()
const customVisible = ref(false)
const customItem = reactive({ itemTypeCode: 'BUSINESS_SURVEY' as 'BUSINESS_SURVEY' | 'RISK', itemName: '', itemDescription: '', required: false })
const openCustom = (code: string) => { customItem.itemTypeCode = code === 'RISK' ? 'RISK' : 'BUSINESS_SURVEY'; customVisible.value = true }
const hasAction = (action: CutoverTaskDetail['allowedActions'][number]) =>
  !props.readonly && props.detail.allowedActions.includes(action)
const canGenerate = computed(() => hasAction('GENERATE_CHECKLIST'))
const canSave = computed(() => hasAction('SAVE_CHECKLIST'))
const canRequestCollection = computed(() => hasAction('REQUEST_COLLECTION'))
const canSubmit = computed(() => hasAction('SUBMIT_CHECKLIST'))
const canExport = computed(() => checklist.value !== null
  && ['DRAFT', 'SUBMITTED'].includes(checklist.value.status))
const readonly = computed(() => props.readonly || props.detail.task.currentStage !== 'P3'
  || props.detail.task.manualGrade === 'D' || checklist.value?.status !== 'DRAFT')
const canRematch = computed(() => !readonly.value && canSave.value)
const selectionChanged = computed(() => JSON.stringify([...deviceTypeCodes.value].sort()) !== JSON.stringify([...frozenDeviceTypeCodes.value].sort()))
const frozenTypeLabels = computed(() => frozenDeviceTypeCodes.value.map(code => matchingOptions.value?.deviceTypes.find(option => option.value === code)?.label || code).join('、'))
const search = ref('')
const applicableItems = computed(() => checklist.value?.items.filter((item) => item.applicable) || [])
const cutoverTypeLabel = computed(() => matchingOptions.value?.cutoverTypes?.find(option => option.value === props.detail.task.cutoverType)?.label || props.detail.task.cutoverType || '未配置')
const networkModeLabel = computed(() => matchingOptions.value?.networkModes?.find(option => option.value === props.detail.task.networkMode)?.label || props.detail.task.networkMode || '未配置')
const itemSections = computed(() => [
  { code: 'BACKGROUND', label: '割接背景', icon: 'ep:document' },
  { code: 'BUSINESS_SURVEY', label: '业务调研项', icon: 'ep:edit' },
  { code: 'RISK', label: '风险考察表', icon: 'ep:warning' },
  { code: 'DUAL_MACHINE_CHECK', label: '双机部署规范性检查表', icon: 'ep:circle-check' },
  { code: 'TECHNICAL_BULLETIN', label: '技术公告检查表', icon: 'ep:notification' }
].map(group => ({ ...group, items: applicableItems.value.filter(item => {
  const section = item.stableItemKey === 'SURVEY_CUTOVER_BACKGROUND' ? 'BACKGROUND'
    : ['RISK_CURRENT_VERSION_BULLETIN', 'RISK_TARGET_VERSION_BULLETIN'].includes(item.stableItemKey) ? 'TECHNICAL_BULLETIN'
    : item.itemTypeCode
  return section === group.code && (!search.value || item.itemName.includes(search.value) || item.itemDescription?.includes(search.value))
}) })).filter(group => group.items.length || ['BUSINESS_SURVEY', 'RISK', 'DUAL_MACHINE_CHECK'].includes(group.code)))
const gapMessage = computed(() => {
  if (!checklist.value?.configGapSnapshot || checklist.value.configGapSnapshot === '[]') return ''
  return '当前冻结配置存在缺口，可补充任务级自定义项后提交。'
})

const hydrateAnswers = () => {
  for (const key of Object.keys(answers)) delete answers[key]
  for (const item of checklist.value?.items || []) {
    if (item.currentResult?.resultSourceCode === 'DIRECT' || (item.currentResult?.resultSourceCode === 'COLLECTION' && JSON.parse(item.currentResult.answerSnapshot).analysis)) {
      answers[item.stableItemKey] = decodeChecklistDirectAnswer(item.currentResult.answerSnapshot)
    }
  }
}
const load = async () => {
  loading.value = true
  try {
    const [options, current] = await Promise.all([
      CutoverApi.getCutoverChecklistMatchingOptions(props.detail.task.id),
      CutoverApi.getCutoverChecklist(props.detail.task.id).catch(error => {
        if (error?.response?.status === 404) return null
        message.error('调研清单加载失败，请刷新重试')
        throw error
      })
    ])
    const changedChecklist = String(current?.checklistId) !== String(checklist.value?.checklistId)
    const nextFrozenDeviceTypeCodes = current ? [...options.selectedDeviceTypeCodes] : []
    const frozenTypesChanged = JSON.stringify([...nextFrozenDeviceTypeCodes].sort()) !== JSON.stringify([...frozenDeviceTypeCodes.value].sort())
    matchingOptions.value = options
    checklist.value = current
    const external = current ? await CutoverApi.getCutoverExternalState(props.detail.task.id) : null
    hydrateExternal(external)
    frozenDeviceTypeCodes.value = nextFrozenDeviceTypeCodes
    if (changedChecklist || frozenTypesChanged) deviceTypeCodes.value = [...nextFrozenDeviceTypeCodes]
    hydrateAnswers()
    savedAnswers.value = JSON.stringify(answers)
  } finally {
    loading.value = false
  }
}
const hydrateExternal = (state: CutoverExternalState | null) => {
  const changedChecklist = String(state?.checklistId) !== String(externalState.value?.checklistId)
  const serverVersions = JSON.stringify(state?.devices || [])
  if (changedChecklist || serverVersions !== savedDeviceVersions.value) {
    deviceVersions.value = (state?.devices || []).map(device => ({ ...device }))
    savedDeviceVersions.value = serverVersions
  }
  if (!state || changedChecklist) {
    for (const key of Object.keys(externalFeedbacks)) delete externalFeedbacks[key]
    for (const key of Object.keys(savedExternalFeedbacks)) delete savedExternalFeedbacks[key]
  }
  for (const item of state?.items || []) {
    const previous = externalState.value?.items.find(entry => entry.stableItemKey === item.stableItemKey)
    if (!externalFeedbacks[item.stableItemKey] || String(previous?.resultId) !== String(item.resultId)) {
      externalFeedbacks[item.stableItemKey] = item.bulletins.map(row => ({ sourceBulletinRowId: row.sourceBulletinRowId, involved: row.involved, measures: row.measures }))
      savedExternalFeedbacks[item.stableItemKey] = JSON.stringify(externalFeedbacks[item.stableItemKey])
    }
  }
  externalState.value = state
}
const refreshAfterSubmit = async () => {
  if (!props.refreshWorkspace) throw new Error('提交后刷新未接通')
  await props.refreshWorkspace()
}
const beforeWrite = async () => {
  const state = await submitBarrier.beforeWrite()
  if (state === 'PROCEED') return true
  if (state === 'REFRESHED') {
    message.warning('已确认任务进入P4，本次未重发业务命令')
    emit('navigate', 'TASK_OVERVIEW')
  } else {
    message.warning('提交结果仍在确认中，本次未重发业务命令')
  }
  return false
}
const generate = async () => {
  if (!canGenerate.value || !deviceTypeCodes.value.length || !props.detail.assessment || !props.detail.project.projectScopeVersion) return
  if (!(await beforeWrite())) return
  generating.value = true
  try {
    await CutoverApi.generateCutoverChecklist(props.detail.task.id, {
      expectedTaskVersion: props.detail.task.version,
      expectedAssessmentVersion: props.detail.assessment.assessmentVersion,
      expectedProjectScopeVersion: props.detail.project.projectScopeVersion,
      deviceTypeCodes: [...deviceTypeCodes.value],
      selectedConflictDefinitions: {}
    }, newIntentKey())
    message.success('已按任务冻结配置生成清单')
    await props.refreshDraft?.()
    await load()
  } finally { generating.value = false }
}
const setAnswer = (stableItemKey: string, value: string) => { answers[stableItemKey] = value }
const saveDirect = async () => {
  if (!checklist.value) return false
  if (!(await beforeWrite())) return false
  const changedAnswers = Object.entries(answers).map(([stableItemKey, value]) => ({
    stableItemKey,
    answerSnapshot: encodeChecklistDirectAnswer(value)
  }))
  if (!changedAnswers.length || JSON.stringify(answers) === savedAnswers.value) return true
  saving.value = true
  try {
    await CutoverApi.saveCutoverChecklist(props.detail.task.id, {
      expectedTaskVersion: checklist.value.taskVersion,
      expectedProjectScopeVersion: checklist.value.projectScopeVersion,
      checklistId: checklist.value.checklistId,
      expectedChecklistVersion: checklist.value.checklistFactVersion,
      answers: changedAnswers
    })
    message.success('调研及风险考察清单已保存')
    await load()
    return true
  } finally { saving.value = false }
}
const mutation = (): CutoverApi.CutoverChecklistMutation => {
  if (!checklist.value) throw new Error('清单尚未加载')
  return { expectedTaskVersion: checklist.value.taskVersion, expectedProjectScopeVersion: checklist.value.projectScopeVersion,
    checklistId: checklist.value.checklistId, expectedChecklistVersion: checklist.value.checklistFactVersion }
}
const persistVersions = async () => {
  if (!checklist.value || !deviceVersionsDirty.value) return
  await CutoverApi.saveCutoverDeviceVersions(props.detail.task.id, { ...mutation(), devices: deviceVersions.value })
  await load()
}
const saveVersions = async () => {
  if (!(await beforeWrite()) || !canSave.value || readonly.value) return
  externalBusy.value = true
  try { if (!(await saveDirect())) return; await persistVersions(); message.success('设备软件版本已保存') }
  finally { externalBusy.value = false }
}
const setExternalFeedback = (key: string, feedback: CutoverExternalFeedback[]) => { externalFeedbacks[key] = feedback }
const persistExternalFeedback = async (key: string) => {
  const item = externalState.value?.items.find(entry => entry.stableItemKey === key)
  if (!item?.resultId || !item.contextCurrent || deviceVersionsDirty.value) {
    message.warning('请先保存设备版本，并按当前条件重新加载公告')
    return false
  }
  await CutoverApi.saveCutoverExternalFeedback(props.detail.task.id, key, { ...mutation(), expectedResultId: item.resultId, feedback: externalFeedbacks[key] || [] })
  await load()
  return true
}
const saveExternalFeedback = async (key: string) => {
  if (!(await beforeWrite()) || readonly.value || !canSave.value) return
  externalBusy.value = true
  try { if (!(await saveDirect())) return; if (await persistExternalFeedback(key)) message.success('本项公告核查反馈已保存') }
  finally { externalBusy.value = false }
}
const loadExternal = async (key: string) => {
  if (!(await beforeWrite()) || readonly.value || !canSave.value) return
  externalBusy.value = true
  try {
    if (!(await saveDirect())) return
    await persistVersions()
    const result = await CutoverApi.loadCutoverExternal(props.detail.task.id, key, mutation())
    if (!result.selected) message.warning('查询期间清单已变化，本次结果已保留在历史，请重新打开清单')
    else if (result.queries.some(query => query.succeeded === false)) message.warning('外部加载存在失败，请查看各设备查询原因')
    else message.success('外部公告已加载，请核查并保存本次反馈')
    await load()
  } finally { externalBusy.value = false }
}
const save = async () => {
  externalBusy.value = true
  try {
    if (!(await saveDirect())) return false
    await persistVersions()
    for (const key of Object.keys(externalFeedbacks)) {
      if (JSON.stringify(externalFeedbacks[key]) !== savedExternalFeedbacks[key] && !(await persistExternalFeedback(key))) return false
    }
    return true
  } finally { externalBusy.value = false }
}
const rematch = async () => {
  if (!canRematch.value || !checklist.value || !props.detail.assessment || !deviceTypeCodes.value.length) return
  if (!(await beforeWrite())) return
  const selected = [...deviceTypeCodes.value]
  rematching.value = true
  try {
    if (JSON.stringify(answers) !== savedAnswers.value && !(await save())) return
    if (!checklist.value) return
    deviceTypeCodes.value = selected
    await CutoverApi.rematchCutoverChecklist(props.detail.task.id, {
      expectedTaskVersion: checklist.value.taskVersion,
      expectedAssessmentVersion: props.detail.assessment.assessmentVersion,
      expectedProjectScopeVersion: checklist.value.projectScopeVersion,
      checklistId: checklist.value.checklistId,
      expectedChecklistVersion: checklist.value.checklistFactVersion,
      expectedInputSnapshotHash: checklist.value.inputSnapshotHash,
      deviceTypeCodes: selected,
      selectedConflictDefinitions: {}
    }, newIntentKey())
    message.success('已按所选设备类型重新匹配清单')
    await props.refreshDraft?.()
    await load()
  } finally { deviceTypeCodes.value = selected; rematching.value = false }
}
const saveManual = async (stableItemKey: string, file: ChecklistFileHandle, factDescription: string) => {
  if (!checklist.value) return
  if (!(await beforeWrite())) return
    if (checklist.value.items.find(item => item.stableItemKey === stableItemKey)?.workModeCode === 'EXTERNAL'
      && !(await saveDirect())) return
  await CutoverApi.saveManualChecklistResult(props.detail.task.id, stableItemKey, {
    expectedTaskVersion: checklist.value.taskVersion,
    expectedProjectScopeVersion: checklist.value.projectScopeVersion,
    checklistId: checklist.value.checklistId,
    expectedChecklistVersion: checklist.value.checklistFactVersion,
    file,
    factDescription
  })
  message.success('人工证据已选为当前结果')
  await load()
}
const addCustom = async () => {
  if (!checklist.value || !customItem.itemName.trim()) return
  if (!(await beforeWrite())) return
  await CutoverApi.addCustomChecklistItem(props.detail.task.id, {
    expectedTaskVersion: checklist.value.taskVersion,
    expectedProjectScopeVersion: checklist.value.projectScopeVersion,
    checklistId: checklist.value.checklistId,
    expectedChecklistVersion: checklist.value.checklistFactVersion,
    itemTypeCode: customItem.itemTypeCode,
    itemName: customItem.itemName.trim(),
    itemDescription: customItem.itemDescription.trim(),
    interfaceFormatCode: 'TEXT',
    interfaceSchema: '{"type":"string"}',
    required: customItem.required,
    answerSnapshot: null
  })
  customItem.itemName = ''
  customItem.itemDescription = ''
  customItem.required = false
  customVisible.value = false
  message.success('已添加任务级自定义项')
  await load()
}
const removeCustom = async (stableItemKey: string) => {
  if (!checklist.value) return
  if (!(await beforeWrite())) return
  await CutoverApi.removeCustomChecklistItem(props.detail.task.id, stableItemKey, {
    expectedTaskVersion: checklist.value.taskVersion,
    expectedProjectScopeVersion: checklist.value.projectScopeVersion,
    checklistId: checklist.value.checklistId,
    expectedChecklistVersion: checklist.value.checklistFactVersion
  })
  message.success('已移出自定义项')
  await load()
}
const requestCollection = async (stableItemKey: string, deviceId: string, commandTemplateId: string, executionId: string) => {
  if (!checklist.value) return
  if (!(await beforeWrite())) return
  await CutoverApi.requestChecklistCollection(props.detail.task.id, stableItemKey, {
    expectedTaskVersion: checklist.value.taskVersion,
    expectedProjectScopeVersion: checklist.value.projectScopeVersion,
    checklistId: checklist.value.checklistId,
    expectedChecklistVersion: checklist.value.checklistFactVersion,
    deviceId,
    commandTemplateId
  }, newIntentKey(), executionId)
  message.success('已关联平台采集记录')
  await load()
}
const exportChecklist = async () => {
  if (!checklist.value) return
  exporting.value = true
  try {
    const content = await CutoverApi.exportCutoverChecklist(
      props.detail.task.id,
      checklist.value.checklistVersion
    )
    download.excel(
      content,
      `cutover-checklist-${props.detail.task.id}-v${checklist.value.checklistVersion}.xlsx`
    )
  } finally {
    exporting.value = false
  }
}
const submit = async () => {
  if (!checklist.value || !props.detail.assessment || selectionChanged.value || !frozenDeviceTypeCodes.value.length) return
  if (!(await beforeWrite())) return
  submitting.value = true
  try {
    if (!(await save())) return
    const result = await CutoverApi.submitCutoverChecklist(props.detail.task.id, {
      expectedTaskVersion: checklist.value.taskVersion,
      expectedAssessmentVersion: props.detail.assessment.assessmentVersion,
      expectedProjectScopeVersion: checklist.value.projectScopeVersion,
      checklistId: checklist.value.checklistId,
      expectedChecklistVersion: checklist.value.checklistFactVersion
    }, newIntentKey())
    message.success('P3 清单已提交，任务进入 P4')
    try {
      await refreshAfterSubmit()
      emit('navigate', result.navigationDecision.target)
    } catch {
      submitBarrier.register(refreshAfterSubmit)
      message.warning('业务提交结果待刷新确认，请再次操作以仅重试刷新')
    }
  } catch (error) {
    const status = (error as { response?: { status?: number } })?.response?.status
    if (status && status !== 500) throw error
    submitBarrier.register(refreshAfterSubmit)
    message.warning('提交响应未确认，请再次操作以仅刷新任务状态')
  } finally { submitting.value = false }
}

watch(() => props.detail.task.id, load, { immediate: true })
</script>

<style scoped>
.checklist-panel { min-width: 0; }
.matching-grid { display: grid; grid-template-columns: 1.4fr 1.2fr 1.2fr .55fr; gap: 16px; }
.matching-grid .el-select { width: 100%; }
.matching-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 14px; }
.matching-footer p { margin: 0; }
.checklist-search { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 0 0 12px; }
.item-description { margin: 5px 0 3px; color: #888; font-size: 12px; line-height: 1.6; }
.group-actions { padding: 12px 20px; border-top: 1px solid #f0f2f5; }
@media (width <= 1023px) { .matching-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (width <= 767px) { .matching-grid { grid-template-columns: 1fr; } .matching-footer { align-items: flex-start; flex-direction: column; } }
</style>
