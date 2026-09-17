<script setup lang="ts">
import { ElButton, ElUpload } from 'element-plus'
import type { FormFieldConfig } from '@/api/lowcode'

/** 保留 V1 上传按钮、提示和非 v-model 语义；不改文件接口或鉴权策略。 */
defineOptions({ inheritAttrs: false })
defineProps<{ field: FormFieldConfig; disabled?: boolean }>()
</script>

<template>
  <ElUpload
    :action="(field.props?.action as string) || '/api/file/upload'"
    :limit="(field.props?.limit as number) || 5"
    :accept="(field.props?.accept as string) || ''"
    :multiple="(field.props?.multiple as boolean) ?? false"
    :list-type="(field.props?.listType as 'text' | 'picture' | 'picture-card') || 'text'"
    :disabled="disabled"
  >
    <ElButton type="primary" :disabled="disabled">点击上传</ElButton>
    <template v-if="field.props?.tip" #tip>
      <div class="el-upload__tip">{{ field.props.tip }}</div>
    </template>
  </ElUpload>
</template>
