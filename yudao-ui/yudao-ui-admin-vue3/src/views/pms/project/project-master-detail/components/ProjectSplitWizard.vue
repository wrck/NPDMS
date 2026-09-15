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
        <el-button type="primary" :disabled="previewing || applying" :loading="saving" @click="saveDraft">保存草稿</el-button>
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
        <el-form label-position="top">
          <el-form-item label="子项目模板" required>
            <ChildTemplatePicker
              :parent-project-id="projectId" :revision-id="item.templateRevisionId"
              :disabled="saving || applying" @change="selectTemplate(item, $event)" />
          </el-form-item>
          <el-form-item label="模板选择原因（选择非匹配模板时必填）">
            <el-input v-model="item.templateSelectionReason" maxlength="512" placeholder="说明子项目采用该模板的原因" />
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
      <el-button :disabled="!draft || dirty" :loading="previewing" @click="previewDraft">生成预览</el-button>
      <el-button :disabled="!preview || dirty" :loading="previewing" @click="validateAgain">重新校验</el-button>
      <el-button type="primary" :disabled="!preview?.valid || dirty" :loading="applying" @click="applyDraft">确认原子应用</el-button>
    </div>
    <el-alert v-if="draft && dirty" title="方案已修改，请先保存草稿再预览和应用" type="info" :closable="false" />
    <el-result
      v-if="preview"
      :icon="preview.valid ? 'success' : 'error'"
      :title="preview.valid ? '拆分方案校验通过' : '拆分方案需要修正'"
      :sub-title="preview.valid ? `预览哈希 ${preview.previewHash || '—'}` : preview.errors.join('；')"
    />
  </ContentWrap>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import * as SplitApi from '@/api/pms/project/project-splits'
import type { LongId, ProjectSplitDraftInput, ProjectSplitDraftVO, ProjectSplitItemInput, ProjectSplitPreviewVO } from '@/api/pms/project/project-splits'
import ChildTemplatePicker from './ChildTemplatePicker.vue'

