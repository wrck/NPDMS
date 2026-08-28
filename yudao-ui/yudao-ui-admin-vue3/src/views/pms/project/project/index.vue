<template>
  <div class="project-list-page">
    <!-- ============ 顶部状态卡 ============ -->
    <div class="status-cards">
      <div
        v-for="card in statusCards"
        :key="card.key"
        class="status-card"
        :class="[
          `status-card--${card.tone}`,
          { 'status-card--active': activeStatus === card.value }
        ]"
        @click="toggleStatusFilter(card.value)"
      >
        <div class="status-card-icon">
          <Icon :icon="card.icon" />
        </div>
        <div class="status-card-body">
          <div class="status-card-num">{{ card.count }}</div>
          <div class="status-card-label">{{ card.label }}</div>
        </div>
        <div class="status-card-strip"></div>
      </div>
    </div>

    <!-- ============ 中间筛选条件 + 操作按钮 ============ -->
    <ContentWrap class="filter-wrap">
      <el-form ref="queryFormRef" :model="query" inline class="filter-form">
        <el-form-item label="项目编码" prop="code">
          <el-input
            v-model="query.code"
            placeholder="请输入项目编码"
            clearable
            class="!w-200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="项目名称" prop="name">
          <el-input
            v-model="query.name"
            placeholder="请输入项目名称"
            clearable
            class="!w-220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="项目状态" prop="status">
          <el-select v-model="query.status" placeholder="全部" clearable class="!w-140px">
            <el-option
              v-for="dict in getIntDictOptions(DICT_TYPE.PMS_PROJECT_STATUS)"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="项目分类" prop="category">
          <el-select v-model="query.category" placeholder="全部" clearable class="!w-140px">
            <el-option
              v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PROJECT_CATEGORY)"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="重大项目" prop="majorProjectFlag">
          <el-select v-model="query.majorProjectFlag" placeholder="全部" clearable class="!w-120px">
            <el-option :value="true" label="是" />
            <el-option :value="false" label="否" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目经理" prop="managerUserId">
          <PmsEntitySelect
            v-model="query.managerUserId"
            :api="UserApi.getUserPage"
            label-field="nickname"
            value-field="id"
            query-field="nickname"
            placeholder="请选择用户"
            class="!w-180px"
          />
        </el-form-item>
        <el-form-item class="filter-actions">
          <el-button type="primary" @click="handleSearch">
            <Icon icon="ep:search" />查询
          </el-button>
          <el-button @click="handleReset">
            <Icon icon="ep:refresh-left" />重置
          </el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <!-- ============ 下面表格 ============ -->
    <ContentWrap class="table-wrap">
      <div class="table-toolbar">
        <span class="table-title">
          <Icon icon="ep:folder-opened" /> 项目列表
          <span class="table-count">共 {{ listTotal }} 条</span>
        </span>
        <el-button text bg @click="loadList">
          <Icon icon="ep:refresh" />刷新
        </el-button>
      </div>
      <el-table
        v-loading="listLoading"
        :data="projectList"
        empty-text="暂无项目数据"
        highlight-current-row
        :row-class-name="rowClassName"
      >
        <el-table-column prop="code" label="项目编码" width="150" fixed="left">
          <template #default="{ row }">
            <span class="project-code">{{ row.code || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="项目名称" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="project-name" @click.stop="goDetail(row)">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="90">
          <template #default="{ row }">
            <dict-tag :type="DICT_TYPE.PMS_PROJECT_CATEGORY" :value="row.category" />
          </template>
        </el-table-column>
        <el-table-column prop="majorProjectFlag" label="重大项目" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.majorProjectFlag" type="warning" size="small" effect="dark">
              <Icon icon="ep:star-filled" /> 重大
            </el-tag>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="projectType" label="项目类型" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <dict-tag :type="DICT_TYPE.PMS_PROJECT_TYPE" :value="row.projectType" />
          </template>
        </el-table-column>
        <el-table-column prop="managerUserId" label="项目经理" width="100">
          <template #default="{ row }">
            <UserTag :user-id="row.managerUserId" />
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <dict-tag :type="DICT_TYPE.PMS_PROJECT_STATUS" :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="goDetail(row)">
              <Icon icon="ep:view" />详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        :total="listTotal"
        v-model:page="listQuery.pageNo"
        v-model:limit="listQuery.pageSize"
        @pagination="loadList"
      />
    </ContentWrap>
  </div>
</template>

<script setup lang="ts">
/**
 * 旧链项目列表页（F-PM01 存量冻结后仅只读）
 *
 * 写操作（分类/指派/编辑）已随旧链写端点退役；
 * 新项目创建、分类、指派请使用新链页面（ProjectMaster）。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as ProjectApi from '@/api/pms/project/project'
import { formatDate } from '@/utils/formatTime'
import * as UserApi from '@/api/system/user'
import UserTag from '@/components/UserTag/index.vue'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'

defineOptions({ name: 'PmsProject' })

const router = useRouter()
const listLoading = ref(false)

// ============ 状态卡数据 ============
const statusStats = reactive({
  total: 0,
  pending: 0, // 0 立项待指派
  inProgress: 0, // 1 进行中
  completed: 0, // 2 已完成
  closed: 0 // 3 已关闭
})

const activeStatus = ref<number | undefined>(undefined)

const statusCards = computed(() => [
  {
    key: 'total',
    label: '项目总数',
    value: undefined,
    count: statusStats.total,
    tone: 'blue',
    icon: 'ep:folder-opened'
  },
  {
    key: 'pending',
    label: '立项待指派',
    value: 0,
    count: statusStats.pending,
    tone: 'gray',
    icon: 'ep:clock'
  },
  {
    key: 'inProgress',
    label: '进行中',
    value: 1,
    count: statusStats.inProgress,
    tone: 'green',
    icon: 'ep:loading'
  },
  {
    key: 'completed',
    label: '已完成',
    value: 2,
    count: statusStats.completed,
    tone: 'blue',
    icon: 'ep:circle-check-filled'
  },
  {
    key: 'closed',
    label: '已关闭',
    value: 3,
    count: statusStats.closed,
    tone: 'gray',
    icon: 'ep:lock'
  }
])

// ============ 筛选条件 ============
const query = reactive({
  code: '',
  name: '',
  status: undefined as number | undefined,
  category: '' as string,
  majorProjectFlag: undefined as boolean | undefined,
  managerUserId: undefined as number | undefined
})

// ============ 列表数据 ============
const projectList = ref<any[]>([])
const listTotal = ref(0)
const listQuery = reactive({ pageNo: 1, pageSize: 10 })

// ============ 数据加载 ============
const loadStats = async () => {
  try {
    // 拉取全量项目用于状态统计（Yudao 默认 pageSize 上限 100）
    const data = await ProjectApi.getProjectPage({ pageNo: 1, pageSize: 100 })
    const all = data?.list || []
    statusStats.total = all.length
    statusStats.pending = all.filter((p: any) => Number(p.status) === 0).length
    statusStats.inProgress = all.filter((p: any) => Number(p.status) === 1).length
    statusStats.completed = all.filter((p: any) => Number(p.status) === 2).length
    statusStats.closed = all.filter((p: any) => Number(p.status) === 3).length
  } catch (e) {
    // 统计失败不阻断列表
    console.warn('[PmsProject] loadStats failed:', e)
  }
}

const loadList = async () => {
  listLoading.value = true
  try {
    const params: any = {
      pageNo: listQuery.pageNo,
      pageSize: listQuery.pageSize
    }
    if (query.code) params.code = query.code
    if (query.name) params.name = query.name
    if (query.status !== undefined && query.status !== null) params.status = query.status
    if (query.category) params.category = query.category
    if (query.majorProjectFlag !== undefined && query.majorProjectFlag !== null)
      params.majorProjectFlag = query.majorProjectFlag
    if (query.managerUserId) params.managerUserId = query.managerUserId
    const data = await ProjectApi.getProjectPage(params)
    projectList.value = data.list
    listTotal.value = data.total
  } finally {
    listLoading.value = false
  }
}

const handleSearch = () => {
  listQuery.pageNo = 1
  loadList()
}

const handleReset = () => {
  query.code = ''
  query.name = ''
  query.status = undefined
  query.category = ''
  query.majorProjectFlag = undefined
  query.managerUserId = undefined
  activeStatus.value = undefined
  listQuery.pageNo = 1
  loadList()
}

const toggleStatusFilter = (value?: number) => {
  if (value === undefined) {
    // 总数卡：清除状态筛选
    activeStatus.value = undefined
    query.status = undefined
  } else {
    // 切换该状态筛选
    if (activeStatus.value === value) {
      activeStatus.value = undefined
      query.status = undefined
    } else {
      activeStatus.value = value
      query.status = value
    }
  }
  listQuery.pageNo = 1
  loadList()
}

const rowClassName = ({ row }: { row: any }) => {
  return row.majorProjectFlag ? 'row-major' : ''
}

const goDetail = (row: any) => {
  router.push({ path: '/pms/project-detail', query: { projectId: row.id } })
}

onMounted(() => {
  loadStats()
  loadList()
})
</script>

<style lang="scss" scoped>
$primary: #1e3a5f;
$border: #e5e7eb;

.project-list-page {
  --pl-primary: #{$primary};
  --pl-border: #{$border};
}

/* ============ 状态卡 ============ */
.status-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 15px;
}
.status-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid var(--pl-border);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
  overflow: hidden;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(15, 23, 42, 0.08);
  }
  &--active {
    border-color: var(--pl-primary);
    box-shadow: 0 0 0 2px rgba(30, 58, 95, 0.12);
  }

  .status-card-icon {
    flex-shrink: 0;
    width: 42px;
    height: 42px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
    color: #fff;
  }
  &--blue .status-card-icon { background: linear-gradient(135deg, #3b82f6, #60a5fa); }
  &--green .status-card-icon { background: linear-gradient(135deg, #10b981, #34d399); }
  &--gray .status-card-icon { background: linear-gradient(135deg, #64748b, #94a3b8); }
  &--blue.status-card--active .status-card-icon { background: linear-gradient(135deg, #1d4ed8, #3b82f6); }
  &--green.status-card--active .status-card-icon { background: linear-gradient(135deg, #059669, #10b981); }
  &--gray.status-card--active .status-card-icon { background: linear-gradient(135deg, #475569, #64748b); }

  .status-card-body {
    flex: 1;
    min-width: 0;
  }
  .status-card-num {
    font-size: 24px;
    font-weight: 700;
    color: #1f2937;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    line-height: 1.1;
  }
  .status-card-label {
    font-size: 12px;
    color: #6b7280;
    margin-top: 2px;
  }
  .status-card-strip {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 3px;
  }
  &--blue .status-card-strip { background: #3b82f6; }
  &--green .status-card-strip { background: #10b981; }
  &--gray .status-card-strip { background: #94a3b8; }
}

/* ============ 筛选区 ============ */
.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  :deep(.el-form-item) {
    margin-bottom: 12px;
  }
}
.filter-actions {
  :deep(.el-form-item__content) {
    flex-wrap: wrap;
    gap: 8px;
  }
}

/* ============ 表格区 ============ */
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.table-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}
.table-count {
  font-size: 12px;
  color: #6b7280;
  font-weight: 400;
  margin-left: 4px;
}

.project-code {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: #6b7280;
}
.project-name {
  color: var(--pl-primary);
  cursor: pointer;
  font-weight: 500;
  &:hover {
    text-decoration: underline;
    color: #2563eb;
  }
}
.category-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 600;
  &--MAIN {
    background: rgba(30, 58, 95, 0.08);
    color: #1e3a5f;
  }
  &--SUB {
    background: rgba(100, 116, 139, 0.1);
    color: #64748b;
  }
}
.manager-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: rgba(96, 165, 250, 0.1);
  border-radius: 10px;
  font-size: 12px;
  color: #2563eb;
  font-family: 'JetBrains Mono', monospace;
}
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  .status-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
  }
  &--gray {
    background: rgba(148, 163, 184, 0.15);
    color: #64748b;
  }
  &--green {
    background: rgba(52, 211, 153, 0.15);
    color: #059669;
  }
  &--blue {
    background: rgba(96, 165, 250, 0.15);
    color: #2563eb;
  }
}

:deep(.row-major) {
  background: rgba(251, 191, 36, 0.04) !important;
  td {
    background: transparent !important;
  }
}

/* ============ 响应式 ============ */
@media (max-width: 1200px) {
  .status-cards {
    grid-template-columns: repeat(3, 1fr);
  }
}
@media (max-width: 768px) {
  .status-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
