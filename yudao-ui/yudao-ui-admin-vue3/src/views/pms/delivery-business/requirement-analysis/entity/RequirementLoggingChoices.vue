<template>
  <div class="requirement-logging-choices">
    <el-checkbox-group :model-value="selected" :disabled="disabled" @update:model-value="updateSelection">
      <el-checkbox v-for="option in options" :key="option" :label="option" :value="option" />
    </el-checkbox-group>
    <p v-if="legacyText" class="legacy-text">原有日志说明：{{ legacyText }}</p>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ modelValue?: string; disabled?: boolean; options: string[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const entries = computed(() => (props.modelValue || '').split('\n').filter(Boolean))
const selected = computed(() => entries.value.filter(value => props.options.includes(value)))
const legacyText = computed(() => entries.value.filter(value => !props.options.includes(value)).join('\n'))
const updateSelection = (values: (string | number | boolean)[]) => {
  if (props.disabled) return
  emit('update:modelValue', [...values.map(String), ...(legacyText.value ? [legacyText.value] : [])].join('\n'))
}
</script>
<style scoped>
.requirement-logging-choices { width: 100%; min-width: 0; }
.legacy-text { white-space: pre-wrap; overflow-wrap: anywhere; color: var(--el-text-color-secondary); }
</style>
