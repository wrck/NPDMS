<template>
  <ContentWrap>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon>
      <template #default>
        <el-button link type="primary" @click="load">重新装载</el-button>
      </template>
    </el-alert>
    <template v-else-if="detail">
      <el-page-header v-if="mode === 'FORM'" :content="pageTitle" @back="backToList" />
      <h3 v-else>{{ pageTitle }}</h3>

      <el-alert
        v-if="receipt"
        :title="`操作回执：${receipt.outcome}；并发依据：${receipt.newConcurrencyBasis ?? '-'}${
          receipt.failureReason ? '；原因：' + receipt.failureReason : ''
        }`"
        :type="receipt.outcome === 'FAILED' ? 'error' : 'success'"
        :closable="false"
        show-icon
        class="mb-8px"
      />
      <el-alert
        v-if="conflictMessage"
        :title="conflictMessage"
        type="warning"
        :closable="false"
        show-icon
        class="mb-8px"
      />
      <el-alert
        v-if="operationError"
        :title="operationError"
        type="error"
        :closable="false"
        show-icon
        class="mb-8px"
      />

      <BusinessEntityList
        v-if="mode === 'LIST'"
        :rows="rows"
        :readable-fields="readableFields"
        :create-operation="createOperation"
        :list-loading="listLoading"
        :list-error="listError"
        :slice-complete="sliceComplete"
        @create="openCreate"
        @open="openEntity"
        @reload="reloadList"
        @load-more="loadPage()"
      />
      <template v-else>
        <el-alert
          v-if="!editingOperation?.executable"
          :title="editingOperation?.reason || '没有可用的保存操作'"
          type="warning"
          :closable="false"
          class="mb-8px"
        />
        <BusinessEntityForm
          ref="formRef"
          :writable-fields="writableFields"
          :initial-values="current?.fieldValues"
          :disabled="executing || !editingOperation?.executable"
        >
          <template #extra>
            <el-form-item>
              <el-button
                type="primary"
                :loading="executing"
                :disabled="!editingOperation?.executable"
                @click="save"
              >
                保存
              </el-button>
              <el-button :disabled="executing" @click="backToList">返回列表</el-button>
              <el-button v-if="current" :disabled="executing" @click="reopen">
                重新读取（重开）
              </el-button>
            </el-form-item>
          </template>
        </BusinessEntityForm>
        <el-descriptions
          v-if="current"
          :column="2"
          title="当前读取状态"
          border
          class="mt-8px"
          size="small"
        >
          <el-descriptions-item label="实体ID">{{ current.ref.entityId }}</el-descriptions-item>
          <el-descriptions-item label="并发依据">
            {{ current.concurrencyBasis ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="修订ID">{{ current.revisionId ?? '当前对象' }}</el-descriptions-item>
          <el-descriptions-item label="可用性">
            {{ current.available ? '可用' : current.unavailableReason }}
          </el-descriptions-item>
        </el-descriptions>
        <DeliveryPanel
          v-if="deliveryEnabled && current"
          :owner-module="props.ownerModule"
          :entity-type="props.entityType"
          :entity-id="current.ref.entityId"
          @changed="reloadCurrent"
        />
        <ApprovalPanel
          v-if="approvalEnabled && current"
          :owner-module="props.ownerModule"
          :entity-type="props.entityType"
          :entity-id="current.ref.entityId"
          :operations="detail?.operations"
          @changed="reloadCurrent"
        />
        <ContentHistoryPanel
          v-if="contentHistoryEnabled && current"
          :owner-module="props.ownerModule"
          :entity-type="props.entityType"
          :entity-id="current.ref.entityId"
          :fields="detail?.fields"
          :current="current"
          @changed="reloadCurrent"
        />
      </template>
    </template>
  </ContentWrap>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import type { BusinessEntityData, BusinessOperationReceipt, OperationVO } from '@/api/pms/platform/businessmodel'
import { isConcurrencyConflict, serverErrorMessage, useBusinessEntity } from './useBusinessEntity'
import BusinessEntityList from './BusinessEntityList.vue'
import BusinessEntityForm from './BusinessEntityForm.vue'
import DeliveryPanel from './DeliveryPanel.vue'
import ApprovalPanel from './ApprovalPanel.vue'
import ContentHistoryPanel from './ContentHistoryPanel.vue'

defineOptions({ name: 'BusinessEntityHost' })
const props = defineProps<{ ownerModule: string; entityType: string }>()

const entity = useBusinessEntity(
  () => props.ownerModule,
  () => props.entityType
)
const {
  detail,
  loadError,
  loadDetail,
  writableFields,
  readableFields,
  createOperation,
  updateOperation,
  rows,
  listLoading,
  listError,
  sliceComplete,
  loadPage,
  current,
  readEntity,
  executing,
  execute
} = entity

const mode = ref<'LIST' | 'FORM'>('LIST')
const formRef = ref<{ buildInput: () => Promise<Record<string, unknown>>; resetValidation: () => void }>()
const receipt = shallowRef<BusinessOperationReceipt>()
const conflictMessage = ref('')
const operationError = ref('')
const editingOperation = ref<OperationVO>()

const pageTitle = computed(() => (mode.value === 'FORM' ? '统一业务实体办理' : detail.value?.title || '统一业务实体'))
const isCreate = computed(() => editingOperation.value?.kind === 'CREATE')
// 能力声明驱动：仅描述符启用 DELIVERY 且可读的实体渲染公共交付面板，不按实体名分支。
const deliveryEnabled = computed(() =>
  detail.value?.capabilities?.some((capability) => capability.type === 'DELIVERY' && capability.enabled) ?? false
)
const approvalEnabled = computed(() =>
  detail.value?.capabilities?.some((capability) => capability.type === 'APPROVAL' && capability.enabled) ?? false
)
const contentHistoryEnabled = computed(() =>
  detail.value?.capabilities?.some((capability) => capability.type === 'CONTENT_HISTORY' && capability.enabled) ?? false
)

const load = async () => {
  receipt.value = undefined
  conflictMessage.value = ''
  operationError.value = ''
  await loadDetail()
  if (detail.value) await loadPage([], true)
}

onMounted(load)

const reloadList = () => loadPage([], true)

const openCreate = () => {
  receipt.value = undefined
  conflictMessage.value = ''
  operationError.value = ''
  current.value = undefined
  editingOperation.value = createOperation.value
  mode.value = 'FORM'
}
const openEntity = async (row: BusinessEntityData) => {
  receipt.value = undefined
  conflictMessage.value = ''
  operationError.value = ''
  editingOperation.value = updateOperation.value
  try {
    await readEntity(row.ref.entityId)
    mode.value = 'FORM'
  } catch (error: any) {
    operationError.value = serverErrorMessage(error, '实体读取失败')
  }
}
const reopen = async () => {
  if (!current.value) return
  try {
    await readEntity(current.value.ref.entityId)
    formRef.value?.resetValidation()
  } catch (error: any) {
    operationError.value = serverErrorMessage(error, '重新读取失败')
  }
}
// 交付面板动作（登记/确认/撤回）后刷新当前读取状态，保持并发依据最新。
const reloadCurrent = async () => {
  if (!current.value) return
  try {
    await readEntity(current.value.ref.entityId)
  } catch (error: any) {
    operationError.value = serverErrorMessage(error, '刷新实体状态失败')
  }
}
const backToList = () => {
  mode.value = 'LIST'
  reloadList()
}

const save = async () => {
  if (!editingOperation.value) return
  receipt.value = undefined
  conflictMessage.value = ''
  operationError.value = ''
  let input: Record<string, unknown>
  try {
    input = await formRef.value!.buildInput()
  } catch {
    return
  }
  try {
    const result = await execute(
      editingOperation.value,
      isCreate.value ? undefined : current.value?.ref.entityId,
      input,
      isCreate.value ? undefined : current.value?.concurrencyBasis
    )
    receipt.value = result
    if (result.outcome !== 'FAILED' && result.entityRef) {
      // 保存成功后重开：以服务端回执的并发依据重新读取，不沿用本地旧状态。
      await readEntity(result.entityRef.entityId)
    }
    if (isCreate.value) {
      editingOperation.value = updateOperation.value
    }
  } catch (error: any) {
    if (isConcurrencyConflict(error)) {
      conflictMessage.value = '修改冲突：该实体已被其他操作更新，已重新读取服务端最新内容，请确认后再次保存。'
      if (current.value) await readEntity(current.value.ref.entityId)
    } else {
      operationError.value =
        error?.response?.data?.msg || error?.message || '操作执行失败'
    }
  }
}

defineExpose({ reloadList })
</script>
