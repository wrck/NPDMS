<!-- src/views/lowcode/version-history/RollbackPreviewDialog.vue -->
<script setup lang="ts">
/**
 * 回滚预览对话框（批次5-T5，借鉴 OutSystems LifeTime 回滚预览）。
 *
 * <p>回滚前展示：
 * <ul>
 *   <li>版本 Diff（复用 JsonTreeDiff 组件）</li>
 *   <li>发布影响范围分析（受影响的下游配置列表）</li>
 * </ul>
 * 用户确认后执行回滚。</p>
 */
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import JsonTreeDiff from '@/components/JsonTreeDiff/index.vue'
import {
  getRollbackPreview,
  getPublishImpact,
  rollbackVersion,
  type VersionDiffDTO,
  type PublishImpactDTO,
  type LowCodeConfigVersion
} from '@/api/lowcode-version'
import type { EpTagType } from '@/types'

interface Props {
  visible: boolean
  configType: string
  configId: number
  /** 要回滚到的目标版本 */
  targetVersion?: number | null
  /** 目标版本快照（用于 JsonTreeDiff） */
  targetSnapshot?: string
  /** 全部版本列表（用于查找当前版本快照） */
  versionList?: LowCodeConfigVersion[]
}

const props = withDefaults(defineProps<Props>(), {
  targetVersion: null,
  targetSnapshot: '',
  versionList: () => []
})

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'rolled'): void
}>()

const loading = ref(false)
const rolling = ref(false)
const diffResult = ref<VersionDiffDTO | null>(null)
const impactResult = ref<PublishImpactDTO | null>(null)
const oldSnapshot = ref<unknown>(null)
const newSnapshot = ref<unknown>(null)

const visibleComputed = computed({
  get: () => props.visible,
  set: (v: boolean) => emit('update:visible', v)
})

watch(() => props.visible, async (v) => {
  if (v && props.targetVersion) {
    await loadPreview()
  }
})

async function loadPreview() {
  if (!props.targetVersion) return
  loading.value = true
  try {
    diffResult.value = await getRollbackPreview(props.configType, props.configId, props.targetVersion)
    // 加载快照用于 JsonTreeDiff
    const currentVersion = props.versionList.find(v => v.environment === 'DEV' && v.status === 'ACTIVE')
    if (currentVersion && props.targetSnapshot) {
      try {
        oldSnapshot.value = JSON.parse(currentVersion.snapshot || '{}')
        newSnapshot.value = JSON.parse(props.targetSnapshot)
      } catch {
        oldSnapshot.value = null
        newSnapshot.value = null
      }
    }
    // 加载影响范围（如有 configCode）
    const targetVer = props.versionList.find(v => v.version === props.targetVersion)
    if (targetVer?.configCode) {
      impactResult.value = await getPublishImpact(
        props.configType,
        props.configId,
        targetVer.configCode
      )
    }
  } catch (e) {
    ElMessage.error('加载预览失败')
    diffResult.value = null
    impactResult.value = null
  } finally {
    loading.value = false
  }
}

async function confirmRollback() {
  if (!props.targetVersion) return
  rolling.value = true
  try {
    await rollbackVersion(props.configType, props.configId, props.targetVersion, `回滚到 v${props.targetVersion}`)
    ElMessage.success('回滚成功，已生成新版本')
    visibleComputed.value = false
    emit('rolled')
  } catch (e) {
    ElMessage.error('回滚失败')
  } finally {
    rolling.value = false
  }
}

function severityTag(s: string): EpTagType {
  if (s === 'HIGH') return 'danger'
  if (s === 'MEDIUM') return 'warning'
  return 'info'
}
</script>

<template>
  <el-dialog
    v-model="visibleComputed"
    title="回滚预览"
    width="900px"
    :close-on-click-modal="false"
    v-loading="loading"
  >
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    >
      <template #title>
        回滚到 v{{ targetVersion }}？以下为版本差异与影响范围分析，确认后将创建新版本（不删除历史）。
      </template>
    </el-alert>

    <el-tabs>
      <el-tab-pane label="版本差异">
        <el-empty v-if="!diffResult || diffResult.entries.length === 0"
          description="无差异"
          :image-size="60"
        />
        <JsonTreeDiff v-else :old-data="oldSnapshot" :new-data="newSnapshot" />
        <el-table
          v-if="diffResult && diffResult.entries.length > 0"
          :data="diffResult.entries"
          size="small"
          border
          style="margin-top: 12px"
        >
          <el-table-column label="类型" prop="changeType" width="80" />
          <el-table-column label="字段路径" prop="fieldPath" />
          <el-table-column label="旧值" prop="oldValue" show-overflow-tooltip />
          <el-table-column label="新值" prop="newValue" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`影响范围${impactResult ? '(' + impactResult.totalImpacted + ')' : ''}`">
        <el-empty
          v-if="!impactResult || impactResult.totalImpacted === 0"
          description="无受影响配置"
          :image-size="60"
        />
        <el-table
          v-else
          :data="impactResult.impactedConfigs"
          size="small"
          border
        >
          <el-table-column label="类型" prop="configType" width="100" />
          <el-table-column label="编码" prop="configCode" />
          <el-table-column label="引用字段" prop="referenceField" width="120" />
          <el-table-column label="状态" prop="status" width="100" />
          <el-table-column label="严重度" width="100">
            <template #default="{ row }">
              <el-tag :type="severityTag(row.severity)" size="small">{{ row.severity }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="visibleComputed = false">取消</el-button>
      <el-button
        type="warning"
        :loading="rolling"
        :disabled="loading"
        @click="confirmRollback"
      >
        确认回滚
      </el-button>
    </template>
  </el-dialog>
</template>
