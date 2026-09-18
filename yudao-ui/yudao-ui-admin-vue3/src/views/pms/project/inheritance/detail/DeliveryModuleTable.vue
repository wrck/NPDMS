<template>
  <ContentWrap>
    <div class="panel-header">
      <span class="panel-title">
        <Icon :icon="config.icon" /> {{ config.label }}
      </span>
      <el-button v-if="config.path" link type="primary" @click="goPage(config.path)">
        前往完整页面
      </el-button>
    </div>
    <el-table
      v-loading="loading"
      :data="list"
      empty-text="暂无数据"
      size="small"
      max-height="520"
      class="clickable-table"
      @row-click="openDetail"
    >
      <el-table-column
        v-for="col in config.columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span v-if="col.type === 'status'" class="status-pill" :class="`status-pill--${statusTone(row)}`">
            {{ statusLabel(row) }}
          </span>
          <span v-else-if="col.type === 'time'">{{ formatDate(row[col.prop]) }}</span>
          <span v-else-if="col.type === 'enum'">{{ enumLabel(col, row[col.prop]) }}</span>
          <span v-else-if="col.type === 'html'">{{ stripHtml(row[col.prop]) }}</span>
          <span v-else>{{ row[col.prop] ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="hasActions" label="操作" :width="actionColumnWidth" fixed="right">
        <template #default="{ row }">
          <el-button
            v-for="act in visibleActions(row)"
            :key="act.label"
            :type="act.type || 'primary'"
            link
            size="small"
            @click.stop="runAction(act, row)"
          >{{ act.label }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="total > pageSize" class="panel-pagination">
      <el-pagination
        v-model:current-page="pageNo"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        small
        @current-change="load"
      />
    </div>
    <!-- 行详情：字段口径同列表，便于核对整行 -->
    <el-dialog v-model="detailVisible" :title="`${config.label} 详情`" width="640px">
      <el-descriptions v-if="detailRow" :column="2" border size="small">
        <el-descriptions-item
          v-for="col in config.columns"
          :key="col.prop"
          :label="col.label"
          :span="col.minWidth ? 2 : 1"
        >
          <span v-if="col.type === 'status'" class="status-pill" :class="`status-pill--${statusTone(detailRow)}`">
            {{ statusLabel(detailRow) }}
          </span>
          <span v-else-if="col.type === 'time'">{{ formatDate(detailRow[col.prop]) }}</span>
          <span v-else-if="col.type === 'enum'">{{ enumLabel(col, detailRow[col.prop]) }}</span>
          <span v-else-if="col.type === 'html'">{{ stripHtml(detailRow[col.prop]) }}</span>
          <span v-else>{{ detailRow[col.prop] ?? '-' }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </ContentWrap>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'DeliveryModuleTable' })

export interface DeliveryModuleColumn {
  prop: string
  label: string
  width?: number
  minWidth?: number
  type?: 'status' | 'time' | 'enum' | 'html'
  /** enum 列的字符串值→展示标签映射（如 crmPushStatus、换货类型） */
  valueMap?: Record<string, string>
}

export interface DeliveryModuleAction {
  label: string
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  show?: (row: any) => boolean
  confirm?: string
  run: (row: any) => Promise<any>
}

export interface DeliveryModuleConfig {
  label: string
  icon: string
  /** 完整页面路由（可空） */
  path?: string
  /** 按项目加载真实数据（过滤字段由模块自身确定） */
  load: (projectId: number, pageNo: number, pageSize: number) => Promise<{ list: any[]; total: number }>
  columns: DeliveryModuleColumn[]
  /** 状态字段与值→{label,tone} 映射；status 列缺省读该字段 */
  statusField?: string
  statusMap?: Record<number, { label: string; tone: string }>
  actions?: DeliveryModuleAction[]
}

const props = defineProps<{ config: DeliveryModuleConfig; projectId: number }>()

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 20

const load = async () => {
  loading.value = true
  try {
    const result = await props.config.load(props.projectId, pageNo.value, pageSize)
    list.value = result.list || []
    total.value = result.total ?? list.value.length
  } finally {
    loading.value = false
  }
}

// 切换配置或项目时分页复位重载
watch(
  () => [props.config, props.projectId],
  () => {
    pageNo.value = 1
    load()
  },
  { immediate: true }
)

const statusField = computed(() => props.config.statusField || 'status')
const statusLabel = (row: any) =>
  props.config.statusMap?.[row?.[statusField.value]]?.label ?? row?.[statusField.value] ?? '-'
const statusTone = (row: any) =>
  props.config.statusMap?.[row?.[statusField.value]]?.tone ?? 'gray'

const enumLabel = (col: DeliveryModuleColumn, value: any) =>
  (value == null || value === '' ? '-' : col.valueMap?.[value] ?? value)
const stripHtml = (value: any) =>
  (value == null || value === '' ? '-' : String(value).replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim() || '-')

const visibleActions = (row: any) => (props.config.actions || []).filter((act) => !act.show || act.show(row))
const hasActions = computed(() => (props.config.actions || []).length > 0)
const actionColumnWidth = computed(() => 96 + (props.config.actions || []).length * 88)

const runAction = async (act: DeliveryModuleAction, row: any) => {
  if (act.confirm) {
    const confirmed = await ElMessageBox.confirm(act.confirm, '提示', { type: 'warning' })
      .then(() => true)
      .catch(() => false)
    if (!confirmed) return
  }
  await act.run(row)
  await load()
}

const detailVisible = ref(false)
const detailRow = ref<any>(null)
const openDetail = (row: any) => {
  detailRow.value = row
  detailVisible.value = true
}

const goPage = (path: string) => router.push(path)
</script>

<style lang="scss" scoped>
.panel-header {
  display: flex;
  padding-bottom: 8px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  align-items: center;
  justify-content: space-between;
}

.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.panel-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 10px;
}

.clickable-table {
  cursor: pointer;
}

.status-pill {
  display: inline-flex;
  padding: 2px 8px;
  font-size: 12px;
  border-radius: 10px;
  align-items: center;

  &--gray {
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color);
  }

  &--blue {
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }

  &--green {
    color: var(--el-color-success);
    background: var(--el-color-success-light-9);
  }

  &--yellow {
    color: var(--el-color-warning);
    background: var(--el-color-warning-light-9);
  }

  &--red {
    color: var(--el-color-danger);
    background: var(--el-color-danger-light-9);
  }
}
</style>
