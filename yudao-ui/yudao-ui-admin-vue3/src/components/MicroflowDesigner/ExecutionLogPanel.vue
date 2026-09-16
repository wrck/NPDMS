<script setup lang="ts">
/**
 * 微流执行日志可视化面板（借鉴 Joget APM）。
 *
 * <p>展示某次执行的节点级轨迹时间轴：</p>
 * <ul>
 *   <li>顶部下拉：选择历史 executionId（多次执行历史）</li>
 *   <li>时间轴：节点名 + 类型图标 + 开始时间 + 耗时(ms) + 状态徽章</li>
 *   <li>点击节点条目展开详情：inputs / outputs / variablesSnapshot / errorMessage</li>
 * </ul>
 *
 * <p>状态颜色：SUCCESS=绿、FAILED=红、RUNNING=蓝。面板可折叠/展开。</p>
 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getExecutionLogs,
  getRecentExecutionLogs,
  type MicroflowExecutionLog
} from '@/api/lowcode-microflow'

defineOptions({ name: 'MicroflowExecutionLogPanel' })

const props = defineProps<{
  /** 当前微流 ID（用于查询历史执行） */
  microflowId?: number
  /** 当前执行 ID（执行后由父组件传入） */
  executionId?: string
  /** 是否折叠 */
  collapsed?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:collapsed', v: boolean): void
  /** 点击日志条目时触发，父组件据 nodeId 在画布上高亮对应节点 */
  (e: 'highlight-node', nodeId: string): void
  /** 日志加载完成后触发，父组件据 status 在画布上同步节点状态 */
  (e: 'logs-loaded', logs: MicroflowExecutionLog[]): void
}>()

const innerCollapsed = ref(props.collapsed || false)
watch(
  () => props.collapsed,
  (v) => {
    innerCollapsed.value = v
  }
)

function toggleCollapse() {
  innerCollapsed.value = !innerCollapsed.value
  emit('update:collapsed', innerCollapsed.value)
}

const recentExecutions = ref<MicroflowExecutionLog[]>([])
const currentExecutionId = ref<string>(props.executionId || '')
const logs = ref<MicroflowExecutionLog[]>([])
const loading = ref(false)
/** 展开详情的 nodeId 集合 */
const expandedNodeIds = ref<Set<string>>(new Set())

watch(
  () => props.executionId,
  (v) => {
    if (v && v !== currentExecutionId.value) {
      currentExecutionId.value = v
      loadLogs(v)
    }
  }
)

watch(
  () => props.microflowId,
  async (id) => {
    if (id) await loadRecentExecutions(id)
  },
  { immediate: true }
)

async function loadRecentExecutions(microflowId: number) {
  try {
    const list = await getRecentExecutionLogs(microflowId, 20)
    // 同一 executionId 只保留首条作为代表（去重）
    const seen = new Set<string>()
    recentExecutions.value = (list || []).filter((l) => {
      if (seen.has(l.executionId)) return false
      seen.add(l.executionId)
      return true
    })
  } catch {
    recentExecutions.value = []
  }
}

async function loadLogs(executionId: string) {
  if (!executionId) {
    logs.value = []
    emit('logs-loaded', logs.value)
    return
  }
  loading.value = true
  try {
    logs.value = (await getExecutionLogs(executionId)) || []
  } catch (e) {
    ElMessage.error('加载执行日志失败')
    logs.value = []
  } finally {
    loading.value = false
    emit('logs-loaded', logs.value)
  }
}

async function onExecutionChange(v: string) {
  currentExecutionId.value = v
  await loadLogs(v)
}

function toggleExpand(nodeId: string) {
  const s = new Set(expandedNodeIds.value)
  if (s.has(nodeId)) s.delete(nodeId)
  else s.add(nodeId)
  expandedNodeIds.value = s
}

/** 点击日志条目：展开/收起详情，并通知父组件在画布上高亮对应节点 */
function onLogItemClick(log: MicroflowExecutionLog) {
  toggleExpand(log.nodeId)
  emit('highlight-node', log.nodeId)
}

function statusColor(status: string): string {
  if (status === 'SUCCESS') return '#67c23a'
  if (status === 'FAILED') return '#f56c6c'
  return '#409eff'
}

function statusTagType(status: string): 'success' | 'danger' | 'info' | 'primary' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'primary'
  return 'info'
}

function prettyJson(s?: string): string {
  if (!s) return ''
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch {
    return s
  }
}

function formatTime(s?: string): string {
  if (!s) return ''
  try {
    const d = new Date(s)
    return d.toLocaleString('zh-CN', { hour12: false })
  } catch {
    return s
  }
}

async function refresh() {
  if (props.microflowId) await loadRecentExecutions(props.microflowId)
  if (currentExecutionId.value) await loadLogs(currentExecutionId.value)
}
</script>

