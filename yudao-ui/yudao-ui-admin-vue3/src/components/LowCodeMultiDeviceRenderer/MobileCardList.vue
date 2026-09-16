<template>
  <div class="mobile-card-list">
    <!-- 搜索栏（如有筛选字段） -->
    <div v-if="filterFields.length > 0" class="mobile-search-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索..."
        :prefix-icon="Search"
        clearable
        size="large"
      />
      <el-button :icon="Filter" circle @click="showFilter = !showFilter" />
    </div>

    <!-- 筛选面板（折叠展开） -->
    <transition name="slide-down">
      <div v-if="showFilter" class="mobile-filter-panel">
        <div v-for="f in filterFields" :key="f.id" class="filter-item">
          <label>{{ f.label }}</label>
          <component
            :is="getFilterComponent(f)"
            :model-value="normalizeOptionValue(filterValues[f.prop])"
            @update:model-value="(value: unknown) => (filterValues[f.prop] = value)"
            :placeholder="f.placeholder || `选择${f.label}`"
            size="large"
            clearable
          >
            <template v-if="f.options">
              <el-option
                v-for="opt in f.options"
                :key="opt.value"
                :label="opt.label"
                :value="normalizeOptionValue(opt.value)"
              />
            </template>
          </component>
        </div>
        <div class="filter-actions">
          <el-button @click="resetFilter">重置</el-button>
          <el-button type="primary" @click="applyFilter">确定</el-button>
        </div>
      </div>
    </transition>

    <!-- 卡片列表 -->
    <div v-if="filteredData.length > 0" class="card-list">
      <div
        v-for="(row, index) in pagedData"
        :key="(row.id as string | number) || index"
        class="data-card"
        @click="onCardClick(row)"
      >
        <!-- 卡片头部：主标题 + 状态标签 -->
        <div class="card-header">
          <span class="card-title">{{ getPrimaryText(row) }}</span>
          <el-tag
            v-if="statusColumn"
            :type="getStatusTagType(row[statusColumn.prop])"
            size="small"
          >
            {{ getStatusText(row) }}
          </el-tag>
        </div>
        <!-- 卡片内容：次要字段 -->
        <div class="card-body">
          <div
            v-for="c in secondaryColumns"
            :key="c.id"
            class="card-field"
          >
            <span class="field-label">{{ c.label }}:</span>
            <span class="field-value">{{ formatValue(row[c.prop], c) }}</span>
          </div>
        </div>
        <!-- 卡片底部：操作按钮 -->
        <div v-if="listConfig.operations && listConfig.operations.length > 0" class="card-actions">
          <el-button
            v-for="op in listConfig.operations"
            :key="op.id"
            :type="(op as any).type || 'primary'"
            size="small"
            text
            @click.stop="onAction(op, row)"
          >
            {{ op.label }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <el-empty v-else description="暂无数据" />

    <!-- 分页 -->
    <div v-if="total > pageSize" class="mobile-pagination">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        small
        background
      />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * MobileCardList — 移动端卡片式列表（批次4-T9）。
 *
 * <p>在移动设备上以卡片形式展示列表数据，替代桌面端的表格视图。借鉴腾讯微搭移动端
 * 与 Zoho 移动卡片列表的设计：
 * <ul>
 *   <li>每个数据行渲染为一张卡片，主标题 + 状态标签 + 次要字段</li>
 *   <li>顶部搜索栏 + 可折叠筛选面板（替代桌面端的高级筛选）</li>
 *   <li>卡片点击触发 row-click 事件，底部操作按钮触发 action 事件</li>
 *   <li>简化分页（prev/pager/next），移动端不需要跳页与每页条数</li>
 * </ul>
 * </p>
 *
 * <p>字段映射：本组件消费 ListConfig 的 columns / filters / operations 字段
 * （与 LowCodeListRenderer 桌面端一致），实现"同配置多端渲染"。</p>
 */
import { ref, computed } from 'vue'
import { Search, Filter } from '@element-plus/icons-vue'
import { ElInput, ElSelect, ElButton, ElTag, ElPagination, ElEmpty, ElOption } from 'element-plus'
import type { ListConfig, ListColumnConfig, ListFilterConfig, ListOperationConfig } from '@/api/lowcode'

interface Props {
  /** 列表配置 */
  config: ListConfig
  /** 列表数据 */
  data?: Record<string, unknown>[]
  /** 是否禁用 */
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  data: () => [],
  disabled: false
})

const emit = defineEmits<{
  (e: 'row-click', row: Record<string, unknown>): void
  (e: 'action', opId: string, row: Record<string, unknown>): void
  (e: 'search', keyword: string): void
  (e: 'filter', values: Record<string, unknown>): void
}>()

const listConfig = computed(() => props.config)

/** 搜索关键词 */
const keyword = ref('')
/** 是否显示筛选面板 */
const showFilter = ref(false)
/** 筛选值 */
const filterValues = ref<Record<string, unknown>>({})
/** 当前页码 */
const currentPage = ref(1)
/** 每页条数（移动端固定 10） */
const pageSize = 10

