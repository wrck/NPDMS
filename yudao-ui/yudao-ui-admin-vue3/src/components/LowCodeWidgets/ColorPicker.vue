<template>
  <div class="widget-placeholder color-picker">
    <el-color-picker v-model="model" :show-alpha="showAlpha" />
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
const props = defineProps<{ modelValue: any; showAlpha?: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void }>()
const model = ref(props.modelValue)
const showAlpha = props.showAlpha ?? true
watch(model, (v) => emit('update:modelValue', v))
</script>
