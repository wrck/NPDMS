<template>
  <section class="delivery-designer">
    <header class="designer-heading"
      ><div
        ><h3>规则驱动的项目交付执行系统</h3
        ><p>节点侧栏直接配置；准入满足即激活。条件树默认，决策表按需。</p></div
      >
      <el-radio-group v-model="view"
        ><el-radio-button value="FLOW">交付流程</el-radio-button
        ><el-radio-button value="RULES">模板条件与本版本规则</el-radio-button></el-radio-group
      >
    </header>
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" class="mb-12px" />
    <div v-if="view === 'FLOW'" class="designer-layout">
      <main>
        <div class="canvas-navigation"
          ><el-button :type="!stage ? 'primary' : 'default'" @click="stageKey = undefined"
            >阶段主画布</el-button
          ><template v-if="stage"
            ><span>／ {{ stage.name }} · 任务子画布</span
            ><el-select
              v-model="referenceKey"
              clearable
              filterable
              placeholder="添加跨阶段任务引用"
              @update:model-value="addReference"
              ><el-option
                v-for="task in otherTasks"
                :key="task.nodeKey"
                :value="task.nodeKey"
                :label="`${task.stageCode} · ${task.name}`" /></el-select></template
        ></div>
        <TemplateFlowCanvas
          :key="stageKey ?? 'main'"
          :nodes="canvasNodes"
          :edges="canvasEdges"
          :mode="stage ? 'TASK' : 'STAGE'"
          :readonly="readonly"
          @create="create"
          @select="selectedKey = $event"
          @enter="enterStage"
          @move="move"
          @connect="connect"
          @remove="remove"
          @disconnect="disconnect"
        />
        <div v-if="!readonly" class="asset-copy"
          ><el-button link @click="assetPicker = !assetPicker"
            >从资产库复制{{ stage ? '任务' : '阶段' }}（可选）</el-button
          >
          <DefinitionSelect
            v-if="assetPicker"
            :kind="stage ? 'TASK' : 'STAGE'"
            business
            @selected="copyAsset"
          />
        </div>
      </main>
      <aside class="node-inspector" aria-label="节点配置侧栏">
        <template v-if="selected">
          <header class="inspector-heading"
            ><h4>{{ selected.node.name }}</h4
            ><el-tag size="small">{{ kindNames[selected.kind] }}</el-tag></header
          >
          <el-alert
            v-if="referenceSelected"
            title="跨阶段引用只读；位置仅保存在当前画布，不修改原节点。"
            type="info"
            :closable="false"
          >
            <el-button link @click="openReferenceOwner">打开所属阶段编辑</el-button>
          </el-alert>
          <el-form label-position="top" :disabled="nodeReadonly">
            <el-form-item label="名称"><el-input v-model="selected.node.name" /></el-form-item>
            <el-form-item label="编码"
              ><el-input
                v-model="codeDraft"
                :maxlength="selected.kind === 'STAGE' ? 32 : undefined"
                @blur="renameCode(codeDraft)"
                @keyup.enter="renameCode(codeDraft)"
            /></el-form-item>
            <template v-if="selected.kind === 'STAGE'"
              ><el-form-item label="终点"
                ><el-switch v-model="(selected.node as DesignerStageNode).terminal" /><span
                  class="field-hint"
                  >终点不等于自动关闭项目，仍由项目收口条件判断。</span
                ></el-form-item
              ><el-button @click="enterStage(selected.node.nodeKey)"
                >进入任务子画布</el-button
              ></template
            >
            <el-form-item v-if="selected.kind === 'TASK'" label="父任务（不限制层级）"
              ><el-select
                v-model="(selected.node as DesignerTaskNode).parentTaskCode"
                clearable
                filterable
                ><el-option
                  v-for="task in content.tasks.filter((item) => item.nodeKey !== selectedKey)"
                  :key="task.nodeKey"
                  :value="task.code"
                  :label="`${task.stageCode} · ${task.name}`" /></el-select
            ></el-form-item>
            <template v-if="selected.kind === 'MILESTONE'"
              ><el-form-item label="达成说明"
                ><el-input
                  v-model="(selected.node as DesignerMilestoneNode).criteria"
                  type="textarea" /></el-form-item
            ></template>
            <template v-if="selected.kind === 'DELIVERABLE'"
              ><el-form-item label="必要交付件"
                ><el-switch
                  v-model="(selected.node as DesignerDeliverableNode).required" /></el-form-item
              ><el-form-item label="关联任务"
                ><el-select v-model="(selected.node as DesignerDeliverableNode).taskCode" clearable
                  ><el-option
                    v-for="task in content.tasks"
                    :key="task.nodeKey"
                    :value="task.code"
                    :label="task.name" /></el-select></el-form-item
            ></template>
            <template v-if="selected.kind === 'GATE'"
              ><el-form-item label="门禁用途"
                ><el-select v-model="(selected.node as DesignerGateNode).gateType"
                  ><el-option value="ENTRY" label="准入" /><el-option
                    value="EXIT"
                    label="退出" /></el-select
              ></el-form-item>
            </template>
          </el-form>
          <GateReferencesEditor v-if="selected.kind === 'GATE'" :key="selected.node.nodeKey"
            :gate="selected.node as DesignerGateNode" :readonly="nodeReadonly" :binding-permission="bindingPermission"
            :consumers="gateConsumers" />
          <template v-if="runtimeNode">
            <el-divider content-position="left">业务办理</el-divider>
            <p class="field-hint">{{
              selected.kind === 'STAGE'
                ? '阶段可以仅组织任务，也可以直接绑定业务办理。'
                : '手工任务需要真实提交；业务与审批任务使用原模块结果。'
            }}</p>
            <el-select
              v-if="selected.kind === 'STAGE'"
              aria-label="阶段办理方式"
              :disabled="nodeReadonly"
              :model-value="
                runtimeNode.workBinding
                  ? runtimeNode.workBinding.type === 'STAGE_NATIVE'
                    ? 'MANUAL'
                    : 'BUSINESS'
                  : 'NONE'
              "
              @update:model-value="setStageHandling"
            >
              <el-option value="NONE" label="仅组织任务，不单独办理" />
              <el-option value="MANUAL" label="阶段手工办理" />
              <el-option value="BUSINESS" label="已绑定业务页面／表单／审批" disabled />
            </el-select>
            <el-button v-if="!nodeReadonly" @click="businessOpen = !businessOpen"
              >{{ businessOpen ? '收起' : '配置' }}业务页面／表单／审批</el-button
            >
            <el-button
              v-if="
                !nodeReadonly &&
                selected.kind === 'TASK' &&
                (runtimeNode.workBinding?.type !== 'TASK_NATIVE' ||
                  pendingBindings.has(runtimeNode.nodeKey))
              "
              @click="setTaskManualHandling"
              >切换为手工办理</el-button
            >
            <el-radio-group v-if="selected.kind === 'TASK' && businessOpen" v-model="handlingTab" aria-label="任务办理类型" :disabled="nodeReadonly">
              <el-radio-button value="BUSINESS">业务页面／表单</el-radio-button>
              <el-radio-button value="APPROVAL">审批流程</el-radio-button>
            </el-radio-group>
            <TaskBindingEditor
              v-if="bindingHost && handlingTab !== 'APPROVAL' && (businessOpen || pendingBindings.has(runtimeNode.nodeKey))"
              :key="runtimeNode.nodeKey"
              :task="bindingHost"
              :model-value="pendingBindings.get(runtimeNode.nodeKey)"
              :readonly="nodeReadonly"
              :binding-permission="bindingPermission"
              @update:model-value="setBinding"
            />
            <ApprovalDefinitionSelect v-if="selected.kind === 'TASK' && (businessOpen && handlingTab === 'APPROVAL' || nodeReadonly && runtimeNode.workBinding?.type === 'APPROVAL')"
              :key="`approval-${runtimeNode.nodeKey}`" :binding="runtimeNode.workBinding" :readonly="nodeReadonly"
              :binding-permission="bindingPermission" @choose="setApprovalBinding" />
            <p v-if="pendingBindings.has(runtimeNode.nodeKey)" class="field-hint"
              >保存时完成业务绑定；若完成条件原先共享，将为当前节点保留独立修改，不影响其他节点。</p
            >
            <RuleSlotEditor
              v-model="runtimeNode.admissionRuleKey"
              :document="content"
              :label="`${runtimeNode.name} · 准入`"
              :readonly="nodeReadonly"
              empty-text="无附加准入限制；任务仍必须等待所属阶段激活。"
            />
            <RuleSlotEditor
              v-model="runtimeNode.completionRuleKey"
              :document="content"
              :label="`${runtimeNode.name} · 完成`"
              :readonly="nodeReadonly"
              required
              :initial-expression="nativeCompletion"
              empty-text="请配置完成条件；业务办理结果不能由打开页面或HTTP成功代替。"
            />
            <RuleSlotEditor
              v-model="runtimeNode.exitRuleKey"
              :document="content"
              :label="`${runtimeNode.name} · 退出`"
              :readonly="nodeReadonly"
              empty-text="无附加退出限制；已启动工作仍须完成或明确终止。"
            />
          </template>
          <el-button v-if="!readonly" type="danger" plain @click="remove(selected.node.nodeKey)"
            >删除节点</el-button
          >
        </template>
        <el-empty v-else description="从画布选择节点，在这里直接配置。" :image-size="64" />
      </aside>
    </div>
    <div v-else class="template-rules">
      <RuleSlotEditor
        v-model="content.matchRuleKey"
        :document="content"
        label="模板适用条件"
        :readonly="readonly"
        empty-text="未限定适用字段；匹配是推荐，授权覆盖选择仍需原因与审计。"
      />
      <RuleSlotEditor
        v-model="content.closureRuleKey"
        :document="content"
        label="项目收口条件"
        :readonly="readonly"
        required
        :initial-expression="constantRule(false)"
        empty-text="配置明确的收口条件；不附加固定审批，子孙项目是否必须结束也由这里决定。"
      />
      <el-collapse
        ><el-collapse-item title="本版本规则集合 · 不单独发布" name="rules">
          <el-table :data="content.rules ?? []"
            ><el-table-column prop="name" label="规则" /><el-table-column
              prop="kind"
              label="结果类型"
              width="130"
            /><el-table-column label="使用范围"
              ><template #default="{ row }">{{
                ruleUses(content, row.key).join('；') || '本版本内尚未引用'
              }}</template></el-table-column
            ><el-table-column label="操作" width="110"
              ><template #default="{ row }"
                ><el-button link @click="selectedRuleKey = row.key">查看／编辑</el-button></template
              ></el-table-column
            ></el-table
          >
          <el-button v-if="!readonly" @click="addStrategy">添加策略决策表（可选）</el-button>
          <template v-if="selectedRule"
            ><RuleSlotEditor
              v-if="selectedRule.kind === 'CONDITION'"
              v-model="selectedRuleKey"
              :document="content"
              :label="selectedRule.name"
              :readonly="readonly" /><template v-else-if="selectedRule.decision"
              ><el-alert
                v-if="strategyUses.length"
                :title="`使用范围：${strategyUses.join('；')}`"
                type="info"
                :closable="false" /><el-button
                v-if="
                  !readonly && strategyUses.length > 1 && strategyEditableKey !== selectedRule.key
                "
                @click="authorizeStrategy"
                :loading="strategyConfirming"
                >确认影响并编辑共享策略</el-button
              ><DecisionTableEditor
                ref="strategyEditor"
                :key="selectedRule.key"
                :model-value="selectedRule.decision"
                :readonly="strategyReadonly"
                @update:model-value="updateStrategy" /><RuleSimulationPanel
                :rule-key="selectedRule.key"
                :rules="content.rules ?? []" /></template
          ></template> </el-collapse-item
      ></el-collapse>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import type {
  DesignerStageNode,
  DesignerTaskNode,
  DesignerMilestoneNode,
  DesignerDeliverableNode,
  DesignerGateNode,
  JsonObject,
  TemplateDesignerDocument
} from '@/api/pms/project/project-templates'
import type { DefinitionRevision } from '@/api/pms/project/project-templates/definitions'
import type { DecisionTableDefinition } from '@/api/pms/project/project-templates/rules'
import {
  createBindingSaveSession,
  prepareTaskBinding,
  type BindingSelection,
  type TaskBindingHost
} from '@/api/pms/project/project-templates/directBinding'
import {
  stageFromDefinition,
  taskFromDefinition
} from '@/api/pms/project/project-templates/designerAssets'
import TemplateFlowCanvas, { type DeliveryNodeKind } from './TemplateFlowCanvas.vue'
import {
  allNodes,
  captureInlineRules,
  connectNodes,
  createDeliveryNode,
  dependencyEdges,
  disconnectNodes,
  projectTransitions,
  toCanvasNode
} from './templateCanvasModel'
import { constantRule, copyVersionRule, createVersionRule, ruleUses } from './versionRuleModel'
import RuleSlotEditor from './RuleSlotEditor.vue'
import RuleSimulationPanel from './RuleSimulationPanel.vue'
import DecisionTableEditor from './DecisionTableEditor.vue'
import { newDecisionTable } from './decisionTableModel'
import TaskBindingEditor from './TaskBindingEditor.vue'
import ApprovalDefinitionSelect, { type ApprovalDefinitionChoice } from './ApprovalDefinitionSelect.vue'
import GateReferencesEditor from './GateReferencesEditor.vue'
import { hasPermission } from '@/directives/permission/hasPermi'
import DefinitionSelect from './DefinitionSelect.vue'
const props = defineProps<{
  content: TemplateDesignerDocument
  readonly?: boolean
  bindingPermission?: 'pms:project-template:update' | 'pms:project-plan:manage'
}>()
const emit = defineEmits<{ 'dirty-change': [value: boolean] }>()
const view = ref('FLOW')
const stageKey = ref<string>()
const selectedKey = ref<string>()
const selectedRuleKey = ref<string>()
const strategyEditableKey = ref<string>()
const strategyConfirming = ref(false)
let strategyConfirmationVersion = 0
let editorActive = true
const referenceKey = ref<string>()
const references = reactive(new Set<string>())
const businessOpen = ref(false)
const handlingTab = ref('BUSINESS')
const assetPicker = ref(false)
const failure = ref('')
const strategyEditor = ref<InstanceType<typeof DecisionTableEditor>>()
const pendingBindings = reactive(new Map<string, BindingSelection>())
let session = createBindingSaveSession()
const kindNames = {
  STAGE: '阶段',
  TASK: '任务',
  MILESTONE: '里程碑',
  DELIVERABLE: '交付件',
  GATE: '门禁'
}
const stage = computed(() => props.content.stages.find((node) => node.nodeKey === stageKey.value))
const selected = computed(() =>
  allNodes(props.content).find((item) => item.node.nodeKey === selectedKey.value)
)
const runtimeNode = computed(() =>
  selected.value && ['STAGE', 'TASK'].includes(selected.value.kind)
    ? (selected.value.node as DesignerStageNode | DesignerTaskNode)
    : undefined
)
const referenceSelected = computed(
  () =>
    !!stage.value &&
    !!selected.value &&
    (selected.value.kind === 'STAGE'
      ? selected.value.node.nodeKey !== stage.value.nodeKey
      : 'stageCode' in selected.value.node && selected.value.node.stageCode !== stage.value.code)
)
const nodeReadonly = computed(() => !!props.readonly || referenceSelected.value)
const gateConsumers = computed(() => {
  const item = selected.value
  return item?.kind === 'GATE' ? props.content.tasks.filter(task => task.gateRef === item.node.code).map(task => task.name) : []
})
const otherTasks = computed(() =>
  props.content.tasks.filter((task) => task.stageCode !== stage.value?.code)
)
const edges = computed(() => dependencyEdges(props.content))
const canvasNodes = computed(() => {
  const included = allNodes(props.content).filter((item) =>
    stage.value
      ? item.kind !== 'STAGE' &&
        'stageCode' in item.node &&
        item.node.stageCode === stage.value.code
      : item.kind === 'STAGE' ||
        (item.kind !== 'TASK' && !('stageCode' in item.node && item.node.stageCode))
  )
  if (stage.value) {
    const keys = new Set(included.map((item) => item.node.nodeKey))
    const referenced = new Set(references)
    for (const edge of edges.value)
      if (keys.has(edge.to) && !keys.has(edge.from)) referenced.add(edge.from)
    for (const key of referenced) {
      const item = allNodes(props.content).find((candidate) => candidate.node.nodeKey === key)
      if (item && !keys.has(key)) included.push(item)
    }
  }
  return included.map((item, index) => {
    const reference =
      !!stage.value &&
      (item.kind === 'STAGE' ||
        ('stageCode' in item.node && item.node.stageCode !== stage.value.code))
    const point = reference
      ? props.content.layout?.nodes?.[`${stageKey.value}:reference:${item.node.nodeKey}`]
      : undefined
    return {
      ...toCanvasNode(props.content, item.kind, item.node),
      reference,
      ...(reference ? { x: point?.x ?? 90, y: point?.y ?? 100 + index * 120 } : {}),
      name: reference ? `引用 · ${item.node.name}` : item.node.name
    }
  })
})
const canvasEdges = computed(() => {
  const keys = new Set(canvasNodes.value.map((node) => node.key))
  return edges.value.filter((edge) => keys.has(edge.from) && keys.has(edge.to))
})
const nativeCompletion = computed<JsonObject>(() => ({
  predicate: selected.value?.kind === 'STAGE' ? 'STAGE_NATIVE_STATUS' : 'TASK_NATIVE_STATUS',
  parameters: { requiredStatus: 'DONE' }
}))
const expressionFor = (
  document: TemplateDesignerDocument,
  node: DesignerStageNode | DesignerTaskNode
): JsonObject =>
  document.rules?.find((rule) => rule.key === node.completionRuleKey)?.expression ??
  node.completionRule?.expression ?? {
    predicate: 'stageCode' in node ? 'TASK_NATIVE_STATUS' : 'STAGE_NATIVE_STATUS',
    parameters: { requiredStatus: 'DONE' }
  }