const props = defineProps<{ projectId: number }>()
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
const editVersion = ref(0)
const savedEditVersion = ref(0)
const dirty = computed(() => editVersion.value !== savedEditVersion.value)
let workspaceGeneration = 0
watch([items, serialInputs], () => { editVersion.value++; preview.value = undefined }, { deep: true, flush: 'sync' })
const storageKey = () => `fproj002:split-request:${props.projectId}`
const key = () => crypto.randomUUID()
const selectTemplate = (item: ProjectSplitItemInput, revisionId: LongId) => {
  item.templateRevisionId = revisionId
  item.templateSelectionReason = ''
  preview.value = undefined
}

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
    templateRevisionId: item.templateRevisionId, templateSelectionReason: item.templateSelectionReason,
    scopes: item.scopes.map((scope) => ({ orderLineId: scope.orderLineId, quantity: scope.allocatedQty,
      officeDepartmentCode: scope.officeDepartmentCode, serialNumbers: scope.serialNo ? [scope.serialNo] : [] }))
  }))
  items.value.forEach((item) => { serialInputs[item.clientItemKey] = item.scopes.map((scope) => (scope.serialNumbers || []).join(',')) })
}
const validateLocal = () => {
  if (!items.value.length) return '请新增至少一个子项目'
  if (items.value.some((item) => !item.templateRevisionId)) return '请为每个子项目独立选择模板'
  if (items.value.some((item) => !item.projectName.trim() || !item.scopes.length)) return '请填写项目名称和组合范围'
  if (items.value.some((item) => item.scopes.some((scope) => !scope.orderLineId || scope.quantity <= 0))) return '订单行ID和数量必须有效'
}
const saveDraft = async () => {
  if (saving.value || applying.value || previewing.value) return
  const error = validateLocal()
  if (error) return message.warning(error)
  saving.value = true
  const savingVersion = editVersion.value
  const generation = workspaceGeneration
  try {
    const saved = draft.value
      ? await SplitApi.updateDraft(draft.value.id, payload(), draft.value.draftVersion, key())
      : await SplitApi.createDraft(payload(), key())
    if (generation !== workspaceGeneration) return
    draft.value = saved
    localStorage.setItem(storageKey(), String(draft.value.id))
    savedEditVersion.value = savingVersion
    preview.value = undefined
    step.value = 0
    message.success('拆分草稿已保存')
  } finally { if (generation === workspaceGeneration) saving.value = false }
}
const previewDraft = async () => {
  if (!draft.value || dirty.value || previewing.value || saving.value || applying.value) return
  previewing.value = true
  const generation = workspaceGeneration
  try {
    const result = await SplitApi.previewDraft(draft.value.id, draft.value.draftVersion, key())
    if (generation !== workspaceGeneration || dirty.value) return
    preview.value = result
    draft.value = { ...draft.value, parentVersion: preview.value.parentVersion,
      scopeVersion: preview.value.scopeVersion, treeVersion: preview.value.treeVersion }
    step.value = 1
  }
  finally { if (generation === workspaceGeneration) previewing.value = false }
}
const validateAgain = async () => {
  if (!draft.value || dirty.value || previewing.value || saving.value || applying.value) return
  previewing.value = true
  const generation = workspaceGeneration
  try {
    const result = await SplitApi.validateDraft(draft.value.id, draft.value.draftVersion, key())
    if (generation !== workspaceGeneration || dirty.value) return
    preview.value = result
    draft.value = { ...draft.value, parentVersion: preview.value.parentVersion,
      scopeVersion: preview.value.scopeVersion, treeVersion: preview.value.treeVersion }
  }
  finally { if (generation === workspaceGeneration) previewing.value = false }
}
const applyDraft = async () => {
  if (!draft.value || !preview.value?.valid || dirty.value || applying.value || saving.value || previewing.value) return
  applying.value = true
  const generation = workspaceGeneration
  try {
    await SplitApi.applyDraft(draft.value, key())
    if (generation !== workspaceGeneration) return
    localStorage.removeItem(storageKey())
    step.value = 3
    message.success('拆分方案已原子应用')
    emit('applied')
  } finally { if (generation === workspaceGeneration) applying.value = false }
}
const itemErrors = (clientItemKey: string) => (preview.value?.items.find((item) => item.clientItemKey === clientItemKey)?.errors || []).map((error) => {
  if (error.startsWith('CHILD_TEMPLATE_OVERRIDE_FORBIDDEN:')) return '当前用户没有模板匹配覆盖权限，请选择匹配模板或由授权用户处理'
  if (error.startsWith('CHILD_TEMPLATE_REASON_REQUIRED:')) return '选择非匹配模板须填写原因（不超过512字）'
  if (error.startsWith('CHILD_TEMPLATE_NOT_SELECTABLE:')) return '子项目模板未选择、已停用或发布定义不可用，请重新选择'
  return error
})
const restore = async () => {
  const generation = ++workspaceGeneration
  const projectId = props.projectId
  const cacheKey = storageKey()
  saving.value = false; previewing.value = false; applying.value = false; step.value = 0
  draft.value = undefined; preview.value = undefined; items.value = []
  const requestId = localStorage.getItem(cacheKey)
  if (requestId) {
    try {
      const restored = await SplitApi.getDraft(requestId)
      if (generation !== workspaceGeneration) return
      if (String(restored.parentProjectId) !== String(projectId)) throw new Error('草稿不属于当前父项目')
      draft.value = restored; hydrate(restored)
    } catch { if (generation === workspaceGeneration) localStorage.removeItem(cacheKey) }
  }
  if (generation !== workspaceGeneration) return
  if (!items.value.length) addItem()
  savedEditVersion.value = editVersion.value
}
watch(() => props.projectId, restore, { immediate: true })
onBeforeUnmount(() => { ++workspaceGeneration })
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
