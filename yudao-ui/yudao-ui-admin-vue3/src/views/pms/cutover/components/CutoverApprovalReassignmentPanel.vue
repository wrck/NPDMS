<template>
  <section class="reassignment-panel" aria-label="审批改派">
    <h3 v-if="!embedded" class="reassignment-heading">审批改派</h3>
    <el-tag v-if="view.holdReason" type="warning">{{ holdLabels[view.holdReason] }}</el-tag>
    <el-form label-position="top">
      <div class="reassignment-fields">
        <el-form-item label="审批节点" required>
          <el-select v-model="selectedNodeNo" data-testid="reassignment-node" placeholder="请选择审批节点" :disabled="busy">
            <el-option v-for="node in view.nodes" :key="node.nodeId" :label="`${node.nodeNo}. ${nodeLabels[node.nodeCode]}`" :value="node.nodeNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="当前审批人">
          <div class="current-approver">
            <span>{{ selectedNode?.currentApproverUserId == null ? '未分配' : userNames[String(selectedNode.currentApproverUserId)] || '姓名暂不可用' }}</span>
            <el-tag v-if="selectedNode" size="small" :type="selectedNode.nodeStatus === 'PENDING' ? 'primary' : 'info'">{{ selectedNode.nodeStatus === 'PENDING' ? '审批中' : '等待中' }}</el-tag>
          </div>
        </el-form-item>
      </div>
      <el-alert v-if="usersError" :title="usersError" type="error" :closable="false" class="users-error">
        <el-button link type="primary" data-testid="retry-reassignment-users" :loading="usersLoading" @click="$emit('retry-users')">重新加载人员</el-button>
      </el-alert>
      <el-form-item label="新审批人" required>
        <el-select v-model="newApproverUserId" data-testid="new-approver-id" filterable clearable placeholder="搜索并选择新审批人" :loading="usersLoading" :disabled="busy || usersLoading || !!usersError || !selectedNode">
          <el-option v-for="(name, id) in userNames" :key="id" :label="name" :value="String(id)" :disabled="String(id) === String(selectedNode?.currentApproverUserId)" />
        </el-select>
      </el-form-item>
      <el-form-item label="改派原因" required>
        <el-input v-model="reason" data-testid="reassignment-reason" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="请说明人员调整的原因" :disabled="busy" />
      </el-form-item>
      <div class="reassignment-actions">
        <el-button v-if="embedded" :disabled="busy" @click="$emit('cancel')">取消</el-button>
        <el-button type="primary" data-testid="submit-reassignment" :loading="busy" :disabled="!canSubmit" v-hasPermi="['pms:cutover-task:reassign-approval']" @click="submit">确认改派</el-button>
      </div>
    </el-form>
  </section>
</template>

<script setup lang="ts">
import type {
  CutoverApprovalNodeCode,
  CutoverApprovalReassignmentView,
  WireLong
} from '@/api/pms/cutover'
const props = defineProps<{
  view: Pick<CutoverApprovalReassignmentView, 'approvalInstanceId' | 'approvalVersion' | 'holdReason'> & {
    nodes: Omit<CutoverApprovalReassignmentView['nodes'][number], 'nodeVersion'>[]
  }
  busy: boolean
  embedded?: boolean
  userNames?: Record<string, string>
  usersLoading?: boolean
  usersError?: string
}>()
const emit = defineEmits<{
  reassign: [value: { nodeNo: number; newApproverUserId: WireLong; reason: string }]
  'retry-users': []
  cancel: []
}>()
const userNames = computed(() => props.userNames || {})
const selectedNodeNo = ref(props.view.nodes[0]?.nodeNo || 1)
const selectedNode = computed(() => props.view.nodes.find(node => node.nodeNo === selectedNodeNo.value))
const newApproverUserId = ref('')
const reason = ref('')
watch(selectedNodeNo, () => { newApproverUserId.value = ''; reason.value = '' })
watch(
  () => `${props.view.approvalInstanceId}:${props.view.approvalVersion}`,
  () => {
    selectedNodeNo.value = props.view.nodes[0]?.nodeNo || 1
    newApproverUserId.value = ''
    reason.value = ''
  }
)
const canSubmit = computed(
  () => !props.busy && !props.usersLoading && !props.usersError && !!selectedNode.value
    && !!userNames.value[newApproverUserId.value] && /^[1-9]\d*$/.test(newApproverUserId.value)
    && newApproverUserId.value !== String(selectedNode.value.currentApproverUserId)
    && Boolean(reason.value.trim()) && reason.value.trim().length <= 1000
)
const nodeLabels: Record<CutoverApprovalNodeCode, string> = {
  INITIATOR: '发起人',
  SERVICE_MANAGER: '服务经理',
  SECOND_LINE: '二线审批',
  RND: '研发审批'
}
const holdLabels = {
  ROUTE_CANDIDATE_NOT_UNIQUE: '候选人不唯一',
  APPROVER_UNAVAILABLE: '审批人当前不可用'
}
const submit = () => {
  if (!canSubmit.value) return
  emit('reassign', {
    nodeNo: selectedNodeNo.value,
    newApproverUserId: newApproverUserId.value.trim(),
    reason: reason.value.trim()
  })
}
</script>

<style scoped>
.reassignment-heading {
  margin: 0 0 14px;
}
.reassignment-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.el-select { width: 100%; }
.current-approver {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 32px;
  color: var(--el-text-color-primary);
}
.users-error { margin-bottom: 18px; }
.reassignment-actions { display: flex; justify-content: flex-end; gap: 8px; }
@media (width <= 767px) {
  .reassignment-fields {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
