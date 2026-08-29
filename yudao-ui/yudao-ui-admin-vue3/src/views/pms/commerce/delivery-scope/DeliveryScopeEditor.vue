<template>
  <el-drawer
    v-model="visible"
    size="min(780px, 100%)"
    :title="mode === 'assign' ? '预览并分配交付范围' : '预览并调整交付范围'"
    destroy-on-close
  >
    <el-alert
      v-if="!projectContext"
      title="缺少项目工作台提供的项目 ID、项目版本或项目范围版本，不能执行写操作。"
      type="warning"
      :closable="false"
      show-icon
    />
    <template v-else>
      <el-descriptions :column="narrow ? 1 : 3" border class="mb-18px">
        <el-descriptions-item label="项目 ID">{{ projectContext.projectId }}</el-descriptions-item>
        <el-descriptions-item label="项目版本">{{
          projectContext.projectVersion
        }}</el-descriptions-item>
        <el-descriptions-item label="范围版本">{{
          projectContext.projectScopeVersion
        }}</el-descriptions-item>
      </el-descriptions>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="订单行" prop="orderLineId">
          <el-select
            v-model="form.orderLineId"
            filterable
            :disabled="mode === 'adjust'"
            class="w-100%"
            placeholder="选择服务端返回的订单行"
          >
            <el-option
              v-for="line in orderLines"
              :key="line.id"
              :value="line.id"
              :label="`${line.orderNo}/${line.lineNo} · ${line.itemCode} · 可用 ${line.openQty} ${line.unitCode}`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="分配数量" prop="quantity">
          <el-input-number
            v-model="form.quantity"
            :min="0.000001"
            :controls="false"
            class="w-100%"
          />
        </el-form-item>
        <el-form-item label="设备序列号">
          <el-input
            v-model="form.serialText"
            type="textarea"
            :rows="3"
            placeholder="可选；多个序列号使用逗号、空格或换行分隔"
          />
        </el-form-item>
        <el-form-item label="操作原因" prop="reason">
          <el-input v-model="form.reason" type="textarea" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <div class="editor-actions">
        <el-button :loading="previewing" @click="preview"
          ><Icon icon="ep:view" />预览校验</el-button
        >
        <el-button
          v-if="mode === 'assign'"
          type="primary"
          :disabled="!previewResult?.allowed"
          :loading="submitting"
          v-hasPermi="['pms:commerce:scope:assign']"
          @click="submit"
          >确认分配</el-button
        >
        <el-button
          v-else
          type="primary"
          :disabled="!previewResult?.allowed"
          :loading="submitting"
          v-hasPermi="['pms:commerce:scope:adjust']"
          @click="submit"
          >确认调整</el-button
        >
      </div>
      <el-result
        v-if="previewResult"
        :icon="previewResult.allowed ? 'success' : 'warning'"
        :title="previewResult.allowed ? '预览通过，可以提交' : '预览未通过'"
      >
        <template #sub-title>
          <div v-if="previewResult.validationErrors.length" class="validation-errors">
            <div v-for="error in previewResult.validationErrors" :key="error">{{ error }}</div>
          </div>
        </template>
        <template #extra>
          <el-descriptions :column="narrow ? 1 : 2" border>
            <el-descriptions-item label="可用数量">{{
              previewResult.availableQuantity
            }}</el-descriptions-item>
            <el-descriptions-item label="已分配数量">{{
              previewResult.allocatedQuantity
            }}</el-descriptions-item>
            <el-descriptions-item label="项目编码">{{
              previewResult.projectCode
            }}</el-descriptions-item>
            <el-descriptions-item label="发生时办事处">
              {{ previewResult.officeDepartmentName }}（{{ previewResult.officeDepartmentCode }}）
            </el-descriptions-item>
          </el-descriptions>
          <el-table :data="previewResult.occupiedScopes" class="mt-16px" empty-text="没有占用范围">
            <el-table-column prop="deliveryScopeId" label="范围 ID" min-width="120" />
            <el-table-column prop="projectId" label="占用项目" min-width="120" />
            <el-table-column prop="allocatedQuantity" label="占用数量" min-width="100" />
            <el-table-column prop="scopeStatus" label="状态" min-width="110" />
          </el-table>
        </template>
      </el-result>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'
import { useWindowSize } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import * as CommerceApi from '@/api/pms/commerce'
import type {
  DeliveryScopeAdjustReqVO,
  DeliveryScopeAssignReqVO,
  DeliveryScopePreviewResult,
  DeliveryScopeRespVO,
  SalesOrderLineRespVO
} from '@/api/pms/commerce'
import type { ProjectRouteContext } from '../commerceInteraction'
import {
  commerceIntentOf,
  createCommerceIntentStore,
  splitSerialNumbers
} from '../commerceInteraction'

