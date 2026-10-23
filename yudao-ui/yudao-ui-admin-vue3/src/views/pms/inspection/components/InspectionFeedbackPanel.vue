<template>
  <section class="feedback-panel" aria-labelledby="inspection-feedback-title">
    <ContentWrap class="inspection-card" :body-style="{}">
      <div class="inspection-card__header"
        ><h2 id="inspection-feedback-title">反馈筛选</h2
        ><el-button v-if="canQuery" :disabled="locked" :loading="loading" @click="loadFeedback"
          >刷新</el-button
        ></div
      >
      <el-alert
        v-if="!canQuery"
        title="没有误报查询权限"
        type="warning"
        :closable="false"
        show-icon
      />
      <template v-else>
        <el-form
          :model="query"
          label-position="top"
          :disabled="locked"
          class="filter-grid"
          @submit.prevent="search"
        >
          <el-form-item label="检测 ID"
            ><el-input v-model="query.detectionId" clearable
          /></el-form-item>
          <el-form-item label="规则修订 ID"
            ><el-input
              v-model="query.ruleRevisionId"
              clearable
              inputmode="numeric"
              placeholder="完整修订编号"
          /></el-form-item>
          <el-form-item label="检测分类"
            ><el-select v-model="query.categoryCode" clearable placeholder="全部分类"
              ><el-option
                v-for="item in categories"
                :key="item.value"
                :value="item.value"
                :label="item.label" /></el-select
          ></el-form-item>
          <el-form-item label="严重级别"
            ><el-select v-model="query.severityCode" clearable placeholder="全部级别"
              ><el-option
                v-for="item in severities"
                :key="item.value"
                :value="item.value"
                :label="item.label" /></el-select
          ></el-form-item>
          <el-form-item v-if="hasDetailsPermission" label="设备型号"
            ><el-input v-model="query.productModel" clearable
          /></el-form-item>
          <el-form-item label="记录范围"
            ><el-select v-model="query.currentOnly"
              ><el-option label="当前及历史误报" :value="false" /><el-option
                label="仅当前误报"
                :value="true" /></el-select
          ></el-form-item>
          <el-form-item label="确认状态"
            ><el-select v-model="query.confirmation" clearable placeholder="全部状态"
              ><el-option value="PENDING" label="待确认" /><el-option
                value="VALID"
                label="确认有效误报" /><el-option value="INVALID" label="非有效误报" /></el-select
          ></el-form-item>
          <el-form-item label="检出时间范围" class="range-field"
            ><el-date-picker
              v-model="timeRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
          /></el-form-item>
          <el-form-item class="filter-actions"
            ><el-button type="primary" native-type="submit" :loading="loading">查询</el-button
            ><el-button @click="resetQuery">重置</el-button></el-form-item
          >
        </el-form>
        <el-alert
          v-if="queryError"
          :title="queryError"
          type="error"
          :closable="false"
          show-icon
          class="mb-16px"
          ><template #default
            ><el-button link type="primary" :disabled="locked || loading" @click="loadFeedback"
              >重新查询</el-button
            ></template
          ></el-alert
        >
        <p class="help-text" role="status">{{
          feedback
            ? `本次数据截止：${formatDate(feedback.dataAsOf)}`
            : lastSuccessAt
              ? `最近成功查询截止：${formatDate(lastSuccessAt)}，本次结果尚未获取。`
              : '尚未获取查询结果。'
        }}</p>
      </template>
    </ContentWrap>
    <el-alert
      v-if="operationError"
      :title="operationError"
      :type="pending ? 'warning' : 'error'"
      :closable="false"
      show-icon
      class="operation-alert"
      ><template #default
        ><el-button v-if="pending" type="primary" :loading="busy" @click="retryPending"
          >重试原请求</el-button
        ><el-button
          v-if="pending?.kind === 'generate'"
          :disabled="busy || exportLoading"
          @click="refreshExport(pending.id)"
          >查询导出结果</el-button
        ></template
      ></el-alert
    >
    <el-tabs v-model="feedbackTab" class="feedback-tabs">
      <el-tab-pane v-if="canQuery" label="误报来源记录" name="records">
        <ContentWrap class="inspection-card" :body-style="{}">
          <div class="inspection-card__header"
            ><h3>误报来源记录</h3
            ><span v-if="feedback" class="help-text">共 {{ feedback.page.total }} 条</span></div
          >
          <el-table
            v-loading="loading"
            :data="feedback?.page.list || []"
            :row-key="(row: FeedbackRow) => String(row.annotationId)"
            :empty-text="
              queryError ? '查询失败，请重新查询' : loading ? '正在查询' : '本次查询暂无误报记录'
            "
          >
            <el-table-column v-if="feedback?.details" type="expand" width="48"
              ><template #default="{ row }"
                ><div class="details-panel"
                  ><el-descriptions :column="smallScreen ? 1 : 2" border
                    ><el-descriptions-item label="设备 SN">{{
                      row.deviceSerial || '未提供'
                    }}</el-descriptions-item
                    ><el-descriptions-item label="设备型号">{{
                      row.productModel || '未提供'
                    }}</el-descriptions-item></el-descriptions
                  ><h4 class="inspection-section-title">冻结规则命令</h4
                  ><pre class="output-text">{{ row.commands || '未提供命令内容' }}</pre
                  ><h4 class="inspection-section-title">检出输出摘要</h4
                  ><pre class="output-text">{{ row.outputSummary || '未提供输出摘要' }}</pre>
                </div></template
              ></el-table-column
            >
            <el-table-column prop="detectionId" label="检测 ID" min-width="130" /><el-table-column
              prop="inspectionItem"
              label="检测项目"
              min-width="170"
              show-overflow-tooltip
            />
            <el-table-column label="规则修订" min-width="190"
              ><template #default="{ row }"
                >R{{ row.revisionNo }} · {{ row.ruleRevisionId }}</template
              ></el-table-column
            >
            <el-table-column prop="categoryName" label="分类" min-width="120" /><el-table-column
              prop="severityName"
              label="严重级别"
              width="110"
            />
            <el-table-column label="来源" min-width="220"
              ><template #default="{ row }"
                >任务：{{ row.taskCode
                }}<p class="help-text"
                  >标注 {{ row.annotationId }} · 结果 {{ row.resultId }}</p
                ></template
              ></el-table-column
            >
            <el-table-column prop="reason" label="误报原因" min-width="240" show-overflow-tooltip />
            <el-table-column label="检出时间" min-width="170"
              ><template #default="{ row }">{{
                formatDate(row.detectedAt)
              }}</template></el-table-column
            >
            <el-table-column label="记录范围" width="130"
              ><template #default="{ row }"
                ><el-tag :type="row.activeFlag ? 'primary' : 'info'">{{
                  row.activeFlag ? '当前误报' : '历史或已取消'
                }}</el-tag></template
              ></el-table-column
            >
            <el-table-column label="确认状态" min-width="150"
              ><template #default="{ row }"
                >{{ confirmationLabel(row.validFlag)
                }}<p v-if="row.confirmationReason" class="help-text">{{
                  row.confirmationReason
                }}</p></template
              ></el-table-column
            >
            <el-table-column
              v-if="canConfirm || canOptimize"
              label="操作"
              min-width="200"
              :fixed="smallScreen ? false : 'right'"
              ><template #default="{ row }"
                ><el-button
                  v-if="canConfirm"
                  link
                  type="primary"
                  :disabled="locked || !feedback"
                  @click="openConfirm(row)"
                  >确认误报</el-button
                ><el-button
                  v-if="canOptimize"
                  link
                  type="primary"
                  :disabled="locked || !feedback"
                  @click="openDraft(row)"
                  >创建优化草稿</el-button
                ></template
              ></el-table-column
            >
          </el-table>
          <Pagination
            v-model:page="query.pageNo"
            v-model:limit="query.pageSize"
            :total="feedback?.page.total || 0"
            @pagination="loadFeedback"
          />
        </ContentWrap>
      </el-tab-pane>
      <el-tab-pane v-if="feedback?.administrator" label="规则效果" name="statistics">
        <ContentWrap class="inspection-card" :body-style="{}">
          <div class="inspection-card__header"><h3>按规则修订统计</h3></div>
          <p class="help-text"
            >比率为确认有效误报数除以异常检出样本数。统计使用规则、分类、级别、型号和检出时间条件；记录范围与确认状态仅筛选“误报来源记录”页签。</p
          >
          <el-table
            :data="feedback.statistics"
            :row-key="(row: FeedbackStatistic) => String(row.ruleRevisionId)"
            empty-text="本次查询暂无统计样本"
          >
            <el-table-column prop="detectionId" label="检测 ID" min-width="140" /><el-table-column
              prop="inspectionItem"
              label="检测项目"
              min-width="180"
              show-overflow-tooltip
            />
            <el-table-column label="规则修订" min-width="190"
              ><template #default="{ row }"
                >R{{ row.revisionNo }} · {{ row.ruleRevisionId }}</template
              ></el-table-column
            >
            <el-table-column
              prop="detectionCount"
              label="异常检出样本数"
              min-width="140"
            /><el-table-column
              prop="confirmedFalsePositiveCount"
              label="确认有效误报数"
              min-width="140"
            />
            <el-table-column label="有效误报比率" min-width="130"
              ><template #default="{ row }">{{
                rateLabel(row.falsePositiveRate)
              }}</template></el-table-column
            >
          </el-table>
        </ContentWrap>
      </el-tab-pane>
      <el-tab-pane v-if="canExport" label="本人导出记录" name="exports">
        <ContentWrap
          class="inspection-card exports-panel"
          :body-style="{}"
          aria-labelledby="feedback-export-title"
        >
          <div class="inspection-card__header"
            ><h3 id="feedback-export-title">本人导出记录</h3
            ><div class="section-actions"
              ><el-button :disabled="locked || !feedback" @click="startExport('XLSX')"
                >导出 Excel</el-button
              ><el-button :disabled="locked || !feedback" @click="startExport('CSV')"
                >导出 CSV</el-button
              ><el-button :loading="exportsLoading" :disabled="busy" @click="loadExports"
                >刷新记录</el-button
              ></div
            ></div
          >
          <p class="help-text"
            >导出使用最近成功查询条件建立来源快照，再生成文件。快照已建立不代表文件已生成。</p
          >
          <div v-if="currentExport" class="current-export" role="status"
            ><span
              >本次导出 {{ currentExport.id }}：{{ exportStatus(currentExport.statusCode) }} ·
              {{ currentExport.resultCount }} 条 · 截止
              {{ formatDate(currentExport.dataAsOf) }}</span
            ><span v-if="currentExport.failureMessage" class="failure-text">{{
              currentExport.failureMessage
            }}</span
            ><el-button
              v-if="currentExport.statusCode === 'AVAILABLE'"
              link
              type="primary"
              :disabled="busy"
              @click="downloadExport(currentExport)"
              >下载本次文件</el-button
            ><el-button
              v-if="['REQUESTED', 'FAILED'].includes(currentExport.statusCode)"
              link
              type="primary"
              :disabled="locked"
              @click="startGeneration(currentExport)"
              >{{ currentExport.statusCode === 'FAILED' ? '重试生成' : '生成文件' }}</el-button
            ></div
          >
          <el-alert
            v-if="exportsError"
            :title="exportsError"
            type="error"
            :closable="false"
            class="mb-16px"
            ><template #default
              ><el-button
                link
                type="primary"
                :disabled="exportsLoading || busy"
                @click="loadExports"
                >重新加载</el-button
              ></template
            ></el-alert
          >
          <el-alert
            v-if="exportError"
            :title="exportError"
            type="error"
            :closable="false"
            class="mb-16px"
          />
          <el-table
            v-loading="exportsLoading"
            :data="exports"
            :row-key="(row: ExportView) => String(row.id)"
            :empty-text="exportsError ? '导出记录加载失败' : '暂无本人导出记录'"
          >
            <el-table-column prop="id" label="导出记录" min-width="160" /><el-table-column
              prop="formatCode"
              label="格式"
              width="90"
            />
            <el-table-column label="状态" min-width="120"
              ><template #default="{ row }"
                ><el-tag
                  :type="
                    row.statusCode === 'FAILED'
                      ? 'danger'
                      : row.statusCode === 'AVAILABLE'
                        ? 'success'
                        : 'info'
                  "
                  >{{ exportStatus(row.statusCode) }}</el-tag
                ></template
              ></el-table-column
            >
            <el-table-column prop="resultCount" label="快照条数" width="100" /><el-table-column
              label="数据截止时间"
              min-width="170"
              ><template #default="{ row }">{{
                formatDate(row.dataAsOf)
              }}</template></el-table-column
            >
            <el-table-column label="生成时间" min-width="170"
              ><template #default="{ row }">{{
                row.generatedAt ? formatDate(row.generatedAt) : '未生成'
              }}</template></el-table-column
            >
            <el-table-column
              prop="failureMessage"
              label="失败说明"
              min-width="220"
              show-overflow-tooltip
            />
            <el-table-column label="操作" min-width="230" :fixed="smallScreen ? false : 'right'"
              ><template #default="{ row }"
                ><el-button link :disabled="busy || exportLoading" @click="refreshExport(row.id)"
                  >查询状态</el-button
                ><el-button
                  v-if="['REQUESTED', 'FAILED'].includes(row.statusCode)"
                  link
                  type="primary"
                  :disabled="locked"
                  @click="prepareGeneration(row.id)"
                  >{{ row.statusCode === 'FAILED' ? '重试生成' : '生成文件' }}</el-button
                ><el-button
                  v-if="row.statusCode === 'AVAILABLE'"
                  link
                  type="primary"
                  :disabled="busy"
                  @click="downloadExport(row)"
                  >下载</el-button
                ></template
              ></el-table-column
            >
          </el-table>
          <Pagination
            v-model:page="exportQuery.pageNo"
            v-model:limit="exportQuery.pageSize"
            :total="exportTotal"
            @pagination="loadExports"
          />
        </ContentWrap>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      class="inspection-page feedback-dialog"
      v-model="editorVisible"
      :title="editorMode === 'confirm' ? '确认误报有效性' : '创建规则优化草稿'"
      width="min(600px, 94vw)"
      :close-on-click-modal="false"
      :close-on-press-escape="!locked"
      :show-close="!locked"
      :before-close="closeEditor"
    >
      <el-alert
        v-if="operationError"
        :title="operationError"
        :type="pending ? 'warning' : 'error'"
        :closable="false"
        show-icon
        class="mb-16px"
      />
      <p v-if="selectedRow" class="help-text"
        >{{ selectedRow.detectionId }} · R{{ selectedRow.revisionNo }} · 来源误报
        {{ selectedRow.annotationId }}</p
      >
      <el-form
        ref="editorForm"
        :model="editor"
        :rules="editorRules"
        label-position="top"
        :disabled="locked"
        @submit.prevent="submitEditor"
      >
        <el-form-item v-if="editorMode === 'confirm'" label="确认结论" prop="valid"
          ><el-radio-group v-model="editor.valid"
            ><el-radio :value="true">有效误报</el-radio
            ><el-radio :value="false">非有效误报</el-radio></el-radio-group
          ></el-form-item
        >
        <el-form-item :label="editorMode === 'confirm' ? '确认依据' : '优化原因'" prop="reason"
          ><el-input
            v-model="editor.reason"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
        /></el-form-item>
      </el-form>
      <p v-if="editorMode === 'draft'" class="help-text"
        >将从该误报对应的冻结规则修订复制新草稿并登记来源；发布规则和历史结果保持原件。</p
      >
      <template #footer
        ><el-button :disabled="locked" @click="editorVisible = false">取消</el-button
        ><el-button
          v-if="pending && ['confirm', 'draft'].includes(pending.kind)"
          type="primary"
          :loading="busy"
          @click="retryPending"
          >重试原请求</el-button
        ><el-button
          v-else
          type="primary"
          :disabled="locked"
          :loading="editorValidating"
          @click="submitEditor"
          >{{ editorMode === 'confirm' ? '保存确认' : '创建草稿' }}</el-button
        ></template
      >
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useMediaQuery } from '@vueuse/core'
import type { FormInstance, FormRules } from 'element-plus'
import * as Api from '@/api/pms/inspection/feedback'
import type {
  FeedbackFilter,
  FeedbackPage,
  FeedbackRow,
  FeedbackStatistic,
  ExportView
} from '@/api/pms/inspection/feedback'
import type { WireLong } from '@/api/pms/inspection/tasks'
import { getRuleRevision } from '@/api/pms/inspection/rules'
import * as FileVersionApi from '@/api/pms/platform/file/version-reference'
import { generateUUID } from '@/utils'
import { checkPermi } from '@/utils/permission'
import { getStrDictOptions } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { inspectionFailure } from '../shared/interaction'
import '../shared/inspection.css'

