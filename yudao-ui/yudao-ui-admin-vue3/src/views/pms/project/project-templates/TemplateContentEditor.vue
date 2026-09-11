<template>
  <section class="delivery-design">
    <div class="design-heading">
      <div>
        <h3>项目交付设计器</h3>
        <p>DesignerDocument 是唯一设计态真值。阶段、任务、关系、绑定和规则直接在这里维护，发布时由 Compiler 生成不可变执行快照。</p>
      </div>
      <el-tag effect="plain">{{ content.stages.length }} 个阶段 · {{ content.tasks.filter((task) => task.stageCode !== 'S0').length }} 项任务</el-tag>
    </div>
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" class="notice" />

    <div class="designer-toolbar" aria-label="模板设计工作区">
      <el-radio-group v-model="designerView" size="large">
        <el-radio-button value="TASKS">阶段与任务</el-radio-button>
        <el-radio-button value="FLOW">流程画布</el-radio-button>
        <el-radio-button value="RULES">规则与决策</el-radio-button>
        <el-radio-button value="ADVANCED">高级配置</el-radio-button>
      </el-radio-group>
      <span class="toolbar-hint">显示排序不生成流程边；配置业务入口不授予领域权限；规则树与决策表保存同一 AST。</span>
    </div>

    <section v-if="designerView === 'FLOW'" class="workspace-panel">
      <div class="flow-overview">
        <div class="canvas-panel">
          <div class="panel-heading"><div><h4>阶段关系画布</h4><p>只呈现显式关系，不根据阶段编号或排序补边。</p></div></div>
          <StageGraphPreview :content="content" />
        </div>
        <aside class="designer-summary" aria-label="模板结构摘要">
          <div><strong>{{ content.stages.length }}</strong><span>阶段</span></div>
          <div><strong>{{ content.transitions.length }}</strong><span>关系</span></div>
          <div><strong>{{ content.gates.length }}</strong><span>门禁</span></div>
          <div><strong>{{ content.deliverables.length }}</strong><span>交付要求</span></div>
        </aside>
      </div>
      <div class="relation-panel">
        <div class="panel-heading"><div><h4>关系配置</h4><p>关系条件直接保存 Rule AST，不再引用独立 CompletionRule revision。</p></div></div>
        <StageRelationsEditor :content="content" :readonly="readonly" />
      </div>
    </section>

    <section v-else-if="designerView === 'TASKS'" class="workspace-panel">
      <div class="delivery-layout">
        <nav class="stage-nav" aria-label="交付阶段">
          <p class="nav-caption">阶段导航 <span>显示顺序不代表流程关系</span></p>
          <div v-for="(stage, index) in orderedStages" :key="stage.nodeKey" class="stage-item">
            <button type="button" class="stage-button" :class="{ active: activeStage === stage.code }" :aria-current="activeStage === stage.code ? 'step' : undefined" @click="selectStage(stage.code)">
              <span class="stage-code">{{ stage.code }}</span>
              <span class="stage-title">{{ stage.code === 'S0' ? '项目基本操作' : stage.name }}<small>{{ stage.code === 'S0' ? '创建 · 属性 · 团队 · 范围' : `${tasksFor(stage.code).length} 项业务任务` }}</small></span>
            </button>
            <span v-if="!readonly" class="item-move">
              <button type="button" class="move-button" title="上移" :disabled="index === 0" @click.stop="moveStage(stage.code, -1)">↑</button>
              <button type="button" class="move-button" title="下移" :disabled="index === orderedStages.length - 1" @click.stop="moveStage(stage.code, 1)">↓</button>
            </span>
          </div>
          <button v-if="unassigned.length" type="button" class="stage-button" :class="{ active: activeStage === '__unassigned' }" @click="selectStage('__unassigned')">未归属阶段 · {{ unassigned.length }} 项</button>
          <el-empty v-if="!content.stages.length" description="尚未选择阶段" :image-size="48" />
          <el-button v-if="!readonly" link type="primary" @click="stagePicker = !stagePicker">从资产库导入阶段</el-button>
          <DefinitionSelect v-if="stagePicker && !readonly" kind="STAGE" business @selected="addStage" />
        </nav>

        <main class="stage-content">
          <template v-if="activeStage === 'S0'">
            <div class="section-heading"><div><h3>项目基本操作</h3><p>项目创建、属性维护、团队与范围管理在项目中直接办理，不配置为交付任务。</p></div><el-tag type="info">S0</el-tag></div>
            <div class="basic-operations"><div v-for="item in basicOperations" :key="item.name"><strong>{{ item.name }}</strong><p>{{ item.description }}</p></div></div>
            <section v-if="tasksFor('S0').length" class="historical-tasks">
              <el-alert title="发现 S0 任务。V2 Compiler 会拒绝 S0 交付任务，请明确移除。" type="warning" :closable="false" />
              <div v-for="task in tasksFor('S0')" :key="task.nodeKey" class="historical-row"><span>{{ task.name || '未命名任务' }}</span><el-button v-if="!readonly" link type="danger" @click="removeTask(task)">移除</el-button></div>
            </section>
          </template>

          <template v-else-if="currentStage || activeStage === '__unassigned'">
            <div class="section-heading"><div><h3>{{ currentStage?.name ?? '未归属阶段的任务' }}</h3><p>以可独立负责的业务结果命名；提交、审批、上传和采集属于任务内办理功能。</p></div><el-button v-if="!readonly && currentStage" type="primary" plain @click="newTaskOpen = !newTaskOpen">新增任务</el-button></div>
            <section v-if="newTaskOpen && !readonly" class="task-create">
              <h4>从已发布任务资产导入</h4><p>导入时把绑定、权限、完成规则和 BusinessView 冻结为 Designer 节点；后续编辑不依赖原 revision。</p>
              <DefinitionSelect kind="TASK" business @selected="addTask" />
            </section>

            <div class="task-layout">
              <div class="task-list" role="list" aria-label="业务任务">
                <div v-for="(task, index) in stageTasks" :key="task.nodeKey" class="task-item">
                  <button type="button" class="task-button" :class="{ active: selectedTask === task }" @click="selectedTask = task">
                    <strong>{{ task.name || '未命名任务' }}</strong><span>{{ objectiveOf(task.description) || '选择任务，完善业务目标与办理方式' }}</span>
                    <small v-if="task.parentTaskCode">子任务 · {{ content.tasks.find((row) => row.code === task.parentTaskCode)?.name ?? '保留父任务关系' }}</small>
                  </button>
                  <span v-if="!readonly" class="item-move">
                    <button type="button" class="move-button" :disabled="index === 0" @click.stop="moveTask(task, -1)">↑</button>
                    <button type="button" class="move-button" :disabled="index === stageTasks.length - 1" @click.stop="moveTask(task, 1)">↓</button>
                  </span>
                </div>
                <el-empty v-if="!stageTasks.length" description="暂无业务任务，按本场景需要添加" :image-size="64" />
              </div>

              <section v-if="selectedTask && stageTasks.includes(selectedTask)" class="task-detail" aria-label="任务详情">
                <div class="detail-heading"><h4>任务详情</h4><el-button v-if="!readonly" link type="danger" @click="removeTask(selectedTask)">移除任务</el-button></div>
                <el-form label-position="top" :disabled="readonly">
                  <el-form-item label="任务编码"><el-input v-model="selectedTask.code" /></el-form-item>
                  <el-form-item label="任务名称"><el-input v-model="selectedTask.name" /></el-form-item>
                  <el-form-item label="业务目标"><el-input :model-value="objectiveOf(selectedTask.description)" type="textarea" :rows="3" placeholder="说明要达成的业务结果" @update:model-value="setObjective" /></el-form-item>
                </el-form>
                <div v-if="taskTools.length" class="task-tools"><span class="muted">任务内办理</span><el-tag v-for="tool in taskTools" :key="tool" type="info" effect="plain" size="small">{{ tool }}</el-tag></div>
                <TaskBindingEditor :key="selectedTask.nodeKey" :task="selectedTask" :model-value="pendingBindings.get(selectedTask)" :readonly="readonly" @update:model-value="setBinding(selectedTask!, $event)" />

                <div class="deliverable-heading"><h4>必要交付物</h4><el-button v-if="!readonly" link @click="deliverablePicker = !deliverablePicker">从资产库导入</el-button></div>
                <p v-if="!taskDeliverables.length" class="muted">未额外要求文件。优先复用真实业务结果，不默认每个任务上传附件。</p>
                <div v-for="item in taskDeliverables" :key="item.nodeKey" class="historical-row"><span>{{ item.name }} <el-tag size="small" :type="item.required ? 'warning' : 'info'">{{ item.required ? '必要' : '可选' }}</el-tag></span><el-button v-if="!readonly" link @click="removeDeliverable(item.nodeKey)">移除</el-button></div>
                <DefinitionSelect v-if="deliverablePicker && !readonly" kind="DELIVERABLE" business @selected="addDeliverable" />
              </section>
            </div>
          </template>
          <el-empty v-else description="选择阶段开始设计，不自动生成阶段或任务" :image-size="72" />
        </main>
      </div>
    </section>

    <section v-else-if="designerView === 'RULES'" class="workspace-panel rules-workspace">
      <div class="panel-heading"><div><h4>规则与决策</h4><p>阶段和任务的完成规则直接属于节点。选择一个节点后，可在规则树与决策表间无损切换。</p></div></div>
      <div class="rules-layout">
        <el-table :data="ruleEntries" border highlight-current-row @current-change="selectRule">
          <el-table-column prop="scope" label="范围" width="80" />
          <el-table-column prop="name" label="节点" min-width="180" />
          <el-table-column prop="code" label="编码" min-width="150" />
          <el-table-column label="规则" width="120"><template #default="{ row }"><el-tag :type="row.target.completionRule?.expression ? 'success' : 'warning'">{{ row.target.completionRule?.expression ? '已配置' : '缺失' }}</el-tag></template></el-table-column>
        </el-table>
        <div class="rule-editor-panel">
          <template v-if="activeRuleEntry">
            <div class="rule-title"><strong>{{ activeRuleEntry.scope }} · {{ activeRuleEntry.name }}</strong><span>{{ activeRuleEntry.code }}</span></div>
            <RuleDecisionDesigner
              :model-value="activeRuleEntry.target.completionRule.expression"
              :disabled="readonly"
              @update:model-value="activeRuleEntry!.target.completionRule.expression = $event"
            />
          </template>
          <el-empty v-else description="选择一个阶段或任务编辑完成规则" :image-size="60" />
        </div>
      </div>
    </section>

    <section v-else class="workspace-panel advanced-workspace">
      <el-alert title="这里仍编辑同一份 DesignerDocument。旧 DefinitionRevision ID 只作为导入来源证据，不是运行时配置。" type="info" :closable="false" class="mb-16px" />
      <el-divider content-position="left">适用场景</el-divider>
      <el-form label-position="top" :disabled="readonly"><div class="dimension-grid"><el-form-item v-for="dimension in dimensions" :key="dimension.key" :label="dimension.label"><el-select v-model="content.match[dimension.key]" clearable placeholder="不限"><el-option v-for="option in getStrDictOptions(dimension.dict)" :key="option.value" :value="option.value" :label="option.label" /></el-select></el-form-item></div></el-form>
      <el-divider content-position="left">流程定义</el-divider>
      <el-form label-position="top" :disabled="readonly"><el-form-item label="BPM流程定义Key（可选）"><el-input v-model="content.processDefinitionKey" placeholder="只保存稳定Key，不保存PMS流程版本" /></el-form-item></el-form>
      <el-divider content-position="left">专用闭环</el-divider>
      <TemplateClosurePolicyEditor :content="content" :readonly="readonly" />
      <el-divider content-position="left">V2 设计元数据</el-divider>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="Designer Schema">v{{ content.schemaVersion }}</el-descriptions-item>
        <el-descriptions-item label="稳定节点">{{ content.stages.length + content.tasks.length + content.milestones.length + content.deliverables.length + content.gates.length }}</el-descriptions-item>
        <el-descriptions-item label="规则资产">{{ content.ruleAssets.length }}</el-descriptions-item>
        <el-descriptions-item label="旧来源证据">{{ content.sourceEvidence ? '保留，仅追溯' : '无' }}</el-descriptions-item>
      </el-descriptions>
    </section>
  </section>
