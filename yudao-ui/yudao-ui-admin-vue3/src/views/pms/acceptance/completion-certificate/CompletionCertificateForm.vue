<template>
  <el-form
    ref="formRef"
    :model="model"
    :rules="rules"
    :disabled="disabled"
    label-position="top"
    size="large"
  >
    <div class="completion-form-scroll" tabindex="0" aria-label="完工证明填写表，可横向滚动">
      <table class="completion-form-table">
        <caption class="sr-only">完工证明</caption>
        <colgroup><col class="label-column" /><col /><col class="label-column" /><col /></colgroup>
        <tbody>
          <tr>
            <th scope="row">项目名称<span class="required"> *</span></th>
            <td colspan="3">
              <el-form-item prop="projectId">
                <PmsEntitySelect
                  v-if="!projectId"
                  v-model="model.projectId"
                  :api="ProjectApi.getProjectPage"
                  label-field="projectName"
                  value-field="id"
                  query-field="projectName"
                  placeholder="请选择项目"
                  :disabled="!!model.id"
                  aria-label="项目名称"
                  @change="emit('project-change', $event)"
                />
                <el-input
                  v-else
                  :model-value="projectName || model.name"
                  readonly
                  aria-label="项目名称"
                />
              </el-form-item>
            </td>
          </tr>
          <tr
            ><th scope="row">客户单位</th
            ><td colspan="3"
              ><el-form-item prop="customerUnit"
                ><el-input
                  v-model="model.customerUnit"
                  aria-label="客户单位"
                  placeholder="选择项目后自动带入，可修改" /></el-form-item></td
          ></tr>
          <tr>
            <th scope="row">合同号</th
            ><td
              ><el-form-item prop="contractNo"
                ><el-input
                  v-model="model.contractNo"
                  aria-label="合同号"
                  placeholder="自动带入，可修改" /></el-form-item
            ></td>
            <th scope="row">工程服务类型<span class="required"> *</span></th
            ><td
              ><el-form-item prop="serviceType"
                ><el-radio-group v-model="model.serviceType" aria-label="工程服务类型"
                  ><el-radio value="工程实施">工程实施</el-radio
                  ><el-radio value="工程督导">工程督导</el-radio></el-radio-group
                ></el-form-item
              ></td
            >
          </tr>
          <tr>
            <th scope="row">迪普工程师</th
            ><td
              ><el-form-item prop="engineerName"
                ><PmsEntitySelect
                  v-model="model.engineerName"
                  :api="UserApi.getUserPage"
                  label-field="nickname"
                  value-field="nickname"
                  query-field="nickname"
                  placeholder="默认当前用户，可修改"
                  aria-label="迪普工程师"
                  @change="onEngineerChange" /></el-form-item
            ></td>
            <th scope="row">联系方式</th
            ><td
              ><el-form-item prop="engineerContact"
                ><el-input
                  v-model="model.engineerContact"
                  aria-label="联系方式"
                  placeholder="随工程师带入，可修改" /></el-form-item
            ></td>
          </tr>
          <tr v-for="(item, index) in serviceItems" :key="item.field" class="cert-service-row">
            <th v-if="index === 0" scope="rowgroup" :rowspan="serviceItems.length">工程服务内容</th>
            <th colspan="2" scope="row" class="service-label">{{ item.label }}</th>
            <td
              ><el-form-item :prop="item.field"
                ><el-radio-group v-model="model[item.field]" :aria-label="item.label"
                  ><el-radio
                    v-for="choice in ['是', '否', '不涉及']"
                    :key="choice"
                    :value="choice"
                    >{{ choice }}</el-radio
                  ></el-radio-group
                ></el-form-item
              ></td
            >
          </tr>
          <tr>
            <th scope="row">设备名称和数量</th>
            <td colspan="3">
              <div class="device-table">
                <div class="device-row device-header"
                  ><span>设备名称</span><span>数量</span><span></span
                ></div>
                <div v-for="(device, index) in devices" :key="index" class="device-row">
                  <el-input
                    v-model="device.deviceType"
                    :aria-label="`第${index + 1}行设备名称`"
                    placeholder="设备名称"
                  />
                  <el-input-number
                    v-model="device.quantity"
                    :min="1"
                    controls-position="right"
                    class="!w-full"
                    :aria-label="`第${index + 1}行设备数量`"
                  />
                  <el-button link type="danger" @click="emit('remove-device', index)"
                    >删除</el-button
                  >
                </div>
                <el-button link type="primary" @click="emit('add-device')"
                  ><Icon icon="ep:plus" />添加一行</el-button
                >
                <p class="template-hint">按项目设备清单的产品名称和设备数量带入，可调整。</p>
              </div>
            </td>
          </tr>
          <tr
            ><th scope="row">完工时间</th
            ><td colspan="3"
              ><el-form-item prop="completionDate"
                ><el-date-picker
                  v-model="model.completionDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  class="!w-full"
                  aria-label="完工时间" /></el-form-item></td
          ></tr>
        </tbody>
      </table>
    </div>
    <div class="completion-form-signatures">
      <section
        ><h3>甲方签章</h3
        ><SignaturePad
          v-model="customerSignature"
          :form-create-inject="signatureInject"
          :disabled="disabled" /><el-form-item prop="customerSignDate" label="签章日期"
          ><el-date-picker
            v-model="model.customerSignDate"
            type="date"
            value-format="YYYY-MM-DD"
            class="!w-full"
            aria-label="甲方签章日期" /></el-form-item
      ></section>
      <section
        ><h3>服务方签章</h3
        ><SignaturePad
          v-model="vendorSignature"
          :form-create-inject="signatureInject"
          :disabled="disabled" /><el-form-item prop="vendorSignDate" label="签章日期"
          ><el-date-picker
            v-model="model.vendorSignDate"
            type="date"
            value-format="YYYY-MM-DD"
            class="!w-full"
            aria-label="服务方签章日期" /></el-form-item
      ></section>
    </div>
    <el-row
      v-if="
        printTemplates.length ||
        model.completionPrintTemplateId ||
        model.satisfactionPrintTemplateId
      "
      :gutter="16"
    >
      <el-col v-for="kind in printKinds" :key="kind.field" :xs="24" :sm="12"
        ><el-form-item :label="kind.label">
          <el-select
            v-model="model[kind.field]"
            clearable
            class="!w-full"
            placeholder="使用标准打印版式"
          >
            <el-option
              v-if="
                model[kind.field] &&
                !templatesFor(kind.category).some((item) => item.templateId === model[kind.field])
              "
              :value="model[kind.field]!"
              label="已绑定历史打印模板（保留原版式）"
              disabled
            />
            <el-option
              v-for="item in templatesFor(kind.category)"
              :key="item.templateId"
              :value="item.templateId"
              :label="`${item.templateName} · 修订 ${item.currentPublishedRevisionNo}`"
            />
          </el-select> </el-form-item
      ></el-col>
    </el-row>
    <p class="template-hint"
      >保存后保留所选打印版式。推送时自动生成满意度调查，客户一次签字确认两份表单。</p
    >
  </el-form>
