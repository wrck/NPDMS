<template>
  <div class="commerce-scope-page">
    <ContentWrap>
      <div class="page-heading">
        <div>
          <h2>交付范围</h2>
          <p>按订单行分配项目交付范围；调整会关闭旧区间并追加新版本。</p>
        </div>
        <el-button
          type="primary"
          :disabled="!projectContext"
          v-hasPermi="['pms:commerce:scope:assign']"
          @click="editorRef?.openAssign()"
        >
          <Icon icon="ep:plus" />预览并分配
        </el-button>
      </div>
      <el-alert
        v-if="!projectContext"
        title="请从项目工作台进入本页面；路由必须携带服务端返回的 projectId、projectVersion 和 projectScopeVersion。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-descriptions v-else :column="narrow ? 1 : 4" border>
        <el-descriptions-item label="项目 ID">{{ projectContext.projectId }}</el-descriptions-item>
        <el-descriptions-item label="项目版本">{{
          projectContext.projectVersion
        }}</el-descriptions-item>
        <el-descriptions-item label="项目范围版本">{{
          projectContext.projectScopeVersion
        }}</el-descriptions-item>
        <el-descriptions-item label="交付范围水位">{{ deliveryScopeVersion }}</el-descriptions-item>
      </el-descriptions>
    </ContentWrap>

    <ContentWrap>
      <el-form :model="query" inline class="query-form">
        <el-form-item label="订单行 ID">
          <el-input-number v-model="query.orderLineId" :min="1" :controls="false" clearable />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="query.includeHistory">显示历史版本</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><Icon icon="ep:search" />查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-table v-loading="loading" :data="rows" empty-text="当前项目范围内没有交付范围">
        <el-table-column prop="orderNo" label="订单号" min-width="150" fixed="left" />
        <el-table-column prop="lineNo" label="行号" min-width="80" />
        <el-table-column prop="itemCode" label="物料编码" min-width="130" />
        <el-table-column prop="allocatedQuantity" label="分配数量" min-width="105" />
        <el-table-column prop="scopeStatus" label="状态" min-width="110" />
        <el-table-column prop="allocationVersion" label="分配版本" min-width="100" />
        <el-table-column
          prop="officeDepartmentName"
          label="发生时办事处"
          min-width="170"
          show-overflow-tooltip
        />
        <el-table-column prop="effectiveFrom" label="生效时间" min-width="170" />
        <el-table-column label="明细主体" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ detailSubjects(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link @click="historyRef?.open(row)">历史</el-button>
            <el-button
              v-if="isCurrent(row)"
              link
              :disabled="!projectContext"
              v-hasPermi="['pms:commerce:scope:adjust']"
              @click="editorRef?.openAdjust(row)"
              >调整</el-button
            >
            <el-button
              v-if="isCurrent(row)"
              link
              type="danger"
              :disabled="!projectContext"
              v-hasPermi="['pms:commerce:scope:release']"
              @click="release(row)"
              >释放</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        :total="total"
        v-model:page="query.pageNo"
        v-model:limit="query.pageSize"
        @pagination="load"
      />
    </ContentWrap>

    <DeliveryScopeEditor
      ref="editorRef"
      :project-context="projectContext"
      :delivery-scope-version="deliveryScopeVersion"
      @success="load"
    />
    <DeliveryScopeHistoryDrawer ref="historyRef" />
  </div>
</template>

<script setup lang="ts">
import { ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useWindowSize } from '@vueuse/core'
import { useRoute } from 'vue-router'
import { useMessage } from '@/hooks/web/useMessage'
import * as CommerceApi from '@/api/pms/commerce'
import type { DeliveryScopePageReqVO, DeliveryScopeRespVO } from '@/api/pms/commerce'
import DeliveryScopeEditor from './DeliveryScopeEditor.vue'
import DeliveryScopeHistoryDrawer from './DeliveryScopeHistoryDrawer.vue'
import {
  commerceIntentOf,
  createCommerceIntentStore,
  parseProjectRouteContext
} from '../commerceInteraction'

defineOptions({ name: 'PmsCommerceDeliveryScopes' })
const route = useRoute()
const message = useMessage()
const { width } = useWindowSize()
const narrow = computed(() => width.value < 768)
const projectContext = computed(() => parseProjectRouteContext(route.query))
const loading = ref(false)
const rows = ref<DeliveryScopeRespVO[]>([])
const total = ref(0)
const deliveryScopeVersion = ref(0)
const editorRef = ref<InstanceType<typeof DeliveryScopeEditor>>()
const historyRef = ref<InstanceType<typeof DeliveryScopeHistoryDrawer>>()
const intents = createCommerceIntentStore()
const query = reactive<DeliveryScopePageReqVO>({ pageNo: 1, pageSize: 10, includeHistory: false })

const load = async () => {
  if (!projectContext.value) {
    rows.value = []
    total.value = 0
    deliveryScopeVersion.value = 0
    return
  }
  loading.value = true
  try {
    const [data, version] = await Promise.all([
      CommerceApi.getDeliveryScopePage({
        ...query,
        projectId: projectContext.value.projectId
      }),
      CommerceApi.getDeliveryScopeVersion(projectContext.value.projectId)
    ])
    rows.value = data.list
    total.value = data.total
    deliveryScopeVersion.value = version
  } finally {
    loading.value = false
  }
}
const search = () => {
  query.pageNo = 1
  load()
}
const reset = () => {
  query.orderLineId = undefined
  query.includeHistory = false
  search()
}
const isCurrent = (row: DeliveryScopeRespVO) => !row.effectiveTo && row.scopeStatus !== 'RELEASED'
const detailSubjects = (row: DeliveryScopeRespVO) =>
  row.details
    .map((item) => item.serialNo || item.productCode || item.deviceTypeCode)
    .filter(Boolean)
    .join('、') || '—'
const release = async (scope: DeliveryScopeRespVO) => {
  if (!projectContext.value) return
  const { value } = await ElMessageBox.prompt('请输入释放原因', '释放交付范围', {
    inputPattern: /\S+/,
    inputErrorMessage: '释放原因不能为空',
    confirmButtonText: '确认释放',
    type: 'warning'
  })
  const data = {
    projectId: projectContext.value.projectId,
    expectedProjectVersion: projectContext.value.projectVersion,
    expectedProjectScopeVersion: projectContext.value.projectScopeVersion,
    expectedDeliveryScopeVersion: deliveryScopeVersion.value,
    expectedOrderLineSourceVersion: await sourceVersionOf(scope),
    reason: value.trim()
  }
  const intent = commerceIntentOf('release', {
    scopeId: scope.id,
    allocationVersion: scope.allocationVersion,
    data
  })
  await CommerceApi.releaseDeliveryScope(
    scope.id,
    data,
    scope.allocationVersion,
    intents.key(intent)
  )
  intents.complete(intent)
  message.success('交付范围已释放，历史版本保留')
  await load()
}
const sourceVersionOf = async (scope: DeliveryScopeRespVO) => {
  const data = await CommerceApi.getSalesOrderLinePage({
    pageNo: 1,
    pageSize: 200,
    lineNo: scope.lineNo
  })
  const line = data.list.find((item) => item.id === scope.orderLineId)
  if (!line) throw new Error('当前授权范围内无法读取订单行来源版本')
  return line.sourceVersion
}

watch(projectContext, load)
onMounted(load)
</script>

<style scoped>
.commerce-scope-page {
  min-width: 0;
}

.page-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.page-heading h2 {
  margin: 0;
  font-size: 22px;
}

.page-heading p {
  margin: 8px 0 20px;
  color: var(--el-text-color-secondary);
}

@media (width <= 767px) {
  .page-heading {
    flex-direction: column;
  }

  .page-heading > .el-button {
    width: 100%;
  }

  .query-form {
    display: grid;
  }

  .query-form :deep(.el-form-item),
  .query-form :deep(.el-input-number) {
    width: 100%;
    margin-right: 0;
  }
}
</style>
