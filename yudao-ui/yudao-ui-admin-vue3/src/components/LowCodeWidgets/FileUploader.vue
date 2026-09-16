<template>
  <el-upload :action="action" :accept="accept" :limit="1" :on-success="onSuccess">
    <el-button type="primary">点击上传</el-button>
  </el-upload>
</template>
<script setup lang="ts">
import { ElMessage } from 'element-plus'

defineProps<{ modelValue: any; accept?: string; maxSize?: number }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void }>()
const action = '/api/file/upload'

function onSuccess(resp: any) {
  if (resp?.data?.id) {
    emit('update:modelValue', resp.data.id)
    ElMessage.success('上传成功')
  }
}
</script>
