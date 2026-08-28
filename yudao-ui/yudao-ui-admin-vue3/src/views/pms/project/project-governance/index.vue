<template>
  <ContentWrap>
    <el-alert
      title="此页面仅保留 V1.7 治理动作历史查询；V1.8 回退、异常关闭和受控重开请在项目详情的“异常治理”区域操作。"
      type="info"
      :closable="false"
      show-icon
      class="history-alert"
    />
    <el-form :model="query" inline class="query-form">
      <el-form-item label="项目">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择项目"
          class="query-project"
        />
      </el-form-item>
      <el-form-item label="动作单号">
        <el-input v-model="query.actionNo" clearable @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="动作类型">
        <el-select v-model="query.actionType" clearable>
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_GOVERNANCE_ACTION_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable>
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_GOVERNANCE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        ><el-button @click="load"><Icon icon="ep:search" />查询</el-button></el-form-item
      >
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <div class="table-scroll">
      <el-table v-loading="loading" :data="rows" empty-text="暂无历史治理动作">
        <el-table-column prop="actionNo" label="动作单号" min-width="170" />
        <el-table-column prop="projectId" label="项目编号" min-width="120">
          <template #default="{ row }"><ProjectTag :project-id="row.projectId" /></template>
        </el-table-column>
        <el-table-column prop="actionType" label="动作类型" min-width="110">
          <template #default="{ row }">
            <dict-tag :type="DICT_TYPE.PMS_GOVERNANCE_ACTION_TYPE" :value="row.actionType" />
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="历史原因" min-width="220" show-overflow-tooltip />
        <el-table-column prop="status" label="历史状态" min-width="100">
          <template #default="{ row }">
            <dict-tag :type="DICT_TYPE.PMS_GOVERNANCE_STATUS" :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column
          prop="applyTime"
          label="申请时间"
          min-width="168"
          :formatter="dateFormatter"
        />
        <el-table-column
          prop="executeTime"
          label="执行时间"
          min-width="168"
          :formatter="dateFormatter"
        />
        <el-table-column label="操作" width="72" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="openDetail(row)"
              v-hasPermi="['pms:project-governance:query']"
              >明细</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </div>
    <Pagination
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      :total="total"
      @pagination="load"
    />
  </ContentWrap>

  <Dialog
    v-model="detailVisible"
    title="V1.7 治理动作历史明细"
    width="min(780px, calc(100vw - 24px))"
  >
    <el-descriptions :column="descriptionColumns" border>
      <el-descriptions-item label="动作单号">{{ current.actionNo }}</el-descriptions-item>
      <el-descriptions-item label="动作类型">
        <dict-tag :type="DICT_TYPE.PMS_GOVERNANCE_ACTION_TYPE" :value="current.actionType ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="项目编号"
        ><ProjectTag :project-id="current.projectId"
      /></el-descriptions-item>
      <el-descriptions-item label="历史状态">
        <dict-tag :type="DICT_TYPE.PMS_GOVERNANCE_STATUS" :value="current.status ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="申请人"
        ><UserTag :user-id="current.applicantUserId"
      /></el-descriptions-item>
      <el-descriptions-item label="申请时间">{{ current.applyTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="审批人"
        ><UserTag :user-id="current.approverUserId"
      /></el-descriptions-item>
      <el-descriptions-item label="审批时间">{{ current.approveTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="历史原因" :span="descriptionColumns">
        <div class="reason-text">{{ plainText(current.reason) }}</div>
      </el-descriptions-item>
      <el-descriptions-item label="审批意见" :span="descriptionColumns">{{
        current.approveOpinion || '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="执行时间" :span="descriptionColumns">{{
        current.executeTime || '-'
      }}</el-descriptions-item>
    </el-descriptions>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { dateFormatter } from '@/utils/formatTime'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as GovernanceApi from '@/api/pms/project/project-governance'
import * as ProjectApi from '@/api/pms/project/project'
import ProjectTag from '@/components/ProjectTag/index.vue'
import UserTag from '@/components/UserTag/index.vue'
import type { ProjectGovernanceVO } from '@/api/pms/project/project-governance'

defineOptions({ name: 'PmsProjectGovernance' })

const mobile = useMediaQuery('(max-width: 767px)')
const descriptionColumns = computed(() => (mobile.value ? 1 : 2))
const loading = ref(false)
const rows = ref<ProjectGovernanceVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  actionNo: '',
  actionType: '',
  status: undefined as number | undefined
})
const detailVisible = ref(false)
const current = ref<Partial<ProjectGovernanceVO>>({})

const load = async () => {
  loading.value = true
  try {
    const data = await GovernanceApi.getGovernanceActionPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openDetail = async (row: ProjectGovernanceVO) => {
  current.value = await GovernanceApi.getGovernanceAction(row.id!)
  detailVisible.value = true
}
const plainText = (value?: string) =>
  value
    ? value
        .replace(/<[^>]*>/g, ' ')
        .replace(/\s+/g, ' ')
        .trim()
    : '-'

onMounted(load)
</script>

<style scoped lang="scss">
.history-alert {
  margin-bottom: 16px;
}

.query-form {
  margin-bottom: -15px;
}

.query-form :deep(.el-input),
.query-form :deep(.el-select) {
  width: 180px;
}

.query-project {
  width: 220px;
}

.table-scroll {
  overflow-x: auto;
}

.reason-text {
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

@media (width <= 767px) {
  .query-form :deep(.el-form-item),
  .query-form :deep(.el-input),
  .query-form :deep(.el-select),
  .query-project {
    width: 100%;
  }
}
</style>
