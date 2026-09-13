<template>
  <section class="rule-decision-designer">
    <p class="rule-description"
      >条件树为默认表达；需要时添加“决策表输出”条件，使用原生决策表独立维护，不强制互转。</p
    >
    <RuleEditor
      :model-value="modelValue"
      :rules="rules"
      :disabled="disabled"
      @update:model-value="emit('update:modelValue', $event)"
    />
  </section>
</template>
<script setup lang="ts">
import type { JsonObject } from '@/api/pms/project/project-templates'
import type { VersionRule } from '@/api/pms/project/project-templates/rules'
import RuleEditor from './RuleEditor.vue'
defineProps<{ modelValue: JsonObject; rules?: VersionRule[]; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: JsonObject] }>()
</script>
<style scoped>
.rule-description {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
</style>
