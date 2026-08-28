<template>
  <el-drawer v-model="visible" title="文件版本历史" :size="drawerSize" destroy-on-close>
    <el-skeleton v-if="loading && !items.length" :rows="4" animated />
    <el-empty v-else-if="!items.length" description="暂无可见版本" />
    <template v-else>
      <el-table v-if="!narrow" :data="items">
        <el-table-column label="版本" prop="versionNo" width="80" />
        <el-table-column label="状态" min-width="150">
          <template #default="scope">
            <el-tag :type="scope.row.availabilityStatus === 'AVAILABLE' ? 'success' : 'warning'">
              {{ availabilityLabel(scope.row.availabilityStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="安全扫描" min-width="230">
          <template #default="scope">
            <el-tag :type="scanStatusTagType(scope.row.scanStatus)">
              {{ scanStatusLabel(scope.row.scanStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" min-width="110">
          <template #default="scope">{{ formatSize(scope.row.sizeBytes) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createdAt" min-width="180" />
      </el-table>
      <div v-else class="version-list">
        <article v-for="item in items" :key="item.id" class="version-card">
          <div
            ><strong>V{{ item.versionNo }}</strong
            ><el-tag size="small">{{ availabilityLabel(item.availabilityStatus) }}</el-tag></div
          >
          <span>{{ formatSize(item.sizeBytes) }} · {{ item.createdAt }}</span>
          <span class="scan-fact">{{ scanStatusLabel(item.scanStatus) }}</span>
        </article>
      </div>
      <el-button v-if="hasMore" :loading="loading" class="load-more" @click="loadMore"
        >加载更多</el-button
      >
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import { computed, ref } from 'vue'
import * as FileApi from '@/api/pms/platform/file'
import type { FileBusinessKey, FileVersionVO } from '@/api/pms/platform/file'

const narrow = useMediaQuery('(max-width: 767px)')
const drawerSize = computed(() => (narrow.value ? '100%' : '720px'))
const visible = ref(false)
const loading = ref(false)
const artifactId = ref<number>()
const key = ref<FileBusinessKey>()
const items = ref<FileVersionVO[]>([])
const cursor = ref<string>()
const hasMore = ref(false)

const load = async (append = false) => {
  if (!artifactId.value || !key.value) return
  loading.value = true
  try {
    const page = await FileApi.getVersions(artifactId.value, {
      ...key.value,
      cursor: append ? cursor.value : undefined,
      pageSize: 20
    })
    items.value = append ? [...items.value, ...page.items] : page.items
    cursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } finally {
    loading.value = false
  }
}
const open = (id: number, businessKey: FileBusinessKey) => {
  artifactId.value = id
  key.value = businessKey
  items.value = []
  cursor.value = undefined
  hasMore.value = false
  visible.value = true
  load()
}
const loadMore = () => load(true)
const formatSize = (size: number) =>
  size < 1024 * 1024
    ? `${Math.max(1, Math.round(size / 1024))} KB`
    : `${(size / 1024 / 1024).toFixed(1)} MB`
const availabilityLabel = (status: string) =>
  ({
    AVAILABLE: '可用',
    INVALIDATED: '已失效',
    UNAVAILABLE: '暂不可用'
  })[status] || status
const scanStatusLabel = (status: string) =>
  status === 'PASSED'
    ? '已执行并通过扫描'
    : status === 'SKIPPED'
      ? '未执行安全扫描（不代表安全）'
      : '扫描状态未知'
const scanStatusTagType = (status: string) => (status === 'PASSED' ? 'success' : 'info')

defineExpose({ open })
</script>

<style scoped lang="scss">
.version-list {
  display: grid;
  gap: 10px;
}

.version-card {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.version-card div {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.version-card span {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.version-card .scan-fact {
  color: var(--el-text-color-regular);
}

.load-more {
  width: 100%;
  margin-top: 12px;
}
</style>
