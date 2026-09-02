<template>
  <section aria-labelledby="reassignment-heading">
    <div class="section-heading"
      ><h3 id="reassignment-heading">审批改派</h3
      ><el-tag v-if="view.holdReason" type="warning">{{ holdLabels[view.holdReason] }}</el-tag></div
    >
    <el-table :data="view.nodes" row-key="nodeId">
      <el-table-column prop="nodeNo" label="顺序" width="70" />
      <el-table-column label="节点" min-width="130"
        ><template #default="{ row }">{{ nodeLabels[row.nodeCode] }}</template></el-table-column
      >
      <el-table-column prop="currentApproverUserId" label="当前审批人ID" min-width="150" />
      <el-table-column label="状态" width="100"
        ><template #default="{ row }">{{
          row.nodeStatus === 'PENDING' ? '审批中' : '等待中'
        }}</template></el-table-column
      >
      <el-table-column label="操作" width="90"
        ><template #default="{ row }"
          ><el-button
            link
            type="primary"
            data-testid="select-reassignment"
            v-hasPermi="['pms:cutover-task:reassign-approval']"
            @click="selectedNodeNo = row.nodeNo"
            >选择</el-button
          ></template
        ></el-table-column
      >
    </el-table>
    <el-form label-position="top" class="reassignment-form">
      <el-form-item label="节点序号"
        ><el-input-number v-model="selectedNodeNo" :min="1" :disabled="busy"
      /></el-form-item>
      <el-form-item label="新审批人用户ID"
        ><el-input v-model="newApproverUserId" data-testid="new-approver-id" :disabled="busy"
      /></el-form-item>
      <el-form-item label="改派原因"
        ><el-input
          v-model="reason"
          data-testid="reassignment-reason"
          maxlength="1000"
          :disabled="busy"
      /></el-form-item>
      <el-form-item label=" "
        ><el-button
          type="primary"
          data-testid="submit-reassignment"
          :loading="busy"
          :disabled="!canSubmit"
          v-hasPermi="['pms:cutover-task:reassign-approval']"
          @click="submit"
          >确认改派</el-button
        ></el-form-item
      >
    </el-form>
  </section>
</template>

<script setup lang="ts">
import type {
  CutoverApprovalNodeCode,
  CutoverApprovalReassignmentView,
  WireLong
} from '@/api/pms/cutover/cutover-task'
const props = defineProps<{ view: CutoverApprovalReassignmentView; busy: boolean }>()
const emit = defineEmits<{
  reassign: [value: { nodeNo: number; newApproverUserId: WireLong; reason: string }]
}>()
const selectedNodeNo = ref(props.view.nodes[0]?.nodeNo || 1)
const newApproverUserId = ref('')
const reason = ref('')
watch(
  () => `${props.view.approvalInstanceId}:${props.view.approvalVersion}`,
  () => {
    selectedNodeNo.value = props.view.nodes[0]?.nodeNo || 1
    newApproverUserId.value = ''
    reason.value = ''
  }
)
const canSubmit = computed(
  () => /^[1-9]\d*$/.test(newApproverUserId.value.trim()) && Boolean(reason.value.trim())
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
const submit = () =>
  emit('reassign', {
    nodeNo: selectedNodeNo.value,
    newApproverUserId: newApproverUserId.value.trim(),
    reason: reason.value.trim()
  })
</script>

<style scoped>
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-heading h3 {
  margin: 0 0 14px;
}

.reassignment-form {
  display: grid;
  grid-template-columns: 120px minmax(180px, 1fr) minmax(220px, 2fr) auto;
  gap: 0 12px;
  margin-top: 18px;
}

@media (width <= 1023px) {
  .reassignment-form {
    grid-template-columns: 1fr 1fr;
  }
}

@media (width <= 767px) {
  .reassignment-form {
    grid-template-columns: 1fr;
  }
}
</style>
