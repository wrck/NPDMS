<template>
  <ContentWrap aria-label="项目正常闭环">
    <div class="closure-heading"
      ><div
        ><h3>项目正常闭环</h3
        ><p>按项目冻结条件校验，经过真实审批后收口；不会补造阶段或验收结果。</p></div
      ><el-button :loading="loading" :disabled="busy" @click="refresh">刷新</el-button></div
    >
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-skeleton v-if="loading && !overview" :rows="4" animated />
    <template v-if="overview">
      <el-alert
        v-if="!overview.policyAvailable"
        title="本项目未冻结可用的正常闭环规则，不能用默认通过代替。"
        type="warning"
        :closable="false"
      />
      <el-descriptions :column="mobile ? 1 : 2" border>
        <el-descriptions-item label="当前真实阶段">{{
          overview.currentStage
        }}</el-descriptions-item>
        <el-descriptions-item label="项目状态">{{
          lifecycleLabel(overview.lifecycleStatus)
        }}</el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="overview.lifecycleStatus === 'NORMAL_CLOSED'" title="项目已正常闭环，审批和来源记录保留供查询，无需再次校验或提交。" type="success" :closable="false" />
      <ul v-else class="closure-checks"
        ><li v-for="item in overview.checks" :key="`${item.code}:${item.subjectId ?? ''}`"
          ><el-tag :type="item.passed ? 'success' : 'warning'">{{
            item.passed ? '满足' : '待处理'
          }}</el-tag
          ><span>{{ checkLabel(item.reason || item.code) }}</span></li
        ></ul
      >
      <div v-if="overview.latestApplication" class="closure-application">
        <strong>最近闭环申请：{{ applicationLabel(overview.latestApplication.status) }}</strong>
        <p>提交时间：{{ formatDate(overview.latestApplication.submittedAt) }}</p>
        <el-button v-if="overview.latestApplication.processInstanceId" @click="openProcess"
          >查看审批流程与待办</el-button
        >
        <p>审批必须在原工作流中由实际候选人办理，此处不提供直接批准按钮。</p>
      </div>
      <div class="closure-actions">
        <el-button v-if="allowed('CHECK')" :loading="busy" type="primary" @click="check"
          >校验闭环条件</el-button
        >
        <el-button v-if="allowed('SUBMIT')" :loading="busy" type="success" @click="submit"
          >提交闭环审批</el-button
        >
      </div>
    </template>
  </ContentWrap>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from '@/hooks/web/useMessage'
import { formatDate } from '@/utils/formatTime'
import * as ClosureApi from '@/api/pms/project/normal-closure'
import type { ClosureOverview } from '@/api/pms/project/normal-closure'
import type { BusinessViewId } from '@/api/pms/platform/business-view/ids'
const props = defineProps<{ projectId: BusinessViewId; readonly?: boolean }>()
const emit = defineEmits<{ updated: [] }>()
const router = useRouter(),
  route = useRoute(),
  message = useMessage(),
  mobile = useMediaQuery('(max-width: 767px)')
const overview = ref<ClosureOverview>(),
  loading = ref(false),
  busy = ref(false),
  error = ref('')
const needsRefresh = ref(false)
let generation = 0,
  operation = 0,
  disposed = false
// Keep the key even after success: the same submit payload is still the same intent.
const keys = new Map<string, string>()
const key = (value: string) => {
  if (!keys.has(value)) keys.set(value, crypto.randomUUID())
  return keys.get(value)!
}
const eligible = (action: string) => {
  const value = overview.value
  if (
    disposed ||
    props.readonly ||
    loading.value ||
    needsRefresh.value ||
    !value ||
    String(value.projectId) !== String(props.projectId) ||
    value.lifecycleStatus !== 'ACTIVE' ||
    !value.policyAvailable ||
    !value.allowedActions.includes(action) ||
    value.latestApplication?.status === 'IN_REVIEW' ||
    value.latestApplication?.status === 'APPROVED'
  )
    return false
  if (action !== 'SUBMIT') return true
  const snapshot = value.latestSnapshot
  return (
    snapshot?.passed === true &&
    snapshot.projectVersion === value.version &&
    snapshot.treeVersion === value.treeVersion &&
    snapshot.fromStage === value.currentStage
  )
}
const allowed = (action: string) => !busy.value && eligible(action)
const intentOf = (action: string, value: ClosureOverview) =>
  JSON.stringify([
    action,
    String(value.projectId),
    value.version,
    value.treeVersion,
    ...(action === 'submit' ? [String(value.latestSnapshot?.id)] : [])
  ])
const contextIsCurrent = (epoch: number, id: BusinessViewId, path: string) =>
  !disposed &&
  epoch === generation &&
  String(id) === String(props.projectId) &&
  path === route.fullPath
