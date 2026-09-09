<template>
  <section class="survey-metadata" aria-label="工勘基本信息">
    <header class="survey-heading">
      <div>
        <h4>工勘基本信息</h4>
        <p>记录现场情况与总体结论，工勘项确认仍按各项办理。</p>
      </div>
      <el-button
        v-if="!editing && canEdit"
        data-testid="survey-edit"
        :disabled="loading"
        @click="beginEdit"
      >
        编辑基本信息
      </el-button>
    </header>

    <el-alert
      v-if="errorText"
      :title="errorText"
      :type="awaitingReload ? 'warning' : 'error'"
      :closable="false"
      show-icon
      class="survey-alert"
      data-testid="survey-error"
    >
      <el-button
        v-if="awaitingReload || !metadata"
        data-testid="survey-reload"
        :loading="loading"
        :disabled="saving"
        @click="reload"
      >
        重新加载
      </el-button>
    </el-alert>
    <el-skeleton v-if="loading && !metadata" :rows="4" animated />

    <template v-if="metadata">
      <dl v-if="!editing" class="survey-grid survey-summary">
        <div
          ><dt>工勘日期</dt><dd>{{ metadata.surveyDate || '未填写' }}</dd></div
        >
        <div
          ><dt>工勘人员</dt><dd>{{ surveyorLabel }}</dd></div
        >
        <div class="survey-wide">
          <dt>现场地点</dt>
          <dd>
            {{ metadata.location || '未填写' }}
            <el-tag
              v-if="metadata.locationResolutionStatus === 'UNRESOLVED'"
              type="warning"
              size="small"
            >
              待维护
            </el-tag>
          </dd>
        </div>
        <div
          ><dt>接地条件</dt><dd>{{ metadata.grounding || '未填写' }}</dd></div
        >
        <div
          ><dt>施工资源</dt><dd>{{ metadata.constructionResource || '未填写' }}</dd></div
        >
        <div class="survey-wide"
          ><dt>总体结论</dt><dd>{{ metadata.conclusion || '未填写' }}</dd></div
        >
      </dl>

      <el-form v-else label-position="top" :model="form" @submit.prevent="save">
        <el-alert
          v-if="!canEdit && !awaitingReload"
          title="当前为只读状态，已保留编辑内容；可取消编辑，不可保存。"
          type="info"
          :closable="false"
          class="survey-alert"
        />
        <fieldset :disabled="formDisabled" class="survey-fields">
          <div class="survey-grid">
            <el-form-item label="工勘日期">
              <el-date-picker
                v-model="form.surveyDate"
                data-testid="survey-date"
                type="date"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                placeholder="选择工勘日期"
                :disabled="formDisabled"
                clearable
              />
            </el-form-item>
            <el-form-item label="工勘人员">
              <el-select
                v-model="form.surveyorUserId"
                data-testid="survey-person"
                filterable
                remote
                clearable
                :value-on-clear="null"
                :remote-method="searchCandidates"
                :loading="candidateLoading"
                :disabled="formDisabled"
                placeholder="按姓名、账号或工号搜索"
                @visible-change="openCandidateSelect"
              >
                <el-option
                  v-for="candidate in candidateOptions"
                  :key="candidate.userId"
                  :label="candidateLabel(candidate)"
                  :value="candidate.userId"
                />
              </el-select>
              <p v-if="candidateError" class="survey-field-error" role="alert">{{
                candidateError
              }}</p>
            </el-form-item>
            <el-form-item label="现场地点" class="survey-wide">
              <!-- PRE-02：只读不挂载地点管理查询，切换对象时销毁旧选择器。 -->
              <PmsLocationSelector
                :key="contextEpoch"
                v-model="locationDraft"
                :project-id="project.id"
              />
            </el-form-item>
            <el-form-item label="接地条件">
              <el-input
                v-model="form.grounding"
                data-testid="survey-grounding"
                type="textarea"
                :rows="3"
                :disabled="formDisabled"
                placeholder="填写现场接地条件"
              />
            </el-form-item>
            <el-form-item label="施工资源">
              <el-input
                v-model="form.constructionResource"
                data-testid="survey-resource"
                type="textarea"
                :rows="3"
                :disabled="formDisabled"
                placeholder="填写现场施工资源情况"
              />
            </el-form-item>
            <el-form-item label="总体结论" class="survey-wide">
              <el-input
                v-model="form.conclusion"
                data-testid="survey-conclusion"
                type="textarea"
                :rows="3"
                :disabled="formDisabled"
                placeholder="填写本次工勘的总体结论"
              />
            </el-form-item>
          </div>
        </fieldset>
        <footer class="survey-actions">
          <span v-if="awaitingReload">已保存，等待核对最新信息</span>
          <span v-else-if="dirty">有未保存的修改</span>
          <span v-else>未修改</span>
          <el-button
            data-testid="survey-cancel"
            :disabled="saving || awaitingReload"
            @click="discardChanges"
            >取消编辑</el-button
          >
          <el-button
            data-testid="survey-save"
            type="primary"
            :loading="saving"
            :disabled="formDisabled || !dirty || project.version == null"
            @click="save"
            >保存基本信息</el-button
          >
        </footer>
      </el-form>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import * as PreparationApi from '@/api/pms/engineering/preparation'
