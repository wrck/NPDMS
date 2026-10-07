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

      <el-alert v-if="pendingIntent" title="上次操作结果尚未确认" type="warning" :closable="false" class="mb-8px">
        <template #default>
          <el-button :loading="executing" @click="recoverPending">确认上次操作结果</el-button>
          <span>尚无回执时，请恢复原输入后重试。</span>
        </template>
      </el-alert>
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

      <template v-if="professionalView">
        <el-form inline>
          <el-form-item label="项目">
            <PmsEntitySelect :model-value="professionalProjectId" :api="ProjectApi.getProjectPage"
              :disabled="professionalSwitching || executing" @update:model-value="selectProfessionalProject"
              label-field="projectName" value-field="id" query-field="projectName" placeholder="请选择项目" />
          </el-form-item>
        </el-form>
        <el-skeleton v-if="professionalLoading" :rows="6" animated />
        <component v-if="professionalProject" ref="professionalRef" :is="professionalView.component"
          :key="String(professionalProject.id)" v-bind="professionalProps" />
        <el-empty v-else description="选择项目后可查看和办理业务" />
      </template>
      <template v-else>
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
        @search="(filters) => loadPage(filters, true)"
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
          :fields="detail.fields"
          :presentation="formPresentation"
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
              <el-button
                v-for="operation in customOperations"
                :key="operation.code"
                :disabled="executing || !operation.executable || !current"
                :title="operation.reason"
                @click="runCustom(operation)"
              >{{ operation.name }}</el-button>
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
        <DefaultBusinessDelivery
          v-if="current && !current.revisionId"
          :readonly="props.readonly || props.allowedActions !== undefined || !updateOperation?.executable"
          :owner-module="props.ownerModule" :entity-type="props.entityType" :entity-id="current.ref.entityId"
          :deliverable-type="props.deliverableType" @changed="reloadCurrent"
        />
        <DeliveryPanel
          v-if="deliveryEnabled && current"
          :readonly="props.readonly || props.allowedActions !== undefined"
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
          :operations="(detail?.operations || []).map(operation => availableOperation(operation)!)"
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
    </template>
  </ContentWrap>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { isBusinessViewId } from '@/api/pms/platform/business-view/ids'
import type { BusinessEntityData, BusinessOperationReceipt, OperationVO } from '@/api/pms/platform/businessmodel'
import { isConcurrencyConflict, serverErrorMessage, useBusinessEntity } from './useBusinessEntity'
import BusinessEntityList from './BusinessEntityList.vue'
import BusinessEntityForm from './BusinessEntityForm.vue'
import DeliveryPanel from './DeliveryPanel.vue'
import DefaultBusinessDelivery from './DefaultBusinessDelivery.vue'
import ApprovalPanel from './ApprovalPanel.vue'
import ContentHistoryPanel from './ContentHistoryPanel.vue'
import { resolveStandaloneBusinessEntityView } from '@/components/BusinessView/registry'
import * as ProjectApi from '@/api/pms/project/projects'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import PmsEntitySelect from '@/components/PmsEntitySelect/index.vue'

defineOptions({ name: 'BusinessEntityHost' })
const props = defineProps<{ ownerModule: string; entityType: string; initialEntityId?: string | number;
  expectedStableCode?: string; deliverableType?: string; readonly?: boolean; allowedActions?: string[] }>()

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
  createOperation: declaredCreate,
  updateOperation: declaredUpdate,
  rows,
  listLoading,
  listError,
  sliceComplete,
  loadPage,
  current,
  formPresentation,
  readEntity,
  executing,
  pendingIntent,
  recover,
  execute
} = entity

const professionalView = computed(() => props.expectedStableCode ? undefined : resolveStandaloneBusinessEntityView(detail.value?.viewCode))
// The selector requests a transition; it never replaces the active Owner before its leave protocol succeeds.
const professionalProjectId = ref<number | string>()
const professionalProject = shallowRef<ProjectMasterVO>()
const professionalLoading = ref(false)
const professionalSwitching = ref(false)
const professionalRef = ref<{
  requestLeave?: () => boolean | Promise<boolean>
  discardChanges?: () => boolean
  isDirty?: () => boolean
}>()
const professionalProps = computed(() => {
  if (!professionalProject.value || !professionalView.value) return {}
  const resolved = professionalView.value.resolve(professionalProject.value)
  return { ...resolved, ...(props.allowedActions !== undefined ? { allowedActions: props.allowedActions } : {}), ...(professionalSwitching.value || props.readonly ? { readonly: true } : {}) }
})
let leaving: Promise<boolean> | undefined
const guardContentLeave = (): Promise<boolean> => {
  if (executing.value) return Promise.resolve(false)
  if (leaving) return leaving
  leaving = (async () => {
    try {
      if (professionalRef.value?.requestLeave) return await professionalRef.value.requestLeave()
      return !professionalRef.value?.isDirty?.()
    } catch { return false }
  })().finally(() => { leaving = undefined })
  return leaving
}
const requestLeave = () => professionalSwitching.value ? Promise.resolve(false) : guardContentLeave()
const selectProfessionalProject = async (value: unknown) => {
  const id = value == null || value === '' ? undefined : value
  if (id !== undefined && !isBusinessViewId(id)) return false
  if (professionalSwitching.value || String(id ?? '') === String(professionalProjectId.value ?? '')) return false
  professionalSwitching.value = true
  const previous = professionalProject.value, content = professionalRef.value
  try {
    if (!(await guardContentLeave())) return false
    operationError.value = ''
    professionalLoading.value = id !== undefined
    // Keep the existing professional component mounted while reading the destination.
    const project = id === undefined ? undefined : await ProjectApi.getProject(id as number)
    if (project && String(project.id) !== String(id)) throw new Error('项目读取身份不匹配')
    if (professionalProject.value !== previous || professionalRef.value !== content) return false
    if (content?.discardChanges?.() === false) return false
    professionalProjectId.value = id as number | string | undefined
    professionalProject.value = project
    return true
  } catch (error) {
    operationError.value = serverErrorMessage(error, '项目读取失败，当前业务仍保留')
    return false
  } finally {
    professionalLoading.value = false
    professionalSwitching.value = false
  }
}
// Includes browser back/forward and a parameter change on the same route record before its keyed Owner unmounts.
onBeforeRouteLeave(requestLeave)
onBeforeRouteUpdate(requestLeave)

