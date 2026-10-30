<template>
  <el-form label-position="top" @submit.prevent="submit">
    <el-form-item label="项目角色" required>
      <el-select v-model="memberRole" aria-label="项目角色" :disabled="!!member && !rejoin || saving" @change="clearCandidate">
        <el-option v-for="role in availableRoles" :key="role.value" :value="role.value" :label="role.label" :disabled="role.value === memberRoles.SERVICE_MANAGER && !canMaintainServiceManagers" />
      </el-select>
    </el-form-item>
    <p>{{ roleHint }}</p>
    <template v-if="isExternal">
      <el-form-item label="姓名" required><el-input v-model="externalName" aria-label="姓名" maxlength="128" :disabled="saving" /></el-form-item>
      <el-form-item label="单位"><el-input v-model="externalCompany" aria-label="单位" maxlength="255" :disabled="saving" /></el-form-item>
      <el-form-item label="部门"><el-input v-model="externalDepartment" aria-label="部门" maxlength="64" :disabled="saving" /></el-form-item>
      <el-form-item label="职务"><el-input v-model="externalTitle" aria-label="职务" maxlength="64" :disabled="saving" /></el-form-item>
      <el-form-item label="联系电话"><el-input v-model="externalMobile" aria-label="联系电话" maxlength="64" :disabled="saving" /></el-form-item>
      <el-form-item label="邮箱"><el-input v-model="externalEmail" aria-label="邮箱" maxlength="255" :disabled="saving" /></el-form-item>
    </template>
    <template v-else>
      <el-form-item label="人员" required>
        <UserSelect
v-model="userId" aria-label="人员" option-label="nickname-account" :scoped-source="memberSource"
          :disabled="!candidateReady || saving || searching" :clearable="false" placeholder="按姓名、账号或邮箱搜索"
          :current-label="selectedPerson ? formatSystemUserLabel(selectedPerson) : undefined" @change="selectPerson" />
      </el-form-item>
      <el-form-item label="联系电话">
        <el-input :model-value="selectedPerson?.mobile || ''" aria-label="联系电话" readonly placeholder="选择人员后显示" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input :model-value="selectedPerson?.email || ''" aria-label="邮箱" readonly placeholder="选择人员后显示" />
      </el-form-item>
    </template>
    <el-form-item v-if="memberRole === memberRoles.PROJECT_MANAGER || memberRole === memberRoles.SERVICE_MANAGER" label="主责">
      <el-checkbox v-model="primary" aria-label="设为当前角色主责" :disabled="saving || memberRole === memberRoles.PROJECT_MANAGER && !canAppointPrimary">设为当前角色主责</el-checkbox>
      <p v-if="memberRole === memberRoles.PROJECT_MANAGER && !canAppointPrimary" class="form-helper">主责项目经理只能由本项目有效服务经理指定。</p>
      <p class="form-helper">首次添加自动成为该角色主责；勾选可切换主责，取消勾选不自动撤销已有主责。</p>
    </el-form-item>
    <el-form-item label="备注"><el-input v-model="remark" aria-label="备注" type="textarea" maxlength="500" /></el-form-item>
    <el-form-item label="调整原因"><el-input
v-model="reason" aria-label="调整原因" type="textarea"
      maxlength="500" show-word-limit placeholder="选填，说明加入或调整的原因" /></el-form-item>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <div class="actions">
      <el-button :disabled="saving" @click="$emit('cancel')">取消</el-button>
      <el-button type="primary" native-type="submit" :loading="saving" :disabled="searching">保存成员</el-button>
    </div>
  </el-form>
</template>
<script setup lang="ts">
import UserSelect from '@/views/system/user/components/UserSelect.vue'
import { sameSystemUserId, type SystemUserSelectionUser } from '@/api/system/user/selection'
import { formatSystemUserLabel } from '@/components/SystemUserSelect/userLabel'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import * as Members from '@/api/pms/project/unified-members'
import * as Projects from '@/api/pms/project/projects'
import { checkPermi } from '@/utils/permission'
import { createSubmissionIdempotencyState } from '@/views/pms/project/projects/submissionIdempotency'

const memberRoles = Members.MEMBER_ROLE
const props = defineProps<{ project: Projects.ProjectMasterVO; primaryUserId?: number; member?: Members.MemberRecord; rejoin?: boolean; defaultRole?: Members.ProjectMemberRole }>()
const emit = defineEmits<{ saved: []; cancel: [] }>()
const userId = ref<number>()
const availableRoles = computed(() => Members.memberRoleOptions.filter(role => checkPermi([role.permission])))
const memberRole = ref<Members.ProjectMemberRole>(Members.logicalMemberRole(props.member?.memberRole || '') as Members.ProjectMemberRole
  || (props.defaultRole && availableRoles.value.some(role => role.value === props.defaultRole) ? props.defaultRole : undefined)
  || (availableRoles.value.some(role => role.value === memberRoles.TEAM_MEMBER) ? memberRoles.TEAM_MEMBER : availableRoles.value[0]?.value) || memberRoles.TEAM_MEMBER)
