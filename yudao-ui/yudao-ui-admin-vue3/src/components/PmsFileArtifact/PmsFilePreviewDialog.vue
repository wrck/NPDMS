<template>
  <el-dialog
    v-if="visible"
    :model-value="visible"
    :title="title"
    width="min(1200px, calc(100vw - 32px))"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @update:model-value="close"
    @close="close"
  >
    <div class="file-preview" :aria-busy="loading">
      <div v-if="loading" class="preview-state" role="status">正在加载预览…</div>
      <div v-else-if="errorText" class="preview-state" role="alert">
        <p>{{ errorText }}</p>
        <el-button type="primary" @click="loadPreview">重试预览</el-button>
      </div>
      <PmsPdfPreview v-else-if="pdfContent" :file="pdfContent" @error="previewFailed" />
      <div v-else-if="kind === 'image'" class="preview-image">
        <img :src="contentUrl" :alt="title" @error="previewFailed" />
      </div>
      <pre v-else-if="kind === 'text'" class="preview-text">{{ textContent }}</pre>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import PmsPdfPreview from './PmsPdfPreview.vue'

type PreviewRequest = { title: string; loadContent: () => Promise<Blob> }

const visible = ref(false)
const title = ref('文件预览')
const loading = ref(false)
const errorText = ref('')
const contentUrl = ref('')
const textContent = ref('')
const pdfContent = shallowRef<Blob>()
const kind = ref<'' | 'image' | 'text'>('')
let request: PreviewRequest | undefined
let generation = 0

const releaseContent = () => {
  if (contentUrl.value) URL.revokeObjectURL(contentUrl.value)
  contentUrl.value = ''
  pdfContent.value = undefined
  textContent.value = ''
  kind.value = ''
}
const close = () => {
  visible.value = false
  generation++
  request = undefined
  loading.value = false
  releaseContent()
}
const previewFailed = () => {
  releaseContent()
  errorText.value = '文件预览未完成，请重试或刷新文件信息。'
}
const loadPreview = async () => {
  if (!request || loading.value) return
  const current = ++generation
  const selected = request
  loading.value = true
  errorText.value = ''
  releaseContent()
  try {
    const blob = await selected.loadContent()
    if (current !== generation) return
    const mediaType = blob.type.toLowerCase().split(';', 1)[0].trim()
    if (mediaType === 'text/plain') {
      const text = await blob.text()
      if (current !== generation) return
      textContent.value = text
      kind.value = 'text'
    } else if (mediaType === 'application/pdf') {
      pdfContent.value = blob
    } else if (['image/png', 'image/jpeg', 'image/gif', 'image/webp'].includes(mediaType)) {
      contentUrl.value = URL.createObjectURL(blob)
      kind.value = 'image'
    } else {
      errorText.value = '此文件格式暂不支持在线预览。'
    }
  } catch {
    if (current === generation) errorText.value = '文件预览未完成，请重试或刷新文件信息。'
  } finally {
    if (current === generation) loading.value = false
  }
}
const open = async (selected: PreviewRequest) => {
  close()
  request = selected
  title.value = selected.title
  visible.value = true
  await loadPreview()
}

onBeforeUnmount(close)
defineExpose({ open, close })
</script>

<style scoped lang="scss">
.file-preview {
  height: 78vh;
  min-height: 160px;
  overflow: hidden;
  background: var(--el-fill-color-light);
}

.preview-state {
  display: flex;
  height: 100%;
  padding: 16px;
  color: var(--el-text-color-secondary);
  text-align: center;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.preview-image {
  display: flex;
  height: 100%;
  overflow: auto;
  align-items: center;
  justify-content: center;

  img {
    max-width: 100%;
    max-height: 100%;
    object-fit: contain;
  }
}

.preview-text {
  height: 100%;
  padding: 16px;
  margin: 0;
  overflow: auto;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