const checkLabel = (code: string) => ({
  CLOSURE_POLICY_FROZEN: '已冻结本项目闭环规则', PROJECT_ACTIVE: '项目处于进行中',
  NOT_TERMINAL: '尚未到达模板最后实际阶段', TERMINAL_STAGE: '已到达模板最后实际阶段',
  STAGE_COMPLETION: '当前阶段完成条件已满足', TASKS_NOT_DONE: '仍有未完成任务',
  ALL_TASKS_DONE: '实际任务均已完成', NEW_CHECK_REQUIRED: '请重新校验并生成闭环快照',
  CHECK_SNAPSHOT: '闭环快照可供提交重验', OWNER_FACTS_REVALIDATED_ON_SUBMIT: '提交时将重新核验业务结果',
  DESCENDANTS_CLOSED: '全部后代项目满足闭环条件', TASK_BUSINESS_FACTS: '任务关联业务结果已核验'
}[code] || code)
const lifecycleLabel = (status: string) =>
  ({
    ACTIVE: '进行中',
    NORMAL_CLOSED: '正常闭环',
    NO_TRACKING_CLOSED: '不予跟踪闭环',
    EXCEPTION_CLOSED: '异常关闭'
  })[status] || status
const applicationLabel = (status: string) =>
  ({ IN_REVIEW: '审批中', APPROVED: '已批准', REJECTED: '已驳回', CANCELLED: '已取消' })[status] ||
  status
const load = async () => {
  const epoch = ++generation,
    id = props.projectId,
    path = route.fullPath
  loading.value = true
  error.value = ''
  try {
    const result = await ClosureApi.getNormalClosure(id)
    if (!contextIsCurrent(epoch, id, path)) return false
    if (!result || String(result.projectId) !== String(id)) throw new Error('Unavailable overview')
    overview.value = result
    needsRefresh.value = false
    return true
  } catch {
    if (contextIsCurrent(epoch, id, path)) {
      overview.value = undefined
      needsRefresh.value = true
      error.value = '无法读取闭环条件，请检查权限或稍后重试。'
    }
    return false
  } finally {
    if (contextIsCurrent(epoch, id, path)) loading.value = false
  }
}
const refresh = () => {
  if (!busy.value && !disposed) return load()
}
const check = async () => {
  if (!overview.value || !allowed('CHECK')) return
  const value = { ...overview.value },
    epoch = generation,
    path = route.fullPath,
    token = ++operation,
    intent = intentOf('check', value)
  busy.value = true
  try {
    await ClosureApi.checkNormalClosure(value, key(intent))
    // A deliberate new check after a confirmed result must re-evaluate source facts.
    keys.delete(intent)
    if (contextIsCurrent(epoch, value.projectId, path)) {
      if ((await load()) && !disposed && token === operation) emit('updated')
    }
  } catch {
    if (contextIsCurrent(epoch, value.projectId, path)) {
      needsRefresh.value = true
      error.value = '条件校验未成功，原项目和闭环历史未被替换。请刷新后重新读取原因再处理。'
    }
  } finally {
    if (!disposed && token === operation) busy.value = false
  }
}
const submit = async () => {
  if (!overview.value || !allowed('SUBMIT')) return
  const value = { ...overview.value },
    snapshot = { ...value.latestSnapshot! },
    epoch = generation,
    path = route.fullPath,
    token = ++operation,
    intent = intentOf('submit', value)
  // Confirmation is part of the operation, not a window for another submission.
  busy.value = true
  try {
    try {
      await message.confirm(
        '将依据当前有效校验快照发起正常闭环审批。审批通过前项目仍保持进行中，是否继续？'
      )
    } catch {
      return
    }
    if (
      !contextIsCurrent(epoch, value.projectId, path) ||
      !eligible('SUBMIT') ||
      intentOf('submit', overview.value!) !== intent ||
      overview.value!.currentStage !== value.currentStage ||
      overview.value!.latestSnapshot?.sourceDigest !== snapshot.sourceDigest
    )
      return
    await ClosureApi.submitNormalClosure(value, snapshot.id, key(intent))
    if (contextIsCurrent(epoch, value.projectId, path)) {
      if ((await load()) && !disposed && token === operation) emit('updated')
    }
  } catch {
    if (contextIsCurrent(epoch, value.projectId, path)) {
      needsRefresh.value = true
      error.value = '提交结果未确认，请刷新查询已有申请；重试同一意图不会重复创建审批。'
    }
  } finally {
    if (!disposed && token === operation) busy.value = false
  }
}
const openProcess = () => {
  const id = overview.value?.latestApplication?.processInstanceId
  if (id) return router.push({ name: 'BpmProcessInstanceDetail', query: { id } })
}
watch(
  () => [props.projectId, route.fullPath],
  () => {
    ++operation
    busy.value = false
    overview.value = undefined
    needsRefresh.value = true
    void load()
  },
  { immediate: true, flush: 'sync' }
)
onBeforeUnmount(() => {
  disposed = true
  ++generation
})
</script>
<style scoped>
.closure-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
h3 {
  margin: 0 0 6px;
}
p {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.closure-checks {
  list-style: none;
  padding: 0;
}
.closure-checks li {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.closure-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}
.closure-application {
  padding: 16px;
  background: var(--el-fill-color-light);
}
@media (max-width: 767px) {
  .closure-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .closure-checks li {
    align-items: flex-start;
  }
  .closure-actions {
    flex-wrap: wrap;
  }
}
</style>
