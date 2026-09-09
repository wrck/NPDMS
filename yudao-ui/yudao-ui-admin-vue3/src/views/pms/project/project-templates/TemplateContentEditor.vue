<template>
  <section class="delivery-design">
    <div class="design-heading"><div><h3>设计项目交付</h3><p>按阶段组织真实业务任务，在任务内选择办理页面、完成依据与必要交付物。</p></div><el-tag effect="plain">{{ content.stages.length }} 个阶段 · {{ content.tasks.filter((task) => task.stageCode !== 'S0').length }} 项任务</el-tag></div>
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" class="notice" />
    <div class="delivery-layout">
      <nav class="stage-nav" aria-label="交付阶段">
        <p class="nav-caption">阶段导航 <span>显示顺序不代表流程关系</span></p>
        <button v-for="stage in orderedStages" :key="stage.stageCode" type="button" class="stage-button" :class="{ active: activeStage === stage.stageCode }" :aria-current="activeStage === stage.stageCode ? 'step' : undefined" @click="selectStage(stage.stageCode)">
          <span class="stage-code">{{ stage.stageCode }}</span><span class="stage-title">{{ stage.stageCode === 'S0' ? '项目基本操作' : stage.name }}<small>{{ stage.stageCode === 'S0' ? '创建 · 属性 · 团队 · 范围' : `${tasksFor(stage.stageCode).length} 项业务任务` }}</small></span>
        </button>
        <button v-if="unassigned.length" type="button" class="stage-button" :class="{ active: activeStage === '__unassigned' }" @click="selectStage('__unassigned')">未归属阶段 · {{ unassigned.length }} 项</button>
        <el-empty v-if="!content.stages.length" description="尚未选择阶段" :image-size="48" />
        <el-button v-if="!readonly" link type="primary" @click="stagePicker = !stagePicker">选择阶段</el-button>
        <DefinitionSelect v-if="stagePicker && !readonly" kind="STAGE" business @selected="addStage" />
      </nav>
      <main class="stage-content">
        <template v-if="activeStage === 'S0'">
          <div class="section-heading"><div><h3>项目基本操作</h3><p>项目创建、属性维护、团队与范围管理在项目中直接办理，不配置为交付任务。</p></div><el-tag type="info">S0</el-tag></div>
          <div class="basic-operations"><div v-for="item in basicOperations" :key="item.name"><strong>{{ item.name }}</strong><p>{{ item.description }}</p></div></div>
          <section v-if="tasksFor('S0').length" class="historical-tasks">
            <el-alert title="发现历史 S0 任务。不会静默删除，请核对引用后明确移除；新发布不允许把基本操作配置为任务。" type="warning" :closable="false" />
            <div v-for="task in tasksFor('S0')" :key="task.taskCode" class="historical-row"><span>{{ task.name || '未命名历史任务' }}</span><el-button v-if="!readonly" link type="danger" @click="removeTask(task)">移除历史任务</el-button></div>
          </section>
        </template>
        <template v-else-if="currentStage || activeStage === '__unassigned'">
          <div class="section-heading"><div><h3>{{ currentStage?.name ?? '未归属阶段的任务' }}</h3><p>以可独立负责的业务结果命名，提交、审批、上传和采集在任务内办理。</p></div><el-button v-if="!readonly && currentStage" type="primary" plain @click="newTaskOpen = !newTaskOpen">新增任务</el-button></div>
          <section v-if="newTaskOpen && !readonly" class="task-create">
            <h4>从已发布任务方案开始</h4><p>复用其办理方式、权限和完成依据，再调整任务名称与业务目标；不自动添加附件。</p>
            <DefinitionSelect kind="TASK" business @selected="addTask" />
          </section>
          <div class="task-layout">
            <div class="task-list" role="list" aria-label="业务任务">
              <button v-for="task in stageTasks" :key="task.taskCode" type="button" class="task-button" :class="{ active: selectedTask === task }" :aria-pressed="selectedTask === task" @click="selectedTask = task">
                <strong>{{ task.name || '未命名任务' }}</strong><span>{{ objectiveOf(task.description) || '选择任务，完善业务目标与办理方式' }}</span><small v-if="task.parentTaskCode">子任务 · {{ content.tasks.find((row) => row.taskCode === task.parentTaskCode)?.name ?? '保留父任务关系' }}</small>
              </button>
              <el-empty v-if="!stageTasks.length" description="暂无业务任务，按本场景需要添加" :image-size="64" />
            </div>
            <section v-if="selectedTask && stageTasks.includes(selectedTask)" class="task-detail" aria-label="任务详情">
              <div class="detail-heading"><h4>任务详情</h4><el-button v-if="!readonly" link type="danger" @click="removeTask(selectedTask)">移除任务</el-button></div>
              <el-form label-position="top" :disabled="readonly">
                <el-form-item label="任务名称"><el-input v-model="selectedTask.name" placeholder="例如：完成实施方案编制与审核" /></el-form-item>
                <el-form-item label="业务目标"><el-input :model-value="objectiveOf(selectedTask.description)" type="textarea" :rows="3" placeholder="说明要达成的业务结果，而不是上传或提交按钮" @update:model-value="setObjective" /></el-form-item>
              </el-form>
              <div v-if="taskTools.length" class="task-tools" aria-label="任务内办理功能"><span class="muted">任务内办理</span><el-tag v-for="tool in taskTools" :key="tool" type="info" effect="plain" size="small">{{ tool }}</el-tag></div>
              <TaskBindingEditor :key="selectedTask.taskCode" :task="selectedTask" :model-value="pendingBindings.get(selectedTask)" :readonly="readonly" @update:model-value="setBinding(selectedTask!, $event)" />
              <div class="deliverable-heading"><h4>必要交付物</h4><el-button v-if="!readonly" link @click="deliverablePicker = !deliverablePicker">选择交付要求</el-button></div>
              <p v-if="!taskDeliverables.length" class="muted">未额外要求文件。优先复用实际业务结果，不默认每个任务上传一份附件。</p>
              <div v-for="item in taskDeliverables" :key="item.deliverableCode" class="historical-row"><span>{{ item.name }} <el-tag size="small" :type="item.required ? 'warning' : 'info'">{{ item.required ? '必要' : '可选' }}</el-tag></span><el-button v-if="!readonly" link @click="removeDeliverable(item.deliverableCode)">移除要求</el-button></div>
              <DefinitionSelect v-if="deliverablePicker && !readonly" kind="DELIVERABLE" business @selected="addDeliverable" />
            </section>
          </div>
        </template>
        <el-empty v-else description="选择阶段开始设计，不自动生成阶段或任务" :image-size="72" />
      </main>
    </div>
    <el-collapse v-model="sections" class="design-sections">
      <el-collapse-item name="scenario" title="适用场景">
        <el-form label-position="top" :disabled="readonly"><div class="dimension-grid"><el-form-item v-for="dimension in dimensions" :key="dimension.key" :label="dimension.label"><el-select v-model="content[dimension.key]" clearable placeholder="不限"><el-option v-for="option in getStrDictOptions(dimension.dict)" :key="option.value" :value="option.value" :label="option.label" /></el-select></el-form-item></div></el-form>
      </el-collapse-item>
      <el-collapse-item name="flow" title="流程顺序 · 显式维护阶段关系">
        <StageRelationsEditor v-if="sections.includes('flow')" :content="content" :readonly="readonly" />
      </el-collapse-item>
      <el-collapse-item name="advanced" title="高级配置 · 定义、版本与底层规则">
        <template v-if="sections.includes('advanced')"><p class="muted">与交付设计使用同一份配置。已有历史字段保留，不自动转换；底层引用调整后请重新核对任务。</p><AdvancedTemplateContentEditor :content="content" :readonly="readonly" /></template>
      </el-collapse-item>
    </el-collapse>
  </section>
