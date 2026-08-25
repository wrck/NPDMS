<template>
  <ContentWrap>
    <div class="panel-heading">
      <div>
        <h3>项目授权</h3>
        <span>授权只作用于当前项目或当前项目及全部后代，不改变项目成员角色。</span>
      </div>
      <el-button
        type="primary"
        @click="openCreate"
        v-hasPermi="['pms:project:authorization:manage']"
      >
        <Icon icon="ep:plus" />新增授权
      </el-button>
    </div>

    <el-form :model="query" class="query-form" label-position="top">
      <el-form-item label="被授权用户">
        <UserSelect v-model="query.subjectUserId" clearable />
      </el-form-item>
      <el-form-item label="授权动作">
        <el-select v-model="query.actionCode" clearable placeholder="全部动作">
          <el-option
            v-for="item in actionOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="授权范围">
        <el-select v-model="query.scopeCode" clearable placeholder="全部范围">
          <el-option
            v-for="item in scopeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.statusCode" clearable placeholder="全部状态">
          <el-option label="有效" value="ACTIVE" />
          <el-option label="已撤销" value="REVOKED" />
          <el-option label="已到期" value="EXPIRED" />
        </el-select>
      </el-form-item>
      <el-form-item class="query-actions">
        <el-button type="primary" @click="search"><Icon icon="ep:search" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap :aria-busy="loading">
    <div class="table-scroll desktop-list">
      <el-table v-loading="loading" :data="rows" size="small" border empty-text="暂无项目授权">
        <el-table-column label="被授权用户" min-width="150">
          <template #default="{ row }">{{ userLabel(row.subjectUserId) }}</template>
        </el-table-column>
        <el-table-column label="动作" min-width="110">
          <template #default="{ row }">
            <dict-tag :type="DICT_TYPE.PMS_PROJECT_AUTHORIZATION_ACTION" :value="row.actionCode" />
          </template>
        </el-table-column>
        <el-table-column label="范围" min-width="150">
          <template #default="{ row }">
            <dict-tag :type="DICT_TYPE.PMS_PROJECT_AUTHORIZATION_SCOPE" :value="row.scopeCode" />
          </template>
        </el-table-column>
        <el-table-column label="生效区间" min-width="260">
          <template #default="{ row }">
            {{ formatDateTime(row.effectiveFrom) }} ～ {{ formatDateTime(row.effectiveTo) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType[row.statusCode]" size="small">
              {{ statusLabel[row.statusCode] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.statusCode === 'ACTIVE'"
              link
              type="danger"
              @click="openRevoke(row)"
              v-hasPermi="['pms:project:authorization:revoke']"
              >撤销</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-loading="loading" class="mobile-list" aria-live="polite">
      <article v-for="row in rows" :key="row.id" class="authorization-card">
        <div class="card-heading">
          <strong>{{ userLabel(row.subjectUserId) }}</strong>
          <el-tag :type="statusType[row.statusCode]" size="small">
            {{ statusLabel[row.statusCode] }}
          </el-tag>
        </div>
        <div class="card-dicts">
          <dict-tag :type="DICT_TYPE.PMS_PROJECT_AUTHORIZATION_ACTION" :value="row.actionCode" />
          <dict-tag :type="DICT_TYPE.PMS_PROJECT_AUTHORIZATION_SCOPE" :value="row.scopeCode" />
        </div>
        <span
          >{{ formatDateTime(row.effectiveFrom) }} ～ {{ formatDateTime(row.effectiveTo) }}</span
        >
        <el-button
          v-if="row.statusCode === 'ACTIVE'"
          link
          type="danger"
          @click="openRevoke(row)"
          v-hasPermi="['pms:project:authorization:revoke']"
          >撤销授权</el-button
        >
      </article>
      <el-empty v-if="!loading && !rows.length" description="暂无项目授权" />
    </div>

    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>

  <Dialog v-model="createVisible" title="新增项目授权" :width="dialogWidth" max-height="70vh">
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top">
      <el-form-item label="被授权用户" prop="subjectUserId">
        <UserSelect v-model="createForm.subjectUserId" :clearable="false" />
      </el-form-item>
      <div class="form-grid">
        <el-form-item label="授权动作" prop="actionCode">
          <el-select v-model="createForm.actionCode">
            <el-option
              v-for="item in actionOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="授权范围" prop="scopeCode">
          <el-select v-model="createForm.scopeCode">
            <el-option
              v-for="item in scopeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </div>
      <div class="form-grid">
        <el-form-item label="生效时间">
          <el-date-picker
            v-model="createForm.effectiveFrom"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="立即生效"
          />
        </el-form-item>
        <el-form-item label="失效时间" prop="effectiveTo">
          <el-date-picker
            v-model="createForm.effectiveTo"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="长期有效"
          />
        </el-form-item>
      </div>
      <el-form-item label="授权原因">
        <el-input
          v-model="createForm.reason"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitCreate">确认授权</el-button>
    </template>
  </Dialog>

  <Dialog v-model="revokeVisible" title="撤销项目授权" :width="dialogWidth">
    <el-alert
      title="撤销后立即失去该范围，历史授权记录仍会保留。"
      type="warning"
      :closable="false"
      class="revoke-alert"
    />
    <el-form ref="revokeFormRef" :model="revokeForm" :rules="revokeRules" label-position="top">
      <el-form-item label="撤销原因" prop="reason">
        <el-input
          v-model="revokeForm.reason"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="revokeVisible = false">取消</el-button>
      <el-button type="danger" :loading="saving" @click="submitRevoke">确认撤销</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import type { FormInstance, FormRules } from 'element-plus'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import * as UserApi from '@/api/system/user'
import * as ProjectsApi from '@/api/pms/project/projects'
import type {
  ProjectAuthorizationAction,
  ProjectAuthorizationCreateReqVO,
  ProjectAuthorizationPageParams,
  ProjectAuthorizationScope,
  ProjectAuthorizationStatus,
  ProjectAuthorizationVO
} from '@/api/pms/project/projects'
import UserSelect from '@/views/system/user/components/UserSelect.vue'

defineOptions({ name: 'ProjectAuthorizationPanel' })

const props = defineProps<{ projectId: number }>()
const message = useMessage()
const mobile = useMediaQuery('(max-width: 767px)')
const dialogWidth = computed(() => (mobile.value ? '96%' : '620px'))
const actionOptions = computed(() => getStrDictOptions(DICT_TYPE.PMS_PROJECT_AUTHORIZATION_ACTION))
const scopeOptions = computed(() => getStrDictOptions(DICT_TYPE.PMS_PROJECT_AUTHORIZATION_SCOPE))
const statusLabel: Record<ProjectAuthorizationStatus, string> = {
  ACTIVE: '有效',
  REVOKED: '已撤销',
  EXPIRED: '已到期'
}
const statusType: Record<ProjectAuthorizationStatus, 'success' | 'info' | 'warning'> = {
  ACTIVE: 'success',
  REVOKED: 'info',
  EXPIRED: 'warning'
}

const loading = ref(false)
const saving = ref(false)
const rows = ref<ProjectAuthorizationVO[]>([])
const total = ref(0)
const userNames = ref<Record<number, string>>({})
const simpleUsers = ref<UserApi.UserVO[]>([])
const query = reactive<ProjectAuthorizationPageParams>({ pageNo: 1, pageSize: 10 })

const createVisible = ref(false)
const createFormRef = ref<FormInstance>()
type ProjectAuthorizationForm = Omit<ProjectAuthorizationCreateReqVO, 'subjectUserId'> & {
  subjectUserId?: number
}
const createForm = reactive<ProjectAuthorizationForm>({
  subjectUserId: undefined,
  actionCode: 'PROJECT_VIEW',
  scopeCode: 'CURRENT_PROJECT',
  effectiveFrom: undefined,
  effectiveTo: undefined,
  reason: ''
})
const createRules: FormRules = {
  subjectUserId: [{ required: true, message: '请选择被授权用户', trigger: 'change' }],
  actionCode: [{ required: true, message: '请选择授权动作', trigger: 'change' }],
  scopeCode: [{ required: true, message: '请选择授权范围', trigger: 'change' }],
  effectiveTo: [
    {
      validator: (_rule, value, callback) => {
        if (value && createForm.effectiveFrom && value <= createForm.effectiveFrom) {
          callback(new Error('失效时间必须晚于生效时间'))
        } else callback()
      },
      trigger: 'change'
    }
  ]
}

const revokeVisible = ref(false)
const revokeFormRef = ref<FormInstance>()
const revokeTarget = ref<ProjectAuthorizationVO>()
const revokeForm = reactive({ reason: '' })
const revokeRules: FormRules = {
  reason: [{ required: true, whitespace: true, message: '请输入撤销原因', trigger: 'blur' }]
}

const formatDateTime = (value?: string | null) => (value ? formatDate(value) : '长期有效')
const userLabel = (userId: number) => userNames.value[userId] || `用户 #${userId}`

const loadUserNames = async (items: ProjectAuthorizationVO[]) => {
  const ids = [...new Set(items.map((item) => item.subjectUserId))]
  if (!ids.length) return
  try {
    if (!simpleUsers.value.length) simpleUsers.value = await UserApi.getSimpleUserList()
    const requiredIds = new Set(ids)
    userNames.value = {
      ...userNames.value,
      ...Object.fromEntries(
        simpleUsers.value
          .filter((user) => requiredIds.has(user.id))
          .map((user) => [user.id, user.nickname || user.username || `用户 #${user.id}`])
      )
    }
  } catch {
    // 精简用户目录不可用时保留已有回显，并降级展示用户编号。
  }
}

const load = async () => {
  loading.value = true
  try {
    const data = await ProjectsApi.getProjectAuthorizationPage(props.projectId, query)
    rows.value = data.list || []
    total.value = data.total || 0
    await loadUserNames(rows.value)
  } finally {
    loading.value = false
  }
}

const search = () => {
  query.pageNo = 1
  load()
}

const resetQuery = () => {
  Object.assign(query, {
    pageNo: 1,
    pageSize: query.pageSize,
    subjectUserId: undefined,
    actionCode: undefined,
    scopeCode: undefined,
    statusCode: undefined,
    effectiveAt: undefined
  })
  load()
}

const openCreate = () => {
  Object.assign(createForm, {
    subjectUserId: undefined,
    actionCode: 'PROJECT_VIEW' as ProjectAuthorizationAction,
    scopeCode: 'CURRENT_PROJECT' as ProjectAuthorizationScope,
    effectiveFrom: undefined,
    effectiveTo: undefined,
    reason: ''
  })
  createVisible.value = true
  createFormRef.value?.clearValidate()
}

const submitCreate = async () => {
  await createFormRef.value?.validate()
  saving.value = true
  try {
    await ProjectsApi.createProjectAuthorization(
      props.projectId,
      {
        ...createForm,
        subjectUserId: createForm.subjectUserId!
      },
      crypto.randomUUID()
    )
    message.success('项目授权已创建')
    createVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const openRevoke = (row: ProjectAuthorizationVO) => {
  revokeTarget.value = row
  revokeForm.reason = ''
  revokeVisible.value = true
  revokeFormRef.value?.clearValidate()
}

const submitRevoke = async () => {
  await revokeFormRef.value?.validate()
  if (!revokeTarget.value) return
  saving.value = true
  try {
    await ProjectsApi.revokeProjectAuthorization(
      revokeTarget.value.id,
      revokeTarget.value.version,
      revokeForm.reason,
      crypto.randomUUID()
    )
    message.success('项目授权已撤销')
    revokeVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

watch(() => props.projectId, load, { immediate: true })
</script>

<style scoped lang="scss">
.panel-heading,
.card-heading,
.card-dicts {
  display: flex;
  align-items: center;
}

.panel-heading {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-heading h3 {
  margin: 0 0 4px;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.panel-heading span,
.authorization-card > span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.query-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(140px, 1fr)) auto;
  gap: 0 12px;
}

.query-actions {
  align-self: end;
}

.table-scroll {
  max-width: 100%;
  overflow-x: auto;
}

.mobile-list {
  display: none;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.form-grid :deep(.el-select),
.form-grid :deep(.el-date-editor) {
  width: 100%;
}

.revoke-alert {
  margin-bottom: 16px;
}

@media (width <= 1199px) {
  .query-form {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }
}

@media (width <= 767px) {
  .panel-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .query-form,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .query-actions :deep(.el-form-item__content) {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
  }

  .desktop-list {
    display: none;
  }

  .mobile-list {
    display: grid;
    gap: 8px;
  }

  .authorization-card {
    display: grid;
    gap: 8px;
    padding: 12px;
    border: 1px solid var(--el-border-color);
    border-radius: var(--el-border-radius-base);
  }

  .card-heading {
    justify-content: space-between;
    gap: 8px;
  }

  .card-dicts {
    flex-wrap: wrap;
    gap: 8px;
  }

  .authorization-card .el-button {
    justify-self: start;
  }
}
</style>
