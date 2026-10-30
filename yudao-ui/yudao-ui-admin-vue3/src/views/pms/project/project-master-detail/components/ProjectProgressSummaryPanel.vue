<template>
  <section class="progress-workspace" aria-label="子项目进度汇总工作区">
  <ContentWrap>
    <div class="panel-heading">
      <div>
        <h3>子项目进度汇总</h3>
        <span>按直接子项目的有效权重逐级汇总。</span>
      </div>
      <div class="actions">
        <el-button :disabled="loading || !!error || !children.length" @click="openPolicy" v-hasPermi="['pms:project:progress-policy:update']">调整权重</el-button>
        <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="status-alert" />
    <el-skeleton v-if="loading" :rows="5" animated />
    <el-empty v-else-if="!error && !children.length" description="当前项目没有直接子项目，无需汇总；本项目进度可在项目树中查看。" />
    <template v-else-if="progress">
      <el-alert
        v-if="progress.status === 'PENDING'"
        title="进度待计算：存在缺失或未就绪的直接子项目事实"
        type="warning"
        :closable="false"
        class="status-alert"
      />
      <div class="progress-summary">
        <div class="summary-value"><span>汇总进度</span><strong>{{ progress.status === 'READY' ? formatPercent(progress.progress) : '待计算' }}</strong></div>
        <div class="summary-detail"><span>{{ children.length }} 个直接子项目参与当前汇总</span>
          <el-progress v-if="progress.status === 'READY' && progress.progress != null" :percentage="Number(progress.progress)"
            :show-text="false" :stroke-width="8" :status="Number(progress.progress) === 100 ? 'success' : undefined" />
          <p>{{ progress.status === 'READY' ? '子项目进度 × 归一化权重，再逐项相加' : '缺失进度保留为待计算，不作为 0% 参与汇总' }}</p>
        </div>
      </div>
      <div class="table-scroll desktop-list">
        <el-table :data="progress.items" size="small" border>
          <el-table-column label="子项目" min-width="240">
            <template #default="{ row }">{{ childLabel(row.childProjectId) }}</template>
          </el-table-column>
          <el-table-column label="进度" min-width="130">
            <template #default="{ row }">{{ formatPercent(row.childProgress) }}</template>
          </el-table-column>
          <el-table-column label="归一化权重" min-width="130">
            <template #default="{ row }">{{ formatPercent(row.normalizedWeight) }}</template>
          </el-table-column>
          <el-table-column label="贡献值" min-width="120">
            <template #default="{ row }">{{ formatPercent(row.contribution) }}</template>
          </el-table-column>
          <el-table-column label="待计算原因" min-width="180"><template #default="{ row }">{{ missingLabel(row.missingReason) }}</template></el-table-column>
        </el-table>
      </div>
      <div class="mobile-list">
        <article v-for="item in progress.items" :key="item.childProjectId" class="progress-card">
          <strong>{{ childLabel(item.childProjectId) }}</strong>
          <dl><div><dt>子项目进度</dt><dd>{{ formatPercent(item.childProgress) }}</dd></div>
            <div><dt>归一化权重</dt><dd>{{ formatPercent(item.normalizedWeight) }}</dd></div>
            <div><dt>贡献值</dt><dd>{{ formatPercent(item.contribution) }}</dd></div></dl>
          <el-tag v-if="item.missingReason" type="warning" size="small">{{ missingLabel(item.missingReason) }}</el-tag>
        </article>
      </div>
    </template>
  </ContentWrap>

  <ContentWrap>
    <div class="section-title"><h4>权重策略历史</h4><span>调整经审批通过后生效</span></div>
    <el-table :data="policies" size="small" border empty-text="暂无策略版本">
      <el-table-column prop="revisionNo" label="版本" width="80" />
      <el-table-column label="类型" width="100"><template #default="{ row }">{{ row.policyType === 'SYSTEM_EQUAL' ? '默认等权' : '人工权重' }}</template></el-table-column>
      <el-table-column label="状态" min-width="110"><template #default="{ row }">{{ policyStatusLabels[row.status] || row.status }}</template></el-table-column>
      <el-table-column label="生效时间" min-width="170"><template #default="{ row }">{{ row.effectiveFrom ? formatDate(row.effectiveFrom) : '—' }}</template></el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'DRAFT'"
            link
            type="primary"
            :loading="submittingId === row.id"
            :disabled="submittingId != null"
            @click="submitPolicy(row)"
            v-hasPermi="['pms:project:progress-policy:submit']"
          >提交审批</el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>
  </section>

  <Dialog v-model="policyVisible" title="调整子项目权重" :width="dialogWidth" scroll max-height="65vh">
    <el-alert v-if="policyError" :title="policyError" type="error" :closable="false" />
    <el-form label-position="top" :disabled="saving">
      <el-form-item label="策略类型">
        <el-radio-group v-model="policyType">
          <el-radio-button value="SYSTEM_EQUAL">默认等权</el-radio-button>
          <el-radio-button value="MANUAL">人工权重</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-alert title="人工权重必须完整覆盖全部直接子项目且合计为 100%。" type="info" :closable="false" />
      <div v-for="item in policyItems" :key="item.childProjectId" class="policy-row">
        <span>{{ childLabel(item.childProjectId) }}</span>
        <el-input-number v-model="item.weight" :disabled="saving || policyType === 'SYSTEM_EQUAL'" :min="0" :max="100" :precision="4" :aria-label="`${childLabel(item.childProjectId)}权重`" />
      </div>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="policyVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="createPolicy">保存草稿</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import { formatDate } from '@/utils/formatTime'
