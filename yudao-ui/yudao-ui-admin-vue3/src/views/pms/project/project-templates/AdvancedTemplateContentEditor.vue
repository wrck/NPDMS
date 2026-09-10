<template>
  <section>
    <el-form label-width="120px" :disabled="readonly">
      <el-divider content-position="left">四维匹配条件（留空＝不限）</el-divider>
      <el-row :gutter="16"><el-col v-for="dimension in dimensions" :key="dimension.key" :xs="24" :sm="12"><el-form-item :label="dimension.label"><el-select v-model="content[dimension.key]" clearable><el-option v-for="option in getStrDictOptions(dimension.dict)" :key="option.value" :value="option.value" :label="option.label" /></el-select></el-form-item></el-col></el-row>
      <el-form-item label="BPM流程Key"><el-input v-model="content.processDefinitionKey" placeholder="可选；仅实际审批引用才校验，不维护PMS流程版本" /></el-form-item>
    </el-form>
    <el-tabs>
      <el-tab-pane label="阶段与任务">
        <section v-for="group in executionGroups" :key="group.key">
          <el-divider content-position="left">{{ group.label }}</el-divider>
          <el-table :data="content[group.key]" border empty-text="尚未配置；请显式新增或选择已发布定义">
            <el-table-column type="expand"><template #default="{ row }">
              <el-form label-width="140px" class="node-details" :disabled="readonly">
                <el-form-item label="精确定义修订"><DefinitionSelect v-model="row.definitionRevisionId" :kind="group.kind" :disabled="readonly" @selected="applyExecution(row, $event)" /></el-form-item>
                <el-form-item v-for="slot in executionSlots" :key="slot.key" :label="slot.label"><DefinitionSelect v-model="row[slot.key]" :kind="slot.kind" :disabled="readonly" /></el-form-item>
                <template v-if="group.key === 'stages'">
                  <el-form-item v-for="flag in flags" :key="flag.key" :label="flag.label"><el-radio-group v-model="row[flag.key]"><el-radio :value="true">是</el-radio><el-radio :value="false">否</el-radio></el-radio-group></el-form-item>
                  <el-form-item label="准入条件说明"><el-input v-model="row.entryCriteria" /></el-form-item><el-form-item label="准出条件说明"><el-input v-model="row.exitCriteria" /></el-form-item>
                </template>
                <template v-else>
                  <el-form-item label="所属阶段"><el-select v-model="row.stageCode" clearable><el-option v-for="stage in content.stages" :key="stage.stageCode" :value="stage.stageCode" :label="`${stage.stageCode} ${stage.name}`" /></el-select></el-form-item>
                  <el-form-item label="父任务"><el-select v-model="row.parentTaskCode" clearable><el-option v-for="task in content.tasks.filter((task) => task !== row)" :key="task.taskCode" :value="task.taskCode" :label="`${task.taskCode} ${task.name}`" /></el-select></el-form-item>
                  <el-form-item label="优先级"><el-input-number v-model="row.priority" :min="0" /></el-form-item><el-form-item label="预估工时"><el-input-number v-model="row.estimatedHours" :min="0" /></el-form-item>
                  <el-form-item label="满意度时点"><el-input v-model="row.satisfactionTiming" /></el-form-item><el-form-item label="任务说明"><el-input v-model="row.description" type="textarea" /></el-form-item>
                  <el-alert v-if="row.workBindingTypeCode" :title="`保留历史内联绑定 ${row.workBindingTypeCode}；不会自动转换。新配置请选择精确修订。`" type="info" :closable="false" />
                </template>
                <el-collapse v-if="row.definitionSnapshot"><el-collapse-item title="服务端定义快照（只读，保存不回传）"><pre>{{ JSON.stringify(row.definitionSnapshot, null, 2) }}</pre></el-collapse-item></el-collapse>
              </el-form>
            </template></el-table-column>
            <el-table-column :label="group.kind === 'STAGE' ? '阶段编码' : '任务编码'" min-width="140"><template #default="{ row }"><el-input v-model="row[group.code]" :disabled="readonly" /></template></el-table-column>
            <el-table-column label="名称" min-width="160"><template #default="{ row }"><el-input v-model="row.name" :disabled="readonly" /></template></el-table-column>
            <el-table-column label="显示排序（不生成边）" width="190"><template #default="{ row }"><el-input-number v-model="row.sortOrder" :min="0" :disabled="readonly" controls-position="right" class="!w-full" /></template></el-table-column>
            <el-table-column label="主绑定 / 权限 / 完成规则" min-width="220"><template #default="{ row }">{{ row.workBindingRevisionId ?? '待选绑定' }} / {{ row.permissionPolicyRevisionId ?? '待选权限' }} / {{ row.completionRuleRevisionId ?? '待选规则' }}<div class="text-12px">展开行配置精确修订{{ group.key === 'stages' ? '及开始/收口标志' : '' }}</div></template></el-table-column>
            <el-table-column v-if="!readonly" label="操作" width="75"><template #default="{ $index }"><el-button link type="danger" @click="removeNode(group.key, $index)">删除</el-button></template></el-table-column>
          </el-table>
          <el-button v-if="!readonly" class="mt-8px" @click="addNode(group.key)">新增{{ group.label }}</el-button>
        </section>
      </el-tab-pane>
      <el-tab-pane label="统一阶段关系"><StageRelationsEditor :content="content" :readonly="readonly" /></el-tab-pane>
      <el-tab-pane label="交付件 · 门禁 · 里程碑">
        <el-alert title="数量、来源、文件/业务产出、确认审批规则在交付件定义库统一维护，模板只引用精确修订并指定归属。" type="info" :closable="false" />
        <section v-for="group in otherGroups" :key="group.key">
          <el-divider content-position="left">{{ group.label }}</el-divider>
          <el-table :data="content[group.key]" border>
            <el-table-column label="编码" min-width="160"><template #default="{ row }"><el-input v-model="row[group.code]" :disabled="readonly" /></template></el-table-column>
            <el-table-column label="名称" min-width="160"><template #default="{ row }"><el-input v-model="row.name" :disabled="readonly" /></template></el-table-column>
            <el-table-column label="精确定义 / 要求详情" min-width="270"><template #default="{ row }"><DefinitionSelect v-model="row.definitionRevisionId" :kind="group.kind" :disabled="readonly" @selected="applyOther(row, group.kind, $event)" /></template></el-table-column>
            <el-table-column label="所属阶段" min-width="150"><template #default="{ row }"><el-select v-model="row.stageCode" clearable :disabled="readonly"><el-option v-for="stage in content.stages" :key="stage.stageCode" :value="stage.stageCode" :label="stage.name" /></el-select></template></el-table-column>
            <el-table-column v-if="group.key === 'deliverables'" label="归属任务（空＝阶段）" min-width="180"><template #default="{ row }"><el-select v-model="row.taskCode" clearable :disabled="readonly"><el-option v-for="task in content.tasks" :key="task.taskCode" :value="task.taskCode" :label="task.name" /></el-select></template></el-table-column>
            <el-table-column v-if="group.key === 'deliverables'" label="必需" width="80"><template #default="{ row }"><el-checkbox v-model="row.required" :disabled="readonly" /></template></el-table-column>
            <el-table-column v-if="group.key === 'milestones'" label="时点 / 达成标准" min-width="200"><template #default="{ row }"><el-input v-model="row.timing" :disabled="readonly" placeholder="时点" /><el-input v-model="row.criteria" :disabled="readonly" placeholder="达成标准" /></template></el-table-column>
            <el-table-column v-if="group.key === 'gates'" label="门禁与事实引用" min-width="280"><template #default="{ row }">
              <el-select v-model="row.gateType" :disabled="readonly"><el-option value="ENTRY" label="准入" /><el-option value="EXIT" label="准出" /></el-select>
              <div v-for="(reference, index) in row.references" :key="index" class="gate-row">
                <el-select v-model="reference.refType" :disabled="readonly"><el-option v-for="type in gateTypes" :key="type" :value="type" :label="gateTypeLabels[type] ?? type" /></el-select>
                <el-select v-if="reference.refType === 'TASK'" v-model="reference.refCode" :disabled="readonly" filterable placeholder="选择模板任务">
                  <el-option v-for="task in content.tasks" :key="task.taskCode" :value="task.taskCode" :label="`${task.taskCode} ${task.name ?? ''}`" />
                </el-select>
                <el-select v-else-if="reference.refType === 'MILESTONE'" v-model="reference.refCode" :disabled="readonly" filterable placeholder="选择里程碑">
                  <el-option v-for="milestone in content.milestones" :key="milestone.milestoneCode" :value="milestone.milestoneCode" :label="`${milestone.milestoneCode} ${milestone.name ?? ''}`" />
                </el-select>
                <el-select v-else-if="reference.refType === 'DELIVERABLE'" v-model="reference.refCode" :disabled="readonly" filterable placeholder="选择交付件">
                  <el-option v-for="deliverable in content.deliverables" :key="deliverable.deliverableCode" :value="deliverable.deliverableCode" :label="`${deliverable.deliverableCode} ${deliverable.name ?? ''}`" />
                </el-select>
                <el-select v-else-if="reference.refType === 'STATE'" v-model="reference.refCode" :disabled="readonly" placeholder="阶段完成码">
                  <el-option v-for="code in stateCodes" :key="code" :value="code" :label="code" />
                </el-select>
                <el-input v-else v-model="reference.refCode" :disabled="readonly" placeholder="BPM定义Key" />
                <el-button v-if="!readonly" link @click="row.references.splice(index, 1)">移除</el-button>
              </div>
              <el-button v-if="!readonly" link @click="row.references.push({ refType: 'TASK', refCode: '' })">新增引用</el-button>
            </template></el-table-column>
            <el-table-column v-if="!readonly" label="操作" width="75"><template #default="{ $index }"><el-button link type="danger" @click="removeOther(group.key, $index)">删除</el-button></template></el-table-column>
          </el-table>
          <el-button v-if="!readonly" class="mt-8px" @click="addOther(group.key)">新增{{ group.label }}</el-button>
        </section>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>
