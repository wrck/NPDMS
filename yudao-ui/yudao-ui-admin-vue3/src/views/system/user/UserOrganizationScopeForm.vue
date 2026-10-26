<template>
  <Dialog
    v-model="visible"
    :title="`公司部门范围 · ${user?.nickname || user?.username || ''}`"
    width="1120px"
  >
    <el-alert
      title="公司必选；不选部门表示公司级范围。范围角色用于组织资格匹配，功能权限请在“分配角色”中维护。"
      type="info"
      :closable="false"
      class="mb-16px"
    />
    <div class="flex flex-wrap gap-12px items-center mb-12px">
      <el-select
        v-model="query.status"
        placeholder="全部记录（含历史）"
        clearable
        class="!w-220px"
        @change="search"
      >
        <el-option label="启用记录" :value="0" />
        <el-option label="停用记录" :value="1" />
      </el-select>
      <el-button @click="loadRows" :disabled="loading || saving">刷新</el-button>
      <el-button
        v-if="canManage"
        type="primary"
        @click="edit()"
        :disabled="loading || saving || !optionsReady"
      >
        新增范围
      </el-button>
    </div>
    <el-alert
      v-if="loadError"
      title="范围加载失败，请点击刷新重试。"
      type="error"
      :closable="false"
      class="mb-12px"
    />
    <el-alert
      v-if="optionsError"
      title="公司或部门选项加载失败。"
      type="warning"
      :closable="false"
      class="mb-12px"
    >
      <el-button link type="primary" @click="loadOptions">重新加载选项</el-button>
    </el-alert>
    <el-table v-loading="loading" :data="rows" row-key="id" empty-text="尚未配置公司部门范围">
      <el-table-column label="公司" min-width="160">
        <template #default="{ row }">
          <div>{{ row.companyName }}</div>
          <div class="text-12px text-gray-500">{{ row.companyCode }}</div>
        </template>
      </el-table-column>
      <el-table-column label="部门" min-width="150">
        <template #default="{ row }">
          <div>{{ row.departmentName || (row.departmentId ? '部门名称缺失' : '公司级范围') }}</div>
          <div class="text-12px text-gray-500">{{ row.departmentCode }}</div>
        </template>
      </el-table-column>
      <el-table-column label="范围角色" prop="scopeRole" min-width="170" />
      <el-table-column label="主范围" width="80">
        <template #default="{ row }">{{ row.isPrimary ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="生效时间" min-width="170">
        <template #default="{ row }">{{ formatDate(row.effectiveFrom) }}</template>
      </el-table-column>
      <el-table-column label="到期时间" min-width="170">
        <template #default="{ row }">{{
          row.effectiveTo ? formatDate(row.effectiveTo) : '长期有效'
        }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'info' : 'success'">{{ stateLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 0"
            link
            type="primary"
            :disabled="saving || !optionsReady"
            @click="edit(row)"
            >变更</el-button
          >
          <el-button
            v-if="row.status === 0"
            link
            type="danger"
            :disabled="saving"
            @click="disable(row)"
            >停用</el-button
          >
          <span v-else class="text-gray-500">历史保留</span>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="loadRows"
    />

    <el-divider v-if="editing">{{ editingId ? '变更范围（保留原记录）' : '新增范围' }}</el-divider>
    <el-form
      v-if="editing"
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      :disabled="saving"
    >
      <el-form-item label="公司" prop="companyId">
        <el-select v-model="form.companyId" filterable placeholder="请选择公司" class="!w-full">
          <el-option
            v-for="company in companies"
            :key="company.id"
            :value="company.id!"
            :label="`${company.name}（${company.code}）`"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="部门" prop="departmentId">
        <el-tree-select
          v-model="form.departmentId"
          :data="departmentTree"
          :props="{ label: 'name' }"
          node-key="id"
          check-strictly
          filterable
          clearable
          placeholder="不选择表示公司级范围"
          class="!w-full"
        />
      </el-form-item>
      <el-form-item label="范围角色" prop="scopeRole">
        <el-input
          v-model="form.scopeRole"
          :maxlength="32"
          placeholder="填写既定范围角色编码，如 PROJECT_MANAGER、SERVICE_MANAGER"
        />
      </el-form-item>
      <el-form-item label="主范围" prop="isPrimary"
        ><el-switch v-model="form.isPrimary"
      /></el-form-item>
      <el-form-item label="生效时间" prop="effectiveFrom">
        <el-date-picker
          v-model="form.effectiveFrom"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="请选择生效时间"
        />
      </el-form-item>
      <el-form-item label="到期时间" prop="effectiveTo">
        <el-date-picker
          v-model="form.effectiveTo"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          clearable
          placeholder="留空表示长期有效"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存范围</el-button>
        <el-button @click="editing = false">取消编辑</el-button>
      </el-form-item>
    </el-form>
    <template #footer
      ><el-button :disabled="saving" @click="visible = false">关闭</el-button></template
    >
  </Dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import dayjs from 'dayjs'
import * as ScopeApi from '@/api/system/user/organizationScope'
import * as CompanyApi from '@/api/system/company'
import * as DeptApi from '@/api/system/dept'
import type { UserVO } from '@/api/system/user'
import { checkPermi } from '@/utils/permission'
import { handleTree } from '@/utils/tree'

