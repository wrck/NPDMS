<template>
  <div>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="客户" prop="customerId">
        <PmsEntitySelect
          v-model="query.customerId"
          :api="CustomerApi.getCustomerPage"
          :label-field="['code', 'name']"
          value-field="id"
          query-field="name"
          placeholder="请选择客户"
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item label="姓名" prop="name">
        <el-input v-model="query.name" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="open()" v-hasPermi="['pms:customer-contact:create']"
          ><Icon icon="ep:plus" />新增联系人</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无联系人数据">
      <el-table-column prop="customerId" label="客户编号" width="110">
        <template #default="{ row }">
          <CustomerTag :customer-id="row.customerId" />
        </template>
      </el-table-column>
      <el-table-column prop="customerName" label="客户名称" min-width="160" />
      <el-table-column prop="name" label="姓名" min-width="100" />
      <el-table-column prop="department" label="部门" min-width="120" />
      <el-table-column prop="title" label="职务" min-width="120" />
      <el-table-column prop="mobile" label="手机" min-width="130" />
      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
      <el-table-column prop="primaryFlag" label="主联系人" width="90">
        <template #default="{ row }">{{ row.primaryFlag ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)" v-hasPermi="['pms:customer-contact:update']"
            >编辑</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:customer-contact:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>

  <Dialog v-model="visible" :title="form.id ? '编辑联系人' : '新增联系人'" width="620px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="客户" prop="customerId">
        <PmsEntitySelect
          v-model="form.customerId"
          :api="CustomerApi.getCustomerPage"
          :label-field="['code', 'name']"
          value-field="id"
          query-field="name"
          placeholder="请选择客户"
        />
      </el-form-item>
      <el-form-item label="姓名" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="部门"><el-input v-model="form.department" /></el-form-item>
      <el-form-item label="职务"><el-input v-model="form.title" /></el-form-item>
      <el-form-item label="手机"><el-input v-model="form.mobile" /></el-form-item>
      <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
      <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
      <el-form-item label="主联系人"><el-switch v-model="form.primaryFlag" /></el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="0">启用</el-radio>
          <el-radio :value="1">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as ContactApi from '@/api/pms/project/customer-contact'
import * as CustomerApi from '@/api/pms/project/customer'
import CustomerTag from '@/components/CustomerTag/index.vue'
import type { CustomerContactVO } from '@/api/pms/project/customer-contact'

defineOptions({ name: 'PmsCustomerContact' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<CustomerContactVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  customerId: undefined as number | undefined,
  name: '',
  status: undefined as number | undefined
})
const visible = ref(false)
const formRef = ref()
const form = reactive<CustomerContactVO>({
  name: '',
  primaryFlag: false,
  status: 0
})
const rules = {
  customerId: [{ required: true, message: '请选择客户' }],
  name: [{ required: true, message: '请输入姓名' }],
  primaryFlag: [{ required: true, message: '请选择是否主联系人' }],
  status: [{ required: true, message: '请选择状态' }]
}

const load = async () => {
  loading.value = true
  try {
    const params: any = { ...query }
    if (!params.customerId) delete params.customerId
    const data = await ContactApi.getContactPage(params)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const open = (row?: CustomerContactVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      customerId: undefined,
      name: '',
      department: '',
      title: '',
      mobile: '',
      phone: '',
      email: '',
      primaryFlag: false,
      status: 0,
      remark: ''
    },
    row || {}
  )
  visible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    form.id ? await ContactApi.updateContact(form) : await ContactApi.createContact(form)
    message.success('保存成功')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: CustomerContactVO) => {
  await message.delConfirm()
  await ContactApi.deleteContact(row.id!)
  message.success('删除成功')
  await load()
}
onMounted(load)
</script>
