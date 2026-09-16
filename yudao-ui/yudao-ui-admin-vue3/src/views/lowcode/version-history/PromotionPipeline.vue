<!-- src/views/lowcode/version-history/PromotionPipeline.vue -->
<script setup lang="ts">
/**
 * 晋升管道图（批次5-T2，借鉴 OutSystems LifeTime）。
 *
 * <p>横向三列布局：DEV → TEST → PROD，每列展示对应环境最新 ACTIVE 版本卡片。
 * 列间显示门禁检查状态图标（绿勾通过/红叉失败/警告部分通过），鼠标悬停显示失败原因。
 * 提供"晋升"按钮，点击先做门禁预检，通过后实际晋升。</p>
 */
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPipelineStatus,
  checkPromotionGate,
  promoteConfig,
  type PromotionPipelineDTO,
  type VersionBrief,
  type GateBrief,
  type GateResult
} from '@/api/lowcode-version'
import type { EpTagType } from '@/types'

interface Props {
  /** 初始 configCode 列表（逗号分隔字符串或数组） */
  configCodes?: string[]
  /** 初始选中的 configCode */
  initialCode?: string
}

const props = withDefaults(defineProps<Props>(), {
  configCodes: () => [],
  initialCode: ''
})

const emit = defineEmits<{
  (e: 'diff', payload: { configCode: string; version: number }): void
  (e: 'promoted', configCode: string): void
}>()

const pipelineData = ref<PromotionPipelineDTO[]>([])
const currentCode = ref(props.initialCode || '')
const loading = ref(false)

const ENV_COLUMNS: Array<{ code: string; name: string; tag: EpTagType }> = [
  { code: 'DEV', name: '开发环境', tag: 'info' },
  { code: 'TEST', name: '测试环境', tag: 'warning' },
  { code: 'PROD', name: '生产环境', tag: 'danger' }
]

const currentPipeline = computed<PromotionPipelineDTO | null>(() => {
  if (!currentCode.value || pipelineData.value.length === 0) return null
  return pipelineData.value.find(p => p.configCode === currentCode.value) || null
})

function getVersion(p: PromotionPipelineDTO | null, env: string): VersionBrief | null | undefined {
  if (!p) return null
  if (env === 'DEV') return p.devVersion
  if (env === 'TEST') return p.testVersion
  if (env === 'PROD') return p.prodVersion
  return null
}

function getGate(p: PromotionPipelineDTO | null, from: string, to: string): GateBrief | null {
  if (!p) return null
  if (from === 'DEV' && to === 'TEST') return p.devToTestGate
  if (from === 'TEST' && to === 'PROD') return p.testToProdGate
  return null
}

function gateIcon(gate: GateBrief | null): { icon: string; color: string; label: string } {
  if (!gate) return { icon: 'Minus', color: '#909399', label: '无门禁' }
  if (gate.passed) return { icon: 'CircleCheck', color: '#67c23a', label: '门禁通过' }
  return { icon: 'CircleClose', color: '#f56c6c', label: `门禁失败 (${gate.failureCount})` }
}

function gateTooltip(gate: GateBrief | null): string {
  if (!gate) return '无门禁数据'
  if (gate.passed) return '门禁通过，可执行晋升'
  return '门禁失败：\n' + gate.failureSummaries.map((s, i) => `${i + 1}. ${s}`).join('\n')
}

function fmtTime(t?: string): string {
  return t ? t.replace('T', ' ').slice(0, 16) : '-'
}

function statusTag(status?: string): EpTagType {
  return status === 'ACTIVE' ? 'success' : 'info'
}

async function loadPipeline() {
  if (props.configCodes.length === 0) {
    ElMessage.warning('请先输入配置编码')
    return
  }
  loading.value = true
  try {
    pipelineData.value = await getPipelineStatus(props.configCodes)
    if (!currentCode.value && pipelineData.value.length > 0) {
      currentCode.value = pipelineData.value[0].configCode
    }
  } catch (e) {
    ElMessage.error('加载管道状态失败')
    pipelineData.value = []
  } finally {
    loading.value = false
  }
}

async function promote(from: string, to: string) {
  if (!currentCode.value) return
  try {
    const gate: GateResult = await checkPromotionGate({
      sourceEnvironment: from,
      targetEnvironment: to,
      configCodes: [currentCode.value]
    })
    if (!gate.passed) {
      const reasons = gate.failures.map(f => `• ${f.rule}: ${f.reason}`).join('\n')
      await ElMessageBox.alert(
        `门禁检查未通过，禁止晋升：\n${reasons}`,
        '晋升门禁失败',
        { confirmButtonText: '知道了', type: 'error' }
      )
      return
    }
    await ElMessageBox.confirm(
      `确认将配置 '${currentCode.value}' 从 ${from} 晋升到 ${to}？`,
      '环境晋升',
      { confirmButtonText: '晋升', cancelButtonText: '取消', type: 'warning' }
    )
    await promoteConfig(to, [currentCode.value])
    ElMessage.success('晋升成功')
    emit('promoted', currentCode.value)
    await loadPipeline()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('晋升失败')
    }
  }
}

