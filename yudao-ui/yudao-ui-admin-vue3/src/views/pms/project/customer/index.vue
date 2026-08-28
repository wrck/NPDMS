<template>
  <ContentWrap>
    <el-alert title="客户历史（只读）" type="warning" :closable="false" show-icon class="mb-16px">
      <template #default>
        <div class="flex items-center gap-12px">
          <span>本页面仅展示迁移前历史快照，数据截止时间以迁移基线为准。</span>
          <el-link type="primary" :underline="false" @click="openCustomerWorkbench">
            前往新客户工作台
          </el-link>
        </div>
      </template>
    </el-alert>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="客户编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="客户名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="客户编码" min-width="130" />
      <el-table-column prop="name" label="客户名称" min-width="180" />
      <el-table-column prop="shortName" label="简称" min-width="100" />
      <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
      <el-table-column prop="status" label="历史状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" :formatter="dateFormatter" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
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

  <Dialog v-model="detailVisible" title="客户历史详情" width="620px">
    <el-descriptions v-if="detail" :column="2" border>
      <el-descriptions-item label="客户编码">{{ detail.code }}</el-descriptions-item>
      <el-descriptions-item label="客户名称">{{ detail.name }}</el-descriptions-item>
      <el-descriptions-item label="客户简称">{{ detail.shortName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="历史状态">
        <dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="detail.status" />
      </el-descriptions-item>
      <el-descriptions-item label="地址" :span="2">{{
        detail.address || '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      <el-descriptions-item label="数据截止时间" :span="2">迁移基线历史快照</el-descriptions-item>
    </el-descriptions>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { DICT_TYPE } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import * as CustomerApi from '@/api/pms/project/customer'
import type { CustomerVO } from '@/api/pms/project/customer'

defineOptions({ name: 'PmsCustomerHistory' })

const router = useRouter()
const queryFormRef = ref()
const loading = ref(false)
const rows = ref<CustomerVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, code: '', name: '' })
const detailVisible = ref(false)
const detail = ref<CustomerVO>()

const load = async () => {
  loading.value = true
  try {
    const data = await CustomerApi.getCustomerPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  query.pageNo = 1
  load()
}

const openDetail = async (id: number) => {
  detail.value = await CustomerApi.getCustomer(id)
  detailVisible.value = true
}

const openCustomerWorkbench = () => router.push('/customer-asset/customers')

onMounted(load)
</script>
