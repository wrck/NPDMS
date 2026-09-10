<template>
  <ContentWrap>
    <el-form :model="query" inline class="-mb-15px query-form" @submit.prevent>
      <el-form-item label="项目">
        <el-input v-if="scoped" :model-value="projectName || `项目 #${projectId}`" disabled class="!w-220px" />
        <PmsEntitySelect v-else v-model="query.projectId" :api="ProjectApi.getProjectPage" :label-field="['projectCode', 'projectName']" value-field="id" query-field="projectName" placeholder="选择项目查看初验与终验" clearable :disabled="detailRef?.isDirty()" class="!w-220px" />
      </el-form-item>
      <el-form-item><el-button :loading="loading" :disabled="!validProject" @click="load"><Icon icon="ep:search" />查询报告活动</el-button></el-form-item>
    </el-form>
    <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" />
  </ContentWrap>

  <ContentWrap>
    <el-skeleton v-if="loading" :rows="4" animated aria-label="正在加载验收报告活动" />
    <el-table v-else :data="activities" data-testid="acceptance-activities" aria-label="验收报告活动列表"
      :empty-text="errorText ? '验收活动未加载成功' : validProject ? '当前可见范围暂无验收活动，不自动创建或判定完成' : '请选择有权限的项目'">
      <el-table-column prop="id" label="活动编号" min-width="180" />
      <el-table-column label="验收类型" width="110"><template #default="{ row }">{{ typeLabel(row.acceptanceType) }}</template></el-table-column>
      <el-table-column label="活动状态" width="110"><template #default="{ row }"><el-tag :type="row.activityStatus === 'COMPLETED' ? 'success' : 'warning'">{{ activityStatusLabel(row.activityStatus) }}</el-tag></template></el-table-column>
      <el-table-column prop="projectTaskId" label="来源任务" min-width="170" />
      <el-table-column prop="version" label="活动版本" width="100" />
      <el-table-column label="当前报告" width="110"><template #default="{ row }">{{ row.currentReportVersionId ? '已生效' : '未生效' }}</template></el-table-column>
      <el-table-column label="操作" width="160" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="detailRef?.open(row.id)">进入报告工作台</el-button></template></el-table-column>
    </el-table>
  </ContentWrap>
  <AcceptanceReportDetail ref="detailRef" @changed="load" />
</template>

<script setup lang="ts">
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import * as ProjectApi from '@/api/pms/project/projects'
import * as ReportApi from '@/api/pms/project/acceptance-report'
import type { AcceptanceActivityVO } from '@/api/pms/project/acceptance-report'
import AcceptanceReportDetail from './detail.vue'

defineOptions({ name: 'PmsAcceptanceReport' })
const props = defineProps<{ projectId?: number; projectName?: string }>()
const route = useRoute()
const message = useMessage()
const loading = ref(false)
const errorText = ref('')
const activities = ref<AcceptanceActivityVO[]>([])
const detailRef = ref<InstanceType<typeof AcceptanceReportDetail>>()
const query = reactive<{ projectId?: number }>({})
const scoped = computed(() => props.projectId !== undefined)
const effectiveProjectId = computed(() => scoped.value ? props.projectId : query.projectId)
const validProject = computed(() => Number.isSafeInteger(effectiveProjectId.value) && Number(effectiveProjectId.value) > 0)
let loadSequence = 0

const requestLeave = () => {
  if (!detailRef.value?.isDirty()) return true
  message.warning('请先保存或关闭验收报告编辑，再切换页面。')
  return false
}
onBeforeRouteLeave(requestLeave)
onBeforeRouteUpdate(requestLeave)

const load = async () => {
  const sequence = ++loadSequence
  errorText.value = ''
  if (!validProject.value) {
    activities.value = []
    loading.value = false
    if (scoped.value || effectiveProjectId.value !== undefined) errorText.value = '项目上下文无效，未查询其他项目。'
    return
  }
  loading.value = true
  try {
    const result = await ReportApi.getActivities(effectiveProjectId.value)
    if (sequence === loadSequence) activities.value = result
  } catch {
    if (sequence === loadSequence) errorText.value = '验收活动加载失败，请检查访问权限或重试；这不代表验收已完成。'
  } finally { if (sequence === loadSequence) loading.value = false }
}
const typeLabel = (type: string) => (type === 'FINAL' ? '终验' : '初验')
const activityStatusLabel = (status: string) => ({ PENDING: '待完成', COMPLETED: '已完成' })[status] || status

watch(() => route.query.projectId, (value) => {
  if (!scoped.value) query.projectId = value === undefined ? undefined : Number(value)
}, { immediate: true })
watch(effectiveProjectId, () => {
  activities.value = []
  detailRef.value?.close()
  void load()
}, { immediate: true })
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (!detailRef.value?.isDirty()) return
  event.preventDefault()
  event.returnValue = ''
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => { loadSequence++; window.removeEventListener('beforeunload', beforeUnload) })
defineExpose({ requestLeave })
</script>

<style scoped lang="scss">
@media (width <= 767px) { .query-form :deep(.el-form-item) { width: 100%; } }
</style>
