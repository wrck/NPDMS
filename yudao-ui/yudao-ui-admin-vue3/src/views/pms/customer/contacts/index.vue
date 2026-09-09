<template>
  <div class="min-w-0 w-full max-w-full">
    <ContentWrap>
      <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
        <el-form-item label="客户" prop="customerId">
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
          <el-input v-model="query.name" clearable class="!w-220px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="query.status" clearable class="!w-160px">
            <el-option v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button :loading="loading" @click="load"><Icon icon="ep:search" />查询</el-button>
          <el-button type="primary" :disabled="!canEdit || (!!projectId && (!customerId || !customerEnabled))" @click="open()" v-hasPermi="['pms:customer-contact:create']">
            <Icon icon="ep:plus" />新增联系人
          </el-button>
          <el-button v-if="projectId && context?.project.canManage && !customerId" :disabled="!query.customerId" :loading="loading" @click="bindCustomer" v-hasPermi="['pms:project:update']">关联项目客户</el-button>
          <el-button v-if="projectId && context?.project.canManage && customerId" :disabled="!customerEnabled" @click="importSources" v-hasPermi="['pms:customer-contact:create']">载入客户联系人</el-button>
          <el-button v-if="projectId && context?.project.canViewHistory" @click="historyRef?.open(projectId)">变更历史</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
    </ContentWrap>
    <ContentWrap>
      <el-table v-loading="loading" :data="rows" empty-text="暂无联系人数据">
        <el-table-column prop="customerId" label="客户编号" width="110">
          <template #default="{ row }"><CustomerTag :customer-id="row.customerId" /></template>
        </el-table-column>
        <el-table-column label="客户名称" min-width="160">
          <template #default="{ row }">{{ row.customerName || context?.customerName || selectedCustomerName }}</template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" min-width="100" />
        <el-table-column prop="department" label="部门" min-width="120" />
        <el-table-column prop="title" label="职务" min-width="120" />
        <el-table-column prop="mobile" label="手机" min-width="130" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="primaryFlag" label="主联系人" width="90">
          <template #default="{ row }">{{ row.primaryFlag && row.status === 0 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }"><dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="row.status" /></template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="!canEdit" @click="open(row)" v-hasPermi="['pms:customer-contact:update']">编辑</el-button>
            <el-button link type="danger" :disabled="!canEdit" @click="remove(row)" v-hasPermi="['pms:customer-contact:delete']">删除</el-button>
          </template>
        </el-table-column>
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
        <ContactFields v-model="form" />
      </el-form>
      <template #footer>
        <el-button @click="beforeClose(() => visible = false)">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!canEdit" @click="save">保存</el-button>
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
import CustomerTag from '@/components/CustomerTag/index.vue'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'

defineOptions({ name: 'PmsCustomerContactsWorkbench' })
const props = defineProps<{ projectId?: number }>()
const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const loading = ref(false), saving = ref(false), visible = ref(false), error = ref('')
const context = ref<ProjectContactContext>()
const query = reactive({ pageNo: 1, pageSize: 10, customerId: undefined as number | undefined, name: '', status: undefined as number | undefined })
const selectedCustomerName = ref('')
const rows = ref<ContactVO[]>([]), total = ref(0)
const customerId = computed(() => props.projectId ? context.value?.project.customerId : query.customerId)
const customerEnabled = computed(() => context.value?.customerStatus === 'ENABLED')
const canEdit = computed(() => props.projectId ? !!context.value?.project.canManage && context.value.sensitiveRead : checkPermi(['pms:customer:sensitive-read']))
const form = ref<ContactVO>({ name: '', status: 0, primaryFlag: false })
const formRef = ref()
const historyRef = ref<InstanceType<typeof ContactHistoryDialog>>()
const createMode = ref<'new' | 'reference'>('new')
let operationKey = '', baseline = '', initialPrimary = false, sequence = 0
const rules = { name: [{ required: true, message: '请输入联系人姓名' }], customerId: [{ required: true, message: '请选择客户' }] }
const sourceRows = new Map<number, ContactVO>()
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
    return context.value
  } catch { if (current === sequence) error.value = '联系人加载失败，请检查权限或重试。' }
  finally { if (current === sequence) loading.value = false }
}
const bindCustomer = async () => {
  if (!props.projectId || !query.customerId || !context.value) return
  await message.confirm('将所选客户关联到当前项目？已有客户关联不会被此入口替换。')
  await ContactApi.associateCustomer(props.projectId, query.customerId, context.value.project.projectVersion)
  await load(); await importSources(); emit('changed')
}
const importSources = async () => {
  if (!props.projectId || !context.value) return
  const count = await ContactApi.importDefaults(props.projectId, context.value.project.projectVersion)
  message.success(`已载入 ${count} 条新引用；已有编辑、停用和删除记录保持不变`)
  await load(); emit('changed')
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
  const loaded = await load()
  if (props.projectId && loaded?.project.canManage && loaded.project.customerId && loaded.customerStatus === 'ENABLED' && checkPermi(['pms:customer-contact:create'])) {
    try { await ContactApi.importDefaults(props.projectId, loaded.project.projectVersion); await load(); emit('changed') }
    catch { error.value = '客户联系人默认引用未完成，请处理提示后点击“载入客户联系人”重试。' }
  }
}, { immediate: true })
watch(() => query.customerId, () => { if (!props.projectId) { query.pageNo = 1; load() } })
</script>
