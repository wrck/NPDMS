<template>
  <Dialog v-model="visible" :title="form.id ? '编辑外采申请' : '新建外采申请'" width="min(960px, 95vw)" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form ref="formRef" :model="form" :rules="rules" :disabled="saving" label-width="120px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="项目" prop="projectId">
            <el-input v-if="sourceSurveyId" :model-value="`工勘所属项目 #${form.projectId}`" disabled />
            <PmsEntitySelect
              v-else
              v-model="form.projectId"
              :api="ProjectApi.getProjectPage"
              label-field="projectName"
              value-field="id"
              query-field="projectName"
              placeholder="请选择项目"
              :disabled="!!form.id"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="单号" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 EP-2026-001" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="类型" prop="procurementType">
            <el-select v-model="form.procurementType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_EXT_PROC_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="产品名称/物料名称" prop="productName">
            <el-input v-model="form.productName" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="产品编码/物料编码" prop="productCode">
            <el-input v-model="form.productCode" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="规格型号" prop="specification">
            <el-input v-model="form.specification" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="品牌" prop="brand"><el-input v-model="form.brand" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="产品型号/物料型号" prop="productModel"><el-input v-model="form.productModel" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="数量" prop="quantity">
            <el-input-number v-model="form.quantity" :min="0" :precision="2" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="单价" prop="unitPrice">
            <el-input-number v-model="form.unitPrice" :min="0" :precision="2" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="总价" prop="totalPrice">
            <el-input-number v-model="form.totalPrice" :min="0" :precision="2" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="币种" prop="currency">
            <el-select v-model="form.currency" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_CURRENCY)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="供应商名称" prop="supplierName">
            <el-input v-model="form.supplierName" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="供应商联系人" prop="supplierContact">
            <el-input v-model="form.supplierContact" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="供应商电话" prop="supplierPhone">
            <el-input v-model="form.supplierPhone" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="需求日期" prop="neededDate">
            <el-date-picker
              v-model="form.neededDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择需求日期"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="预计到货日期" prop="expectedDeliveryDate">
            <el-date-picker
              v-model="form.expectedDeliveryDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择预计到货日期"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="申请人" prop="applicantUserId">
            <PmsEntitySelect
              v-model="form.applicantUserId"
              :api="UserApi.getUserPage"
              label-field="nickname"
              value-field="id"
              query-field="nickname"
              placeholder="请选择申请人"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="申请时间" prop="applyTime">
            <el-date-picker
              v-model="form.applyTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择申请时间"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="附件" prop="attachmentFiles">
            <UploadFile v-model="form.attachmentFiles!" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
        <el-col v-if="!sourceSurveyId" :span="12">
          <el-form-item label="触发来源" prop="triggerSource">
            <el-select v-model="form.triggerSource" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_TRIGGER_SOURCE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="emit('save')">保存</el-button>
    </template>
  </Dialog>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import * as ProjectApi from '@/api/pms/project/projects'
import * as UserApi from '@/api/system/user'
import type { ExternalProcurementVO } from '@/api/pms/engineering/ext-proc'
const props = defineProps<{ modelValue: boolean; form: ExternalProcurementVO; sourceSurveyId?: number | string; saving: boolean; error?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; save: [] }>()
const visible = computed({ get: () => props.modelValue, set: value => { if (!props.saving) emit('update:modelValue', value) } })
const formRef = ref()
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入单号' }],
  name: [{ required: true, message: '请输入名称' }],
  procurementType: [{ required: true, message: '请选择类型' }],
  productName: [{ required: true, message: '请输入产品名称/物料名称' }],
  quantity: [{ required: true, message: '请输入数量' }],
  applicantUserId: [{ required: true, message: '请选择申请人' }],
  applyTime: [{ required: true, message: '请选择申请时间' }]
}
defineExpose({ validate: () => formRef.value.validate() })
</script>
