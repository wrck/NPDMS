<script setup lang="ts">
/**
 * 低代码规则设计器（可视化）。
 *
 * <p>列表页保留 el-table（code/name/type/status/操作）。新建/编辑打开可视化对话框：
 * 顶部规则元信息（type 选中后不可更改），中部按类型分发可视化编辑器——
 * 决策表编辑器 / 表达式编辑器 / LiteFlow 文本编辑，底部可折叠测试面板。</p>
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteRule,
  getRuleList,
  getRuleVersions,
  publishRuleWithVersion,
  rollbackRule,
  saveRule,
  type LowCodeRule
} from '@/api/lowcode-rule'
import type { LowCodeConfigVersion } from '@/api/lowcode-version'
import DecisionTableEditor from '@/components/RuleDesigner/DecisionTableEditor.vue'
import ExpressionRuleEditor from '@/components/RuleDesigner/ExpressionRuleEditor.vue'
import RuleTestPanel from '@/components/RuleDesigner/RuleTestPanel.vue'
import JsonTreeDiff from '@/components/JsonTreeDiff/index.vue'

defineOptions({ name: 'RuleDesignerView' })

const list = ref<LowCodeRule[]>([])
const current = ref<LowCodeRule | null>(null)
const dialogVisible = ref(false)
/** 列表「执行」入口打开时自动展开测试面板 */
const testExpanded = ref(false)

// ===================== 版本管理 =====================
/** 版本历史对话框 */
const versionDialogVisible = ref(false)
/** 版本历史加载中 */
const versionLoading = ref(false)
/** 版本历史列表 */
const versionList = ref<LowCodeConfigVersion[]>([])
/** 版本历史对应的规则（用于 Diff 取当前定义、回滚后刷新） */
const versionRule = ref<LowCodeRule | null>(null)
/** Diff 对话框 */
const diffDialogVisible = ref(false)
/** Diff 旧数据（历史版本快照） */
const diffOldData = ref<unknown>(null)
/** Diff 新数据（当前规则定义） */
const diffNewData = ref<unknown>(null)
/** Diff 标题（标注对比版本） */
const diffTitle = ref('')

const typeOptions = [
  { label: '决策表', value: 'DECISION_TABLE' },
  { label: '表达式', value: 'EXPRESSION' },
  { label: 'LiteFlow', value: 'LITEFLOW' }
] as const

const statusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' }
]

async function load() {
  list.value = await getRuleList()
}

function openNew() {
  current.value = {
    code: '',
    name: '',
    description: '',
    type: 'EXPRESSION',
    definition: '',
    ext: '',
    status: 'DRAFT'
  }
  testExpanded.value = false
  dialogVisible.value = true
}

function openEdit(row: LowCodeRule) {
  current.value = { ...row }
  testExpanded.value = false
  dialogVisible.value = true
}

/** 列表「执行」入口：打开编辑对话框并自动展开测试面板 */
function openExec(row: LowCodeRule) {
  current.value = { ...row }
  testExpanded.value = true
  dialogVisible.value = true
}

/**
 * 类型切换（仅新建时可切换）。切换时清空 definition 与 ext，避免不同类型格式串扰。
 * 已有 id 的规则（编辑态）类型锁定，不会触发本方法。
 */
function onTypeChange(t: LowCodeRule['type']) {
  if (!current.value) return
  current.value.type = t
  current.value.definition = ''
  if (t !== 'EXPRESSION') current.value.ext = ''
}

/** 当前规则是否处于编辑态（已有 id，类型锁定） */
const isEdit = computed(() => !!current.value?.id)

/** 表达式规则的 inputsSchema（从 ext 解析，传给测试面板生成模板） */
const testInputsSchema = computed(() => {
  if (!current.value || current.value.type !== 'EXPRESSION' || !current.value.ext) return undefined
  try {
    const obj = JSON.parse(current.value.ext) as { inputsSchema?: { name: string; type?: string }[] }
    return Array.isArray(obj.inputsSchema) ? obj.inputsSchema : undefined
  } catch {
    return undefined
  }
})

