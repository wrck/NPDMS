<template>
  <component
    :is="approvalContainer" v-model="visible" title="实施方案审批"
    v-bind="projectApproval ? { size: 'min(660px, 100%)', appendToBody: true, destroyOnClose: true } : { width: '660px' }">
    <section v-loading="loading">
      <p>{{ solution?.name }} · 版本 {{ solution?.version }}</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <template v-else-if="record">
        <p>审批状态：{{ statusLabel }}</p>
        <el-button v-hasPermi="['bpm:process-instance:query']" type="primary" plain @click="openProcess">查看流程详情</el-button>
        <el-table :data="history">
          <el-table-column prop="nodeKey" label="审批节点" :formatter="row => reviewNodeLabel(row.nodeKey)" />
          <el-table-column prop="decision" label="结果" :formatter="row => reviewDecisionLabel(row.decision)" />
          <el-table-column prop="reason" label="意见" />
          <el-table-column prop="time" label="时间" min-width="170" :formatter="row => formatDate(row.time) || '—'" />
        </el-table>
      </template>
      <el-form v-else-if="definition" label-position="top">
        <el-alert
v-if="readiness && !readiness.allowed" type="warning" :closable="false"
          :title="`暂不能提交审核：${readiness.blockers.join('、')}。仍可关闭此窗口维护并保存草稿。`" />
        <!-- 审批人由系统按节点职责自动路由，提交时不再手工指定 -->
        <p>提交后自动进入审批流：服务经理审核（本项目服务经理），重大项目由工程管理部复审（工程管理部方案复审授权持有人）。审批期间保留当前方案版本。</p>
        <el-form-item v-for="node in routedNodes" :key="node.key" :label="node.name">
          {{ node.responsibility === 'SERVICE_MANAGER' ? '自动路由到本项目服务经理' : '自动路由到工程管理部方案复审授权持有人' }}
        </el-form-item>
      </el-form>
    </section>
    <template #footer>
      <el-button v-if="!props.readonly && !record && definition && !error" type="primary" :disabled="loading || readiness?.allowed !== true" :loading="saving" @click="submit" v-hasPermi="['pms:sol-solution:update']">提交审批</el-button>
      <el-button v-if="!props.readonly && record?.status === 'RUNNING'" :loading="saving" @click="refresh" v-hasPermi="['pms:sol-solution:update']">刷新审批结果</el-button>
      <el-button v-if="!props.readonly && record && record.status !== 'RUNNING'" type="primary" :loading="saving" @click="revise" v-hasPermi="['pms:sol-solution:create']">复制为新方案版本</el-button>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </component>
