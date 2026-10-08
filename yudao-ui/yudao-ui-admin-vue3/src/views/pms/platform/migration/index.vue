<template>
  <ContentWrap>
    <div class="mb-10px flex items-center justify-between">
      <h3 class="m-0">迁移工具台</h3>
      <el-button type="primary" @click="openCreate">创建批次</el-button>
    </div>
    <el-alert
      type="info"
      :closable="false"
      title="迁移预演只读：基于既有迁移证据（来源记录 + 外部键映射）分类，不做按名称猜测合并。"
      class="mb-10px"
    />
    <el-form inline>
      <el-form-item label="来源系统">
        <el-input v-model="query.sourceSystem" style="width: 140px" clearable @change="load" />
      </el-form-item>
      <el-form-item label="用途">
        <el-input v-model="query.purposeCode" style="width: 160px" clearable @change="load" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" style="width: 150px" clearable @change="load">
          <el-option v-for="s in batchStatuses" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load">查询</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="batches">
      <el-table-column prop="id" label="批次" width="90" />
      <el-table-column prop="ownerContextCode" label="归属上下文" min-width="110" />
      <el-table-column prop="purposeCode" label="用途" min-width="130" />
      <el-table-column prop="releaseId" label="发布标识" min-width="130" />
      <el-table-column label="来源" min-width="140">
        <template #default="{ row }">{{ row.sourceSystem }}/{{ row.sourceTable }}</template>
      </el-table-column>
      <el-table-column prop="sourceCount" label="来源数" width="80" />
      <el-table-column label="对账（映射/问题/保留）" width="160">
        <template #default="{ row }">{{ row.mappedCount }} / {{ row.issueCount }} / {{ row.retainedCount }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="70" />
      <el-table-column label="操作" width="290" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openSourceRecords(row)">来源记录</el-button>
          <el-button
            v-if="row.status === 'IMPORTING'"
            type="primary"
            link
            @click="openAppendRecords(row)"
          >追加来源</el-button>
          <el-button
            v-if="row.status === 'IMPORTING'"
            type="primary"
            link
            @click="openStagedReady(row)"
          >暂存就绪</el-button>
          <el-button
            v-if="row.status === 'STAGED_READY'"
            type="warning"
            link
            @click="claim(row)"
          >认领</el-button>
          <el-button
            v-if="row.status === 'RECONCILING'"
            type="primary"
            link
            @click="openMapping(row)"
          >登记映射</el-button>
          <el-button
            v-if="row.status === 'RECONCILING'"
            type="primary"
            link
            @click="openIssue(row)"
          >登记问题</el-button>
          <el-button
            v-if="row.status === 'COMPLETED'"
            type="warning"
            link
            @click="openCloseIssue(row)"
          >问题结案</el-button>
          <el-button
            v-if="row.status === 'RECONCILING'"
            type="success"
            link
            @click="openComplete(row)"
          >完成对账</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="query.pageNo"
      :page-size="query.pageSize"
      :total="total"
      layout="total, prev, pager, next"
      class="mt-10px"
      @current-change="load"
    />
  </ContentWrap>

  <ContentWrap>
    <h4 class="mt-0 mb-10px">迁移预演（只读，不写任何证据表）</h4>
    <el-form inline>
      <el-form-item label="来源系统">
        <el-input v-model="previewForm.sourceSystem" style="width: 140px" />
      </el-form-item>
      <el-form-item label="来源表">
        <el-input v-model="previewForm.sourceTable" style="width: 180px" />
      </el-form-item>
      <el-form-item label="记录JSON">
        <el-input
          v-model="previewForm.recordsJson"
          type="textarea"
          :rows="3"
          style="width: 420px"
          placeholder='[{"sourcePk":"CUST-001","sourceChecksum":"<sha256可选>"}]'
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="runPreview">预演</el-button>
      </el-form-item>
    </el-form>
    <template v-if="previewResult">
      <el-descriptions :column="6" border size="small" class="mb-10px">
        <el-descriptions-item label="无既有记录">{{ previewResult.noExistingSourceCount }}</el-descriptions-item>
        <el-descriptions-item label="校验和冲突">{{ previewResult.checksumConflictCount }}</el-descriptions-item>
        <el-descriptions-item label="未映射">{{ previewResult.unmappedCount }}</el-descriptions-item>
        <el-descriptions-item label="保留旧系统">{{ previewResult.retainedCount }}</el-descriptions-item>
        <el-descriptions-item label="已映射">{{ previewResult.mappedCount }}</el-descriptions-item>
        <el-descriptions-item label="歧义">{{ previewResult.ambiguousCount }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="previewResult.items" size="small">
        <el-table-column prop="sourcePk" label="来源主键" min-width="150" />
        <el-table-column label="分类" width="150">
          <template #default="{ row }">
            <el-tag :type="classificationTagType(row.classification)">{{ row.classification }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="latestSourceRecordId" label="既有来源记录" width="120" />
        <el-table-column prop="latestBatchId" label="所在批次" width="100" />
        <el-table-column label="既有映射目标" min-width="260">
          <template #default="{ row }">
            <template v-if="(row.targets || []).length">
              <div v-for="(t, i) in row.targets" :key="i" class="text-12px">
                [{{ t.resultType }}] {{ t.targetRole }}#{{ t.targetSequence }} →
                {{ t.targetContext }}/{{ t.targetObjectType }}/{{ t.targetTable }}#{{ t.targetId }}
              </div>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </ContentWrap>

  <!-- 创建批次 -->
  <el-dialog v-model="createVisible" title="创建迁移批次" width="640px">
    <el-form :model="createForm" label-width="130px">
      <el-form-item label="归属上下文" required>
        <el-input v-model="createForm.ownerContextCode" style="width: 220px" />
      </el-form-item>
      <el-form-item label="用途" required>
        <el-input v-model="createForm.purposeCode" style="width: 320px" />
      </el-form-item>
      <el-form-item label="发布标识" required>
        <el-input v-model="createForm.releaseId" style="width: 320px" />
      </el-form-item>
      <el-form-item label="来源系统" required>
        <el-input v-model="createForm.sourceSystem" style="width: 220px" />
      </el-form-item>
      <el-form-item label="来源表" required>
        <el-input v-model="createForm.sourceTable" style="width: 220px" />
      </el-form-item>
      <el-form-item label="清单模式版本" required>
        <el-input v-model="createForm.manifestSchemaVersion" style="width: 220px" />
      </el-form-item>
      <el-form-item label="期望行数" required>
        <el-input-number v-model="createForm.expectedRowCount" :min="0" />
      </el-form-item>
      <el-form-item label="内容SHA256" required>
        <el-input v-model="createForm.contentSha256" style="width: 420px" placeholder="64位小写十六进制" />
      </el-form-item>
      <el-form-item label="导出时间" required>
        <el-date-picker v-model="createForm.exportedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
    </template>
  </el-dialog>

  <!-- 追加来源记录 -->
  <el-dialog v-model="appendVisible" title="追加来源记录" width="680px">
    <el-alert
      type="info"
      :closable="false"
      title="同一批次的记录必须属于批次的来源系统/来源表，sourcePk 不得重复；校验和为待导入内容的小写 sha256。"
      class="mb-10px"
    />
    <el-input
      v-model="appendForm.recordsJson"
      type="textarea"
      :rows="8"
      placeholder='[{"sourcePk":"CUST-001","sourceBusinessKey":"客户一","sourcePayload":{"name":"客户一"},"sourceChecksum":"<64位sha256>","extractedAt":"2026-09-25T10:00:00"}]'
    />
    <template #footer>
      <el-button @click="appendVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitAppendRecords">追加</el-button>
    </template>
  </el-dialog>

  <!-- 来源记录 -->
  <el-dialog v-model="recordsVisible" :title="`来源记录 - 批次 ${activeBatch?.id ?? ''}`" width="900px">
    <el-table :data="sourceRecords" size="small" max-height="420">
      <el-table-column prop="sourceRecordId" label="ID" width="190" />
      <el-table-column prop="sourcePk" label="来源主键" min-width="140" />
      <el-table-column prop="sourceBusinessKey" label="业务键" min-width="120" />
      <el-table-column prop="sourceChecksum" label="校验和" min-width="140" show-overflow-tooltip />
      <el-table-column prop="resultType" label="结论" width="100" />
      <el-table-column prop="extractedAt" label="提取时间" width="160" />
    </el-table>
    <div class="mt-10px">
      <el-button
        v-if="nextCursor"
        size="small"
        @click="loadSourceRecords(activeBatch!.id, nextCursor)"
      >加载更多</el-button>
      <span v-else class="text-12px text-gray-400">已加载全部 {{ sourceRecords.length }} 条</span>
    </div>
  </el-dialog>

  <!-- 暂存就绪 -->
  <el-dialog v-model="stagedReadyVisible" title="暂存就绪 / 失败收尾" width="560px">
    <el-form :model="stagedReadyForm" label-width="150px">
      <el-form-item label="期望批次版本" required>
        <el-input-number v-model="stagedReadyForm.expectedBatchVersion" :min="0" />
      </el-form-item>
      <el-form-item label="决定" required>
        <el-select v-model="stagedReadyForm.decision" style="width: 200px">
          <el-option label="READY（就绪）" value="READY" />
          <el-option label="FAIL_IMPORT（失败收尾）" value="FAIL_IMPORT" />
        </el-select>
      </el-form-item>
      <el-form-item label="清单行数">
        <el-input-number v-model="stagedReadyForm.manifestRowCount" :min="0" />
      </el-form-item>
      <el-form-item label="清单模式版本">
        <el-input v-model="stagedReadyForm.manifestSchemaVersion" style="width: 220px" />
      </el-form-item>
      <el-form-item label="清单内容SHA256">
        <el-input v-model="stagedReadyForm.manifestContentSha256" style="width: 360px" />
      </el-form-item>
      <el-form-item v-if="stagedReadyForm.decision === 'FAIL_IMPORT'" label="失败码" required>
        <el-select v-model="stagedReadyForm.failureCode" style="width: 280px">
          <el-option
            v-for="c in failureCodes"
            :key="c"
            :label="c"
            :value="c"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="stagedReadyVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitStagedReady">提交</el-button>
    </template>
  </el-dialog>

  <!-- 登记映射 -->
  <el-dialog v-model="mappingVisible" title="登记外部键映射" width="640px">
    <el-form label-width="150px">
      <el-form-item label="来源记录ID" required>
        <el-input v-model="mappingForm.sourceRecordId" style="width: 260px" placeholder="来源记录列表中的长ID" />
      </el-form-item>
      <el-form-item label="结论类型" required>
        <el-select v-model="mappingForm.resultType" style="width: 200px">
          <el-option label="MAPPED（映射到目标）" value="MAPPED" />
          <el-option label="RETAINED（保留旧系统）" value="RETAINED" />
        </el-select>
      </el-form-item>
      <el-form-item label="目标JSON" required>
        <el-input
          v-model="mappingForm.targetsJson"
          type="textarea"
          :rows="5"
          style="width: 420px"
          placeholder='[{"targetContext":"PMS","targetObjectType":"CUSTOMER","targetTable":"pms_customer","targetId":1001,"targetRole":"PRIMARY","targetSequence":0}]'
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="mappingVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitMapping">登记</el-button>
    </template>
  </el-dialog>

  <!-- 登记问题 -->
  <el-dialog v-model="issueVisible" title="登记迁移问题" width="640px">
    <el-form label-width="150px">
      <el-form-item label="来源记录ID" required>
        <el-input v-model="issueForm.sourceRecordId" style="width: 260px" placeholder="来源记录列表中的长ID" />
      </el-form-item>
      <el-form-item label="问题键" required>
        <el-input v-model="issueForm.issueKey" style="width: 320px" />
      </el-form-item>
      <el-form-item label="问题类型" required>
        <el-input v-model="issueForm.issueType" style="width: 320px" />
      </el-form-item>
      <el-form-item label="原始业务键">
        <el-input v-model="issueForm.rawBusinessKey" style="width: 320px" />
      </el-form-item>
      <el-form-item label="候选目标ID">
        <el-input
          v-model="issueForm.candidateTargetIdsJson"
          type="textarea"
          :rows="2"
          placeholder="冲突候选目标ID的JSON数组，如 [1,2]；留空表示无候选目标"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="issueVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitIssue">登记</el-button>
    </template>
  </el-dialog>

  <!-- 完成对账 -->
  <el-dialog v-model="completeVisible" title="完成对账" width="560px">
    <el-alert
      type="warning"
      :closable="false"
      title="期望计数必须与批次事实一致：来源数 = 映射 + 问题 + 保留。"
      class="mb-10px"
    />
    <el-form :model="completeForm" label-width="150px">
      <el-form-item label="期望批次版本" required>
        <el-input-number v-model="completeForm.expectedBatchVersion" :min="0" />
      </el-form-item>
      <el-form-item label="期望映射数" required>
        <el-input-number v-model="completeForm.expectedMappedCount" :min="0" />
      </el-form-item>
      <el-form-item label="期望问题数" required>
        <el-input-number v-model="completeForm.expectedIssueCount" :min="0" />
      </el-form-item>
      <el-form-item label="期望保留数" required>
        <el-input-number v-model="completeForm.expectedRetainedCount" :min="0" />
      </el-form-item>
      <el-form-item label="规则版本" required>
        <el-input v-model="completeForm.ruleVersion" style="width: 220px" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="completeVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitComplete">完成</el-button>
    </template>
  </el-dialog>

  <!-- 问题结案 -->
  <el-dialog v-model="closeIssueVisible" title="结案迁移问题" width="640px">
    <el-table :data="issues" size="small" max-height="260" class="mb-10px">
      <el-table-column prop="issueId" label="ID" width="190" />
      <el-table-column prop="issueKey" label="问题键" min-width="150" />
      <el-table-column prop="issueType" label="类型" min-width="120" />
      <el-table-column prop="status" label="状态" width="90" />
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'OPEN'"
            type="primary"
            link
            @click="closeIssueForm.issueId = row.issueId"
          >选择</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-form label-width="150px">
      <el-form-item label="问题ID" required>
        <el-input v-model="closeIssueForm.issueId" style="width: 260px" placeholder="问题列表中的长ID" />
      </el-form-item>
      <el-form-item label="规则版本" required>
        <el-input v-model="closeIssueForm.ruleVersion" style="width: 220px" />
      </el-form-item>
      <el-form-item label="结案结果JSON" required>
        <el-input
          v-model="closeIssueForm.targetResultJson"
          type="textarea"
          :rows="3"
          style="width: 420px"
          placeholder='{"resolution":"MERGED","targetId":1001}'
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="closeIssueVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitCloseIssue">结案</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import * as MigrationApi from '@/api/pms/platform/migration'
import type {
  MigrationBatchVO,
  MigrationSourceRecordVO,
  PreviewResultVO
} from '@/api/pms/platform/migration'

defineOptions({ name: 'PmsMigrationConsole' })

const message = useMessage()

const batchStatuses = ['IMPORTING', 'STAGED_READY', 'RECONCILING', 'COMPLETED', 'FAILED']
const failureCodes = [
  'MANIFEST_STRUCTURE_INVALID',
  'MANIFEST_ROW_COUNT_MISMATCH',
  'MANIFEST_SCHEMA_VERSION_MISMATCH',
  'MANIFEST_CONTENT_SHA256_MISMATCH',
  'SOURCE_PAYLOAD_INVALID',
  'SOURCE_RECORD_CONFLICT'
]

const loading = ref(false)
const submitting = ref(false)
const batches = ref<MigrationBatchVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  sourceSystem: '',
  purposeCode: '',
  status: ''
})

