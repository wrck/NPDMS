<template>
  <ContentWrap>
    <header class="page-heading">
      <div><h1>初验 / 终验报告</h1><p>管理不可变报告版本、附件历史与交付件归档状态。</p></div>
      <el-button :loading="loading" @click="load"><Icon icon="ep:refresh" />刷新</el-button>
    </header>
    <el-form :model="query" class="query-form" label-position="top" @submit.prevent>
      <el-form-item label="项目">
        <PmsEntitySelect v-model="query.projectId" :api="ProjectApi.getProjectPage" :label-field="['code', 'name']" value-field="id" query-field="name" placeholder="选择项目查看初验与终验" clearable class="project-select" />
      </el-form-item>
      <el-button type="primary" :disabled="!query.projectId" @click="load"><Icon icon="ep:search" />查询报告活动</el-button>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-skeleton v-if="loading" :rows="4" animated aria-label="正在加载验收报告活动" />
    <el-empty v-else-if="!activities.length" description="请选择有权限的项目，或当前项目尚未形成初验/终验活动" />
    <section v-else class="activity-grid" aria-label="验收报告活动列表">
      <article v-for="item in activities" :key="item.id" class="activity-card">
        <div class="activity-title"><div><span class="eyebrow">{{ typeLabel(item.acceptanceType) }}</span><h2>{{ typeLabel(item.acceptanceType) }}报告</h2></div><el-tag :type="item.activityStatus === 'COMPLETED' ? 'success' : 'warning'">{{ activityStatusLabel(item.activityStatus) }}</el-tag></div>
        <dl><div><dt>项目任务</dt><dd>{{ item.projectTaskId }}</dd></div><div><dt>活动版本</dt><dd>{{ item.version }}</dd></div><div><dt>当前报告</dt><dd>{{ item.currentReportVersionId ? '已生效' : '未生效' }}</dd></div></dl>
        <el-button type="primary" plain class="open-button" @click="detailRef?.open(item.id)">进入报告工作台</el-button>
      </article>
    </section>
  </ContentWrap>
  <AcceptanceReportDetail ref="detailRef" @changed="load" />
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import * as ProjectApi from '@/api/pms/project/project'
import * as ReportApi from '@/api/pms/project/acceptance-report'
import type { AcceptanceActivityVO } from '@/api/pms/project/acceptance-report'
import AcceptanceReportDetail from './detail.vue'

defineOptions({ name: 'PmsAcceptanceReport' })
const route = useRoute()
const loading = ref(false)
const activities = ref<AcceptanceActivityVO[]>([])
const detailRef = ref<InstanceType<typeof AcceptanceReportDetail>>()
const query = reactive<{ projectId?: number }>({})

const load = async () => {
  if (!query.projectId) { activities.value = []; return }
  loading.value = true
  try { activities.value = await ReportApi.getActivities(query.projectId) } finally { loading.value = false }
}
const typeLabel = (type: string) => (type === 'FINAL' ? '终验' : '初验')
const activityStatusLabel = (status: string) => ({ PENDING: '待完成', COMPLETED: '已完成' })[status] || status

onMounted(() => {
  const projectId = Number(route.query.projectId)
  if (Number.isSafeInteger(projectId) && projectId > 0) { query.projectId = projectId; load() }
})
</script>

<style scoped lang="scss">
.page-heading, .query-form, .activity-title { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-heading h1 { margin: 0; font-size: 24px; color: var(--el-text-color-primary); }
.page-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); }
.query-form { justify-content: flex-start; margin-top: 20px; }.query-form :deep(.el-form-item) { margin-bottom: 0; }.project-select { width: min(440px, 100%); }
.activity-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.activity-card { padding: 20px; border: 1px solid var(--el-border-color-lighter); border-radius: var(--el-border-radius-base); background: var(--el-fill-color-blank); }
.activity-title { align-items: flex-start; }.activity-title h2 { margin: 4px 0 0; font-size: 18px; color: var(--el-text-color-primary); }.eyebrow { font-size: 12px; color: var(--el-color-primary); }
.activity-card dl { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 20px 0; }.activity-card dt { font-size: 12px; color: var(--el-text-color-secondary); }.activity-card dd { margin: 4px 0 0; color: var(--el-text-color-primary); }.open-button { width: 100%; }
@media (width <= 767px) { .page-heading, .query-form { align-items: stretch; flex-direction: column; }.query-form .el-button { width: 100%; }.activity-grid { grid-template-columns: 1fr; }.activity-card dl { grid-template-columns: 1fr 1fr; } }
</style>
