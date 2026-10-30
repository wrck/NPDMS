<template>
  <el-form label-position="top" @submit.prevent="submit">
    <el-alert
      title="保存后系统重新判断阶段条件；移除主责时，请选择保留成员接任。"
      type="info"
      :closable="false"
    />
    <el-form-item label="增补项目经理">
      <UserSelect v-model="addUserIds" option-label="nickname-account" aria-label="增补项目经理" multiple :clearable="false" :scoped-source="managerSource" :disabled-ids="currentIds" placeholder="输入姓名或账号搜索" />
    </el-form-item>
    <el-form-item label="移除项目经理">
      <UserSelect v-model="removeUserIds" aria-label="移除项目经理" multiple :clearable="false" :scoped-source="removalSource" placeholder="不选则保留所有现有经理" />
    </el-form-item>
    <el-form-item label="当前主责项目经理">
      <UserSelect v-model="primaryUserId" aria-label="当前主责项目经理" :scoped-source="primarySource" :current-label="retained.find(item => item.userId === primaryUserId)?.name" placeholder="从保留及新增经理中选择" />
    </el-form-item>
    <el-checkbox v-model="withService" aria-label="同时指派服务经理">同时指派服务经理</el-checkbox>
    <ProjectMemberServiceFields v-if="withService" ref="serviceFields" :project="project" />
    <el-form-item label="调整原因" required>
      <el-input
        v-model="reason"
        aria-label="调整原因"
        type="textarea"
        :rows="3"
        maxlength="500"
        show-word-limit
        placeholder="说明此次人员调整原因"
      />
    </el-form-item>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <div class="actions">
      <el-button :disabled="saving" @click="$emit('cancel')">取消</el-button>
      <el-button type="primary" native-type="submit" :loading="saving">保存成员调整</el-button>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import UserSelect from '@/views/system/user/components/UserSelect.vue'
import { systemUserSelection } from '@/api/system/user/selection'
import { computed, reactive, ref } from 'vue'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import * as Members from '@/api/pms/project/members'
import { createSubmissionIdempotencyState } from '../../projects/submissionIdempotency'
import ProjectMemberServiceFields from './ProjectMemberServiceFields.vue'

const props = defineProps<{ project: ProjectMasterVO; current: Members.ProjectManagers }>()
const emit = defineEmits<{ saved: []; cancel: [] }>()
const addUserIds = ref<number[]>([])
const removeUserIds = ref<number[]>([])
const primaryUserId = ref<number | undefined>(props.current.primaryUserId ?? undefined)
const reason = ref('')
const withService = ref(false)
const serviceFields = ref<InstanceType<typeof ProjectMemberServiceFields>>()
const saving = ref(false)
const error = ref('')
const selectedNames = reactive(new Map<number, string>())
const submission = createSubmissionIdempotencyState()
const currentIds = computed(() => props.current.members.map((item) => item.userId))
const retained = computed(() => [
  ...props.current.members.filter((item) => !removeUserIds.value.includes(item.userId)),
  ...addUserIds.value
    .filter((id) => !currentIds.value.includes(id))
    .map((userId) => ({ userId, name: selectedNames.get(userId) || `用户 ${userId}` }))
])
const managerSource = computed(() => ({
  key: `project-manager-add:${props.current.projectId}:${props.current.version}`,
  search: async ({ keyword, pageNo }: { keyword: string; pageNo: number }) => {
    const result = await Members.getManagerCandidates(props.current.projectId, keyword, pageNo)
    result.list.forEach(item => selectedNames.set(item.userId, item.nickname))
    return { list: result.list.map(item => ({ ...item, id: item.userId })), total: result.total }
  }
}))
const removalSource = computed(() => systemUserSelection.fromCandidates(
  `project-manager-remove:${props.current.projectId}:${props.current.version}`,
  props.current.members.map(item => ({ id: item.userId, nickname: item.name || `用户 ${item.userId}` }))
))
const primarySource = computed(() => systemUserSelection.fromCandidates(
  `project-manager-primary:${props.current.projectId}:${props.current.version}`,
  retained.value.map(item => ({ id: item.userId, nickname: item.name }))
))
const submit = async () => {
  if (saving.value) return
  error.value = ''
  if (!reason.value.trim()) {
    error.value = '请填写调整原因'
    return
  }
  if (
    retained.value.length &&
    !retained.value.some((item) => item.userId === primaryUserId.value)
  ) {
    error.value = '请从保留或新增经理中选择当前主责'
    return
  }
  const serviceManager = withService.value ? serviceFields.value?.selection() : undefined
  if (withService.value && !serviceManager) {
    error.value = '请完整选择服务经理、办事处及适用站点'
    return
  }
  const payload: Members.MemberUpdate = {
    addUserIds: [...addUserIds.value].sort((a, b) => a - b),
    removeUserIds: [...removeUserIds.value].sort((a, b) => a - b),
    primaryUserId: retained.value.length ? primaryUserId.value : undefined,
    reason: reason.value.trim(),
    serviceManager
  }
  saving.value = true
  try {
    await Members.updateMembers(
      props.current.projectId,
      payload,
      props.current.version,
      submission.keyFor({
        projectId: props.current.projectId,
        version: props.current.version,
        ...payload
      })
    )
    emit('saved')
  } catch {
    error.value = '保存未成功。网络异常可重试；若提示版本冲突，请取消后刷新成员再调整。'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.el-select {
  width: 100%;
}
.el-alert,
.el-checkbox {
  margin-bottom: 1rem;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1rem;
}
</style>