const load = async () => {
  loading.value = true
  try {
    const data = await MigrationApi.getMigrationBatchPage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      sourceSystem: query.sourceSystem || undefined,
      purposeCode: query.purposeCode || undefined,
      status: query.status || undefined
    })
    batches.value = data.list
    total.value = Number(data.total ?? 0)
  } finally {
    loading.value = false
  }
}
onMounted(load)

const statusTagType = (status: string) =>
  status === 'COMPLETED' ? 'success' : status === 'FAILED' ? 'danger' : status === 'RECONCILING' ? 'warning' : 'info'

// ========== 预演 ==========
const previewForm = reactive({ sourceSystem: '', sourceTable: '', recordsJson: '[{"sourcePk":"CUST-001"}]' })
const previewResult = ref<PreviewResultVO>()
const runPreview = async () => {
  try {
    const records = JSON.parse(previewForm.recordsJson)
    previewResult.value = await MigrationApi.migrationPreview({
      sourceSystem: previewForm.sourceSystem,
      sourceTable: previewForm.sourceTable,
      records
    })
  } catch {
    message.error('记录JSON格式不正确')
  }
}
const classificationTagType = (classification: string) =>
  classification === 'MAPPED'
    ? 'success'
    : classification === 'AMBIGUOUS' || classification === 'CHECKSUM_CONFLICT'
      ? 'danger'
      : classification === 'RETAINED'
        ? 'warning'
        : 'info'

