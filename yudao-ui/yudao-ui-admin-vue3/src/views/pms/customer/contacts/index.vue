<template>
  <div class="min-w-0 w-full max-w-full">
    <ContentWrap>
      <div v-if="showSectionHeader" class="contact-section-heading">
        <div class="contact-section-copy">
          <h2 class="contact-section-title">
            <Icon icon="ep:user-filled" />项目联系人
            <el-tag type="info" size="small">{{ total }} 位</el-tag>
          </h2>
          <div class="contact-section-description">维护项目实际协作人员，主联系人优先显示</div>
        </div>
        <div class="contact-actions" role="toolbar" aria-label="项目联系人操作">
          <el-button type="primary" :disabled="!canEdit || !customerId || !customerEnabled" @click="open()">
            <Icon icon="ep:plus" />新增联系人
          </el-button>
          <el-button :disabled="!canEdit || !customerId || !customerEnabled" @click="openSourceDialog">
            <Icon icon="ep:user" />载入客户联系人
          </el-button>
          <el-button v-if="context?.project.canViewHistory && projectId" @click="historyRef?.open(projectId)">变更历史</el-button>
        </div>
      </div>
      <el-form ref="queryFormRef" :model="query" inline class="contact-filter -mb-15px">
        <el-form-item v-if="showCustomerFilter" label="客户" prop="customerId">
          <PmsEntitySelect
            v-if="!projectId || !customerId"
            v-model="query.customerId"
            :api="CustomerApi.getCustomerPage"
            :label-field="['code', 'name']"
            value-field="id"
            query-field="name"
            placeholder="请选择客户"
            class="!w-220px"
            @change="(_id, selected) => selectedCustomerName = selected?.name || ''"
          />
          <el-input v-else :model-value="context?.customerName" disabled class="!w-220px" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="query.name" clearable placeholder="输入联系人姓名" class="!w-220px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="query.status" clearable placeholder="全部状态" class="!w-160px">
            <el-option v-for="dict in getIntDictOptions('pms_contact_status')" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button :loading="loading" @click="load"><Icon icon="ep:search" />查询</el-button>
          <el-button @click="resetQuery"><Icon icon="ep:refresh" />重置</el-button>
          <el-button v-if="!showSectionHeader && (projectId || checkPermi(['pms:customer-contact:create']))" type="primary" :disabled="!canEdit || (!!projectId && (!customerId || !customerEnabled))" @click="open()">
            <Icon icon="ep:plus" />新增联系人
          </el-button>
          <el-button v-if="!showSectionHeader && projectId" :disabled="!canEdit || !customerId || !customerEnabled" @click="openSourceDialog">
            <Icon icon="ep:user" />载入客户联系人
          </el-button>
          <el-button v-if="projectId && context?.project.canManage && !customerId" :disabled="!query.customerId" :loading="loading" @click="bindCustomer" v-hasPermi="['pms:project:update']">关联项目客户</el-button>
          <el-button v-if="!showSectionHeader && projectId && context?.project.canViewHistory" @click="historyRef?.open(projectId)">变更历史</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
    </ContentWrap>
    <ContentWrap>
      <el-table v-loading="loading" :data="displayRows" :row-class-name="contactRowClassName" aria-label="项目联系人列表">
        <el-table-column v-if="showCustomerFilter" prop="customerId" label="客户编号" width="110">
          <template #default="{ row }">{{ customerCodes[row.customerId] || '编号未获取' }}</template>
        </el-table-column>
        <el-table-column v-if="showCustomerFilter" label="客户名称" min-width="160">
          <template #default="{ row }">{{ row.customerName || context?.customerName || selectedCustomerName }}</template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" min-width="100" />
        <el-table-column prop="department" label="部门" min-width="120" />
        <el-table-column prop="title" label="职务" min-width="120"><template #default="{ row }">{{ getDictLabel('pms_contact_title', row.title) || row.title }}</template></el-table-column>
        <el-table-column v-if="projectId" prop="roleCode" label="客户联系人角色" min-width="140">
          <template #default="{ row }">{{ getDictLabel('pms_customer_contact_role', row.roleCode) || row.roleCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="mobile" label="手机" min-width="130" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="primaryFlag" label="主联系人" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.primaryFlag && row.status === 0" type="success" size="small">主联系人</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }"><dict-tag type="pms_contact_status" :value="row.status" /></template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="projectId || checkPermi(['pms:customer-contact:update'])" link type="primary" :disabled="!canEdit" @click="open(row)">编辑</el-button>
            <el-button v-if="projectId || checkPermi(['pms:customer-contact:delete'])" link type="danger" :disabled="!canEdit" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="contact-empty" role="status">
            <Icon icon="ep:user" class="contact-empty-icon" />
            <div class="contact-empty-title">当前项目还没有联系人</div>
            <div class="contact-empty-description">可以新增联系人，或从客户通讯录中选择需要的人员。</div>
            <div v-if="projectId && canEdit && customerId && customerEnabled" class="contact-empty-actions">
              <el-button type="primary" @click="open()"><Icon icon="ep:plus" />新增联系人</el-button>
              <el-button @click="openSourceDialog"><Icon icon="ep:user" />载入客户联系人</el-button>
            </div>
          </div>
        </template>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
    </ContentWrap>
    <ContactHistoryDialog ref="historyRef" @restore="restore" />
    <Dialog v-model="visible" :title="form.id ? '编辑联系人' : '新增联系人'" width="min(620px, 95vw)" :before-close="beforeClose">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="saving || !canEdit">
        <el-form-item label="客户" prop="customerId">
          <PmsEntitySelect v-model="form.customerId" :api="CustomerApi.getCustomerPage" :label-field="['code', 'name']"
            value-field="id" query-field="name" placeholder="请选择客户" :disabled="!!projectId || !!form.id" />
        </el-form-item>
        <el-form-item v-if="projectId && !form.id" label="来源">
          <el-radio-group v-model="createMode"><el-radio value="new">新增客户联系人</el-radio><el-radio value="reference">引用已有客户联系人</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item v-if="projectId && !form.id && createMode === 'reference'" label="客户联系人">
          <PmsEntitySelect v-model="form.sourceContactId" :api="sourcePage" label-field="name" value-field="id" query-field="name" placeholder="请选择联系人" @change="selectSource" />
        </el-form-item>
        <ContactFields v-model="form" :project="!!projectId" />
      </el-form>
      <template #footer>
        <el-button @click="beforeClose(() => visible = false)">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!canEdit" @click="save">保存</el-button>
      </template>
    </Dialog>
    <Dialog v-model="sourceVisible" title="选择客户联系人" width="min(900px, 95vw)">
      <div class="source-guidance">从客户通讯录中选择本项目需要的联系人；仅加入已勾选项。</div>
      <el-form inline class="-mb-15px">
        <el-form-item label="姓名">
          <el-input v-model="sourceQuery.name" clearable placeholder="输入联系人姓名" class="!w-220px" @keyup.enter="loadSources" />
        </el-form-item>
        <el-form-item>
          <el-button :loading="sourceLoading" @click="loadSources"><Icon icon="ep:search" />查询</el-button>
        </el-form-item>
      </el-form>
      <el-table
        v-loading="sourceLoading"
        :data="sourceOptions"
        empty-text="暂无可载入的客户联系人"
        class="mt-16px"
        @selection-change="sourceSelection = $event"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="name" label="姓名" min-width="110" />
        <el-table-column prop="department" label="部门" min-width="120" />
        <el-table-column prop="title" label="职务" min-width="120">
          <template #default="{ row }">{{ getDictLabel('pms_contact_title', row.title) || row.title || '-' }}</template>
        </el-table-column>
        <el-table-column prop="mobile" label="手机" min-width="130" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
      </el-table>
      <div class="selection-status" role="status" aria-live="polite">已选择 {{ sourceSelection.length }} 位联系人</div>
      <Pagination :total="sourceTotal" v-model:page="sourceQuery.pageNo" v-model:limit="sourceQuery.pageSize" @pagination="loadSources" />
      <template #footer>
        <el-button :disabled="sourceSaving" @click="sourceVisible = false">取消</el-button>
        <el-button type="primary" :loading="sourceSaving" :disabled="!sourceSelection.length" @click="addSelectedSources">
          加入所选联系人（{{ sourceSelection.length }}）
        </el-button>
      </template>
    </Dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import * as ContactApi from '@/api/pms/customer/contacts'
