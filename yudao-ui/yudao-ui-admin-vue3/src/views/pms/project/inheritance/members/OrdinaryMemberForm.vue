<template>
  <el-form label-position="top" @submit.prevent="submit">
    <el-form-item label="项目角色" required>
      <el-select v-model="memberRole" aria-label="项目角色" :disabled="!!member && !rejoin || saving" @change="clearCandidate">
        <el-option v-for="role in availableRoles" :key="role.value" :value="role.value" :label="role.label" />
      </el-select>
    </el-form-item>
    <p>{{ roleHint }}</p>
    <el-form-item label="人员" required>
      <el-select v-model="userId" aria-label="人员" filterable remote :remote-method="search"
        :loading="searching" :disabled="!candidateReady || saving" placeholder="按姓名或账号搜索" @visible-change="open => open && search('')">
        <el-option v-for="user in candidates" :key="user.id" :value="user.id"
          :label="`${user.nickname}（${user.username}）`" />
        <template #footer>
          <el-button v-if="candidates.length < total" link :loading="searching" @click="more">加载更多人员</el-button>
        </template>
      </el-select>
    </el-form-item>
    <el-form-item label="联系电话">
      <el-input :model-value="selectedPerson?.mobile || ''" aria-label="联系电话" readonly placeholder="选择人员后显示" />
    </el-form-item>
    <el-form-item label="邮箱">
      <el-input :model-value="selectedPerson?.email || ''" aria-label="邮箱" readonly placeholder="选择人员后显示" />
    </el-form-item>
    <el-form-item v-if="memberRole === 'PROJECT_MANAGER' || memberRole === 'SERVICE_MANAGER'" label="主责">
      <el-checkbox v-model="primary" aria-label="设为当前角色主责" :disabled="saving">设为当前角色主责</el-checkbox>
      <p>首次添加自动成为该角色主责；勾选可切换主责，取消勾选不自动撤销已有主责。</p>
    </el-form-item>
    <el-form-item label="备注"><el-input v-model="remark" aria-label="备注" type="textarea" maxlength="500" /></el-form-item>
    <el-form-item label="调整原因" required><el-input v-model="reason" aria-label="调整原因" type="textarea"
      maxlength="500" show-word-limit placeholder="说明加入、替换或调整的原因" /></el-form-item>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <div class="actions">
      <el-button :disabled="saving" @click="$emit('cancel')">取消</el-button>
      <el-button type="primary" native-type="submit" :loading="saving" :disabled="searching">保存成员</el-button>
    </div>
  </el-form>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import * as Members from '@/api/pms/project/unified-members'
import * as Projects from '@/api/pms/project/projects'
import { checkPermi } from '@/utils/permission'
import { createSubmissionIdempotencyState } from '@/views/pms/project/projects/submissionIdempotency'

const props = defineProps<{ project: Projects.ProjectMasterVO; primaryUserId?: number; member?: Members.MemberRecord; rejoin?: boolean }>()
const emit = defineEmits<{ saved: []; cancel: [] }>()
const userId = ref<number>()
const availableRoles = computed(() => Members.memberRoleOptions.filter(role => checkPermi([role.permission])))
const memberRole = ref<Members.ProjectMemberRole>(Members.logicalMemberRole(props.member?.memberRole || '') as Members.ProjectMemberRole
  || (availableRoles.value.some(role => role.value === 'TEAM_MEMBER') ? 'TEAM_MEMBER' : availableRoles.value[0]?.value) || 'TEAM_MEMBER')
const primary = ref(props.member?.memberRole === 'PROJECT_MANAGER'
  ? props.member.userId === props.primaryUserId
  : !!props.member && props.member.assignmentType !== 'COLLABORATOR')
