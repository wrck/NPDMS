<!-- src/views/lowcode/publish-center/GrayReleaseManager.vue -->
<script setup lang="ts">
/**
 * 灰度发布管理器（批次5-T4，借鉴华为 AppCube / OutSystems LifeTime）。
 *
 * <p>显示指定配置的灰度发布记录列表，支持：
 * <ul>
 *   <li>创建灰度（基于已 PUBLISHED 的发布记录）</li>
 *   <li>调整灰度比例（0-100 滑块）</li>
 *   <li>全量发布（置 100%）</li>
 *   <li>回滚灰度</li>
 * </ul></p>
 */
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listGrayReleases,
  createGrayRelease,
  updateGrayPercentage,
  releaseFull,
  rollbackGray,
  type LowCodeGrayRelease
} from '@/api/lowcode-gray-release'
import type { EpTagType } from '@/types'

interface Props {
  visible: boolean
  configType: string
  configId: number
  /** 可选：当前选中创建灰度的发布记录 ID */
  publishRecordId?: number | null
}

const props = withDefaults(defineProps<Props>(), {
  publishRecordId: null
})

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'changed'): void
}>()

const loading = ref(false)
const grayList = ref<LowCodeGrayRelease[]>([])

/** 创建灰度对话框 */
const createDialogVisible = ref(false)
const createForm = ref({
  publishRecordId: 0 as number,
  grayPercentage: 10,
  tenantWhitelist: '',
  createBy: ''
})

/** 调整比例对话框 */
const percentageDialogVisible = ref(false)
const percentageForm = ref({ id: 0, grayPercentage: 10 })

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v)
})

watch(() => props.visible, (v) => {
  if (v && props.configType && props.configId) {
    loadList()
  }
})

async function loadList() {
  loading.value = true
  try {
    grayList.value = await listGrayReleases(props.configType, props.configId)
  } catch (e) {
    ElMessage.error('加载灰度记录失败')
    grayList.value = []
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  createForm.value = {
    publishRecordId: props.publishRecordId || 0,
    grayPercentage: 10,
    tenantWhitelist: '',
    createBy: ''
  }
  createDialogVisible.value = true
}

async function submitCreate() {
  if (!createForm.value.publishRecordId) {
    ElMessage.warning('请输入发布记录 ID')
    return
  }
  try {
    await createGrayRelease({
      publishRecordId: createForm.value.publishRecordId,
      grayPercentage: createForm.value.grayPercentage,
      tenantWhitelist: createForm.value.tenantWhitelist || undefined,
      createBy: createForm.value.createBy || undefined
    })
    ElMessage.success('灰度发布已创建')
    createDialogVisible.value = false
    await loadList()
    emit('changed')
  } catch (e) {
    ElMessage.error('创建失败')
  }
}

function openPercentageDialog(row: LowCodeGrayRelease) {
  percentageForm.value = {
    id: row.id,
    grayPercentage: row.grayPercentage
  }
  percentageDialogVisible.value = true
}

async function submitPercentage() {
  try {
    await updateGrayPercentage(percentageForm.value.id, percentageForm.value.grayPercentage)
    ElMessage.success('比例已调整')
    percentageDialogVisible.value = false
    await loadList()
    emit('changed')
  } catch (e) {
    ElMessage.error('调整失败')
  }
}

async function doReleaseFull(row: LowCodeGrayRelease) {
  try {
    await ElMessageBox.confirm(
      `确认将配置 ${row.configCode} v${row.version} 全量发布（100%）？全量后所有用户可见新版本。`,
      '全量发布',
      { confirmButtonText: '全量发布', cancelButtonText: '取消', type: 'warning' }
    )
    await releaseFull(row.id)
    ElMessage.success('已全量发布')
    await loadList()
    emit('changed')
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error('全量发布失败')
  }
}

async function doRollback(row: LowCodeGrayRelease) {
  try {
    await ElMessageBox.confirm(
      `确认回滚灰度 ${row.configCode} v${row.version}？回滚后用户将恢复使用旧版本。`,
      '灰度回滚',
      { confirmButtonText: '回滚', cancelButtonText: '取消', type: 'error' }
    )
    await rollbackGray(row.id)
    ElMessage.success('已回滚')
    await loadList()
    emit('changed')
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error('回滚失败')
  }
}

function statusTag(status: string): EpTagType {
  if (status === 'FULL') return 'success'
  if (status === 'ROLLED_BACK') return 'info'
  return 'warning'
}

function statusLabel(status: string): string {
  if (status === 'GRAYING') return '灰度中'
  if (status === 'FULL') return '已全量'
  if (status === 'ROLLED_BACK') return '已回滚'
  return status
}

function fmtTime(t?: string): string {
  return t ? t.replace('T', ' ').slice(0, 16) : '-'
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="灰度发布管理"
    width="900px"
    :close-on-click-modal="false"
    v-loading="loading"
  >
    <div style="margin-bottom: 12px">
      <el-button type="primary" size="small" @click="openCreateDialog">创建灰度</el-button>
      <el-button size="small" @click="loadList">刷新</el-button>
      <span style="margin-left: 12px; color: var(--el-text-color-secondary); font-size: 12px">
        配置: {{ configType }} / {{ configId }}
      </span>
    </div>

    <el-table :data="grayList" size="small" border max-height="400">
      <el-table-column label="ID" prop="id" width="60" />
      <el-table-column label="版本" prop="version" width="70" />
      <el-table-column label="比例" width="120">
        <template #default="{ row }">
          <el-progress :percentage="row.grayPercentage" :stroke-width="10" :status="row.status === 'FULL' ? 'success' : ''" />
        </template>
      </el-table-column>
      <el-table-column label="租户白名单" prop="tenantWhitelist" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" width="140">
        <template #default="{ row }">{{ fmtTime(row.grayStartedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'GRAYING'"
            link type="primary" size="small"
            @click="openPercentageDialog(row)"
          >调整比例</el-button>
          <el-button
            v-if="row.status === 'GRAYING'"
            link type="success" size="small"
            @click="doReleaseFull(row)"
          >全量发布</el-button>
          <el-button
            v-if="row.status === 'GRAYING'"
            link type="danger" size="small"
            @click="doRollback(row)"
          >回滚</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建灰度对话框 -->
    <el-dialog v-model="createDialogVisible" title="创建灰度发布" width="500px" append-to-body>
      <el-form label-width="140px">
        <el-form-item label="发布记录 ID">
          <el-input-number v-model="createForm.publishRecordId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="灰度比例 (%)">
          <el-slider v-model="createForm.grayPercentage" :min="0" :max="100" :step="5" show-input />
        </el-form-item>
        <el-form-item label="租户白名单">
          <el-input
            v-model="createForm.tenantWhitelist"
            type="textarea"
            :rows="2"
            placeholder='JSON 数组，如 ["tenant1","tenant2"]'
          />
        </el-form-item>
        <el-form-item label="创建人">
          <el-input v-model="createForm.createBy" placeholder="操作人用户名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 调整比例对话框 -->
    <el-dialog v-model="percentageDialogVisible" title="调整灰度比例" width="400px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="新比例 (%)">
          <el-slider v-model="percentageForm.grayPercentage" :min="0" :max="100" :step="5" show-input />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="percentageDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPercentage">调整</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>
