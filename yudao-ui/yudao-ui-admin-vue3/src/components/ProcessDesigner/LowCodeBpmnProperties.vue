<!-- src/components/ProcessDesigner/LowCodeBpmnProperties.vue -->
<script setup lang="ts">
/**
 * 低代码 BPMN 属性面板（自研 Element Plus 表单）。
 *
 * <p>借鉴钉钉宜搭 SLA / Appian Process Modeler：为 UserTask 节点提供低代码
 * 专属属性编辑——表单绑定、审批人、候选用户/组、超时处理、完成回调微流、
 * SLA 时长与升级微流。属性以 lowcode:config 扩展元素存储于 extensionElements。</p>
 *
 * <p>非 UserTask 元素回退展示基本属性（id/name）；无选中时展示空提示。</p>
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type BpmnModeler from 'bpmn-js/lib/Modeler'
import type { ModdleElement } from 'bpmn-js/lib/model/Types'
import { listForms, type LowCodeFormConfig } from '@/api/lowcode'
import { getMicroflowList, type LowCodeMicroflow } from '@/api/lowcode-microflow'
import {
  getElementId,
  getElementName,
  getLowCodeConfig,
  getLowCodeProperty,
  isUserTask,
  setLowCodeProperty
} from './bpmn-helper'

defineOptions({ name: 'LowCodeBpmnProperties' })

const props = defineProps<{
  element: ModdleElement | null
  modeler: BpmnModeler | null
}>()

interface OptionItem {
  label: string
  value: string
}

const forms = ref<OptionItem[]>([])
const microflows = ref<OptionItem[]>([])

const timeoutUnitOptions: OptionItem[] = [
  { label: '分钟', value: 'MINUTES' },
  { label: '小时', value: 'HOURS' },
  { label: '天', value: 'DAYS' }
]

const timeoutHandlerOptions: OptionItem[] = [
  { label: '自动通过', value: 'AUTO_APPROVE' },
  { label: '自动拒绝', value: 'AUTO_REJECT' },
  { label: '通知审批人', value: 'NOTIFY' },
  { label: '升级处理', value: 'ESCALATE' }
]

/** 表单模型（与 lowcode:config 属性一一对应） */
const form = ref({
  id: '',
  name: '',
  formCode: '',
  assignee: '',
  candidateUsers: '',
  candidateGroups: '',
  timeoutDuration: '',
  timeoutUnit: 'HOURS',
  timeoutHandler: 'NOTIFY',
  onCompleteMicroflow: '',
  onCreateMicroflow: '',
  onAssignMicroflow: '',
  slaDuration: '',
  slaUnit: 'HOURS',
  slaEscalationMicroflow: ''
})

const isUserTaskEl = computed(() => isUserTask(props.element))

/** 加载表单与微流下拉选项 */
async function loadOptions() {
  try {
    const page = await listForms({ page: 1, size: 1000 })
    forms.value = (page.records || []).map((f: LowCodeFormConfig) => ({
      label: f.name,
      value: f.code
    }))
  } catch {
    forms.value = []
  }
  try {
    const list = await getMicroflowList()
    microflows.value = (list || []).map((m: LowCodeMicroflow) => ({
      label: m.name,
      value: m.code
    }))
  } catch {
    microflows.value = []
  }
}

/** 从当前元素读取属性填充表单 */
function loadFromElement() {
  const el = props.element
  if (!el) {
    resetForm()
    return
  }
  form.value.id = getElementId(el)
  form.value.name = getElementName(el)

  const bo = (el.businessObject as ModdleElement) || el
  const moddle = props.modeler?.get<ModdleElement>('moddle')
  if (!moddle) {
    resetForm()
    return
  }

  if (isUserTask(el)) {
    form.value.formCode = getLowCodeProperty(bo, moddle, 'formCode')
    form.value.assignee = getLowCodeProperty(bo, moddle, 'assignee')
    form.value.candidateUsers = getLowCodeProperty(bo, moddle, 'candidateUsers')
    form.value.candidateGroups = getLowCodeProperty(bo, moddle, 'candidateGroups')
    form.value.timeoutDuration = getLowCodeProperty(bo, moddle, 'timeoutDuration')
    form.value.timeoutUnit = getLowCodeProperty(bo, moddle, 'timeoutUnit') || 'HOURS'
    form.value.timeoutHandler = getLowCodeProperty(bo, moddle, 'timeoutHandler') || 'NOTIFY'
    form.value.onCompleteMicroflow = getLowCodeProperty(bo, moddle, 'onCompleteMicroflow')
    form.value.onCreateMicroflow = getLowCodeProperty(bo, moddle, 'onCreateMicroflow')
    form.value.onAssignMicroflow = getLowCodeProperty(bo, moddle, 'onAssignMicroflow')
    form.value.slaDuration = getLowCodeProperty(bo, moddle, 'slaDuration')
    form.value.slaUnit = getLowCodeProperty(bo, moddle, 'slaUnit') || 'HOURS'
    form.value.slaEscalationMicroflow = getLowCodeProperty(bo, moddle, 'slaEscalationMicroflow')
  } else {
    resetLowCodeFields()
  }
}

