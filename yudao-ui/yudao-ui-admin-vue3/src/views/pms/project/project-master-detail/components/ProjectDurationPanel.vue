<template>
  <ContentWrap>
    <div class="panel-heading">
      <div>
        <h3>项目工期</h3>
        <span>唯一当前工期、审批中变更与计划重算影响</span>
      </div>
      <div class="actions">
        <el-button v-if="plan" @click="historyRef?.open(plan.planId)">查看历史</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-skeleton v-if="loading" :rows="5" animated />
    <template v-else-if="plan">
      <el-alert
        v-if="plan.planRecalculationStatus === 'PENDING_RECALCULATION'"
        title="当前工期已生效，施工计划等待 PLN-01 重算；原施工计划继续有效。"
        type="warning"
        :closable="false"
        class="status-alert"
      />
      <div class="summary-grid">
        <div class="summary-item">
          <span>当前版本</span>
          <strong>V{{ plan.currentRevision.revisionNo }}</strong>
        </div>
        <div class="summary-item">
          <span>工期区间</span>
          <strong>{{ plan.currentRevision.startDate }} 至 {{ plan.currentRevision.endDate }}</strong>
        </div>
        <div class="summary-item">
          <span>自然日工期</span>
          <strong>{{ plan.currentRevision.durationDays }} 天</strong>
        </div>
        <div class="summary-item">
          <span>计算口径</span>
          <strong>{{ basisLabel(plan.currentRevision.calculationBasis) }}</strong>
        </div>
      </div>

      <section v-if="draft" class="change-section">
        <div class="section-heading">
          <div>
            <strong>变更草稿 #{{ draft.changeId }}</strong>
            <span>{{ draft.candidateRevision.startDate }} 至 {{ draft.candidateRevision.endDate }}，{{ draft.candidateRevision.durationDays }} 天</span>
          </div>
          <el-tag type="info">草稿</el-tag>
        </div>
        <div class="section-actions" v-hasPermi="['pms:construction-plan:duration-manage']">
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
        <div class="section-actions">
          <el-button
            v-if="plan.pendingChangeSummary.processInstanceId"
            v-hasPermi="['pms:construction-plan:duration-approve']"
            type="primary"
            @click="openBpm(plan.pendingChangeSummary.processInstanceId)"
          >前往平台审批</el-button>
          <el-button
            v-if="canWithdraw"
            v-hasPermi="['pms:construction-plan:duration-manage']"
            :loading="withdrawing"
            @click="withdraw"
          >撤回申请</el-button>
        </div>
      </section>

      <div v-if="!draft && !plan.pendingChangeSummary" class="primary-action">
        <el-button
          v-if="plan.allowedActions.includes('CREATE_CHANGE')"
          type="primary"
          @click="formRef?.openCreate(plan)"
        >发起工期变更</el-button>
      </div>
    </template>
    <el-empty v-else description="尚未录入项目工期">
      <el-button
        type="primary"
        v-hasPermi="['pms:construction-plan:duration-manage']"
        @click="formRef?.openInitial()"
      >录入首次工期</el-button>
    </el-empty>
  </ContentWrap>

  <ProjectDurationFormDrawer ref="formRef" :project="project" @saved="load" />
  <ProjectDurationHistoryDrawer ref="historyRef" />
</template>

<script setup lang="ts">
import { useMessage } from '@/hooks/web/useMessage'
import { useUserStore } from '@/store/modules/user'
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

const props = defineProps<{ project: ProjectMasterVO }>()
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
const canWithdraw = computed(() =>
  plan.value?.pendingChangeSummary?.status === 'PENDING_APPROVAL'
  && plan.value.pendingChangeSummary.applicantUserId === userStore.getUser.id
)

const load = async () => {
  if (!props.project.id) return
  loading.value = true
  try {
    plan.value = await DurationApi.getByProjectId(props.project.id)
    draft.value = undefined
    if (plan.value) {
      const page = await DurationApi.getChanges(plan.value.planId, { pageSize: 20 })
      draft.value = page.items.find((item) => item.status === 'DRAFT')
    }
  } finally { loading.value = false }
}
const submitDraft = async () => {
  if (!plan.value || !draft.value) return
  await message.confirm('提交后草稿将冻结，并进入服务经理审批。是否继续？')
  submitting.value = true
  try {
    await DurationApi.submitChange(
      plan.value.planId, draft.value.changeId, props.project.version || 0,
      draft.value.version, crypto.randomUUID()
    )
    message.success('工期变更已提交审批')
    await load()
  } finally { submitting.value = false }
}
const openBpm = (processInstanceId: string) =>
  router.push({ name: 'BpmProcessInstanceDetail', query: { id: processInstanceId } })
const withdraw = async () => {
  const instanceId = plan.value?.pendingChangeSummary?.processInstanceId
  if (!instanceId) return
  const prompt = await message.prompt('请输入撤回原因', '撤回工期变更')
  withdrawing.value = true
  try {
    await ProcessInstanceApi.cancelProcessInstanceByStartUser(instanceId as unknown as number, prompt.value)
    message.success('工期变更已撤回')
    await load()
  } finally { withdrawing.value = false }
}

watch(() => props.project.id, load, { immediate: true })
</script>

<style scoped lang="scss">
.panel-heading,
.actions,
.section-heading,
.section-actions {
  display: flex;
  align-items: center;
}

.panel-heading {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-heading h3 {
  margin: 0 0 4px;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.panel-heading span,
.section-heading span,
.summary-item span {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.actions,
.section-actions {
  gap: 8px;
}

.status-alert {
  margin-bottom: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.summary-item {
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: var(--el-border-radius-base);
}

.summary-item strong {
  display: block;
  margin-top: 6px;
  color: var(--el-text-color-primary);
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
  justify-content: flex-end;
  margin-top: 12px;
}

.primary-action {
  margin-top: 12px;
  text-align: right;
}

@media (width <= 1023px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 767px) {
  .panel-heading,
  .section-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .actions,
  .section-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .primary-action,
  .primary-action .el-button {
    width: 100%;
  }
}
</style>
