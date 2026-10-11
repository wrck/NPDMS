<template>
  <Dialog v-model="visible" title="外采申请明细" width="min(960px, 95vw)">
    <el-descriptions :column="2" border class="mb-15px">
      <el-descriptions-item label="单号">{{ current.code }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ current.name }}</el-descriptions-item>
      <el-descriptions-item label="项目">{{ current.projectId }}</el-descriptions-item>
      <el-descriptions-item label="类型">
        <dict-tag :type="DICT_TYPE.PMS_EXT_PROC_TYPE" :value="current.procurementType ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="产品名称/物料名称">{{ current.productName }}</el-descriptions-item>
      <el-descriptions-item label="产品编码/物料编码">{{ current.productCode }}</el-descriptions-item>
      <el-descriptions-item label="规格型号">{{ current.specification }}</el-descriptions-item>
      <el-descriptions-item label="品牌">{{ current.brand }}</el-descriptions-item>
      <el-descriptions-item label="产品型号/物料型号">{{ current.productModel }}</el-descriptions-item>
      <el-descriptions-item label="数量">{{ current.quantity }}</el-descriptions-item>
      <el-descriptions-item label="单价">{{ current.unitPrice }}</el-descriptions-item>
      <el-descriptions-item label="总价">{{ current.totalPrice }}
        <dict-tag :type="DICT_TYPE.PMS_CURRENCY" :value="current.currency ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="币种">
        <dict-tag :type="DICT_TYPE.PMS_CURRENCY" :value="current.currency ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="供应商名称">{{ current.supplierName }}</el-descriptions-item>
      <el-descriptions-item label="供应商联系人">{{ current.supplierContact }}</el-descriptions-item>
      <el-descriptions-item label="供应商电话">{{ current.supplierPhone }}</el-descriptions-item>
      <el-descriptions-item label="需求日期">{{ current.neededDate }}</el-descriptions-item>
      <el-descriptions-item label="预计到货日期">{{ current.expectedDeliveryDate }}</el-descriptions-item>
      <el-descriptions-item label="申请人"><UserTag :user-id="current.applicantUserId" /></el-descriptions-item>
      <el-descriptions-item label="申请时间">{{ current.applyTime }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="current.status ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="触发来源">
        <dict-tag :type="DICT_TYPE.PMS_TRIGGER_SOURCE" :value="current.triggerSource ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ current.remark }}</el-descriptions-item>
      <el-descriptions-item v-if="current.approveOpinion" label="审批意见" :span="2">
        {{ current.approveOpinion }}
      </el-descriptions-item>
    </el-descriptions>
    <NativeAttachments v-if="current.id" kind="externalProcurement" :entity-id="current.id" :readonly="true" :legacy="current.attachmentFiles" />
  </Dialog>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import NativeAttachments from '../attachment/NativeAttachments.vue'
import type { ExternalProcurementVO } from '@/api/pms/engineering/ext-proc'
import { DICT_TYPE } from '@/utils/dict'
import UserTag from '@/components/UserTag/index.vue'
const props = defineProps<{ modelValue: boolean; current: Partial<ExternalProcurementVO> }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
</script>
