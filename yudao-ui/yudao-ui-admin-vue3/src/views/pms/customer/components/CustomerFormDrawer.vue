<template>
  <el-drawer
    v-model="visible"
    class="customer-form-drawer"
    :title="customer ? '维护客户信息' : '创建客户'"
    size="min(760px, 96vw)"
    :close-on-click-modal="false"
    :before-close="beforeClose"
  >
    <div class="form-intro">
      <div class="form-intro-icon" aria-hidden="true"><Icon icon="ep:office-building" /></div>
      <div class="form-intro-copy">
        <div class="form-intro-title">{{ customer ? customer.name : '新客户档案' }}</div>
        <div class="form-intro-description">
          {{ customer ? `客户编码 ${customer.code}，仅提交本次发生变化的字段。` : '建立客户基础档案，并完成行业归属信息。' }}
        </div>
      </div>
      <el-tag :type="customer ? 'info' : 'primary'" size="small">{{ customer ? '维护' : '新增' }}</el-tag>
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      status-icon
      :disabled="saving"
      aria-label="客户信息维护表单"
    >
      <section class="form-section" aria-labelledby="customer-basic-heading">
        <div class="form-section-heading">
          <h3 id="customer-basic-heading">基础信息</h3>
          <span>用于识别客户及确定数据来源</span>
        </div>
        <div class="form-grid">
          <el-form-item label="客户编码" prop="code">
            <el-input v-model="form.code" clearable placeholder="请输入唯一客户编码" :disabled="!!customer" />
            <div v-if="customer" class="field-help">客户创建后编码不可修改</div>
          </el-form-item>
          <el-form-item label="客户名称" prop="name">
            <el-input v-model="form.name" clearable placeholder="请输入客户完整名称" />
          </el-form-item>
          <el-form-item label="客户简称" prop="shortName">
            <el-input v-model="form.shortName" clearable placeholder="请输入便于识别的简称" />
          </el-form-item>
          <el-form-item label="客户级别" prop="customerLevel">
            <el-select
              v-model="form.customerLevel"
              placeholder="请选择客户级别"
              class="field-control"
              :disabled="customer?.sourceType === 'CRM_SYNC'"
            >
              <el-option
                v-for="item in getStrDictOptions('crm_customer_level')"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <div v-if="customer?.sourceType === 'CRM_SYNC'" class="field-help">CRM 权威字段，平台只读</div>
          </el-form-item>
          <el-form-item label="来源类型" prop="sourceType">
            <el-select v-model="form.sourceType" :disabled="!!customer" placeholder="请选择来源类型" class="field-control">
              <el-option label="CRM 同步" value="CRM_SYNC" disabled />
              <el-option label="平台创建" value="PLATFORM_CREATED" />
              <el-option label="平台临时" value="PLATFORM_TEMPORARY" />
            </el-select>
          </el-form-item>
          <el-form-item label="当前服务等级">
            <div class="readonly-field">
              <dict-tag v-if="serviceLevel?.level" type="pms_service_level" :value="serviceLevel.level" />
              <span v-else>{{ customer ? '尚未配置' : '创建客户后配置' }}</span>
            </div>
            <div class="field-help">服务等级按生效区间和变更历史在服务等级功能中独立维护</div>
          </el-form-item>
          <el-form-item
            v-if="form.sourceType === 'PLATFORM_TEMPORARY'"
            class="form-item-wide"
            label="临时客户原因"
            prop="temporaryReason"
          >
            <el-input
              v-model="form.temporaryReason"
              type="textarea"
              :rows="3"
              :disabled="!!customer"
              placeholder="说明暂时无法建立正式客户档案的原因"
            />
            <div v-if="customer" class="field-help">临时客户原因在创建时记录，维护阶段只读</div>
          </el-form-item>
        </div>
      </section>

      <section class="form-section" aria-labelledby="customer-industry-heading">
        <div class="form-section-heading">
          <h3 id="customer-industry-heading">行业划分</h3>
          <span>维护客户所属组织及子行业编码</span>
        </div>
        <div class="form-grid">
          <el-form-item class="form-item-wide" label="市场行业归属" prop="industryCode">
            <el-cascader
              v-model="classificationPath"
              :options="classificationOptions"
              :props="classificationProps"
              clearable
              filterable
              class="field-control"
              placeholder="依次选择市场部、系统部、拓展部、子行业"
              :disabled="customer?.sourceType === 'CRM_SYNC'"
              @change="handleClassificationChange"
            />
            <div class="classification-caption">市场部 / 系统部 / 拓展部 / 子行业</div>
            <div v-if="customer?.sourceType === 'CRM_SYNC'" class="field-help">CRM 权威字段，平台只读</div>
          </el-form-item>
          <el-form-item class="form-item-wide" label="所属办事处" prop="departmentCode">
            <el-select
              v-model="form.departmentCode"
              clearable
              filterable
              class="field-control"
              placeholder="请选择办事处"
              :disabled="customer?.sourceType === 'CRM_SYNC'"
            >
              <el-option
                v-for="department in departmentOptions"
                :key="department.id"
                :label="`${department.name}（${department.code}）`"
                :value="department.code"
              />
            </el-select>
            <div v-if="customer?.sourceType === 'CRM_SYNC'" class="field-help">CRM 权威字段，平台只读</div>
          </el-form-item>
        </div>
      </section>

      <section class="form-section" aria-labelledby="customer-extra-heading">
        <div class="form-section-heading">
          <h3 id="customer-extra-heading">补充信息</h3>
          <span>记录其他需要长期保留的客户说明</span>
        </div>
        <el-form-item label="备注" prop="remark" class="form-item-standalone">
          <el-input v-model="form.remark" type="textarea" :rows="4" placeholder="请输入客户备注" />
        </el-form-item>
      </section>
    </el-form>

    <template #footer>
      <div class="drawer-footer">
        <span class="required-tip"><span aria-hidden="true">*</span> 为必填项</span>
        <div>
          <el-button :disabled="saving" @click="requestClose">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">保存客户信息</el-button>
        </div>
      </div>
    </template>
  </el-drawer>