/** 可见列（过滤 hidden 列） */
const visibleColumns = computed<ListColumnConfig[]>(() => {
  return (listConfig.value.columns || []).filter((c) => !c.hidden)
})

/** 主标题列（第一个非隐藏列，或 type 含 tag/link 的列之外的第一个） */
const primaryColumn = computed<ListColumnConfig | undefined>(() => {
  return visibleColumns.value[0]
})

/** 状态列（type=tag 的列） */
const statusColumn = computed<ListColumnConfig | undefined>(() => {
  return visibleColumns.value.find((c) => c.type === 'tag')
})

/** 次要列（除主标题和状态外的可见列，最多显示 4 个） */
const secondaryColumns = computed<ListColumnConfig[]>(() => {
  return visibleColumns.value
    .filter((c) => c !== primaryColumn.value && c !== statusColumn.value && c.type !== 'action')
    .slice(0, 4)
})

/** 筛选字段 */
const filterFields = computed<ListFilterConfig[]>(() => {
  return listConfig.value.filters || []
})

/** 关键词 + 筛选后的数据 */
const filteredData = computed(() => {
  let result = props.data
  // 关键词搜索（在所有文本字段中匹配）
  if (keyword.value) {
    const kw = keyword.value.toLowerCase()
    result = result.filter((row) =>
      Object.values(row).some(
        (v) => v != null && String(v).toLowerCase().includes(kw)
      )
    )
  }
  // 筛选条件
  for (const [key, value] of Object.entries(filterValues.value)) {
    if (value !== undefined && value !== null && value !== '') {
      result = result.filter((row) => row[key] === value)
    }
  }
  return result
})

/** 总条数 */
const total = computed(() => filteredData.value.length)

/** 当前页数据 */
const pagedData = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredData.value.slice(start, start + pageSize)
})

/** 获取主标题文本 */
function getPrimaryText(row: Record<string, unknown>): string {
  if (!primaryColumn.value) return ''
  const val = row[primaryColumn.value.prop]
  return val == null ? '' : String(val)
}

/** 获取状态文本 */
function getStatusText(row: Record<string, unknown>): string {
  if (!statusColumn.value) return ''
  const val = row[statusColumn.value.prop]
  return String(val ?? '')
}

/** 获取状态标签类型 */
function getStatusTagType(val: unknown): 'success' | 'warning' | 'danger' | 'info' {
  // 简单映射：可扩展为配置驱动
  if (val === 'active' || val === '1' || val === 1 || val === 'success') return 'success'
  if (val === 'warning' || val === '2' || val === 2) return 'warning'
  if (val === 'danger' || val === '0' || val === 0 || val === 'error') return 'danger'
  return 'info'
}

/** 格式化字段值显示 */
function formatValue(val: unknown, column: ListColumnConfig): string {
  if (val == null || val === '') return '-'
  // type=tag 的值通过 statusColumn 单独显示，次要列中跳过
  if (column.type === 'date' || column.formatter?.startsWith('dateFormat')) {
    return String(val).replace('T', ' ').slice(0, 16)
  }
  return String(val)
}

/** 获取筛选组件类型 */
function getFilterComponent(_filter: ListFilterConfig) {
  // 简化：所有筛选用 select（如有 options）或 input
  if (_filter.options) return ElSelect
  return ElInput
}

function normalizeOptionValue(
  value: unknown
): string | number | boolean | Record<string, unknown> {
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return value
  }
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>
  }
  return String(value ?? '')
}

/** 应用筛选 */
function applyFilter() {
  showFilter.value = false
  emit('filter', { ...filterValues.value })
  currentPage.value = 1
}

/** 重置筛选 */
function resetFilter() {
  filterValues.value = {}
  keyword.value = ''
  currentPage.value = 1
  emit('filter', {})
}

/** 卡片点击 */
function onCardClick(row: Record<string, unknown>) {
  emit('row-click', row)
}

/** 操作按钮点击 */
function onAction(op: ListOperationConfig, row: Record<string, unknown>) {
  emit('action', op.id, row)
}
</script>

<style scoped>
.mobile-card-list {
  width: 100%;
}
.mobile-search-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.mobile-filter-panel {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
}
.filter-item {
  margin-bottom: 12px;
}
.filter-item label {
  display: block;
  font-size: 13px;
  color: #606266;
  margin-bottom: 4px;
}
.filter-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 8px;
}
.card-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.data-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: all 0.2s;
}
.data-card:active {
  background: #f5f7fa;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 12px;
}
.card-field {
  font-size: 13px;
  display: flex;
  gap: 4px;
}
.field-label {
  color: #909399;
  flex-shrink: 0;
}
.field-value {
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
  justify-content: flex-end;
}
.mobile-pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
/* 折叠动画 */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.25s ease;
  overflow: hidden;
}
.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  max-height: 0;
  padding: 0 12px;
}
.slide-down-enter-to,
.slide-down-leave-from {
  opacity: 1;
  max-height: 500px;
}
</style>
