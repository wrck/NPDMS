<template>
  <div class="widget-placeholder address-picker" style="display: inline-flex; gap: 8px">
    <el-select v-model="province" placeholder="省" />
    <el-select v-model="city" placeholder="市" />
    <el-select v-model="district" placeholder="区" />
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
const props = defineProps<{ modelValue: any; level?: number }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void }>()
const province = ref('')
const city = ref('')
const district = ref('')
watch([province, city, district], () => {
  emit('update:modelValue', { province: province.value, city: city.value, district: district.value })
})
</script>
