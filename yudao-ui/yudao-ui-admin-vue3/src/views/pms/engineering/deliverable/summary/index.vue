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
          <el-button
            v-if="row.materialLocator" v-hasPermi="['pms:file:preview']" link type="primary"
            :loading="previewLoading === String(row.materialLocator.materialId)" @click="previewMaterial(row)"
            >预览</el-button>
          <el-button v-else-if="row.fileUrl" v-hasPermi="['pms:file:preview']" link type="primary" @click="previewLegacy(row)">预览</el-button>
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
  <PmsFilePreviewDialog ref="previewDialog" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import * as DeliverableApi from '@/api/pms/engineering/deliverable'
import type { DeliverableSummaryItemVO } from '@/api/pms/engineering/deliverable'
import * as ProjectApi from '@/api/pms/project/projects'
import * as FileApi from '@/api/pms/platform/file'
import PmsFilePreviewDialog from '@/components/PmsFileArtifact/PmsFilePreviewDialog.vue'
import { useMessage } from '@/hooks/web/useMessage'
import { resolveSummaryPreview } from './resolveSummaryPreview'

defineOptions({ name: 'PmsImpDeliverableSummary' })

interface CategoryGroup {
  category: string
  label: string
  emptyText: string
}

const groups: CategoryGroup[] = [
  {
    category: 'RECEIPT',
    label: '到货签收单',
    emptyText: '暂无到货签收材料；登记的签收单附件及签收结果会自动归集至此。'
  },
  { category: 'SCHEME', label: '实施方案', emptyText: '暂无已批准实施方案；方案审批通过后自动归档至此。' },
  { category: 'PRELIMINARY', label: '初验报告', emptyText: '暂无初验报告材料；登记的初验报告附件会自动归集至此。' },
  { category: 'FINAL', label: '终验报告', emptyText: '暂无终验报告材料；登记的终验报告附件会自动归集至此。' },
  { category: 'TRAINING', label: '现场培训记录', emptyText: '暂无现场培训记录归档；在"现场培训"页面完成客户签字确认后自动归档。' },
  { category: 'SATISFACTION', label: '满意度调查报告', emptyText: '暂无满意度报告归档；满意度结果生效后由验收侧自动同步。' },
  { category: 'OTHER', label: '其他工程归集', emptyText: '暂无其他归集记录；可经"交付件归集"页面登记。' }
]

const props = defineProps<{ projectId?: number }>()

const projectId = ref<number | undefined>(props.projectId)
const items = ref<DeliverableSummaryItemVO[]>([])
const previewDialog = ref<InstanceType<typeof PmsFilePreviewDialog>>()
const previewLoading = ref('')
const message = useMessage()

const previewLegacy = async (row: DeliverableSummaryItemVO) => {
  if (!row.fileUrl) return
  let url: URL
  try { url = new URL(row.fileUrl, window.location.href) } catch { message.warning('文件地址不支持预览'); return }
  if (!['http:', 'https:'].includes(url.protocol)) { message.warning('文件地址不支持预览'); return }
  await previewDialog.value?.open({ title: row.name || '文件预览', loadContent: async () => {
    const response = await fetch(url.href, { credentials: 'same-origin' })
    if (!response.ok) throw new Error('FILE_PREVIEW_FAILED')
    return response.blob()
  } })
}

const previewMaterial = async (row: DeliverableSummaryItemVO) => {
  const locator = row.materialLocator
  if (!locator || previewLoading.value) return
  const selectedProject = projectId.value
  previewLoading.value = String(locator.materialId)
  try {
    const material = await resolveSummaryPreview(row)
    if (String(projectId.value) !== String(selectedProject) || !items.value.includes(row)) return
    const { fileArtifactId, fileVersionNo, fileBusinessKey } = material
    await previewDialog.value?.open({ title: material.fileName || row.name || '文件预览',
      loadContent: () => FileApi.getPreviewContent(fileArtifactId, fileVersionNo, fileBusinessKey) })
  } catch {
    message.warning('预览不可用，请检查文件状态和访问权限后重试')
  } finally {
    previewLoading.value = ''
  }
}


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