</template>
<script setup lang="ts">
import { computed, inject, ref } from 'vue'
import { ElDrawer } from 'element-plus'
import { Dialog } from '@/components/Dialog'
import { projectApprovalDetailKey, useProjectApprovalDetail } from '@/views/pms/project/project-master-detail/components/projectApprovalDetail'
import * as ReviewApi from '@/api/pms/engineering/solution-review'
import type { SolutionVO } from '@/api/pms/engineering/solution'
import { useMessage } from '@/hooks/web/useMessage'
import { formatDate } from '@/utils/formatTime'
import { checkPermi } from '@/utils/permission'
const emit = defineEmits<{ changed: []; revised: [revision: { projectId: number; sourceSolutionId: string | number; solutionId: string | number }] }>()
const props = defineProps<{ readonly?: boolean }>()
const projectApproval = inject(projectApprovalDetailKey, null)
const approvalContainer = projectApproval ? ElDrawer : Dialog
const visible = ref(false), loading = ref(false), saving = ref(false), error = ref('')
const solution = ref<SolutionVO>(), record = ref<ReviewApi.ReviewRecord | null>(null)
const definition = ref<ReviewApi.ReviewDefinition>()
const readiness = ref<ReviewApi.SubmissionReadiness>()
let openSequence = 0
let beforeSubmit: (() => Promise<SolutionVO>) | undefined
const openApprovalDetail = useProjectApprovalDetail(), message = useMessage()
const statusLabel = computed(() => ({ RUNNING: '审核中', APPROVE: '已通过', REJECT: '已驳回', CANCEL: '已撤回' })[record.value?.status || ''] || record.value?.status)
const history = computed(() => record.value?.reviewsJson ? JSON.parse(record.value.reviewsJson) : [])
const reviewNodeLabel = (key: string) => ({ serviceManagerReview: '服务经理审核', engineeringManagementReview: '工程管理部复审' }[key] || key)
const reviewDecisionLabel = (decision: string) => ({ APPROVE: '通过', REJECT: '驳回', CANCEL: '撤回' }[decision] || decision)
// 与后端分支网关一致：工程管理部复审只在重大方案（审核级别 1）参与，普通方案不显示该节点
const routedNodes = computed(() => (definition.value?.nodes || []).filter(
  (node) => node.responsibility === 'SERVICE_MANAGER' || solution.value?.reviewLevel === 1,
))
const open = async (row: SolutionVO, options?: { beforeSubmit: () => Promise<SolutionVO> }) => {
  const sequence = ++openSequence
  beforeSubmit = options?.beforeSubmit
  visible.value = true; loading.value = true; error.value = ''; solution.value = { ...row }
  record.value = null; definition.value = undefined; readiness.value = undefined
  try {
    const currentRecord = row.id ? await ReviewApi.read(row.projectId, row.id) : null
    if (sequence !== openSequence || !visible.value) return
    record.value = currentRecord
    if (record.value?.status === 'RUNNING') await openProcess()
    else if (!record.value) {
      const prepared = await Promise.all([ReviewApi.definition(), ReviewApi.submissionReadiness(row.projectId)])
      if (sequence !== openSequence || !visible.value) return
      ;[definition.value, readiness.value] = prepared
    }
  } catch (failure: any) {
    if (sequence === openSequence) error.value = failure?.message || '方案审批信息加载失败，请关闭后重试'
  } finally { if (sequence === openSequence) loading.value = false }
}
const submit = async () => {
  if (props.readonly || !visible.value || !solution.value || !definition.value || record.value || loading.value || saving.value || readiness.value?.allowed !== true) return
  const sequence = openSequence, prepare = beforeSubmit, processDefinitionId = definition.value.id
  saving.value = true
  try {
    const current = prepare ? await prepare() : solution.value
    if (sequence !== openSequence || !visible.value) return
    solution.value = current
    const started = await ReviewApi.start({ projectId: current.projectId, solutionId: current.id!,
      expectedVersion: current.version!, processDefinitionId })
    if (sequence !== openSequence) { emit('changed'); return }
    record.value = started
    message.success('已提交审批'); emit('changed')
    if (checkPermi(['bpm:process-instance:query'])) await openProcess()
  } catch (failure: any) {
    message.error(failure?.message || '方案保存或提交失败，请修正后重试')
  } finally { saving.value = false }
}
const openProcess = () => {
  visible.value = false
  return openApprovalDetail(record.value!.processInstanceId)
}
const refresh = async () => {
  if (props.readonly || !solution.value) return
  saving.value = true
  try { record.value = await ReviewApi.refresh(solution.value.projectId, solution.value.id!); emit('changed') }
  catch {
    // 同 submit：拦截器已提示，仅阻止错误逃逸破坏业务办理区
  }
  finally { saving.value = false }
}
const revise = async () => {
  if (props.readonly || !solution.value) return
  const source = solution.value
  saving.value = true
  try {
    const solutionId = await ReviewApi.revise(source.projectId, source.id!)
    message.success('已生成新的方案草稿')
    if (solution.value === source) visible.value = false
    emit('revised', { projectId: source.projectId, sourceSolutionId: source.id!, solutionId })
  }
  catch {
    // 同 submit：拦截器已提示，仅阻止错误逃逸破坏业务办理区
  }
  finally { saving.value = false }
}
defineExpose({ open })
</script>
