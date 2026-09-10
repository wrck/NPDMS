<template>
  <ContentWrap>
    <el-form inline class="-mb-15px duration-query">
      <el-form-item label="项目">
        <el-input :model-value="project.projectName || `项目 #${project.id}`" disabled class="!w-220px" data-testid="duration-project-name" />
      </el-form-item>
      <el-form-item>
        <el-button :loading="loading" @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button v-if="plan" @click="historyRef?.open(plan.planId)">查看历史</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-alert v-if="project.projectEndDate" type="info" :closable="false" class="status-alert"
      :title="`工勘要求的项目结束日期：${project.projectEndDate}；录入工期时据此倒排开始日期。`" />
    <el-alert v-if="project.projectEndDate && plan && plan.currentRevision.endDate !== project.projectEndDate"
      type="warning" :closable="false" class="status-alert"
      title="工勘结束日期与当前生效工期不同，请新建工期变更重新倒排；原生效版本及审批记录保留。" />
    <el-alert
      v-if="!validProject"
      title="项目上下文无效，未查询工期。"
      type="warning"
      :closable="false"
    />
    <el-alert v-else-if="errorText" :title="errorText" type="error" :closable="false" />
    <el-skeleton v-else-if="loading" :rows="5" animated />
    <template v-else-if="plan">
      <el-alert
        v-if="plan.planRecalculationStatus === 'PENDING_RECALCULATION'"
        title="当前工期已生效，阶段施工计划尚待重算；已有计划版本不会被本次工期录入覆盖。"
        type="warning"
        :closable="false"
        class="status-alert"
      />
      <el-table :data="[plan.currentRevision]" data-testid="duration-current-revision">
        <el-table-column prop="revisionNo" label="当前版本" width="100">
          <template #default="{ row }">V{{ row.revisionNo }}</template>
        </el-table-column>
        <el-table-column prop="startDate" label="计划开始" min-width="140" />
        <el-table-column prop="endDate" label="计划结束" min-width="140" />
        <el-table-column prop="durationDays" label="自然日工期" width="120">
          <template #default="{ row }">{{ row.durationDays }} 天</template>
        </el-table-column>
        <el-table-column prop="calculationBasis" label="计算口径" min-width="160">
          <template #default="{ row }">{{ basisLabel(row.calculationBasis) }}</template>
        </el-table-column>
      </el-table>

      <section v-if="draft" class="change-section">
        <div class="section-heading">
          <div>
            <strong>变更草稿 #{{ draft.changeId }}</strong>
            <span
              >{{ draft.candidateRevision.startDate }} 至 {{ draft.candidateRevision.endDate }}，{{
                draft.candidateRevision.durationDays
              }}
              天</span
            >
          </div>
          <el-tag type="info">草稿</el-tag>
        </div>
        <PmsFileReferenceList
          v-if="draft.customerEvidenceFileId && draft.customerEvidenceReferenceKey"
          owner-context="SOL"
          object-type="CONSTRUCTION_PLAN_CHANGE"
          :object-id="String(draft.changeId)"
          purpose-code="CUSTOMER_DELAY_EVIDENCE"
          :reference-key="draft.customerEvidenceReferenceKey"
          :artifact-id="draft.customerEvidenceFileId"
          :version-no="draft.customerEvidenceFileVersion"
          class="evidence-summary"
        />
        <div
          v-if="canWrite"
          class="section-actions"
          v-hasPermi="['pms:construction-plan:duration-manage']"
        >
          <el-button @click="formRef?.openEdit(plan, draft)">编辑草稿</el-button>
          <el-button type="primary" :loading="submitting" @click="submitDraft">提交审批</el-button>
        </div>
      </section>

      <section v-if="plan.pendingChangeSummary" class="change-section pending-section">
        <div class="section-heading">
          <div>
            <strong>审批中变更 #{{ plan.pendingChangeSummary.changeId }}</strong>
            <span>
              候选 V{{ plan.pendingChangeSummary.candidateRevision.revisionNo }} ·
              {{ plan.pendingChangeSummary.candidateRevision.durationDays }} 天
            </span>
          </div>
          <el-tag type="warning">待服务经理审批</el-tag>
        </div>
        <PmsFileReferenceList
          v-if="
            plan.pendingChangeSummary.customerEvidenceFileId &&
            plan.pendingChangeSummary.customerEvidenceReferenceKey
          "
          owner-context="SOL"
          object-type="CONSTRUCTION_PLAN_CHANGE"
          :object-id="String(plan.pendingChangeSummary.changeId)"
          purpose-code="CUSTOMER_DELAY_EVIDENCE"
          :reference-key="plan.pendingChangeSummary.customerEvidenceReferenceKey"
          :artifact-id="plan.pendingChangeSummary.customerEvidenceFileId"
          :version-no="plan.pendingChangeSummary.customerEvidenceFileVersion"
          class="evidence-summary"
        />
        <div class="section-actions">
          <el-button
            v-if="canWrite && plan.pendingChangeSummary.processInstanceId"
            v-hasPermi="['pms:construction-plan:duration-approve']"
            type="primary"
            @click="openBpm(plan.pendingChangeSummary.processInstanceId)"
            >前往平台审批</el-button
          >
          <el-button
            v-if="canWrite && canWithdraw"
            v-hasPermi="['pms:construction-plan:duration-manage']"
            :loading="withdrawing"
            @click="withdraw"
            >撤回申请</el-button
          >
        </div>
      </section>

      <div v-if="!draft && !plan.pendingChangeSummary" class="primary-action">
        <el-button
          v-if="canWrite && plan.allowedActions.includes('CREATE_CHANGE')"
          type="primary"
          @click="formRef?.openCreate(plan)"
          >发起工期变更</el-button
        >
      </div>
    </template>
    <el-empty v-else description="尚未录入项目工期">
      <el-button
        v-if="canWrite"
        type="primary"
        v-hasPermi="['pms:construction-plan:duration-manage']"
        @click="formRef?.openInitial()"
        >录入首次工期</el-button
      >
    </el-empty>
  </ContentWrap>

  <ProjectDurationFormDrawer
    ref="formRef"
    :project="project"
    :readonly="props.readonly"
    @saved="onSaved"
    @dirty-change="drawerDirty = $event"
  />
  <ProjectDurationHistoryDrawer :key="project.id" ref="historyRef" />
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from '@/hooks/web/useMessage'
import { useUserStore } from '@/store/modules/user'
import { PmsFileReferenceList } from '@/components/PmsFileArtifact'
import * as ProcessInstanceApi from '@/api/bpm/processInstance'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import * as DurationApi from '@/api/pms/engineering/construction-plan'
import type {
  ConstructionPlanChangeVO,
  ConstructionPlanVO,
  DurationCalculationBasis
} from '@/api/pms/engineering/construction-plan'
import ProjectDurationFormDrawer from './ProjectDurationFormDrawer.vue'
import ProjectDurationHistoryDrawer from './ProjectDurationHistoryDrawer.vue'