defineOptions({ name: 'InspectionFeedbackPanel' })
const emit = defineEmits<{ openRuleDraft: [revisionId: WireLong] }>()
const message = useMessage()
const feedbackTab = ref('records')
const smallScreen = useMediaQuery('(max-width: 767px)')
const canQuery = computed(() => checkPermi(['pms:inspection-feedback:query']))
const hasDetailsPermission = computed(() => checkPermi(['pms:inspection-feedback:details']))
const analyzePermission = computed(() => checkPermi(['pms:inspection-feedback:analyze']))
const canConfirm = computed(
  () =>
    analyzePermission.value &&
    feedback.value?.administrator === true &&
    checkPermi(['pms:inspection-feedback:confirm'])
)
const canOptimize = computed(
  () =>
    analyzePermission.value &&
    feedback.value?.administrator === true &&
    checkPermi(['pms:inspection-rule:manage']) &&
    checkPermi(['pms:inspection-rule:query'])
)
const canExport = computed(
  () => analyzePermission.value && checkPermi(['pms:inspection-feedback:export'])
)
const categories = computed(() => getStrDictOptions('pms_inspection_rule_category'))
const severities = computed(() => getStrDictOptions('pms_inspection_rule_severity'))
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  ruleRevisionId: '',
  detectionId: '',
  categoryCode: '',
  severityCode: '',
  productModel: '',
  currentOnly: false,
  confirmation: '' as '' | NonNullable<FeedbackFilter['confirmation']>
})
const timeRange = ref<[string, string] | null>(null)
const feedback = shallowRef<FeedbackPage>()
const appliedFilter = shallowRef<FeedbackFilter>()
const lastSuccessAt = ref<number>()
const loading = ref(false)
const queryError = ref('')
let querySequence = 0
let alive = true
const buildFilter = (): FeedbackFilter => {
  const ruleRevisionId = query.ruleRevisionId.trim()
  if (ruleRevisionId && !/^[1-9]\d*$/.test(ruleRevisionId))
    throw new Error('规则修订 ID 须填写完整正整数编号。')
  return {
    pageNo: query.pageNo,
    pageSize: query.pageSize,
    ruleRevisionId: ruleRevisionId || undefined,
    detectionId: query.detectionId.trim() || undefined,
    categoryCode: query.categoryCode || undefined,
    severityCode: query.severityCode || undefined,
    productModel: hasDetailsPermission.value ? query.productModel.trim() || undefined : undefined,
    currentOnly: query.currentOnly,
    confirmation: query.confirmation || undefined,
    fromAt: timeRange.value?.[0],
    toAt: timeRange.value?.[1]
  }
}
const loadFeedback = async () => {
  if (!canQuery.value || !alive) return
  const current = ++querySequence
  feedback.value = undefined
  loading.value = true
  queryError.value = ''
  try {
    const filter = buildFilter()
    const result = await Api.getFeedback(filter)
    if (!alive || current !== querySequence) return
    if (
      !result ||
      !Array.isArray(result.page?.list) ||
      !Array.isArray(result.statistics) ||
      result.dataAsOf == null
    )
      throw new Error('误报查询返回内容不完整。')
    feedback.value = result
    appliedFilter.value = filter
    lastSuccessAt.value = result.dataAsOf
  } catch (error) {
    if (!alive || current !== querySequence) return
    feedback.value = undefined
    appliedFilter.value = undefined
    queryError.value =
      error instanceof Error && error.message.startsWith('规则修订 ID')
        ? error.message
        : '误报查询失败，本次记录与统计已清空，请重新查询。'
  } finally {
    if (alive && current === querySequence) loading.value = false
  }
}
const search = () => {
  query.pageNo = 1
  void loadFeedback()
}
const resetQuery = () => {
  Object.assign(query, {
    pageNo: 1,
    ruleRevisionId: '',
    detectionId: '',
    categoryCode: '',
    severityCode: '',
    productModel: '',
    currentOnly: false,
    confirmation: ''
  })
  timeRange.value = null
  void loadFeedback()
}
const rateLabel = (rate?: number | string) => {
  if (rate == null) return '无样本'
  const value = typeof rate === 'number' ? rate : parseFloat(rate)
  return Number.isFinite(value) ? `${(value * 100).toFixed(2)}%` : '未提供'
}
const confirmationLabel = (valid?: boolean) =>
  valid == null ? '待确认' : valid ? '确认有效误报' : '非有效误报'

