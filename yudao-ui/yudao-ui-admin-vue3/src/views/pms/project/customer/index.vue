<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="客户编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="客户名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openCustomer()" v-hasPermi="['pms:customer:create']"
          ><Icon icon="ep:plus" />新增客户</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="客户编码" min-width="130" />
      <el-table-column prop="name" label="客户名称" min-width="180" />
      <el-table-column prop="shortName" label="简称" min-width="100" />
      <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }"
          ><dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="row.status"
        /></template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openContacts(row)">联系人</el-button>
          <el-button
            link
            type="primary"
            @click="openCustomer(row)"
            v-hasPermi="['pms:customer:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="danger"
            @click="removeCustomer(row)"
            v-hasPermi="['pms:customer:delete']"
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

  <Dialog
    v-model="customerVisible"
    :title="customerForm.id ? '编辑客户' : '新增客户'"
    width="620px"
  >
    <el-form ref="customerFormRef" :model="customerForm" :rules="customerRules" label-width="90px">
      <el-form-item label="客户编码" prop="code"
        ><el-input v-model="customerForm.code" :disabled="!!customerForm.id"
      /></el-form-item>
      <el-form-item label="客户名称" prop="name"
        ><el-input v-model="customerForm.name"
      /></el-form-item>
      <el-form-item label="客户简称"><el-input v-model="customerForm.shortName" /></el-form-item>
      <el-form-item label="状态"
        ><el-radio-group v-model="customerForm.status"
          ><el-radio :value="0">启用</el-radio><el-radio :value="1">停用</el-radio></el-radio-group
        ></el-form-item
      >
      <el-form-item label="地址"><el-input v-model="customerForm.address" /></el-form-item>
      <el-form-item label="备注"
        ><el-input v-model="customerForm.remark" type="textarea"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="customerVisible = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="saveCustomer">保存</el-button></template
    >
  </Dialog>

  <Dialog
    v-model="contactsVisible"
    :title="`${selectedCustomer?.name || ''} - 联系人`"
    width="980px"
  >
    <el-button type="primary" class="mb-12px" @click="openContact()">新增联系人</el-button>
    <el-table :data="contacts">
      <el-table-column prop="name" label="姓名" />
      <el-table-column prop="department" label="部门" />
      <el-table-column prop="title" label="职务" />
      <el-table-column prop="mobile" label="手机" />
      <el-table-column prop="email" label="邮箱" min-width="160" />
      <el-table-column prop="primaryFlag" label="主联系人" width="90"
        ><template #default="{ row }">{{
          row.primaryFlag ? '是' : '否'
        }}</template></el-table-column
      >
      <el-table-column label="操作" width="130"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="openContact(row)">编辑</el-button
          ><el-button link type="danger" @click="removeContact(row)">删除</el-button></template
        ></el-table-column
      >
    </el-table>
  </Dialog>

  <Dialog
    v-model="contactVisible"
    :title="contactForm.id ? '编辑联系人' : '新增联系人'"
    width="560px"
  >
    <el-form
      ref="contactFormRef"
      :model="contactForm"
      :rules="{ name: [{ required: true, message: '请输入姓名' }] }"
      label-width="90px"
    >
      <el-form-item label="姓名" prop="name"><el-input v-model="contactForm.name" /></el-form-item>
      <el-form-item label="部门"><el-input v-model="contactForm.department" /></el-form-item>
      <el-form-item label="职务"><el-input v-model="contactForm.title" /></el-form-item>
      <el-form-item label="手机"><el-input v-model="contactForm.mobile" /></el-form-item>
      <el-form-item label="电话"><el-input v-model="contactForm.phone" /></el-form-item>
      <el-form-item label="邮箱"><el-input v-model="contactForm.email" /></el-form-item>
      <el-form-item label="主联系人"><el-switch v-model="contactForm.primaryFlag" /></el-form-item>
      <el-form-item label="状态"
        ><el-radio-group v-model="contactForm.status"
          ><el-radio :value="0">启用</el-radio><el-radio :value="1">停用</el-radio></el-radio-group
        ></el-form-item
      >
    </el-form>
    <template #footer
      ><el-button @click="contactVisible = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="saveContact">保存</el-button></template
    >
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as CustomerApi from '@/api/pms/project/customer'
import type { CustomerContactVO, CustomerVO } from '@/api/pms/project/customer'

defineOptions({ name: 'PmsCustomer' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<CustomerVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, code: '', name: '' })
const customerVisible = ref(false)
const contactsVisible = ref(false)
const contactVisible = ref(false)
const customerFormRef = ref()
const contactFormRef = ref()
const customerForm = reactive<CustomerVO>({ code: '', name: '', status: 0 })
const contactForm = reactive<CustomerContactVO>({ name: '', primaryFlag: false, status: 0 })
const selectedCustomer = ref<CustomerVO>()
const contacts = ref<CustomerContactVO[]>([])
const customerRules = {
  code: [{ required: true, message: '请输入客户编码' }],
  name: [{ required: true, message: '请输入客户名称' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await CustomerApi.getCustomerPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openCustomer = (row?: CustomerVO) => {
  Object.assign(
    customerForm,
    { id: undefined, code: '', name: '', shortName: '', status: 0, address: '', remark: '' },
    row || {}
  )
  customerVisible.value = true
}
const saveCustomer = async () => {
  await customerFormRef.value.validate()
  saving.value = true
  try {
    customerForm.id
      ? await CustomerApi.updateCustomer(customerForm)
      : await CustomerApi.createCustomer(customerForm)
    message.success('保存成功')
    customerVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const removeCustomer = async (row: CustomerVO) => {
  await message.delConfirm()
  await CustomerApi.deleteCustomer(row.id!)
  message.success('删除成功')
  await load()
}
const openContacts = async (row: CustomerVO) => {
  selectedCustomer.value = row
  contactsVisible.value = true
  contacts.value = await CustomerApi.getContactListByCustomerId(row.id!)
}
const openContact = (row?: CustomerContactVO) => {
  Object.assign(
    contactForm,
    {
      id: undefined,
      name: '',
      department: '',
      title: '',
      mobile: '',
      phone: '',
      email: '',
      primaryFlag: false,
      status: 0
    },
    row || {}
  )
  contactVisible.value = true
}
const saveContact = async () => {
  await contactFormRef.value.validate()
  saving.value = true
  try {
    contactForm.customerId = selectedCustomer.value!.id
    contactForm.id
      ? await CustomerApi.updateContact(contactForm)
      : await CustomerApi.createContact(contactForm)
    message.success('保存成功')
    contactVisible.value = false
    contacts.value = await CustomerApi.getContactListByCustomerId(selectedCustomer.value!.id!)
  } finally {
    saving.value = false
  }
}
const removeContact = async (row: CustomerContactVO) => {
  await message.delConfirm()
  await CustomerApi.deleteContact(row.id!)
  message.success('删除成功')
  contacts.value = await CustomerApi.getContactListByCustomerId(selectedCustomer.value!.id!)
}
onMounted(load)
</script>
