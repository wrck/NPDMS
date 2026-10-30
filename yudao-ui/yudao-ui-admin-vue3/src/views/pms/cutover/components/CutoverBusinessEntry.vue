<template>
  <section :aria-label="label">
    <p>{{ label }}使用当前割接任务和审批记录办理。</p>
    <el-button v-if="allowedActions.includes('QUERY')" type="primary" plain :loading="loading"
      data-testid="open-cutover-business" @click="open">查看{{ label }}</el-button>
    <el-alert v-else title="当前没有查看该业务的权限" type="info" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
  </section>
</template>

<script setup lang="ts">
import { getCutoverApproval } from '@/api/pms/cutover'
import { isBusinessViewId, type BusinessViewId } from '@/api/pms/platform/business-view/ids'

const props = defineProps<{
  projectId: BusinessViewId
  objectId?: BusinessViewId
  mode: 'TASK' | 'APPROVAL'
  allowedActions: string[]
}>()
const router = useRouter()
const label = computed(() => props.mode === 'APPROVAL' ? '割接审批' : '割接上线')
const loading = ref(false)
const error = ref('')
let sequence = 0
const open = async () => {
  if (loading.value || !props.allowedActions.includes('QUERY') || !isBusinessViewId(props.projectId)) return
  if (props.objectId == null) {
    await router.push({ path: '/pms/cutover/tasks', query: { projectId: String(props.projectId) } })
    return
  }
  if (!isBusinessViewId(props.objectId)) return
  if (props.mode === 'TASK') {
    await router.push(`/pms/cutover/tasks/${props.objectId}`)
    return
  }
  // CUT business-view object identity is a task id, including the approval view.
  const request = ++sequence
  const taskId = props.objectId
  loading.value = true
  error.value = ''
  try {
    const approval = await getCutoverApproval(taskId)
    if (request !== sequence || !props.allowedActions.includes('QUERY')) return
    if (String(approval.taskId) !== String(taskId) || !isBusinessViewId(approval.approvalInstanceId)) {
      throw new Error('APPROVAL_CONTEXT_MISMATCH')
    }
    await router.push(`/pms/cutover/approvals/${approval.approvalInstanceId}`)
  } catch {
    if (request === sequence) error.value = '割接审批加载失败，请重试。'
  } finally {
    if (request === sequence) loading.value = false
  }
}
watch(() => [props.projectId, props.objectId, props.mode], () => { sequence++; loading.value = false; error.value = '' })
onBeforeUnmount(() => { sequence++ })
</script>
