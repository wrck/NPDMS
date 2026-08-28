<template>
  <el-drawer v-model="visible" :size="narrow ? '100%' : '720px'" title="就绪阻断与快照历史">
    <el-alert
      v-if="preparation && !preparation.snapshotCurrent"
      title="当前事实已变化，旧 READY 快照不再有效；请显式重新评估。"
      type="warning"
      :closable="false"
    />
    <el-skeleton v-if="loading" :rows="5" animated />
    <el-empty v-else-if="!rows.length" description="尚无就绪快照" />
    <el-timeline v-else class="snapshot-list">
      <el-timeline-item
        v-for="row in rows"
        :key="row.snapshotId"
        :timestamp="row.evaluatedAt"
        placement="top"
      >
        <el-card shadow="never">
          <div class="snapshot-heading">
            <strong>快照 #{{ row.snapshotNo }}</strong>
            <el-tag :type="row.result === 'READY' ? 'success' : 'danger'">{{ row.result }}</el-tag>
          </div>
          <p
            >输入版本 {{ row.inputVersion }} · 准备版本 {{ row.preparationVersion }} · 就绪版本
            {{ row.readinessVersion }}</p
          >
          <details>
            <summary>阻断明细</summary>
            <pre>{{ pretty(row.blockers) }}</pre>
          </details>
        </el-card>
      </el-timeline-item>
    </el-timeline>
    <el-button v-if="hasMore" class="load-more" @click="load(false)">加载更多</el-button>
  </el-drawer>
</template>

<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import * as PreparationApi from '@/api/pms/engineering/preparation'
import type {
  PreparationReadinessSnapshotVO,
  PreparationVO
} from '@/api/pms/engineering/preparation'

const narrow = useMediaQuery('(max-width: 767px)')
const visible = ref(false)
const loading = ref(false)
const preparation = ref<PreparationVO>()
const rows = ref<PreparationReadinessSnapshotVO[]>([])
const cursor = ref<string>()
const hasMore = ref(false)

const pretty = (value: string) => {
  try {
    return JSON.stringify(JSON.parse(value || '[]'), null, 2)
  } catch {
    return value || '[]'
  }
}
const open = async (current: PreparationVO) => {
  preparation.value = current
  visible.value = true
  await load(true)
}
const load = async (reset: boolean) => {
  if (!preparation.value) return
  loading.value = true
  try {
    const page = await PreparationApi.getReadinessSnapshots(preparation.value.preparationId, {
      cursor: reset ? undefined : cursor.value,
      pageSize: 20
    })
    rows.value = reset ? page.items : [...rows.value, ...page.items]
    cursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>

<style scoped lang="scss">
.snapshot-list {
  margin-top: 16px;
}

.snapshot-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--el-text-color-primary);
}

.snapshot-heading + p {
  color: var(--el-text-color-secondary);
}

details {
  color: var(--el-text-color-regular);
}

pre {
  max-width: 100%;
  padding: 10px;
  overflow: auto;
  white-space: pre-wrap;
  background: var(--el-fill-color-light);
  border-radius: var(--el-border-radius-base);
}

.load-more {
  width: 100%;
}

@media (width <= 767px) {
  .snapshot-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