import type {
  AssignmentCandidateVO,
  PreparationVO,
  SurveyMetadata,
  SurveyMetadataSave
} from '@/api/pms/engineering/preparation'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import type { LocationMaintainRequest } from '@/api/pms/asset/location'
import PmsLocationSelector from '@/components/PmsLocationSelector/index.vue'

// PRE-02 / F-SOL-002：仅消费 SOL 元数据命令，AST 地点仍由 Owner 维护。
const props = defineProps<{
  preparation: PreparationVO
  project: ProjectMasterVO
  readonly?: boolean
}>()
const emit = defineEmits<{
  (e: 'saved'): void
  (e: 'dirty-change', value: boolean): void
}>()

const metadata = ref<SurveyMetadata | null>(null)
const editing = ref(false)
const loading = ref(false)
const saving = ref(false)
const awaitingReload = ref(false)
const errorText = ref('')
const contextEpoch = ref(0)
let active = true
let candidateRequest = 0
const candidateLoading = ref(false)
const candidateError = ref('')
const candidates = ref<AssignmentCandidateVO[]>([])

const emptyForm = () => ({
  surveyDate: '' as string | null,
  surveyorUserId: undefined as SurveyMetadata['surveyorUserId'] | undefined,
  grounding: '',
  constructionResource: '',
  conclusion: ''
})
const form = reactive(emptyForm())
const locationDraft = ref<LocationMaintainRequest>()
const baseline = ref('')
const baselineLocation = ref('')

// ID 原样透传；字符串比较不把带前导零的 ID 转成数字。
const contextMatches = computed(
  () => props.project.id != null && String(props.project.id) === String(props.preparation.projectId)
)
const canEdit = computed(
  () =>
    !props.readonly &&
    contextMatches.value &&
    !!metadata.value?.allowedActions.includes('UPDATE_SURVEY')
)
const formDisabled = computed(
  () => !canEdit.value || saving.value || loading.value || awaitingReload.value
)

// 忽略对象键顺序与 undefined，避免选择器初始化回传造成虚假的地点变更。
const canonical = (value: unknown): string =>
  JSON.stringify(value, (_key, item) =>
    item && typeof item === 'object' && !Array.isArray(item)
      ? Object.fromEntries(
          Object.keys(item)
            .sort()
            .map((key) => [key, item[key]])
        )
      : item
  )
const formValues = () => ({
  surveyDate: form.surveyDate || null,
  surveyorUserId: form.surveyorUserId ?? null,
  grounding: form.grounding || null,
  constructionResource: form.constructionResource || null,
  conclusion: form.conclusion || null
})
const locationCommand = (): LocationMaintainRequest | undefined => {
  if (!locationDraft.value) return undefined
  const command = { ...locationDraft.value, projectId: props.project.id }
  const node = command.siteLocation
  if (
    node &&
    node.id == null &&
    !node.code &&
    !node.name &&
    !node.locationType &&
    node.parentId == null
  ) {
    command.siteLocation = undefined
  }
  return command
}
const locationChanged = computed(() => canonical(locationCommand()) !== baselineLocation.value)
const dirty = computed(
  () =>
    saving.value ||
    awaitingReload.value ||
    (editing.value && (canonical(formValues()) !== baseline.value || locationChanged.value))
)
const isDirty = () => dirty.value
watch(dirty, (value) => emit('dirty-change', value), { flush: 'sync' })