</template>
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import type { TaskDef, TemplateDefinitionContent } from '@/api/pms/project/project-templates'
import type { DefinitionRevision } from '@/api/pms/project/project-templates/definitions'
import { createBindingSaveSession, prepareTaskBinding, taskWithExecution, type BindingSelection } from '@/api/pms/project/project-templates/directBinding'
import DefinitionSelect from './DefinitionSelect.vue'
import TaskBindingEditor from './TaskBindingEditor.vue'
import StageRelationsEditor from './StageRelationsEditor.vue'
import AdvancedTemplateContentEditor from './AdvancedTemplateContentEditor.vue'
import { cloneContent, errorText } from './editorModel'
const props = defineProps<{ content: TemplateDefinitionContent; readonly?: boolean }>()
const emit = defineEmits<{ 'dirty-change': [value: boolean] }>()
const message = useMessage()
const sections = ref<string[]>([])
const activeStage = ref('')
const selectedTask = ref<TaskDef>()
const stagePicker = ref(false)
const newTaskOpen = ref(false)
const deliverablePicker = ref(false)
const failure = ref('')
const pendingBindings = reactive(new Map<TaskDef, BindingSelection>())
const session = createBindingSaveSession()
const dimensions = [
  { key: 'signingMethod', label: '签约方式', dict: DICT_TYPE.PMS_SIGNING_METHOD },
  { key: 'projectCategory', label: '项目类别', dict: DICT_TYPE.PMS_PROJECT_CATEGORY },
  { key: 'implementationMethod', label: '实施方式', dict: DICT_TYPE.PMS_IMPLEMENTATION_METHOD },
  { key: 'majorProjectLevel', label: '重大项目级别', dict: DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL }
] as const
const basicOperations = [{ name: '项目创建', description: '建立项目及来源信息' }, { name: '项目属性', description: '维护项目分类与业务属性' }, { name: '项目团队', description: '管理项目角色与成员' }, { name: '业务范围', description: '维护合同、订单与实施范围' }]
const orderedStages = computed(() => [...props.content.stages].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)))
const tasksFor = (code: string) => props.content.tasks.filter((task) => task.stageCode === code).sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
const unassigned = computed(() => props.content.tasks.filter((task) => !props.content.stages.some((stage) => stage.stageCode === task.stageCode)))
const currentStage = computed(() => props.content.stages.find((stage) => stage.stageCode === activeStage.value))
const stageTasks = computed(() => activeStage.value === '__unassigned' ? unassigned.value : tasksFor(activeStage.value))
const taskDeliverables = computed(() => props.content.deliverables.filter((row) => row.taskCode === selectedTask.value?.taskCode))
// Read-only projection of the existing description; not another task or configuration schema.
const taskTools = computed(() => (selectedTask.value?.description?.match(/办理功能[：:]([^。\n]+)/)?.[1] ?? '').split(/[、，,；;]/).map((text) => text.trim()).filter(Boolean))
const objectiveOf = (description?: string) => description?.match(/^业务目标[：:]([^。\n]*)/)?.[1] ?? description ?? ''
const setObjective = (value: string) => {
  if (!selectedTask.value || props.readonly) return
  const previous = selectedTask.value.description ?? ''
  selectedTask.value.description = /^业务目标[：:]/.test(previous)
    ? previous.replace(/^业务目标[：:][^。\n]*/, `业务目标：${value}`)
    : value
}
const selectStage = (code: string) => { activeStage.value = code; selectedTask.value = stageTasks.value[0]; newTaskOpen.value = false; deliverablePicker.value = false }
watch(() => props.content, () => { pendingBindings.clear(); session.clear(); emit('dirty-change', false); selectStage(orderedStages.value[0]?.stageCode ?? '') }, { immediate: true })
watch(() => pendingBindings.size, (size) => emit('dirty-change', size > 0))
const setBinding = (task: TaskDef, selection?: BindingSelection) => { if (selection) pendingBindings.set(task, selection); else pendingBindings.delete(task) }
const links = (revision: DefinitionRevision) => ({
  definitionRevisionId: revision.id,
  workBindingRevisionId: revision.references.find((ref) => ref.referenceKey === revision.payload.workBinding)?.targetRevisionId,
  permissionPolicyRevisionId: revision.references.find((ref) => ref.referenceKey === revision.payload.permissionPolicy)?.targetRevisionId,
  completionRuleRevisionId: revision.references.find((ref) => ref.referenceKey === revision.payload.completionRule)?.targetRevisionId
})
const addStage = (revision?: DefinitionRevision) => {
  if (!revision || props.readonly) return
  if (props.content.stages.some((stage) => stage.stageCode === revision.payload.stageCode)) { failure.value = '该阶段已在模板中，请选择现有阶段。'; return }
  props.content.stages.push({ ...links(revision), stageCode: revision.payload.stageCode, name: revision.payload.name, start: revision.payload.start, terminal: revision.payload.terminal, sortOrder: props.content.stages.length * 10 })
  stagePicker.value = false; selectStage(revision.payload.stageCode)
}
const addTask = (revision?: DefinitionRevision) => {
  if (!revision || props.readonly || !currentStage.value || activeStage.value === 'S0') return
  const task = taskWithExecution({ taskCode: `TASK_${crypto.randomUUID().replaceAll('-', '')}`, name: revision.payload.name, stageCode: activeStage.value, sortOrder: stageTasks.value.length * 10 }, links(revision))
  props.content.tasks.push(task); selectedTask.value = props.content.tasks[props.content.tasks.length - 1]; newTaskOpen.value = false
}
const removeTask = async (task: TaskDef) => {
  if (props.readonly) return
  const referenced = props.content.deliverables.some((row) => row.taskCode === task.taskCode) || props.content.tasks.some((row) => row.parentTaskCode === task.taskCode) || props.content.gates.some((gate) => gate.references.some((ref) => ref.refType === 'TASK' && ref.refCode === task.taskCode))
  if (referenced) { failure.value = '该任务仍有交付要求、子任务或门禁引用。请先明确调整相关引用，再移除任务；不会连带删除历史。'; return }
  try { await message.confirm(`从当前草稿移除「${task.name}」？不改变已发布模板或项目历史；保存时仍会校验其他规则引用。`) } catch { return }
  pendingBindings.delete(task); props.content.tasks.splice(props.content.tasks.indexOf(task), 1); selectedTask.value = stageTasks.value[0]
}
const addDeliverable = (revision?: DefinitionRevision) => {
  if (!revision || !selectedTask.value || props.readonly) return
  if (revision.payload.scope !== 'TASK') { failure.value = '请选择归属任务的交付要求，阶段要求可在高级配置维护。'; return }
  props.content.deliverables.push({ deliverableCode: `DEL_${crypto.randomUUID().replaceAll('-', '')}`, name: revision.payload.name ?? revision.payload.deliverableType, definitionRevisionId: revision.id, stageCode: selectedTask.value.stageCode, taskCode: selectedTask.value.taskCode, required: revision.payload.required })
  deliverablePicker.value = false
}
const removeDeliverable = async (code: string) => {
  try { await message.confirm('移除当前草稿的这项交付要求？保存时会重新校验门禁与完成依据引用。') } catch { return }
  props.content.deliverables.splice(props.content.deliverables.findIndex((row) => row.deliverableCode === code), 1)
}
const prepareSave = async () => {
  const content = cloneContent(props.content)
  try {
    for (const [task, selection] of pendingBindings) {
      const index = props.content.tasks.indexOf(task)
      if (index >= 0) content.tasks[index] = await prepareTaskBinding(task, selection, session)
    }
    return content
  } catch (error) { failure.value = `${errorText(error)} 编辑与原绑定已保留；重新保存将续办同一配置意图。`; throw new Error(failure.value) }
}
defineExpose({ prepareSave, hasPendingBindings: () => pendingBindings.size > 0 })
</script>
<style scoped>
.delivery-design { color: var(--el-text-color-primary); }
.design-heading, .section-heading, .detail-heading, .deliverable-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
h3, h4 { margin: 0; font-weight: 600; } h3 { font-size: 17px; } h4 { font-size: 14px; }
.design-heading p, .section-heading p, .task-create p, .muted { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.7; margin: 8px 0 0; }
.design-heading { margin-bottom: 20px; } .notice { margin-bottom: 16px; }
.delivery-layout { display: grid; grid-template-columns: 220px minmax(0, 1fr); border: 1px solid var(--el-border-color-lighter); border-radius: 8px; overflow: hidden; }
.stage-nav { padding: 16px 12px; background: var(--el-fill-color-light); border-right: 1px solid var(--el-border-color-lighter); }
.nav-caption { font-size: 13px; font-weight: 500; margin: 0 8px 16px; }.nav-caption span { display: block; font-size: 11px; color: var(--el-text-color-secondary); margin-top: 6px; font-weight: normal; }
.stage-button, .task-button { width: 100%; text-align: left; cursor: pointer; font: inherit; background: transparent; border: 1px solid transparent; color: inherit; border-radius: 6px; padding: 12px; }
.stage-button { display: flex; gap: 10px; margin-bottom: 6px; align-items: flex-start; }.stage-button:hover, .task-button:hover { background: var(--el-fill-color); }.stage-button.active, .task-button.active { background: var(--el-color-primary-light-9); border-color: var(--el-color-primary-light-7); }
.stage-code { font-size: 12px; color: var(--el-color-primary); padding-top: 2px; }.stage-title { font-size: 14px; line-height: 1.5; }.stage-title small { display: block; color: var(--el-text-color-secondary); font-size: 11px; margin-top: 5px; }
.stage-content { min-width: 0; padding: 24px; background: var(--el-bg-color); }.section-heading { margin-bottom: 20px; }
.basic-operations { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }.basic-operations > div { border: 1px solid var(--el-border-color-lighter); border-radius: 6px; padding: 20px; }.basic-operations strong { font-size: 14px; font-weight: 500; }.basic-operations p { font-size: 13px; color: var(--el-text-color-secondary); margin: 8px 0 0; }
.task-layout { display: grid; grid-template-columns: minmax(180px, 0.7fr) minmax(0, 1.5fr); gap: 20px; }.task-list { display: flex; flex-direction: column; gap: 6px; }.task-button strong { display: block; font-size: 14px; font-weight: 500; line-height: 1.6; }.task-button span { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.7; margin-top: 6px; }.task-button small { font-size: 11px; color: var(--el-text-color-secondary); }
.task-tools { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin: 0 0 18px; }.task-tools .muted { margin: 0 4px 0 0; }
.task-detail { min-width: 0; padding-left: 20px; border-left: 1px solid var(--el-border-color-lighter); }.detail-heading { margin-bottom: 20px; }.deliverable-heading { margin: 20px 0 12px; }.task-create { padding: 16px; background: var(--el-fill-color-light); border-radius: 6px; margin-bottom: 20px; }.task-create p { margin-bottom: 12px; }
.historical-tasks { margin-top: 24px; }.historical-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--el-border-color-lighter); font-size: 13px; }
.design-sections { margin-top: 24px; }.dimension-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 20px; }
.delivery-design :deep(.el-select) { width: 100%; }.stage-button:focus-visible, .task-button:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -2px; }
@media (max-width: 1050px) { .delivery-layout { grid-template-columns: 190px minmax(0, 1fr); }.stage-content { padding: 16px; }.task-layout { grid-template-columns: 1fr; }.task-detail { border-left: 0; border-top: 1px solid var(--el-border-color-lighter); padding: 20px 0 0; }.task-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 600px) { .delivery-layout { grid-template-columns: 1fr; }.stage-nav { border-right: 0; border-bottom: 1px solid var(--el-border-color-lighter); }.design-heading, .section-heading { flex-wrap: wrap; }.stage-content { padding: 14px; }.basic-operations, .task-list, .dimension-grid { grid-template-columns: 1fr; }.stage-button { padding: 10px; }.basic-operations > div { padding: 16px; }.stage-nav .nav-caption span { display: inline; margin-left: 8px; } }
</style>
