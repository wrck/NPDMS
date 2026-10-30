<template>
  <div class="cutover-schema-editor">
    <CutoverSchemaNodeEditor
      :model-value="modelValue"
      :disabled="disabled"
      @update:model-value="emit('update:modelValue', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import CutoverSchemaNodeEditor from './CutoverSchemaNodeEditor.vue'

defineProps<{ modelValue: Record<string, unknown>; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()
</script>

<style scoped>
.cutover-schema-editor { width: 100%; min-width: 0; }
</style>
