<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  approvePublish,
  getPendingList,
  rejectPublish,
  rollbackPublish,
  type LowCodePublishRecord
} from '@/api/lowcode-publish'
import {
  getApprovalChainList,
  parseLevels,
  type LowCodeApprovalChain
} from '@/api/lowcode-approval-chain'
import { useUserStore } from '@/stores/user'
import GrayReleaseManager from './GrayReleaseManager.vue'

defineOptions({ name: 'PublishCenterView' })

const userStore = useUserStore()
const pendingList = ref<LowCodePublishRecord[]>([])
const loading = ref(false)
/** 审批链映射：approvalChainId → 审批链对象（用于显示当前级别名称/总数） */
const chainMap = ref<Map<number, LowCodeApprovalChain>>(new Map())

/** 灰度发布管理对话框状态（批次5-T4） */
const grayManagerVisible = ref(false)
const grayManagerConfigType = ref('')
const grayManagerConfigId = ref(0)
const grayManagerPublishRecordId = ref<number | null>(null)

/** 打开灰度发布管理器（仅 PUBLISHED 状态可用） */
function openGrayManager(row: LowCodePublishRecord) {
  grayManagerConfigType.value = row.configType
  grayManagerConfigId.value = row.configId ?? 0
  grayManagerPublishRecordId.value = row.id ?? null
  grayManagerVisible.value = true
}

async function loadPending() {
  loading.value = true
  try {
    // 并行加载待审批列表与全部审批链（构建映射用于级别展示）
    const [list, chains] = await Promise.all([getPendingList(), getApprovalChainList()])
    pendingList.value = list
    const map = new Map<number, LowCodeApprovalChain>()
    chains.forEach((c) => {
      if (c.id != null) map.set(c.id, c)
    })
    chainMap.value = map
  } finally {
    loading.value = false
  }
}

/**
 * 计算审批级别展示文本。
 *
 * <p>多级审批（approvalChainId 存在且 currentLevel 存在）：返回 "1/3 主管审批" 格式，
 * 其中 3 为审批链总级数，"主管审批"为当前级别名称；
 * 单步审批（无审批链）：返回 "单步审批"。</p>
 */
function approvalLevelText(row: LowCodePublishRecord): string {
  if (!row.approvalChainId || row.currentLevel == null) return '单步审批'
  const chain = chainMap.value.get(row.approvalChainId)
  if (!chain) return `${row.currentLevel}/?`
  const levels = parseLevels(chain.levels)
  if (levels.length === 0) return `${row.currentLevel}/?`
  const current = levels.find((l) => l.level === row.currentLevel)
  const total = levels.length
  const name = current?.name ?? ''
  return `${row.currentLevel}/${total} ${name}`.trim()
}

/** 状态文案与颜色映射（含多级审批进行中 APPROVING） */
function statusTagType(status: LowCodePublishRecord['status']): 'success' | 'danger' | 'warning' | 'info' {
  switch (status) {
    case 'PUBLISHED':
      return 'success'
    case 'APPROVED':
      return 'success'
    case 'REJECTED':
      return 'danger'
    case 'APPROVING':
      return 'warning'
    default:
      return 'warning'
  }
}

function statusLabel(status: LowCodePublishRecord['status']): string {
  switch (status) {
    case 'DRAFT':
      return '草稿'
    case 'SUBMITTED':
      return '待审批'
    case 'APPROVING':
      return '审批中'
    case 'APPROVED':
      return '已通过'
    case 'REJECTED':
      return '已拒绝'
    case 'PUBLISHED':
      return '已发布'
    default:
      return status
  }
}

async function approve(id: number) {
  try {
    await approvePublish(id, userStore.userInfo?.id || 0, userStore.userInfo?.username)
    ElMessage.success('审批通过')
    await loadPending()
  } catch {
    /* */
  }
}

async function reject(id: number) {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入拒绝原因', '审批拒绝', {
      confirmButtonText: '拒绝',
      cancelButtonText: '取消'
    })
    await rejectPublish(id, reason, userStore.userInfo?.id || 0, userStore.userInfo?.username)
    ElMessage.success('已拒绝')
    await loadPending()
  } catch {
    /* */
  }
}

async function rollback(id: number) {
  try {
    await ElMessageBox.confirm('确认回滚到该版本？', '提示', { type: 'warning' })
    await rollbackPublish(id, userStore.userInfo?.id || 0, userStore.userInfo?.username)
    ElMessage.success('回滚成功')
  } catch {
    /* */
  }
}

onMounted(loadPending)
</script>

<template>
  <div style="padding: 16px">
    <el-card shadow="never">
      <template #header>
        <span>发布审批中心</span>
      </template>
      <el-table v-loading="loading" :data="pendingList">
        <el-table-column label="配置类型" prop="configType" width="100" />
        <el-table-column label="配置 ID" prop="configId" width="80" />
        <el-table-column label="版本" prop="version" width="60" />
        <el-table-column label="申请人" prop="applicant" width="100" />
        <el-table-column label="变更说明" prop="changeLog" show-overflow-tooltip />
        <el-table-column label="审批级别" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.approvalChainId" type="primary" size="small">
              {{ approvalLevelText(row) }}
            </el-tag>
            <span v-else class="single-step">单步审批</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="approve(row.id!)">通过</el-button>
            <el-button size="small" type="danger" @click="reject(row.id!)">拒绝</el-button>
            <el-button size="small" type="warning" :disabled="row.status !== 'PUBLISHED'" @click="rollback(row.id!)">
              回滚
            </el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="warning" size="small" @click="openGrayManager(row)">
              灰度
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <GrayReleaseManager
      v-model:visible="grayManagerVisible"
      :config-type="grayManagerConfigType"
      :config-id="grayManagerConfigId"
      :publish-record-id="grayManagerPublishRecordId"
    />
  </div>
</template>

<style scoped>
.single-step {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
