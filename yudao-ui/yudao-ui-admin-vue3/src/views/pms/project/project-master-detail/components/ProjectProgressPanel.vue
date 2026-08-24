<template>
  <ContentWrap>
    <div class="panel-heading">
      <div>
        <h3>版本化进度汇总</h3>
        <span>树版本 v{{ progress?.treeVersion || '—' }} · 策略 #{{ progress?.policyRevisionId || '—' }}</span>
      </div>
      <div class="actions">
        <el-button @click="policyVisible = true" v-hasPermi="['pms:project:progress-policy:update']">新建策略</el-button>
        <el-button type="primary" @click="load">刷新</el-button>
      </div>
    </div>
    <el-skeleton v-if="loading" :rows="5" animated />
    <template v-else-if="progress">
      <el-alert
        v-if="progress.status === 'PENDING'"
        title="进度待计算：存在缺失或未就绪的直接子项目事实"
        type="warning"
        :closable="false"
        class="status-alert"
      />
      <div class="progress-summary">
        <el-progress
          type="dashboard"
          :percentage="Number(progress.progress || 0)"
          :status="progress.status === 'READY' ? 'success' : 'warning'"
        />
        <div>
          <strong>{{ progress.status === 'READY' ? '汇总就绪' : '待计算' }}</strong>
          <span>来源水位：{{ progress.sourceWatermark || '尚未形成' }}</span>
        </div>
      </div>
      <div class="table-scroll desktop-list">
        <el-table :data="progress.items" size="small" border>
          <el-table-column prop="childProjectId" label="子项目ID" min-width="120" />
          <el-table-column label="进度" min-width="130">
            <template #default="{ row }">{{ formatPercent(row.childProgress) }}</template>
          </el-table-column>
          <el-table-column label="归一化权重" min-width="130">
            <template #default="{ row }">{{ formatPercent(row.normalizedWeight) }}</template>
          </el-table-column>
          <el-table-column label="贡献值" min-width="120">
            <template #default="{ row }">{{ formatPercent(row.contribution) }}</template>
          </el-table-column>
          <el-table-column prop="missingReason" label="待计算原因" min-width="180" />
        </el-table>
      </div>
      <div class="mobile-list">
        <article v-for="item in progress.items" :key="item.childProjectId" class="progress-card">
          <strong>项目 #{{ item.childProjectId }}</strong>
          <span>进度 {{ formatPercent(item.childProgress) }}</span>
          <span>权重 {{ formatPercent(item.normalizedWeight) }}</span>
          <el-tag v-if="item.missingReason" type="warning" size="small">{{ item.missingReason }}</el-tag>
        </article>
      </div>
    </template>
    <el-empty v-else description="尚未形成进度快照" />
  </ContentWrap>

  <ContentWrap>
    <div class="section-title">策略历史</div>
    <el-table :data="policies" size="small" border empty-text="暂无策略版本">
      <el-table-column prop="revisionNo" label="版本" width="80" />
      <el-table-column prop="policyType" label="类型" width="100" />
      <el-table-column prop="status" label="状态" min-width="110" />
      <el-table-column prop="effectiveFrom" label="生效时间" min-width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'DRAFT'"
            link
            type="primary"
            @click="submitPolicy(row)"
            v-hasPermi="['pms:project:progress-policy:submit']"
          >提交审批</el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <Dialog v-model="policyVisible" title="新建进度策略" :width="dialogWidth" scroll max-height="65vh">
    <el-form label-position="top">
      <el-form-item label="策略类型">
        <el-radio-group v-model="policyType">
          <el-radio-button value="SYSTEM_EQUAL">默认等权</el-radio-button>
          <el-radio-button value="MANUAL">人工权重</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-alert title="人工权重必须完整覆盖全部直接子项目且合计为 100%。" type="info" :closable="false" />
      <div v-for="item in policyItems" :key="item.childProjectId" class="policy-row">
        <span>子项目 #{{ item.childProjectId }}</span>
        <el-input-number v-model="item.weight" :disabled="policyType === 'SYSTEM_EQUAL'" :min="0" :max="100" :precision="4" />
      </div>
    </el-form>
    <template #footer>
      <el-button @click="policyVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="createPolicy">保存草稿</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import * as ProjectsApi from '@/api/pms/project/projects'
