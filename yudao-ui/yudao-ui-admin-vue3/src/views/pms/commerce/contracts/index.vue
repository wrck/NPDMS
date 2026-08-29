<template>
  <div class="commerce-page">
    <ContentWrap>
      <div class="page-heading">
        <div>
          <h2>合同与订单</h2>
          <p>只读展示 ERP 权威副本；公司数据范围与字段权限由服务端执行。</p>
        </div>
      </div>
      <el-form :model="query" inline class="query-form">
        <el-form-item label="合同编号">
          <el-input v-model="query.contractNo" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-input v-model="query.status" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><Icon icon="ep:search" />查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-table v-loading="loading" :data="rows" empty-text="当前授权范围内没有合同">
        <el-table-column prop="contractNo" label="合同编号" min-width="160" fixed="left" />
        <el-table-column
          prop="contractName"
          label="合同名称"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          prop="companyName"
          label="所属公司"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column prop="customerName" label="客户" min-width="150" show-overflow-tooltip />
        <el-table-column prop="contractType" label="类型" min-width="100" />
        <el-table-column prop="status" label="状态" min-width="100" />
        <el-table-column prop="sourceVersion" label="来源版本" min-width="130" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="detailRef?.open(row.id, routeProjectId)"
              >详情</el-button
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

    <ContentWrap>
      <el-tabs v-model="activeOrderTab" @tab-change="loadOrders">
        <el-tab-pane label="销售订单" name="orders">
          <el-table v-loading="orderLoading" :data="orders" empty-text="没有可见销售订单">
            <el-table-column prop="orderNo" label="订单号" min-width="150" />
            <el-table-column prop="orderType" label="类型" min-width="100" />
            <el-table-column prop="companyName" label="所属公司" min-width="150" />
            <el-table-column prop="customerName" label="客户" min-width="150" />
            <el-table-column prop="status" label="状态" min-width="100" />
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button link @click="showLines(row)">订单行</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane
          :label="selectedOrder ? `订单行 · ${selectedOrder.orderNo}` : '订单行'"
          name="lines"
        >
          <el-alert
            v-if="!selectedOrder"
            title="请先从销售订单中选择一张订单。"
            type="info"
            :closable="false"
          />
          <el-table v-else v-loading="lineLoading" :data="lines" empty-text="该订单没有订单行">
            <el-table-column prop="lineNo" label="行号" min-width="90" />
            <el-table-column prop="itemCode" label="物料编码" min-width="140" />
            <el-table-column prop="productCode" label="ERP 产品编码" min-width="150">
              <template #default="{ row }">{{ row.productCode || '未提供' }}</template>
            </el-table-column>
            <el-table-column prop="orderQty" label="订单数量" min-width="110" />
            <el-table-column prop="openQty" label="开放数量" min-width="110" />
            <el-table-column prop="unitCode" label="单位" min-width="90" />
            <el-table-column prop="quantityStatus" label="数量状态" min-width="120" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </ContentWrap>
    <ContractDetail ref="detailRef" @success="load" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import * as CommerceApi from '@/api/pms/commerce'
import type {
  ContractPageReqVO,
  ContractRespVO,
  SalesOrderLineRespVO,
  SalesOrderRespVO
} from '@/api/pms/commerce'
import ContractDetail from './detail.vue'

defineOptions({ name: 'PmsCommerceContracts' })
const route = useRoute()
const routeProjectId = computed(() => {
  const value = Number(route.query.projectId)
  return Number.isInteger(value) && value > 0 ? value : undefined
})
const loading = ref(false)
const orderLoading = ref(false)
const lineLoading = ref(false)
const rows = ref<ContractRespVO[]>([])
const orders = ref<SalesOrderRespVO[]>([])
const lines = ref<SalesOrderLineRespVO[]>([])
const total = ref(0)
const activeOrderTab = ref('orders')
const selectedOrder = ref<SalesOrderRespVO>()
const detailRef = ref<InstanceType<typeof ContractDetail>>()
const query = reactive<ContractPageReqVO>({ pageNo: 1, pageSize: 10, contractNo: '', status: '' })

const load = async () => {
  loading.value = true
  try {
    const data = await CommerceApi.getContractPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const search = () => {
  query.pageNo = 1
  load()
}
const reset = () => {
  query.contractNo = ''
  query.status = ''
  search()
}
const loadOrders = async (tab?: string | number) => {
  if ((tab || activeOrderTab.value) !== 'orders' || orders.value.length) return
  orderLoading.value = true
  try {
    const data = await CommerceApi.getSalesOrderPage({ pageNo: 1, pageSize: 100 })
    orders.value = data.list
  } finally {
    orderLoading.value = false
  }
}
const showLines = async (order: SalesOrderRespVO) => {
  selectedOrder.value = order
  activeOrderTab.value = 'lines'
  lineLoading.value = true
  try {
    const data = await CommerceApi.getSalesOrderLinePage({
      pageNo: 1,
      pageSize: 200,
      orderId: order.id
    })
    lines.value = data.list
  } finally {
    lineLoading.value = false
  }
}

onMounted(() => Promise.all([load(), loadOrders()]))
</script>

<style scoped>
.commerce-page {
  min-width: 0;
}

.page-heading h2 {
  margin: 0;
  font-size: 22px;
}

.page-heading p {
  margin: 8px 0 20px;
  color: var(--el-text-color-secondary);
}

.query-form :deep(.el-form-item) {
  max-width: 100%;
}

@media (width <= 767px) {
  .query-form {
    display: grid;
  }

  .query-form :deep(.el-form-item),
  .query-form :deep(.el-input) {
    width: 100%;
    margin-right: 0;
  }
}
</style>
