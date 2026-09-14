<template>
  <el-drawer
    v-model="visible"
    title="项目独立计划"
    size="96%"
    :before-close="beforeClose"
    destroy-on-close
  >
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-loading="busy">
      <template v-if="state && document">
        <p
          >当前项目计划 v{{ state.effective.revisionNo
          }}<span v-if="state.draft">
            · 草稿 v{{ state.draft.revisionNo }}（编辑版本 {{ state.draft.version }}）</span
          ></p
        >
        <p>草稿只属于本项目。保存和影响预览不会改变有效计划，不更新公共模板，不发起审批。</p>
        <el-alert
          v-if="!state.editable"
          title="项目已关闭，计划仅供查看。"
          type="info"
          :closable="false"
        />
        <el-button v-else-if="!state.draft" type="primary" :loading="busy" @click="createDraft"
          >从当前计划创建草稿</el-button
        >
        <div v-else class="plan-actions">
          <el-button type="primary" :loading="busy" @click="saveDraft">保存计划草稿</el-button>
          <el-button :disabled="dirty || busy" @click="preview">影响预览</el-button>
          <el-button type="success" :disabled="!canApply" :loading="busy" @click="applyPlan"
            >生效计划</el-button
          >
          <span v-if="dirty">存在未保存修改；预览使用服务端已保存的草稿。</span>
        </div>
        <section v-if="impact" class="plan-impact" aria-label="项目计划影响预览">
          <el-alert
            :title="
              impact.issues.length
                ? '预览发现需要处理的问题，运行计划保持不变'
                : '预览通过，尚未改变运行计划'
            "
            :type="impact.issues.length ? 'warning' : 'success'"
            :closable="false"
          />
          <el-table v-if="impact.issues.length" :data="impact.issues">
            <el-table-column prop="field" label="位置" /><el-table-column
              prop="code"
              label="问题码"
            /><el-table-column prop="message" label="原因" />
          </el-table>
          <el-table :data="impact.changes" empty-text="没有执行节点或收口规则变化">
            <el-table-column prop="name" label="受影响节点" /><el-table-column label="变化"
              ><template #default="{ row }">{{
                actionLabel(row.action)
              }}</template></el-table-column
            >
            <el-table-column label="运行保护"
              ><template #default="{ row }">{{
                row.completed ? '已完成历史保留' : row.started ? '已开始工作' : '未开始'
              }}</template></el-table-column
            >
            <el-table-column label="影响"
              ><template #default="{ row }">{{ row.effects.join('；') }}</template></el-table-column
            >
            <el-table-column label="执行轮次处理">
              <template #default="{ row }">{{ executionEffect(row.nodeKey) }}</template>
            </el-table-column>
          </el-table>
        </section>
        <TemplateContentEditor
          ref="editor"
          :content="document"
          :readonly="busy || !state.editable || !state.draft"
          binding-permission="pms:project-plan:manage"
          @dirty-change="bindingDirty = $event"
        />
      </template>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import TemplateContentEditor from '../../project-templates/TemplateContentEditor.vue'
import type { TemplateDesignerDocument } from '@/api/pms/project/project-templates'
import {
  getProjectPlan,
  createProjectPlanDraft,
  saveProjectPlanDraft,
  previewProjectPlanDraft,
  applyProjectPlanDraft,
  type ProjectPlanState,
  type PlanImpact
} from '@/api/pms/project/projects/projectPlan'
import { errorText } from '../../project-templates/editorModel'
const props = defineProps<{ projectId: number }>()
const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const visible = defineModel<boolean>({ default: false })
const state = ref<ProjectPlanState>()
const document = ref<TemplateDesignerDocument>()
const editor = ref<InstanceType<typeof TemplateContentEditor>>()
const impact = ref<PlanImpact>()
const busy = ref(false)
const error = ref('')
const bindingDirty = ref(false)
const baseline = ref('')
const dirty = computed(
  () =>
    bindingDirty.value ||
    (!!state.value?.draft && JSON.stringify(document.value) !== baseline.value)
)
let generation = 0
let createKey = ''
let saveKey = ''
let saveIntent = ''
let applyKey = ''
let applyIntent = ''
const canApply = computed(
  () =>
    !!state.value?.editable &&
    !!state.value?.draft &&
    !busy.value &&
    !dirty.value &&
    !!impact.value &&
    impact.value.issues.length === 0
)
const actionLabel = (action: string) =>
  ({ ADD: '新增', REMOVE: '移除', UPDATE: '调整', REEVALUATE: '关联重评' })[action] ?? action