const candidateReady = computed(() => availableRoles.value.some(role => role.value === memberRole.value))
const roleHint = computed(() => ({
  TEAM_MEMBER: '候选为当前租户内具有系统项目经理角色的有效人员，不限制公司；选为团队成员不会成为本项目经理。',
  PROJECT_MANAGER: '候选须同时具备系统项目经理角色及当前项目公司的经理资格。',
  SERVICE_MANAGER: '从当前租户内具有系统服务经理角色的有效人员中选择；主子项目统一使用服务经理角色。',
  SALES_REPRESENTATIVE: '候选为当前租户内具有系统销售代表角色的有效人员。'
})[memberRole.value])
const responsibility = ref(props.member?.responsibility || '')
const remark = ref(props.member?.remark || '')
const reason = ref(''), error = ref(''), searching = ref(false), saving = ref(false)
const candidates = ref<Members.MemberCandidate[]>([]), total = ref(0)
const selectedPerson = computed(() => candidates.value.find(person => person.id === userId.value))
let sequence = 0, keyword = '', pageNo = 1
const submission = createSubmissionIdempotencyState()
const clearCandidate = () => {
  sequence++
  userId.value = undefined
  candidates.value = []
  total.value = 0
  pageNo = 1
  searching.value = false
  error.value = ''
  primary.value = false
}
const candidateParams = () => ({ projectRole: memberRole.value })
const search = async (value: string, append = false) => {
  if (!candidateReady.value) return
  const request = ++sequence
  keyword = value
  const requestedPage = append ? pageNo + 1 : 1
  searching.value = true
  try {
    const result = await Members.getMemberCandidates(props.project.id!, { pageNo: requestedPage, pageSize: 20, keyword: value, ...candidateParams() })
    if (request !== sequence) return
    error.value = ''
    candidates.value = append ? [...new Map([...candidates.value, ...result.list].map(user => [user.id, user])).values()] : result.list
    total.value = result.total
    pageNo = requestedPage
  } catch {
    if (request === sequence) error.value = '人员加载失败，请重新搜索。'
  } finally { if (request === sequence) searching.value = false }
}
const more = () => search(keyword, true)
onMounted(async () => {
  if (!props.member) return
  if (!candidateReady.value) return
  const request = ++sequence
  searching.value = true
  try {
    const result = await Members.getMemberCandidates(props.project.id!, { pageNo: 1, pageSize: 1, userId: props.member.userId, ...candidateParams() })
    if (request !== sequence) return
    candidates.value = result.list
    total.value = result.total
    userId.value = result.list.find(user => user.id === props.member?.userId)?.id
    if (!userId.value) error.value = '原人员当前不可选，请选择有效用户；历史记录保持不变。'
  } catch { if (request === sequence) error.value = '人员校验失败，请重试。' }
  finally { if (request === sequence) searching.value = false }
})
onBeforeUnmount(() => sequence++)
const submit = async () => {
  if (saving.value) return
  error.value = ''
  if (!candidateReady.value || !userId.value || !reason.value.trim() || props.project.version == null) {
    error.value = '请选择项目角色、有效人员并填写调整原因'; return
  }
  const data: Members.MemberMutation = { userId: userId.value, memberRole: memberRole.value,
    responsibility: responsibility.value.trim(), remark: remark.value.trim(), reason: reason.value.trim(),
    primary: memberRole.value === 'PROJECT_MANAGER' || memberRole.value === 'SERVICE_MANAGER' ? primary.value : undefined }
  const assignmentId = props.rejoin ? undefined : props.member?.id
  saving.value = true
  try {
    await Members.saveMember(props.project.id!, assignmentId, data, props.project.version,
      submission.keyFor({ projectId: props.project.id, assignmentId, version: props.project.version, ...data }))
    emit('saved')
  } catch { error.value = '保存未成功。请核对重复成员或人员有效性；版本冲突时取消并刷新后再试。' }
  finally { saving.value = false }
}
</script>
<style scoped>
.el-select { width: 100%; }
p { color: var(--el-text-color-secondary); }
.actions { display: flex; justify-content: flex-end; gap: .5rem; margin-top: 1rem; }
</style>