</template>
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import type { DesignerStageNode, DesignerTaskNode, TemplateDesignerDocument } from '@/api/pms/project/project-templates'
import type { DefinitionRevision } from '@/api/pms/project/project-templates/definitions'
import { createBindingSaveSession, prepareTaskBinding, type BindingSelection } from '@/api/pms/project/project-templates/directBinding'
import { deliverableFromDefinition, stageFromDefinition, taskFromDefinition } from '@/api/pms/project/project-templates/designerAssets'
import DefinitionSelect from './DefinitionSelect.vue'
import TaskBindingEditor from './TaskBindingEditor.vue'
import StageRelationsEditor from './StageRelationsEditor.vue'
import StageGraphPreview from './StageGraphPreview.vue'
import TemplateClosurePolicyEditor from './TemplateClosurePolicyEditor.vue'
import RuleDecisionDesigner from './RuleDecisionDesigner.vue'
import { cloneContent, errorText } from './editorModel'

const props = defineProps<{ content: TemplateDesignerDocument; readonly?: boolean }>()
const emit = defineEmits<{ 'dirty-change': [value: boolean] }>()
const message = useMessage()
const designerView = ref<'TASKS' | 'FLOW' | 'RULES' | 'ADVANCED'>('TASKS')
const activeStage = ref('')
const selectedTask = ref<DesignerTaskNode>()
const stagePicker = ref(false)
const newTaskOpen = ref(false)
const deliverablePicker = ref(false)
const failure = ref('')
const pendingBindings = reactive(new Map<DesignerTaskNode, BindingSelection>())
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
const unassigned = computed(() => props.content.tasks.filter((task) => !props.content.stages.some((stage) => stage.code === task.stageCode)))
const currentStage = computed(() => props.content.stages.find((stage) => stage.code === activeStage.value))
const stageTasks = computed(() => activeStage.value === '__unassigned' ? unassigned.value : tasksFor(activeStage.value))
const taskDeliverables = computed(() => props.content.deliverables.filter((row) => row.taskCode === selectedTask.value?.code))
const taskTools = computed(() => (selectedTask.value?.description?.match(/办理功能[：:]([^。\n]+)/)?.[1] ?? '').split(/[、，,；;]/).map((text) => text.trim()).filter(Boolean))
const objectiveOf = (description?: string) => description?.match(/^业务目标[：:]([^。\n]*)/)?.[1] ?? description ?? ''
const setObjective = (value: string) => {
  if (!selectedTask.value || props.readonly) return
  const previous = selectedTask.value.description ?? ''
  selectedTask.value.description = /^业务目标[：:]/.test(previous) ? previous.replace(/^业务目标[：:][^。\n]*/, `业务目标：${value}`) : value
}
const selectStage = (code: string) => { activeStage.value = code; selectedTask.value = stageTasks.value[0]; newTaskOpen.value = false; deliverablePicker.value = false }
const moveStage = (code: string, offset: -1 | 1) => {
  if (props.readonly) return
  const ordered = orderedStages.value; const index = ordered.findIndex((stage) => stage.code === code); const target = index + offset
  if (index < 0 || target < 0 || target >= ordered.length) return
  ;[ordered[index], ordered[target]] = [ordered[target], ordered[index]]
  ordered.forEach((stage, position) => (stage.sortOrder = position * 10))
}
const moveTask = (task: DesignerTaskNode, offset: -1 | 1) => {
  if (props.readonly) return
  const siblings = tasksFor(task.stageCode); const index = siblings.indexOf(task); const target = index + offset
  if (index < 0 || target < 0 || target >= siblings.length) return
  ;[siblings[index], siblings[target]] = [siblings[target], siblings[index]]
  siblings.forEach((row, position) => (row.sortOrder = position * 10))
}
watch(() => props.content, () => { pendingBindings.clear(); session.clear(); emit('dirty-change', false); selectStage(orderedStages.value[0]?.code ?? '') }, { immediate: true })
watch(() => pendingBindings.size, (size) => emit('dirty-change', size > 0))
const setBinding = (task: DesignerTaskNode, selection?: BindingSelection) => { if (selection) pendingBindings.set(task, selection); else pendingBindings.delete(task) }

