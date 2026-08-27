<template>
  <ContentWrap>
    <el-alert
      title="V1.7 现场工勘仅保留历史查询；当前 PRE-02 请从项目工作区的“工勘准备”进入。"
      type="info"
      :closable="false"
    />
    <el-form :model="query" inline class="query-form">
      <el-form-item label="项目编号">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
        />
      </el-form-item>
      <el-form-item label="工勘编码"
        ><el-input v-model="query.code" clearable @keyup.enter="load"
      /></el-form-item>
      <el-form-item
        ><el-button @click="load">查询</el-button
        ><el-button @click="maintainLocation">维护地点</el-button></el-form-item
      >
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="工勘编码" min-width="140" />
      <el-table-column prop="name" label="工勘名称" min-width="180" />
      <el-table-column prop="location" label="历史地点" min-width="180" show-overflow-tooltip />
      <el-table-column prop="surveyDate" label="工勘日期" width="120" />
      <el-table-column prop="conclusion" label="历史结论" min-width="220" show-overflow-tooltip />
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>
</template>

<script setup lang="ts">
import * as SiteSurveyApi from '@/api/pms/engineering/site-survey'
import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey'
import * as ProjectApi from '@/api/pms/project/projects'

defineOptions({ name: 'PmsEngSiteSurvey' })
const router = useRouter()
const loading = ref(false)
const rows = ref<SiteSurveyVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  code: ''
})
const load = async () => {
  loading.value = true
  try {
    const data = await SiteSurveyApi.getSiteSurveyPage(query)
    rows.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}
const maintainLocation = () => router.push('/pms/customer-asset/asset-site')
onMounted(load)
</script>

<style scoped lang="scss">
.query-form {
  margin-top: 12px;
}

@media (width <= 767px) {
  .query-form :deep(.el-form-item),
  .query-form :deep(.el-select),
  .query-form :deep(.el-input) {
    width: 100%;
  }
}
</style>
