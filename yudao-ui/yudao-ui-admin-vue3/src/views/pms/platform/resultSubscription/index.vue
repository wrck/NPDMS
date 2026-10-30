<template>
  <ContentWrap>
    <div class="mb-10px flex items-center justify-between">
      <h3 class="m-0">结果订阅与执行证据</h3>
      <el-button type="primary" @click="openCreate">新建订阅</el-button>
    </div>
    <el-form inline>
      <el-form-item label="订阅编码">
        <el-input v-model="query.subscriptionCode" style="width: 180px" clearable @change="load" />
      </el-form-item>
      <el-form-item label="结果类型">
        <el-input v-model="query.resultType" style="width: 200px" clearable @change="load" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" style="width: 140px" clearable @change="load">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="subscriptions">
      <el-table-column prop="subscriptionCode" label="订阅编码" min-width="170" />
      <el-table-column prop="subscriberNodeKey" label="订阅节点" min-width="130" />
      <el-table-column prop="resultType" label="结果类型" min-width="170" />
      <el-table-column label="实体" min-width="120">
        <template #default="{ row }">{{ row.ownerModule }}/{{ row.entityType }}</template>
      </el-table-column>
      <el-table-column label="选择策略" min-width="200">
        <template #default="{ row }">
          {{ row.acquisition }} · {{ row.selection }} · {{ row.validityPolicy }}
        </template>
      </el-table-column>
      <el-table-column prop="formationBaseline" label="轮次基线" width="90" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.status === 'SATISFIED' ? 'success' : row.status === 'UNAVAILABLE' ? 'danger' : 'info'">
            {{ statusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openBackfill(row)">存量补扫</el-button>
          <el-button type="primary" link @click="openDecision(row)">判定</el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <ContentWrap v-if="decision">
    <h4 class="mt-0">最近判定：{{ decision.subscriptionCode }}</h4>
    <el-alert
      :type="decision.status === 'SATISFIED' ? 'success' : decision.status === 'UNAVAILABLE' || decision.status === 'AMBIGUOUS' ? 'error' : 'warning'"
      :closable="false"
    >
      状态 {{ statusText(decision.status) }} · 检查 {{ decision.examined }} · 合格 {{ decision.eligible }}
      · 依据 {{ decision.basis }}
      <template v-if="(decision.adoptedResultRefs || []).length">
        · 采纳结果 {{ decision.adoptedResultRefs.length }} 条
      </template>
      <template v-if="(decision.missingObjects || []).length">
        · 缺口对象 {{ decision.missingObjects.join(', ') }}
      </template>
    </el-alert>
    <el-table v-if="(decision.adoptedResultRefs || []).length" :data="adoptedRows" class="mt-10px">
      <el-table-column prop="resultId" label="采纳结果身份" min-width="280" />
    </el-table>
  </ContentWrap>

  <ContentWrap v-if="backfillOutcome">
    <h4 class="mt-0">补扫结果：{{ backfillOutcome.subscriptionCode }}</h4>
    <el-alert :type="backfillOutcome.decision.status === 'SATISFIED' ? 'success' : 'warning'" :closable="false">
      状态 {{ statusText(backfillOutcome.decision.status) }} · 检查 {{ backfillOutcome.decision.examined }}
      · 合格 {{ backfillOutcome.decision.eligible }} · 依据 {{ backfillOutcome.decision.basis }}
    </el-alert>
  </ContentWrap>

  <el-dialog v-model="creating" title="新建结果订阅" width="640px">
    <el-form label-width="120px">
      <el-form-item label="订阅编码" required>
        <el-input v-model="form.subscriptionCode" />
      </el-form-item>
      <el-form-item label="订阅者类型" required>
        <el-select v-model="form.subscriberKind" style="width: 100%">
          <el-option label="任务 TASK" value="TASK" />
          <el-option label="阶段 STAGE" value="STAGE" />
          <el-option label="节点 NODE" value="NODE" />
        </el-select>
      </el-form-item>
      <el-form-item label="订阅节点键" required>
        <el-input v-model="form.subscriberNodeKey" />
      </el-form-item>
      <el-form-item label="结果类型" required>
        <el-input v-model="form.resultType" placeholder="如 demo.ticket.escalated" />
      </el-form-item>
      <el-form-item label="实体" required>
        <div class="flex w-full gap-8px">
          <el-input v-model="form.ownerModule" placeholder="ownerModule" />
          <el-input v-model="form.entityType" placeholder="entityType" />
        </div>
      </el-form-item>
      <el-form-item label="获取语义" required>
        <el-select v-model="form.acquisition" style="width: 100%">
          <el-option label="允许复用既有结果 REUSE_EXISTING" value="REUSE_EXISTING" />
          <el-option label="仅轮次新结果 NEW_RESULT" value="NEW_RESULT" />
          <el-option label="固定结果 PINNED_RESULT" value="PINNED_RESULT" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.acquisition === 'PINNED_RESULT'" label="固定结果身份" required>
        <el-input v-model="form.pinnedResultId" />
      </el-form-item>
      <el-form-item label="数量语义" required>
        <el-select v-model="form.selection" style="width: 100%">
          <el-option label="恰一 EXACT_ONE" value="EXACT_ONE" />
          <el-option label="任一 ANY_MATCHING" value="ANY_MATCHING" />
          <el-option label="全部期望 ALL_EXPECTED" value="ALL_EXPECTED" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.selection === 'ALL_EXPECTED'" label="期望对象ID" required>
        <el-input v-model="expectedObjectsText" placeholder="逗号分隔的对象 ID" />
      </el-form-item>
      <el-form-item label="有效性">
        <el-select v-model="form.validity" style="width: 100%">
          <el-option label="任意（含历史）ANY" value="ANY" />
          <el-option label="仅当前有效 CURRENT_VALID" value="CURRENT_VALID" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="creating = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">建立订阅</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="backfilling" title="存量补扫" width="480px">
    <el-form label-width="120px">
      <el-form-item label="补扫上界" required>
        <el-input v-model="throughText" placeholder="形成序号上界（含）" />
      </el-form-item>
    </el-form>
    <el-alert type="info" :closable="false">轮次基线 {{ activeRow?.formationBaseline }}（不含）；
      已满足的订阅不自动重开。</el-alert>
    <template #footer>
      <el-button @click="backfilling = false">取消</el-button>
      <el-button type="primary" :loading="scanning" @click="doBackfill">开始补扫</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  SubscriptionRow,
  backfillSubscription,
  createSubscription,
  getSubscriptionPage,
  getLastDecision
} from '@/api/pms/platform/resultsubscription'
import { serverErrorMessage } from '@/components/BusinessEntity/useBusinessEntity'

defineOptions({ name: 'PmsResultSubscription' })

const statuses = ['COLLECTING', 'SATISFIED', 'WAITING', 'AMBIGUOUS', 'UNAVAILABLE']

const statusText = (status: string) =>
  ({
    COLLECTING: '收集中',
    SATISFIED: '已满足',
    WAITING: '等待',
    AMBIGUOUS: '歧义',
    UNAVAILABLE: '不可用'
  })[status] ?? status

const loading = ref(false)
const subscriptions = ref<SubscriptionRow[]>([])
const query = reactive<Record<string, string>>({ subscriptionCode: '', resultType: '', status: '' })

const load = async () => {
  loading.value = true
  try {
    const page = await getSubscriptionPage({
      subscriptionCode: query.subscriptionCode || undefined,
      resultType: query.resultType || undefined,
      status: query.status || undefined,
      pageNo: 1,
      pageSize: 50
    })
    subscriptions.value = page.list
  } catch (error) {
    ElMessage.error(serverErrorMessage(error, '订阅加载失败'))
  } finally {
    loading.value = false
  }
}
load()

const creating = ref(false)
const saving = ref(false)
const expectedObjectsText = ref('')
const form = reactive({
  subscriptionCode: '',
  subscriberKind: 'TASK',
  subscriberNodeKey: '',
  resultType: '',
  ownerModule: '',
  entityType: '',
  acquisition: 'REUSE_EXISTING',
  selection: 'ANY_MATCHING',
  validity: 'CURRENT_VALID',
  pinnedResultId: ''
})

const openCreate = () => {
  form.subscriptionCode = ''
  form.subscriberNodeKey = ''
  form.resultType = ''
  form.ownerModule = ''
  form.entityType = ''
  form.acquisition = 'REUSE_EXISTING'
  form.selection = 'ANY_MATCHING'
  form.pinnedResultId = ''
  expectedObjectsText.value = ''
  creating.value = true
}

const save = async () => {
  saving.value = true
  try {
    const expected = expectedObjectsText.value
      .split(/[,，\s]+/)
      .filter((item) => item.length > 0)
      .map((item) => Number(item))
    await createSubscription({
      subscriptionCode: form.subscriptionCode,
      subscriberKind: form.subscriberKind,
      subscriberNodeKey: form.subscriberNodeKey,
      resultType: form.resultType,
      ownerModule: form.ownerModule,
      entityType: form.entityType,
      acquisition: form.acquisition,
      selection: form.selection,
      validity: form.validity,
      pinnedResultId: form.pinnedResultId || undefined,
      expectedObjectIds: expected.length > 0 ? expected : undefined
    })
    ElMessage.success('订阅已建立')
    creating.value = false
    await load()
  } catch (error) {
    ElMessage.error(serverErrorMessage(error, '订阅建立失败'))
  } finally {
    saving.value = false
  }
}

const backfilling = ref(false)
const scanning = ref(false)
const throughText = ref('')
const activeRow = ref<SubscriptionRow>()
const backfillOutcome = ref<{ subscriptionCode: string; decision: any }>()

const openBackfill = (row: SubscriptionRow) => {
  activeRow.value = row
  throughText.value = ''
  backfilling.value = true
}

const doBackfill = async () => {
  if (!activeRow.value) return
  const through = Number(throughText.value)
  if (!Number.isFinite(through) || through <= 0) {
    ElMessage.warning('补扫上界必须是正整数形成序号')
    return
  }
  scanning.value = true
  try {
    const decision = await backfillSubscription(activeRow.value.id, through)
    backfillOutcome.value = { subscriptionCode: activeRow.value.subscriptionCode, decision }
    backfilling.value = false
    await load()
  } catch (error) {
    ElMessage.error(serverErrorMessage(error, '补扫失败'))
  } finally {
    scanning.value = false
  }
}

const decision = ref<any>()
const adoptedRows = ref<{ resultId: string }[]>()

const openDecision = async (row: SubscriptionRow) => {
  try {
    decision.value = { ...(await getLastDecision(row.id)), subscriptionCode: row.subscriptionCode }
    adoptedRows.value = (decision.value.adoptedResultRefs || []).map((resultId: string) => ({ resultId }))
  } catch (error) {
    ElMessage.error(serverErrorMessage(error, '判定加载失败'))
  }
}
</script>