import * as ProjectsApi from '@/api/pms/project/projects'
import type { ProjectProgressPolicyVO, ProjectProgressVO, ProjectTreeNodeVO } from '@/api/pms/project/projects'
import { projectLabel } from '@/views/pms/project/inheritance/tree/projectTree'
import { createSubmissionIdempotencyState } from '@/views/pms/project/projects/submissionIdempotency'
import { splitErrorMessage } from './useProjectSplitWorkspace'

const props = defineProps<{ projectId: number; treeVersion?: number; refreshToken?: number }>()
const message = useMessage()
const mobile = useMediaQuery('(max-width: 767px)')
const dialogWidth = computed(() => mobile.value ? '96%' : '720px')
const loading = ref(false)
const saving = ref(false)
const progress = ref<ProjectProgressVO>()
const policies = ref<ProjectProgressPolicyVO[]>([])
const policyVisible = ref(false)
const policyType = ref<'SYSTEM_EQUAL' | 'MANUAL'>('SYSTEM_EQUAL')
const policyItems = ref<{ childProjectId: number; weight: number }[]>([])
const loadedTreeVersion = ref<number>()
const children = ref<ProjectTreeNodeVO[]>([])
const error = ref(''), policyError = ref(''), submittingId = ref<number>()
const policySubmission = createSubmissionIdempotencyState(), approvalSubmission = createSubmissionIdempotencyState()
let generation = 0
const childLabel = (id: number) => {
  const child = children.value.find(child => String(child.projectId) === String(id))
  return child ? projectLabel(child) : '受限子项目'
}
const missingLabels: Record<string, string> = { PROGRESS_FACT_MISSING: '尚未形成项目进度', CHILD_SNAPSHOT_MISSING: '下级汇总尚未形成',
  CHILD_PROGRESS_PENDING: '下级进度待计算', CHILD_NOT_IN_TREE: '项目关系已变化', POLICY_ITEM_MISSING: '当前策略缺少该子项目',
  EXCLUDED_BY_POLICY: '当前汇总口径不包含该状态', POLICY_DENOMINATOR_EMPTY: '没有可参与汇总的权重' }
const missingLabel = (value?: string) => value ? missingLabels[value] || value : '—'
const policyStatusLabels: Record<string, string> = { DRAFT: '草稿', APPROVING: '审批中', ACTIVE: '已生效', REJECTED: '已驳回', SUPERSEDED: '已替代' }

