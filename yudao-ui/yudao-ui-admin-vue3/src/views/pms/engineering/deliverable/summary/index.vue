<template>
  <!-- 单根节点：多根 fragment 会在父级块 patch 中引发组件 vnode 错位；独立页面保留项目选择器，嵌入模式隐藏并自动加载 -->
  <div class="deliverable-summary-panel">
    <ContentWrap v-if="!props.projectId">
      <el-form inline class="-mb-15px">
      <el-form-item label="项目">
        <PmsEntitySelect
          v-model="projectId"
          :api="ProjectApi.getProjectPage"
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
          class="!w-260px"
          @change="load"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :disabled="!projectId" @click="load">
          <Icon icon="ep:search" />查询
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap v-for="group in groups" :key="group.category" :title="group.label">
    <el-table v-if="itemsOf(group.category).length" :data="itemsOf(group.category)" size="small">
      <el-table-column prop="name" label="名称" min-width="220" show-overflow-tooltip />
      <el-table-column prop="sourceLabel" label="来源" min-width="150" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">{{ statusLabel(row) }}</template>
      </el-table-column>
      <el-table-column prop="archivedTime" label="归档/生效时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-link
            v-if="row.fileUrl"
            :href="row.fileUrl"
            target="_blank"
            type="primary"
            class="mr-10px"
            >下载</el-link
          >
          <span v-if="row.remark" class="text-12px text-gray-400">{{ row.remark }}</span>
        </template>
      </el-table-column>
    </el-table>
    <div v-else class="text-13px text-gray-400 leading-22px">{{ group.emptyText }}</div>
  </ContentWrap>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import * as DeliverableApi from '@/api/pms/engineering/deliverable'
import type { DeliverableSummaryItemVO } from '@/api/pms/engineering/deliverable'
import * as ProjectApi from '@/api/pms/project/projects'

defineOptions({ name: 'PmsImpDeliverableSummary' })

interface CategoryGroup {
  category: string
  label: string
  emptyText: string
}

const groups: CategoryGroup[] = [
  {
    category: 'RECEIPT',
    label: '到货签收单（5.1）',
    emptyText: '暂无签收单归档；签收单自动归档在途（EXE-01 切片承接中），当前可经"交付件归集"以签收单类型登记。'
  },
  { category: 'SCHEME', label: '实施方案（4.1）', emptyText: '暂无已批准实施方案；方案审批通过后自动归档至此。' },
  { category: 'PRELIMINARY', label: '初验报告（6.3）', emptyText: '暂无初验报告归档；初验报告生效后由验收侧自动同步。' },
  { category: 'FINAL', label: '终验报告（6.3）', emptyText: '暂无终验报告归档；终验报告生效后由验收侧自动同步。' },
  { category: 'TRAINING', label: '现场培训记录（6.1）', emptyText: '暂无现场培训记录归档；在"现场培训（6.1）"页面完成客户签字确认后自动归档。' },
  { category: 'SATISFACTION', label: '满意度调查报告（6.2）', emptyText: '暂无满意度报告归档；满意度结果生效后由验收侧自动同步。' },
  { category: 'OTHER', label: '其他工程归集', emptyText: '暂无其他归集记录；可经"交付件归集"页面登记。' }
]

const props = defineProps<{ projectId?: number }>()

const projectId = ref<number | undefined>(props.projectId)
const items = ref<DeliverableSummaryItemVO[]>([])


const itemsOf = (category: string) => items.value.filter((item) => item.category === category)

const statusLabel = (row: DeliverableSummaryItemVO) => {
  if (row.status === null || row.status === undefined) {
    return row.remark ? '未归档' : '已归档'
  }
  return { 0: '待归集', 1: '已归集', 2: '已作废' }[row.status] ?? String(row.status)
}

const load = async () => {
  const pid = projectId.value
  if (pid == null) {
    items.value = []
    return
  }
  items.value = await DeliverableApi.getDeliverableProjectSummary(pid)
}
// 嵌入模式：父页面传入 projectId 时自动加载；独立页面仍由选择器驱动
watch(
  () => props.projectId,
  (value) => {
    if (value == null) return
    projectId.value = value
    load()
  },
  { immediate: true }
)

</script>
