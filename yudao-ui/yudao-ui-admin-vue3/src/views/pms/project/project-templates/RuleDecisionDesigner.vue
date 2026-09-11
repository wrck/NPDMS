<template>
  <section class="rule-decision-designer">
    <div class="designer-heading">
      <div>
        <strong>规则与决策</strong>
        <p>规则树用于复杂嵌套；决策表用于业务人员维护“行内全部满足、任一行命中”的常见规则。</p>
      </div>
      <el-tag effect="plain">{{ ruleSummary }}</el-tag>
    </div>

    <el-tabs v-model="view" class="rule-tabs">
      <el-tab-pane label="规则树" name="TREE">
        <RuleEditor :model-value="modelValue" :disabled="disabled" @update:model-value="emitRule" />
      </el-tab-pane>
      <el-tab-pane label="决策表" name="TABLE">
        <el-alert
          v-if="!tableCompatible"
          title="当前规则包含决策表无法无损表达的嵌套结构，请继续使用规则树。不会自动改写现有规则。"
          type="warning"
          :closable="false"
          class="mb-12px"
        />
        <template v-else>
          <el-alert
            title="决策语义：同一行内条件全部满足；任一决策行命中即可通过。保存仍使用现有 PREDICATE / ALL / ANY 规则结构。"
            type="info"
            :closable="false"
            class="mb-12px"
          />
          <el-table :data="decisionRows" border empty-text="暂无决策行">
            <el-table-column label="决策行" width="92">
              <template #default="{ $index }"><strong>规则 {{ $index + 1 }}</strong></template>
            </el-table-column>
            <el-table-column label="全部满足的条件" min-width="620">
              <template #default="{ row, $index: rowIndex }">
                <div class="condition-list">
                  <div v-for="(condition, conditionIndex) in row.conditions" :key="conditionIndex" class="condition-row">
                    <el-select
                      :model-value="condition.predicate"
                      :disabled="disabled"
                      placeholder="事实类型"
                      @update:model-value="changePredicate(rowIndex, conditionIndex, $event)"
                    >
                      <el-option
                        v-for="predicate in predicates"
                        :key="predicate"
                        :value="predicate"
                        :label="predicateLabels[predicate] ?? predicate"
                      />
                    </el-select>

                    <template v-if="condition.predicate === 'BUSINESS_FACT'">
                      <el-select
                        :model-value="condition.parameters?.factCode"
                        :disabled="disabled"
                        filterable
                        placeholder="完成事实"
                        @update:model-value="setParameter(rowIndex, conditionIndex, 'factCode', $event)"
                      >
                        <el-option
                          v-for="fact in factCatalog"
                          :key="`${fact.ownerContext}/${fact.objectType}/${fact.factCode}`"
                          :value="fact.factCode"
                          :label="`${fact.label}（${fact.ownerContext}/${fact.objectType}）`"
                        />
                      </el-select>
                      <el-select
                        :model-value="condition.parameters?.quantifier ?? 'ALL'"
                        :disabled="disabled"
                        class="quantifier"
                        @update:model-value="setParameter(rowIndex, conditionIndex, 'quantifier', $event)"
                      >
                        <el-option value="ALL" label="全部关联记录" />
                        <el-option value="ANY" label="任一关联记录" />
                      </el-select>
                    </template>
                    <el-input
                      v-else-if="condition.predicate?.endsWith('_NATIVE_STATUS')"
                      model-value="DONE"
                      disabled
                      aria-label="要求状态"
                    />
                    <el-select
                      v-else-if="condition.predicate === 'STATE'"
                      :model-value="condition.parameters?.refCode"
                      :disabled="disabled"
                      placeholder="阶段完成码"
                      @update:model-value="setParameter(rowIndex, conditionIndex, 'refCode', $event)"
                    >
                      <el-option v-for="code in stateCodes" :key="code" :value="code" :label="code" />
                    </el-select>
                    <el-input
                      v-else
                      :model-value="condition.parameters?.refCode"
                      :disabled="disabled"
                      placeholder="Owner事实编码 / BPM定义Key"
                      @update:model-value="setParameter(rowIndex, conditionIndex, 'refCode', $event)"
                    />
                    <el-button
                      v-if="!disabled"
                      link
                      type="danger"
                      :disabled="row.conditions.length === 1"
                      @click="removeCondition(rowIndex, conditionIndex)"
                    >移除</el-button>
                  </div>
                </div>
                <el-button v-if="!disabled" link type="primary" @click="addCondition(rowIndex)">+ 行内条件</el-button>
              </template>
            </el-table-column>
            <el-table-column v-if="!disabled" label="操作" width="88">
              <template #default="{ $index }">
                <el-button link type="danger" @click="removeRow($index)">删除行</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button v-if="!disabled" class="mt-12px" @click="addRow">新增决策行</el-button>
        </template>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import * as TemplateApi from '@/api/pms/project/project-templates'