const mode = ref<'LIST' | 'FORM'>('LIST')
const formRef = ref<{ buildInput: () => Promise<Record<string, unknown>>; resetValidation: () => void }>()
const receipt = shallowRef<BusinessOperationReceipt>()
const conflictMessage = ref('')
const operationError = ref('')
const editingOperation = ref<OperationVO>()

const availableOperation = (operation?: OperationVO): OperationVO | undefined => operation &&
  (props.readonly || props.allowedActions !== undefined && !props.allowedActions.includes(operation.code))
  ? { ...operation, executable: false, reason: '当前运行入口未开放该操作' } : operation
const createOperation = computed(() => availableOperation(declaredCreate.value))
const updateOperation = computed(() => availableOperation(declaredUpdate.value))
const pageTitle = computed(() => (mode.value === 'FORM' ? '统一业务实体办理' : detail.value?.title || '统一业务实体'))
const customOperations = computed(() => (detail.value?.operations || []).filter(operation => operation.kind === 'DOMAIN_COMMAND').map(operation => availableOperation(operation)!))
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
  if (detail.value && props.expectedStableCode && detail.value.stableCode !== props.expectedStableCode) {
    detail.value = undefined
    loadError.value = '运行视图与声明模型不匹配'
    return
  }
  if (detail.value && !professionalView.value) {
    await loadPage([], true)
    if (pendingIntent.value) await recoverPending()
    if (props.initialEntityId != null && !current.value) await openEntity({ ref: { entityId: props.initialEntityId } } as BusinessEntityData)
  }
}

onMounted(load)

const recoverPending = async () => {
  operationError.value = ''
  try {
    const result = await recover()
    if (!result) return
    receipt.value = result
    if (result.entityRef) {
      await readEntity(result.entityRef.entityId)
      editingOperation.value = updateOperation.value
      mode.value = 'FORM'
    }
    await reloadList()
  } catch (error) { operationError.value = serverErrorMessage(error, '上次操作结果暂时无法确认') }
}

const reloadList = () => loadPage(undefined, true)

const openCreate = () => {
  receipt.value = undefined
  conflictMessage.value = ''
  operationError.value = ''
  current.value = undefined
  formPresentation.value = undefined
  editingOperation.value = createOperation.value
  mode.value = 'FORM'
}
const openEntity = async (row: BusinessEntityData) => {
  receipt.value = undefined
  conflictMessage.value = ''
  operationError.value = ''
  editingOperation.value = updateOperation.value
  try {
    await readEntity(row.ref.entityId, row.revisionId)
    mode.value = 'FORM'
  } catch (error: any) {
    operationError.value = serverErrorMessage(error, '实体读取失败')
  }
}
const reopen = async () => {
  if (!current.value) return
  try {
    await readEntity(current.value.ref.entityId, current.value.revisionId)
    formRef.value?.resetValidation()
  } catch (error: any) {
    operationError.value = serverErrorMessage(error, '重新读取失败')
  }
}
// 交付面板动作（登记/确认/撤回）后刷新当前读取状态，保持并发依据最新。
const reloadCurrent = async () => {
  if (!current.value) return
  try {
    await readEntity(current.value.ref.entityId, current.value.revisionId)
  } catch (error: any) {
    operationError.value = serverErrorMessage(error, '刷新实体状态失败')
  }
}
const backToList = () => {
  mode.value = 'LIST'
  reloadList()
}

const save = async () => {
  if (!editingOperation.value?.executable || props.readonly) return
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
      isCreate.value ? undefined : current.value?.concurrencyBasis,
      isCreate.value ? undefined : current.value?.revisionId
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
      if (current.value) await readEntity(current.value.ref.entityId, current.value.revisionId)
    } else {
      operationError.value =
        error?.response?.data?.msg || error?.message || '操作执行失败'
    }
  }
}

const runCustom = async (operation: OperationVO) => {
  if (!current.value || !operation.executable || props.readonly || executing.value) return
  operationError.value = ''
  try {
    receipt.value = await execute(operation, current.value.ref.entityId, {}, current.value.concurrencyBasis)
    await reloadCurrent()
  } catch (error) {
    operationError.value = serverErrorMessage(error, '操作执行失败')
  }
}

defineExpose({ reloadList, requestLeave, isDirty: () => !!professionalRef.value?.isDirty?.() })
</script>