import type { ProjectProgressPolicyVO, ProjectProgressVO } from '@/api/pms/project/projects'

const props = defineProps<{ projectId: number; treeVersion?: number }>()
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

const formatPercent = (value?: number) => value == null ? '—' : `${Number(value).toFixed(2)}%`
const load = async () => {
  loading.value = true
  try {
    const [current, history, tree] = await Promise.all([
      ProjectsApi.getProgress(props.projectId).catch(() => undefined),
      ProjectsApi.getProgressPolicies(props.projectId),
      ProjectsApi.queryTree(props.projectId, { queryType: 'CHILDREN', pageSize: 500 })
    ])
    progress.value = current
    policies.value = history || []
    loadedTreeVersion.value = tree.treeVersion
    const children = current?.items?.length
      ? current.items
      : tree.items.map((item) => ({ childProjectId: item.projectId }))
    const equal = children.length ? 100 / children.length : 0
    policyItems.value = children.map((item) => ({ childProjectId: item.childProjectId, weight: equal }))
  } finally { loading.value = false }
}
const createPolicy = async () => {
  const treeVersion = progress.value?.treeVersion || loadedTreeVersion.value || props.treeVersion
  if (!treeVersion) return message.warning('请先形成项目树版本')
  const total = policyItems.value.reduce((sum, item) => sum + item.weight, 0)
  if (policyType.value === 'MANUAL' && Math.abs(total - 100) > 0.001) return message.warning('人工权重合计必须为 100%')
  saving.value = true
  try {
    await ProjectsApi.createProgressPolicy(props.projectId, {
      policyType: policyType.value,
      items: policyType.value === 'SYSTEM_EQUAL' ? [] : policyItems.value
    }, crypto.randomUUID(), treeVersion)
    message.success('策略草稿已保存')
    policyVisible.value = false
    await load()
  } finally { saving.value = false }
}
const submitPolicy = async (policy: ProjectProgressPolicyVO) => {
  await ProjectsApi.submitProgressPolicy(policy.id, policy.version, crypto.randomUUID())
  message.success('策略已提交审批')
  await load()
}
watch(() => props.projectId, load, { immediate: true })
</script>

<style scoped lang="scss">
.panel-heading, .actions, .progress-summary, .policy-row { display: flex; align-items: center; }
.panel-heading { justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.panel-heading h3 { margin: 0 0 4px; font-size: 15px; color: var(--el-text-color-primary); }
.panel-heading span, .progress-summary span, .progress-card span { display: block; color: var(--el-text-color-secondary); font-size: 12px; }
.actions { gap: 8px; }
.status-alert { margin-bottom: 12px; }
.progress-summary { gap: 20px; margin-bottom: 16px; }
.progress-summary strong { display: block; margin-bottom: 6px; }
.table-scroll { overflow-x: auto; max-width: 100%; }
.section-title { margin-bottom: 12px; font-weight: 600; color: var(--el-text-color-primary); }
.policy-row { justify-content: space-between; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.mobile-list { display: none; }
@media (max-width: 767px) {
  .panel-heading { flex-direction: column; align-items: stretch; }
  .actions { display: grid; grid-template-columns: 1fr 1fr; }
  .desktop-list { display: none; }
  .mobile-list { display: grid; gap: 8px; }
  .progress-card { display: grid; gap: 6px; padding: 12px; border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); }
  .progress-card .el-tag { justify-self: start; }
  .policy-row { align-items: stretch; flex-direction: column; }
}
</style>
