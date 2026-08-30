<template>
  <main class="arrival-workbench">
    <ContentWrap>
      <header class="page-heading">
        <div>
          <h1>到货签收工作台</h1>
          <p>按权威项目范围处理多批到货、差异与签收证据。</p>
        </div>
        <el-button
          type="primary"
          v-hasPermi="['pms:arrival-acceptance:create']"
          @click="openCreate"
        >
          <Icon icon="ep:plus" />创建到货草稿
        </el-button>
      </header>
      <el-form :model="query" class="filter-bar" label-position="top">
        <el-form-item label="项目">
          <PmsEntitySelect
            v-model="query.projectId"
            :api="ProjectApi.getProjectPage"
            label-field="projectName"
            value-field="id"
            query-field="projectName"
            placeholder="全部可见项目"
            clearable
          />
        </el-form-item>
        <el-form-item label="业务批次码">
          <el-input v-model="query.batchCode" clearable maxlength="64" @keyup.enter="loadPage" />
        </el-form-item>
        <el-form-item label="批次状态">
          <el-select v-model="query.status" clearable placeholder="全部状态">
            <el-option
              v-for="status in statuses"
              :key="status"
              :label="projectArrivalProgress(status).label"
              :value="status"
            />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-actions" label=" ">
          <el-button type="primary" @click="loadPage"><Icon icon="ep:search" />查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-table v-loading="loading" :data="rows" row-key="id" @row-dblclick="openDetail">
        <el-table-column prop="batchCode" label="业务批次" min-width="150" />
        <el-table-column
          prop="logisticsNo"
          label="物流单号"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column prop="signerName" label="签收人" min-width="110" />
        <el-table-column prop="arrivedAt" label="到货时间" min-width="170" />
        <el-table-column label="批次进度" min-width="150">
          <template #default="{ row }">
            <el-tag :type="projectArrivalProgress(row.status).tone">
              {{ projectArrivalProgress(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="归档同步" min-width="190">
          <template #default="{ row }">
            {{ evidenceSyncPresentation(row.evidenceSyncStatus).label }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.allowedActions.includes('EDIT_DRAFT')"
              link
              type="primary"
              v-hasPermi="['pms:arrival-acceptance:edit-own-draft']"
              @click="editFromList(row)"
              >编辑草稿</el-button
            >
            <el-button
              v-if="row.allowedActions.includes('CONFIRM')"
              link
              type="success"
              v-hasPermi="['pms:arrival-acceptance:confirm']"
              @click="confirmFromList(row)"
              >项目经理确认</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-model:page="query.pageNo"
        v-model:limit="query.pageSize"
        :total="total"
        @pagination="loadPage"
      />
    </ContentWrap>

    <el-drawer v-model="detailVisible" title="到货签收详情" :size="drawerSize">
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <div class="detail-heading">
            <div>
              <span class="eyebrow">{{ detail.batchCode }}</span>
              <h2>{{ detail.logisticsNo }}</h2>
            </div>
            <el-tag :type="projectArrivalProgress(detail.status).tone">
              {{ projectArrivalProgress(detail.status).label }}
            </el-tag>
          </div>
          <el-descriptions :column="detailColumns" border>
            <el-descriptions-item label="项目ID">{{ detail.projectId }}</el-descriptions-item>
            <el-descriptions-item label="签收人">{{ detail.signerName }}</el-descriptions-item>
            <el-descriptions-item label="到货时间">{{ detail.arrivedAt }}</el-descriptions-item>
            <el-descriptions-item label="聚合版本">{{ detail.version }}</el-descriptions-item>
            <el-descriptions-item label="记录关系">{{
              successorReasonPresentation(detail.successorReason)
            }}</el-descriptions-item>
          </el-descriptions>
          <div class="detail-actions">
            <el-button
              v-if="detail.allowedActions.includes('EDIT_DRAFT')"
              v-hasPermi="['pms:arrival-acceptance:edit-own-draft']"
              @click="openEditDetail"
              >编辑草稿</el-button
            >
            <el-button
              v-if="detail.allowedActions.includes('SUBMIT')"
              type="primary"
              v-hasPermi="['pms:arrival-acceptance:edit-own-draft']"
              @click="runSimpleAction('submit')"
              >提交批次</el-button
            >
            <el-button
              v-if="detail.allowedActions.includes('CONFIRM')"
              type="success"
              v-hasPermi="['pms:arrival-acceptance:confirm']"
              @click="runSimpleAction('confirm')"
              >项目经理确认</el-button
            >
            <el-button
              v-if="
                detail.status === 'CONFIRMED' &&
                detail.allowedActions.includes('RESOLVE_DIFFERENCE')
              "
              v-hasPermi="['pms:arrival-acceptance:resolve-difference']"
              @click="openCorrection"
              >纠正签收信息</el-button
            >
          </div>

          <ArrivalLineEditor :model-value="detailLines" :editable="false" />
          <div v-if="detail.allowedActions.includes('RAISE_DIFFERENCE')" class="raise-actions">
            <el-button
              v-hasPermi="['pms:arrival-acceptance:resolve-difference']"
              :disabled="!detail.currentLines.length"
              @click="openRaise"
              >提出到货差异</el-button
            >
          </div>
          <ArrivalDifferencePanel
            :differences="detail.differences"
            :aggregate-status="detail.status"
            :can-resolve="detail.allowedActions.includes('RESOLVE_DIFFERENCE')"
            :evidence-revision="effectiveEvidenceRevision"
            @resolve="resolveDifference"
          />
          <ArrivalEvidencePanel
            :acceptance-id="detail.id"
            :evidence="detail.evidence"
            :editable="detail.allowedActions.includes('EDIT_DRAFT')"
            @revision="saveEvidenceRevision"
          />
        </template>
      </div>
    </el-drawer>

    <ArrivalAcceptanceForm
      v-model="formVisible"
      :detail="editingDetail"
      :correction="correctionMode"
      @create="createDraft"
      @patch="patchDraft"
      @correct="correctInformation"
    />

    <Dialog v-model="raiseVisible" title="提出到货差异" width="min(600px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="到货明细">
          <el-select v-model="raiseForm.lineId" class="!w-full">
            <el-option
              v-for="line in detail?.currentLines || []"
              :key="String(line.id)"
              :label="lineLabel(line)"
              :value="String(line.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="差异类型">
          <el-select v-model="raiseForm.type" class="!w-full">
            <el-option label="数量不符" value="QUANTITY_MISMATCH" />
            <el-option label="型号或序列号不符" value="MODEL_OR_SN_MISMATCH" />
            <el-option label="外观或质量异常" value="APPEARANCE_OR_QUALITY" />
            <el-option label="证据不完整" value="EVIDENCE_INCOMPLETE" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因"
          ><el-input v-model="raiseForm.reason" type="textarea" :rows="3"
        /></el-form-item>
        <el-form-item label="风险说明"
          ><el-input v-model="raiseForm.risk" type="textarea" :rows="2"
        /></el-form-item>
        <el-form-item
          v-if="selectedRaiseLine?.scopeType === 'ORDER_MODEL_QUANTITY'"
          label="差异数量"
        >
          <el-input-number
            v-model="raiseForm.quantity"
            :min="0.001"
            :max="selectedRaiseLine.expectedQuantity || undefined"
            :precision="3"
          />
        </el-form-item>
        <el-alert v-if="!effectiveEvidenceRevision" type="warning" :closable="false">
          请先在证据面板上传差异证据。
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="raiseVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!canRaise" @click="raiseDifference"
          >提交差异</el-button
        >
      </template>
    </Dialog>
  </main>
</template>

<script setup lang="ts">
import { useWindowSize } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import * as ProjectApi from '@/api/pms/project/projects'
import * as ArrivalApi from '@/api/pms/engineering/arrival-acceptance'
import type {
  ArrivalDetail,
  ArrivalDraftLine,
  ArrivalLine,
  ArrivalListItem,
  CreateArrivalRequest,
  FileRevision,
  PatchArrivalRequest,
  ResolveDifferenceRequest
} from '@/api/pms/engineering/arrival-acceptance'
import ArrivalAcceptanceForm from './components/ArrivalAcceptanceForm.vue'
import ArrivalDifferencePanel from './components/ArrivalDifferencePanel.vue'
import ArrivalEvidencePanel from './components/ArrivalEvidencePanel.vue'
import ArrivalLineEditor from './components/ArrivalLineEditor.vue'
import {
  arrivalAcceptanceLayout,
  createArrivalIntentStore,
  evidenceSyncPresentation,
  projectArrivalProgress,
  resolveArrivalCommandFailure,
  successorReasonPresentation
} from './arrivalAcceptanceInteraction'

defineOptions({ name: 'PmsArrivalAcceptance' })
const message = useMessage()
const { width } = useWindowSize()
const intentStore = createArrivalIntentStore()
const statuses: ArrivalApi.ArrivalStatus[] = [
  'DRAFT',
  'PARTIALLY_ACCEPTED',
  'DIFFERENCE_PENDING',
  'ACCEPTED',
  'CONFIRMED'
]
const loading = ref(false)
const detailLoading = ref(false)
const rows = ref<ArrivalListItem[]>([])
const total = ref(0)
const query = reactive<{
  projectId?: ArrivalApi.WireLong
  batchCode?: string
  status?: ArrivalApi.ArrivalStatus
  pageNo: number
  pageSize: number
}>({ pageNo: 1, pageSize: 20 })
const detailVisible = ref(false)
const detail = ref<ArrivalDetail | null>(null)
const formVisible = ref(false)
const editingDetail = ref<ArrivalDetail | null>(null)
const correctionMode = ref(false)
const stagedEvidenceRevision = ref<FileRevision | null>(null)
const raiseVisible = ref(false)
const raiseForm = reactive({
  lineId: '',
  type: 'QUANTITY_MISMATCH',
  reason: '',
  risk: '',
  quantity: 0
})
const layout = computed(() => arrivalAcceptanceLayout(width.value))
const drawerSize = computed(() =>
  layout.value === 'mobile' ? '100%' : layout.value === 'tablet' ? '86%' : '72%'
)
const detailColumns = computed(() => (layout.value === 'mobile' ? 1 : 2))
const detailLines = computed<ArrivalDraftLine[]>(() =>
  (detail.value?.currentLines || []).map((line) =>
    line.scopeType === 'DEVICE'
      ? {
          scopeType: 'DEVICE',
          lineId: line.id,
          expectedLineVersion: line.version,
          deviceId: line.deviceId!,
          received: line.status === 'ACCEPTED'
        }
      : {
          scopeType: 'ORDER_MODEL_QUANTITY',
          lineId: line.id,
          expectedLineVersion: line.version,
          orderLineId: line.orderLineId!,
          productCode: line.productCode,
          modelCode: line.modelCode,
          acceptedQuantity: line.acceptedQuantity || 0,
          unitCode: line.unitCode || ''
        }
  )
)
const effectiveEvidenceRevision = computed<FileRevision | null>(() => {
  if (stagedEvidenceRevision.value) return stagedEvidenceRevision.value
  const evidence = detail.value?.evidence
  if (!evidence) return null
  return {
    artifactId: evidence.artifactId,
    referenceKey: evidence.referenceKey,
    versionNo: evidence.fileVersionNo,
    scopeVersion: evidence.fileScopeVersion,
    fileFactVersion: evidence.fileFactVersion,
    hash: evidence.fileHash
  }
})
const selectedRaiseLine = computed(() =>
  detail.value?.currentLines.find((line) => String(line.id) === raiseForm.lineId)
)
const canRaise = computed(
  () =>
    Boolean(
      selectedRaiseLine.value && effectiveEvidenceRevision.value && raiseForm.reason.trim()
    ) &&
    (selectedRaiseLine.value?.scopeType !== 'ORDER_MODEL_QUANTITY' || raiseForm.quantity > 0)
)

const loadPage = async () => {
  loading.value = true
  try {
    const page = await ArrivalApi.getArrivalPage(query)
    rows.value = page.list
    total.value = Number(page.total)
  } finally {
    loading.value = false
  }
}
const resetQuery = () => {
  Object.assign(query, { projectId: undefined, batchCode: undefined, status: undefined, pageNo: 1 })
  loadPage()
}
const loadDetail = async (id: ArrivalApi.WireLong) => {
  detailLoading.value = true
  try {
    detail.value = await ArrivalApi.getArrivalDetail(id)
    stagedEvidenceRevision.value = null
  } finally {
    detailLoading.value = false
  }
}
const openDetail = async (row: ArrivalListItem) => {
  detailVisible.value = true
  await loadDetail(row.id)
}
const openCreate = () => {
  editingDetail.value = null
  correctionMode.value = false
  formVisible.value = true
}
const editFromList = async (row: ArrivalListItem) => {
  await loadDetail(row.id)
  editingDetail.value = detail.value
  correctionMode.value = false
  formVisible.value = true
}
const openEditDetail = () => {
  editingDetail.value = detail.value
  correctionMode.value = false
  formVisible.value = true
}
const openCorrection = () => {
  editingDetail.value = detail.value
  correctionMode.value = true
  formVisible.value = true
}

const executeIntent = async (intent: string, call: (key: string) => Promise<unknown>) => {
  const key = intentStore.key(intent)
  try {
    await call(key)
    intentStore.complete(intent)
    await loadPage()
    if (detail.value) await loadDetail(detail.value.id)
    return true
  } catch (error) {
    const recovery = resolveArrivalCommandFailure(error)
    if (recovery !== 'RETAIN_INTENT') intentStore.complete(intent)
    if (recovery === 'REFRESH_AGGREGATE' && detail.value) await loadDetail(detail.value.id)
    if (recovery === 'RETAIN_INTENT')
      message.warning('响应结果未知，已保留本次幂等键，请重试原操作')
    return false
  }
}
const createDraft = async (payload: CreateArrivalRequest) => {
  const intent = `create:${JSON.stringify(payload)}`
  const succeeded = await executeIntent(intent, async (key) => {
    const created = await ArrivalApi.createArrival(payload, key)
    detailVisible.value = true
    await loadDetail(created.id)
  })
  if (succeeded) {
    formVisible.value = false
    message.success('到货草稿已创建')
  }
}
const patchDraft = async (payload: PatchArrivalRequest) => {
  if (!editingDetail.value) return
  try {
    await ArrivalApi.patchArrival(editingDetail.value.id, editingDetail.value.version, payload)
    formVisible.value = false
    message.success('草稿已保存')
    await loadPage()
    await loadDetail(editingDetail.value.id)
  } catch (error) {
    if (resolveArrivalCommandFailure(error) === 'REFRESH_AGGREGATE')
      await loadDetail(editingDetail.value.id)
  }
}
const runSimpleAction = async (name: 'submit' | 'confirm') => {
  if (!detail.value) return
  const current = detail.value
  const succeeded = await executeIntent(`${name}:${current.id}:${current.version}`, (key) =>
    name === 'submit'
      ? ArrivalApi.submitArrival(current.id, current.version, key)
      : ArrivalApi.confirmArrival(current.id, current.version, key)
  )
  if (succeeded) message.success(name === 'submit' ? '批次已提交' : '批次已确认')
}
const confirmFromList = async (row: ArrivalListItem) => {
  await openDetail(row)
  await runSimpleAction('confirm')
}
const saveEvidenceRevision = async (revision: FileRevision) => {
  if (!detail.value) return
  stagedEvidenceRevision.value = revision
  editingDetail.value = detail.value
  await patchDraft({ evidenceRevision: revision })
}
const resolveDifference = async (payload: ResolveDifferenceRequest) => {
  if (!detail.value) return
  const current = detail.value
  const succeeded = await executeIntent(
    `resolve:${current.id}:${current.version}:${JSON.stringify(payload)}`,
    (key) => ArrivalApi.resolveArrivalDifference(current.id, current.version, payload, key)
  )
  if (succeeded) message.success('差异处置已提交')
}
const correctInformation = async (value: { patch: PatchArrivalRequest; reason: string }) => {
  if (!detail.value || !effectiveEvidenceRevision.value) {
    return message.warning('请先绑定当前签收证据')
  }
  const current = detail.value
  const payload: ResolveDifferenceRequest = {
    resolutionType: 'CORRECT_INFORMATION',
    expectedSourceVersion: current.version,
    reason: value.reason,
    correctionPatch: {
      logisticsNo: value.patch.logisticsNo || current.logisticsNo,
      arrivedAt: value.patch.arrivedAt || current.arrivedAt,
      signerName: value.patch.signerName || current.signerName,
      lines: value.patch.lines || detailLines.value
    },
    evidenceRevision: effectiveEvidenceRevision.value
  }
  const succeeded = await executeIntent(
    `correct:${current.id}:${current.version}:${JSON.stringify(payload)}`,
    (key) => ArrivalApi.resolveArrivalDifference(current.id, current.version, payload, key)
  )
  if (succeeded) {
    formVisible.value = false
    message.success('信息纠正后继草稿已创建')
  }
}
const openRaise = () => {
  const first = detail.value?.currentLines[0]
  Object.assign(raiseForm, {
    lineId: first ? String(first.id) : '',
    type: 'QUANTITY_MISMATCH',
    reason: '',
    risk: '',
    quantity: first?.expectedQuantity || 0
  })
  raiseVisible.value = true
}
const lineLabel = (line: ArrivalLine) =>
  line.scopeType === 'DEVICE'
    ? `设备 ${line.deviceId}`
    : `订单行 ${line.orderLineId} · ${line.productCode || line.modelCode || '未命名型号'}`
const scopeOf = (line: ArrivalLine): ArrivalApi.ArrivalScope =>
  line.scopeType === 'DEVICE'
    ? { scopeType: 'DEVICE', deviceId: line.deviceId! }
    : {
        scopeType: 'ORDER_MODEL_QUANTITY',
        orderLineId: line.orderLineId!,
        productCode: line.productCode,
        modelCode: line.modelCode,
        quantity: raiseForm.quantity,
        unitCode: line.unitCode || ''
      }
const raiseDifference = async () => {
  if (!detail.value || !selectedRaiseLine.value || !effectiveEvidenceRevision.value) return
  const current = detail.value
  const payload: ArrivalApi.RaiseDifferenceRequest = {
    arrivalLineId: selectedRaiseLine.value.id,
    expectedLineVersion: selectedRaiseLine.value.version,
    differenceTypeCode: raiseForm.type,
    scopeSnapshot: scopeOf(selectedRaiseLine.value),
    reason: raiseForm.reason.trim(),
    riskDescription: raiseForm.risk.trim() || null,
    evidenceRevision: effectiveEvidenceRevision.value
  }
  const succeeded = await executeIntent(
    `raise:${current.id}:${current.version}:${JSON.stringify(payload)}`,
    (key) => ArrivalApi.raiseArrivalDifference(current.id, current.version, payload, key)
  )
  if (succeeded) {
    raiseVisible.value = false
    message.success('到货差异已提出')
  }
}

onMounted(loadPage)
</script>

<style scoped lang="scss">
.arrival-workbench {
  min-width: 0;
}

.page-heading,
.detail-heading,
.detail-actions,
.raise-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.page-heading h1,
.page-heading p,
.detail-heading h2 {
  margin: 0;
}

.page-heading p {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
}

.filter-bar {
  display: grid;
  grid-template-columns: repeat(3, minmax(160px, 1fr)) auto;
  gap: 0 16px;
  margin-top: 20px;
}

.detail-body {
  min-height: 240px;
}

.eyebrow {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.detail-actions,
.raise-actions {
  justify-content: flex-start;
  margin-top: 16px;
}

@media (width <= 1023px) {
  .filter-bar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 767px) {
  .page-heading,
  .detail-heading,
  .filter-bar {
    display: grid;
    grid-template-columns: 1fr;
  }

  .page-heading :deep(.el-button),
  .filter-actions :deep(.el-button) {
    width: 100%;
    margin: 0 0 8px;
  }
}
</style>