const executionsByKey = computed(
  () => new Map((impact.value?.executionChanges || []).map((change) => [change.nodeKey, change]))
)
const executionEffect = (nodeKey: string) => {
  const execution = executionsByKey.value.get(nodeKey)
  if (!execution) return '—'
  switch (execution.action) {
    case 'CREATE':
      return '新增独立执行'
    case 'REBASE_CURRENT':
      return `延续第${execution.roundNo}轮，不自动返工`
    case 'PRESERVE_HISTORY':
      return `保留第${execution.roundNo}轮及原冻结版本`
    case 'RETIRE_UNSTARTED':
      return '移除未开始节点，保留原计划记录'
  }
}
const setDocument = () => {
  document.value = JSON.parse(
    JSON.stringify(state.value?.draft?.designer ?? state.value?.effective.designer)
  )
  baseline.value = JSON.stringify(document.value)
  bindingDirty.value = false
  impact.value = undefined
}
const load = async () => {
  const request = ++generation
  state.value = undefined
  document.value = undefined
  impact.value = undefined
  error.value = ''
  busy.value = true
  createKey = crypto.randomUUID()
  saveIntent = ''
  saveKey = ''
  applyKey = ''
  applyIntent = ''
  try {
    const result = await getProjectPlan(props.projectId)
    if (request === generation) {
      state.value = result
      setDocument()
    }
  } catch (failure) {
    if (request === generation) error.value = errorText(failure)
  } finally {
    if (request === generation) busy.value = false
  }
}
const createDraft = async () => {
  if (!state.value?.editable || state.value.draft || busy.value) return
  const request = generation
  busy.value = true
  error.value = ''
  try {
    const draft = await createProjectPlanDraft(props.projectId, state.value.effective.id, createKey)
    if (request !== generation || !state.value) return
    state.value.draft = draft
    setDocument()
  } catch (failure) {
    if (request === generation) error.value = errorText(failure)
  } finally {
    if (request === generation) busy.value = false
  }
}
const saveDraft = async () => {
  if (!state.value?.draft || !document.value || busy.value) return
  const request = generation
  const projectId = props.projectId
  const draft = state.value.draft
  const source = document.value
  busy.value = true
  error.value = ''
  try {
    const prepared = (await editor.value?.prepareSave()) ?? source
    if (request !== generation) return
    const intent = JSON.stringify({
      id: draft.id,
      version: draft.version,
      designer: prepared
    })
    if (intent !== saveIntent) {
      saveIntent = intent
      saveKey = crypto.randomUUID()
    }
    const saved = await saveProjectPlanDraft(projectId, draft, prepared, saveKey)
    if (request !== generation || !state.value) return
    state.value.draft = saved
    setDocument()
  } catch (failure) {
    if (request === generation) error.value = errorText(failure)
  } finally {
    if (request === generation) busy.value = false
  }
}
const preview = async () => {
  if (!state.value?.draft || dirty.value || busy.value) return
  const request = generation
  busy.value = true
  error.value = ''
  impact.value = undefined
  try {
    const result = await previewProjectPlanDraft(props.projectId, state.value.draft)
    if (request === generation) impact.value = result
  } catch (failure) {
    if (request === generation) error.value = errorText(failure)
  } finally {
    if (request === generation) busy.value = false
  }
}
const applyPlan = async () => {
  if (!canApply.value || !impact.value) return
  const observed = impact.value
  const projectId = props.projectId
  const request = generation
  busy.value = true
  error.value = ''
  try {
    try {
      await ElMessageBox.confirm(
        '确认将已预览的计划应用到本项目？未结束工作按新计划继续，已完成历史保留，不自动返工。',
        '生效项目计划',
        {
          confirmButtonText: '确认生效',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
    } catch {
      return
    }
    if (request !== generation) return
    const intent = JSON.stringify({ projectId, expectedPreview: observed })
    if (applyIntent !== intent) {
      applyIntent = intent
      applyKey = crypto.randomUUID()
    }
    const result = await applyProjectPlanDraft(projectId, observed, applyKey)
    if (request !== generation) return
    message.success(`项目计划 v${result.revisionNo} 已生效`)
    emit('changed')
    await load()
  } catch (failure) {
    if (request === generation)
      error.value = `${errorText(failure)}；如运行或草稿已变化，请重新预览。`
  } finally {
    if (request === generation) busy.value = false
  }
}
const beforeClose = async (done: () => void) => {
  if (busy.value) return
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('存在未保存的计划修改，确认关闭？', '未保存修改')
    } catch {
      return
    }
  }
  done()
}
watch(
  () => [visible.value, props.projectId],
  () => {
    if (visible.value) void load()
    else {
      generation++
      state.value = undefined
      document.value = undefined
      impact.value = undefined
      busy.value = false
    }
  }
)
watch(
  document,
  () => {
    impact.value = undefined
  },
  { deep: true }
)
</script>

<style scoped>
.plan-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.plan-impact {
  margin-bottom: 20px;
}
</style>
