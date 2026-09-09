<template>
  <Dialog v-model="visible" title="联系人变更历史" width="min(900px, 95vw)">
    <el-table v-loading="loading" :data="rows" empty-text="暂无变更历史">
      <el-table-column label="时间" width="170"><template #default="{ row }">{{ formatDate(row.occurredAt) }}</template></el-table-column>
      <el-table-column prop="actorUserId" label="操作人" width="90" />
      <el-table-column label="操作" width="90"><template #default="{ row }">{{ actionLabel[row.actionCode] || row.actionCode }}</template></el-table-column>
      <el-table-column prop="projectRelationId" label="联系记录" width="100" />
      <el-table-column label="变更内容" min-width="360"><template #default="{ row }">{{ summary(row) }}</template></el-table-column>
      <el-table-column label="操作" width="90"><template #default="{ row }"><el-button v-if="row.actionCode === 'DELETE'" link type="primary" @click="emit('restore', row)" v-hasPermi="['pms:customer-contact:update']">恢复</el-button></template></el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
    <template #footer><el-button @click="visible = false">关闭</el-button></template>
  </Dialog>
</template>
<script setup lang="ts">
import { ref, reactive } from 'vue'
import { formatDate } from '@/utils/formatTime'
import { getProjectHistory, type ContactHistoryVO } from '@/api/pms/customer/contacts'
const visible = ref(false), loading = ref(false), projectId = ref<number>(), total = ref(0)
const rows = ref<ContactHistoryVO[]>([])
const query = reactive({ pageNo: 1, pageSize: 10 })
const emit = defineEmits<{ restore: [row: ContactHistoryVO] }>()
const actionLabel: Record<string, string> = { REFERENCE: '建立引用', UPDATE: '修改', DELETE: '删除', RESTORE: '恢复' }
const labels: Record<string, string> = { name: '姓名', department: '部门', title: '职务', mobile: '手机', phone: '电话', email: '邮箱', primaryFlag: '主联系人', status: '状态', remark: '备注' }
const summary = (row: ContactHistoryVO) => {
  const before = row.beforeValues ? JSON.parse(row.beforeValues) : {}
  const after = row.afterValues ? JSON.parse(row.afterValues) : {}
  if (row.actionCode === 'DELETE') return `删除 ${before.name || ''}，保留来源及历史`
  const display = (key: string, value: unknown) => value == null ? '—' : key === 'primaryFlag' ? (value ? '是' : '否') : key === 'status' ? (value === 0 ? '启用' : '停用') : String(value)
  return Object.entries(labels).filter(([key]) => (before[key] ?? null) !== (after[key] ?? null)).map(([key, label]) => `${label}：${display(key, before[key])} → ${display(key, after[key])}`).join('；')
}
const load = async () => {
  if (!projectId.value) return
  loading.value = true
  try { const page = await getProjectHistory(projectId.value, query); rows.value = page.list; total.value = page.total }
  finally { loading.value = false }
}
const open = async (id: number) => { projectId.value = id; query.pageNo = 1; visible.value = true; await load() }
defineExpose({ open })
</script>