import type { CompletionFactCatalogVO } from '@/api/pms/project/project-templates'
import type { JsonObject } from '@/api/pms/project/project-templates/definitions'
import RuleEditor from './RuleEditor.vue'
import {
  blankPredicate,
  cloneRule,
  decodeDecisionRows,
  encodeDecisionRows,
  type DecisionRow
} from './ruleDecisionModel'

defineOptions({ name: 'RuleDecisionDesigner' })

const props = defineProps<{ modelValue: JsonObject; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: JsonObject] }>()
const view = ref<'TREE' | 'TABLE'>('TREE')
const factCatalog = ref<CompletionFactCatalogVO[]>([])

const predicateLabels: Record<string, string> = {
  BUSINESS_FACT: '业务完成事实',
  TASK_NATIVE_STATUS: '任务自身状态与必填信息',
  STAGE_NATIVE_STATUS: '阶段自身状态',
  TASK: '关联任务结果',
  MILESTONE: '里程碑达成',
  DELIVERABLE: '必要交付物满足情况',
  STATE: '业务阶段完成',
  APPROVAL: '实际审批结果',
  PROCESS: '实际流程结果'
}
const predicates = Object.keys(predicateLabels)
const stateCodes = [
  'S0_COMPLETED',
  'S1_COMPLETED',
  'S2_COMPLETED',
  'S3_COMPLETED',
  'S4_COMPLETED',
  'S5_COMPLETED',
  'S6_COMPLETED'
]

const decisionRows = computed(() => decodeDecisionRows(props.modelValue) ?? [])
const tableCompatible = computed(() => decodeDecisionRows(props.modelValue) !== undefined)
const ruleSummary = computed(() => {
  const rows = decodeDecisionRows(props.modelValue)
  if (!rows) return '复杂嵌套规则'
  const conditions = rows.reduce((sum, row) => sum + row.conditions.length, 0)
  return `${rows.length} 个决策行 · ${conditions} 个条件`
})

const emitRule = (value: JsonObject) => emit('update:modelValue', value)
const editRows = () => cloneRule(decisionRows.value)
const updateRows = (rows: DecisionRow[]) => emitRule(encodeDecisionRows(rows))

const changePredicate = (rowIndex: number, conditionIndex: number, predicate: string) => {
  const rows = editRows()
  rows[rowIndex].conditions[conditionIndex] = {
    predicate,
    parameters:
      predicate === 'BUSINESS_FACT'
        ? { factCode: '', quantifier: 'ALL' }
        : predicate.endsWith('_NATIVE_STATUS')
          ? { requiredStatus: 'DONE' }
          : { refCode: '' }
  }
  updateRows(rows)
}
const setParameter = (rowIndex: number, conditionIndex: number, key: string, value: string) => {
  const rows = editRows()
  const condition = rows[rowIndex].conditions[conditionIndex]
  condition.parameters = { ...(condition.parameters ?? {}), [key]: value }
  updateRows(rows)
}
const addCondition = (rowIndex: number) => {
  const rows = editRows()
  rows[rowIndex].conditions.push(blankPredicate())
  updateRows(rows)
}
const removeCondition = (rowIndex: number, conditionIndex: number) => {
  const rows = editRows()
  if (rows[rowIndex].conditions.length <= 1) return
  rows[rowIndex].conditions.splice(conditionIndex, 1)
  updateRows(rows)
}
const addRow = () => updateRows([...editRows(), { conditions: [blankPredicate()] }])
const removeRow = (rowIndex: number) => {
  const rows = editRows()
  rows.splice(rowIndex, 1)
  updateRows(rows)
}

onMounted(async () => {
  try {
    factCatalog.value = await TemplateApi.getCompletionFactCatalog()
  } catch {
    factCatalog.value = []
  }
})
</script>

<style scoped>
.rule-decision-designer { width: 100%; }
.designer-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 10px; }
.designer-heading strong { font-size: 14px; }
.designer-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
.rule-tabs :deep(.el-tabs__header) { margin-bottom: 12px; }
.condition-list { display: flex; flex-direction: column; gap: 8px; }
.condition-row { display: grid; grid-template-columns: minmax(150px, 0.9fr) minmax(220px, 1.35fr) minmax(130px, 0.75fr) auto; gap: 8px; align-items: center; }
.condition-row > :deep(.el-select), .condition-row > :deep(.el-input) { width: 100%; }
.quantifier { min-width: 130px; }
@media (max-width: 900px) { .condition-row { grid-template-columns: 1fr; }.designer-heading { flex-wrap: wrap; } }
</style>