<template>
  <section v-loading="loading" class="cut-card" aria-labelledby="cutover-project-context-title">
    <header class="cut-card__header"><h3 id="cutover-project-context-title"><Icon icon="ep:location" />项目信息</h3><small>{{ sourceLabel }}带入</small></header>
    <div class="cut-card__body">
      <div class="cut-form-grid">
        <div v-for="field in fields" :key="field.label" class="cut-form-field"><label>{{ field.label }}</label><span class="cut-form-value">{{ field.value || '—' }}</span></div>
      </div>
      <p v-if="errorText" role="status" class="context-error">{{ errorText }}</p>
    </div>
  </section>
</template>
<script setup lang="ts">
import { getProject, getProjectMembers, type ProjectMasterVO, type ProjectMemberAssignmentVO } from '@/api/pms/project/projects'
import type { CutoverTaskDetail } from '@/api/pms/cutover'
import { checkPermi } from '@/utils/permission'
import { getDictLabel, DICT_TYPE } from '@/utils/dict'
import { formatWireDateTime } from '../shared/cutoverTaskInteraction'

const props = defineProps<{ detail: CutoverTaskDetail }>()
const project = ref<ProjectMasterVO | null>(null)
const members = ref<ProjectMemberAssignmentVO[]>([])
const loading = ref(false)
const errorText = ref('')
const sourceLabel = computed(() => ({ SELF_CREATED: '项目与设备信息', ITR: 'ITR来源', PROJECT_EVENT: '项目来源' }[props.detail.source.intakeSourceType]))
const memberNames = (roles: string[]) => [...new Set(members.value.filter(member => {
  const start = new Date(member.effectiveFrom).getTime()
  const end = member.effectiveTo ? new Date(member.effectiveTo).getTime() : null
  return roles.includes(member.memberRole) && start <= Date.now() && (end === null || end > Date.now())
}).map(member => member.memberName).filter(Boolean))].join('、')
const fields = computed(() => [
  { label: '项目名称', value: props.detail.project.projectName || props.detail.task.projectName },
  { label: '项目经理', value: project.value?.managerName || memberNames(['PROJECT_MANAGER']) },
  { label: '服务经理', value: memberNames(['SERVICE_MANAGER', 'SERVICE_MANAGER_L1', 'SERVICE_MANAGER_L2']) },
  { label: '所属行业', value: project.value?.industryName || project.value?.industryCode },
  { label: '办事处', value: project.value?.departmentName },
  { label: '项目级别', value: project.value?.majorProjectLevel ? getDictLabel(DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL, project.value.majorProjectLevel) || project.value.majorProjectLevel : null },
  { label: '客户服务等级', value: props.detail.customerServiceLevel?.serviceLevelCode || '三级普通（默认）' },
  { label: '销售', value: memberNames(['SALES_REPRESENTATIVE']) },
  { label: '计划割接时间', value: formatWireDateTime(props.detail.task.scheduledTime) }
])

watch(() => props.detail.task.id, async () => {
  const taskId = String(props.detail.task.id)
  project.value = null; members.value = []; errorText.value = ''
  loading.value = true
  try {
    const results = await Promise.allSettled([
      checkPermi(['pms:project:query']) ? getProject(props.detail.task.projectId) : Promise.resolve(null),
      checkPermi(['pms:project:query']) ? getProjectMembers(props.detail.task.projectId) : Promise.resolve([])
    ])
    if (taskId !== String(props.detail.task.id)) return
    if (results[0].status === 'fulfilled') project.value = results[0].value
    if (results[1].status === 'fulfilled') members.value = results[1].value
    if (results.some(result => result.status === 'rejected')) errorText.value = '部分项目背景信息暂时无法读取，可刷新后重试。'
  } finally { if (taskId === String(props.detail.task.id)) loading.value = false }
}, { immediate: true })
</script>
<style scoped>
.context-error { margin: 12px 0 0; color: #b26a00; font-size: 12px; }
</style>
