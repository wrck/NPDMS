<template>
  <div>
    <ContentWrap>
      <el-form inline class="integration-query" @submit.prevent
        ><el-form-item label="同步任务"
          ><el-select v-model="taskId" placeholder="选择任务查询映射" @change="changeTask"
            ><el-option
              v-for="task in tasks"
              :key="task.id"
              :value="task.id!"
              :label="task.name" /></el-select></el-form-item
      ></el-form>
    </ContentWrap>
    <ContentWrap>
      <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-4" />
      <el-table
        v-loading="loading"
        :data="rows"
        class="integration-table"
        :empty-text="
          taskId ? '该任务暂无已提交的来源映射' : '请选择同步任务查看来源与目标的对应关系'
        "
      >
        <el-table-column prop="sourceObject" label="来源对象" min-width="160" /><el-table-column
          prop="sourceKey"
          label="源主键"
          width="120"
        />
        <el-table-column prop="objectKey" label="目标类型" min-width="130" /><el-table-column
          prop="targetId"
          label="目标 ID"
          min-width="190"
        />
        <el-table-column label="所属公司目标 ID" min-width="190">
          <template #default="{ row }">{{ field(row, '_companyTargetId') }}</template>
        </el-table-column>
        <el-table-column label="名称" min-width="170"
          ><template #default="{ row }">{{ field(row, 'name') }}</template></el-table-column
        >
        <el-table-column label="公司源键 / 父部门源键" min-width="200"
          ><template #default="{ row }"
            >{{ field(row, 'companyKey') }} / {{ field(row, 'parentKey') }}</template
          ></el-table-column
        >
      </el-table>
      <div class="integration-pagination"
        ><Pagination
          v-model:page="page.pageNo"
          v-model:limit="page.pageSize"
          :total="total"
          @pagination="load"
      /></div>
      <p class="integration-note"
        >来源系统、来源对象和源主键共同标识一条来源记录。公司与部门的来源关系不影响人员权限。</p
      >
    </ContentWrap>
  </div>
</template>
<script setup lang="ts">
import * as api from '@/api/pms/integration'
const tasks = ref<api.Task[]>([]),
  taskId = ref<api.Id>(),
  rows = ref<api.Binding[]>([]),
  total = ref(0),
  loading = ref(false),
  error = ref('')
const page = reactive({ pageNo: 1, pageSize: 20 })
const field = (row: api.Binding, key: string) => {
  try {
    return JSON.parse(row.fieldsJson)[key] ?? '—'
  } catch {
    return '—'
  }
}
const load = async () => {
  if (!taskId.value) return
  loading.value = true
  error.value = ''
  try {
    const p = await api.getBindings({ ...page, taskId: taskId.value })
    rows.value = p.list
    total.value = p.total
  } catch {
    error.value = '来源映射加载失败，请重新选择任务重试。'
  } finally {
    loading.value = false
  }
}
const changeTask = () => {
  page.pageNo = 1
  rows.value = []
  total.value = 0
  void load()
}
onMounted(async () => {
  tasks.value = (await api.getTasks({ pageNo: 1, pageSize: 100 })).list
})
</script>
