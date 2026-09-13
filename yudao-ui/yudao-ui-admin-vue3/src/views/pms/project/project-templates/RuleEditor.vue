<template>
  <section aria-label="条件树编辑器">
    <el-alert v-if="failure" :title="failure" type="warning" :closable="false" class="mb-12px" />
    <RuleTreeGroup
      :group="tree"
      :tree="tree"
      :fields="fields"
      :facts="facts"
      :rules="rules"
      :disabled="disabled"
      @group-change="(id, value) => change(changeGroup(tree, id, value))"
      @append="(id, grouped) => change(appendNode(tree, id, grouped))"
      @remove="(id) => change(removeNode(tree, id))"
      @predicate-change="
        (id, predicate, parameters) => change(changePredicate(tree, id, predicate, parameters))
      "
      @reorder="(id, parent, index) => change(moveNode(tree, id, parent, index))"
    />
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import type { RuleGroupType } from '@react-querybuilder/core'
import type { JsonObject, CompletionFactCatalogVO } from '@/api/pms/project/project-templates'
import { getCompletionFactCatalog } from '@/api/pms/project/project-templates'
import {
  getRuleFields,
  type RuleField,
  type VersionRule
} from '@/api/pms/project/project-templates/rules'
import RuleTreeGroup from './RuleTreeGroup.vue'
import {
  appendNode,
  changeGroup,
  changePredicate,
  decodeTree,
  encodeTree,
  moveNode,
  removeNode
} from './ruleTreeModel'
const props = defineProps<{ modelValue: JsonObject; rules?: VersionRule[]; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: JsonObject] }>()
const tree = ref<RuleGroupType>(decodeTree(props.modelValue))
const fields = ref<RuleField[]>([])
const facts = ref<CompletionFactCatalogVO[]>([])
const failure = ref('')
watch(
  () => props.modelValue,
  (value) => {
    tree.value = decodeTree(value)
  },
  { deep: true }
)
const change = (value: RuleGroupType) => {
  emit('update:modelValue', encodeTree(value))
}
onMounted(async () => {
  if (props.disabled) return
  const results = await Promise.allSettled([getRuleFields(), getCompletionFactCatalog()])
  if (results[0].status === 'fulfilled') fields.value = results[0].value
  if (results[1].status === 'fulfilled') facts.value = results[1].value
  if (results.some((result) => result.status === 'rejected'))
    failure.value = '部分字段目录暂不可用，现有条件已保留；请恢复目录后选择新字段。'
})
</script>