async function save() {
  if (!current.value) return
  if (!current.value.code.trim()) {
    ElMessage.warning('请填写规则编码')
    return
  }
  if (!current.value.name.trim()) {
    ElMessage.warning('请填写规则名称')
    return
  }
  await saveRule(current.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  await load()
}

async function remove(row: LowCodeRule) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`确认删除规则「${row.name}」？`, '确认', { type: 'warning' })
    await deleteRule(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch {
    /* cancelled or error */
  }
}

// ===================== 版本管理 =====================

/** 发布规则并生成版本快照 */
async function publish(row: LowCodeRule) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(
      `确认发布规则「${row.name}」？发布后将生成不可变版本快照。`,
      '规则发布',
      { type: 'warning' }
    )
    await publishRuleWithVersion(row.id)
    ElMessage.success('发布成功，已生成版本快照')
    await load()
  } catch {
    /* cancelled or error */
  }
}

/** 打开版本历史对话框 */
async function openVersionHistory(row: LowCodeRule) {
  if (!row.id) return
  versionRule.value = { ...row }
  versionDialogVisible.value = true
  versionLoading.value = true
  try {
    versionList.value = await getRuleVersions(row.id)
  } catch {
    ElMessage.error('加载版本历史失败')
    versionList.value = []
  } finally {
    versionLoading.value = false
  }
}

/** 解析快照/定义为可 Diff 数据：JSON 优先，否则返回原始字符串 */
function parseForDiff(raw: string | undefined): unknown {
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return raw
  }
}

/** 查看指定历史版本与当前定义的 Diff */
function showVersionDiff(version: LowCodeConfigVersion) {
  diffOldData.value = parseForDiff(version.snapshot)
  diffNewData.value = parseForDiff(versionRule.value?.definition)
  diffTitle.value = `版本 v${version.version} 与当前定义对比`
  diffDialogVisible.value = true
}

/** 回滚到指定历史版本 */
async function onRollbackVersion(version: LowCodeConfigVersion) {
  if (!versionRule.value?.id) return
  try {
    await ElMessageBox.confirm(
      `确认回滚到版本 v${version.version}？当前规则定义将被历史快照覆盖，并生成新的回滚版本。`,
      '版本回滚',
      { type: 'warning' }
    )
    await rollbackRule(versionRule.value.id, version.version)
    ElMessage.success('回滚成功')
    // 刷新版本列表与规则列表（回滚后规则定义已变更）
    await openVersionHistory(await refreshRule(versionRule.value.id))
  } catch {
    /* cancelled or error */
  }
}

/** 重新拉取单条规则，回滚后用于刷新 versionRule 与列表 */
async function refreshRule(id: number): Promise<LowCodeRule> {
  await load()
  const fresh = list.value.find((r) => r.id === id)
  return fresh ?? versionRule.value!
}

onMounted(load)
</script>

