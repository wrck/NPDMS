<script setup lang="ts">
/**
 * 低代码发布多级审批链配置页（借鉴 OutSystems LifeTime 多级审批）。
 *
 * <p>按 configType 筛选审批链列表，支持新建/编辑/删除审批链。
 * 编辑对话框中可视化维护级别列表（level/approverRole/name），可增删级别。</p>
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createApprovalChain,
  deleteApprovalChain,
  getApprovalChainList,
  parseLevels,
  serializeLevels,
  updateApprovalChain,
  type ApprovalLevel,
  type LowCodeApprovalChain
} from '@/api/lowcode-approval-chain'
import { getAllRoles, type RoleOption } from '@/api/system'

defineOptions({ name: 'ApprovalChainView' })

/** 支持的配置类型 */
const configTypeOptions = [
  { label: '表单 FORM', value: 'FORM' },
  { label: '列表 LIST', value: 'LIST' },
  { label: '实体 ENTITY', value: 'ENTITY' },
  { label: '微流 MICROFLOW', value: 'MICROFLOW' },
  { label: '连接器 CONNECTOR', value: 'CONNECTOR' },
  { label: '规则 RULE', value: 'RULE' },
  { label: '标签页 TAB', value: 'TAB' },
  { label: '关联页 RELATED_PAGE', value: 'RELATED_PAGE' }
]

const list = ref<LowCodeApprovalChain[]>([])
const loading = ref(false)
/** 筛选的 configType（空字符串表示全部） */
const filterConfigType = ref('')

/** 角色列表，用于 approverRole 下拉 */
const roles = ref<RoleOption[]>([])

/** 编辑对话框 */
const dialogVisible = ref(false)
const saving = ref(false)
const current = ref<LowCodeApprovalChain | null>(null)
/** 结构化级别列表（编辑器内部操作，保存时序列化为 levels JSON） */
const levelsArr = ref<ApprovalLevel[]>([])

const isEdit = computed(() => !!current.value?.id)

const filteredList = computed(() => {
  if (!filterConfigType.value) return list.value
  return list.value.filter((c) => c.configType === filterConfigType.value)
})

