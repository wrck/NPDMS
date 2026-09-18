<template>
  <ContentWrap>
    <div class="panel-header">
      <span class="panel-title"><Icon icon="ep:document-copy" /> 需求分析</span>
      <div class="panel-header-actions">
        <el-button
          v-if="canCreateInitial"
          type="primary"
          plain
          :loading="commandLoading"
          data-testid="requirement-create-btn"
          @click="createInitial"
        >
          <Icon icon="ep:plus" class="mr-3px" /> 新增
        </el-button>
      </div>
    </div>
    <el-alert v-if="errorText" :title="errorText" type="error" show-icon :closable="false" class="mb-10px">
      <template #default><el-button link type="primary" @click="load">重新加载</el-button></template>
    </el-alert>
    <el-table v-else v-loading="loading" :data="rows" empty-text="当前项目尚未创建需求分析">
      <el-table-column prop="revision.revisionNo" label="业务版本" width="110">
        <template #default="{ row }">V{{ row.revision.revisionNo }}</template>
      </el-table-column>
      <el-table-column label="版本类型" min-width="160">
        <template #default="{ row }">{{ row.revision.state === 'DRAFT' ? '当前草稿' : '当前有效完成版' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ row.revision.state === 'DRAFT' ? '草稿' : '已完成' }}</template>
      </el-table-column>
      <el-table-column label="完成时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.revision.frozenAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)">
            {{ row.revision.state === 'DRAFT' ? '编辑' : '查看' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-alert v-if="commandError" :title="commandError" type="warning" show-icon closable class="mt-10px" @close="commandError = ''" />
  </ContentWrap>

  <!-- 新需求分析动态表单（EntityForm），新增/编辑草稿共用 -->
  <el-dialog
    v-model="formVisible"
    :title="formDetail?.revision.state === 'DRAFT' ? `需求分析 V${formDetail.revision.revisionNo}（草稿）` : `需求分析 V${formDetail?.revision.revisionNo ?? ''}`"
    width="80%"
    top="5vh"
    append-to-body
    :close-on-click-modal="false"
    :before-close="guardClose"
  >
    <el-skeleton v-if="formLoading" :rows="8" animated />
    <template v-else-if="formDetail">
      <EntityForm
        ref="formRef"
        :detail="formDetail"
        :reload="reloadDetail"
        @dirty-change="formDirty = $event"
        @saved="onFormSaved"
      />
      <el-button
        v-if="canComplete"
        type="success"
        class="mt-15px"
        :loading="commandLoading"
        @click="completeDraft"
      >
        完成并冻结当前草稿
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import * as RequirementAnalysisApi from '@/api/pms/engineering/requirement-analysis/entity'
import type { View, Workspace } from '@/api/pms/engineering/requirement-analysis/entity'
import { formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { stableCommandIntent } from '@/views/pms/platform/dynamic-form/components/dynamicFormRuntime'
import EntityForm from '@/views/pms/delivery-business/requirement-analysis/entity/EntityForm.vue'

// 业务中心「需求分析」面板：列表接新需求分析工作区（draft/有效版），新增/编辑接 EntityForm 动态表单；
// 与 delivery-business EntityPanel 消费同一批 API（projectId 语义一致，不校验项目链）。
defineOptions({ name: 'BusinessRequirementPanel' })

const props = defineProps<{ project: { id?: number; version?: number; projectName?: string } }>()

const message = useMessage()
const loading = ref(false)
const commandLoading = ref(false)
const errorText = ref('')
const commandError = ref('')
const overview = ref<Workspace>()

const rows = computed(() =>
  [overview.value?.draft, overview.value?.currentEffective].filter((item): item is View => !!item)
)
const canCreateInitial = computed(
  () =>
    !overview.value?.draft &&
    !overview.value?.currentEffective &&
    (overview.value?.allowedActions || []).some((action) =>
      ['CREATE_INITIAL_DRAFT', 'CREATE_DRAFT'].includes(action)
    )
)
const canComplete = computed(
  () =>
    formDetail.value?.revision.state === 'DRAFT' &&
    ['COMPLETE', 'SUBMIT'].some((action) => formDetail.value!.allowedActions.includes(action))
)

const formatDateTime = (value?: string) => (value ? formatDate(value) : '-')
const commandErrorText = (error: any) => {
  const code = error?.data?.code || error?.code || error?.message
  return code ? `操作未完成：${String(code)}` : '操作未完成，请刷新权威事实后重试。'
}

const load = async () => {
  if (!props.project.id) return
  loading.value = true
  errorText.value = ''
  try {
    overview.value = await RequirementAnalysisApi.workspace(props.project.id)
  } catch {
    overview.value = undefined
    errorText.value = '需求分析工作区加载失败，请检查项目范围或稍后重试。'
  } finally {
    loading.value = false
  }
}
load()

// --- 表单弹窗（EntityForm 需要完整 View + reload 回读） ---
const formVisible = ref(false)
const formLoading = ref(false)
const formDirty = ref(false)
const formDetail = ref<View>()
const formRef = ref<InstanceType<typeof EntityForm>>()

const readDetail = async (revisionId: RequirementAnalysisApi.EntityId) => {
  const value = await RequirementAnalysisApi.read(revisionId)
  if (String(value.projectId) !== String(props.project.id)) throw new Error('需求分析版本不属于当前项目')
  return value
}
const reloadDetail = () => readDetail(formDetail.value!.revision.ref.revisionId)

const openForm = async (view: View) => {
  formVisible.value = true
  formLoading.value = true
  formDirty.value = false
  try {
    // workspace 响应已含草稿/有效版完整内容，仅历史版本需单独 read
    formDetail.value = await readDetail(view.revision.ref.revisionId)
  } catch {
    formDetail.value = undefined
    message.error('需求分析版本加载失败，请刷新后重试。')
  } finally {
    formLoading.value = false
  }
}

const guardClose = async (done: () => void) => {
  if (!formDirty.value || formRef.value?.isSaving()) {
    done()
    return
  }
  try {
    await message.confirm('当前表单尚未保存，是否放弃这些本地修改并关闭？')
    formRef.value?.discardChanges()
    done()
  } catch {
    /* 保留弹窗继续编辑 */
  }
}

const createInitial = async () => {
  if (!props.project.id) return
  const payload = { projectId: props.project.id, projectVersion: props.project.version || 0 }
  const intent = stableCommandIntent('requirement-create', payload)
  commandLoading.value = true
  commandError.value = ''
  try {
    await RequirementAnalysisApi.create(payload.projectId, intent.key)
    intent.clear()
    message.success('需求分析草稿已创建')
    await load()
    if (overview.value?.draft) openForm(overview.value.draft)
  } catch (error) {
    commandError.value = commandErrorText(error)
  } finally {
    commandLoading.value = false
  }
}

const onFormSaved = async () => {
  await load()
  if (formDetail.value) {
    // 回读最新草稿，刷新列表行的同时保持弹窗上下文
    formDetail.value = await readDetail(formDetail.value.revision.ref.revisionId).catch(() => formDetail.value)
  }
}

const completeDraft = async () => {
  if (!formDetail.value) return
  if (formRef.value?.isSaving()) return
  if (formDirty.value) {
    try {
      await message.confirm('当前表单尚未保存，完成后未保存内容将丢失，是否继续？')
    } catch {
      return
    }
  }
  try {
    await message.confirm('完成后正文与附件将永久冻结，是否继续？')
  } catch {
    return
  }
  const revision = formDetail.value.revision
  const intent = stableCommandIntent('requirement-complete', { revision })
  commandLoading.value = true
  commandError.value = ''
  try {
    await RequirementAnalysisApi.complete(revision, intent.key)
    intent.clear()
    message.success('需求分析已完成并冻结为当前有效版本')
    formVisible.value = false
    await load()
  } catch (error) {
    commandError.value = commandErrorText(error)
  } finally {
    commandLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.panel-header {
  display: flex;
  padding-bottom: 8px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  align-items: center;
  justify-content: space-between;
}

.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.panel-header-actions {
  display: inline-flex;
  gap: 8px;
}
</style>
