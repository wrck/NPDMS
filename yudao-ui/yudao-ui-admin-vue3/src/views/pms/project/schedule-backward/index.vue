<template>
  <ContentWrap>
    <el-alert title="V1.7 工期倒排已冻结，仅保留历史查询；当前工期请在项目详情的“项目工期”中维护。" type="info" :closable="false" />
    <el-form :model="query" inline class="query-form">
      <el-form-item label="项目">
        <PmsEntitySelect v-model="query.projectId" :api="ProjectApi.getProjectPage" label-field="name" value-field="id" query-field="name" clearable />
      </el-form-item>
      <el-form-item><el-button @click="load"><Icon icon="ep:search" />查询</el-button></el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无历史工期倒排数据">
      <el-table-column prop="projectId" label="项目" min-width="160"><template #default="{ row }"><ProjectTag :project-id="row.projectId" /></template></el-table-column>
      <el-table-column prop="targetDate" label="目标完工日期" width="130" />
      <el-table-column prop="projectType" label="项目类型" width="100" />
      <el-table-column prop="conflictSummary" label="冲突摘要" min-width="220" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="90" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">明细</el-button></template></el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>
  <Dialog v-model="detailVisible" title="历史倒排明细" width="min(960px, 96%)">
    <el-table :data="detailItems" max-height="420" empty-text="暂无阶段明细">
      <el-table-column prop="sort" label="顺序" width="70" />
      <el-table-column prop="phaseName" label="阶段名称" min-width="140" />
      <el-table-column prop="plannedStartDate" label="计划开始" width="130" />
      <el-table-column prop="plannedEndDate" label="计划结束" width="130" />
      <el-table-column prop="recommendedLatestDate" label="建议最晚日期" width="140" />
      <el-table-column prop="conflictReason" label="冲突原因" min-width="180" />
    </el-table>
  </Dialog>
</template>

<script setup lang="ts">
import { dateFormatter } from '@/utils/formatTime'
import * as ScheduleApi from '@/api/pms/project/schedule-backward'
import * as ProjectApi from '@/api/pms/project/project'
import ProjectTag from '@/components/ProjectTag/index.vue'
import type { ScheduleBackwardItemVO, ScheduleBackwardVO } from '@/api/pms/project/schedule-backward'

defineOptions({ name: 'PmsScheduleBackwardHistory' })
const loading = ref(false)
const rows = ref<ScheduleBackwardVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, projectId: undefined as number | undefined })
const detailVisible = ref(false)
const detailItems = ref<ScheduleBackwardItemVO[]>([])
const load = async () => {
  loading.value = true
  try { const data = await ScheduleApi.getScheduleBackwardPage(query); rows.value = data.list; total.value = data.total }
  finally { loading.value = false }
}
const openDetail = async (row: ScheduleBackwardVO) => {
  detailItems.value = await ScheduleApi.getScheduleBackwardItems(row.id!)
  detailVisible.value = true
}
onMounted(load)
</script>

<style scoped lang="scss">
.query-form {
  margin-top: 16px;
  margin-bottom: -15px;
}

@media (width <= 767px) {
  :deep(.el-form-item),
  :deep(.el-form-item__content) {
    width: 100%;
  }
}
</style>