async function load() {
  loading.value = true
  try {
    list.value = await getApprovalChainList()
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  try {
    roles.value = await getAllRoles()
  } catch (e) {
    // 角色加载失败不阻断主流程，用户可手动输入 approverRole
    console.warn('加载角色列表失败：', e)
    roles.value = []
  }
}

function configTypeLabel(code: string): string {
  return configTypeOptions.find((o) => o.value === code)?.label ?? code
}

function levelSummary(chain: LowCodeApprovalChain): string {
  const levels = parseLevels(chain.levels)
  if (levels.length === 0) return '—'
  return levels.map((l) => `${l.level}. ${l.name}(${l.approverRole})`).join(' → ')
}

// ---------------- 新建 / 编辑 ----------------
function openNew() {
  current.value = {
    configType: 'FORM',
    name: '',
    levels: '[]',
    enabled: 1
  }
  levelsArr.value = []
  dialogVisible.value = true
}

function openEdit(row: LowCodeApprovalChain) {
  current.value = { ...row }
  levelsArr.value = parseLevels(row.levels)
  dialogVisible.value = true
}

// ---------------- 级别增删 ----------------
function addLevel() {
  const nextLevel = levelsArr.value.length + 1
  levelsArr.value.push({
    level: nextLevel,
    // 默认取第一个角色编码（RoleOption 接口字段为 roleCode）
    approverRole: roles.value[0]?.roleCode ?? '',
    name: `第${nextLevel}级审批`
  })
}

function removeLevel(idx: number) {
  levelsArr.value.splice(idx, 1)
  // 重新编号
  levelsArr.value.forEach((l, i) => (l.level = i + 1))
}

function moveLevel(idx: number, delta: -1 | 1) {
  const target = idx + delta
  if (target < 0 || target >= levelsArr.value.length) return
  const tmp = levelsArr.value[idx]
  levelsArr.value[idx] = levelsArr.value[target]
  levelsArr.value[target] = tmp
  // 重新编号
  levelsArr.value.forEach((l, i) => (l.level = i + 1))
}

// ---------------- 保存 ----------------
function validate(): boolean {
  const c = current.value
  if (!c) return false
  if (!c.name.trim()) {
    ElMessage.warning('请填写审批链名称')
    return false
  }
  if (levelsArr.value.length === 0) {
    ElMessage.warning('请至少添加一个审批级别')
    return false
  }
  for (const l of levelsArr.value) {
    if (!l.approverRole?.trim()) {
      ElMessage.warning(`第 ${l.level} 级审批角色不能为空`)
      return false
    }
    if (!l.name?.trim()) {
      ElMessage.warning(`第 ${l.level} 级名称不能为空`)
      return false
    }
  }
  return true
}

async function save(closeAfter = true) {
  if (!validate()) return
  const c = current.value
  if (!c) return
  // 序列化级别（按 level 排序）
  const sorted = [...levelsArr.value].sort((a, b) => a.level - b.level)
  const payload: LowCodeApprovalChain = {
    id: c.id,
    configType: c.configType,
    name: c.name,
    levels: serializeLevels(sorted),
    enabled: c.enabled ?? 1
  }
  saving.value = true
  try {
    if (isEdit.value && c.id) {
      await updateApprovalChain(c.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createApprovalChain(payload)
      ElMessage.success('创建成功')
    }
    await load()
    if (closeAfter) dialogVisible.value = false
  } catch (e: unknown) {
    ElMessage.error('保存失败：' + (e instanceof Error ? e.message : String(e)))
  } finally {
    saving.value = false
  }
}

// ---------------- 删除 ----------------
async function remove(row: LowCodeApprovalChain) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`确认删除审批链「${row.name}」？`, '确认', { type: 'warning' })
    await deleteApprovalChain(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch {
    /* cancelled or error */
  }
}

// ---------------- 启用/停用切换 ----------------
async function toggleEnabled(row: LowCodeApprovalChain) {
  if (!row.id) return
  const newEnabled = row.enabled === 1 ? 0 : 1
  try {
    await updateApprovalChain(row.id, { ...row, enabled: newEnabled })
    ElMessage.success(newEnabled === 1 ? '已启用' : '已停用')
    await load()
  } catch (e: unknown) {
    ElMessage.error('切换失败：' + (e instanceof Error ? e.message : String(e)))
  }
}

onMounted(() => {
  load()
  loadRoles()
})
</script>

<template>
  <div style="padding: 16px">
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>多级审批链配置</span>
          <div style="display: flex; gap: 8px; align-items: center">
            <el-select
              v-model="filterConfigType"
              placeholder="按配置类型筛选"
              clearable
              style="width: 200px"
            >
              <el-option v-for="o in configTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-button type="primary" @click="openNew">新建审批链</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="filteredList">
        <el-table-column label="名称" prop="name" min-width="140" show-overflow-tooltip />
        <el-table-column label="配置类型" width="160">
          <template #default="{ row }">
            <el-tag size="small">{{ configTypeLabel(row.configType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批级别" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">
            {{ levelSummary(row) }}
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled === 1"
              @change="toggleEnabled(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑审批链' : '新建审批链'"
      width="720px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form v-if="current" :model="current" label-width="100px">
        <el-form-item label="审批链名称" required>
          <el-input v-model="current.name" placeholder="如 FORM 三级审批" />
        </el-form-item>
        <el-form-item label="配置类型" required>
          <el-select v-model="current.configType" style="width: 100%" :disabled="isEdit">
            <el-option v-for="o in configTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch :model-value="current.enabled === 1" @update:model-value="current.enabled = $event ? 1 : 0" />
        </el-form-item>
        <el-divider content-position="left">审批级别</el-divider>
        <div v-if="levelsArr.length === 0" class="empty-levels">
          暂无审批级别，点击下方"添加级别"按钮新增
        </div>
        <div v-for="(l, idx) in levelsArr" :key="idx" class="level-row">
          <el-tag class="level-tag" type="info">{{ l.level }}</el-tag>
          <el-input
            v-model="l.name"
            placeholder="级别名称（如 主管审批）"
            style="width: 200px"
          />
          <el-select
            v-model="l.approverRole"
            placeholder="审批角色编码"
            filterable
            allow-create
            default-first-option
            style="width: 200px"
          >
            <el-option
              v-for="r in roles"
              :key="r.id"
              :label="`${r.roleName} (${r.roleCode})`"
              :value="r.roleCode ?? ''"
            />
          </el-select>
          <el-button-group>
            <el-button size="small" :disabled="idx === 0" @click="moveLevel(idx, -1)">↑</el-button>
            <el-button size="small" :disabled="idx === levelsArr.length - 1" @click="moveLevel(idx, 1)">↓</el-button>
          </el-button-group>
          <el-button size="small" type="danger" @click="removeLevel(idx)">删除</el-button>
        </div>
        <el-button type="primary" plain size="small" style="margin-top: 8px" @click="addLevel">
          + 添加级别
        </el-button>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save(true)">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.empty-levels {
  padding: 16px;
  color: var(--el-text-color-secondary);
  text-align: center;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  margin-bottom: 8px;
}

.level-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;

  .level-tag {
    width: 32px;
    text-align: center;
    justify-content: center;
  }
}
</style>