defineOptions({ name: 'PmsCommerceDeliveryScopeEditor' })
const props = defineProps<{ projectContext?: ProjectRouteContext }>()
const emit = defineEmits<{ success: [] }>()
const message = useMessage()
const { width } = useWindowSize()
const narrow = computed(() => width.value < 768)
const visible = ref(false)
const previewing = ref(false)
const submitting = ref(false)
const mode = ref<'assign' | 'adjust'>('assign')
const currentScope = ref<DeliveryScopeRespVO>()
const orderLines = ref<SalesOrderLineRespVO[]>([])
const previewResult = ref<DeliveryScopePreviewResult>()
const formRef = ref<FormInstance>()
const intents = createCommerceIntentStore()
const form = reactive({
  orderLineId: undefined as number | undefined,
  quantity: undefined as number | undefined,
  serialText: '',
  reason: ''
})
const rules: FormRules = {
  orderLineId: [{ required: true, message: '请选择订单行', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入大于 0 的数量', trigger: 'blur' }],
  reason: [{ required: true, whitespace: true, message: '请输入操作原因', trigger: 'blur' }]
}
const selectedLine = computed(() => orderLines.value.find((item) => item.id === form.orderLineId))

watch(form, () => {
  previewResult.value = undefined
})

const loadOrderLines = async () => {
  const data = await CommerceApi.getSalesOrderLinePage({ pageNo: 1, pageSize: 200 })
  orderLines.value = data.list
}
const openAssign = async () => {
  mode.value = 'assign'
  currentScope.value = undefined
  Object.assign(form, { orderLineId: undefined, quantity: undefined, serialText: '', reason: '' })
  previewResult.value = undefined
  visible.value = true
  if (props.projectContext) await loadOrderLines()
}
const openAdjust = async (scope: DeliveryScopeRespVO) => {
  mode.value = 'adjust'
  currentScope.value = scope
  Object.assign(form, {
    orderLineId: scope.orderLineId,
    quantity: scope.allocatedQuantity,
    serialText: scope.details
      .map((item) => item.serialNo)
      .filter(Boolean)
      .join('\n'),
    reason: ''
  })
  previewResult.value = undefined
  visible.value = true
  if (props.projectContext) {
    await loadOrderLines()
    if (!orderLines.value.some((item) => item.id === scope.orderLineId)) {
      message.warning('当前授权范围内无法读取该订单行，不能调整')
    }
  }
}
const requestOf = () => {
  if (!props.projectContext || !selectedLine.value || !form.quantity) return undefined
  return {
    projectId: props.projectContext.projectId,
    expectedProjectVersion: props.projectContext.projectVersion,
    expectedProjectScopeVersion: props.projectContext.projectScopeVersion,
    orderLineId: selectedLine.value.id,
    expectedOrderLineSourceVersion: selectedLine.value.sourceVersion,
    proposedQuantity: form.quantity,
    serialNumbers: splitSerialNumbers(form.serialText)
  }
}
const preview = async () => {
  if (!(await formRef.value?.validate())) return
  const request = requestOf()
  if (!request) return
  previewing.value = true
  try {
    previewResult.value = await CommerceApi.previewDeliveryScope(request)
  } finally {
    previewing.value = false
  }
}
const submit = async () => {
  const request = requestOf()
  if (!request || !previewResult.value?.allowed || !props.projectContext) return
  submitting.value = true
  try {
    if (mode.value === 'assign') {
      const data: DeliveryScopeAssignReqVO = {
        projectId: request.projectId,
        expectedProjectScopeVersion: request.expectedProjectScopeVersion,
        orderLineId: request.orderLineId,
        expectedOrderLineSourceVersion: request.expectedOrderLineSourceVersion,
        allocatedQuantity: request.proposedQuantity,
        serialNumbers: request.serialNumbers,
        reason: form.reason.trim()
      }
      const intent = commerceIntentOf('assign', data)
      await CommerceApi.assignDeliveryScope(
        data,
        props.projectContext.projectVersion,
        intents.key(intent)
      )
      intents.complete(intent)
    } else if (currentScope.value) {
      const data: DeliveryScopeAdjustReqVO = {
        projectId: request.projectId,
        expectedProjectVersion: request.expectedProjectVersion,
        expectedProjectScopeVersion: request.expectedProjectScopeVersion,
        expectedOrderLineSourceVersion: request.expectedOrderLineSourceVersion,
        proposedQuantity: request.proposedQuantity,
        serialNumbers: request.serialNumbers,
        reason: form.reason.trim()
      }
      const intent = commerceIntentOf('adjust', { scopeId: currentScope.value.id, data })
      await CommerceApi.adjustDeliveryScope(
        currentScope.value.id,
        data,
        currentScope.value.allocationVersion,
        intents.key(intent)
      )
      intents.complete(intent)
    }
    message.success(mode.value === 'assign' ? '交付范围已分配' : '交付范围已追加新版本')
    visible.value = false
    emit('success')
  } finally {
    submitting.value = false
  }
}

defineExpose({ openAssign, openAdjust })
</script>

<style scoped>
.editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.validation-errors {
  color: var(--el-color-warning-dark-2);
  text-align: left;
}

@media (width <= 767px) {
  .editor-actions {
    flex-direction: column;
  }

  .editor-actions :deep(.el-button) {
    width: 100%;
    margin: 0;
  }
}
</style>