const addStage = async (revision?: DefinitionRevision) => {
  if (!revision || props.readonly) return
  try {
    const stage = await stageFromDefinition(revision)
    if (props.content.stages.some((row) => row.code === stage.code)) { failure.value = '该阶段已在模板中。'; return }
    stage.sortOrder = props.content.stages.length * 10
    props.content.stages.push(stage); stagePicker.value = false; selectStage(stage.code); failure.value = ''
  } catch (error) { failure.value = errorText(error) }
}
const addTask = async (revision?: DefinitionRevision) => {
  if (!revision || props.readonly || !currentStage.value || activeStage.value === 'S0') return
  try {
    const task = await taskFromDefinition(revision, activeStage.value, stageTasks.value.length * 10)
    props.content.tasks.push(task); selectedTask.value = task; newTaskOpen.value = false; failure.value = ''
  } catch (error) { failure.value = errorText(error) }
}
const removeTask = async (task: DesignerTaskNode) => {
  if (props.readonly) return
  const referenced = props.content.deliverables.some((row) => row.taskCode === task.code)
    || props.content.tasks.some((row) => row.parentTaskCode === task.code)
    || props.content.gates.some((gate) => gate.references.some((ref) => ref.refType === 'TASK' && ref.refCode === task.code))
  if (referenced) { failure.value = '该任务仍被交付要求、子任务或门禁引用，请先调整引用。'; return }
  try { await message.confirm(`从当前 Designer 移除「${task.name}」？不会改变已发布快照或既有项目。`) } catch { return }
  pendingBindings.delete(task); props.content.tasks.splice(props.content.tasks.indexOf(task), 1); selectedTask.value = stageTasks.value[0]
}
const addDeliverable = async (revision?: DefinitionRevision) => {
  if (!revision || !selectedTask.value || props.readonly) return
  if (revision.payload.scope !== 'TASK') { failure.value = '请选择任务级交付要求。'; return }
  try {
    const item = await deliverableFromDefinition(revision, selectedTask.value.stageCode, selectedTask.value.code)
    props.content.deliverables.push(item); deliverablePicker.value = false; failure.value = ''
  } catch (error) { failure.value = errorText(error) }
}
const removeDeliverable = async (nodeKey: string) => {
  try { await message.confirm('移除当前 Designer 的这项交付要求？') } catch { return }
  const index = props.content.deliverables.findIndex((row) => row.nodeKey === nodeKey)
  if (index >= 0) props.content.deliverables.splice(index, 1)
}

