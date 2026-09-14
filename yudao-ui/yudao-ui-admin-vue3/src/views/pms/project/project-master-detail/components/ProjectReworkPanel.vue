<template>
  <el-drawer
    v-model="visible"
    title="选择性返工"
    size="min(960px, 96vw)"
    :before-close="beforeClose"
    destroy-on-close
  >
    <p>只为选中节点及必要的所属阶段建立新执行。未选的已完成结果和业务历史保留，不发起审批。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-loading="busy">
      <template v-if="state">
        <el-input
          v-model="search"
          aria-label="查找返工节点"
          placeholder="按名称或阶段定位"
          clearable
          :disabled="busy"
        />
        <el-checkbox-group
          v-model="selection"
          :disabled="busy"
          aria-label="返工节点选择"
          class="rework-selection"
        >
          <el-checkbox
            v-for="row in filtered"
            :key="row.node.nodeKey"
            :value="row.node.nodeKey"
            :disabled="!['DONE', 'TERMINATED'].includes(row.status)"
            :aria-label="`返工 ${row.node.name}（${row.node.code}）`"
          >
            {{ row.node.nodeKind === 'STAGE' ? '阶段' : '任务' }} · {{ row.node.name }}（{{
              row.node.code
            }}） · {{ row.node.stageCode }} · 第 {{ row.roundNo }} 轮 ·
            {{ statusLabel(row.status) }}
          </el-checkbox>
        </el-checkbox-group>
        <el-empty v-if="!filtered.length" description="没有匹配的执行节点" />
        <el-button :disabled="busy || !selection.length" @click="preview">预览返工影响</el-button>
      </template>
      <section v-if="impact" aria-label="返工影响预览" class="rework-preview">
        <el-alert
          v-for="blocker in impact.plan.blockers"
          :key="`${blocker.nodeKey}:${blocker.code}`"
          :title="blocker.message"
          type="warning"
          :closable="false"
        />
        <el-table :data="impact.plan.targets" row-key="executionId">
          <el-table-column label="节点"
            ><template #default="{ row }">{{ row.node.name }}</template></el-table-column
          >
          <el-table-column label="本次范围"
            ><template #default="{ row }">{{
              row.selected ? '选中返工' : '必要所属阶段'
            }}</template></el-table-column
          >
          <el-table-column label="执行轮次"
            ><template #default="{ row }"
              >第 {{ row.roundNo }} 轮 → 第 {{ row.roundNo + 1 }} 轮</template
            ></el-table-column
          >
        </el-table>
        <p v-if="impact.plan.affectedNodeKeys.length"
          >受影响但不自动返工：{{ affectedNames.join('、') }}</p
        >
        <el-input
          v-model="reason"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          aria-label="返工原因"
          placeholder="说明本次返工原因"
          :disabled="busy"
        />
        <el-button type="primary" :disabled="!canApply" :loading="busy" @click="apply"
          >确认返工</el-button
        >
      </section>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import type { BusinessViewId } from '@/api/pms/platform/business-view/ids'
import {
  getReworkState,
  previewRework,
  applyRework,
  type ReworkState,
  type ReworkPreview,
  type ReworkApply
} from '@/api/pms/project/projects/rework'
import { createSubmissionIdempotencyState } from '../../projects/submissionIdempotency'
import { errorText } from '../../project-templates/editorModel'

const props = defineProps<{ projectId: BusinessViewId }>()
const visible = defineModel<boolean>({ default: false })
const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const state = ref<ReworkState>(),
  impact = ref<ReworkPreview>()
const selection = ref<string[]>([]),
  reason = ref(''),
  search = ref(''),
  error = ref(''),
  busy = ref(false)
const submission = createSubmissionIdempotencyState()
let generation = 0
const filtered = computed(
  () =>
    state.value?.nodes.filter((row) =>
      `${row.node.name} ${row.node.code} ${row.node.stageCode}`.includes(search.value.trim())
    ) || []
)
const affectedNames = computed(
  () =>
    impact.value?.plan.affectedNodeKeys.map((key) =>
      key === '$project'
        ? '项目收口'
        : state.value?.nodes.find((row) => row.node.nodeKey === key)?.node.name || key
    ) || []
)
const canApply = computed(
  () =>
    !busy.value &&
    !!reason.value.trim() &&
    !!impact.value?.plan.targets.length &&
    !impact.value.plan.blockers.length
)
const statusLabel = (status: string) =>
  ({ PENDING: '等待办理', ACTIVE: '进行中', DONE: '已完成', TERMINATED: '已终止' })[status] ||
  status
const current = (epoch: number, id: BusinessViewId) =>
  epoch === generation && visible.value && String(id) === String(props.projectId)
watch(
  selection,
  () => {
    impact.value = undefined
    error.value = ''
  },
  { deep: true }
)
watch(
  () => [visible.value, props.projectId],
  async () => {
    const epoch = ++generation,
      id = props.projectId
    state.value = undefined
    impact.value = undefined
    selection.value = []
    reason.value = ''
    search.value = ''
    error.value = ''
    submission.reset()
    if (!visible.value) {
      busy.value = false
      return
    }
    busy.value = true
    try {
      const result = await getReworkState(id)
      if (current(epoch, id)) state.value = result
    } catch (failure) {
      if (current(epoch, id)) error.value = errorText(failure)
    } finally {
      if (current(epoch, id)) busy.value = false
    }
  },
  { immediate: true }
)
const preview = async () => {
  if (busy.value || !selection.value.length) return
  const epoch = generation,
    id = props.projectId
  busy.value = true
  error.value = ''
  try {
    const result = await previewRework(id, [...selection.value])
    if (current(epoch, id)) impact.value = result
  } catch (failure) {
    if (current(epoch, id)) error.value = errorText(failure)
  } finally {
    if (current(epoch, id)) busy.value = false
  }
}
const apply = async () => {
  if (!canApply.value || !impact.value) return
  const epoch = generation,
    id = props.projectId
  const data: ReworkApply = {
    planVersionId: impact.value.planVersionId,
    expectedProjectVersion: impact.value.projectVersion,
    selectedNodeKeys: [...selection.value],
    reason: reason.value.trim(),
    expectedExecutions: impact.value.plan.targets.map((target) => ({
      nodeKey: target.node.nodeKey,
      executionId: target.executionId,
      version: target.executionVersion
    }))
  }
  busy.value = true
  error.value = ''
  try {
    if (!current(epoch, id)) return
    await applyRework(id, data, submission.keyFor(data))
    if (!current(epoch, id)) return
    message.success('返工已生效，新执行将按项目规则重新判定')
    visible.value = false
    emit('changed')
  } catch (failure) {
    if (current(epoch, id)) error.value = errorText(failure)
  } finally {
    if (current(epoch, id)) busy.value = false
  }
}
const beforeClose = (done: () => void) => {
  if (!busy.value) done()
}
onBeforeUnmount(() => {
  generation++
})
</script>

<style scoped>
.rework-selection {
  display: grid;
  gap: 8px;
  margin: 16px 0;
}
.rework-selection :deep(.el-checkbox) {
  height: auto;
  white-space: normal;
}
.rework-preview {
  margin-top: 24px;
  display: grid;
  gap: 12px;
}
</style>
