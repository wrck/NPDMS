<template>
  <el-drawer v-model="visible" size="min(720px, 100%)" title="合同详情" destroy-on-close>
    <el-skeleton :loading="loading" animated>
      <el-descriptions v-if="contract" :column="narrow ? 1 : 2" border>
        <el-descriptions-item label="合同编号">{{ contract.contractNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ contract.status }}</el-descriptions-item>
        <el-descriptions-item label="合同名称">{{
          contract.contractName || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="合同类型">{{
          contract.contractType || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="所属公司">
          {{ contract.companyName || contract.companyCode }}（{{ contract.companyCode }}）
        </el-descriptions-item>
        <el-descriptions-item label="客户">
          {{ contract.customerName || contract.customerCode || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="来源版本">{{ contract.sourceVersion }}</el-descriptions-item>
        <el-descriptions-item label="来源更新时间">
          {{ contract.sourceUpdatedAt || '—' }}
        </el-descriptions-item>
      </el-descriptions>
      <template v-if="detail">
        <el-divider content-position="left">关联订单</el-divider>
        <el-table :data="detail.relatedOrders" empty-text="暂无关联订单">
          <el-table-column prop="orderNo" label="订单号" min-width="150" />
          <el-table-column prop="orderType" label="类型" min-width="100" />
          <el-table-column prop="status" label="状态" min-width="100" />
        </el-table>
        <el-divider content-position="left">项目关系</el-divider>
        <el-table :data="detail.projectRelations" empty-text="暂无项目关系">
          <el-table-column prop="projectId" label="项目 ID" min-width="130" />
          <el-table-column prop="relationRole" label="关系角色" min-width="120" />
          <el-table-column prop="status" label="状态" min-width="100" />
        </el-table>
        <el-divider content-position="left">来源截止</el-divider>
        <el-descriptions :column="narrow ? 1 : 2" border>
          <el-descriptions-item label="来源系统">{{ detail.sourceSystem }}</el-descriptions-item>
          <el-descriptions-item label="来源版本">{{ detail.sourceVersion }}</el-descriptions-item>
          <el-descriptions-item label="同步截止">{{ detail.sourceSyncTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="来源更新时间">{{ detail.sourceUpdatedAt || '—' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-skeleton>

    <el-divider content-position="left">建立项目关系</el-divider>
    <el-alert
      title="提交时服务端会重新校验当前公司授权与项目操作范围。"
      type="info"
      :closable="false"
      show-icon
      class="mb-16px"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
      <el-form-item label="项目 ID" prop="projectId">
        <el-input-number v-model="form.projectId" :min="1" :controls="false" class="w-100%" />
      </el-form-item>
      <el-form-item label="关系角色" prop="relationRole">
        <el-input v-model="form.relationRole" maxlength="32" placeholder="可选，由业务场景提供" />
      </el-form-item>
      <el-form-item label="维护原因" prop="reason">
        <el-input v-model="form.reason" type="textarea" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        v-hasPermi="['pms:commerce:contract:relate']"
        @click="submit"
      >
        建立关系
      </el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { computed, reactive, ref } from 'vue'
import { useWindowSize } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import * as CommerceApi from '@/api/pms/commerce'
import type { ContractDetailRespVO, ContractRespVO } from '@/api/pms/commerce'
import { commerceIntentOf, createCommerceIntentStore } from '../commerceInteraction'

defineOptions({ name: 'PmsCommerceContractDetail' })
const emit = defineEmits<{ success: [] }>()
const message = useMessage()
const { width } = useWindowSize()
const narrow = computed(() => width.value < 768)
const visible = ref(false)
const loading = ref(false)
const submitting = ref(false)
const contract = ref<ContractRespVO>()
const detail = ref<ContractDetailRespVO>()
const formRef = ref<FormInstance>()
const intents = createCommerceIntentStore()
const form = reactive({ projectId: undefined as number | undefined, relationRole: '', reason: '' })
const rules: FormRules = {
  projectId: [{ required: true, message: '请输入项目 ID', trigger: 'blur' }],
  reason: [{ required: true, whitespace: true, message: '请输入维护原因', trigger: 'blur' }]
}

const open = async (contractId: number, projectId?: number) => {
  visible.value = true
  loading.value = true
  form.projectId = projectId
  form.relationRole = ''
  form.reason = ''
  try {
    detail.value = await CommerceApi.getContract(contractId)
    contract.value = detail.value.contract
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  if (!contract.value || !(await formRef.value?.validate())) return
  const data = {
    projectId: form.projectId!,
    relationRole: form.relationRole.trim() || undefined,
    reason: form.reason.trim()
  }
  const intent = commerceIntentOf('relate-contract', { contractId: contract.value.id, data })
  submitting.value = true
  try {
    await CommerceApi.relateContractProject(contract.value.id, data, intents.key(intent))
    intents.complete(intent)
    message.success('项目—合同关系已建立')
    visible.value = false
    emit('success')
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
