<template>
  <section v-loading="loading" class="task-maintenance">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-button v-if="error" link type="primary" @click="load">重新读取维护信息</el-button>
    <template v-if="view">
      <div class="section-heading"><h4>任务职责</h4><el-button link @click="showHistory">调整历史</el-button></div>
      <el-descriptions :column="1" border>
        <el-descriptions-item v-for="role in TASK_ROLE_OPTIONS" :key="role.value" :label="role.label">
          <span>{{ view.currentRoles.find(row => row.role === role.value)?.name || '未指派' }}</span>
          <el-button v-if="view.canAssign" link type="primary" class="role-action" @click="openRole(role.value)">设置{{ role.label }}</el-button>
        </el-descriptions-item>
      </el-descriptions>
      <div class="section-heading"><h4>任务说明</h4><el-button v-if="view.canEdit" link type="primary" @click="openDescription">编辑说明</el-button></div>
      <div v-if="view.description?.descriptionFormat === 'HTML'" v-dompurify-html="view.description.description || ''" class="description-body" />
      <div v-else class="description-plain">{{ view.description?.description || '暂无任务说明' }}</div>
    </template>
  </section>
  <Dialog v-model="roleVisible" :title="`设置${roleLabel(role)}`" width="min(560px, calc(100vw - 24px))">
    <el-form label-position="top">
      <el-form-item label="项目成员" required><PmsEntitySelect v-if="roleVisible" v-model="userId" :api="loadMembers" label-field="name"
        value-field="userId" query-field="keyword" placeholder="按项目成员姓名搜索" :disabled="saving" /></el-form-item>
      <el-form-item label="调整原因" required><el-input v-model="reason" type="textarea" maxlength="500" :disabled="saving" /></el-form-item>
      <el-alert v-if="saveError" :title="saveError" type="error" :closable="false" />
    </el-form>
    <template #footer><el-button :disabled="saving" @click="roleVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!userId || !reason.trim()" @click="saveRole">保存</el-button></template>
  </Dialog>
  <Dialog v-model="descriptionVisible" title="编辑任务说明" width="min(900px, calc(100vw - 24px))">
    <Editor v-if="descriptionVisible" v-model="html" :editor-id="`wbs-description-${taskId}`" height="320px" :readonly="saving" />
    <p class="length-hint">{{ html.length }} / {{ view?.descriptionLimit }}（包含格式标记）</p>
    <el-alert v-if="saveError" :title="saveError" type="error" :closable="false" />
    <template #footer><el-button :disabled="saving" @click="descriptionVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveText">保存说明</el-button></template>
  </Dialog>
  <Dialog v-model="historyVisible" title="任务职责调整历史" width="min(860px, calc(100vw - 24px))">
    <el-radio-group v-model="historyRole" @change="loadHistory(false)"><el-radio-button v-for="item in TASK_ROLE_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</el-radio-button></el-radio-group>
    <el-table :data="history" v-loading="historyLoading"><el-table-column prop="name" label="人员" />
      <el-table-column label="生效时间" min-width="160"><template #default="{ row }">{{ formatDate(row.effectiveFrom) }}</template></el-table-column>
      <el-table-column label="失效时间" min-width="160"><template #default="{ row }">{{ row.effectiveTo ? formatDate(row.effectiveTo) : '当前有效' }}</template></el-table-column>
      <el-table-column prop="reason" label="调整原因" min-width="160" /></el-table>
    <el-button v-if="historyMore" :loading="historyLoading" @click="loadHistory(true)">加载更多历史</el-button>
    <el-alert v-if="historyError" :title="historyError" type="error" :closable="false" />
  </Dialog>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import escape from 'lodash-es/escape'
