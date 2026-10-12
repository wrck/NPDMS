<template>
  <section :aria-label="title" data-testid="project-associated-data">
    <div class="associated-heading"
      ><h4>{{ title }}</h4><el-button @click="refresh++">刷新</el-button></div
    >
    <el-tabs :model-value="kind" @tab-change="selectKind">
      <el-tab-pane
        v-for="item in visibleKinds"
        :key="item.kind"
        :name="item.kind"
        :label="item.label"
      />
    </el-tabs>
    <div v-loading="loading" :aria-busy="loading">
      <el-alert v-if="error" title="项目资料加载失败，请重试。" type="error" :closable="false">
        <el-button @click="refresh++">重新加载</el-button>
      </el-alert>
      <template v-else>
        <el-table
          :data="rows"
          row-key="id"
          :empty-text="`暂无${currentLabel || title}`"
          @row-dblclick="selected = $event"
        >
          <el-table-column
            v-for="field in columns"
            :key="field.key"
            :label="field.label"
            min-width="160"
            show-overflow-tooltip
          >
            <template #default="{ row }">{{
              valueText(row.values[field.key], field.key)
            }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right"
            ><template #default="{ row }">
              <el-button link type="primary" @click="selected = row">详情</el-button>
            </template></el-table-column
          >
        </el-table>
        <el-pagination
          v-model:current-page="pageNo"
          :page-size="pageSize"
          :total="total"
          layout="prev,pager,next,total"
        />
      </template>
    </div>
    <el-drawer
      :model-value="!!selected"
      :title="currentLabel"
      size="60%"
      @close="selected = undefined"
    >
      <el-descriptions v-if="selected" :column="1" border>
        <el-descriptions-item v-for="field in fields" :key="field.key" :label="field.label">
          {{ valueText(selected.values[field.key], field.key) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { formatSourceDate } from '@/utils/formatSourceDate'
import {
  associatedFields,
  associatedKinds,
  getAssociatedData,
  type AssociatedKind,
  type AssociatedRow
} from '@/api/pms/project/associated-data'
import { legacyValueText, type LegacyId } from '@/api/pms/project/legacy-detail'
const valueText = (value: unknown, field: string) => {
  if (field === 'allocationConfirmed' && typeof value === 'boolean') return value ? '是' : '否'
  const formatted = formatSourceDate(value, field)
  return legacyValueText(
    formatted == null
      ? null
      : typeof formatted === 'object'
        ? JSON.stringify(formatted)
        : String(formatted)
  )
}
const props = withDefaults(defineProps<{ projectId: LegacyId; title?: string; kinds?: readonly AssociatedKind[] }>(), { title: '系统项目资料' })
const visibleKinds = computed(() => associatedKinds.filter(item => !props.kinds || props.kinds.includes(item.kind)))
const kind = ref<AssociatedKind>(props.kinds?.[0] ?? 'products')
const rows = ref<AssociatedRow[]>([]),
  total = ref(0),
  pageNo = ref(1),
  refresh = ref(0)
const pageSize = 20
const loading = ref(false),
  error = ref(false),
  selected = ref<AssociatedRow>()
const fields = computed(() => associatedFields[kind.value])
const columns = computed(() => fields.value.slice(0, 7))
const currentLabel = computed(() => visibleKinds.value.find((x) => x.kind === kind.value)?.label)
const selectKind = (name: string | number) => {
  if (!visibleKinds.value.some((x) => x.kind === name)) return
  pageNo.value = 1
  kind.value = name as AssociatedKind
}
watch(
  [() => props.projectId, kind, pageNo, refresh],
  async ([projectId, category, page], _previous, onCleanup) => {
    let current = true
    onCleanup(() => {
      current = false
    })
    loading.value = true
    error.value = false
    rows.value = []
    if (projectId !== _previous?.[0] || category !== _previous?.[1]) total.value = 0
    selected.value = undefined
    try {
      const result = await getAssociatedData(projectId, category, page, pageSize)
      if (current) {
        rows.value = result.list
        total.value = result.total
      }
    } catch {
      if (current) error.value = true
    } finally {
      if (current) loading.value = false
    }
  },
  { immediate: true }
)
</script>
<style scoped>
.associated-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}
.el-pagination {
  margin-top: 1rem;
  justify-content: flex-end;
}
</style>
