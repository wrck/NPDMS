<template>
  <div class="pms-file-uploader" v-hasPermi="['pms:file:upload']">
    <el-upload
      ref="uploadRef"
      :accept="accept"
      :auto-upload="false"
      :disabled="disabled || busy"
      :limit="1"
      :on-change="selectFile"
      :on-exceed="onExceed"
      :on-remove="removeFile"
      :show-file-list="true"
      drag
    >
      <Icon icon="ep:upload-filled" class="upload-icon" />
      <div class="el-upload__text">拖放文件到这里，或<em>点击选择</em></div>
      <template #tip>
        <div class="upload-tip">
          单文件不超过 50MB；上传完成后由服务端执行适用校验，是否执行安全扫描由部署配置决定。
        </div>
      </template>
    </el-upload>
    <el-progress v-if="busy" :percentage="progress" class="upload-progress" />
    <div v-if="busy" class="upload-state" role="status" aria-live="polite">
      {{ stage === 'UPLOADING' ? '正在上传文件' : '上传完成，服务端正在执行适用校验' }}
    </div>
    <el-button
      :disabled="!selectedFile || disabled"
      :loading="busy"
      type="primary"
      class="upload-submit"
      @click="submit"
    >
      {{ uploadMode === 'ADD_VERSION' ? '上传新版本' : '上传并绑定' }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import type { UploadFile, UploadInstance } from 'element-plus'
import { useMessage } from '@/hooks/web/useMessage'
import * as FileApi from '@/api/pms/platform/file'
import type { FileBusinessKey } from '@/api/pms/platform/file'
import type { FileSelection } from './types'
import { resolveFileUploadMode } from './useFileSlotState'

const props = withDefaults(
  defineProps<
    FileBusinessKey & {
      artifactId?: number
      expectedReferenceVersion?: number
      categoryCode: string
      accept?: string
      disabled?: boolean
    }
  >(),
  {
    accept: '.pdf,.png,.jpg,.jpeg',
    disabled: false
  }
)
const emit = defineEmits<{ completed: [selection: FileSelection] }>()
const message = useMessage()
const uploadRef = ref<UploadInstance>()
const selectedFile = ref<File>()
const busy = ref(false)
const progress = ref(0)
const stage = ref<'UPLOADING' | 'VALIDATING'>('UPLOADING')
const uploadMode = computed(() => resolveFileUploadMode(props.artifactId))
const attempt = ref<{
  initKey: string
  completeKey: string
  initialized?: FileApi.FileUploadInitRespVO
}>()

const businessKey = (): FileBusinessKey => ({
  ownerContext: props.ownerContext,
  objectType: props.objectType,
  objectId: props.objectId,
  purposeCode: props.purposeCode,
  referenceKey: props.referenceKey
})

const selectFile = (file: UploadFile) => {
  if (selectedFile.value !== file.raw) attempt.value = undefined
  selectedFile.value = file.raw
}
const removeFile = () => {
  selectedFile.value = undefined
  attempt.value = undefined
}
const onExceed = () => message.warning('每个材料槽位一次只能选择一个文件')

const submit = async () => {
  const file = selectedFile.value
  if (!file) return
  if (file.size <= 0 || file.size > 50 * 1024 * 1024) {
    return message.error('文件大小必须在 50MB 以内')
  }
  busy.value = true
  progress.value = 0
  stage.value = 'UPLOADING'
  try {
    attempt.value ||= { initKey: crypto.randomUUID(), completeKey: crypto.randomUUID() }
    const initialized =
      attempt.value.initialized ||
      (await FileApi.initializeUpload(
        {
          ...businessKey(),
          modeCode: uploadMode.value,
          artifactId: props.artifactId,
          expectedReferenceVersion: props.expectedReferenceVersion,
          fileName: file.name,
          categoryCode: props.categoryCode,
          declaredSizeBytes: file.size,
          declaredMediaType: file.type || 'application/octet-stream'
        },
        attempt.value.initKey
      ))
    attempt.value.initialized = initialized
    const completed = await FileApi.completeUpload(
      initialized.artifactId,
      initialized.sessionId,
      file,
      attempt.value.completeKey,
      (value) => {
        progress.value = value
        if (value >= 100) stage.value = 'VALIDATING'
      }
    )
    progress.value = 100
    message.success('文件已通过服务端校验并绑定')
    emit('completed', completed)
    selectedFile.value = undefined
    attempt.value = undefined
    uploadRef.value?.clearFiles()
  } finally {
    busy.value = false
  }
}
</script>

<style scoped lang="scss">
.pms-file-uploader {
  width: 100%;
}

.upload-icon {
  font-size: 32px;
  color: var(--el-color-primary);
}

.upload-tip,
.upload-state {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.upload-progress,
.upload-state,
.upload-submit {
  margin-top: 12px;
}

@media (width <= 767px) {
  .upload-submit {
    width: 100%;
  }
}
</style>
