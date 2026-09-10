<template>
  <section>
    <el-alert title="仅维护一组显式转移关系；前置与后置视图立即同步。排序不生成关系。默认分支仅在其他分支均未命中时使用，未知条件不会按默认放行。" type="info" :closable="false" class="mb-12px" />
    <div class="relation-toolbar">
      <el-select v-model="stage" clearable placeholder="全部阶段"><el-option v-for="node in content.stages" :key="node.stageCode" :value="node.stageCode" :label="`${node.stageCode} ${node.name}`" /></el-select>
      <el-radio-group v-model="direction"><el-radio-button value="from">后置关系</el-radio-button><el-radio-button value="to">前置关系</el-radio-button></el-radio-group>
      <el-button v-if="!readonly" @click="addRelation(content, stage, direction)">新增关系</el-button>
      <el-button link type="primary" @click="graphVisible = !graphVisible">{{ graphVisible ? '收起图预览' : '图预览' }}</el-button>
    </div>
    <StageGraphPreview v-if="graphVisible" :content="content" />
    <el-alert v-if="content.transitions == null" title="此内容未提供关系图，保留原样可读。新增关系仅由显式操作产生。" type="warning" :closable="false" />
    <el-table :data="visibleEdges" border empty-text="未配置关系（不会自动补图）">
      <el-table-column label="关系编码" min-width="155"><template #default="{ row }"><el-input v-model="row.transitionCode" :disabled="readonly" /></template></el-table-column>
      <el-table-column v-for="field in endpoints" :key="field.key" :label="field.label" min-width="160"><template #default="{ row }">
        <el-select v-model="row[field.key]" :disabled="readonly"><el-option v-for="node in content.stages" :key="node.stageCode" :value="node.stageCode" :label="`${node.stageCode} ${node.name}`" /></el-select>
      </template></el-table-column>
      <el-table-column label="条件规则（精确修订）" min-width="240"><template #default="{ row }"><DefinitionSelect v-model="row.conditionRuleRevisionId" kind="COMPLETION_RULE" :disabled="readonly" /></template></el-table-column>
      <el-table-column label="优先级（小者优先）" width="155"><template #default="{ row }"><el-input-number v-model="row.priority" :disabled="readonly" :precision="0" controls-position="right" class="!w-full" /></template></el-table-column>
      <el-table-column label="默认" width="80"><template #default="{ row }"><el-checkbox v-model="row.default" :disabled="readonly" /></template></el-table-column>
      <el-table-column label="关系版本" width="120"><template #default="{ row }"><el-input-number v-model="row.revisionNo" :min="1" :precision="0" :disabled="readonly" controls-position="right" class="!w-full" /></template></el-table-column>
      <el-table-column v-if="!readonly" label="操作" width="70"><template #default="{ row }"><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table>
    <el-alert v-if="issues.length" type="warning" :closable="false" class="mt-12px" title="结构预检（不替代服务端发布校验）">
      <ul><li v-for="(issue, index) in issues" :key="index">{{ issue.field }} · {{ issue.message }}</li></ul>
    </el-alert>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import type { StageTransition, TemplateDefinitionContent } from '@/api/pms/project/project-templates'
import DefinitionSelect from './DefinitionSelect.vue'
import StageGraphPreview from './StageGraphPreview.vue'
import { addRelation, graphIssues, relationsFor } from './editorModel'
const props = defineProps<{ content: TemplateDefinitionContent; readonly?: boolean }>()
const stage = ref('')
const direction = ref<'from' | 'to'>('from')
const graphVisible = ref(true)
const endpoints = [{ key: 'fromStageCode', label: '来源阶段' }, { key: 'toStageCode', label: '目标阶段' }] as const
const visibleEdges = computed(() => stage.value ? relationsFor(props.content, stage.value, direction.value) : (props.content.transitions ?? []))
const issues = computed(() => graphIssues(props.content))
const remove = (row: StageTransition) => {
  const index = props.content.transitions?.indexOf(row) ?? -1
  if (index >= 0) props.content.transitions!.splice(index, 1)
}
</script>
<style scoped>
.relation-toolbar { display: flex; flex-wrap: wrap; gap: 12px; margin: 12px 0; }
.relation-toolbar :deep(.el-select) { width: 240px; }
ul { margin: 6px 0; padding-left: 18px; }
</style>