const isExternal = computed(() => memberRole.value === memberRoles.END_USER)
const externalName = ref(props.member?.memberName || '')
const externalCompany = ref(props.member?.companyName || '')
const externalDepartment = ref(props.member?.departmentName || '')
const externalTitle = ref(props.member?.title || '')
const externalMobile = ref(props.member?.mobile || '')
const externalEmail = ref(props.member?.email || '')
const primary = ref(props.member?.memberRole === memberRoles.PROJECT_MANAGER
  ? sameSystemUserId(props.member.userId, props.primaryUserId)
  : !!props.member && props.member.assignmentType !== 'COLLABORATOR')
const canAppointPrimary = ref(false)
const canMaintainServiceManagers = ref(false)
onMounted(async () => {
  const id = props.project.id!
  const [appointment, maintenance] = await Promise.all([
    Members.canAppointPrimaryProjectManager(id).catch(() => false),
    Members.canMaintainServiceManagers(id).catch(() => false)
  ])
  canAppointPrimary.value = appointment
  canMaintainServiceManagers.value = maintenance
})
const candidateReady = computed(() => availableRoles.value.some(role => role.value === memberRole.value))
const roleHint = computed(() => Members.memberRoleOptions.find(role => role.value === memberRole.value)?.hint)
const responsibility = ref(props.member?.responsibility || '')
const remark = ref(props.member?.remark || '')
const reason = ref(''), error = ref(''), searching = ref(false), saving = ref(false)
const candidates = ref<Members.MemberCandidate[]>([])
const selectedPerson = computed(() => candidates.value.find(person => sameSystemUserId(person.id, userId.value)))
let sequence = 0
const submission = createSubmissionIdempotencyState()
const clearCandidate = () => {
  sequence++
  userId.value = undefined
  candidates.value = []
  searching.value = false
  error.value = ''
  primary.value = false
}
const candidateParams = () => ({ projectRole: memberRole.value })
const memberSource = computed(() => ({
  key: `project-member:${props.project.id}:${memberRole.value}:${candidateReady.value}`,
  search: async (query: { keyword: string; pageNo: number; pageSize: number }) => {
    if (isExternal.value || !candidateReady.value) return { list: [], total: 0 }
    return Members.getMemberCandidates(props.project.id!, { ...query, ...candidateParams() })
  }
}))
const selectPerson = (person: SystemUserSelectionUser | SystemUserSelectionUser[] | undefined) => {
  if (!person || Array.isArray(person)) return
  candidates.value = [{ ...person, id: person.id, username: person.username || '', nickname: person.nickname || '' } as Members.MemberCandidate]
}
onMounted(async () => {
  if (!props.member || props.member.userId == null || isExternal.value || !candidateReady.value) return
  const request = ++sequence
  searching.value = true
  try {
    const result = await Members.getMemberCandidates(props.project.id!, { pageNo: 1, pageSize: 1, userId: props.member.userId ?? undefined, ...candidateParams() })
    if (request !== sequence) return
    candidates.value = result.list
    userId.value = result.list.find(user => sameSystemUserId(user.id, props.member?.userId))?.id
    if (!userId.value) error.value = '原人员当前不可选，请选择有效用户；历史记录保持不变。'
  } catch { if (request === sequence) error.value = '人员校验失败，请重试。' }
  finally { if (request === sequence) searching.value = false }
})
onBeforeUnmount(() => sequence++)
const submit = async () => {
  if (saving.value) return
  error.value = ''
  if (memberRole.value === memberRoles.SERVICE_MANAGER && !canMaintainServiceManagers.value) {
    error.value = '服务经理成员只能由具备权限的项目管理角色、管理员或本项目有效服务经理维护。'; return
  }
  if (!candidateReady.value || (isExternal.value ? !externalName.value.trim() : !userId.value) || props.project.version == null) {
    error.value = isExternal.value ? '请选择项目角色并填写最终用户姓名' : '请选择项目角色和有效人员'; return
  }
  const data: Members.MemberMutation = { userId: isExternal.value ? null : userId.value!, memberRole: memberRole.value,
    responsibility: responsibility.value.trim(), remark: remark.value.trim(), reason: reason.value.trim(),
    primary: memberRole.value === memberRoles.PROJECT_MANAGER ? (canAppointPrimary.value && primary.value)
      : memberRole.value === memberRoles.SERVICE_MANAGER ? primary.value : undefined }
  if (isExternal.value) {
    data.externalPerson = { name: externalName.value.trim(), companyName: externalCompany.value.trim(),
      department: externalDepartment.value.trim(), title: externalTitle.value.trim(),
      mobile: externalMobile.value.trim(), email: externalEmail.value.trim() }
    const { mobile, email } = data.externalPerson
    if (mobile && !/^(?:\+86[ -]?)?(?:1[3-9][0-9]{9}|(?:0[1-9][0-9]{1,2}|\(0?[1-9][0-9]{1,2}\))[ -]?(?:[1-9][0-9]{6,7}|[1-9][0-9]{2,3}-[0-9]{4})(?:-[0-9]{1,6})?)$/.test(mobile)) {
      error.value = '联系电话格式无效'; return
    }
    if (email && !/^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$/.test(email)) {
      error.value = '邮箱格式无效'; return
    }
  }
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
.form-helper { flex-basis: 100%; margin: 4px 0 0; font-size: 12px; line-height: 1.5; }
.actions { display: flex; justify-content: flex-end; gap: .5rem; margin-top: 1rem; }
</style>
