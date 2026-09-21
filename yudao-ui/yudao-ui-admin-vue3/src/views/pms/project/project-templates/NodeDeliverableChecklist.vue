<template>
  <div class="deliverable-checklist">
    <p class="checklist-hint">{{
      taskCode
        ? '维护本任务交付的成果物；实例化后随任务展示，必选／可选仅标记要求强度。'
        : '维护本阶段交付的成果物，可关联到阶段内任务；实例化后随所属位置展示。'
    }}</p>
    <div v-for="row in rows" :key="row.nodeKey" class="checklist-row">
      <el-input
        v-model="row.name"
        class="row-name"
        :disabled="readonly"
        placeholder="交付件名称"
        :aria-label="`交付件名称 ${row.code}`"
      />
      <el-input
        class="row-code"
        :model-value="row.code"
        :disabled="readonly"
        placeholder="编码"
        :aria-label="`交付件编码 ${row.code}`"
        @change="applyCode(row, String($event ?? ''))"
      />
      <el-select
        v-if="!taskCode"
        class="row-task"
        :model-value="row.taskCode"
        clearable
        :disabled="readonly"
        placeholder="阶段级"
        :aria-label="`交付件归属任务 ${row.code}`"
        @update:model-value="row.taskCode = ($event as string | undefined) || undefined"
      >
        <el-option v-for="task in stageTasks" :key="task.nodeKey" :value="task.code" :label="task.name" />
      </el-select>
      <span class="row-required"
        ><el-switch v-model="row.required" :disabled="readonly"
          /><span class="row-required-label">{{ row.required ? '必选' : '可选' }}</span></span
      >
      <el-button v-if="!readonly" link type="danger" @click="removeRow(row)">删除</el-button>
      <el-button link type="primary" @click="rulesRef?.open(row)">判定规则</el-button>
    </div>
    <p v-if="rows.length === 0" class="checklist-hint">{{
      taskCode ? '本任务尚未定义交付件。' : '本阶段尚未定义交付件。'
    }}</p>
    <p v-if="error" class="checklist-error">{{ error }}</p>
    <el-button v-if="!readonly" @click="addRow">添加交付件</el-button>
    <DeliverableRuleDialog ref="rulesRef" :document="document" :readonly="readonly" />
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import type { TemplateDesignerDocument } from '@/api/pms/project/project-templates'
import { allNodes, createDeliverable } from './templateCanvasModel'
import DeliverableRuleDialog from './DeliverableRuleDialog.vue'

const props = defineProps<{
  document: TemplateDesignerDocument
  stageCode: string
  /** 传入时只维护该任务的交付件；不传则维护整个阶段并允许调整归属任务。 */
  taskCode?: string
  readonly?: boolean
}>()
const error = ref('')
const rulesRef = ref<InstanceType<typeof DeliverableRuleDialog>>()
const rows = computed(() =>
  props.document.deliverables.filter(
    (node) => node.stageCode === props.stageCode && (!props.taskCode || node.taskCode === props.taskCode)
  )
)
const stageTasks = computed(() => props.document.tasks.filter((task) => task.stageCode === props.stageCode))
const addRow = () => {
  if (props.readonly) return
  error.value = ''
  createDeliverable(props.document, props.stageCode, props.taskCode)
}
const applyCode = (row: TemplateDesignerDocument['deliverables'][number], value: string) => {
  if (props.readonly) return
  const code = value.trim()
  if (code === row.code) return
  if (
    !/^[A-Za-z][A-Za-z0-9_.:-]{0,127}$/.test(code) ||
    allNodes(props.document).some((other) => other.node.nodeKey !== row.nodeKey && other.node.code === code)
  ) {
    error.value = '编码须为唯一的字母开头标识，未保存修改'
    return
  }
  error.value = ''
  row.code = code
}
const removeRow = (row: TemplateDesignerDocument['deliverables'][number]) => {
  if (props.readonly) return
  props.document.deliverables = props.document.deliverables.filter((node) => node.nodeKey !== row.nodeKey)
}
</script>
<style scoped>
.checklist-hint {
  margin: 0 0 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.checklist-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}
.checklist-row:last-of-type {
  border-bottom: none;
}
.row-name {
  flex: 1 1 160px;
  min-width: 120px;
}
.row-code {
  flex: 0 1 140px;
  min-width: 110px;
}
.row-task {
  flex: 0 1 150px;
  min-width: 120px;
}
.row-required {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.row-required-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
.checklist-error {
  margin: 8px 0 0;
  color: var(--el-color-danger);
  font-size: 12px;
}
.deliverable-checklist > .el-button {
  margin-top: 8px;
}
</style>
