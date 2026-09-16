<template>
  <div class="widget-placeholder qrcode-display">
    <div :style="{ width: size + 'px', height: size + 'px', border: '1px solid #dcdfe6', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#909399' }">
      {{ model || '二维码占位' }}
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue'
const props = defineProps<{ modelValue: any; size?: number }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void }>()
const size = props.size ?? 128
const model = ref(props.modelValue)
</script>
