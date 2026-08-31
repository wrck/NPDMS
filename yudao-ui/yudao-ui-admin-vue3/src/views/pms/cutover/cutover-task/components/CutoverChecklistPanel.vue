<template>
  <section class="checklist-panel" aria-labelledby="cutover-checklist-title">
    <header class="panel-heading">
      <div>
        <h3 id="cutover-checklist-title">P3 动态采集清单</h3>
        <p>{{ checklist ? `清单版本 ${checklist.checklistVersion}` : '按任务冻结配置生成清单' }}</p>
      </div>
      <el-tag v-if="checklist">{{ checklist.status }}</el-tag>
    </header>

    <el-alert v-if="gapMessage" :title="gapMessage" type="warning" :closable="false" show-icon />
    <el-empty v-if="!loading && !checklist" description="尚未生成 P3 清单">
      <el-button
        v-if="canGenerate"
        type="primary"
        v-hasPermi="['pms:cutover-task:save-checklist']"
        :loading="generating"
        @click="generate"
      >生成清单</el-button>
    </el-empty>
    <div v-else v-loading="loading">
      <CutoverChecklistField
        v-for="item in applicableItems"
        :key="item.stableItemKey"
        :item="item"
        :direct-value="answers[item.stableItemKey] || ''"
        :readonly="readonly"
        :allow-save="canSave"
        :allow-collection="canRequestCollection"
        :devices="detail.devices"
        @direct="setAnswer"
        @manual="saveManual"
        @collection="requestCollection"
        @remove="removeCustom"
      />
      <details v-if="checklist && !readonly && canSave" class="custom-item-area">
        <summary>补充任务级自定义项</summary>
        <el-input v-model="customItem.itemName" data-testid="custom-item-name" placeholder="清单项名称" />
        <el-input v-model="customItem.itemDescription" placeholder="核查说明" />
        <el-checkbox v-model="customItem.required">必填</el-checkbox>
        <el-button data-testid="add-custom-item" :disabled="!customItem.itemName.trim()" @click="addCustom">
          添加自定义项
        </el-button>
      </details>
      <footer v-if="checklist" class="panel-actions">
        <el-button
          v-if="!readonly && canSave"
          data-testid="checklist-save"
          v-hasPermi="['pms:cutover-task:save-checklist']"
          :loading="saving"
          @click="save"
        >暂存</el-button>
        <el-button
          v-if="!readonly && canSubmit"
          type="primary"
          v-hasPermi="['pms:cutover-task:submit-checklist']"
          :loading="submitting"
          @click="submit"
        >提交并进入 P4</el-button>
      </footer>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useMessage } from '@/hooks/web/useMessage'
import * as CutoverApi from '@/api/pms/cutover/cutover-task'
import type { ChecklistFileHandle, CutoverChecklistView, CutoverTaskDetail } from '@/api/pms/cutover/cutover-task'
import {
  decodeChecklistDirectAnswer,
  encodeChecklistDirectAnswer,
  newIntentKey
} from '../cutoverTaskInteraction'
import CutoverChecklistField from './CutoverChecklistField.vue'

const props = defineProps<{ detail: CutoverTaskDetail }>()
const emit = defineEmits<{ submitted: [] }>()
const message = useMessage()
const checklist = ref<CutoverChecklistView | null>(null)
const answers = reactive<Record<string, string>>({})
const loading = ref(false)
const generating = ref(false)
const saving = ref(false)
const submitting = ref(false)
const customItem = reactive({ itemName: '', itemDescription: '', required: false })
const hasAction = (action: CutoverTaskDetail['allowedActions'][number]) =>
  props.detail.allowedActions.includes(action)
const canGenerate = computed(() => hasAction('GENERATE_CHECKLIST'))
const canSave = computed(() => hasAction('SAVE_CHECKLIST'))
const canRequestCollection = computed(() => hasAction('REQUEST_COLLECTION'))
const canSubmit = computed(() => hasAction('SUBMIT_CHECKLIST'))
const readonly = computed(() => props.detail.task.currentStage !== 'P3'
  || props.detail.task.manualGrade === 'D' || checklist.value?.status !== 'DRAFT')
const applicableItems = computed(() => checklist.value?.items.filter((item) => item.applicable) || [])
const gapMessage = computed(() => {
  if (!checklist.value?.configGapSnapshot || checklist.value.configGapSnapshot === '[]') return ''
  return '当前冻结配置存在缺口，可补充任务级自定义项后提交。'
})