const formatPercent = (value?: number) => value == null ? '—' : `${Number(value).toFixed(2)}%`
const load = async () => {
  const current = ++generation, id = props.projectId
  loading.value = true; error.value = ''; progress.value = undefined; policies.value = []; children.value = []; loadedTreeVersion.value = undefined
  try {
    const nodes: ProjectTreeNodeVO[] = []
    let cursor: string | undefined, version: number | undefined
    do {
      const tree = await ProjectsApi.queryTree(id, { queryType: 'CHILDREN', pageSize: 100, cursor })
      if (current !== generation) return
      if (tree.updating) throw new Error('项目树正在更新，请稍后刷新')
      if (version != null && tree.treeVersion !== version) throw new Error('项目树已变化，请刷新后再调整权重')
      version = tree.treeVersion; cursor = tree.nextCursor; nodes.push(...tree.items)
    } while (cursor)
    const [summary, history] = await Promise.all([
      nodes.length ? ProjectsApi.getProgress(id) : Promise.resolve(undefined),
      ProjectsApi.getProgressPolicies(id)
    ])
    if (current !== generation) return
    if (summary && summary.treeVersion !== version) throw new Error('项目关系已变化，请刷新后查看汇总')
    progress.value = summary
    policies.value = history || []
    children.value = nodes
    loadedTreeVersion.value = version
  } catch (failure) { if (current === generation) error.value = `进度汇总读取失败：${splitErrorMessage(failure)}` }
  finally { if (current === generation) loading.value = false }
}
const openPolicy = () => {
  if (loading.value || error.value || !children.value.length) return
  policySubmission.reset(); policyError.value = ''; policyType.value = 'SYSTEM_EQUAL'
  const weight = Math.floor(1000000 / children.value.length) / 10000
  policyItems.value = children.value.map((child, index) => ({ childProjectId: child.projectId,
    weight: index === children.value.length - 1 ? Number((100 - weight * index).toFixed(4)) : weight }))
  policyVisible.value = true
}
const createPolicy = async () => {
  if (saving.value) return
  const treeVersion = loadedTreeVersion.value
  if (!treeVersion) return message.warning('请先形成项目树版本')
  const total = policyItems.value.reduce((sum, item) => sum + item.weight, 0)
  if (policyType.value === 'MANUAL' && (!Number.isFinite(total) || Number(total.toFixed(4)) !== 100)) return message.warning('人工权重合计必须为 100%')
  saving.value = true; policyError.value = ''
  const current = generation, id = props.projectId
  const data = { policyType: policyType.value, items: policyType.value === 'SYSTEM_EQUAL' ? [] : policyItems.value }
  try {
    await ProjectsApi.createProgressPolicy(id, data, policySubmission.keyFor({ id, data, treeVersion }), treeVersion)
    if (current !== generation) return
    message.success('策略草稿已保存')
    policyVisible.value = false
    await load()
  } catch (failure) { if (current === generation) policyError.value = splitErrorMessage(failure) }
  finally { saving.value = false }
}
const submitPolicy = async (policy: ProjectProgressPolicyVO) => {
  if (submittingId.value != null) return
  submittingId.value = policy.id; error.value = ''
  const current = generation
  try {
    await ProjectsApi.submitProgressPolicy(policy.id, policy.version, approvalSubmission.keyFor({ id: policy.id, version: policy.version }))
    if (current !== generation) return
    message.success('策略已提交审批')
    await load()
  } catch (failure) { if (current === generation) error.value = splitErrorMessage(failure) }
  finally { submittingId.value = undefined }
}
watch(() => [props.projectId, props.refreshToken], () => { policyVisible.value = false; void load() }, { immediate: true })
onBeforeUnmount(() => { generation++ })
</script>

<style scoped lang="scss">
.progress-workspace { container-type: inline-size; font-family: "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", sans-serif; }
.panel-heading, .actions, .progress-summary, .policy-row { display: flex; align-items: center; }
.panel-heading { justify-content: space-between; flex-wrap: wrap; gap: 14px; margin-bottom: 22px; }
.panel-heading h3 { margin: 0 0 7px; font-size: 20px; line-height: 1.3; font-weight: 650; color: var(--el-text-color-primary); }
.panel-heading span, .progress-summary span { display: block; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.7; }
.actions { gap: 8px; flex: none; }
.actions :deep(.el-button + .el-button) { margin-left: 0; }
.status-alert { margin-bottom: 12px; }
.progress-summary { gap: 28px; padding: 20px 18px; background: var(--el-fill-color-lighter); border-left: 3px solid var(--el-color-primary); margin-bottom: 22px; }
.summary-value { flex: none; min-width: 108px; }
.summary-value strong { display: block; margin-top: 5px; font-size: 30px; font-weight: 600; line-height: 1.3; color: var(--el-text-color-primary); font-variant-numeric: tabular-nums; }
.summary-detail { flex: 1; min-width: 0; }
.summary-detail :deep(.el-progress) { margin-top: 10px; }
.summary-detail p { margin: 8px 0 0; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.7; }
.table-scroll { overflow-x: auto; max-width: 100%; }
.section-title { display: flex; align-items: baseline; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; color: var(--el-text-color-primary); }
.section-title h4 { margin: 0; font-size: 15px; font-weight: 600; }
.section-title span { color: var(--el-text-color-secondary); font-size: 12px; }
.policy-row { justify-content: space-between; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.mobile-list { display: none; }
.progress-card { padding: 16px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.progress-card:first-child { padding-top: 0; }
.progress-card:last-child { border-bottom: 0; padding-bottom: 0; }
.progress-card > strong { display: block; margin-bottom: 14px; font-size: 13px; font-weight: 500; line-height: 1.7; overflow-wrap: anywhere; }
.progress-card dl { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin: 0; }
.progress-card dt { color: var(--el-text-color-secondary); font-size: 12px; margin-bottom: 5px; }
.progress-card dd { margin: 0; font-size: 16px; line-height: 1.4; font-variant-numeric: tabular-nums; }
.progress-card :deep(.el-tag) { margin-top: 12px; }
@container (max-width: 760px) {
  .desktop-list { display: none; }
  .mobile-list { display: block; }
}
@container (max-width: 440px) {
  .actions { margin-left: auto; }
  .progress-summary { gap: 16px; padding: 16px 12px; align-items: flex-start; }
  .summary-value { min-width: 80px; }
  .summary-value strong { font-size: 24px; }
}
@media (max-width: 767px) {
  .policy-row { align-items: stretch; flex-direction: column; }
}
</style>