<template>
  <div class="execution-log-panel" :class="{ collapsed: innerCollapsed }">
    <div class="log-header" @click="toggleCollapse">
      <span class="log-title">
        <el-icon><DataLine /></el-icon>
        执行日志
      </span>
      <span v-if="!innerCollapsed && currentExecutionId" class="log-count">
        {{ logs.length }} 节点
      </span>
      <el-icon class="collapse-icon" :class="{ rotated: innerCollapsed }">
        <ArrowDown />
      </el-icon>
    </div>

    <div v-show="!innerCollapsed" class="log-body" v-loading="loading">
      <div class="log-toolbar">
        <el-select
          v-model="currentExecutionId"
          size="small"
          placeholder="选择执行 ID"
          filterable
          style="flex: 1"
          @change="onExecutionChange"
        >
          <el-option
            v-for="e in recentExecutions"
            :key="e.executionId"
            :label="`${e.executionId.substring(0, 8)} (${formatTime(e.startTime)})`"
            :value="e.executionId"
          />
        </el-select>
        <el-button size="small" link @click.stop="refresh">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>

      <div v-if="logs.length === 0" class="empty-tip">暂无执行日志</div>

      <el-scrollbar v-else class="log-scroll">
        <el-timeline>
          <el-timeline-item
            v-for="log in logs"
            :key="log.nodeId"
            :color="statusColor(log.status)"
            :timestamp="formatTime(log.startTime)"
            placement="top"
          >
            <div class="log-item" :class="`status-${log.status.toLowerCase()}`" @click="onLogItemClick(log)">
              <div class="log-item-header">
                <span class="node-name">{{ log.nodeId }}</span>
                <el-tag size="small" :type="statusTagType(log.status)">{{ log.nodeType }}</el-tag>
                <el-tag size="small" :type="statusTagType(log.status)" effect="dark">
                  {{ log.status }}
                </el-tag>
                <span v-if="log.durationMs !== undefined && log.durationMs !== null" class="duration">
                  {{ log.durationMs }} ms
                </span>
                <el-icon class="expand-icon" :class="{ rotated: expandedNodeIds.has(log.nodeId) }">
                  <ArrowRight />
                </el-icon>
              </div>

              <div v-if="expandedNodeIds.has(log.nodeId)" class="log-detail">
                <div v-if="log.errorMessage" class="detail-block error-block">
                  <div class="detail-label">错误信息</div>
                  <pre>{{ log.errorMessage }}</pre>
                </div>
                <div v-if="log.inputs" class="detail-block">
                  <div class="detail-label">输入</div>
                  <pre>{{ prettyJson(log.inputs) }}</pre>
                </div>
                <div v-if="log.outputs" class="detail-block">
                  <div class="detail-label">输出</div>
                  <pre>{{ prettyJson(log.outputs) }}</pre>
                </div>
                <div v-if="log.variablesSnapshot" class="detail-block">
                  <div class="detail-label">变量快照</div>
                  <pre>{{ prettyJson(log.variablesSnapshot) }}</pre>
                </div>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </el-scrollbar>
    </div>
  </div>
</template>

<style scoped lang="scss">
.execution-log-panel {
  background: #fff;
  border-top: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  height: 280px;
  transition: height 0.2s;
  flex-shrink: 0;

  &.collapsed {
    height: 36px;
  }
}

.log-header {
  height: 36px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  font-weight: 600;

  .log-title {
    display: flex;
    align-items: center;
    gap: 4px;
    color: #303133;
  }

  .log-count {
    font-size: 12px;
    font-weight: 400;
    color: #909399;
  }

  .collapse-icon {
    margin-left: auto;
    transition: transform 0.2s;
    &.rotated {
      transform: rotate(-90deg);
    }
  }
}

.log-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.log-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border-bottom: 1px solid #f5f5f5;
}

.log-scroll {
  flex: 1;
  padding: 10px;
}

.empty-tip {
  padding: 20px;
  text-align: center;
  color: #c0c4cc;
  font-size: 12px;
}

.log-item {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 6px 10px;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    border-color: #409eff;
    background: #f5f7fa;
  }

  &.status-failed {
    border-left: 3px solid #f56c6c;
  }
  &.status-success {
    border-left: 3px solid #67c23a;
  }
  &.status-running {
    border-left: 3px solid #409eff;
  }
}

.log-item-header {
  display: flex;
  align-items: center;
  gap: 6px;

  .node-name {
    font-weight: 600;
    color: #303133;
    font-size: 13px;
  }

  .duration {
    font-size: 12px;
    color: #909399;
    margin-left: auto;
  }

  .expand-icon {
    transition: transform 0.2s;
    &.rotated {
      transform: rotate(90deg);
    }
  }
}

.log-detail {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-block {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 6px;

  .detail-label {
    font-size: 11px;
    color: #909399;
    margin-bottom: 4px;
    font-weight: 600;
  }

  pre {
    margin: 0;
    font-size: 11px;
    color: #303133;
    white-space: pre-wrap;
    word-break: break-all;
    max-height: 160px;
    overflow-y: auto;
  }

  &.error-block {
    background: #fef0f0;
    pre {
      color: #f56c6c;
    }
  }
}
</style>
