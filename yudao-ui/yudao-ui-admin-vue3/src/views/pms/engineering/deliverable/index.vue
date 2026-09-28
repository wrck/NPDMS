<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="项目编号" prop="projectId">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="类型" prop="deliverableType">
        <el-select v-model="query.deliverableType" clearable class="!w-160px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_ENG_DELIVERABLE_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_ENG_DELIVERABLE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
    <ProjectDeliverablesPanel v-if="query.projectId" :project-id="Number(query.projectId)" />
  <el-alert
    v-else
    title="请先选择项目，查看模板交付件并上传材料。交付件统一经交付面板登记，下方为统一交付件启用前的历史归集记录（只读）。"
    type="info"
    :closable="false"
  />
  <ContentWrap>
    <h3>历史归集记录（只读）</h3>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="name" label="交付件名称" min-width="220" show-overflow-tooltip />
      <el-table-column prop="deliverableType" label="类型" width="110">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ENG_DELIVERABLE_TYPE" :value="row.deliverableType" />
        </template>
      </el-table-column>
      <el-table-column prop="fileUrl" label="文件地址" min-width="220" show-overflow-tooltip />
      <el-table-column prop="archivedTime" label="归集时间" width="160" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ENG_DELIVERABLE_STATUS" :value="row.status" />
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
</template>

<script setup lang="ts">
import ProjectDeliverablesPanel from '@/views/pms/project/project-master-detail/components/ProjectDeliverablesPanel.vue'
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as DeliverableApi from '@/api/pms/engineering/deliverable'
import type { DeliverableVO } from '@/api/pms/engineering/deliverable'
import * as ProjectApi from '@/api/pms/project/projects'

defineOptions({ name: 'PmsEngDeliverable' })
const loading = ref(false)
const rows = ref<DeliverableVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: '',
  deliverableType: undefined,
  status: undefined
})

const load = async () => {
  loading.value = true
  try {
    const data = await DeliverableApi.getDeliverablePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
