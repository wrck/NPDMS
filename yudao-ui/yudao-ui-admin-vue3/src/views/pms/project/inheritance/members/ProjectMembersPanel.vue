<template>
  <ContentWrap v-loading="loading" class="members-panel">
    <header class="panel-header">
      <span class="panel-title"><Icon icon="ep:user" /> 项目成员</span>
      <div class="actions">
        <el-button :disabled="opening" @click="load"><Icon icon="ep:refresh" />刷新</el-button>
        <el-button v-hasPermi="['pms:project-team:create', 'pms:project:assign']" type="primary" :disabled="!editable" @click="openMember()"><Icon icon="ep:plus" />新增成员</el-button>
      </div>
    </header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <el-form inline class="member-filters" @submit.prevent="reload">
      <el-form-item label="记录">
        <el-radio-group v-model="query.state" aria-label="成员记录" @change="reload">
          <el-radio-button value="CURRENT">当前成员</el-radio-button><el-radio-button value="HISTORY">历史成员</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="query.role" aria-label="角色筛选" clearable placeholder="全部角色" class="role-select" @change="reload">
          <el-option v-for="(label, code) in roles" :key="code" :value="code" :label="label" />
        </el-select>
      </el-form-item>
      <el-form-item label="人员"><el-input v-model="query.keyword" aria-label="成员姓名" placeholder="按姓名查询" clearable @keyup.enter="reload" /></el-form-item>
      <el-form-item><el-button native-type="submit"><Icon icon="ep:search" />查询</el-button></el-form-item>
    </el-form>
    <el-table :data="rows" row-key="id" border size="small" :empty-text="error ? '成员读取失败，请刷新重试' : '暂无成员记录'">
      <el-table-column label="人员" min-width="110"><template #default="{ row }">
        {{ row.memberName || '姓名未记录' }}
      </template></el-table-column>
      <el-table-column label="角色" min-width="95"><template #default="{ row }">{{ roleLabel(row.memberRole) }}</template></el-table-column>
      <el-table-column label="联系电话" min-width="125"><template #default="{ row }">{{ row.mobile || '—' }}</template></el-table-column>
      <el-table-column label="邮箱" min-width="155" show-overflow-tooltip><template #default="{ row }">{{ row.email || '—' }}</template></el-table-column>
      <el-table-column label="主责" width="70" align="center"><template #default="{ row }">{{ primaryLabel(row) }}</template></el-table-column>
      <el-table-column label="生效时间" min-width="155"><template #default="{ row }">{{ time(row.effectiveFrom) }}</template></el-table-column>
      <el-table-column label="失效时间" min-width="155"><template #default="{ row }">{{ time(row.effectiveTo) }}</template></el-table-column>
      <el-table-column label="操作" width="140" fixed="right"><template #default="{ row }">
        <template v-if="canMaintain(row)">
          <el-button v-if="isCurrent(row)" link type="primary" :disabled="!editable" @click="openMember(row)">编辑</el-button>
          <el-button v-if="isCurrent(row)" link type="danger" :disabled="!editable" @click="openRemoval(row)">移出项目</el-button>
          <el-button v-else link type="primary" :disabled="!editable" @click="openMember(row, true)">重新加入</el-button>
        </template>
        <span v-else>只读</span>
      </template></el-table-column>
    </el-table>
    <Pagination v-model:page="query.pageNo" v-model:limit="query.pageSize" :total="total" @pagination="load" />
    <el-text v-if="project.lifecycleStatus !== 'ACTIVE'" type="info">项目已关闭，成员只读。</el-text>
    <el-dialog v-model="memberVisible" :title="rejoin ? '重新加入项目' : selected ? '编辑项目成员' : '新增项目成员'"
      :width="dialogWidth" :close-on-click-modal="false" destroy-on-close>
      <UnifiedMemberForm v-if="memberVisible && dialogProject?.version != null" :project="dialogProject"
        :primary-user-id="managers?.primaryUserId ?? undefined" :member="selected" :rejoin="rejoin" @cancel="memberVisible = false" @saved="saved" />
    </el-dialog>
    <el-dialog v-model="removeVisible" title="移出项目成员" :width="dialogWidth" :close-on-click-modal="false">
      <el-form label-position="top" @submit.prevent="remove">
        <p>{{ selected?.memberName }} · {{ selected && roleLabel(selected.memberRole) }}；只结束当前区间，不删除历史。</p>
        <el-form-item v-if="requiresReplacementPrimary" label="接任主责项目经理" required>
          <el-select v-model="replacementPrimaryUserId" aria-label="接任主责项目经理" placeholder="从保留的项目经理中选择">
            <el-option v-for="manager in retainedManagers" :key="manager.userId" :value="manager.userId" :label="manager.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="退出原因" required><el-input v-model="removeReason" aria-label="退出原因" type="textarea" maxlength="500" /></el-form-item>
        <el-alert v-if="removeError" :title="removeError" type="error" :closable="false" role="alert" />
        <div class="actions"><el-button :disabled="removing" @click="removeVisible = false">取消</el-button>
          <el-button type="danger" native-type="submit" :loading="removing">确认移出</el-button></div>
      </el-form>
    </el-dialog>
  </ContentWrap>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { formatDate } from '@/utils/formatTime'