// ========== 创建批次 ==========
const createVisible = ref(false)
const createForm = reactive({
  ownerContextCode: 'PMS',
  purposeCode: '',
  releaseId: '',
  sourceSystem: '',
  sourceTable: '',
  manifestSchemaVersion: '',
  expectedRowCount: 0,
  contentSha256: '',
  exportedAt: ''
})
const openCreate = () => {
  createForm.purposeCode = ''
  createForm.releaseId = ''
  createForm.sourceSystem = ''
  createForm.sourceTable = ''
  createForm.manifestSchemaVersion = ''
  createForm.expectedRowCount = 0
  createForm.contentSha256 = ''
  createForm.exportedAt = ''
  createVisible.value = true
}
const submitCreate = async () => {
  submitting.value = true
  try {
    await MigrationApi.createMigrationBatch({
      ...createForm,
      expectedRowCount: Number(createForm.expectedRowCount),
      idempotencyKey: `create-${createForm.releaseId}-${createForm.sourceSystem}-${createForm.sourceTable}-${Date.now()}`,
      correlationId: `console-${Date.now()}`
    })
    message.success('批次已创建')
    createVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

// ========== 来源记录 ==========
const recordsVisible = ref(false)
const activeBatch = ref<MigrationBatchVO>()
const sourceRecords = ref<MigrationSourceRecordVO[]>([])
const nextCursor = ref<number>()
const openSourceRecords = async (row: MigrationBatchVO) => {
  activeBatch.value = row
  sourceRecords.value = []
  nextCursor.value = undefined
  recordsVisible.value = true
  await loadSourceRecords(row.id, undefined)
}
const loadSourceRecords = async (batchId: number, after?: number) => {
  const page = await MigrationApi.getMigrationSourceRecords(batchId, after)
  sourceRecords.value = after ? sourceRecords.value.concat(page.records) : page.records
  nextCursor.value = page.nextAfterSourceRecordId
}

// ========== 追加来源记录 ==========
const appendVisible = ref(false)
const appendForm = reactive({ recordsJson: '[]' })
const openAppendRecords = (row: MigrationBatchVO) => {
  activeBatch.value = row
  appendForm.recordsJson = '[]'
  appendVisible.value = true
}
const submitAppendRecords = async () => {
  if (!activeBatch.value) return
  submitting.value = true
  try {
    const records = JSON.parse(appendForm.recordsJson)
    await MigrationApi.appendMigrationSourceRecords(activeBatch.value.id, {
      records,
      correlationId: `console-${Date.now()}`
    })
    message.success('来源记录已追加')
    appendVisible.value = false
    await load()
  } catch {
    message.error('记录JSON格式不正确')
  } finally {
    submitting.value = false
  }
}

// ========== 暂存就绪 ==========
const stagedReadyVisible = ref(false)
const stagedReadyForm = reactive({
  expectedBatchVersion: 0,
  decision: 'READY',
  manifestRowCount: 0,
  manifestSchemaVersion: '',
  manifestContentSha256: '',
  failureCode: ''
})
const openStagedReady = (row: MigrationBatchVO) => {
  stagedReadyForm.expectedBatchVersion = row.version
  stagedReadyForm.decision = 'READY'
  stagedReadyForm.manifestRowCount = row.expectedRowCount
  stagedReadyForm.manifestSchemaVersion = row.manifestSchemaVersion
  stagedReadyForm.manifestContentSha256 = row.contentSha256
  stagedReadyForm.failureCode = ''
  stagedReadyVisible.value = true
}
const submitStagedReady = async () => {
  if (!activeBatch.value) return
  submitting.value = true
  try {
    await MigrationApi.markMigrationStagedReady(activeBatch.value.id, {
      expectedBatchVersion: stagedReadyForm.expectedBatchVersion,
      decision: stagedReadyForm.decision as 'READY' | 'FAIL_IMPORT',
      manifestRowCount:
        stagedReadyForm.decision === 'READY' ? Number(stagedReadyForm.manifestRowCount) : undefined,
      manifestSchemaVersion:
        stagedReadyForm.decision === 'READY' ? stagedReadyForm.manifestSchemaVersion : undefined,
      manifestContentSha256:
        stagedReadyForm.decision === 'READY' ? stagedReadyForm.manifestContentSha256 : undefined,
      failureCode: stagedReadyForm.decision === 'FAIL_IMPORT' ? stagedReadyForm.failureCode : undefined,
      idempotencyKey: `staged-ready-${activeBatch.value.id}-${stagedReadyForm.expectedBatchVersion}-${Date.now()}`,
      correlationId: `console-${Date.now()}`
    })
    message.success('已提交')
    stagedReadyVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

// ========== 认领 ==========
const claim = async (row: MigrationBatchVO) => {
  const result = await MigrationApi.claimMigrationBatch({
    ownerContextCode: row.ownerContextCode,
    purposeCode: row.purposeCode,
    sourceSystems: [row.sourceSystem],
    sourceTables: [row.sourceTable]
  })
  message.success(result.claimed ? `批次 ${result.batch?.id} 已认领进入对账` : '无可认领批次')
  await load()
}

// ========== 登记映射 / 问题 / 完成对账 / 结案 ==========
const mappingVisible = ref(false)
const mappingForm = reactive({
  sourceRecordId: '',
  resultType: 'MAPPED',
  targetsJson: ''
})
const openMapping = (row: MigrationBatchVO) => {
  activeBatch.value = row
  mappingForm.sourceRecordId = ''
  mappingForm.resultType = 'MAPPED'
  mappingForm.targetsJson = ''
  mappingVisible.value = true
}
const submitMapping = async () => {
  if (!activeBatch.value) return
  submitting.value = true
  try {
    const targets = JSON.parse(mappingForm.targetsJson)
    await MigrationApi.appendMigrationMapping(activeBatch.value.id, {
      sourceRecordId: mappingForm.sourceRecordId as unknown as number,
      resultType: mappingForm.resultType as 'MAPPED' | 'RETAINED',
      targets,
      idempotencyKey: `mapping-${activeBatch.value.id}-${mappingForm.sourceRecordId}-${Date.now()}`,
      correlationId: `console-${Date.now()}`
    })
    message.success('映射已登记')
    mappingVisible.value = false
    await load()
  } catch {
    message.error('目标JSON格式不正确')
  } finally {
    submitting.value = false
  }
}

const issueVisible = ref(false)
const issueForm = reactive({
  sourceRecordId: '',
  issueKey: '',
  issueType: '',
  rawBusinessKey: '',
  candidateTargetIdsJson: ''
})
const parseCandidateTargetIds = (raw: string): number[] => {
  const text = raw.trim()
  if (!text) return []
  const parsed = JSON.parse(text)
  if (!Array.isArray(parsed) || parsed.some((v) => !Number.isFinite(Number(v)))) {
    throw new Error('候选目标ID必须为数字数组')
  }
  return parsed.map((v) => Number(v))
}
const openIssue = (row: MigrationBatchVO) => {
  activeBatch.value = row
  issueForm.sourceRecordId = ''
  issueForm.issueKey = ''
  issueForm.issueType = ''
  issueForm.rawBusinessKey = ''
  issueForm.candidateTargetIdsJson = ''
  issueVisible.value = true
}
const submitIssue = async () => {
  if (!activeBatch.value) return
  submitting.value = true
  try {
    await MigrationApi.appendMigrationIssue(activeBatch.value.id, {
      sourceRecordId: issueForm.sourceRecordId as unknown as number,
      issueKey: issueForm.issueKey,
      issueType: issueForm.issueType,
      rawBusinessKey: issueForm.rawBusinessKey || undefined,
      candidateTargetIds: parseCandidateTargetIds(issueForm.candidateTargetIdsJson),
      idempotencyKey: `issue-${activeBatch.value.id}-${issueForm.issueKey}-${Date.now()}`,
      correlationId: `console-${Date.now()}`
    })
    message.success('问题已登记')
    issueVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

const completeVisible = ref(false)
const completeForm = reactive({
  expectedBatchVersion: 0,
  expectedMappedCount: 0,
  expectedIssueCount: 0,
  expectedRetainedCount: 0,
  ruleVersion: 'console-v1'
})
const openComplete = (row: MigrationBatchVO) => {
  activeBatch.value = row
  completeForm.expectedBatchVersion = row.version
  completeForm.expectedMappedCount = row.mappedCount
  completeForm.expectedIssueCount = row.issueCount
  completeForm.expectedRetainedCount = row.retainedCount
  completeVisible.value = true
}
const submitComplete = async () => {
  if (!activeBatch.value) return
  submitting.value = true
  try {
    await MigrationApi.completeMigrationReconciliation(activeBatch.value.id, {
      expectedBatchVersion: completeForm.expectedBatchVersion,
      expectedMappedCount: Number(completeForm.expectedMappedCount),
      expectedIssueCount: Number(completeForm.expectedIssueCount),
      expectedRetainedCount: Number(completeForm.expectedRetainedCount),
      ruleVersion: completeForm.ruleVersion,
      idempotencyKey: `complete-${activeBatch.value.id}-${completeForm.expectedBatchVersion}-${Date.now()}`,
      correlationId: `console-${Date.now()}`
    })
    message.success('对账已完成')
    completeVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

const closeIssueVisible = ref(false)
const issues = ref<Record<string, any>[]>([])
const closeIssueForm = reactive({ issueId: '', ruleVersion: 'console-v1', targetResultJson: '' })
watch(closeIssueVisible, async (visible) => {
  if (visible && activeBatch.value) {
    issues.value = (await requestIssues(activeBatch.value.id)) as any
  }
})
const requestIssues = (batchId: number) => MigrationApi.getMigrationIssues(batchId)
const openCloseIssue = async (row: MigrationBatchVO) => {
  activeBatch.value = row
  closeIssueForm.issueId = ''
  closeIssueForm.targetResultJson = ''
  closeIssueVisible.value = true
}
const submitCloseIssue = async () => {
  if (!/^[1-9]\d*$/.test(closeIssueForm.issueId)) {
    message.error('请输入有效的问题 ID')
    return
  }
  submitting.value = true
  try {
    const targetResult = JSON.parse(closeIssueForm.targetResultJson)
    await MigrationApi.closeMigrationIssue(closeIssueForm.issueId, {
      ruleVersion: closeIssueForm.ruleVersion,
      targetResult,
      idempotencyKey: `close-issue-${closeIssueForm.issueId}-${Date.now()}`,
      correlationId: `console-${Date.now()}`
    })
    message.success('问题已结案')
    closeIssueVisible.value = false
    await load()
  } catch {
    message.error('结案结果JSON格式不正确')
  } finally {
    submitting.value = false
  }
}
</script>