interface RuleEntry { scope: string; name: string; code: string; target: DesignerStageNode | DesignerTaskNode }
const ruleEntries = computed<RuleEntry[]>(() => [
  ...props.content.stages.map((target) => ({ scope: '阶段', name: target.name, code: target.code, target })),
  ...props.content.tasks.map((target) => ({ scope: '任务', name: target.name, code: target.code, target }))
])
const activeRuleEntry = ref<RuleEntry>()
const selectRule = (row?: RuleEntry) => { activeRuleEntry.value = row }

const prepareSave = async () => {
  const content = cloneContent(props.content)
  try {
    const policy = content.closurePolicy as any
    if (policy && !policy.reviewerUserId) throw new Error('启用专用闭环后必须显式选择材料审核人。')
    for (const [task, selection] of pendingBindings) {
      const index = content.tasks.findIndex((row) => row.nodeKey === task.nodeKey)
      if (index >= 0) content.tasks[index] = await prepareTaskBinding(content.tasks[index], selection, session)
    }
    return content
  } catch (error) {
    failure.value = `${errorText(error)} 编辑内容已保留；重新保存将续办同一配置意图。`
    throw new Error(failure.value)
  }
}
defineExpose({ prepareSave, hasPendingBindings: () => pendingBindings.size > 0 })
</script>
<style scoped>
.delivery-design { color: var(--el-text-color-primary); }
.design-heading, .section-heading, .detail-heading, .deliverable-heading, .panel-heading, .rule-title { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
h3, h4 { margin: 0; font-weight: 600; } h3 { font-size: 17px; } h4 { font-size: 14px; }
.design-heading p, .section-heading p, .task-create p, .panel-heading p, .muted { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.7; margin: 8px 0 0; }
.design-heading { margin-bottom: 16px; } .notice { margin-bottom: 16px; }
.designer-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 0 18px; border-bottom: 1px solid var(--el-border-color-lighter); margin-bottom: 20px; }
.toolbar-hint { color: var(--el-text-color-secondary); font-size: 12px; text-align: right; }
.workspace-panel { min-width: 0; }.flow-overview { display: grid; grid-template-columns: minmax(0, 1fr) 180px; gap: 18px; margin-bottom: 18px; }
.canvas-panel, .relation-panel, .rule-editor-panel { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 18px; background: var(--el-bg-color); }
.designer-summary { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; align-content: start; }.designer-summary > div { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 86px; border-radius: 8px; background: var(--el-fill-color-light); }.designer-summary strong { font-size: 24px; }.designer-summary span { margin-top: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
.delivery-layout { display: grid; grid-template-columns: 220px minmax(0, 1fr); border: 1px solid var(--el-border-color-lighter); border-radius: 8px; overflow: hidden; }
.stage-nav { padding: 16px 12px; background: var(--el-fill-color-light); border-right: 1px solid var(--el-border-color-lighter); }.nav-caption { font-size: 13px; font-weight: 500; margin: 0 8px 16px; }.nav-caption span { display: block; font-size: 11px; color: var(--el-text-color-secondary); margin-top: 6px; font-weight: normal; }
.stage-button, .task-button { width: 100%; text-align: left; cursor: pointer; font: inherit; background: transparent; border: 1px solid transparent; color: inherit; border-radius: 6px; padding: 12px; }.stage-item, .task-item { position: relative; }.item-move { position: absolute; top: 6px; right: 6px; display: none; gap: 2px; }.stage-item:hover .item-move, .task-item:hover .item-move { display: inline-flex; }
.move-button { width: 22px; height: 22px; padding: 0; border: 1px solid var(--el-border-color-lighter); border-radius: 4px; background: var(--el-bg-color); cursor: pointer; }.move-button:disabled { opacity: .35; }.stage-item { margin-bottom: 6px; }.stage-button { display: flex; gap: 10px; align-items: flex-start; }.stage-button:hover, .task-button:hover { background: var(--el-fill-color); }.stage-button.active, .task-button.active { background: var(--el-color-primary-light-9); border-color: var(--el-color-primary-light-7); }
.stage-code { font-size: 12px; color: var(--el-color-primary); padding-top: 2px; }.stage-title { font-size: 14px; line-height: 1.5; }.stage-title small { display: block; color: var(--el-text-color-secondary); font-size: 11px; margin-top: 5px; }
.stage-content { min-width: 0; padding: 24px; }.basic-operations { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }.basic-operations > div { border: 1px solid var(--el-border-color-lighter); border-radius: 6px; padding: 20px; }.basic-operations p { font-size: 13px; color: var(--el-text-color-secondary); }
.task-layout { display: grid; grid-template-columns: minmax(180px, .7fr) minmax(0, 1.5fr); gap: 20px; }.task-list { display: flex; flex-direction: column; gap: 6px; }.task-button strong { display: block; }.task-button span { display: block; color: var(--el-text-color-secondary); font-size: 12px; margin-top: 6px; }.task-button small { font-size: 11px; color: var(--el-text-color-secondary); }.task-detail { min-width: 0; padding-left: 20px; border-left: 1px solid var(--el-border-color-lighter); }.task-tools { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 18px; }.task-create { padding: 16px; background: var(--el-fill-color-light); border-radius: 6px; margin-bottom: 20px; }
.historical-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--el-border-color-lighter); font-size: 13px; }.dimension-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 20px; }
.rules-layout { display: grid; grid-template-columns: minmax(300px, .8fr) minmax(0, 1.4fr); gap: 18px; margin-top: 16px; }.rule-title { margin-bottom: 14px; }.rule-title span { color: var(--el-text-color-secondary); font-size: 12px; }.advanced-workspace { max-width: 1180px; }
.delivery-design :deep(.el-select) { width: 100%; }
@media (max-width: 1050px) { .designer-toolbar { align-items: flex-start; flex-direction: column; }.flow-overview, .rules-layout { grid-template-columns: 1fr; }.delivery-layout { grid-template-columns: 190px minmax(0, 1fr); }.task-layout { grid-template-columns: 1fr; }.task-detail { border-left: 0; border-top: 1px solid var(--el-border-color-lighter); padding: 20px 0 0; } }
@media (max-width: 600px) { .designer-toolbar :deep(.el-radio-group) { display: grid; grid-template-columns: repeat(2, 1fr); width: 100%; }.delivery-layout { grid-template-columns: 1fr; }.stage-nav { border-right: 0; border-bottom: 1px solid var(--el-border-color-lighter); }.basic-operations, .dimension-grid { grid-template-columns: 1fr; } }
</style>