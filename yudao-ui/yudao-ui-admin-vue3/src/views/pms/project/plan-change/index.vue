<template>
  <ContentWrap>
    <el-alert title="V1.7 计划变更写入口已冻结，仅保留历史查询；PRE-01 工期变更统一从项目详情发起。" type="info" :closable="false" />
    <el-form :model="query" inline class="query-form">
      <el-form-item label="项目"><PmsEntitySelect v-model="query.projectId" :api="ProjectApi.getProjectPage" label-field="name" value-field="id" query-field="name" clearable /></el-form-item>
      <el-form-item label="变更单号"><el-input v-model="query.changeNo" clearable /></el-form-item>
      <el-form-item><el-button @click="load"><Icon icon="ep:search" />查询</el-button></el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无历史计划变更数据">
      <el-table-column prop="changeNo" label="变更单号" width="160" />
      <el-table-column prop="projectId" label="项目" min-width="180"><template #default="{ row }"><ProjectTag :project-id="row.projectId" /></template></el-table-column>
      <el-table-column prop="title" label="变更标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="changeType" label="类型" width="120" />
      <el-table-column prop="status" label="历史状态" width="100" />
      <el-table-column prop="applyTime" label="申请时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="90" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">明细</el-button></template></el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>
  <Dialog v-model="detailVisible" title="历史计划变更明细" width="min(960px, 96%)">
    <el-descriptions :column="descriptionColumns" border>
      <el-descriptions-item label="变更单号">{{ current.changeNo }}</el-descriptions-item>
      <el-descriptions-item label="变更标题">{{ current.title }}</el-descriptions-item>
      <el-descriptions-item label="原因" :span="descriptionColumns">{{ current.reason || '-' }}</el-descriptions-item>
    </el-descriptions>
    <el-table :data="snapshots" class="snapshot-table" empty-text="暂无阶段快照">
      <el-table-column prop="phaseName" label="阶段" min-width="130" />
      <el-table-column prop="beforePlanStart" label="变更前开始" width="130" />
      <el-table-column prop="beforePlanEnd" label="变更前结束" width="130" />
      <el-table-column prop="afterPlanStart" label="变更后开始" width="130" />
      <el-table-column prop="afterPlanEnd" label="变更后结束" width="130" />
    </el-table>
  </Dialog>
</template>

<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import { dateFormatter } from '@/utils/formatTime'
import * as PlanChangeApi from '@/api/pms/project/plan-change'
import * as ProjectApi from '@/api/pms/project/project'
import ProjectTag from '@/components/ProjectTag/index.vue'
import type { PlanChangePhaseSnapshotVO, PlanChangeVO } from '@/api/pms/project/plan-change'

defineOptions({ name: 'PmsPlanChangeHistory' })
const mobile = useMediaQuery('(max-width: 767px)')
const descriptionColumns = computed(() => mobile.value ? 1 : 2)
const loading = ref(false)
const rows = ref<PlanChangeVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, projectId: undefined as number | undefined, changeNo: '' })
const detailVisible = ref(false)
const current = ref<Partial<PlanChangeVO>>({})
const snapshots = ref<PlanChangePhaseSnapshotVO[]>([])
const load = async () => {
  loading.value = true
  try { const data = await PlanChangeApi.getPlanChangePage(query); rows.value = data.list; total.value = data.total }
  finally { loading.value = false }
}
const openDetail = async (row: PlanChangeVO) => {
  current.value = await PlanChangeApi.getPlanChange(row.id!)
  snapshots.value = await PlanChangeApi.getPlanChangeSnapshots(row.id!)
  detailVisible.value = true
}
onMounted(load)
</script>

<style scoped lang="scss">
.query-form {
  margin-top: 16px;
  margin-bottom: -15px;
}

.snapshot-table {
  margin-top: 16px;
}

@media (width <= 767px) {
  :deep(.el-form-item),
  :deep(.el-form-item__content) {
    width: 100%;
  }
}
</style>
