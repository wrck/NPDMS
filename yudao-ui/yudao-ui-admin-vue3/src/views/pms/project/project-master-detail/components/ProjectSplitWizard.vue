<template>
  <ContentWrap>
    <div class="panel-heading">
      <div>
        <h3>项目拆分方案</h3>
        <span v-if="draft">草稿 #{{ draft.id }} · v{{ draft.draftVersion }} · {{ draft.status }}</span>
        <span v-else>按订单行、数量、办事处和序列号自由组合</span>
      </div>
      <div class="actions">
        <el-button @click="addItem">新增子项目</el-button>
        <el-button type="primary" :loading="saving" @click="saveDraft">保存草稿</el-button>
      </div>
    </div>
    <el-steps :active="step" finish-status="success" simple class="steps">
      <el-step title="编辑范围" />
      <el-step title="服务端预览" />
      <el-step title="确认应用" />
    </el-steps>
    <el-alert
      v-if="draft?.id"
      :title="`刷新后可按 requestId=${draft.id} 恢复草稿`"
      type="info"
      :closable="false"
      class="info-alert"
    />

    <div v-if="items.length" class="split-list">
      <article v-for="(item, itemIndex) in items" :key="item.clientItemKey" class="split-card">
        <header>
          <strong>子项目 {{ itemIndex + 1 }}</strong>
          <el-button link type="danger" @click="removeItem(itemIndex)">移除</el-button>
        </header>
        <el-form label-position="top" class="item-form">
          <el-form-item label="项目名称" required>
            <el-input v-model="item.projectName" maxlength="255" />
          </el-form-item>
          <el-form-item label="业务层级编码">
            <el-input v-model="item.businessLevelCode" maxlength="64" />
          </el-form-item>
          <el-form-item label="办事处编码">
            <el-input v-model="item.officeDepartmentCode" maxlength="64" />
          </el-form-item>
        </el-form>
        <div v-for="(scope, scopeIndex) in item.scopes" :key="scopeIndex" class="scope-row">
          <el-input-number v-model="scope.orderLineId" :min="1" placeholder="订单行ID" aria-label="订单行ID" />
          <el-input-number v-model="scope.quantity" :min="0.0001" :precision="4" placeholder="数量" aria-label="拆分数量" />
          <el-input v-model="scope.officeDepartmentCode" placeholder="范围办事处编码" aria-label="范围办事处编码" />
          <el-input v-model="serialInputs[item.clientItemKey][scopeIndex]" placeholder="序列号，逗号分隔" aria-label="序列号列表" />
          <el-button link type="danger" @click="removeScope(itemIndex, scopeIndex)">删除范围</el-button>
        </div>
        <el-button link type="primary" @click="addScope(itemIndex)">新增组合范围</el-button>
        <el-alert
          v-for="error in itemErrors(item.clientItemKey)"
          :key="error"
          :title="error"
          type="error"
          :closable="false"
          class="item-error"
        />
      </article>
    </div>
    <el-empty v-else description="请新增至少一个子项目" />

    <div class="footer-actions">
      <el-button :disabled="!draft" :loading="previewing" @click="previewDraft">生成预览</el-button>
      <el-button :disabled="!preview" :loading="previewing" @click="validateAgain">重新校验</el-button>
      <el-button type="primary" :disabled="!preview?.valid" :loading="applying" @click="applyDraft">确认原子应用</el-button>
    </div>
    <el-result
      v-if="preview"
      :icon="preview.valid ? 'success' : 'error'"
      :title="preview.valid ? '拆分方案校验通过' : '拆分方案需要修正'"
      :sub-title="preview.valid ? `预览哈希 ${preview.previewHash || '—'}` : preview.errors.join('；')"
    />
  </ContentWrap>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import * as SplitApi from '@/api/pms/project/project-splits'
import type { ProjectSplitDraftInput, ProjectSplitDraftVO, ProjectSplitItemInput, ProjectSplitPreviewVO } from '@/api/pms/project/project-splits'

const props = defineProps<{ projectId: number; templateRevisionId?: number }>()
const emit = defineEmits<{ applied: [] }>()
const message = useMessage()
const items = ref<ProjectSplitItemInput[]>([])
const serialInputs = reactive<Record<string, string[]>>({})
const draft = ref<ProjectSplitDraftVO>()
const preview = ref<ProjectSplitPreviewVO>()
const step = ref(0)
const saving = ref(false)
const previewing = ref(false)
const applying = ref(false)
const storageKey = () => `fproj002:split-request:${props.projectId}`
const key = () => crypto.randomUUID()