type Pending =
  | {
      kind: 'confirm'
      id: WireLong
      version: WireLong
      key: string
      data: { valid: boolean; reason: string }
    }
  | { kind: 'draft'; id: WireLong; version: WireLong; key: string; data: { reason: string } }
  | { kind: 'request'; format: 'CSV' | 'XLSX'; filter: FeedbackFilter; key: string }
  | { kind: 'generate'; id: WireLong; version: WireLong; key: string }
const pending = shallowRef<Pending>()
const busy = ref(false)
const operationError = ref('')
const editorValidating = ref(false)
const sourceLoading = ref(false)
const locked = computed(
  () =>
    busy.value ||
    !!pending.value ||
    editorValidating.value ||
    sourceLoading.value ||
    exportLoading.value
)
const editorVisible = ref(false)
const editorMode = ref<'confirm' | 'draft'>('confirm')
const selectedRow = shallowRef<FeedbackRow>()
const sourceVersion = ref<WireLong>()
const editor = reactive({ valid: undefined as boolean | undefined, reason: '' })
const editorForm = ref<FormInstance>()
const editorRules: FormRules = {
  valid: [{ required: true, message: '请选择确认结论', trigger: 'change' }],
  reason: [
    {
      required: true,
      whitespace: true,
      max: 2000,
      message: '请填写理由（不超过2000字）',
      trigger: 'blur'
    }
  ]
}
let editorSequence = 0
const openConfirm = (row: FeedbackRow) => {
  if (locked.value || !canConfirm.value) return
  editorSequence++
  selectedRow.value = { ...row }
  editorMode.value = 'confirm'
  Object.assign(editor, { valid: row.validFlag ?? undefined, reason: '' })
  operationError.value = ''
  editorVisible.value = true
  editorForm.value?.clearValidate()
}
const openDraft = async (row: FeedbackRow) => {
  if (locked.value || !canOptimize.value) return
  const current = ++editorSequence
  sourceLoading.value = true
  operationError.value = ''
  try {
    const source = await getRuleRevision(row.ruleRevisionId)
    if (!alive || current !== editorSequence) return
    if (String(source.revisionId) !== String(row.ruleRevisionId)) throw new Error('来源规则不匹配')
    selectedRow.value = { ...row }
    sourceVersion.value = source.version
    editorMode.value = 'draft'
    Object.assign(editor, { valid: undefined, reason: '' })
    editorVisible.value = true
    editorForm.value?.clearValidate()
  } catch {
    if (alive && current === editorSequence)
      operationError.value = '来源规则修订获取失败，尚未创建优化草稿，请重新打开。'
  } finally {
    if (alive && current === editorSequence) sourceLoading.value = false
  }
}
const closeEditor = (done: () => void) => {
  if (!locked.value) done()
}
const submitEditor = async () => {
  if (
    locked.value ||
    !selectedRow.value ||
    (editorMode.value === 'confirm' ? !canConfirm.value : !canOptimize.value)
  )
    return
  editorValidating.value = true
  const current = editorSequence
  try {
    if (
      !(await editorForm.value?.validate().catch(() => false)) ||
      !alive ||
      current !== editorSequence ||
      !selectedRow.value
    )
      return
    const row = selectedRow.value
    if (editorMode.value === 'confirm' && editor.valid != null)
      pending.value = {
        kind: 'confirm',
        id: row.annotationId,
        version: row.confirmationVersion ?? 0,
        key: generateUUID(),
        data: { valid: editor.valid, reason: editor.reason.trim() }
      }
    if (editorMode.value === 'draft' && sourceVersion.value != null)
      pending.value = {
        kind: 'draft',
        id: row.annotationId,
        version: sourceVersion.value,
        key: generateUUID(),
        data: { reason: editor.reason.trim() }
      }
  } finally {
    editorValidating.value = false
  }
  if (pending.value) await retryPending()
}