const hostFor = (
  document: TemplateDesignerDocument,
  node: DesignerStageNode | DesignerTaskNode
): TaskBindingHost => ({
  ...node,
  stageCode: 'stageCode' in node ? node.stageCode : node.code,
  workBinding: node.workBinding ?? {
    type: 'stageCode' in node ? 'TASK_NATIVE' : 'STAGE_NATIVE',
    parameters: {}
  },
  permission: node.permission ?? { policySnapshot: { requiredActions: ['VIEW'] } },
  completionRule: { expression: expressionFor(document, node) }
})
const bindingHost = computed(() =>
  runtimeNode.value ? hostFor(props.content, runtimeNode.value) : undefined
)
const selectedRule = computed(() =>
  props.content.rules?.find((rule) => rule.key === selectedRuleKey.value)
)
const strategyUses = computed(() =>
  selectedRule.value ? ruleUses(props.content, selectedRule.value.key) : []
)
const strategyReadonly = computed(() => props.readonly || strategyConfirming.value
  || (strategyUses.value.length > 1 && strategyEditableKey.value !== selectedRule.value?.key))
watch(
  [() => props.content, selectedRule, () => props.readonly, strategyUses],
  ([document, rule, readonly, uses], [previousDocument, previousRule, previousReadonly, previousUses]) => {
    if (document === previousDocument && rule === previousRule && readonly === previousReadonly
      && uses.length === previousUses.length && uses.every((use, index) => use === previousUses[index])) return
    strategyConfirmationVersion++
    strategyEditableKey.value = undefined
  }
)
onBeforeUnmount(() => { strategyConfirmationVersion++; editorActive = false })
watch(
  () => props.content,
  () => {
    pendingBindings.clear()
    session = createBindingSaveSession()
    references.clear()
    if (!props.readonly) captureInlineRules(props.content)
  },
  { immediate: true }
)
const codeDraft = ref('')
watch(
  () => [props.content, selected.value?.node.code] as const,
  ([, value]) => {
    codeDraft.value = value ?? ''
  },
  { immediate: true }
)
watch(selectedKey, () => {
  businessOpen.value = false
  handlingTab.value = runtimeNode.value?.workBinding?.type === 'APPROVAL' ? 'APPROVAL' : 'BUSINESS'
})
watch(stageKey, () => {
  references.clear()
  referenceKey.value = undefined
})
watch(
  () => pendingBindings.size,
  (value) => emit('dirty-change', value > 0)
)
const create = (
  kind: DeliveryNodeKind,
  key: string | undefined,
  point: { x: number; y: number }
) => {
  try {
    selectedKey.value = createDeliveryNode(
      props.content,
      kind,
      stage.value?.code,
      key,
      point
    ).nodeKey
    failure.value = ''
  } catch (error) {
    failure.value = String(error)
  }
}
const move = (key: string, point: { x: number; y: number }) => {
  const layoutKey = canvasNodes.value.find((node) => node.key === key)?.reference
    ? `${stageKey.value}:reference:${key}`
    : key
  ;((props.content.layout ??= {}).nodes ??= {})[layoutKey] = point
}
const connect = (from: string, to: string) => {
  try {
    connectNodes(props.content, from, to, stage.value?.code)
    failure.value = ''
  } catch (error) {
    failure.value = String(error)
  }
}
const disconnect = (key: string) => {
  if (!props.readonly) disconnectNodes(props.content, key)
}
const enterStage = (key: string) => {
  if (props.content.stages.some((node) => node.nodeKey === key)) {
    stageKey.value = key
    selectedKey.value = key
  }
}
const addReference = (key: string) => {
  if (key) references.add(key)
}
const openReferenceOwner = () => {
  const item = selected.value
  if (!item) return
  const ownerStageCode = 'stageCode' in item.node ? item.node.stageCode : undefined
  const owner =
    item.kind === 'STAGE'
      ? item.node.nodeKey
      : props.content.stages.find((stage) => stage.code === ownerStageCode)?.nodeKey
  if (owner) stageKey.value = owner
}
const setBinding = (value: BindingSelection | undefined) => {
  if (runtimeNode.value && !nodeReadonly.value) {
    if (value) pendingBindings.set(runtimeNode.value.nodeKey, value)
    else pendingBindings.delete(runtimeNode.value.nodeKey)
  }
}
const setApprovalBinding = async (definition: ApprovalDefinitionChoice) => {
  const authorized = () => !nodeReadonly.value && hasPermission([props.bindingPermission ?? 'pms:project-template:update'])
  if (!authorized() || selected.value?.kind !== 'TASK') return
  const document = props.content, task = selected.value.node as DesignerTaskNode
  const replacing = task.workBinding.type !== 'APPROVAL' || pendingBindings.has(task.nodeKey)
  try {
    await ElMessageBox.confirm(replacing
      ? `“${task.name}”将使用 ${definition.name} 第${definition.version}版办理。完成依据改为本轮审批通过（无附加条件），准入、退出及权限保留；原共享完成规则不影响其他节点。保存草稿不会发起审批。`
      : `“${task.name}”的审批绑定改为 ${definition.name} 第${definition.version}版，已有准入、完成、退出及权限保持不变。保存草稿不会影响在用版本。`,
    '配置任务审批', { type: 'warning', confirmButtonText: '确认配置', cancelButtonText: '取消' })
  } catch { return }
  if (props.content !== document || selected.value?.node !== task || !authorized()) return
  pendingBindings.delete(task.nodeKey)
  task.workBinding = { type: 'APPROVAL', approvalDefinitionKey: definition.key,
    parameters: { processDefinitionId: definition.id } }
  if (replacing) {
    // Approval completion first requires the current-round BPM result in the domain service.
    // CONSTANT true here means no additional completion criteria, not approval bypass.
    task.completionRuleKey = createVersionRule(document, `${task.name} · 审批通过后的附加条件`, {
      predicate: 'CONSTANT', parameters: { value: true }
    }).key
    Reflect.deleteProperty(task, 'completionRule')
    if (task.source) Reflect.deleteProperty(task.source, 'completionRuleRevisionId')
  }
  if (task.source) Reflect.deleteProperty(task.source, 'workBindingRevisionId')
}
const setStageHandling = (value: 'NONE' | 'MANUAL') => {
  if (nodeReadonly.value || selected.value?.kind !== 'STAGE' || !runtimeNode.value) return
  pendingBindings.delete(runtimeNode.value.nodeKey)
  businessOpen.value = false
  if (value === 'NONE') {
    Reflect.deleteProperty(runtimeNode.value, 'workBinding')
    Reflect.deleteProperty(runtimeNode.value, 'permission')
  } else {
    runtimeNode.value.workBinding = { type: 'STAGE_NATIVE', parameters: {} }
    runtimeNode.value.permission = { policyRef: 'PROJECT_STAGE_NATIVE_DEFAULT' }
  }
}
const setTaskManualHandling = async () => {
  if (nodeReadonly.value || selected.value?.kind !== 'TASK') return
  const document = props.content
  const task = selected.value.node as DesignerTaskNode
  try {
    await ElMessageBox.confirm(
      `“${task.name}”将切换为手工办理，整个完成条件替换为本轮真实提交；准入、退出和节点权限保留。原完成规则如被其他节点共享，不受影响。保存草稿不会推进运行，项目改版仍须通过生效校验。`,
      '切换为手工办理',
      { type: 'warning', confirmButtonText: '确认切换', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  if (props.content !== document || selected.value?.node !== task || nodeReadonly.value) return
  pendingBindings.delete(task.nodeKey)
  businessOpen.value = false
  task.workBinding = { type: 'TASK_NATIVE', parameters: {} }
  task.completionRuleKey = createVersionRule(document, `${task.name} · 手工完成`, {
    predicate: 'TASK_NATIVE_STATUS',
    parameters: { requiredStatus: 'DONE' }
  }).key
  Reflect.deleteProperty(task, 'completionRule')
  if (task.source) {
    Reflect.deleteProperty(task.source, 'workBindingRevisionId')
    Reflect.deleteProperty(task.source, 'completionRuleRevisionId')
  }
}
const remove = async (key: string) => {
  const item = allNodes(props.content).find((entry) => entry.node.nodeKey === key)
  if (!item || props.readonly) return
  if (
    stage.value &&
    (item.kind === 'STAGE' ||
      ('stageCode' in item.node && item.node.stageCode !== stage.value.code))
  ) {
    for (const edge of edges.value)
      if (
        edge.from === key &&
        props.content.tasks.some(
          (task) => task.nodeKey === edge.to && task.stageCode === stage.value?.code
        )
      )
        disconnectNodes(props.content, edge.key)
    references.delete(key)
    selectedKey.value = undefined
    return
  }
  try {
    await ElMessageBox.confirm(
      `删除“${item.node.name}”及其在准入条件中的引用${item.kind === 'STAGE' ? '，阶段内任务也将一并移除' : ''}？`,
      '删除设计节点'
    )
  } catch {
    return
  }
  const removed = new Set([key])
  if (item.kind === 'STAGE')
    props.content.tasks
      .filter((task) => task.stageCode === item.node.code)
      .forEach((task) => removed.add(task.nodeKey))
  const removedTaskCodes = new Set(
    props.content.tasks.filter((task) => removed.has(task.nodeKey)).map((task) => task.code)
  )
  for (const edge of [...dependencyEdges(props.content)])
    if (removed.has(edge.from) || removed.has(edge.to)) disconnectNodes(props.content, edge.key)
  props.content.stages = props.content.stages.filter((node) => !removed.has(node.nodeKey))
  props.content.tasks = props.content.tasks.filter((node) => !removed.has(node.nodeKey))
  props.content.milestones = props.content.milestones.filter((node) => !removed.has(node.nodeKey))
  props.content.deliverables = props.content.deliverables.filter(
    (node) => !removed.has(node.nodeKey)
  )
  props.content.gates = props.content.gates.filter((node) => !removed.has(node.nodeKey))
  for (const node of props.content.tasks)
    if (node.parentTaskCode && removedTaskCodes.has(node.parentTaskCode))
      node.parentTaskCode = undefined
  for (const asset of [
    ...props.content.milestones,
    ...props.content.deliverables,
    ...props.content.gates
  ])
    if (item.kind === 'STAGE' && asset.stageCode === item.node.code) asset.stageCode = undefined
  for (const asset of props.content.deliverables)
    if (asset.taskCode && removedTaskCodes.has(asset.taskCode)) asset.taskCode = undefined
  for (const id of removed) {
    pendingBindings.delete(id)
    if (props.content.layout?.nodes) delete props.content.layout.nodes[id]
  }
  selectedKey.value = undefined
  if (removed.has(stageKey.value ?? '')) stageKey.value = undefined
  projectTransitions(props.content)
}
const renameCode = (value: string) => {
  const item = selected.value
  if (!item || nodeReadonly.value || value === item.node.code) return
  if (
    !/^[A-Za-z][A-Za-z0-9_.:-]{0,127}$/.test(value) ||
    allNodes(props.content).some(
      (other) => other.node.nodeKey !== item.node.nodeKey && other.node.code === value
    )
  ) {
    failure.value = '编码须为唯一的字母开头标识'
    return
  }
  const previous = item.node.code
  const rewrite = (expression: JsonObject) => {
    if (Array.isArray(expression.rules))
      for (const child of expression.rules)
        if (child && typeof child === 'object' && !Array.isArray(child)) rewrite(child)
    const parameters = expression.parameters as JsonObject | undefined
    if (parameters?.refCode === previous && expression.predicate === 'TASK')
      parameters.refCode = value
    if (parameters?.refCode === `${previous}_COMPLETED` && expression.predicate === 'STATE')
      parameters.refCode = `${value}_COMPLETED`
  }
  props.content.rules?.forEach((rule) => {
    if (rule.expression) rewrite(rule.expression)
  })
  if (item.kind === 'STAGE')
    for (const entry of allNodes(props.content))
      if ('stageCode' in entry.node && entry.node.stageCode === previous)
        entry.node.stageCode = value
  if (item.kind === 'TASK') {
    props.content.tasks.forEach((task) => {
      if (task.parentTaskCode === previous) task.parentTaskCode = value
    })
    props.content.deliverables.forEach((deliverable) => {
      if (deliverable.taskCode === previous) deliverable.taskCode = value
    })
  }
  props.content.gates.forEach((gate) =>
    gate.references.forEach((reference) => {
      if (reference.refType === item.kind && reference.refCode === previous)
        reference.refCode = value
      if (
        item.kind === 'STAGE' &&
        reference.refType === 'STATE' &&
        reference.refCode === `${previous}_COMPLETED`
      )
        reference.refCode = `${value}_COMPLETED`
    })
  )
  item.node.code = value
  projectTransitions(props.content)
}
const copyAsset = async (definition: DefinitionRevision) => {
  try {
    const node = stage.value
      ? await taskFromDefinition(definition, stage.value.code, props.content.tasks.length)
      : await stageFromDefinition(definition)
    if (allNodes(props.content).some((item) => item.node.code === node.code))
      throw new Error('已有相同编码，请先调整当前节点编码再复制')
    if ('stageCode' in node) props.content.tasks.push(node)
    else props.content.stages.push(node)
    captureInlineRules(props.content)
    selectedKey.value = node.nodeKey
    assetPicker.value = false
  } catch (error) {
    failure.value = error instanceof Error ? error.message : '资产复制失败'
  }
}
const addStrategy = () => {
  if (props.readonly || strategyConfirming.value) return
  const key = `rule_${crypto.randomUUID()}`
  ;(props.content.rules ??= []).push({
    key,
    name: '新策略',
    kind: 'DECISION',
    shared: false,
    decision: newDecisionTable()
  })
  selectedRuleKey.value = key
}
const authorizeStrategy = async () => {
  const rule = selectedRule.value
  const document = props.content
  const version = strategyConfirmationVersion
  if (!rule || props.readonly || strategyConfirming.value) return
  strategyConfirming.value = true
  try {
    await ElMessageBox.confirm(`修改影响：${strategyUses.value.join('；')}`, '共享策略影响')
    if (version !== strategyConfirmationVersion || document !== props.content || rule !== selectedRule.value || props.readonly) return
    rule.shared = true
    strategyEditableKey.value = rule.key
  } catch {
    /* No changes on cancellation. */
  } finally {
    strategyConfirming.value = false
  }
}
const updateStrategy = (decision: DecisionTableDefinition) => {
  if (selectedRule.value && !strategyReadonly.value) selectedRule.value.decision = decision
}
const prepareSave = async () => {
  const source = props.content
  const assertCurrent = () => {
    if (!editorActive || source !== props.content || props.readonly) throw new Error('编辑上下文已变化，请重新保存。')
  }
  assertCurrent()
  await strategyEditor.value?.flush()
  assertCurrent()
  const document: TemplateDesignerDocument = JSON.parse(JSON.stringify(source))
  for (const [key, selection] of [...pendingBindings]) {
    const node = [...document.stages, ...document.tasks].find((item) => item.nodeKey === key)
    if (!node) continue
    const prepared = await prepareTaskBinding(
      hostFor(document, node),
      selection,
      session,
      props.bindingPermission === 'pms:project-plan:manage' ? 'PROJECT_PLAN' : 'TEMPLATE'
    )
    assertCurrent()
    node.workBinding = prepared.workBinding
    node.permission = prepared.permission
    node.source = prepared.source
    let rule = document.rules?.find((item) => item.key === node.completionRuleKey)
    if (rule && ruleUses(document, rule.key).length > 1) {
      rule = copyVersionRule(document, rule.key)
      node.completionRuleKey = rule.key
    }
    if (!rule) {
      rule = createVersionRule(document, `${node.name}·完成`, prepared.completionRule.expression)
      node.completionRuleKey = rule.key
    }
    rule.expression = prepared.completionRule.expression
    Reflect.deleteProperty(node, 'completionRule')
  }
  return document
}
defineExpose({ prepareSave, hasPendingBindings: () => pendingBindings.size > 0 })
</script>

<style scoped>
.designer-heading,
.canvas-navigation,
.inspector-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}
.designer-heading h3,
.inspector-heading h4 {
  margin: 0;
}
.designer-heading p,
.field-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.designer-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(380px, 1fr);
  gap: 16px;
}
.designer-layout main {
  min-width: 0;
}
.node-inspector {
  min-width: 0;
  max-height: 78vh;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  padding: 16px;
}
.canvas-navigation :deep(.el-select) {
  width: 230px;
}
.asset-copy {
  margin-top: 12px;
}
.template-rules {
  max-width: 1200px;
}
@media (max-width: 1100px) {
  .designer-layout {
    grid-template-columns: 1fr;
  }
  .node-inspector {
    max-height: none;
  }
}
</style>