const newScope = () => ({ orderLineId: undefined as unknown as number, quantity: 1, officeDepartmentCode: '', serialNumbers: [] as string[] })
const addItem = () => {
  const clientItemKey = key()
  items.value.push({ clientItemKey, projectName: '', businessLevelCode: '', officeDepartmentCode: '', scopes: [newScope()] })
  serialInputs[clientItemKey] = ['']
}
const removeItem = (index: number) => {
  const [removed] = items.value.splice(index, 1)
  if (removed) delete serialInputs[removed.clientItemKey]
}
const addScope = (itemIndex: number) => {
  items.value[itemIndex].scopes.push(newScope())
  serialInputs[items.value[itemIndex].clientItemKey].push('')
}
const removeScope = (itemIndex: number, scopeIndex: number) => {
  if (items.value[itemIndex].scopes.length === 1) return message.warning('每个子项目至少保留一个范围')
  items.value[itemIndex].scopes.splice(scopeIndex, 1)
  serialInputs[items.value[itemIndex].clientItemKey].splice(scopeIndex, 1)
}
const payload = (): ProjectSplitDraftInput => ({
  parentProjectId: props.projectId,
  templateRevisionId: props.templateRevisionId,
  items: items.value.map((item) => ({ ...item, scopes: item.scopes.map((scope, index) => ({
    ...scope,
    serialNumbers: (serialInputs[item.clientItemKey]?.[index] || '').split(/[,，\s]+/).filter(Boolean)
  })) }))
})
const hydrate = (value: ProjectSplitDraftVO) => {
  items.value = value.items.map((item) => ({
    clientItemKey: item.clientItemKey, projectName: item.projectName,
    businessLevelCode: item.businessLevelCode, officeDepartmentCode: item.officeDepartmentCode,
    treeSort: item.treeSort,
    scopes: item.scopes.map((scope) => ({ orderLineId: scope.orderLineId, quantity: scope.allocatedQty,
      officeDepartmentCode: scope.officeDepartmentCode, serialNumbers: scope.serialNo ? [scope.serialNo] : [] }))
  }))
  items.value.forEach((item) => { serialInputs[item.clientItemKey] = item.scopes.map((scope) => (scope.serialNumbers || []).join(',')) })
}
const validateLocal = () => {
  if (!items.value.length) return '请新增至少一个子项目'
  if (items.value.some((item) => !item.projectName.trim() || !item.scopes.length)) return '请填写项目名称和组合范围'
  if (items.value.some((item) => item.scopes.some((scope) => !scope.orderLineId || scope.quantity <= 0))) return '订单行ID和数量必须有效'
}
const saveDraft = async () => {
  const error = validateLocal()
  if (error) return message.warning(error)
  saving.value = true
  try {
    draft.value = draft.value
      ? await SplitApi.updateDraft(draft.value.id, payload(), draft.value.draftVersion, key())
      : await SplitApi.createDraft(payload(), key())
    localStorage.setItem(storageKey(), String(draft.value.id))
    preview.value = undefined
    step.value = 0
    message.success('拆分草稿已保存')
  } finally { saving.value = false }
}
const previewDraft = async () => {
  if (!draft.value) return
  previewing.value = true
  try {
    preview.value = await SplitApi.previewDraft(draft.value.id, draft.value.draftVersion, key())
    draft.value = { ...draft.value, parentVersion: preview.value.parentVersion,
      scopeVersion: preview.value.scopeVersion, treeVersion: preview.value.treeVersion }
    step.value = 1
  }
  finally { previewing.value = false }
}
const validateAgain = async () => {
  if (!draft.value) return
  previewing.value = true
  try {
    preview.value = await SplitApi.validateDraft(draft.value.id, draft.value.draftVersion, key())
    draft.value = { ...draft.value, parentVersion: preview.value.parentVersion,
      scopeVersion: preview.value.scopeVersion, treeVersion: preview.value.treeVersion }
  }
  finally { previewing.value = false }
}
const applyDraft = async () => {
  if (!draft.value || !preview.value?.valid) return
  applying.value = true
  try {
    await SplitApi.applyDraft(draft.value, key())
    localStorage.removeItem(storageKey())
    step.value = 3
    message.success('拆分方案已原子应用')
    emit('applied')
  } finally { applying.value = false }
}
const itemErrors = (clientItemKey: string) => preview.value?.items.find((item) => item.clientItemKey === clientItemKey)?.errors || []
const restore = async () => {
  draft.value = undefined; preview.value = undefined; items.value = []
  const requestId = Number(localStorage.getItem(storageKey()))
  if (requestId) {
    try { draft.value = await SplitApi.getDraft(requestId); hydrate(draft.value) } catch { localStorage.removeItem(storageKey()) }
  }
  if (!items.value.length) addItem()
}
watch(() => props.projectId, restore, { immediate: true })
</script>

<style scoped lang="scss">
.panel-heading, .actions, .split-card header, .footer-actions { display: flex; align-items: center; }
.panel-heading { justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.panel-heading h3 { margin: 0 0 4px; font-size: 15px; color: var(--el-text-color-primary); }
.panel-heading span { color: var(--el-text-color-secondary); font-size: 12px; }
.actions { gap: 8px; }
.steps, .info-alert { margin-bottom: 16px; }
.split-list { display: grid; gap: 12px; }
.split-card { padding: 14px; border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); background: var(--el-bg-color); }
.split-card header { justify-content: space-between; margin-bottom: 8px; }
.item-form { display: grid; grid-template-columns: 2fr 1fr 1fr; gap: 12px; }
.scope-row { display: grid; grid-template-columns: 150px 150px minmax(150px, 1fr) minmax(180px, 1fr) auto; gap: 8px; margin-bottom: 8px; }
.scope-row :deep(.el-input-number) { width: 100%; }
.item-error { margin-top: 8px; }
.footer-actions { justify-content: flex-end; gap: 8px; margin-top: 16px; }
@media (max-width: 991px) { .scope-row { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 767px) {
  .panel-heading { align-items: stretch; flex-direction: column; }
  .actions, .item-form, .scope-row, .footer-actions { display: grid; grid-template-columns: 1fr; }
  .steps :deep(.el-step__title) { font-size: 12px; }
}
</style>
