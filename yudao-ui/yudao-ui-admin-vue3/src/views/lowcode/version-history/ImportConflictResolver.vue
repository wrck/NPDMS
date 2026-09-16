<!-- src/views/lowcode/version-history/ImportConflictResolver.vue -->
<script setup lang="ts">
/**
 * 导入冲突解决器（批次5-T3，借鉴 Appsmith Git 导入冲突解决）。
 *
 * <p>显示冲突项表格，每行展示源版本与目标版本对比，用户可选择解决方式：
 * <ul>
 *   <li>保留源版本（KEEP_SOURCE）: 用导入包的版本覆盖目标环境</li>
 *   <li>保留目标版本（KEEP_TARGET）: 跳过此项导入，保留目标环境现有版本</li>
 *   <li>跳过（SKIP）: 不导入此项</li>
 * </ul></p>
 */
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  detectImportConflicts,
  importWithResolution,
  readFileAsText,
  type ImportConflictDTO
} from '@/api/lowcode-version'

interface Props {
  /** 是否显示对话框 */
  visible: boolean
  /** 上传的文件 */
  file: File | null
  /** 目标环境 */
  targetEnvironment: string
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'imported'): void
}>()

const loading = ref(false)
const importing = ref(false)
const conflictData = ref<ImportConflictDTO | null>(null)
const packageJson = ref<string>('')

/** 冲突项的解决方式选择（key=configCode, value=KEEP_SOURCE|KEEP_TARGET|SKIP） */
const resolutions = ref<Record<string, string>>({})

const dialogVisible = computed({
  get: () => props.visible,
  set: (v: boolean) => emit('update:visible', v)
})

const hasConflicts = computed(() => (conflictData.value?.conflicts.length || 0) > 0)

watch(() => props.visible, async (v) => {
  if (v && props.file) {
    await loadConflicts()
  }
})

async function loadConflicts() {
  if (!props.file) {
    ElMessage.warning('请先选择文件')
    return
  }
  loading.value = true
  try {
    // 读取文件内容
    const text = await readFileAsText(props.file)
    packageJson.value = text
    const result = await detectImportConflicts(text, props.targetEnvironment)
    conflictData.value = result
    // 默认所有冲突项设为 KEEP_SOURCE
    const newRes: Record<string, string> = {}
    for (const c of result.conflicts) {
      newRes[c.configCode] = 'KEEP_SOURCE'
    }
    resolutions.value = newRes
  } catch (e) {
    ElMessage.error('检测冲突失败')
    conflictData.value = null
  } finally {
    loading.value = false
  }
}

async function submitImport() {
  if (!packageJson.value) {
    ElMessage.warning('配置包内容为空')
    return
  }
  importing.value = true
  try {
    await importWithResolution(packageJson.value, props.targetEnvironment, resolutions.value)
    ElMessage.success(`导入成功（共 ${conflictData.value?.totalCount} 项）`)
    dialogVisible.value = false
    emit('imported')
  } catch (e) {
    ElMessage.error('导入失败')
  } finally {
    importing.value = false
  }
}

function resolutionTagType(r?: string): 'success' | 'warning' | 'info' {
  if (r === 'KEEP_SOURCE') return 'success'
  if (r === 'KEEP_TARGET') return 'warning'
  return 'info'
}

function resolutionLabel(r?: string): string {
  if (r === 'KEEP_SOURCE') return '保留源版本'
  if (r === 'KEEP_TARGET') return '保留目标版本'
  if (r === 'SKIP') return '跳过'
  return '未选择'
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="导入冲突解决"
    width="900px"
    :close-on-click-modal="false"
    v-loading="loading"
  >
    <div v-if="conflictData" class="conflict-summary">
      <el-alert
        :title="`共 ${conflictData.totalCount} 项配置，无冲突 ${conflictData.noConflictCount} 项，有冲突 ${conflictData.conflicts.length} 项`"
        :type="hasConflicts ? 'warning' : 'success'"
        :closable="false"
        show-icon
      />
    </div>

    <el-empty
      v-if="conflictData && !hasConflicts"
      description="无冲突，可直接导入"
      :image-size="60"
    />

    <el-table
      v-if="hasConflicts"
      :data="conflictData?.conflicts || []"
      size="small"
      border
      max-height="400"
    >
      <el-table-column label="配置编码" prop="configCode" width="160" />
      <el-table-column label="类型" prop="configType" width="100" />
      <el-table-column label="源版本" width="140">
        <template #default="{ row }">
          <div>v{{ row.sourceVersion }}</div>
          <div class="version-meta">{{ row.sourceChangeLog || '（无说明）' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="目标版本（已有）" width="140">
        <template #default="{ row }">
          <div>v{{ row.targetVersion }}</div>
          <div class="version-meta">{{ row.targetChangeLog || '（无说明）' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="解决方式" width="180">
        <template #default="{ row }">
          <el-select v-model="resolutions[row.configCode]" size="small">
            <el-option label="保留源版本（覆盖）" value="KEEP_SOURCE" />
            <el-option label="保留目标版本（跳过）" value="KEEP_TARGET" />
            <el-option label="跳过" value="SKIP" />
          </el-select>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="importing"
        :disabled="loading || !conflictData"
        @click="submitImport"
      >
        确认导入
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.conflict-summary {
  margin-bottom: 16px;
}

.version-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}
</style>
