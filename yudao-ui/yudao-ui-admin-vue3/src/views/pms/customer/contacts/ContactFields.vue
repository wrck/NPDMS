<template>
  <el-form-item label="姓名" prop="name"><el-input v-model="model.name" maxlength="64" /></el-form-item>
  <el-form-item label="部门"><el-input v-model="model.department" maxlength="64" /></el-form-item>
  <el-form-item label="职务">
<el-input v-model="model.title" maxlength="64" placeholder="支持手动输入" />
  </el-form-item>
  <el-form-item label="手机"><el-input v-model="model.mobile" maxlength="32" /></el-form-item>
  <el-form-item label="电话"><el-input v-model="model.phone" maxlength="32" /></el-form-item>
  <el-form-item label="邮箱"><el-input v-model="model.email" maxlength="128" /></el-form-item>
  <el-form-item label="主联系人"><el-switch v-model="model.primaryFlag" /></el-form-item>
  <el-form-item label="状态">
    <el-radio-group v-model="model.status"><el-radio v-for="item in statuses" :key="item.value" :value="item.value">{{ item.label }}</el-radio></el-radio-group>
  </el-form-item>
  <el-form-item label="备注"><el-input v-model="model.remark" type="textarea" maxlength="500" /></el-form-item>
</template>
<script setup lang="ts">
import type { ContactVO } from '@/api/pms/customer/contacts'
import { computed } from 'vue'
import { getIntDictOptions } from '@/utils/dict'
const statuses = computed(() => getIntDictOptions('pms_contact_status').filter(item => item.value === 0 || item.value === 1))
// Same field set as the original customer-contact page, shared by master and project-local forms.
const model = defineModel<ContactVO>({ required: true })
</script>