import * as Projects from '@/api/pms/project/projects'
import * as Managers from '@/api/pms/project/members'
import * as Members from '@/api/pms/project/unified-members'
import { createSubmissionIdempotencyState } from '@/views/pms/project/projects/submissionIdempotency'
import UnifiedMemberForm from './OrdinaryMemberForm.vue'
import { checkPermi } from '@/utils/permission'

const props = defineProps<{ project: Projects.ProjectMasterVO }>()
const emit = defineEmits<{ updated: [] }>()
const roles: Record<string, string> = { PROJECT_MANAGER: '项目经理', SERVICE_MANAGER: '服务经理',
  TEAM_MEMBER: '团队成员', SALES_REPRESENTATIVE: '销售代表' }
const roleLabel = (role: string) => roles[Members.logicalMemberRole(role)] || role
const query = reactive({ pageNo: 1, pageSize: 10, state: 'CURRENT' as 'CURRENT' | 'HISTORY', role: '', keyword: '' })
const rows = ref<Members.MemberRecord[]>([]), total = ref(0)
const loading = ref(false), opening = ref(false), error = ref('')
const memberVisible = ref(false), removeVisible = ref(false)
const selected = ref<Members.MemberRecord>(), rejoin = ref(false), dialogProject = ref<Projects.ProjectMasterVO>()
const managers = ref<Managers.ProjectManagers>()
const removeReason = ref(''), removeError = ref(''), removing = ref(false)
const replacementPrimaryUserId = ref<number>()
const retainedManagers = computed(() => managers.value?.members.filter(member => member.userId !== selected.value?.userId) || [])
const requiresReplacementPrimary = computed(() => selected.value?.memberRole === 'PROJECT_MANAGER'
  && selected.value.userId === managers.value?.primaryUserId && retainedManagers.value.length > 0)
const mobile = useMediaQuery('(max-width: 767px)')
const dialogWidth = computed(() => mobile.value ? '96%' : '680px')
const editable = computed(() => props.project.lifecycleStatus === 'ACTIVE' && !loading.value && !opening.value && !error.value)
const removalSubmission = createSubmissionIdempotencyState()
let sequence = 0
const canMaintain = (row: Members.MemberRecord) => Members.memberRoleOptions.some(role =>
  role.value === Members.logicalMemberRole(row.memberRole) && checkPermi([role.permission]))
const isCurrent = (row: Members.MemberRecord) => row.status === 'ACTIVE'
  && (!row.effectiveFrom || new Date(row.effectiveFrom).getTime() <= Date.now())
  && (!row.effectiveTo || new Date(row.effectiveTo).getTime() > Date.now())