const props = defineProps<{ project: ProjectMasterVO; readonly?: boolean }>()
const emit = defineEmits<{ 'dirty-change': [value: boolean]; changed: [] }>()
const validProject = computed(() => Number.isSafeInteger(props.project.id) && props.project.id! > 0)
const canWrite = computed(() => validProject.value && !props.readonly)
const drawerDirty = ref(false)
const errorText = ref('')
let loadSequence = 0
let contextVersion = 0
const router = useRouter()
const message = useMessage()
const userStore = useUserStore()
const loading = ref(false)
const submitting = ref(false)
const withdrawing = ref(false)
const plan = ref<ConstructionPlanVO | null>(null)
const draft = ref<ConstructionPlanChangeVO>()
const formRef = ref<InstanceType<typeof ProjectDurationFormDrawer>>()
const historyRef = ref<InstanceType<typeof ProjectDurationHistoryDrawer>>()

const basisLabel = (value: DurationCalculationBasis) =>
  value === 'DATE_RANGE' ? '起止日期' : '起点 + 天数'
const canWithdraw = computed(
  () =>
    plan.value?.pendingChangeSummary?.status === 'PENDING_APPROVAL' &&
    plan.value.pendingChangeSummary.applicantUserId === userStore.getUser.id
)

const load = async () => {
  const sequence = ++loadSequence
  const projectId = props.project.id!
  plan.value = null
  draft.value = undefined
  errorText.value = ''
  if (!validProject.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const current = await DurationApi.getByProjectId(projectId)
    if (sequence !== loadSequence) return
    let currentDraft: ConstructionPlanChangeVO | undefined
    if (current) {
      const page = await DurationApi.getChanges(current.planId, { pageSize: 20 })
      if (sequence !== loadSequence) return
      const draftSummary = page.items.find((item) => item.status === 'DRAFT')
      currentDraft = draftSummary
        ? await DurationApi.getChange(current.planId, draftSummary.changeId)
        : undefined
    }
    if (sequence !== loadSequence) return
    plan.value = current
    draft.value = currentDraft
  } catch {
    if (sequence === loadSequence) errorText.value = '项目工期加载失败，请刷新重试。'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}
const submitDraft = async () => {
  if (!canWrite.value || !plan.value || !draft.value) return
  if (draft.value.customerEvidenceRequired && !draft.value.customerEvidenceFileId) {
    return message.warning('请先在编辑草稿中上传客户延期依据')
  }
  const version = contextVersion
  const currentPlan = plan.value
  const currentDraft = draft.value
  const projectVersion = props.project.version || 0
  await message.confirm('提交后草稿将冻结，并进入服务经理审批。是否继续？')
  if (!canWrite.value || version !== contextVersion) return
  submitting.value = true
  try {
    await DurationApi.submitChange(
      currentPlan.planId,
      currentDraft.changeId,
      projectVersion,
      currentDraft.version,
      crypto.randomUUID()
    )
    if (version !== contextVersion) return
    emit('changed')
    message.success('工期变更已提交审批')
    await load()
  } finally {
    submitting.value = false
  }
}
const openBpm = (processInstanceId: string) => {
  if (!canWrite.value) return
  return router.push({ name: 'BpmProcessInstanceDetail', query: { id: processInstanceId } })
}
const withdraw = async () => {
  const instanceId = plan.value?.pendingChangeSummary?.processInstanceId
  if (!canWrite.value || !instanceId) return
  const version = contextVersion
  const prompt = await message.prompt('请输入撤回原因', '撤回工期变更')
  if (!canWrite.value || version !== contextVersion) return
  withdrawing.value = true
  try {
    await ProcessInstanceApi.cancelProcessInstanceByStartUser(
      instanceId as unknown as number,
      prompt.value
    )
    if (version !== contextVersion) return
    emit('changed')
    message.success('工期变更已撤回')
    await load()
  } finally {
    withdrawing.value = false
  }
}

const onSaved = () => {
  emit('changed')
  void load()
}
const dirty = computed(() => drawerDirty.value || submitting.value || withdrawing.value)
watch(dirty, (value) => emit('dirty-change', value), { immediate: true })
watch(
  () => props.project.id,
  () => {
    contextVersion++
    void load()
  },
  { immediate: true, flush: 'sync' }
)
watch(
  () => props.readonly,
  () => {
    contextVersion++
  },
  { flush: 'sync' }
)
onBeforeUnmount(() => {
  contextVersion++
  loadSequence++
})
defineExpose({
  isDirty: () => dirty.value,
  discardChanges: () => {
    if (submitting.value || withdrawing.value) return false
    return formRef.value?.discardChanges() !== false
  }
})
</script>

<style scoped lang="scss">
.section-heading,
.section-actions {
  display: flex;
  align-items: center;
}

.evidence-summary {
  margin-top: 12px;
}

.section-heading span {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.section-actions {
  gap: 8px;
}

.status-alert {
  margin-bottom: 12px;
}

.change-section {
  padding: 12px;
  margin-top: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.pending-section {
  border-color: var(--el-color-warning-light-5);
}

.section-heading {
  justify-content: space-between;
  gap: 12px;
}

.section-actions {
  justify-content: flex-start;
  margin-top: 12px;
}

.primary-action {
  margin-top: 12px;
  text-align: left;
}

@media (width <= 1023px) {
  .section-actions {
    flex-wrap: wrap;
  }
}

@media (width <= 767px) {
  .section-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .section-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .duration-query :deep(.el-form-item),
  .duration-query :deep(.el-form-item__content) {
    width: 100%;
  }

  .primary-action,
  .primary-action .el-button {
    width: 100%;
  }
}
</style>
