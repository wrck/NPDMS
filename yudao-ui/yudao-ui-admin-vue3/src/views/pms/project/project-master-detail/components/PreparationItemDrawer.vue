<template>
  <el-drawer v-model="visible" :size="drawerSize" title="工勘项详情">
    <template v-if="item">
      <el-descriptions :column="narrow ? 1 : 2" border size="small" class="item-summary">
        <el-descriptions-item label="工勘项">{{ item.itemName }}</el-descriptions-item>
        <el-descriptions-item label="确认状态">{{ item.confirmationStatus }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-position="top" class="item-form">
        <el-form-item label="适用性">
          <el-radio-group v-model="draft.applicabilityCode" :disabled="!canManager">
            <el-radio-button value="REQUIRED">适用</el-radio-button>
            <el-radio-button value="NOT_APPLICABLE_PENDING">不适用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="draft.applicabilityCode === 'NOT_APPLICABLE_PENDING'"
          label="不适用原因"
        >
          <el-input
            v-model="draft.notApplicableReason"
            type="textarea"
            :rows="3"
            :disabled="!canManager"
          />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select
            v-model="draft.assigneeUserId"
            clearable
            filterable
            remote
            :remote-method="searchCandidates"
            :loading="candidateLoading"
            :disabled="!canManager"
            placeholder="按姓名、账号或工号搜索"
            @visible-change="openCandidateSelect"
          >
            <el-option
              v-for="candidate in candidates"
              :key="candidate.userId"
              :value="candidate.userId"
              :label="candidateLabel(candidate)"
            />
          </el-select>
          <Pagination
            v-if="canManager && candidateTotal > candidateQuery.pageSize"
            v-model:page="candidateQuery.pageNo"
            v-model:limit="candidateQuery.pageSize"
            :total="candidateTotal"
            @pagination="loadCandidates"
          />
        </el-form-item>
        <el-form-item label="上架加电外包">
          <el-switch v-model="draft.outsourced" :disabled="!canManager" />
        </el-form-item>
        <el-form-item label="确认结果">
          <el-input
            v-model="draft.siteResultCode"
            :disabled="!canAssignee"
            placeholder="按冻结表单填写结果编码"
          />
        </el-form-item>
        <el-form-item label="结果说明">
          <el-input
            v-model="draft.siteResultDetail"
            type="textarea"
            :rows="3"
            :disabled="!canAssignee"
          />
        </el-form-item>
        <section class="fixed-form" aria-labelledby="fixed-form-title">
          <h4 id="fixed-form-title"
            >固定表单 · {{ item.form.formCode }} v{{ item.form.formVersion }}</h4
          >
          <el-form-item
            v-for="field in schemaFields"
            :key="field.fieldCode"
            :label="field.fieldCode"
          >
            <el-select
              v-if="field.fieldType === 'SINGLE_SELECT'"
              v-model="formValues[field.fieldCode]"
              :disabled="!canAssignee"
            >
              <el-option
                v-for="option in field.options || []"
                :key="option"
                :label="option"
                :value="option"
              />
            </el-select>
            <el-select
              v-else-if="field.fieldType === 'MULTI_SELECT'"
              v-model="formValues[field.fieldCode]"
              multiple
              :disabled="!canAssignee"
            >
              <el-option
                v-for="option in field.options || []"
                :key="option"
                :label="option"
                :value="option"
              />
            </el-select>
            <el-switch
              v-else-if="field.fieldType === 'BOOLEAN'"
              v-model="formValues[field.fieldCode]"
              :disabled="!canAssignee"
            />
            <el-input-number
              v-else-if="field.fieldType === 'NUMBER'"
              v-model="formValues[field.fieldCode]"
              :disabled="!canAssignee"
            />
            <el-input
              v-else
              v-model="formValues[field.fieldCode]"
              :type="field.maxLength && field.maxLength > 255 ? 'textarea' : 'text'"
              :disabled="!canAssignee"
            />
          </el-form-item>
        </section>
        <section class="evidence" aria-labelledby="evidence-title">
          <h4 id="evidence-title">文件证据</h4>
          <PmsFileReferenceList
            v-if="evidence?.artifactId"
            owner-context="SOL"
            object-type="SITE_SURVEY_ITEM"
            :object-id="String(item.sourceItemId || item.itemId)"
            purpose-code="SITE_SURVEY_EVIDENCE"
            :reference-key="referenceKey"
            :artifact-id="evidence.artifactId"
            :version-no="evidence.versionNo"
            :editable="canAssignee"
            @detached="captureDetachedEvidence"
          />
          <PmsFileUploader
            v-if="canAssignee"
            owner-context="SOL"
            object-type="SITE_SURVEY_ITEM"
            :object-id="String(item.sourceItemId || item.itemId)"
            purpose-code="SITE_SURVEY_EVIDENCE"
            :reference-key="referenceKey"
            category-code="SITE_SURVEY_EVIDENCE"
            :artifact-id="evidence?.artifactId"
            :expected-reference-version="evidence?.fileFactVersion.referenceVersion"
            @completed="captureEvidence"
          />
        </section>
      </el-form>
    </template>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button v-if="editable" type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import { PmsFileReferenceList, PmsFileUploader } from '@/components/PmsFileArtifact'
import type { DetachedFileSlot, FileSelection } from '@/components/PmsFileArtifact'
import * as FileApi from '@/api/pms/platform/file'
import * as PreparationApi from '@/api/pms/engineering/preparation'
import type {
  AssignmentCandidateVO,
  EvidenceReference,
  PatchPreparationItemReqVO,
  PreparationItemVO,
  PreparationVO
} from '@/api/pms/engineering/preparation'
import { buildEvidenceReference, setChanged } from './preparationInteraction'

const props = defineProps<{ projectVersion: number }>()
const emit = defineEmits<{ saved: [] }>()
const message = useMessage()
const narrow = useMediaQuery('(max-width: 767px)')
const drawerSize = computed(() => (narrow.value ? '100%' : '640px'))
const visible = ref(false)
const saving = ref(false)
const preparation = ref<PreparationVO>()
const item = ref<PreparationItemVO>()
const canManager = computed(
  () => item.value?.allowedActions.includes('PATCH_MANAGER_FIELDS') === true
)
const canAssignee = computed(
  () => item.value?.allowedActions.includes('PATCH_ASSIGNEE_FIELDS') === true
)
const editable = computed(() => canManager.value || canAssignee.value)
const formValues = reactive<Record<string, any>>({})
const evidence = ref<EvidenceReference>()
const baseline = ref<{
  applicabilityCode: string
  outsourced: boolean
  assigneeUserId: number | null
  notApplicableReason: string | null
  siteResultCode: string | null
  siteResultDetail: string | null
  formValues: Record<string, any>
  evidence: EvidenceReference[]
}>()
const referenceKey = computed(
  () => evidence.value?.referenceKey || `site-survey-${item.value?.itemCode || 'evidence'}`
)
const candidateLoading = ref(false)
const candidates = ref<AssignmentCandidateVO[]>([])
const candidateTotal = ref(0)
const candidateQuery = reactive({ keyword: '', pageNo: 1, pageSize: 10 })
const draft = reactive({
  applicabilityCode: 'REQUIRED',
  outsourced: false,
  assigneeUserId: undefined as number | undefined,
  notApplicableReason: '',
  siteResultCode: '',
  siteResultDetail: ''
})
type SchemaField = {
  fieldCode: string
  fieldType: 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'SINGLE_SELECT' | 'MULTI_SELECT'
  maxLength?: number
  options?: string[]
}
const schemaFields = computed<SchemaField[]>(
  () => parseJson<{ fields?: SchemaField[] }>(item.value?.form.schemaSnapshot, {}).fields || []
)

function parseJson<T>(value: string | undefined, fallback: T): T {
  try {
    return value ? JSON.parse(value) : fallback
  } catch {
    return fallback
  }
}

const open = (current: PreparationVO, row: PreparationItemVO) => {
  preparation.value = current
  item.value = row
  Object.assign(draft, {
    applicabilityCode: row.applicability,
    outsourced: row.outsourced,
    assigneeUserId: row.assigneeUserId,
    notApplicableReason: row.notApplicableReason || '',
    siteResultCode: row.siteResultCode || '',
    siteResultDetail: row.siteResultDetail || ''
  })
  Object.keys(formValues).forEach((key) => delete formValues[key])
  Object.assign(formValues, parseJson(row.form.valueSnapshot, {}))
  evidence.value = parseJson<EvidenceReference[] | undefined>(
    row.evidenceReferenceSnapshot,
    undefined
  )?.[0]
  baseline.value = {
    applicabilityCode: row.applicability,
    outsourced: row.outsourced,
    assigneeUserId: row.assigneeUserId ?? null,
    notApplicableReason: row.notApplicableReason || null,
    siteResultCode: row.siteResultCode || null,
    siteResultDetail: row.siteResultDetail || null,
    formValues: parseJson(JSON.stringify(formValues), {}),
    evidence: evidence.value ? [parseJson(JSON.stringify(evidence.value), evidence.value)] : []
  }
  candidates.value = row.assigneeUserId
    ? [
        {
          userId: row.assigneeUserId,
          username: String(row.assigneeUserId),
          nickname: `当前负责人 #${row.assigneeUserId}`
        }
      ]
    : []
  candidateTotal.value = candidates.value.length
  visible.value = true
}

const candidateLabel = (candidate: AssignmentCandidateVO) =>
  [candidate.nickname || candidate.username, candidate.employeeNo, candidate.departmentName]
    .filter(Boolean)
    .join(' · ')

const loadCandidates = async () => {
  if (!preparation.value) return
  candidateLoading.value = true
  try {
    const page = await PreparationApi.getAssignmentCandidates(
      preparation.value.preparationId,
      candidateQuery
    )
    candidates.value = page.list
    candidateTotal.value = page.total
  } finally {
    candidateLoading.value = false
  }
}

const searchCandidates = (keyword: string) => {
  candidateQuery.keyword = keyword
  candidateQuery.pageNo = 1
  void loadCandidates()
}

const openCandidateSelect = (opened: boolean) => {
  if (opened && candidates.value.length <= 1) void loadCandidates()
}

const captureEvidence = async (selection: FileSelection) => {
  if (!item.value) return
  const frozenReferenceKey = evidence.value?.referenceKey
  const selectedReferenceKey = frozenReferenceKey || selection.referenceKey
  const businessKey = {
    ownerContext: 'SOL',
    objectType: 'SITE_SURVEY_ITEM',
    objectId: String(item.value.sourceItemId || item.value.itemId),
    purposeCode: 'SITE_SURVEY_EVIDENCE',
    referenceKey: selectedReferenceKey
  }
  const artifact = await FileApi.getArtifact(selection.artifactId, {
    ...businessKey
  })
  let cursor: string | undefined
  let version: FileApi.FileVersionVO | undefined
  do {
    const page = await FileApi.getVersions(selection.artifactId, {
      ...businessKey,
      cursor,
      pageSize: 100
    })
    version = page.items.find((row) => row.versionNo === selection.versionNo)
    cursor = version || !page.hasMore ? undefined : page.nextCursor
  } while (cursor)
  if (!version) throw new Error('FILE_VERSION_NOT_FOUND')
  evidence.value = buildEvidenceReference(selection, artifact, version, frozenReferenceKey)
}

const captureDetachedEvidence = (detached: DetachedFileSlot) => {
  if (!evidence.value) return
  evidence.value = {
    ...evidence.value,
    referenceKey: detached.referenceKey,
    fileFactVersion: {
      ...evidence.value.fileFactVersion,
      referenceVersion: detached.factVersion
    }
  }
}

const save = async () => {
  if (!preparation.value || !item.value || !baseline.value) return
  saving.value = true
  try {
    const patch: PatchPreparationItemReqVO = {
      expectedPreparationVersion: preparation.value.version,
      expectedInputVersion: preparation.value.inputVersion,
      expectedReadinessVersion: preparation.value.readinessVersion,
      expectedFormVersion: item.value.form.version,
      expectedProjectVersion: props.projectVersion
    }
    if (canManager.value) {
      setChanged(
        patch,
        'applicabilityCode',
        draft.applicabilityCode,
        baseline.value.applicabilityCode
      )
      setChanged(patch, 'outsourced', draft.outsourced, baseline.value.outsourced)
      setChanged(
        patch,
        'assigneeUserId',
        draft.assigneeUserId ?? null,
        baseline.value.assigneeUserId
      )
      setChanged(
        patch,
        'notApplicableReason',
        draft.notApplicableReason || null,
        baseline.value.notApplicableReason
      )
    }
    if (canAssignee.value) {
      setChanged(
        patch,
        'siteResultCode',
        draft.siteResultCode || null,
        baseline.value.siteResultCode
      )
      setChanged(
        patch,
        'siteResultDetail',
        draft.siteResultDetail || null,
        baseline.value.siteResultDetail
      )
      setChanged(
        patch,
        'formValueSnapshot',
        JSON.stringify(formValues),
        JSON.stringify(baseline.value.formValues)
      )
      setChanged(
        patch,
        'evidenceReferences',
        evidence.value ? [evidence.value] : [],
        baseline.value.evidence
      )
    }
    if (Object.keys(patch).length === 5) {
      message.info('没有需要保存的变化')
      return
    }
    await PreparationApi.patchItem(
      preparation.value.preparationId,
      item.value.itemId,
      item.value.version,
      patch
    )
    message.success('工勘项已保存')
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<style scoped lang="scss">
.item-summary,
.item-form,
.fixed-form,
.evidence {
  margin-bottom: 16px;
}

h4 {
  margin: 0 0 12px;
  color: var(--el-text-color-primary);
}

.fixed-form,
.evidence {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

@media (width <= 767px) {
  .fixed-form,
  .evidence {
    padding: 10px;
  }
}
</style>
