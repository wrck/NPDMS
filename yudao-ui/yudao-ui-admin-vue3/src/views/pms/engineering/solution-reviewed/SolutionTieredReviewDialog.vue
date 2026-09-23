<template>
  <Dialog v-model="visible" title="实施方案分级审核" width="660px">
    <section v-loading="loading">
      <p>{{ solution?.name }} · 版本 {{ solution?.version }}</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <template v-else-if="record">
        <p>审批状态：{{ statusLabel }}</p>
        <el-button type="primary" plain @click="openProcess">查看审批过程</el-button>
        <el-table :data="history">
          <el-table-column prop="nodeKey" label="审批节点" />
          <el-table-column prop="decision" label="结果" />
          <el-table-column prop="reason" label="意见" />
          <el-table-column prop="time" label="时间" />
        </el-table>
      </template>
      <el-form v-else-if="definition" label-position="top">
        <p>服务经理初审，工程管理部复审。审批期间保留当前方案版本。</p>
        <el-form-item v-for="node in definition.nodes" :key="node.key" :label="node.name" required>
          <el-select v-model="candidates[node.key]" filterable clearable :aria-label="node.name" placeholder="选择本项目范围内的审批人" class="w-full">
            <el-option v-for="user in users" :key="user.id" :value="user.id" :label="user.nickname" />
          </el-select>
        </el-form-item>
      </el-form>
    </section>
    <template #footer>
      <el-button v-if="!record && definition && !error" type="primary" :loading="saving" :disabled="!completeCandidates" @click="submit" v-hasPermi="['pms:sol-solution:update']">提交分级审核</el-button>
      <el-button v-if="record?.status === 'RUNNING'" :loading="saving" @click="refresh" v-hasPermi="['pms:sol-solution:update']">刷新审批结果</el-button>
      <el-button v-if="record && record.status !== 'RUNNING'" type="primary" :loading="saving" @click="revise" v-hasPermi="['pms:sol-solution:create']">复制为新方案版本</el-button>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </Dialog>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as ReviewApi from '@/api/pms/engineering/solution-review'
import * as UserApi from '@/api/system/user'
import type { SolutionVO } from '@/api/pms/engineering/solution'
import { useMessage } from '@/hooks/web/useMessage'
const emit = defineEmits<{ changed: [] }>()
const visible = ref(false), loading = ref(false), saving = ref(false), error = ref('')
const solution = ref<SolutionVO>(), record = ref<ReviewApi.ReviewRecord | null>(null)
const definition = ref<ReviewApi.ReviewDefinition>(), users = ref<UserApi.UserVO[]>([])
const candidates = ref<Record<string, number | string>>({})
const router = useRouter(), message = useMessage()
const statusLabel = computed(() => ({ RUNNING: '审核中', APPROVE: '已通过', REJECT: '已驳回', CANCEL: '已撤回' })[record.value?.status || ''] || record.value?.status)
const history = computed(() => record.value?.reviewsJson ? JSON.parse(record.value.reviewsJson) : [])
const completeCandidates = computed(() => definition.value?.nodes.every(node => !!candidates.value[node.key]))
const open = async (row: SolutionVO) => {
  visible.value = true; loading.value = true; error.value = ''; solution.value = row
  record.value = null; definition.value = undefined; candidates.value = {}
  try {
    record.value = await ReviewApi.read(row.projectId, row.id!)
    if (!record.value) [definition.value, users.value] = await Promise.all([ReviewApi.definition(), UserApi.getSimpleUserList()])
  } catch (failure: any) { error.value = failure?.message || '方案审批信息加载失败，请关闭后重试' }
  finally { loading.value = false }
}
const submit = async () => {
  if (!solution.value || !definition.value || !completeCandidates.value) return
  saving.value = true
  try {
    record.value = await ReviewApi.start({ projectId: solution.value.projectId, solutionId: solution.value.id!,
      expectedVersion: solution.value.version!, processDefinitionId: definition.value.id, candidates: candidates.value })
    message.success('已提交分级审核'); emit('changed')
  } finally { saving.value = false }
}
const openProcess = () => router.push({ name: 'BpmProcessInstanceDetail', query: { id: record.value!.processInstanceId } })
const refresh = async () => {
  if (!solution.value) return
  saving.value = true
  try { record.value = await ReviewApi.refresh(solution.value.projectId, solution.value.id!); emit('changed') }
  finally { saving.value = false }
}
const revise = async () => {
  if (!solution.value) return
  saving.value = true
  try { await ReviewApi.revise(solution.value.projectId, solution.value.id!); message.success('已生成新的方案草稿'); visible.value = false; emit('changed') }
  finally { saving.value = false }
}
defineExpose({ open })
</script>
