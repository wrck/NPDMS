<template>
  <section>
    <el-alert title="只维护 DesignerDocument 中的一组显式转移关系。条件规则直接保存在关系上；不创建独立规则修订，也不根据排序补图。" type="info" :closable="false" class="mb-12px" />
    <div class="relation-toolbar">
      <el-select v-model="stage" clearable placeholder="全部阶段">
        <el-option v-for="node in content.stages" :key="node.nodeKey" :value="node.code" :label="`${node.code} ${node.name}`" />
      </el-select>
      <el-radio-group v-model="direction"><el-radio-button value="from">后置关系</el-radio-button><el-radio-button value="to">前置关系</el-radio-button></el-radio-group>
      <el-button v-if="!readonly" @click="addRelation(content, stage, direction)">新增关系</el-button>
      <el-button link type="primary" @click="graphVisible = !graphVisible">{{ graphVisible ? '收起可视化编辑' : '可视化编辑' }}</el-button>
    </div>
    <StageGraphDesigner v-if="graphVisible" :content="content" :readonly="readonly" />

    <el-table :data="visibleEdges" border empty-text="未配置关系（不会自动补图）" @current-change="selected = $event">
      <el-table-column label="关系编码" min-width="155"><template #default="{ row }"><el-input v-model="row.code" :disabled="readonly" /></template></el-table-column>
      <el-table-column v-for="field in endpoints" :key="field.key" :label="field.label" min-width="160"><template #default="{ row }">
        <el-select v-model="row[field.key]" :disabled="readonly"><el-option v-for="node in content.stages" :key="node.nodeKey" :value="node.code" :label="`${node.code} ${node.name}`" /></el-select>
      </template></el-table-column>
      <el-table-column label="条件" width="110"><template #default="{ row }">
        <el-tag v-if="row.defaultBranch" type="info">默认</el-tag>
        <el-tag v-else-if="row.condition" type="success">已配置</el-tag>
        <el-tag v-else type="info" effect="plain">无条件</el-tag>
      </template></el-table-column>
      <el-table-column label="优先级" width="130"><template #default="{ row }"><el-input-number v-model="row.priority" :disabled="readonly" :precision="0" controls-position="right" class="!w-full" /></template></el-table-column>
      <el-table-column label="默认" width="80"><template #default="{ row }"><el-checkbox :model-value="row.defaultBranch" :disabled="readonly" @update:model-value="setDefault(row, $event)" /></template></el-table-column>
      <el-table-column label="编辑规则" width="100"><template #default="{ row }"><el-button link type="primary" @click="selected = row">{{ row.condition ? '编辑' : '配置' }}</el-button></template></el-table-column>
      <el-table-column v-if="!readonly" label="操作" width="70"><template #default="{ row }"><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table>

    <section v-if="selected && !selected.defaultBranch" class="condition-editor">
      <div class="condition-heading">
        <div><strong>{{ selected.fromStageCode }} → {{ selected.toStageCode }} 条件</strong><p>条件直接进入发布快照；未配置表示普通无条件分支。</p></div>
        <el-button v-if="!readonly && selected.condition" link type="danger" @click="selected.condition = undefined">清除条件</el-button>
      </div>
      <el-button v-if="!selected.condition && !readonly" @click="createCondition(selected)">添加条件规则</el-button>
      <RuleDecisionDesigner
        v-if="selected.condition"
        :model-value="selected.condition.expression"
        :disabled="readonly"
        @update:model-value="selected!.condition!.expression = $event"
      />
    </section>

    <el-alert v-if="issues.length" type="warning" :closable="false" class="mt-12px" title="结构预检（不替代服务端 Compiler 校验）">
      <ul><li v-for="(issue, index) in issues" :key="index">{{ issue.field }} · {{ issue.message }}</li></ul>
    </el-alert>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import type { DesignerTransitionNode, TemplateDesignerDocument } from '@/api/pms/project/project-templates'
import RuleDecisionDesigner from './RuleDecisionDesigner.vue'
import StageGraphDesigner from './StageGraphDesigner.vue'
import { addRelation, graphIssues, relationsFor } from './editorModel'

const props = defineProps<{ content: TemplateDesignerDocument; readonly?: boolean }>()
const stage = ref('')
const direction = ref<'from' | 'to'>('from')
const graphVisible = ref(true)
const selected = ref<DesignerTransitionNode>()
const endpoints = [{ key: 'fromStageCode', label: '来源阶段' }, { key: 'toStageCode', label: '目标阶段' }] as const
const visibleEdges = computed(() => stage.value ? relationsFor(props.content, stage.value, direction.value) : props.content.transitions)
const issues = computed(() => graphIssues(props.content))
const createCondition = (row: DesignerTransitionNode) => {
  row.condition = { expression: { predicate: 'TASK', parameters: { refCode: '' } } }
}
const setDefault = (row: DesignerTransitionNode, value: boolean) => {
  if (value) {
    const conflict = props.content.transitions.find((edge) => edge !== row && edge.fromStageCode === row.fromStageCode && edge.defaultBranch)
    if (conflict) return
    row.condition = undefined
  }
  row.defaultBranch = value
}
const remove = (row: DesignerTransitionNode) => {
  const index = props.content.transitions.indexOf(row)
  if (index >= 0) props.content.transitions.splice(index, 1)
  if (selected.value === row) selected.value = undefined
}
</script>
<style scoped>
.relation-toolbar { display: flex; flex-wrap: wrap; gap: 12px; margin: 12px 0; }
.relation-toolbar :deep(.el-select) { width: 240px; }
.condition-editor { margin-top: 16px; padding: 16px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; }
.condition-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.condition-heading p { margin: 5px 0 0; color: var(--el-text-color-secondary); font-size: 12px; }
ul { margin: 6px 0; padding-left: 18px; }
</style>