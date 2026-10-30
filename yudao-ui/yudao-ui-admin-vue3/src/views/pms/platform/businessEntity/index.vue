<template>
  <BusinessEntityHost
    v-if="route.params.entityType"
    :owner-module="String(route.params.ownerModule)"
    :entity-type="String(route.params.entityType)"
    :key="`${route.params.ownerModule}/${route.params.entityType}`"
  />
  <ContentWrap v-else>
    <h3>统一业务模型目录</h3>
    <el-table v-loading="loading" :data="models" @row-click="open">
      <el-table-column prop="ownerModule" label="所属模块" min-width="140" />
      <el-table-column prop="entityType" label="实体类型" min-width="160" />
      <el-table-column prop="stableCode" label="稳定编码" min-width="180" />
      <el-table-column prop="title" label="名称" min-width="180" />
    </el-table>
    <h4 class="mt-20px mb-10px">执行后端能力（当前装配声明）</h4>
    <el-alert
      v-if="!backends.length"
      title="未装配独立执行后端；模板发布的运行适配与过程语义校验将按未安装拒绝"
      type="warning"
      :closable="false"
    />
    <el-table v-else :data="backends" size="small">
      <el-table-column prop="backendId" label="后端" min-width="160" />
      <el-table-column label="支持语义" min-width="260">
        <template #default="{ row }">{{ (row.supportedSemantics || []).join('、') || '—' }}</template>
      </el-table-column>
      <el-table-column label="不支持语义" min-width="220">
        <template #default="{ row }">{{ (row.unsupportedSemantics || []).join('、') || '—' }}</template>
      </el-table-column>
      <el-table-column label="恢复" width="80">
        <template #default="{ row }">{{ row.supportsRecovery ? '支持' : '不支持' }}</template>
      </el-table-column>
      <el-table-column label="在途迁移" width="90">
        <template #default="{ row }">{{ row.supportsInFlightMigration ? '支持' : '不支持' }}</template>
      </el-table-column>
    </el-table>
  </ContentWrap>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BusinessEntityHost from '@/components/BusinessEntity/BusinessEntityHost.vue'
import {
  getModelCatalog,
  getExecutionCapabilities,
  type ExecutionBackendCapabilityVO,
  type ModelSummaryVO
} from '@/api/pms/platform/businessmodel'

defineOptions({ name: 'PmsBusinessEntityBrowser' })
const route = useRoute()
const router = useRouter()
const models = ref<ModelSummaryVO[]>([])
const backends = ref<ExecutionBackendCapabilityVO[]>([])
const loading = ref(false)

onMounted(async () => {
  if (route.params.entityType) return
  loading.value = true
  try {
    models.value = await getModelCatalog()
  } finally {
    loading.value = false
  }
  backends.value = await getExecutionCapabilities()
})
const open = (row: ModelSummaryVO) =>
  router.push(`/pms/business-entity/${row.ownerModule}/${row.entityType}`)
</script>
