<template>
  <section class="rule-group" :aria-label="group.not ? '不满足条件组' : '条件组'">
    <header class="group-heading">
      <el-select
        :model-value="group.combinator"
        :disabled="disabled"
        aria-label="条件组合"
        @update:model-value="emit('group-change', String(group.id), { combinator: $event })"
      >
        <el-option value="and" label="全部满足" /><el-option value="or" label="任一满足" />
      </el-select>
      <el-checkbox
        :model-value="!!group.not"
        :disabled="disabled"
        @update:model-value="emit('group-change', String(group.id), { not: !!$event })"
        >取反</el-checkbox
      >
      <template v-if="!disabled">
        <el-button link @click="emit('append', String(group.id), false)">添加条件</el-button>
        <el-button link @click="emit('append', String(group.id), true)">添加条件组</el-button>
      </template>
    </header>
    <Draggable
      :model-value="group.rules"
      item-key="id"
      group="pms-template-rule-tree"
      handle=".rule-handle"
      :disabled="disabled"
      :move="allowMove"
      :data-group-id="group.id"
      class="rule-items"
      @end="finishDrag"
    >
      <template #item="{ element }">
        <article class="rule-item" :data-rule-id="element.id">
          <button v-if="!disabled" type="button" class="rule-handle" aria-label="拖动条件或条件组"
            >⠿</button
          >
          <RuleTreeGroup
            v-if="isGroup(element)"
            :group="element"
            :tree="tree"
            :fields="fields"
            :facts="facts"
            :rules="rules"
            :disabled="disabled"
            @group-change="(id, value) => emit('group-change', id, value)"
            @append="(id, grouped) => emit('append', id, grouped)"
            @remove="(id) => emit('remove', id)"
            @predicate-change="
              (id, predicate, value) => emit('predicate-change', id, predicate, value)
            "
            @reorder="(id, parent, index) => emit('reorder', id, parent, index)"
          />
          <RulePredicateEditor
            v-else
            :predicate="element.field"
            :parameters="element.value"
            :fields="fields"
            :facts="facts"
            :rules="rules"
            :disabled="disabled"
            @change="(predicate, value) => emit('predicate-change', element.id, predicate, value)"
          />
          <el-button
            v-if="!disabled"
            link
            type="danger"
            aria-label="删除此条件或条件组"
            @click="emit('remove', element.id)"
            >删除</el-button
          >
        </article>
      </template>
    </Draggable>
    <p v-if="!group.rules.length" class="empty-group">添加条件后才能发布；空条件组不会自动放行。</p>
  </section>
</template>

<script setup lang="ts">
import Draggable from 'vuedraggable'
import type { RuleGroupType } from '@react-querybuilder/core'
import type { JsonObject, CompletionFactCatalogVO } from '@/api/pms/project/project-templates'
import type { RuleField, VersionRule } from '@/api/pms/project/project-templates/rules'
import { canMoveNode, isGroup } from './ruleTreeModel'
import RulePredicateEditor from './RulePredicateEditor.vue'
defineOptions({ name: 'RuleTreeGroup' })
const props = defineProps<{
  group: RuleGroupType
  tree: RuleGroupType
  fields: RuleField[]
  facts: CompletionFactCatalogVO[]
  rules?: VersionRule[]
  disabled?: boolean
}>()
const emit = defineEmits<{
  'group-change': [id: string, value: { combinator?: string; not?: boolean }]
  append: [id: string, grouped: boolean]
  remove: [id: string]
  'predicate-change': [id: string, predicate: string, value: JsonObject]
  reorder: [id: string, parent: string, index: number]
}>()
interface DragEvent {
  item: HTMLElement
  to: HTMLElement
  newIndex?: number
}
const finishDrag = (event: DragEvent) => {
  const id = event.item.dataset.ruleId
  const parent = event.to.dataset.groupId
  if (id && parent && event.newIndex != null) emit('reorder', id, parent, event.newIndex)
}
const allowMove = (event: { draggedContext: { element: { id?: string } }; to: HTMLElement }) => {
  const id = event.draggedContext.element.id
  const parent = event.to.dataset.groupId
  return !!id && !!parent && canMoveNode(props.tree, id, parent)
}
</script>

<style scoped>
.rule-group {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  padding: 12px;
  flex: 1;
  min-width: 0;
}
.group-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.group-heading :deep(.el-select) {
  width: 128px;
}
.rule-items {
  min-height: 40px;
}
.rule-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 0;
}
.rule-handle {
  border: 0;
  background: none;
  color: var(--el-text-color-secondary);
  cursor: grab;
  padding: 6px;
  font-size: 20px;
}
.rule-handle:focus-visible {
  outline: 2px solid var(--el-color-primary);
}
.empty-group {
  margin: 8px 0;
  color: var(--el-color-warning);
  font-size: 12px;
}
</style>