import * as CustomerApi from '@/api/pms/customer'
import type { ContactVO, ProjectContactContext } from '@/api/pms/customer/contacts'
import { checkPermi } from '@/utils/permission'
import { useMessage } from '@/hooks/web/useMessage'
import ContactFields from './ContactFields.vue'
import ContactHistoryDialog from './ContactHistoryDialog.vue'
import { getDictLabel, getIntDictOptions } from '@/utils/dict'

defineOptions({ name: 'PmsCustomerContactsWorkbench' })
const props = withDefaults(defineProps<{ projectId?: number; showCustomerFilter?: boolean; showSectionHeader?: boolean }>(), {
  showCustomerFilter: true,
  showSectionHeader: false
})
const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const loading = ref(false), saving = ref(false), visible = ref(false), error = ref('')
const sourceVisible = ref(false), sourceLoading = ref(false), sourceSaving = ref(false)
const context = ref<ProjectContactContext>()
const query = reactive({ pageNo: 1, pageSize: 10, customerId: undefined as number | undefined, name: '', status: undefined as number | undefined })
const selectedCustomerName = ref('')
const rows = ref<ContactVO[]>([]), total = ref(0)
const displayRows = computed(() => [...rows.value].sort((left, right) => {
  const leftPrimary = left.primaryFlag && left.status === 0 ? 1 : 0
  const rightPrimary = right.primaryFlag && right.status === 0 ? 1 : 0
  return rightPrimary - leftPrimary || (left.id || 0) - (right.id || 0)
}))
const contactRowClassName = ({ row }: { row: ContactVO }) =>
  row.primaryFlag && row.status === 0 ? 'contact-row--primary' : ''