const exports = ref<ExportView[]>([])
const exportQuery = reactive({ pageNo: 1, pageSize: 10 })
const exportTotal = ref(0)
const exportsLoading = ref(false)
const exportsError = ref('')
const exportLoading = ref(false)
const exportError = ref('')
const currentExport = shallowRef<ExportView>()
let exportSequence = 0
let exportReadSequence = 0
const exportStatuses: Record<string, string> = {
  REQUESTED: '快照已建立，待生成',
  GENERATING: '生成中',
  AVAILABLE: '文件可下载',
  FAILED: '生成失败'
}
const exportStatus = (code: string) => exportStatuses[code] || code
const loadExports = async () => {
  if (!canExport.value || !alive) return
  const current = ++exportSequence
  exportsLoading.value = true
  exportsError.value = ''
  try {
    const page = await Api.getExports({ ...exportQuery })
    if (!alive || current !== exportSequence) return
    if (!page || !Array.isArray(page.list)) throw new Error('导出记录不完整')
    exports.value = page.list
    exportTotal.value = page.total
  } catch {
    if (!alive || current !== exportSequence) return
    exports.value = []
    exportTotal.value = 0
    exportsError.value = '本人导出记录加载失败，请重新加载。'
  } finally {
    if (alive && current === exportSequence) exportsLoading.value = false
  }
}
const rememberExport = (result: ExportView) => {
  currentExport.value = result
  exports.value = exports.value.map((row) => (String(row.id) === String(result.id) ? result : row))
}
const startExport = async (format: 'CSV' | 'XLSX') => {
  if (locked.value || !canExport.value || !feedback.value || !appliedFilter.value) return
  pending.value = {
    kind: 'request',
    format,
    filter: { ...appliedFilter.value },
    key: generateUUID()
  }
  currentExport.value = undefined
  await retryPending()
}
const startGeneration = async (record: ExportView) => {
  if (locked.value || !canExport.value || !['REQUESTED', 'FAILED'].includes(record.statusCode))
    return
  rememberExport(record)
  pending.value = {
    kind: 'generate',
    id: record.id,
    version: record.version,
    key: generateUUID()
  }
  await retryPending()
}
const readExport = async (id: WireLong): Promise<ExportView | undefined> => {
  if (!canExport.value || !alive) return
  const current = ++exportReadSequence
  exportLoading.value = true
  exportError.value = ''
  try {
    const result = await Api.getExport(id)
    if (!alive || current !== exportReadSequence) return
    if (!result || String(result.id) !== String(id)) throw new Error('导出记录不匹配')
    rememberExport(result)
    return result
  } catch {
    if (alive && current === exportReadSequence)
      exportError.value = '导出状态获取失败，尚未确认最新结果，请重新查询。'
  } finally {
    if (alive && current === exportReadSequence) exportLoading.value = false
  }
}
const refreshExport = async (id: WireLong) => {
  if (busy.value || exportLoading.value) return
  const result = await readExport(id)
  if (!result) return
  if (
    pending.value?.kind === 'generate' &&
    String(pending.value.id) === String(id) &&
    (result.statusCode === 'AVAILABLE' ||
      (result.statusCode === 'FAILED' && String(result.version) !== String(pending.value.version)))
  ) {
    pending.value = undefined
    operationError.value =
      result.statusCode === 'FAILED'
        ? '已确认本次生成失败，来源快照保留，可按最新版本重试生成。'
        : ''
  }
}
const prepareGeneration = async (id: WireLong) => {
  if (locked.value || exportLoading.value) return
  const result = await readExport(id)
  if (result) await startGeneration(result)
}
const retryPending = async () => {
  if (!pending.value || busy.value || !alive) return
  busy.value = true
  operationError.value = ''
  try {
    while (pending.value && alive) {
      const intent = pending.value
      if (intent.kind === 'confirm') {
        if (!analyzePermission.value || !checkPermi(['pms:inspection-feedback:confirm'])) {
          operationError.value = '确认权限已变化，原请求保留，请恢复对应权限后再重试。'
          break
        }
        await Api.confirmFeedback(intent.id, intent.data, intent.version, intent.key)
        if (!alive) return
        pending.value = undefined
        editorVisible.value = false
        await loadFeedback()
        message.success('误报确认已保存')
      } else if (intent.kind === 'draft') {
        if (
          !analyzePermission.value ||
          !checkPermi(['pms:inspection-rule:manage']) ||
          !checkPermi(['pms:inspection-rule:query'])
        ) {
          operationError.value = '规则草稿权限已变化，原请求保留，请恢复对应权限后再重试。'
          break
        }
        const draft = await Api.createRuleDraft(intent.id, intent.data, intent.version, intent.key)
        if (!alive) return
        pending.value = undefined
        editorVisible.value = false
        message.success('已创建来源可追溯的优化草稿')
        emit('openRuleDraft', draft.revisionId)
      } else if (intent.kind === 'request') {
        if (!canExport.value) {
          operationError.value = '导出权限已变化，原请求保留，请恢复对应权限后再重试。'
          break
        }
        const result = await Api.requestExport(intent.format, intent.filter, intent.key)
        if (!alive) return
        rememberExport(result)
        pending.value =
          result.statusCode === 'REQUESTED'
            ? { kind: 'generate', id: result.id, version: result.version, key: generateUUID() }
            : undefined
        if (!pending.value) await loadExports()
      } else {
        if (!canExport.value) {
          operationError.value = '导出权限已变化，原请求保留，请恢复对应权限后再重试。'
          break
        }
        const result = await Api.generateExport(intent.id, intent.version, intent.key)
        if (!alive) return
        rememberExport(result)
        if (!['AVAILABLE', 'FAILED'].includes(result.statusCode)) {
          operationError.value = '尚未确认文件生成结果，请重试原请求或查询导出状态。'
          break
        }
        pending.value = undefined
        if (result.statusCode === 'FAILED')
          operationError.value = result.failureMessage || '文件生成失败，来源快照保留，可重试生成。'
        await loadExports()
      }
    }
  } catch (error) {
    if (!alive) return
    const failure = inspectionFailure(error)
    const response = (
      error as { response?: { status?: number; data?: { data?: { reasonCode?: string } } } }
    )?.response
    const conflict = response?.status === 409 && response.data?.data?.reasonCode !== 'IN_PROGRESS'
    operationError.value = failure.message
    if (!failure.unknown || conflict) {
      const intent = pending.value
      pending.value = undefined
      if (conflict) {
        editorVisible.value = false
        operationError.value += ' 原请求已结束，请根据刷新后的资料重新办理。'
        if (intent?.kind === 'confirm' || intent?.kind === 'draft') await loadFeedback()
        if (intent?.kind === 'generate') await readExport(intent.id)
        if (intent?.kind === 'request' || intent?.kind === 'generate') await loadExports()
      }
    }
  } finally {
    if (alive) busy.value = false
  }
}
const downloadExport = async (record: ExportView) => {
  if (!canExport.value || busy.value || record.statusCode !== 'AVAILABLE') return
  if (record.artifactId == null || record.fileVersionNo == null || !record.referenceKey) {
    message.error('导出文件引用不完整，请查询最新状态')
    return
  }
  const target = window.open('about:blank', '_blank')
  if (!target) {
    message.warning('浏览器阻止了下载窗口，请允许弹窗后重试')
    return
  }
  target.opener = null
  try {
    const ticket = await FileVersionApi.createAccessTicket(
      record.artifactId,
      record.fileVersionNo,
      'DOWNLOAD',
      {
        ownerContext: 'SRV',
        objectType: 'INSPECTION_FEEDBACK_EXPORT',
        objectId: String(record.id),
        purposeCode: 'EXPORT',
        referenceKey: record.referenceKey
      }
    )
    if (!alive) {
      target.close()
      return
    }
    target.location.replace(ticket.shortLivedUrl)
  } catch {
    target.close()
    if (alive) message.error('导出文件暂不可用或无权下载，请查询最新状态后重试')
  }
}
const mayLeave = () => {
  if (busy.value || pending.value) {
    message.warning('请先确认当前误报操作结果，再离开页面。')
    return false
  }
  return true
}
onBeforeRouteLeave(mayLeave)
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (busy.value || pending.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
onMounted(() => {
  void loadFeedback()
  void loadExports()
  window.addEventListener('beforeunload', beforeUnload)
})
onBeforeUnmount(() => {
  alive = false
  querySequence++
  exportSequence++
  exportReadSequence++
  editorSequence++
  window.removeEventListener('beforeunload', beforeUnload)
})
</script>

<style scoped>
.feedback-panel {
  min-width: 0;
}
.feedback-tabs :deep(> .el-tabs__header) {
  padding: 0 20px;
  margin: 0 0 16px;
  background: #fff;
  border-radius: 8px;
}
.feedback-tabs :deep(> .el-tabs__header .el-tabs__nav-wrap::after) {
  height: 1px;
  background: #f0f2f5;
}
.feedback-tabs :deep(> .el-tabs__header .el-tabs__item) {
  height: 40px;
  font-size: 13px;
}
.feedback-dialog :deep(.el-dialog__title) {
  color: #1a2332;
  font-size: 15px;
  font-weight: 600;
}
.help-text {
  margin: 8px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0 16px;
}
.filter-grid :deep(.el-select),
.filter-grid :deep(.el-date-editor) {
  width: 100%;
}
.filter-grid :deep(.el-form-item) {
  margin-bottom: 14px;
}
.range-field {
  grid-column: span 2;
}
.filter-actions {
  grid-column: 1 / -1;
  margin-bottom: 0 !important;
}
.filter-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
.details-panel {
  padding: 16px;
}
.details-panel h4 {
  margin: 16px 0 8px;
  font-size: 13px;
}
.output-text {
  margin: 0;
  padding: 12px;
  background: #fafbfc;
  border: 1px solid #f0f2f5;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.operation-alert {
  margin-bottom: 16px;
}
.section-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.section-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.current-export {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 12px;
  margin-bottom: 16px;
  border: 1px solid #f0f2f5;
  border-radius: 4px;
  background: #fafbfc;
  font-size: 13px;
}
.failure-text {
  color: var(--el-color-danger);
}
@media (max-width: 1023px) {
  .filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 767px) {
  .filter-grid {
    grid-template-columns: 1fr;
  }
  .range-field {
    grid-column: auto;
  }
}
</style>