</template>
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useMessage } from '@/hooks/web/useMessage'
import * as CustomerApi from '@/api/pms/customer'
import * as ServiceLevelApi from '@/api/pms/project/service-level'
import type { CustomerServiceLevelVO } from '@/api/pms/project/service-level'
import * as DeptApi from '@/api/system/dept'
import type { DeptVO } from '@/api/system/dept'
import { getStrDictOptions } from '@/utils/dict'
import { checkPermi } from '@/utils/permission'
import type {
  CustomerCreateReqVO,
  CustomerDetailRespVO,
  CustomerUpdateReqVO
} from '@/api/pms/customer'
import { createCustomerIntentStore, customerIntentOf } from '../customerInteraction'
const emit = defineEmits<{ success: [] }>()
const message = useMessage()
const intentKeys = createCustomerIntentStore()
const visible = ref(false)
const saving = ref(false)
const customer = ref<CustomerDetailRespVO>()
const serviceLevel = ref<CustomerServiceLevelVO>()
const classificationRows = ref<CustomerApi.CustomerClassificationOption[]>([])
const departmentOptions = ref<DeptVO[]>([])
const classificationPath = ref<string[]>([])
const formRef = ref<FormInstance>()
let baseline = ''
const form = reactive<CustomerCreateReqVO & { changedFields: string[] }>({
  code: '',
  name: '',
  customerLevel: '',
  sourceType: 'PLATFORM_CREATED',
  temporaryReason: undefined,
  reconciliationPending: false,
  departmentCode: '',
  marketCode: '',
  systemCode: '',
  expendCode: '',
  industryCode: '',
  changedFields: []
})
const classificationProps = { expandTrigger: 'hover' as const }
const classificationOptions = computed(() => {
  const markets = new Map<string, any>()
  for (const row of classificationRows.value) {
    let market = markets.get(row.marketCode)
    if (!market) {
      market = { value: row.marketCode, label: row.marketName, children: new Map<string, any>() }
      markets.set(row.marketCode, market)
    }
    let system = market.children.get(row.systemCode)
    if (!system) {
      system = { value: row.systemCode, label: row.systemName, children: new Map<string, any>() }
      market.children.set(row.systemCode, system)
    }
    let expend = system.children.get(row.expendCode)
    if (!expend) {
      expend = { value: row.expendCode, label: row.expendName, children: [] }
      system.children.set(row.expendCode, expend)
    }
    if (!expend.children.some((item: any) => item.value === row.industryCode)) {
      expend.children.push({ value: row.industryCode, label: row.industryName })
    }
  }
  return [...markets.values()].map((market) => ({
    ...market,
    children: [...market.children.values()].map((system: any) => ({
      ...system,
      children: [...system.children.values()]
    }))
  }))
})
const handleClassificationChange = (value: unknown) => {
  const [marketCode = '', systemCode = '', expendCode = '', industryCode = ''] =
    Array.isArray(value) ? value.map(String) : []
  Object.assign(form, { marketCode, systemCode, expendCode, industryCode })
}
const loadReferenceData = async (value?: CustomerDetailRespVO) => {
  const tasks: Promise<unknown>[] = [
    CustomerApi.getClassificationOptions().then((rows) => (classificationRows.value = rows)),
    DeptApi.getSimpleDeptList().then((rows) => {
      departmentOptions.value = rows
        .filter((department) => department.code && department.status === 0)
        .sort((left, right) => left.sort - right.sort || left.name.localeCompare(right.name, 'zh-CN'))
    })
  ]
  if (value?.id && checkPermi(['pms:service-level:query'])) {
    tasks.push(
      ServiceLevelApi.getServiceLevelPage({ pageNo: 1, pageSize: 10, customerId: value.id, status: 1 })
        .then((page) => (serviceLevel.value = page.list?.[0]))
    )
  } else serviceLevel.value = undefined
  await Promise.all(tasks)
}
const open = (value?: CustomerDetailRespVO) => {
  customer.value = value
  Object.assign(
    form,
    {
      code: '',
      name: '',
      customerLevel: '',
      shortName: '',
      remark: '',
      sourceType: 'PLATFORM_CREATED',
      temporaryReason: undefined,
      reconciliationPending: false,
      departmentCode: '',
      marketCode: '',
      systemCode: '',
      expendCode: '',
      industryCode: '',
      changedFields: []
    },
    value || {}
  )
  classificationPath.value = [form.marketCode, form.systemCode, form.expendCode, form.industryCode].filter(Boolean)
  baseline = JSON.stringify(form)
  visible.value = true
  void loadReferenceData(value).catch(() => message.warning('客户分类或服务等级信息加载失败，请重新打开后重试'))
}
const rules: FormRules = {
  code: [{ required: true, whitespace: true, message: '请输入客户编码', trigger: 'blur' }],
  name: [{ required: true, whitespace: true, message: '请输入客户名称', trigger: 'blur' }],
  sourceType: [{ required: true, message: '请选择来源类型', trigger: 'change' }],
  customerLevel: [{ required: true, message: '请选择客户级别', trigger: 'change' }],
  industryCode: [{ required: true, whitespace: true, message: '请选择完整的市场行业归属', trigger: 'change' }],
  departmentCode: [{ required: true, message: '请选择所属办事处', trigger: 'change' }],
  temporaryReason: [
    {
      validator: (_rule, value, callback) => {
        if (form.sourceType === 'PLATFORM_TEMPORARY' && !String(value || '').trim()) {
          callback(new Error('请输入临时客户原因'))
        } else callback()
      },
      trigger: 'blur'
    }
  ]
}
const beforeClose = async (done: () => void) => {
  if (saving.value) return
  if (baseline !== JSON.stringify(form)) {
    try {
      await message.confirm('客户信息尚未保存，确定放弃本次修改？')
    } catch {
      return
    }
  }
  done()
}
const requestClose = () => beforeClose(() => (visible.value = false))
const submit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const temporary = form.sourceType === 'PLATFORM_TEMPORARY'
  const temporaryReason = form.temporaryReason?.trim()
  if (temporary && !temporaryReason) {
    message.error('临时客户原因不能为空')
    return
  }
  saving.value = true
  try {
    if (customer.value) {
      const fields = [
        'name',
        'shortName',
        'customerLevel',
        'remark'
      ] as const
      const data: CustomerUpdateReqVO = {
        changedFields: fields.filter((field) => form[field] !== customer.value?.[field])
      }
      const classificationChanged = ['departmentCode', 'marketCode', 'systemCode', 'expendCode', 'industryCode']
        .some((field) => form[field as keyof typeof form] !== customer.value?.[field as keyof CustomerDetailRespVO])
      if (classificationChanged) data.changedFields.push('classification')
      if (!data.changedFields.length) {
        message.info('未检测到需要保存的修改')
        return
      }
      data.changedFields.forEach((field) => {
        if (field === 'classification') {
          Object.assign(data, {
            departmentCode: form.departmentCode,
            marketCode: form.marketCode,
            systemCode: form.systemCode,
            expendCode: form.expendCode,
            industryCode: form.industryCode
          })
        } else {
          data[field as keyof CustomerUpdateReqVO] = form[field as keyof typeof form] as never
        }
      })
      const intent = customerIntentOf('update', {
        id: customer.value.id,
        version: customer.value.version,
        data
      })
      await CustomerApi.updateCustomer(
        customer.value.id,
        data,
        customer.value.version,
        intentKeys.key(intent)
      )
      intentKeys.complete(intent)
    } else {
      const data: CustomerCreateReqVO = {
        ...form,
        temporaryReason: temporary ? temporaryReason : undefined,
        reconciliationPending: temporary
      }
      const intent = customerIntentOf('create', data)
      await CustomerApi.createCustomer(data, intentKeys.key(intent))
      intentKeys.complete(intent)
    }
    visible.value = false
    message.success(customer.value ? '客户信息已更新' : '客户已创建')
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
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-light);
}