defineOptions({ name: 'SystemUserOrganizationScopeForm' })
const message = useMessage()
const visible = ref(false)
const user = ref<UserVO>()
const loading = ref(false)
const saving = ref(false)
const editing = ref(false)
const editingId = ref<number>()
const optionsReady = ref(false)
const loadError = ref(false)
const optionsError = ref(false)
let listRequest = 0
const rows = ref<ScopeApi.UserOrganizationScope[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, status: undefined as number | undefined })
const companies = ref<CompanyApi.CompanyVO[]>([])
const departmentTree = ref<DeptApi.DeptVO[]>([])
const canManage = computed(() => checkPermi(['system:user-scope:manage']))
const formRef = ref<FormInstance>()
const form = ref<Omit<ScopeApi.UserOrganizationScopeSave, 'companyId'> & { companyId?: number }>({
  userId: 0,
  scopeRole: '',
  isPrimary: false,
  effectiveFrom: ''
})
const rules: FormRules = {
  companyId: [{ required: true, message: '请选择公司', trigger: 'change' }],
  scopeRole: [{ required: true, whitespace: true, message: '请填写范围角色编码', trigger: 'blur' }],
  effectiveFrom: [{ required: true, message: '请选择生效时间', trigger: 'change' }]
}
const formatDate = (value: string | number) => dayjs(value).format('YYYY-MM-DD HH:mm:ss')
const stateLabel = (row: ScopeApi.UserOrganizationScope) => {
  if (row.status === 1) return '已停用'
  if (dayjs(row.effectiveFrom).isAfter(dayjs())) return '未生效'
  if (row.effectiveTo && !dayjs(row.effectiveTo).isAfter(dayjs())) return '已到期'
  return '有效'
}
const loadRows = async () => {
  if (!user.value) return
  const request = ++listRequest
  loading.value = true
  loadError.value = false
  rows.value = []
  total.value = 0
  try {
    const data = await ScopeApi.getUserOrganizationScopes({ ...query, userId: user.value.id })
    if (request !== listRequest) return
    rows.value = data.list
    total.value = data.total
  } catch {
    if (request === listRequest) loadError.value = true
  } finally {
    if (request === listRequest) loading.value = false
  }
}
const search = async () => {
  query.pageNo = 1
  await loadRows()
}
const loadOptions = async () => {
  optionsReady.value = false
  optionsError.value = false
  try {
    const [companyList, departmentList] = await Promise.all([
      CompanyApi.getSimpleCompanyList(),
      DeptApi.getSimpleDeptList()
    ])
    companies.value = companyList
    departmentTree.value = handleTree(departmentList.filter((dept) => !!dept.code))
    optionsReady.value = true
  } catch {
    optionsError.value = true
  }
}
const open = async (row: UserVO) => {
  user.value = row
  visible.value = true
  editing.value = false
  optionsReady.value = false
  optionsError.value = false
  query.pageNo = 1
  query.status = undefined
  await loadRows()
  if (canManage.value && user.value.id === row.id) await loadOptions()
}
defineExpose({ open })
const edit = (row?: ScopeApi.UserOrganizationScope) => {
  if (!user.value) return
  editingId.value = row?.id
  form.value = {
    userId: user.value.id,
    companyId: row?.companyId,
    departmentId: row?.departmentId || undefined,
    scopeRole: row?.scopeRole || '',
    isPrimary: row?.isPrimary || false,
    effectiveFrom: row ? formatDate(row.effectiveFrom) : formatDate(Date.now()),
    effectiveTo: row?.effectiveTo ? formatDate(row.effectiveTo) : undefined,
    expectedVersion: row?.version
  }
  editing.value = true
  nextTick(() => formRef.value?.clearValidate())
}
const save = async () => {
  if (!(await formRef.value?.validate())) return
  if (
    form.value.effectiveTo &&
    !dayjs(form.value.effectiveTo).isAfter(dayjs(form.value.effectiveFrom))
  ) {
    message.warning('到期时间必须晚于生效时间')
    return
  }
  saving.value = true
  try {
    const data = {
      ...form.value,
      companyId: form.value.companyId!,
      scopeRole: form.value.scopeRole.trim(),
      effectiveFrom: dayjs(form.value.effectiveFrom).valueOf(),
      effectiveTo: form.value.effectiveTo ? dayjs(form.value.effectiveTo).valueOf() : undefined
    }
    if (editingId.value) await ScopeApi.reviseUserOrganizationScope(editingId.value, data)
    else await ScopeApi.createUserOrganizationScope(data)
    message.success('范围已保存')
    editing.value = false
    await search()
  } finally {
    saving.value = false
  }
}
const disable = async (row: ScopeApi.UserOrganizationScope) => {
  await message.confirm(
    `确认停用“${row.companyName} / ${row.departmentName || '公司级范围'} / ${row.scopeRole}”吗？`
  )
  saving.value = true
  try {
    await ScopeApi.disableUserOrganizationScope({
      id: row.id,
      userId: row.userId,
      expectedVersion: row.version
    })
    editing.value = false
    message.success('范围已停用，历史记录已保留')
    await loadRows()
  } finally {
    saving.value = false
  }
}
</script>