<script setup lang="ts">
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import type { TemplateDefinitionContent } from '@/api/pms/project/project-templates'
import type { DefinitionKind, DefinitionRevision } from '@/api/pms/project/project-templates/definitions'
import DefinitionSelect from './DefinitionSelect.vue'
import StageRelationsEditor from './StageRelationsEditor.vue'
const props = defineProps<{ content: TemplateDefinitionContent; readonly?: boolean }>()
const dimensions = [
  { key: 'signingMethod', label: '签约方式', dict: DICT_TYPE.PMS_SIGNING_METHOD },
  { key: 'projectCategory', label: '项目类别', dict: DICT_TYPE.PMS_PROJECT_CATEGORY },
  { key: 'implementationMethod', label: '实施方式', dict: DICT_TYPE.PMS_IMPLEMENTATION_METHOD },
  { key: 'majorProjectLevel', label: '重大项目级别', dict: DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL }
] as const
const executionGroups = [{ key: 'stages', kind: 'STAGE', code: 'stageCode', label: '阶段' }, { key: 'tasks', kind: 'TASK', code: 'taskCode', label: '任务' }] as const
const otherGroups = [{ key: 'deliverables', kind: 'DELIVERABLE', code: 'deliverableCode', label: '交付件' }, { key: 'gates', kind: 'GATE', code: 'gateCode', label: '门禁' }, { key: 'milestones', kind: 'MILESTONE', code: 'milestoneCode', label: '里程碑' }] as const
const executionSlots = [{ key: 'workBindingRevisionId', kind: 'WORK_BINDING', label: '主工作绑定' }, { key: 'permissionPolicyRevisionId', kind: 'PERMISSION_POLICY', label: '权限策略' }, { key: 'completionRuleRevisionId', kind: 'COMPLETION_RULE', label: '完成规则' }] as const
const flags = [{ key: 'start', label: '开始阶段' }, { key: 'terminal', label: '正常收口' }]
const gateTypes = ['TASK', 'MILESTONE', 'DELIVERABLE', 'STATE', 'APPROVAL', 'PROCESS']
const gateTypeLabels: Record<string, string> = {
  TASK: '任务结果',
  MILESTONE: '里程碑',
  DELIVERABLE: '交付件',
  STATE: '阶段完成',
  APPROVAL: '审批结果',
  PROCESS: '流程结果'
}
const stateCodes = ['S0_COMPLETED', 'S1_COMPLETED', 'S2_COMPLETED', 'S3_COMPLETED', 'S4_COMPLETED', 'S5_COMPLETED', 'S6_COMPLETED']
const addNode = (key: 'stages' | 'tasks') => {
  if (key === 'stages') props.content.stages.push({ stageCode: '', name: '', start: false, terminal: false })
  else props.content.tasks.push({ taskCode: '', name: '' })
}
const removeNode = (key: 'stages' | 'tasks', index: number) => props.content[key].splice(index, 1)
const removeOther = (key: 'deliverables' | 'gates' | 'milestones', index: number) => props.content[key].splice(index, 1)
const addOther = (key: 'deliverables' | 'gates' | 'milestones') => {
  if (key === 'deliverables') props.content.deliverables.push({ deliverableCode: '', name: '' })
  if (key === 'milestones') props.content.milestones.push({ milestoneCode: '', name: '' })
  if (key === 'gates') props.content.gates.push({ gateCode: '', name: '', gateType: 'ENTRY', references: [] })
}
const applyExecution = (row: any, revision?: DefinitionRevision) => {
  if (!revision) return
  row.name = revision.payload.name
  if (revision.definitionKind === 'STAGE') for (const key of ['stageCode', 'start', 'terminal']) row[key] = revision.payload[key]
  for (const slot of ['workBinding', 'permissionPolicy', 'completionRule']) row[`${slot}RevisionId`] = revision.references.find((ref) => ref.referenceKey === revision.payload[slot])?.targetRevisionId
}
const applyOther = (row: any, kind: DefinitionKind, revision?: DefinitionRevision) => {
  if (!revision) return
  if (kind === 'DELIVERABLE') row.required = revision.payload.required
  if (kind === 'MILESTONE') { row.name = revision.payload.name; row.criteria = revision.payload.criteria }
  if (kind === 'GATE') { row.gateType = revision.payload.gateType; row.references = JSON.parse(JSON.stringify(revision.payload.references)) }
}
</script>
<style scoped>
.node-details { padding: 16px; max-width: 900px; }
.gate-row { display: flex; gap: 4px; margin-top: 6px; }
.gate-row :deep(.el-select) { min-width: 110px; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
