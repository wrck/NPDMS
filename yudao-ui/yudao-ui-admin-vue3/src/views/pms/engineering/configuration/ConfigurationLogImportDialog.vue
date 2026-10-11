<template>
  <el-dialog v-model="visible" title="上传配置日志" width="min(640px, 95vw)" :close-on-click-modal="!uploading" :show-close="!uploading">
    <el-form label-width="90px">
      <el-form-item v-if="!props.projectId" label="项目">
        <PmsEntitySelect v-model="projectId" :api="ProjectApi.getProjectPage" label-field="projectName"
          value-field="id" query-field="projectName" :disabled="uploading" placeholder="请选择项目" />
      </el-form-item>
      <el-form-item label="配置日志">
        <el-upload :show-file-list="false" accept=".txt,.log,.cfg,.conf" :disabled="!projectId || uploading"
          :http-request="upload" :before-upload="checkFile">
          <el-button type="primary" :disabled="!projectId" :loading="uploading">选择并上传日志</el-button>
        </el-upload>
      </el-form-item>
    </el-form>
    <el-alert title="按日志中的设备序列号自动拆分并关联当前项目设备。支持UTF-8文本，最大20 MiB；上传只形成配置记录，调试完成另行确认。"
      type="info" :closable="false" />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { UploadRequestOptions } from 'element-plus'
import PmsEntitySelect from '@/components/PmsEntitySelect/index.vue'
import * as ProjectApi from '@/api/pms/project/projects'
import { importConfigurationLog } from '@/api/pms/engineering/configuration'
import { useMessage } from '@/hooks/web/useMessage'
import { generateUUID } from '@/utils'

const props = defineProps<{ projectId?: number }>()
const emit = defineEmits<{ (e: 'saved'): void }>()
const message = useMessage()
const visible = ref(false)
const uploading = ref(false)
const projectId = ref<number>()
// A retry of the same selected file keeps its key; a different file is a new import.
let selectedFile: File | undefined
let operationId = ''
const open = () => {
  projectId.value = props.projectId
  selectedFile = undefined
  operationId = ''
  visible.value = true
}
const checkFile = (file: File) => {
  if (!file.size || file.size > 20 * 1024 * 1024) {
    message.error('日志文件须为1字节至20 MiB')
    return false
  }
  if (!/\.(txt|log|cfg|conf)$/i.test(file.name)) {
    message.error('请选择txt、log、cfg或conf日志')
    return false
  }
  return true
}
const upload = async (options: UploadRequestOptions) => {
  if (!projectId.value || uploading.value) return
  if (selectedFile !== options.file) {
    selectedFile = options.file
    operationId = generateUUID()
  }
  uploading.value = true
  try {
    const result = await importConfigurationLog(projectId.value, operationId, options.file)
    message.success(`已形成${result.configurationIds.length}条配置记录`)
    emit('saved')
    visible.value = false
  } finally {
    uploading.value = false
  }
}
defineExpose({ open })
</script>