const candidateLabel = (candidate: AssignmentCandidateVO) =>
  [candidate.nickname || candidate.username, candidate.employeeNo, candidate.departmentName]
    .filter(Boolean)
    .join(' · ')
const fallbackCandidate = (
  userId: NonNullable<SurveyMetadata['surveyorUserId']>
): AssignmentCandidateVO => ({
  userId,
  username: String(userId),
  nickname: `工勘人员 #${userId}`
})
const candidateOptions = computed(() => {
  const userId = form.surveyorUserId
  if (
    userId == null ||
    candidates.value.some((candidate) => String(candidate.userId) === String(userId))
  ) {
    return candidates.value
  }
  return [fallbackCandidate(userId), ...candidates.value]
})
const surveyorLabel = computed(() => {
  const userId = metadata.value?.surveyorUserId
  if (userId == null) return '未填写'
  return candidateLabel(
    candidates.value.find((candidate) => String(candidate.userId) === String(userId)) ||
      fallbackCandidate(userId)
  )
})

const captureContext = () => ({
  epoch: contextEpoch.value,
  projectId: props.project.id,
  preparationId: props.preparation.preparationId
})
const isCurrent = (context: ReturnType<typeof captureContext>) =>
  active &&
  context.epoch === contextEpoch.value &&
  context.projectId === props.project.id &&
  context.preparationId === props.preparation.preparationId

const resetDraft = () => {
  const current = metadata.value
  Object.assign(
    form,
    emptyForm(),
    current
      ? {
          surveyDate: current.surveyDate ?? '',
          surveyorUserId: current.surveyorUserId ?? undefined,
          grounding: current.grounding ?? '',
          constructionResource: current.constructionResource ?? '',
          conclusion: current.conclusion ?? ''
        }
      : {}
  )
  locationDraft.value = {
    projectId: props.project.id,
    address:
      current?.addressId != null
        ? { id: current.addressId, expectedVersion: current.addressVersion }
        : undefined,
    site:
      current?.siteId != null
        ? { id: current.siteId, expectedVersion: current.siteVersion }
        : undefined,
    siteLocation:
      current?.siteLocationId != null
        ? { id: current.siteLocationId, expectedVersion: current.siteLocationVersion }
        : undefined,
    fallbackLocation: current?.location ?? ''
  }
  baseline.value = canonical(formValues())
  baselineLocation.value = canonical(locationCommand())
}
const applyReadback = (value: SurveyMetadata, context: ReturnType<typeof captureContext>) => {
  if (String(value.preparationId) !== String(context.preparationId))
    throw new Error('PREPARATION_MISMATCH')
  metadata.value = value
  editing.value = false
  awaitingReload.value = false
  resetDraft()
}
const reload = async () => {
  if (
    !contextMatches.value ||
    loading.value ||
    saving.value ||
    (editing.value && !awaitingReload.value)
  )
    return
  const context = captureContext()
  const saved = awaitingReload.value
  loading.value = true
  errorText.value = ''
  try {
    const value = await PreparationApi.getSurvey(context.preparationId)
    if (!isCurrent(context)) return
    applyReadback(value, context)
    if (saved) emit('saved')
  } catch {
    if (isCurrent(context))
      errorText.value = saved
        ? '保存已成功，但最新信息回读失败。输入已保留，请重新加载核对，不要重复提交。'
        : '工勘基本信息加载失败，请重新加载。'
  } finally {
    if (isCurrent(context)) loading.value = false
  }
}
const beginEdit = () => {
  if (!canEdit.value || loading.value || saving.value || awaitingReload.value || editing.value)
    return
  resetDraft()
  errorText.value = ''
  editing.value = true
}
const discardChanges = () => {
  if (saving.value || awaitingReload.value) return false
  editing.value = false
  candidateRequest++
  candidateLoading.value = false
  candidateError.value = ''
  errorText.value = ''
  resetDraft()
  return true
}
const searchCandidates = async (keyword: string) => {
  if (!editing.value || formDisabled.value) return
  const context = captureContext()
  const request = ++candidateRequest
  candidateLoading.value = true
  candidateError.value = ''
  try {
    const page = await PreparationApi.getAssignmentCandidates(context.preparationId, {
      keyword,
      pageNo: 1,
      pageSize: 20
    })
    if (isCurrent(context) && request === candidateRequest && editing.value)
      candidates.value = page.list
  } catch {
    if (isCurrent(context) && request === candidateRequest)
      candidateError.value = '工勘人员加载失败，请重新搜索。'
  } finally {
    if (isCurrent(context) && request === candidateRequest) candidateLoading.value = false
  }
}
const openCandidateSelect = (opened: boolean) => {
  if (opened) void searchCandidates('')
}
const save = async () => {
  if (
    !editing.value ||
    formDisabled.value ||
    !dirty.value ||
    !metadata.value ||
    props.project.version == null
  )
    return
  const context = captureContext()
  const version = metadata.value.version
  const values = formValues()
  const previous = JSON.parse(baseline.value) as ReturnType<typeof formValues>
  const patch: SurveyMetadataSave = { expectedProjectVersion: props.project.version }
  for (const key of Object.keys(values) as (keyof typeof values)[]) {
    if (values[key] !== previous[key]) Object.assign(patch, { [key]: values[key] })
  }
  if (locationChanged.value) patch.locationCommand = locationCommand()
  saving.value = true
  errorText.value = ''
  try {
    await PreparationApi.saveSurvey(context.preparationId, version, patch)
    if (!isCurrent(context)) return
    // PATCH 成功不代表回读完成：从此禁止重复提交旧版本，只允许 GET 重试。
    awaitingReload.value = true
    const value = await PreparationApi.getSurvey(context.preparationId)
    if (!isCurrent(context)) return
    applyReadback(value, context)
    saving.value = false
    emit('saved')
  } catch {
    if (isCurrent(context))
      errorText.value = awaitingReload.value
        ? '保存已成功，但最新信息回读失败。输入已保留，请重新加载核对，不要重复提交。'
        : '工勘基本信息保存失败，输入已保留，请检查后重试。'
  } finally {
    if (isCurrent(context)) saving.value = false
  }
}