const time = (value?: Date | null) => value ? formatDate(value) : '—'
const primaryLabel = (row: Members.MemberRecord) => {
  if (row.memberRole === 'PROJECT_MANAGER') return isCurrent(row)
    ? (String(row.userId) === String(managers.value?.primaryUserId) ? '是' : '否') : '—'
  return Members.logicalMemberRole(row.memberRole) === 'SERVICE_MANAGER'
    ? (row.assignmentType === 'COLLABORATOR' ? '否' : '是') : '—'
}
const load = async () => {
  const request = ++sequence, projectId = props.project.id!
  loading.value = true
  error.value = ''
  try {
    const [result, managerState] = await Promise.all([
      Members.getMemberPage(projectId, { ...query, role: query.role || undefined, keyword: query.keyword || undefined }),
      Managers.getProjectManagers(projectId)
    ])
    if (request !== sequence) return
    rows.value = result.list
    total.value = result.total
    managers.value = managerState
  } catch { if (request === sequence) error.value = '成员加载失败，当前数据可能未刷新。请重试后再操作。' }
  finally { if (request === sequence) loading.value = false }
}
const reload = () => { query.pageNo = 1; return load() }
const prepare = async () => {
  if (!editable.value) return false
  const id = props.project.id!
  opening.value = true
  try {
    const project = await Projects.getProject(id)
    if (id !== props.project.id) return false
    if (project.version == null || project.lifecycleStatus !== 'ACTIVE') throw new Error('项目状态或版本变化')
    dialogProject.value = project
    return true
  } catch { error.value = '项目状态或版本读取失败，请刷新后重试。'; return false }
  finally { opening.value = false }
}
const openMember = async (row?: Members.MemberRecord, joining = false) => {
  if (!await prepare()) return
  selected.value = row
  rejoin.value = joining
  memberVisible.value = true
}
const openRemoval = async (row: Members.MemberRecord) => {
  if (!await prepare()) return
  selected.value = row
  removeReason.value = ''
  removeError.value = ''
  replacementPrimaryUserId.value = undefined
  removeVisible.value = true
}
const saved = async () => {
  memberVisible.value = removeVisible.value = false
  await load()
  emit('updated')
}
const remove = async () => {
  if (removing.value || !selected.value || dialogProject.value?.version == null) return
  if (!removeReason.value.trim()) { removeError.value = '请填写退出原因'; return }
  if (requiresReplacementPrimary.value && !replacementPrimaryUserId.value) { removeError.value = '请选择接任主责项目经理'; return }
  removing.value = true
  try {
    const id = dialogProject.value.id!, version = dialogProject.value.version, assignmentId = selected.value.id
    const reason = removeReason.value.trim()
    await Members.removeMember(id, assignmentId, reason, version,
      removalSubmission.keyFor({ id, assignmentId, version, reason, replacementPrimaryUserId: replacementPrimaryUserId.value }),
      replacementPrimaryUserId.value)
    await saved()
  } catch { removeError.value = '移出未成功。网络异常可重试；版本冲突时取消并刷新成员。' }
  finally { removing.value = false }
}
watch(() => props.project.id, () => {
  memberVisible.value = removeVisible.value = false
  rows.value = []
  total.value = 0
  reload()
}, { immediate: true })
onBeforeUnmount(() => sequence++)
</script>
<style scoped>
.panel-header, .actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.panel-header { justify-content: space-between; padding-bottom: 8px; margin-bottom: 12px; border-bottom: 1px solid var(--el-border-color-lighter); }
.panel-title { display: inline-flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 600; color: var(--el-text-color-primary); }
p { color: var(--el-text-color-secondary); }
.member-filters { display: flex; flex-wrap: wrap; column-gap: 16px; }
.member-filters :deep(.el-form-item) { margin-right: 0; margin-bottom: 12px; }
.role-select { width: 140px; }
.member-filters :deep(.el-input) { width: 150px; }
@media (width <= 767px) { .panel-header { align-items: flex-start; } }
</style>
