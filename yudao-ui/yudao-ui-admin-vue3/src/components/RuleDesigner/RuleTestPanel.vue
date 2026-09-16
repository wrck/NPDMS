<script setup lang="ts">
/**
 * 规则测试面板（可折叠）。
 *
 * <p>提供事实输入（JSON 文本框）、执行按钮、结果展示与最近 5 次执行历史（可重放）。
 * 决策表返回 hitActions 列表（result.actions），表达式返回求值结果（result.result）。</p>
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { executeRule } from '@/api/lowcode-rule'

defineOptions({ name: 'RuleTestPanel' })

const props = defineProps<{
  /** 待测规则编码（执行接口入参） */
  ruleCode: string
  /** 规则类型，用于结果展示区分 */
  ruleType: 'DECISION_TABLE' | 'EXPRESSION' | 'LITEFLOW'
  /** 可用输入变量（用于生成 facts 模板），来自表达式规则 inputsSchema */
  inputsSchema?: { name: string; type?: string }[]
  /** 初始是否展开（列表「执行」入口可设为 true） */
  defaultExpanded?: boolean
}>()

const collapsed = ref(!props.defaultExpanded)

function toggle() {
  collapsed.value = !collapsed.value
}

const factsText = ref('')

/** 依据 inputsSchema 生成 facts 模板，便于快速填入测试输入 */
function fillTemplate() {
  const schema = props.inputsSchema || []
  if (schema.length === 0) {
    factsText.value = '{\n  \n}'
    return
  }
  const tpl: Record<string, string> = {}
  schema.forEach((s) => {
    if (!s.name) return
    switch (s.type) {
      case 'number':
        tpl[s.name] = '0'
        break
      case 'boolean':
        tpl[s.name] = 'false'
        break
      default:
        tpl[s.name] = ''
    }
  })
  factsText.value = JSON.stringify(tpl, null, 2)
}

// ===================== 执行 + 历史 =====================

interface HistoryEntry {
  time: string
  facts: string
  result: unknown
  error?: string
}

const history = ref<HistoryEntry[]>([])
const loading = ref(false)
const currentResult = ref<unknown>(null)
const currentError = ref('')

/** 决策表命中动作列表（结果展示用） */
const hitActions = computed<unknown[]>(() => {
  if (props.ruleType !== 'DECISION_TABLE') return []
  const r = currentResult.value as { actions?: unknown[] } | null
  return r?.actions || []
})

/** 表达式求值结果（结果展示用） */
const exprResult = computed<unknown>(() => {
  if (props.ruleType === 'DECISION_TABLE') return null
  const r = currentResult.value as { result?: unknown } | null
  return r?.result
})

function pretty(v: unknown): string {
  if (v === null || v === undefined) return ''
  if (typeof v === 'string') return v
  try {
    return JSON.stringify(v, null, 2)
  } catch {
    return String(v)
  }
}

async function execute() {
  if (!props.ruleCode) {
    ElMessage.warning('请先填写规则编码')
    return
  }
  let facts: Record<string, unknown> = {}
  if (factsText.value.trim()) {
    try {
      facts = JSON.parse(factsText.value)
    } catch {
      ElMessage.error('输入事实 JSON 解析失败')
      return
    }
  }
  loading.value = true
  currentError.value = ''
  const entry: HistoryEntry = {
    time: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
    facts: factsText.value,
    result: null
  }
  try {
    const result = await executeRule(props.ruleCode, facts)
    currentResult.value = result
    entry.result = result
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    currentError.value = msg
    entry.error = msg
  } finally {
    loading.value = false
    // 历史保留最近 5 次
    history.value.unshift(entry)
    if (history.value.length > 5) history.value.length = 5
  }
}

/** 重放历史：将该次 facts 填回输入框 */
function replay(entry: HistoryEntry) {
  factsText.value = entry.facts
}
</script>

