<template>
  <Dialog v-model="visible" title="实施方案审批" width="660px">
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
        <!-- 审批人由系统按节点职责自动路由，提交时不再手工指定 -->
        <p>提交后自动进入审批流：服务经理审核（本项目服务经理），重大项目由工程管理部复审（工程管理部方案复审授权持有人）。审批期间保留当前方案版本。</p>
        <el-form-item v-for="node in routedNodes" :key="node.key" :label="node.name">
          {{ node.responsibility === 'SERVICE_MANAGER' ? '自动路由到本项目服务经理' : '自动路由到工程管理部方案复审授权持有人' }}
        </el-form-item>
      </el-form>
    </section>
    <template #footer>
      <el-button v-if="!record && definition && !error" type="primary" :loading="saving" @click="submit" v-hasPermi="['pms:sol-solution:update']">提交审批</el-button>
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
import type { SolutionVO } from '@/api/pms/engineering/solution'
import { useMessage } from '@/hooks/web/useMessage'
const emit = defineEmits<{ changed: [] }>()
const visible = ref(false), loading = ref(false), saving = ref(false), error = ref('')
const solution = ref<SolutionVO>(), record = ref<ReviewApi.ReviewRecord | null>(null)
const definition = ref<ReviewApi.ReviewDefinition>()
const router = useRouter(), message = useMessage()
const statusLabel = computed(() => ({ RUNNING: '审核中', APPROVE: '已通过', REJECT: '已驳回', CANCEL: '已撤回' })[record.value?.status || ''] || record.value?.status)
const history = computed(() => record.value?.reviewsJson ? JSON.parse(record.value.reviewsJson) : [])
// 与后端分支网关一致：工程管理部复审只在重大方案（审核级别 1）参与，普通方案不显示该节点
const routedNodes = computed(() => (definition.value?.nodes || []).filter(
  (node) => node.responsibility === 'SERVICE_MANAGER' || solution.value?.reviewLevel === 1,
))
const open = async (row: SolutionVO) => {
  visible.value = true; loading.value = true; error.value = ''; solution.value = row
  record.value = null; definition.value = undefined
  try {
    record.value = await ReviewApi.read(row.projectId, row.id!)
    if (!record.value) definition.value = await ReviewApi.definition()
  } catch (failure: any) { error.value = failure?.message || '方案审批信息加载失败，请关闭后重试' }
  finally { loading.value = false }
}
const submit = async () => {
  if (!solution.value || !definition.value) return
  saving.value = true
  try {
    record.value = await ReviewApi.start({ projectId: solution.value.projectId, solutionId: solution.value.id!,
      expectedVersion: solution.value.version!, processDefinitionId: definition.value.id })
    message.success('已提交审批'); emit('changed')
  } catch {
    // 拦截器已弹后端错误提示；此处吞掉 rejection，防止逃逸到业务视图错误边界导致整块工作区被替换
  } finally { saving.value = false }
}
const openProcess = () => router.push({ name: 'BpmProcessInstanceDetail', query: { id: record.value!.processInstanceId } })
const refresh = async () => {
  if (!solution.value) return
  saving.value = true
  try { record.value = await ReviewApi.refresh(solution.value.projectId, solution.value.id!); emit('changed') }
  catch {
    // 同 submit：拦截器已提示，仅阻止错误逃逸破坏业务办理区
  }
  finally { saving.value = false }
}
const revise = async () => {
  if (!solution.value) return
  saving.value = true
  try { await ReviewApi.revise(solution.value.projectId, solution.value.id!); message.success('已生成新的方案草稿'); visible.value = false; emit('changed') }
  catch {
    // 同 submit：拦截器已提示，仅阻止错误逃逸破坏业务办理区
  }
  finally { saving.value = false }
}
defineExpose({ open })
</script>
