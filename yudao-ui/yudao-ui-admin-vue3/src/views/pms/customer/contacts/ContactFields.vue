<template>
  <el-form-item label="姓名" prop="name"><el-input v-model="model.name" maxlength="64" /></el-form-item>
  <el-form-item label="部门"><el-input v-model="model.department" maxlength="64" /></el-form-item>
  <el-form-item label="职务">
    <el-select v-model="model.title" clearable filterable placeholder="请选择职务">
      <el-option v-if="model.title && !titles.some(item => item.value === model.title)" :label="model.title" :value="model.title" disabled />
      <el-option v-for="item in titles" :key="item.value" :label="item.label" :value="item.value" />
    </el-select>
  </el-form-item>
  <el-form-item v-if="project" label="联系人角色">
    <el-select v-model="model.roleCode" clearable filterable :placeholder="roles.length ? '请选择客户联系人角色' : '暂无客户联系人角色，可在字典管理中配置'">
      <el-option v-if="model.roleCode && !roles.some(item => item.value === model.roleCode)" :label="model.roleCode" :value="model.roleCode" disabled />
      <el-option v-for="item in roles" :key="item.value" :label="item.label" :value="item.value" />
    </el-select>
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
import { getStrDictOptions, getIntDictOptions } from '@/utils/dict'
defineProps<{ project?: boolean }>()
const titles = computed(() => getStrDictOptions('pms_contact_title'))
const roles = computed(() => getStrDictOptions('pms_customer_contact_role'))
const statuses = computed(() => getIntDictOptions('pms_contact_status').filter(item => item.value === 0 || item.value === 1))
// Same field set as the original customer-contact page, shared by master and project-local forms.
const model = defineModel<ContactVO>({ required: true })
</script>