<template>
  <div class="rule-test-panel" :class="{ collapsed }">
    <div class="rtp-header" @click="toggle">
      <span class="rtp-title">
        <el-icon><Cpu /></el-icon>
        测试面板
      </span>
      <el-icon class="rtp-collapse-icon" :class="{ rotated: collapsed }">
        <ArrowDown />
      </el-icon>
    </div>

    <div v-show="!collapsed" class="rtp-body">
      <div class="rtp-input-row">
        <span class="rtp-label">输入事实 (Facts JSON)</span>
        <el-button size="small" link @click="fillTemplate">生成模板</el-button>
      </div>
      <el-input
        v-model="factsText"
        type="textarea"
        :rows="4"
        placeholder='{"field":"value"}'
        class="rtp-facts"
      />
      <div class="rtp-actions">
        <el-button
          size="small"
          type="primary"
          :loading="loading"
          :disabled="!ruleCode"
          @click="execute"
        >
          执行
        </el-button>
        <span v-if="!ruleCode" class="rtp-hint">请先填写规则编码</span>
      </div>

      <!-- 结果展示 -->
      <div v-if="currentError" class="rtp-result rtp-error">
        <div class="rtp-result-label">执行错误</div>
        <pre>{{ currentError }}</pre>
      </div>
      <div v-else-if="currentResult !== null" class="rtp-result">
        <div class="rtp-result-label">
          {{ ruleType === 'DECISION_TABLE' ? `命中动作 (${hitActions.length})` : '求值结果' }}
        </div>
        <pre v-if="ruleType === 'DECISION_TABLE'">{{ pretty(hitActions) }}</pre>
        <pre v-else>{{ pretty(exprResult) }}</pre>
      </div>

      <!-- 历史 -->
      <div v-if="history.length" class="rtp-history">
        <div class="rtp-history-title">最近 {{ history.length }} 次执行（点击重放）</div>
        <div
          v-for="(h, i) in history"
          :key="i"
          class="rtp-history-item"
          :class="{ 'has-error': !!h.error }"
          @click="replay(h)"
        >
          <span class="rtp-history-time">{{ h.time }}</span>
          <el-tag size="small" :type="h.error ? 'danger' : 'success'">
            {{ h.error ? '失败' : '成功' }}
          </el-tag>
          <span class="rtp-history-facts">{{ h.facts || '(空)' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.rule-test-panel {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-bg-color);
  display: flex;
  flex-direction: column;
  transition: all 0.2s;
}

.rtp-header {
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.rtp-title {
  display: flex;
  align-items: center;
  gap: 4px;
}

.rtp-collapse-icon {
  transition: transform 0.2s;
}

.rtp-collapse-icon.rotated {
  transform: rotate(-90deg);
}

.rtp-body {
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rtp-input-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rtp-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.rtp-facts :deep(textarea) {
  font-family: 'SF Mono', Menlo, Consolas, 'Courier New', monospace;
  font-size: 12px;
}

.rtp-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rtp-hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.rtp-result {
  background: var(--el-fill-color-light);
  border-radius: 4px;
  padding: 8px;
}

.rtp-result.rtp-error {
  background: #fef0f0;
}

.rtp-result-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.rtp-error .rtp-result-label {
  color: var(--el-color-danger);
}

.rtp-result pre {
  margin: 0;
  font-size: 12px;
  font-family: 'SF Mono', Menlo, Consolas, 'Courier New', monospace;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
  color: var(--el-text-color-primary);
}

.rtp-history {
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 6px;
}

.rtp-history-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.rtp-history-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  border-radius: 3px;
  cursor: pointer;
  font-size: 12px;
  transition: background 0.15s;
}

.rtp-history-item:hover {
  background: var(--el-fill-color);
}

.rtp-history-item.has-error .rtp-history-facts {
  color: var(--el-color-danger);
}

.rtp-history-time {
  color: var(--el-text-color-secondary);
  font-family: 'SF Mono', Menlo, Consolas, 'Courier New', monospace;
  flex-shrink: 0;
}

.rtp-history-facts {
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
</style>