const customerCodes = ref<Record<number, string>>({})
const customerId = computed(() => props.projectId ? context.value?.project.customerId : query.customerId)
const customerEnabled = computed(() => context.value?.customerStatus === 'ENABLED')
const canEdit = computed(() => props.projectId ? !!context.value?.project.canManage : checkPermi(['pms:customer:sensitive-read']))
const form = ref<ContactVO>({ name: '', status: 0, primaryFlag: false })
const formRef = ref()
const queryFormRef = ref()
const historyRef = ref<InstanceType<typeof ContactHistoryDialog>>()
const createMode = ref<'new' | 'reference'>('new')
let operationKey = '', baseline = '', initialPrimary = false, sequence = 0
const rules = { name: [{ required: true, message: '请输入联系人姓名' }], customerId: [{ required: true, message: '请选择客户' }] }
const sourceRows = new Map<number, ContactVO>()
const sourceQuery = reactive({ pageNo: 1, pageSize: 10, name: '' })
const sourceOptions = ref<ContactVO[]>([]), sourceTotal = ref(0), sourceSelection = ref<ContactVO[]>([])
const sourcePage = async (params: PageParam) => {
  const page = await ContactApi.getProjectSources(props.projectId!, params)
  page.list.forEach(row => sourceRows.set(row.id!, row))
  return page
}
const selectSource = (id: number) => {
  const row = sourceRows.get(id)
  if (row) form.value = { ...row, id: undefined, version: undefined, sourceContactId: id, primaryFlag: false }
}
const load = async () => {
  const current = ++sequence
  loading.value = true; error.value = ''
  try {
    if (props.projectId) {
      const result = await ContactApi.getProjectContext(props.projectId)
      if (current !== sequence) return
      context.value = result
    }
    if (props.projectId && !customerId.value) { rows.value = []; total.value = 0; return context.value }
    const page = props.projectId ? await ContactApi.getProjectPage(props.projectId, query)
      : await ContactApi.getMasterPage({ ...query, customerId: customerId.value })
    if (current !== sequence) return
    rows.value = page.list; total.value = page.total
    // Resolve the business code from CUS, once per customer on this page. The legacy
    // CustomerTag displays names and must not be used under a customer-code heading.
    const codes = await Promise.all([...new Set(page.list.map(row => row.customerId).filter((id): id is number => !!id))].map(async id => {
      try { return [id, (await CustomerApi.getCustomer(id)).code] as const }
      catch { return [id, ''] as const }
    }))
    if (current !== sequence) return
    customerCodes.value = Object.fromEntries(codes)
    if (codes.some(([, code]) => !code)) error.value = '部分客户编号未获取，联系人数据仍可查看，请点击查询重试。'
    return context.value
  } catch { if (current === sequence) error.value = '联系人加载失败，请检查权限或重试。' }
  finally { if (current === sequence) loading.value = false }
}
const bindCustomer = async () => {
  if (!props.projectId || !query.customerId || !context.value) return
  await message.confirm('将所选客户关联到当前项目？已有客户关联不会被此入口替换。')
  await ContactApi.associateCustomer(props.projectId, query.customerId, context.value.project.projectVersion)
  await load(); emit('changed')
}
const resetQuery = async () => {
  query.name = ''
  query.status = undefined
  if (!props.projectId) {
    query.customerId = undefined
    selectedCustomerName.value = ''
  }
  query.pageNo = 1
  await load()
}
const openSourceDialog = async () => {
  if (!props.projectId || !canEdit.value || !customerId.value) return
  sourceQuery.pageNo = 1; sourceQuery.name = ''; sourceSelection.value = []
  sourceVisible.value = true
  await loadSources()
}
const loadSources = async () => {
  if (!props.projectId) return
  sourceLoading.value = true
  try {
    const page = await ContactApi.getProjectSources(props.projectId, sourceQuery)
    sourceOptions.value = page.list; sourceTotal.value = page.total; sourceSelection.value = []
  } finally { sourceLoading.value = false }
}
const addSelectedSources = async () => {
  if (!props.projectId || !context.value || !sourceSelection.value.length || sourceSaving.value) return
  sourceSaving.value = true
  let created = 0
  try {
    for (const source of sourceSelection.value) {
      await ContactApi.createProjectContact(props.projectId, {
        ...source,
        id: undefined,
        version: undefined,
        sourceContactId: source.id,
        expectedProjectVersion: context.value.project.projectVersion,
        primaryFlag: false,
        status: 0
      }, crypto.randomUUID())
      created++
    }
    sourceVisible.value = false
    message.success(`已加入 ${created} 位客户联系人`)
  } catch {
    message.warning(`已加入 ${created} 位客户联系人，其余未完成，请刷新后重新选择`)
  } finally {
    sourceSaving.value = false
    await load(); emit('changed')
  }
}
const open = (row?: ContactVO) => {
  if (!canEdit.value || (props.projectId && !customerId.value)) return
  createMode.value = 'new'; sourceRows.clear()
  form.value = { name: '', department: '', title: '', mobile: '', phone: '', email: '', remark: '', primaryFlag: false, status: 0, customerId: customerId.value, ...row }
  operationKey = crypto.randomUUID(); initialPrimary = !!row?.primaryFlag
  baseline = JSON.stringify(form.value); visible.value = true
}
const beforeClose = async (done: () => void) => {
  if (saving.value) return
  if (baseline !== JSON.stringify(form.value)) { try { await message.confirm('存在未保存的联系人信息，确定放弃？') } catch { return } }
  done()
}
const save = async () => {
  if (!canEdit.value || saving.value || !(await formRef.value.validate())) return
  if (createMode.value === 'reference' && !form.value.id && !form.value.sourceContactId) return message.warning('请选择来源客户联系人')
  if (initialPrimary && form.value.status === 1) {
    try { await message.confirm('停用主联系人后，项目将暂不设置主联系人，是否确认？') } catch { return }
    form.value.primaryFlag = false; form.value.confirmNoPrimary = true
  }
  saving.value = true
  try {
    const data = { ...form.value, sourceContactId: createMode.value === 'reference' ? form.value.sourceContactId : undefined }
    if (props.projectId) {
      data.expectedProjectVersion = context.value!.project.projectVersion
      if (data.id) await ContactApi.updateProjectContact(props.projectId, data)
      else await ContactApi.createProjectContact(props.projectId, data, operationKey)
    } else if (data.id) await ContactApi.updateMaster(data)
    else await ContactApi.createMaster(data, operationKey)
    visible.value = false; message.success('联系人已保存'); await load(); emit('changed')
  } catch { message.warning('保存未完成，填写内容和本次操作标识已保留，请按提示处理后重试。') }
  finally { saving.value = false }
}
const remove = async (row: ContactVO) => {
  if (!canEdit.value) return
  if (row.primaryFlag && props.projectId) await message.confirm('删除当前主联系人并确认项目暂不设置主联系人？客户主档和历史快照将保留。')
  else await message.delConfirm()
  if (props.projectId) await ContactApi.deleteProjectContact(props.projectId, row, context.value!.project.projectVersion, !!row.primaryFlag)
  else await ContactApi.deleteMaster(row)
  message.success('联系人已删除'); await load(); emit('changed')
}
const restore = async (entry: ContactApi.ContactHistoryVO) => {
  if (!props.projectId || !context.value?.project.canManage) return
  const before = entry.beforeValues ? JSON.parse(entry.beforeValues) : null
  if (!before || before.version == null) return message.warning('删除前版本不可用，无法恢复')
  const status = before.status === 1 ? 1 : 0
  await message.confirm(`恢复该项目联系记录为删除前的${status === 1 ? '停用' : '启用'}状态，不设为主联系人；客户主档和其他项目不变，是否继续？`)
  await ContactApi.restoreProjectContact(props.projectId, entry.projectRelationId, before.version + 1, context.value.project.projectVersion, status)
  message.success('项目联系记录已恢复'); await load(); await historyRef.value?.open(props.projectId); emit('changed')
}
watch(() => props.projectId, async () => {
  visible.value = false; context.value = undefined; query.pageNo = 1
  await load()
}, { immediate: true })
watch(() => query.customerId, () => { if (!props.projectId) { query.pageNo = 1; load() } })
</script>
<style scoped>
.contact-section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.contact-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
  margin: 0;
}

.contact-section-description {
  margin-top: 3px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.contact-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.contact-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.contact-filter :deep(.el-form-item) {
  margin-bottom: 15px;
}

.source-guidance {
  padding: 9px 12px;
  margin-bottom: 14px;
  border-left: 3px solid var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 20px;
}

.selection-status {
  min-height: 20px;
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 20px;
}

.contact-empty {
  display: flex;
  align-items: center;
  flex-direction: column;
  padding: 28px 16px;
  color: var(--el-text-color-secondary);
}

.contact-empty-icon {
  width: 32px;
  height: 32px;
  margin-bottom: 8px;
  color: var(--el-text-color-placeholder);
}

.contact-empty-title {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.contact-empty-description {
  margin-top: 4px;
  font-size: 12px;
  line-height: 20px;
}

.contact-empty-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.contact-empty-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

:deep(.contact-row--primary td.el-table__cell) {
  background: var(--el-color-success-light-9);
}

@media (max-width: 767px) {
  .contact-section-heading {
    flex-direction: column;
  }

  .contact-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .contact-actions :deep(.el-button),
  .contact-empty-actions :deep(.el-button) {
    flex: 1;
  }

  .contact-empty-actions {
    width: 100%;
  }
}
</style>