import { Editor } from '@/components/Editor'
import { formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import * as Api from '@/api/pms/project/task-maintenance'
import { TASK_ROLE_OPTIONS, type TaskRole, type TaskRoleEntry, type TaskMaintenanceView } from '@/api/pms/project/task-maintenance'
import type { TaskCommandResult } from '@/api/pms/project/task-workbench'
import { createSubmissionIdempotencyState } from '@/views/pms/project/projects/submissionIdempotency'
const props = defineProps<{ projectId: number; taskId: number | string; taskVersion: number }>()
const emit = defineEmits<{ changed: [result: TaskCommandResult] }>()
const message = useMessage(), view = ref<TaskMaintenanceView>(), loading = ref(false), error = ref('')
const roleVisible = ref(false), descriptionVisible = ref(false), saving = ref(false), saveError = ref('')
const role = ref<TaskRole>('RESPONSIBLE'), userId = ref<number>(), reason = ref(''), html = ref('')
const roleSubmission = createSubmissionIdempotencyState(), descriptionSubmission = createSubmissionIdempotencyState()
const historyVisible = ref(false), historyRole = ref<TaskRole>('RESPONSIBLE'), history = ref<TaskRoleEntry[]>([])
const historyPage = ref(1), historyMore = ref(false), historyLoading = ref(false), historyError = ref('')
let sequence = 0, historySequence = 0
const roleLabel = (value: TaskRole) => TASK_ROLE_OPTIONS.find(row => row.value === value)?.label
const loadMembers = (params: PageParam & { keyword?: string }) => Api.getMembers(props.projectId, params)
const load = async () => {
  const request = ++sequence; loading.value = true; error.value = ''
  try { const data = await Api.getMaintenance(props.taskId); if (request === sequence) view.value = data }
  catch (failure: any) { if (request === sequence) error.value = failure?.message || '维护信息加载失败，请重试' }
  finally { if (request === sequence) loading.value = false }
}
const openRole = (value: TaskRole) => { role.value = value; userId.value = undefined; reason.value = ''; saveError.value = ''; roleSubmission.reset(); roleVisible.value = true }
const saveRole = async () => {
  if (!view.value || !userId.value || !reason.value.trim() || saving.value) return
  saving.value = true; saveError.value = ''
  const body = { role: role.value, userId: userId.value, reason: reason.value.trim() }
  try {
    const result = await Api.changeRole(props.taskId, view.value.version, body, roleSubmission.keyFor({ taskId: props.taskId, version: view.value.version, ...body }))
    roleVisible.value = false; message.success('任务职责已保存，原责任区间保留'); emit('changed', result)
  } catch (failure: any) { saveError.value = failure?.message || '职责调整失败，请重试' }
  finally { saving.value = false }
}
const openDescription = () => {
  if (!view.value) return
  const body = view.value.description?.description || ''
  html.value = view.value.description?.descriptionFormat === 'HTML' ? body : `<p>${escape(body).replace(/\r?\n/g, '</p><p>')}</p>`
  saveError.value = ''; descriptionSubmission.reset(); descriptionVisible.value = true
}
const saveText = async () => {
  if (!view.value || saving.value) return
  if (html.value.length > view.value.descriptionLimit) return message.warning('任务说明超出允许长度，请缩短后保存')
  saving.value = true; saveError.value = ''
  try {
    const result = await Api.saveDescription(props.taskId, view.value.version, html.value,
      descriptionSubmission.keyFor({ taskId: props.taskId, version: view.value.version, html: html.value }))
    descriptionVisible.value = false; message.success('任务说明已保存'); emit('changed', result)
  } catch (failure: any) { saveError.value = failure?.message || '任务说明保存失败，请重试' }
  finally { saving.value = false }
}
const loadHistory = async (append: boolean) => {
  if (append && historyLoading.value) return
  const request = ++historySequence, page = append ? historyPage.value + 1 : 1
  historyLoading.value = true; historyError.value = ''
  try { const result = await Api.getRoleHistory(props.taskId, historyRole.value, page)
    if (request === historySequence) { history.value = append ? [...history.value, ...result.list] : result.list; historyPage.value = page; historyMore.value = result.hasMore } }
  catch (failure: any) { if (request === historySequence) historyError.value = failure?.message || '历史加载失败，请重试' }
  finally { if (request === historySequence) historyLoading.value = false }
}
const showHistory = () => { historyVisible.value = true; void loadHistory(false) }
watch(() => [props.taskId, props.taskVersion], () => { void load() }, { immediate: true })
watch(() => props.taskId, () => { view.value = undefined; roleVisible.value = false; descriptionVisible.value = false; historyVisible.value = false; historySequence++ })
const requestLeave = () => {
  if (saving.value || roleVisible.value || descriptionVisible.value) {
    message.warning('请先保存或关闭任务维护窗口，再切换内容'); return false
  }
  return true
}
defineExpose({ requestLeave })
</script>
<style scoped>
.section-heading { display: flex; justify-content: space-between; align-items: center; margin: 12px 0; }
.section-heading h4 { margin: 0; }
.role-action { margin-left: 16px; }
.description-plain { white-space: pre-wrap; }
.description-body { overflow-wrap: anywhere; }
.description-body :deep(img) { max-width: 100%; }
.length-hint { color: var(--el-text-color-secondary); }
</style>