function resetLowCodeFields() {
  form.value.formCode = ''
  form.value.assignee = ''
  form.value.candidateUsers = ''
  form.value.candidateGroups = ''
  form.value.timeoutDuration = ''
  form.value.timeoutUnit = 'HOURS'
  form.value.timeoutHandler = 'NOTIFY'
  form.value.onCompleteMicroflow = ''
  form.value.onCreateMicroflow = ''
  form.value.onAssignMicroflow = ''
  form.value.slaDuration = ''
  form.value.slaUnit = 'HOURS'
  form.value.slaEscalationMicroflow = ''
}

function resetForm() {
  form.value.id = ''
  form.value.name = ''
  resetLowCodeFields()
}

/** 写入基本属性（id/name）到 businessObject */
function updateBase() {
  const el = props.element
  const modeler = props.modeler
  if (!el || !modeler) return
  const modeling = modeler.get<{ updateProperties: (e: ModdleElement, p: Record<string, unknown>) => void }>('modeling')
  try {
    modeling.updateProperties(el, {
      name: form.value.name,
      id: form.value.id
    })
  } catch (e) {
    console.error('[bpmn-props] update base failed:', e)
  }
}

/** 写入单个低代码属性到 lowcode:config 扩展元素 */
function updateLowCode(name: string) {
  const el = props.element
  const modeler = props.modeler
  if (!el || !modeler || !isUserTask(el)) return
  const bo = (el.businessObject as ModdleElement) || el
  const moddle = modeler.get<ModdleElement>('moddle')
  const modeling = modeler.get<{
    updateModdleProperties: (e: ModdleElement, m: ModdleElement, p: Record<string, unknown>) => void
  }>('modeling')
  try {
    // 先确保 lowcode:config 存在（setLowCodeProperty 会按需创建）
    setLowCodeProperty(bo, moddle, name, form.value[name as keyof typeof form.value] as string)
    // 通过 modeling 命令触发变更事件 + 撤销栈记录
    const config = getLowCodeConfig(bo, moddle, false)
    if (config) {
      modeling.updateModdleProperties(el, config, {
        [name]: form.value[name as keyof typeof form.value] as string || undefined
      })
    }
  } catch (e) {
    console.error('[bpmn-props] update lowcode failed:', e)
  }
}

watch(() => props.element, loadFromElement, { immediate: true })

onMounted(loadOptions)

function copyNodeId() {
  if (!form.value.id) return
  navigator.clipboard?.writeText(form.value.id).then(
    () => ElMessage.success('节点 ID 已复制'),
    () => ElMessage.warning('复制失败，请手动选择复制')
  )
}
</script>