// 父组件须先用 isDirty/discardChanges 保护切换；这里仅隔离已经发生的切换。
watch(
  [
    () => props.project.id,
    () => props.preparation.preparationId,
    () => props.preparation.projectId
  ],
  () => {
    contextEpoch.value++
    candidateRequest++
    editing.value = false
    saving.value = false
    loading.value = false
    awaitingReload.value = false
    metadata.value = null
    errorText.value = ''
    candidates.value = []
    candidateLoading.value = false
    candidateError.value = ''
    resetDraft()
    if (contextMatches.value) void reload()
    else errorText.value = '工勘准备与当前项目不一致，无法加载。'
  },
  { immediate: true, flush: 'sync' }
)
watch(
  () => props.preparation.version,
  () => {
    if (!editing.value && !saving.value && !awaitingReload.value) void reload()
  }
)
onBeforeUnmount(() => {
  active = false
  candidateRequest++
})
defineExpose({ isDirty, discardChanges })
</script>

<style scoped>
.survey-metadata {
  padding: 20px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
}
.survey-heading,
.survey-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.survey-heading {
  justify-content: space-between;
  margin-bottom: 16px;
}
.survey-heading h4 {
  margin: 0 0 6px;
  font-size: 16px;
  color: var(--el-text-color-primary);
}
.survey-heading p,
.survey-actions > span {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.survey-alert {
  margin-bottom: 16px;
}
.survey-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 24px;
}
.survey-wide {
  grid-column: 1 / -1;
}
.survey-summary {
  margin: 0;
  gap: 20px 24px;
}
.survey-summary dt {
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.survey-summary dd {
  margin: 0;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.survey-fields {
  min-width: 0;
  padding: 0;
  margin: 0;
  border: 0;
}
.survey-fields:disabled {
  pointer-events: none;
}
.survey-grid :deep(.el-date-editor),
.survey-grid :deep(.el-select) {
  width: 100%;
  min-width: 0;
}
.survey-grid :deep(.el-form-item__content) {
  min-width: 0;
}
.survey-field-error {
  margin: 6px 0 0;
  color: var(--el-color-danger);
  font-size: 12px;
}
.survey-actions {
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.survey-actions > span {
  margin-right: auto;
}
@media (max-width: 767px) {
  .survey-metadata {
    padding: 14px;
  }
  .survey-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
  }
  .survey-summary {
    gap: 16px;
  }
  .survey-actions > span {
    flex-basis: 100%;
  }
}
</style>
