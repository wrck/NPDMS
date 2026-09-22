<template>
  <el-drawer
    v-model="visible"
    :title="form.id ? '设备详情' : '新增设备'"
    size="min(1200px, 96vw)"
    :close-on-click-modal="false"
    :before-close="beforeClose"
    @closed="$emit('closed')"
  >
    <div class="form-intro">
      <div class="form-intro-copy">
        <div class="form-intro-title">{{ form.name || '新设备档案' }}</div>
        <div class="field-help">{{ form.sn || '建立设备基础档案' }}</div>
      </div>
      <el-tag type="info" size="small">{{ readonly ? '只读' : form.id ? '维护' : '新增' }}</el-tag>
    </div>
    <div v-if="$slots.actions && form.id" class="device-actions"><slot name="actions"></slot></div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="设备信息" name="profile">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="device-form"
          :disabled="readonly || saving"
          :hide-required-asterisk="readonly"
        >
          <div class="section-heading">基础信息</div>
          <div class="form-grid">
            <el-form-item label="序列号" prop="sn">
              <el-input v-model="form.sn" :disabled="!!form.id" />
            </el-form-item>
            <el-form-item label="设备名称" prop="name"
              ><el-input v-model="form.name"
            /></el-form-item>
            <el-form-item label="设备型号"><el-input v-model="form.productModel" /></el-form-item>
            <el-form-item label="所属客户">
              <PmsEntitySelect
                v-model="form.customerId"
                :api="CustomerApi.getCustomerPage"
                :label-field="['code', 'name']"
                value-field="id"
                query-field="name"
                :disabled="readonly || saving"
                placeholder="请选择客户"
              />
            </el-form-item>
            <el-form-item label="所属项目">
              <PmsEntitySelect
                v-model="form.projectId"
                :api="ProjectApi.getProjectPage"
                label-field="projectName"
                value-field="id"
                query-field="projectName"
                placeholder="请选择项目"
                :disabled="projectLocked || readonly || saving"
              />
            </el-form-item>
          </div>
          <el-alert type="info" :closable="false" show-icon class="mb-16px">
            设备当前位置由安装完成动作生效，此处仅维护设备档案。
          </el-alert>
          <div class="section-heading">保修信息</div>
          <div class="form-grid">
            <el-form-item label="保修开始日期">
              <el-date-picker
                v-model="form.warrantyStartDate"
                type="date"
                value-format="YYYY-MM-DD"
                class="field-control"
              />
            </el-form-item>
            <el-form-item label="保修结束日期">
              <el-date-picker
                v-model="form.warrantyEndDate"
                type="date"
                value-format="YYYY-MM-DD"
                class="field-control"
              />
            </el-form-item>
          </div>
          <div class="section-heading">补充信息</div>
          <el-form-item label="备注"
            ><el-input v-model="form.remark" type="textarea"
          /></el-form-item>
          <template v-if="!form.id">
            <el-form-item label="补录原因" prop="manualReason">
              <el-input
                v-model="form.manualReason"
                type="textarea"
                maxlength="100"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="补录证据" prop="manualEvidence">
              <el-input
                v-model="form.manualEvidence"
                type="textarea"
                maxlength="300"
                show-word-limit
                placeholder="填写可追溯的设备资料或现场记录说明"
              />
            </el-form-item>
            <el-alert type="info" :closable="false"
              >人工补录设备将标记为待对账，原因和证据保存在创建历史中。</el-alert
            >
          </template>
        </el-form>
      </el-tab-pane>
      <slot name="detail"></slot>
    </el-tabs>
    <template #footer>
      <div class="drawer-footer">
        <span class="field-help">{{
          readonly ? '当前账号仅可查看设备档案' : '保存仅更新设备档案，业务操作按独立权限执行'
        }}</span>
        <div>
          <el-button :disabled="saving" @click="beforeClose(() => (visible = false))">{{
            readonly ? '关闭' : '取消'
          }}</el-button>
          <el-button v-if="!readonly" type="primary" :loading="saving" @click="save"
            >保存设备信息</el-button
          >
        </div>
      </div>
    </template>
  </el-drawer>
</template>
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { checkPermi } from '@/utils/permission'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import type { DeviceArchiveSaveReqVO, DeviceArchiveVO } from '@/api/pms/asset/device/archive'
import * as ProjectApi from '@/api/pms/project/projects'
import * as CustomerApi from '@/api/pms/customer'

const props = defineProps<{ lockedProjectId?: number | string }>()
const emit = defineEmits<{ success: []; closed: [] }>()
const readonly = computed(() => !checkPermi([form.id ? 'pms:device:update' : 'pms:device:create']))
const message = useMessage()
const visible = ref(false)
const activeTab = ref('profile')
const saving = ref(false)
const formRef = ref()
const form = reactive<DeviceArchiveSaveReqVO>({
  sn: '',
  name: ''
})
const rules = {
  sn: [{ required: true, message: '请输入序列号' }],
  name: [{ required: true, message: '请输入设备名称' }],
  manualReason: [{ required: true, whitespace: true, message: '请输入补录原因' }],
  manualEvidence: [{ required: true, whitespace: true, message: '请输入补录证据' }]
}
const projectLocked = computed(() => props.lockedProjectId != null && props.lockedProjectId !== '')
const lockedProjectNumber = computed(() =>
  projectLocked.value ? Number(props.lockedProjectId) : undefined
)

let baseline = ''
const beforeClose = async (done: () => void) => {
  if (saving.value) return
  if (!readonly.value && baseline !== JSON.stringify(form)) {
    try {
      await message.confirm('设备信息尚未保存，确定放弃本次修改？')
    } catch {
      return
    }
  }
  done()
}
const open = (row?: DeviceArchiveVO) => {
  activeTab.value = 'profile'
  Object.assign(
    form,
    {
      id: undefined,
      sn: '',
      name: '',
      productModel: '',
      customerId: undefined,
      projectId: lockedProjectNumber.value,
      warrantyStartDate: undefined,
      warrantyEndDate: undefined,
      remark: '',
      manualReason: '',
      manualEvidence: ''
    },
    row || {},
    lockedProjectNumber.value != null ? { projectId: lockedProjectNumber.value } : {}
  )
  baseline = JSON.stringify(form)
  visible.value = true
}
const save = async () => {
  if (readonly.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    form.id
      ? await DeviceArchiveApi.updateDeviceArchive(form.id, form)
      : await DeviceArchiveApi.createDeviceArchive(form)
    message.success('保存成功')
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>
<style scoped>
.form-intro {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 16px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.form-intro-copy {
  flex: 1;
  min-width: 0;
}

.form-intro-title {
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
  overflow-wrap: anywhere;
}

.field-help {
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
}

.device-actions {
  margin-bottom: 16px;
}

.field-control {
  width: 100%;
}

.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.section-heading {
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
  line-height: 24px;
  color: var(--el-text-color-primary);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.device-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

@media (width <= 600px) {
  .drawer-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