<template>
  <div class="lowcode-bpmn-properties">
    <div class="props-header">属性面板</div>

    <div v-if="!element" class="props-empty">请在画布上选中一个节点查看其属性</div>

    <el-form v-else label-position="top" size="small" class="props-form">
      <!-- 基本属性 -->
      <el-divider content-position="left">基本属性</el-divider>
      <el-form-item label="节点 ID">
        <el-input :model-value="form.id" readonly>
          <template #append>
            <el-button @click="copyNodeId">复制</el-button>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item label="节点名称">
        <el-input v-model="form.name" @change="updateBase" placeholder="如：部门经理审批" />
      </el-form-item>

      <template v-if="isUserTaskEl">
        <!-- 表单绑定 -->
        <el-divider content-position="left">表单绑定</el-divider>
        <el-form-item label="绑定表单 (formCode)">
          <el-select
            v-model="form.formCode"
            placeholder="选择该节点审批表单"
            filterable
            clearable
            @change="updateLowCode('formCode')"
          >
            <el-option v-for="f in forms" :key="f.value" :label="f.label" :value="f.value" />
          </el-select>
        </el-form-item>

        <!-- 审批人 -->
        <el-divider content-position="left">审批人</el-divider>
        <el-form-item label="审批人 (assignee)">
          <el-input
            v-model="form.assignee"
            placeholder="用户 ID，如 1001"
            @change="updateLowCode('assignee')"
          />
        </el-form-item>
        <el-form-item label="候选用户 (candidateUsers)">
          <el-input
            v-model="form.candidateUsers"
            placeholder="多个以逗号分隔，如 1001,1002"
            @change="updateLowCode('candidateUsers')"
          />
        </el-form-item>
        <el-form-item label="候选组 (candidateGroups)">
          <el-input
            v-model="form.candidateGroups"
            placeholder="多个以逗号分隔，如 finance,manager"
            @change="updateLowCode('candidateGroups')"
          />
        </el-form-item>

        <!-- 超时 -->
        <el-divider content-position="left">超时处理</el-divider>
        <el-form-item label="超时时长">
          <el-input-number
            v-model="form.timeoutDuration"
            :min="0"
            controls-position="right"
            style="width: 60%"
            @change="updateLowCode('timeoutDuration')"
          />
          <el-select
            v-model="form.timeoutUnit"
            style="width: 40%"
            @change="updateLowCode('timeoutUnit')"
          >
            <el-option v-for="o in timeoutUnitOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="超时处理策略">
          <el-select v-model="form.timeoutHandler" @change="updateLowCode('timeoutHandler')">
            <el-option v-for="o in timeoutHandlerOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>

        <!-- 回调微流 -->
        <el-divider content-position="left">回调微流</el-divider>
        <el-form-item label="创建时回调微流 (onCreate)">
          <el-select
            v-model="form.onCreateMicroflow"
            placeholder="任务创建时触发的微流"
            filterable
            clearable
            @change="updateLowCode('onCreateMicroflow')"
          >
            <el-option v-for="m in microflows" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="分配时回调微流 (onAssign)">
          <el-select
            v-model="form.onAssignMicroflow"
            placeholder="任务分配时触发的微流"
            filterable
            clearable
            @change="updateLowCode('onAssignMicroflow')"
          >
            <el-option v-for="m in microflows" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="完成时回调微流 (onComplete)">
          <el-select
            v-model="form.onCompleteMicroflow"
            placeholder="任务完成时触发的微流"
            filterable
            clearable
            @change="updateLowCode('onCompleteMicroflow')"
          >
            <el-option v-for="m in microflows" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>

        <!-- SLA -->
        <el-divider content-position="left">SLA（服务等级）</el-divider>
        <el-form-item label="SLA 时长">
          <el-input-number
            v-model="form.slaDuration"
            :min="0"
            controls-position="right"
            style="width: 60%"
            @change="updateLowCode('slaDuration')"
          />
          <el-select v-model="form.slaUnit" style="width: 40%" @change="updateLowCode('slaUnit')">
            <el-option v-for="o in timeoutUnitOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="SLA 超时升级微流">
          <el-select
            v-model="form.slaEscalationMicroflow"
            placeholder="SLA 超时触发的升级微流"
            filterable
            clearable
            @change="updateLowCode('slaEscalationMicroflow')"
          >
            <el-option v-for="m in microflows" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
      </template>

      <div v-else class="props-tip">
        当前节点类型不支持低代码专属属性，仅可编辑基本属性。
      </div>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
.lowcode-bpmn-properties {
  width: 100%;
  height: 100%;
  background: #fff;
  display: flex;
  flex-direction: column;
  font-size: 12px;
}

.props-header {
  padding: 10px 12px;
  font-weight: 600;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
}

.props-empty {
  padding: 40px 16px;
  text-align: center;
  color: #909399;
  line-height: 1.8;
}

.props-tip {
  padding: 8px 12px;
  color: #909399;
  background: #f4f4f5;
  border-radius: 4px;
  margin: 8px 0;
}

.props-form {
  padding: 8px 12px;
  overflow-y: auto;
  flex: 1;

  :deep(.el-form-item) {
    margin-bottom: 12px;
  }

  :deep(.el-form-item__label) {
    font-size: 12px;
    color: #606266;
    padding-bottom: 4px;
    line-height: 1.5;
  }

  :deep(.el-divider__text) {
    font-size: 12px;
    color: #409eff;
  }

  :deep(.el-select) {
    width: 100%;
  }
}
</style>