.form-intro-icon {
  display: grid;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: var(--el-border-radius-base);
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
}

.form-intro-copy {
  flex: 1;
  min-width: 0;
}

.form-intro-title {
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.form-intro-description,
.field-help {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 20px;
}

.form-section {
  padding: 0 0 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.form-section:last-child {
  padding-bottom: 0;
  margin-bottom: 0;
  border-bottom: 0;
}

.form-section-heading {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 12px;
}

.form-section-heading h3 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 600;
  line-height: 24px;
}

.form-section-heading span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.form-grid :deep(.el-form-item),
.form-item-standalone {
  margin-bottom: 14px;
}

.form-item-wide {
  grid-column: 1 / -1;
}

.form-item-wide :deep(.el-form-item__content),
.form-item-standalone :deep(.el-form-item__content) {
  width: 100%;
  min-width: 0;
}

.form-item-wide :deep(.el-input),
.form-item-wide :deep(.el-select),
.form-item-wide :deep(.el-cascader),
.form-item-wide :deep(.el-textarea),
.form-item-standalone :deep(.el-input),
.form-item-standalone :deep(.el-textarea) {
  width: 100%;
}

.field-control {
  width: 100%;
}

.readonly-field {
  display: flex;
  align-items: center;
  min-height: 32px;
  color: var(--el-text-color-regular);
}

.classification-caption {
  width: 100%;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 20px;
}

.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.required-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.required-tip span {
  color: var(--el-color-danger);
}

@media (max-width: 600px) {
  .form-intro {
    align-items: flex-start;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .form-item-wide {
    grid-column: auto;
  }

  .form-section-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 0;
  }

  .drawer-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .drawer-footer > div {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .drawer-footer :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}
</style>
