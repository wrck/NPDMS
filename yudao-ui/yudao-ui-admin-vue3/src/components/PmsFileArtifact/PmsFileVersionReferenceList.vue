<template>
  <div class="reference-list" :aria-busy="loading">
    <div v-if="loading && !artifact" class="reference-card reference-placeholder" role="status" aria-label="正在加载材料">
      <div class="file-main">
        <div class="file-name" aria-hidden="true">正在加载材料</div>
        <div class="file-meta" aria-hidden="true"><el-tag size="small">加载中</el-tag><span>版本</span></div>
      </div>
      <div class="file-actions" aria-hidden="true">
        <el-button link type="primary" disabled>版本历史</el-button>
        <el-button link type="primary" disabled>预览</el-button>
        <el-button link type="primary" disabled>下载</el-button>
      </div>
    </div>
    <el-empty v-else-if="!artifact" description="尚未绑定材料" :image-size="56" />
    <div v-else class="reference-card" :inert="loading">
      <div class="file-main">
        <div class="file-name" :title="artifact.name">{{ artifact.name }}</div>
        <div class="file-meta">
          <el-tag size="small" :type="artifact.reference.status === 'ACTIVE' ? 'success' : 'info'">
            {{ statusLabel(artifact.reference.status) }}
          </el-tag>
          <span>V{{ props.versionNo || artifact.reference.versionNo }}</span>
        </div>
      </div>
      <div class="file-actions">
        <el-button link type="primary" @click="historyRef?.open(artifact.artifactId, businessKey)"
          >版本历史</el-button
        >
        <el-button
          v-if="artifact.allowedActions.includes('PREVIEW')"
          :disabled="downloading"
          link
          type="primary"
          @click="openAccess('PREVIEW')"
          >预览</el-button
        >
        <el-button
          v-if="artifact.allowedActions.includes('DOWNLOAD')"
          :loading="downloading"
          link
          type="primary"
          @click="openAccess('DOWNLOAD')"
          >下载</el-button
        >
      </div>
    </div>
    <div v-if="errorText" class="error-state">
      <el-alert :title="errorText" type="error" show-icon :closable="false" />
      <el-button link type="primary" @click="load">重新加载</el-button>
    </div>
  </div>
  <PmsFileVersionReferenceHistory ref="historyRef" />
</template>

<script setup lang="ts">
import { useMessage } from '@/hooks/web/useMessage'
import download from '@/utils/download'
import * as FileApi from '@/api/pms/platform/file/version-reference'
import type { FileArtifactVO } from '@/api/pms/platform/file/version-reference'
import type { FileAccessOperation, FileBusinessKey } from '@/api/pms/platform/file'
import PmsFileVersionReferenceHistory from './PmsFileVersionReferenceHistory.vue'

const props = defineProps<
    FileBusinessKey & {
      artifactId?: string | number
      versionNo?: number
    }
>()
const emit = defineEmits<{
  loaded: [artifact: FileArtifactVO]
}>()
const message = useMessage()
const loading = ref(false)
const artifact = ref<FileArtifactVO>()
const errorText = ref('')
const downloading = ref(false)
const historyRef = ref<InstanceType<typeof PmsFileVersionReferenceHistory>>()
const businessKey = computed<FileBusinessKey>(() => ({
  ownerContext: props.ownerContext,
  objectType: props.objectType,
  objectId: props.objectId,
  purposeCode: props.purposeCode,
  referenceKey: props.referenceKey
}))

let loadGeneration = 0
let loadedIdentity = ''
const load = async () => {
  const generation = ++loadGeneration
  const identity = JSON.stringify([props.artifactId, businessKey.value])
  if (identity !== loadedIdentity) artifact.value = undefined
  errorText.value = ''
  if (!props.artifactId) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const current = await FileApi.getArtifact(props.artifactId, businessKey.value)
    if (generation !== loadGeneration) return
    loadedIdentity = identity
    artifact.value = current
    emit('loaded', artifact.value)
  } catch {
    if (generation !== loadGeneration) return
    errorText.value = '文件事实已变化，请刷新业务页面后重试。'
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}
const openAccess = async (operation: FileAccessOperation) => {
  if (!artifact.value || downloading.value) return
  const selected = artifact.value
  const target = operation === 'PREVIEW' ? window.open('about:blank', '_blank') : null
  if (operation === 'PREVIEW' && !target) return message.warning('浏览器已阻止新窗口，请允许弹窗后重试')
  if (target) target.opener = null
  downloading.value = operation === 'DOWNLOAD'
  errorText.value = ''
  try {
    const ticket = await FileApi.createAccessTicket(
      selected.artifactId,
      props.versionNo || selected.reference.versionNo,
      operation,
      businessKey.value
    )
    if (operation === 'DOWNLOAD') {
      // A cross-origin navigation may display the file inline. Download the authorized
      // bytes via the existing Blob helper without forwarding application credentials.
      // https://developer.mozilla.org/en-US/docs/Web/API/Response/blob
      const response = await fetch(ticket.shortLivedUrl, { credentials: 'omit' })
      if (!response.ok) throw new Error('FILE_DOWNLOAD_FAILED')
      download.file(await response.blob(), selected.name)
    } else {
      target!.location.replace(ticket.shortLivedUrl)
    }
  } catch {
    target?.close()
    errorText.value = operation === 'DOWNLOAD'
      ? '文件下载未完成，请检查权限、存储连接及跨域配置后重试。'
      : '文件预览未完成，请刷新文件信息后重试。'
  } finally {
    downloading.value = false
  }
}
const statusLabel = (status: string) =>
  ({ ACTIVE: '已绑定', DETACHED: '已解绑', ARCHIVED: '已归档' })[status] || status

watch(
  () => [props.artifactId, props.versionNo, props.objectId, props.referenceKey],
  () => {
    load()
  },
  { immediate: true }
)
defineExpose({ refresh: load })
</script>

<style scoped lang="scss">
.reference-card,
.file-meta,
.file-actions {
  display: flex;
  align-items: center;
}

.reference-card {
  padding: 12px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  justify-content: space-between;
  gap: 12px;
}

.reference-placeholder {
  pointer-events: none;
  background: var(--el-fill-color-light);
}
.reference-placeholder > * {
  visibility: hidden;
}

.file-main {
  min-width: 0;
}

.file-name {
  overflow: hidden;
  font-weight: 500;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta,
.file-actions {
  gap: 8px;
}

.error-state {
  text-align: right;
}

.file-meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (width <= 767px) {
  .reference-card {
    align-items: stretch;
    flex-direction: column;
  }

  .file-name {
    white-space: nowrap;
  }

  .file-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