const hydrateAnswers = () => {
  for (const key of Object.keys(answers)) delete answers[key]
  for (const item of checklist.value?.items || []) {
    if (item.currentResult?.resultSourceCode === 'DIRECT') {
      answers[item.stableItemKey] = decodeChecklistDirectAnswer(item.currentResult.answerSnapshot)
    }
  }
}
const load = async () => {
  loading.value = true
  try {
    checklist.value = await CutoverApi.getCutoverChecklist(props.detail.task.id)
    hydrateAnswers()
  } catch (error) {
    const status = (error as { response?: { status?: number } })?.response?.status
    if (status !== 404) throw error
    checklist.value = null
  } finally {
    loading.value = false
  }
}
const generate = async () => {
  if (!props.detail.assessment || !props.detail.project.projectScopeVersion) return
  generating.value = true
  try {
    await CutoverApi.generateCutoverChecklist(props.detail.task.id, {
      expectedTaskVersion: props.detail.task.version,
      expectedAssessmentVersion: props.detail.assessment.assessmentVersion,
      expectedProjectScopeVersion: props.detail.project.projectScopeVersion,
      selectedConflictDefinitions: {}
    }, newIntentKey())
    message.success('已按任务冻结配置生成清单')
    await load()
  } finally { generating.value = false }
}
const setAnswer = (stableItemKey: string, value: string) => { answers[stableItemKey] = value }
const save = async () => {
  if (!checklist.value) return
  saving.value = true
  try {
    await CutoverApi.saveCutoverChecklist(props.detail.task.id, {
      expectedTaskVersion: checklist.value.taskVersion,
      expectedProjectScopeVersion: checklist.value.projectScopeVersion,
      checklistId: checklist.value.checklistId,
      expectedChecklistVersion: checklist.value.checklistFactVersion,
      answers: Object.entries(answers)
        .filter(([, value]) => value)
        .map(([stableItemKey, value]) => ({
          stableItemKey,
          answerSnapshot: encodeChecklistDirectAnswer(value)
        }))
    })
    message.success('P3 清单草稿已暂存')
    await load()
  } finally { saving.value = false }
}
const saveManual = async (stableItemKey: string, file: ChecklistFileHandle, factDescription: string) => {
  if (!checklist.value) return
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
  await CutoverApi.addCustomChecklistItem(props.detail.task.id, {
    expectedTaskVersion: checklist.value.taskVersion,
    expectedProjectScopeVersion: checklist.value.projectScopeVersion,
    checklistId: checklist.value.checklistId,
    expectedChecklistVersion: checklist.value.checklistFactVersion,
    itemTypeCode: 'BUSINESS_SURVEY',
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
  message.success('已添加任务级自定义项')
  await load()
}
const removeCustom = async (stableItemKey: string) => {
  if (!checklist.value) return
  await CutoverApi.removeCustomChecklistItem(props.detail.task.id, stableItemKey, {
    expectedTaskVersion: checklist.value.taskVersion,
    expectedProjectScopeVersion: checklist.value.projectScopeVersion,
    checklistId: checklist.value.checklistId,
    expectedChecklistVersion: checklist.value.checklistFactVersion
  })
  message.success('已移出自定义项')
  await load()
}
const requestCollection = async (stableItemKey: string, deviceId: string, commandTemplateId: string) => {
  if (!checklist.value) return
  await CutoverApi.requestChecklistCollection(props.detail.task.id, stableItemKey, {
    expectedTaskVersion: checklist.value.taskVersion,
    expectedProjectScopeVersion: checklist.value.projectScopeVersion,
    checklistId: checklist.value.checklistId,
    expectedChecklistVersion: checklist.value.checklistFactVersion,
    deviceId,
    commandTemplateId
  }, newIntentKey())
  message.success('采集请求已形成当前结果')
  await load()
}
const submit = async () => {
  if (!checklist.value || !props.detail.assessment) return
  submitting.value = true
  try {
    await CutoverApi.submitCutoverChecklist(props.detail.task.id, {
      expectedTaskVersion: checklist.value.taskVersion,
      expectedAssessmentVersion: props.detail.assessment.assessmentVersion,
      expectedProjectScopeVersion: checklist.value.projectScopeVersion,
      checklistId: checklist.value.checklistId,
      expectedChecklistVersion: checklist.value.checklistFactVersion
    }, newIntentKey())
    message.success('P3 清单已提交，任务进入 P4')
    emit('submitted')
  } finally { submitting.value = false }
}

watch(() => props.detail.task.id, load, { immediate: true })
</script>

<style scoped>
.checklist-panel { padding-top: 18px; border-top: 1px solid var(--el-border-color-lighter); }
.panel-heading { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.panel-heading h3, .panel-heading p { margin: 0; }
.panel-heading p { margin-top: 4px; color: var(--el-text-color-secondary); }
.panel-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 16px; }
.custom-item-area { margin-top: 14px; padding: 12px; border: 1px dashed var(--el-border-color); border-radius: 8px; }
.custom-item-area summary { margin-bottom: 10px; cursor: pointer; }
.custom-item-area :deep(.el-input) { margin-bottom: 10px; }
@media (max-width: 767px) { .panel-actions { display: grid; grid-template-columns: 1fr; } }
</style>
