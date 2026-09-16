<template>
  <el-drawer v-model="visible" :size="narrow ? '100%' : '720px'" title="需求分析修订记录">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-empty v-if="!loading && !rows.length" description="暂无修订记录" />
    <article v-for="row in rows" :key="String(row.ref.revisionId)" class="revision-row">
      <div><strong>修订 V{{ row.revisionNo }}</strong>
        <el-tag class="ml-10px" :type="row.effective ? 'success' : 'info'">{{ row.effective ? '当前有效' : '已冻结' }}</el-tag>
        <p>完成时间：{{ row.frozenAt ? formatDate(row.frozenAt) : '—' }}</p>
        <p v-if="row.reason">{{ row.reason }}</p>
      </div>
      <div><el-button link type="primary" @click="emit('view', row.ref.revisionId)">查看</el-button>
        <el-button v-if="selected && String(selected) !== String(row.ref.revisionId)" link @click="emit('compare', selected, row.ref.revisionId)">与查看版对比</el-button></div>
    </article>
    <el-button v-if="hasMore || loading" :loading="loading" @click="loadMore">加载更多</el-button>
  </el-drawer>
</template>
<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import { formatDate } from '@/utils/formatTime'
import * as api from '@/api/pms/engineering/requirement-analysis/entity'
import type { EntityId, Revision } from '@/api/pms/engineering/requirement-analysis/entity'
const emit = defineEmits<{ view: [revisionId: EntityId]; compare: [left: EntityId, right: EntityId] }>()
const narrow = useMediaQuery('(max-width: 767px)')
const visible = ref(false), loading = ref(false), hasMore = ref(false), error = ref('')
const entityId = ref<EntityId>(), selected = ref<EntityId>(), beforeId = ref<EntityId>()
const rows = ref<Revision[]>([])
let sequence = 0
const loadMore = async () => {
  if (!entityId.value || loading.value) return
  const request = sequence
  loading.value = true; error.value = ''
  try {
    const page = await api.revisions(entityId.value, beforeId.value)
    if (request !== sequence) return
    rows.value.push(...page)
    beforeId.value = page.at(-1)?.ref.revisionId
    hasMore.value = page.length === 20
  } catch { if (request === sequence) error.value = '修订记录加载失败，请重试' }
  finally { if (request === sequence) loading.value = false }
}
const open = async (id: EntityId, revisionId?: EntityId) => {
  ++sequence; loading.value = false
  entityId.value = id; selected.value = revisionId; beforeId.value = undefined
  rows.value = []; hasMore.value = true; visible.value = true
  await loadMore()
}
defineExpose({ open })
</script>
<style scoped>
.revision-row { display: flex; justify-content: space-between; gap: 12px; padding: 16px 0; border-bottom: 1px solid var(--el-border-color-light); }
.revision-row p { color: var(--el-text-color-secondary); }
@media (width <= 767px) { .revision-row { flex-direction: column; } }
</style>