<template>
  <div style="padding: 16px">
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>规则设计器</span>
          <el-button type="primary" @click="openNew">新建规则</el-button>
        </div>
      </template>
      <el-table :data="list">
        <el-table-column label="编码" prop="code" min-width="140" show-overflow-tooltip />
        <el-table-column label="名称" prop="name" min-width="140" show-overflow-tooltip />
        <el-table-column label="类型" prop="type" width="140">
          <template #default="{ row }">
            <el-tag>{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="380">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="success" @click="openExec(row)">执行</el-button>
            <el-button size="small" type="primary" @click="publish(row)">发布</el-button>
            <el-button size="small" @click="openVersionHistory(row)">版本历史</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      title="规则设计器"
      width="1100px"
      top="5vh"
      :close-on-click-modal="false"
    >
      <div v-if="current" class="rule-editor">
        <!-- 顶部：规则元信息 -->
        <el-form label-width="80px" class="rule-meta-form">
          <div class="rule-meta-row">
            <el-form-item label="编码" class="rule-meta-item">
              <el-input v-model="current.code" :disabled="isEdit" placeholder="唯一编码" />
            </el-form-item>
            <el-form-item label="名称" class="rule-meta-item">
              <el-input v-model="current.name" placeholder="规则名称" />
            </el-form-item>
            <el-form-item label="类型" class="rule-meta-item rule-meta-type">
              <el-select
                :model-value="current.type"
                :disabled="isEdit"
                placeholder="选择类型"
                @update:model-value="(v: string) => onTypeChange(v as LowCodeRule['type'])"
              >
                <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态" class="rule-meta-item rule-meta-status">
              <el-select v-model="current.status">
                <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="描述">
            <el-input v-model="current.description" placeholder="规则描述（可选）" />
          </el-form-item>
        </el-form>

        <!-- 中部：按类型分发可视化编辑器 -->
        <div class="rule-editor-section">
          <!-- 决策表编辑器 -->
          <DecisionTableEditor
            v-if="current.type === 'DECISION_TABLE'"
            v-model="current.definition"
          />
          <!-- 表达式编辑器（包装 ExpressionEditor + inputsSchema 侧栏） -->
          <ExpressionRuleEditor
            v-else-if="current.type === 'EXPRESSION'"
            v-model="current.definition"
            v-model:ext="current.ext"
          />
          <!-- LiteFlow EL 文本编辑 -->
          <div v-else class="rule-liteflow-placeholder">
            <el-alert
              title="LiteFlow EL 编辑"
              type="info"
              :closable="false"
              show-icon
              description="后端通过 LiteFlow 2.15.0 执行 EL 表达式，组件内可通过 DefaultContext 读写上下文，执行结果取自 result 键。"
            />
            <el-input
              v-model="current.definition"
              type="textarea"
              :rows="10"
              placeholder="THEN(a, b, c)"
              class="rule-liteflow-text"
            />
          </div>
        </div>

        <!-- 底部：测试面板（可折叠） -->
        <RuleTestPanel
          :key="current.id ?? current.code ?? 'new-rule'"
          :rule-code="current.code"
          :rule-type="current.type"
          :inputs-schema="testInputsSchema"
          :default-expanded="testExpanded"
        />
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史对话框 -->
    <el-dialog
      v-model="versionDialogVisible"
      :title="`版本历史 — ${versionRule?.name ?? ''}`"
      width="900px"
      top="8vh"
    >
      <el-table v-loading="versionLoading" :data="versionList" row-key="version" size="small">
        <el-table-column label="版本" prop="version" width="70">
          <template #default="{ row }">
            <el-tag size="small">v{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变更说明" prop="changeLog" show-overflow-tooltip />
        <el-table-column label="操作人" prop="createBy" width="100" />
        <el-table-column label="时间" prop="createTime" width="160">
          <template #default="{ row }">
            {{ row.createTime?.replace('T', ' ').slice(0, 16) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="showVersionDiff(row)">查看 Diff</el-button>
            <el-button size="small" link type="warning" @click="onRollbackVersion(row)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!versionLoading && versionList.length === 0"
        description="暂无版本历史，发布规则后将生成版本快照"
        :image-size="60"
      />
      <template #footer>
        <el-button @click="versionDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 版本 Diff 对话框 -->
    <el-dialog
      v-model="diffDialogVisible"
      :title="diffTitle"
      width="900px"
      top="8vh"
    >
      <JsonTreeDiff :old-data="diffOldData" :new-data="diffNewData" />
      <template #footer>
        <el-button @click="diffDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.rule-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 78vh;
  overflow-y: auto;
  padding-right: 4px;
}

.rule-meta-form {
  margin-bottom: 0;
}

.rule-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0 16px;
}

.rule-meta-item {
  flex: 1;
  min-width: 200px;
  margin-bottom: 12px;
}

.rule-meta-type {
  max-width: 200px;
}

.rule-meta-status {
  max-width: 160px;
}

.rule-editor-section {
  min-height: 200px;
}

.rule-liteflow-placeholder {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rule-liteflow-text :deep(textarea) {
  font-family: 'SF Mono', Menlo, Consolas, 'Courier New', monospace;
  font-size: 13px;
}
</style>
