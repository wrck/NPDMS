<template>
  <Dialog v-model="visible" title="创建割接任务" width="min(760px, 94vw)">
    <el-steps :active="step" finish-status="success" class="wizard-steps">
      <el-step title="解析设备" /><el-step title="选择项目" /><el-step title="填写任务" />
    </el-steps>

    <el-form label-position="top">
      <template v-if="step === 0">
        <el-form-item label="设备序列号">
          <el-input v-model="serialText" type="textarea" :rows="6" placeholder="每行一个 SN，也支持逗号分隔" />
        </el-form-item>
        <el-alert title="系统将按当前设备归属解析可创建的项目候选。" type="info" :closable="false" />
      </template>

      <template v-else-if="step === 1">
        <el-radio-group v-model="selectedProjectId" class="candidate-list">
          <el-radio v-for="candidate in candidates" :key="String(candidate.project.projectId)" :value="String(candidate.project.projectId)" class="candidate-card">
            <strong>{{ candidate.project.projectName }}</strong>
            <span>{{ candidate.project.projectCode }} · {{ candidate.project.officeName }}</span>
            <span>{{ candidate.devices.length }} 台设备 · {{ candidate.implementationReadiness.decision }}</span>
            <span>客户等级：{{ candidate.customerServiceLevel.serviceLevelCode || '未配置' }}</span>
          </el-radio>
        </el-radio-group>
      </template>

      <template v-else>
        <el-descriptions v-if="selectedCandidate" :column="2" border class="context-summary">
          <el-descriptions-item label="项目">{{ selectedCandidate.project.projectName }}</el-descriptions-item>
          <el-descriptions-item label="办事处">{{ selectedCandidate.project.officeName }}</el-descriptions-item>
          <el-descriptions-item label="设备">{{ selectedCandidate.devices.length }} 台</el-descriptions-item>
          <el-descriptions-item label="实施就绪">{{ selectedCandidate.implementationReadiness.decision }}</el-descriptions-item>
        </el-descriptions>
        <el-form-item label="配置修订">
          <el-select v-model="form.configurationCode" placeholder="请选择当前生效配置">
            <el-option
              v-for="choice in configurationChoices"
              :key="String(choice.revisionId)"
              :label="`${choice.configurationName}（${choice.configurationCode} / R${choice.revisionNo}）`"
              :value="choice.configurationCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务名称"><el-input v-model="form.taskName" maxlength="128" /></el-form-item>
        <el-form-item label="割接背景"><el-input v-model="form.background" type="textarea" :rows="3" maxlength="4000" /></el-form-item>
        <div class="form-grid">
          <el-form-item label="割接类型"><el-input v-model="form.cutoverType" placeholder="选择启用的割接类型代码" /></el-form-item>
          <el-form-item label="组网方式"><el-input v-model="form.networkMode" clearable placeholder="可选" /></el-form-item>
          <el-form-item label="计划时间"><el-date-picker v-model="form.scheduledTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" class="!w-full" /></el-form-item>
        </div>
      </template>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button v-if="step > 0" @click="step--">上一步</el-button>
      <el-button v-if="step === 0" type="primary" :loading="resolving" :disabled="!parsedSerials.length" @click="resolve">解析项目</el-button>
      <el-button v-else-if="step === 1" type="primary" :disabled="!selectedCandidate" @click="step = 2">下一步</el-button>
      <el-button v-else type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">创建并进入 P2</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { useMessage } from '@/hooks/web/useMessage'
import * as CutoverApi from '@/api/pms/cutover/cutover-task'
import type { ConfigurationChoice, CreateContextCandidate } from '@/api/pms/cutover/cutover-task'
import { buildCreateRequest, newIntentKey, parseSerials } from '../cutoverTaskInteraction'

const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ created: [] }>()
const message = useMessage()
const step = ref(0)
const serialText = ref('')
const candidates = ref<CreateContextCandidate[]>([])
const configurationChoices = ref<ConfigurationChoice[]>([])
const selectedProjectId = ref('')
const resolving = ref(false)
const submitting = ref(false)
const form = reactive({ configurationCode: '', taskName: '', background: '', cutoverType: '', networkMode: null as string | null, scheduledTime: '' })

const parsedSerials = computed(() => parseSerials(serialText.value))
const selectedCandidate = computed(() => candidates.value.find((item) => String(item.project.projectId) === selectedProjectId.value))
const canSubmit = computed(() => Boolean(selectedCandidate.value?.createAllowed && form.configurationCode && form.taskName.trim() && form.background.trim() && form.cutoverType && form.scheduledTime))

const resolve = async () => {
  resolving.value = true
  try {
    const result = await CutoverApi.resolveCreateContext(parsedSerials.value)
    candidates.value = result.candidates
    configurationChoices.value = result.configurationChoices
    form.configurationCode = result.configurationChoices.length === 1
      ? result.configurationChoices[0].configurationCode
      : ''
    selectedProjectId.value = result.candidates.length === 1 ? String(result.candidates[0].project.projectId) : ''
    step.value = 1
  } finally { resolving.value = false }
}

const submit = async () => {
  if (!selectedCandidate.value) return
  submitting.value = true
  try {
    await CutoverApi.createCutoverTask(buildCreateRequest(selectedCandidate.value, form), newIntentKey())
    message.success('割接任务已创建并进入 P2 人工分级')
    visible.value = false
    emit('created')
  } finally { submitting.value = false }
}

watch(visible, (open) => {
  if (!open) return
  step.value = 0
  serialText.value = ''
  candidates.value = []
  configurationChoices.value = []
  selectedProjectId.value = ''
  Object.assign(form, { configurationCode: '', taskName: '', background: '', cutoverType: '', networkMode: null, scheduledTime: '' })
})
</script>

<style scoped>
.wizard-steps { margin-bottom: 24px; }
.candidate-list { display: grid; gap: 12px; width: 100%; }
.candidate-card { box-sizing: border-box; width: 100%; height: auto; margin: 0; padding: 14px; border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); }
.candidate-card :deep(.el-radio__label) { display: grid; gap: 4px; white-space: normal; }
.candidate-card span { color: var(--el-text-color-secondary); }
.context-summary { margin-bottom: 18px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }
@media (max-width: 767px) { .form-grid { grid-template-columns: 1fr; } }
</style>
