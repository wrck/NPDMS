<template>
  <Dialog v-model="visible" title="换货申请明细" width="min(960px, 95vw)">
    <el-descriptions :column="2" border class="mb-15px">
      <el-descriptions-item label="单号">{{ current.code }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ current.name }}</el-descriptions-item>
      <el-descriptions-item label="项目"><ProjectTag :project-id="current.projectId" /></el-descriptions-item>
      <el-descriptions-item label="换货类型">
        <dict-tag :type="DICT_TYPE.PMS_MATERIAL_EXCH_TYPE" :value="current.exchangeType ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="物料编码">
        <template v-if="current.productCode">
          <el-tag v-for="code in current.productCode.split(',').filter(Boolean)" :key="code" size="small"
            class="mr-4px">{{ code }}</el-tag>
        </template>
        <span v-else>-</span>
      </el-descriptions-item>
      <el-descriptions-item label="产品型号">{{ current.productModel }}</el-descriptions-item>
      <el-descriptions-item label="数量">{{ current.quantity }}</el-descriptions-item>
      <el-descriptions-item label="申请人">{{ current.applicantUserId === userStore.getUser.id ? userStore.getUser.nickname || current.applicantUserId : current.applicantUserId }}</el-descriptions-item>
      <el-descriptions-item label="申请时间">{{ current.applyTime ? formatDate(current.applyTime) : '-' }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="current.status ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="CRM推送状态">
        <dict-tag :type="DICT_TYPE.PMS_CRM_SYNC_STATUS" :value="current.crmPushStatus ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="CRM推送时间">{{ current.crmPushTime }}</el-descriptions-item>
      <el-descriptions-item label="CRM订单号">{{ current.crmOrderNo }}</el-descriptions-item>
      <el-descriptions-item label="换货原因" :span="2">
        <div v-html="current.reason"></div>
      </el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ current.remark }}</el-descriptions-item>
      <el-descriptions-item v-if="current.approveOpinion" label="审批意见" :span="2">
        {{ current.approveOpinion }}
      </el-descriptions-item>
    </el-descriptions>
    <el-table v-if="current.serials?.length" :data="current.serials" border max-height="360" class="mt-4">
      <el-table-column prop="orderNo" label="订单号" min-width="140" />
      <el-table-column prop="lineNo" label="行号" width="80" />
      <el-table-column prop="itemCode" label="物料编码" min-width="130" show-overflow-tooltip />
      <el-table-column label="换货产品" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.productName || '-' }}</template>
      </el-table-column>
      <el-table-column label="设备类型" width="110">
        <template #default="{ row }">{{ row.deviceTypeName || row.deviceTypeCode }}</template>
      </el-table-column>
      <el-table-column label="换货数量" width="100">
        <template #default="{ row }">{{ row.quantity ?? 1 }}</template>
      </el-table-column>
    </el-table>
  </Dialog>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { MaterialExchangeVO } from '@/api/pms/engineering/material-exch'
import { DICT_TYPE } from '@/utils/dict'
import ProjectTag from '@/components/ProjectTag/index.vue'
import { useUserStore } from '@/store/modules/user'
const userStore = useUserStore()
import { formatDate } from '@/utils/formatTime'
const props = defineProps<{ modelValue: boolean; current: Partial<MaterialExchangeVO> }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
</script>
