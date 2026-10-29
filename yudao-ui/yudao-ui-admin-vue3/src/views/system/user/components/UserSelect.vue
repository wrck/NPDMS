<template>
  <SystemUserSelect
    v-bind="$attrs"
    :model-value="modelValue"
    presentation="dropdown"
    @update:model-value="emit('update:modelValue', $event)"
    @change="emit('change', $event)"
    @loaded="emit('loaded', $event)"
    ><template #prefix-options><slot name="prefix-options"></slot></template
  ></SystemUserSelect>
</template>
<script
  setup
  lang="ts"
  generic="T extends string | number | (string | number)[] = string | number | (string | number)[]"
>
import SystemUserSelect from '@/components/SystemUserSelect/index.vue'
defineOptions({ name: 'UserSelect', inheritAttrs: false })
defineProps<{ modelValue?: T }>()
const emit = defineEmits<{
  'update:modelValue': [value: T | undefined]
  change: [value: any]
  loaded: [value: any[]]
}>()
</script>
