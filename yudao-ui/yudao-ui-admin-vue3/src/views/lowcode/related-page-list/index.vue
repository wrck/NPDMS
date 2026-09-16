<script setup lang="ts">
/**
 * 低代码关联页配置列表页。
 *
 * <p>提供关联页配置的分页查询、新建（跳转到设计器）、编辑、删除、发布、归档、导入、导出。</p>
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  archiveRelatedPage,
  deleteRelatedPage,
  exportRelatedPage,
  listRelatedPages,
  publishRelatedPage,
  type LowCodeRelatedPageConfig,
  type LowCodeRelatedPageQuery
} from '@/api/lowcode'

const router = useRouter()

const loading = ref(false)
const tableData = ref<LowCodeRelatedPageConfig[]>([])
const total = ref(0)

const query = reactive<LowCodeRelatedPageQuery>({
  page: 1,
  size: 10,
  code: '',
  name: '',
  status: '',
  bizType: ''
})

/** 状态选项 */
const statusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已归档', value: 'ARCHIVED' }
]

/** 状态标签颜色 */
function statusTagType(status?: string): 'warning' | 'success' | 'info' {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'ARCHIVED') return 'info'
  return 'warning'
}

/** 加载列表 */
async function loadData() {
  loading.value = true
  try {
    const res = await listRelatedPages(query)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch {
    /* handled by interceptor */
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadData()
}

function handleReset() {
  query.code = ''
  query.name = ''
  query.status = ''
  query.bizType = ''
  query.page = 1
  loadData()
}

function handleAdd() {
  router.push('/lowcode/related-page-designer')
}

function handleEdit(row: LowCodeRelatedPageConfig) {
  router.push({ path: '/lowcode/related-page-designer', query: { id: String(row.id) } })
}

async function handleDelete(row: LowCodeRelatedPageConfig) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`确认删除关联页「${row.name}」？`, '确认', { type: 'warning' })
    await deleteRelatedPage(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
    /* cancelled or error */
  }
}

async function handlePublish(row: LowCodeRelatedPageConfig) {
  if (!row.id) return
  try {
    await publishRelatedPage(row.id)
    ElMessage.success('发布成功')
    loadData()
  } catch {
    /* handled by interceptor */
  }
}

async function handleArchive(row: LowCodeRelatedPageConfig) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`确认归档关联页「${row.name}」？`, '确认', { type: 'warning' })
    await archiveRelatedPage(row.id)
    ElMessage.success('归档成功')
    loadData()
  } catch {
    /* cancelled or error */
  }
}

async function handleExport(row: LowCodeRelatedPageConfig) {
  if (!row.code) return
  try {
    await exportRelatedPage(row.code)
    ElMessage.success('导出成功')
  } catch {
    /* handled by interceptor */
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="related-page-list-page">
    <!-- 查询区 -->
    <el-card shadow="never" :body-style="{ padding: '12px 16px' }">
      <el-form inline :model="query">
        <el-form-item label="编码">
          <el-input v-model="query.code" placeholder="关联页编码" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="query.name" placeholder="关联页名称" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务类型">
          <el-input v-model="query.bizType" placeholder="业务类型" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="handleSearch">查询</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" style="margin-top: 12px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>关联页配置列表</span>
          <el-button type="primary" :icon="'Plus'" @click="handleAdd">新建关联页</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="code" label="编码" min-width="180" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="bizType" label="业务类型" width="120" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" align="center" />
        <el-table-column prop="updateTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" size="small" link type="success" @click="handlePublish(row)">发布</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" size="small" link type="warning" @click="handleArchive(row)">归档</el-button>
            <el-button size="small" link @click="handleExport(row)">导出</el-button>
            <el-button size="small" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="loadData"
        @size-change="loadData"
      />
    </el-card>
  </div>
</template>

<style scoped>
.related-page-list-page {
  display: flex;
  flex-direction: column;
}
</style>