function diffVersion(env: string) {
  if (!currentCode.value) return
  const v = getVersion(currentPipeline.value, env)
  if (v) {
    emit('diff', { configCode: currentCode.value, version: v.version })
  }
}

watch(() => props.configCodes, () => {
  if (props.configCodes.length > 0) {
    loadPipeline()
  }
}, { immediate: true })
</script>

<template>
  <div class="promotion-pipeline" v-loading="loading">
    <!-- 顶部 configCode 选择器 -->
    <div class="pipeline-toolbar" v-if="pipelineData.length > 0">
      <el-select v-model="currentCode" placeholder="选择配置编码" size="small" style="width: 240px">
        <el-option
          v-for="p in pipelineData"
          :key="p.configCode"
          :label="`${p.configCode}${p.configType ? ' (' + p.configType + ')' : ''}`"
          :value="p.configCode"
        />
      </el-select>
      <el-button size="small" @click="loadPipeline">刷新</el-button>
    </div>

    <el-empty v-if="pipelineData.length === 0" description="请输入配置编码查询管道状态" :image-size="80" />

    <div v-else-if="currentPipeline" class="pipeline-grid">
      <div v-for="(col, idx) in ENV_COLUMNS" :key="col.code" class="pipeline-col">
        <div class="pipeline-header">
          <el-tag :type="col.tag" effect="dark" size="large">{{ col.code }}</el-tag>
          <span class="pipeline-col-name">{{ col.name }}</span>
        </div>

        <div class="pipeline-card-area">
          <div v-if="getVersion(currentPipeline, col.code)" class="version-card">
            <div class="version-card-header">
              <span class="version-no">v{{ getVersion(currentPipeline, col.code)?.version }}</span>
              <el-tag size="small" :type="statusTag(getVersion(currentPipeline, col.code)?.status)">
                {{ getVersion(currentPipeline, col.code)?.status }}
              </el-tag>
            </div>
            <div class="version-card-body">
              <div class="version-card-row" :title="getVersion(currentPipeline, col.code)?.changeLog">
                {{ getVersion(currentPipeline, col.code)?.changeLog || '（无变更说明）' }}
              </div>
              <div class="version-card-meta">
                <span>{{ getVersion(currentPipeline, col.code)?.createBy || '-' }}</span>
                <span>{{ fmtTime(getVersion(currentPipeline, col.code)?.createTime) }}</span>
              </div>
            </div>
            <div class="version-card-actions">
              <el-button link type="primary" size="small" @click="diffVersion(col.code)">对比</el-button>
            </div>
          </div>
          <el-empty v-else description="无版本" :image-size="40" />
        </div>

        <!-- 门禁 + 晋升按钮（最后一列不显示） -->
        <div v-if="idx < ENV_COLUMNS.length - 1" class="pipeline-gate">
          <el-tooltip :content="gateTooltip(getGate(currentPipeline, col.code, ENV_COLUMNS[idx + 1].code))" placement="top">
            <el-icon
              :color="gateIcon(getGate(currentPipeline, col.code, ENV_COLUMNS[idx + 1].code)).color"
              :size="22"
            >
              <component :is="gateIcon(getGate(currentPipeline, col.code, ENV_COLUMNS[idx + 1].code)).icon" />
            </el-icon>
          </el-tooltip>
          <span class="gate-label" :style="{ color: gateIcon(getGate(currentPipeline, col.code, ENV_COLUMNS[idx + 1].code)).color }">
            {{ gateIcon(getGate(currentPipeline, col.code, ENV_COLUMNS[idx + 1].code)).label }}
          </span>
          <el-button
            size="small"
            type="primary"
            :disabled="!getGate(currentPipeline, col.code, ENV_COLUMNS[idx + 1].code)?.passed"
            @click="promote(col.code, ENV_COLUMNS[idx + 1].code)"
          >
            晋升到{{ ENV_COLUMNS[idx + 1].code }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.promotion-pipeline {
  padding: 8px 0;
}

.pipeline-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 16px;
}

.pipeline-grid {
  display: flex;
  align-items: stretch;
  gap: 0;
  overflow-x: auto;
}

.pipeline-col {
  flex: 1 1 0;
  min-width: 220px;
  display: flex;
  flex-direction: column;
  padding: 0 12px;
}

.pipeline-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 2px solid var(--el-border-color-light);
}

.pipeline-col-name {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.pipeline-card-area {
  flex: 1;
  min-height: 120px;
}

.version-card {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-bg-color);
  padding: 10px 12px;
  transition: box-shadow 0.2s, transform 0.2s;
}

.version-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.version-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.version-no {
  font-weight: 600;
  color: var(--el-color-primary);
}

.version-card-row {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.version-card-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.version-card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 4px;
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 4px;
}

.pipeline-gate {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.gate-label {
  font-size: 12px;
  font-weight: 500;
}
</style>