</template>

<script setup lang="ts">
import * as ProjectApi from '@/api/pms/project/projects'
import * as UserApi from '@/api/system/user'
import type { CompletionCertificateVO } from '@/api/pms/acceptance/completion-certificate'
import type { DynamicFormSelectionVO } from '@/api/pms/platform/dynamic-form'
import { SignaturePad } from '@/components/FormCreate'

const props = defineProps<{
  form: CompletionCertificateVO
  devices: { deviceType: string; deviceModel: string; quantity: number }[]
  projectId?: number
  projectName: string
  disabled: boolean
  printTemplates: DynamicFormSelectionVO[]
}>()
const emit = defineEmits<{
  'project-change': [projectId?: number]
  'engineer-change': [nickname: string, selected?: any]
  'add-device': []
  'remove-device': [index: number]
}>()
const model = props.form
const customerSignature = defineModel<string>('customerSignature', { required: true })
const vendorSignature = defineModel<string>('vendorSignature', { required: true })
const formRef = ref()
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  serviceType: [{ required: true, message: '请选择工程服务类型' }]
}
const serviceItems = [
  { field: 'itemArrival', label: '完成到货验收' },
  { field: 'itemInstall', label: '完成设备硬件安装和软件调测' },
  { field: 'itemCutover', label: '完成业务上线/割接且业务测试正常' },
  { field: 'itemTraining', label: '完成产品维护现场讲解和培训' },
  { field: 'itemDocs', label: '工程文档、帐号密码已移交并协助修改' }
] as const
const printKinds = [
  { field: 'completionPrintTemplateId', category: 'COMPLETION_PRINT', label: '完工证明打印模板' },
  { field: 'satisfactionPrintTemplateId', category: 'SATISFACTION_PRINT', label: '满意度打印模板' }
] as const
const templatesFor = (category: string) =>
  props.printTemplates.filter((item) => item.categoryCode === category)
const onEngineerChange = (nickname: string, selected?: any) =>
  emit('engineer-change', nickname, selected)
const signatureInject = {
  t: (key: string) =>
    (
      ({
        signaturePadTip: '点击添加手写签章',
        signaturePadTitle: '请在虚线框内书写',
        reset: '重置',
        ok: '确定'
      }) as Record<string, string>
    )[key] ?? key
}
defineExpose({ validate: () => formRef.value.validate() })
</script>

<style scoped>
.completion-form-scroll {
  overflow-x: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 5px;
}
.completion-form-table {
  width: 100%;
  min-width: 740px;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 13px;
}
.label-column {
  width: 116px;
}
.completion-form-table th,
.completion-form-table td {
  padding: 12px;
  border-right: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
  overflow-wrap: anywhere;
}
.completion-form-table th {
  font-weight: 400;
  background: var(--el-fill-color-light);
  text-align: left;
}
.completion-form-table th.service-label {
  background: transparent;
}
.completion-form-table tr:nth-child(even) td {
  background: var(--el-fill-color-lighter);
}
.completion-form-table tr > :last-child {
  border-right: 0;
}
.completion-form-table tr:last-child > * {
  border-bottom: 0;
}
.completion-form-table :deep(.el-form-item) {
  margin: 0;
}
.completion-form-table :deep(.el-form-item.is-error) {
  margin-bottom: 18px;
}
.completion-form-table :deep(.el-radio) {
  margin-right: 12px;
  height: auto;
  padding: 4px 0;
}
.completion-form-table :deep(.el-radio:last-child) {
  margin-right: 0;
}
.completion-form-table :deep(.el-radio-group) {
  flex-wrap: wrap;
}
.required {
  color: var(--el-color-danger);
}
.device-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 100px 48px;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.device-header {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.completion-form-signatures {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin: 20px 0;
}
.completion-form-signatures section {
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
}
.completion-form-signatures h3 {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 500;
}
.completion-form-signatures :deep(.el-form-item) {
  margin: 12px 0 0;
}
.template-hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
  margin: 8px 0 0;
}
@media (max-width: 600px) {
  .completion-form-signatures {
    grid-template-columns: 1fr;
  }
}
</style>
