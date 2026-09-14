<template>
  <div class="customer-overview">
    <ContentWrap>
      <section aria-labelledby="project-customer-title" :aria-busy="customerLoading">
        <div v-if="customerError" class="customer-error" role="alert">
          <el-alert type="warning" :title="customerError" :closable="false" show-icon />
          <el-button link type="primary" :loading="customerLoading" @click="load">重新加载</el-button>
        </div>
        <div v-if="customerLoading" class="customer-skeleton" role="status" aria-label="正在加载客户信息">
          <el-skeleton :rows="5" animated />
        </div>
        <template v-else-if="project.customerCode">
          <div class="customer-dossier">
            <aside class="customer-identity">
              <div class="customer-monogram" aria-hidden="true">{{ customerInitial }}</div>
              <div class="identity-copy">
                <h2 id="project-customer-title" class="customer-name">{{ customer?.name || project.customerName || '-' }}</h2>
                <div class="customer-code">{{ customer?.code || project.customerCode || '-' }}</div>
              </div>
              <div class="customer-identity-meta">
                <span class="meta-label">当前状态</span>
                <div class="meta-value">
                  <el-tag :type="customer?.lifecycleStatus === 'ENABLED' ? 'success' : 'info'" size="small">{{ customerStatus }}</el-tag>
                </div>
                <span class="meta-label">数据来源</span>
                <span class="meta-value">{{ customerSource }}</span>
              </div>
              <el-button
                v-if="customer"
                class="maintain-button"
                type="primary"
                plain
                v-hasPermi="['pms:customer:update']"
                @click="customerFormRef?.open(customer)"
              >
                <Icon icon="ep:edit" />维护客户信息
              </el-button>
            </aside>

            <div class="dossier-content">
              <section class="dossier-group">
                <h3><Icon icon="ep:user" />基础信息</h3>
                <dl class="field-grid">
                  <div class="field-item"><dt>客户简称</dt><dd>{{ customer?.shortName || '-' }}</dd></div>
                  <div class="field-item"><dt>客户级别</dt><dd><dict-tag v-if="customer?.customerLevel" type="crm_customer_level" :value="customer.customerLevel" /><span v-else>-</span></dd></div>
                  <div class="field-item"><dt>服务等级</dt><dd><dict-tag v-if="serviceLevel" type="pms_service_level" :value="serviceLevel" /><span v-else>未配置</span></dd></div>
                  <div class="field-item"><dt>联系电话</dt><dd>{{ customer?.contactPhone || '-' }}</dd></div>
                  <div class="field-item"><dt>联系邮箱</dt><dd>{{ customer?.contactEmail || '-' }}</dd></div>
                </dl>
              </section>

              <section class="dossier-group">
                <h3><Icon icon="ep:coordinate" />行业划分</h3>
                <dl class="field-grid">
                  <div class="field-item"><dt>市场部</dt><dd>{{ customer?.marketName || customer?.marketCode || '-' }}</dd></div>
                  <div class="field-item"><dt>系统部</dt><dd>{{ customer?.systemName || customer?.systemCode || '-' }}</dd></div>
                  <div class="field-item"><dt>拓展部</dt><dd>{{ customer?.expendName || customer?.expendCode || '-' }}</dd></div>
                  <div class="field-item"><dt>子行业</dt><dd>{{ customer?.industryName || customer?.industryCode || '-' }}</dd></div>
                  <div class="field-item"><dt>办事处</dt><dd>{{ customer?.departmentName || customer?.departmentCode || '-' }}</dd></div>
                </dl>
              </section>

              <section class="dossier-group dossier-group--extension">
                <h3><Icon icon="ep:connection" />拓展信息</h3>
                <dl class="field-grid">
                  <div class="field-item"><dt>CRM 客户ID</dt><dd>{{ customer?.sourceType === 'CRM_SYNC' ? customer.sourceKey || '-' : '不适用' }}</dd></div>
                  <div class="field-item"><dt>来源版本</dt><dd>{{ customer?.sourceVersion || '-' }}</dd></div>
                  <div class="field-item"><dt>同步状态</dt><dd>{{ customer?.syncStatus || '-' }}</dd></div>
                  <div class="field-item"><dt>数据时间</dt><dd>{{ formatNullableDate(customer?.dataAsOf) }}</dd></div>
                  <div class="field-item"><dt>数据版本</dt><dd>{{ customer?.version ?? '-' }}</dd></div>
                  <div class="field-item"><dt>对账状态</dt><dd><el-tag :type="customer?.reconciliationPending ? 'warning' : 'success'" size="small">{{ customer?.reconciliationPending ? '待对账' : '无需对账' }}</el-tag></dd></div>
                  <div v-if="customer?.sourceType === 'PLATFORM_TEMPORARY'" class="field-item"><dt>临时客户原因</dt><dd>{{ customer.temporaryReason || '-' }}</dd></div>
                  <div class="field-item"><dt>创建人</dt><dd>{{ customer?.creator || '-' }}</dd></div>
                  <div class="field-item"><dt>创建时间</dt><dd>{{ formatNullableDate(customer?.createTime) }}</dd></div>
                  <div class="field-item"><dt>更新人</dt><dd>{{ customer?.updater || '-' }}</dd></div>
                  <div class="field-item"><dt>更新时间</dt><dd>{{ formatNullableDate(customer?.updateTime) }}</dd></div>
                  <div class="field-item field-item--wide"><dt>备注</dt><dd class="remark-value">{{ customer?.remark || '-' }}</dd></div>
                </dl>
              </section>
            </div>
          </div>
        </template>
        <el-empty v-else description="当前项目尚未关联客户" />
      </section>
    </ContentWrap>

    <ProjectCustomerContacts
      v-if="project.id"
      :key="project.id"
      :project-id="project.id"
      :show-customer-filter="false"
      show-section-header
      @changed="emit('changed')"
    />
    <CustomerFormDrawer ref="customerFormRef" @success="load" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import * as CustomerApi from '@/api/pms/customer'
import * as ServiceLevelApi from '@/api/pms/project/service-level'
import type { CustomerDetailRespVO } from '@/api/pms/customer'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import ProjectCustomerContacts from '@/views/pms/customer/contacts/index.vue'
import CustomerFormDrawer from '@/views/pms/customer/components/CustomerFormDrawer.vue'
import { formatNullableDate } from '@/utils/formatTime'
import { checkPermi } from '@/utils/permission'

const props = defineProps<{ project: ProjectMasterVO }>()
const emit = defineEmits<{ changed: [] }>()
const customer = ref<CustomerDetailRespVO>()
const serviceLevel = ref('')
const customerFormRef = ref<InstanceType<typeof CustomerFormDrawer>>()
const customerError = ref('')
const customerLoading = ref(false)
let loadSequence = 0

const customerStatus = computed(
  () =>
    ({
      ENABLED: '启用',
      DISABLED: '停用',
      DELETED: '已删除'
    })[customer.value?.lifecycleStatus || ''] ||
    customer.value?.lifecycleStatus ||
    '-'
)
const customerInitial = computed(() =>
  (customer.value?.name || props.project.customerName || '客').trim().slice(0, 1)
)
const customerSource = computed(
  () =>
    ({
      CRM_SYNC: 'CRM 同步',
      PLATFORM_CREATED: '平台创建',
      PLATFORM_TEMPORARY: '平台临时'
    })[customer.value?.sourceType || ''] ||
    customer.value?.sourceType ||
    '-'
)

const load = async () => {
  const sequence = ++loadSequence
  customer.value = undefined
  serviceLevel.value = ''
  customerError.value = ''
  customerLoading.value = !!props.project.customerCode

  if (props.project.customerCode) {
    try {
      const result = await CustomerApi.getCustomerByCode(props.project.customerCode)
      if (sequence === loadSequence) {
        customer.value = result
        if (checkPermi(['pms:service-level:query'])) try {
          const levels = await ServiceLevelApi.getServiceLevelPage({
            pageNo: 1,
            pageSize: 10,
            customerId: result.id,
            status: 1
          })
          if (sequence === loadSequence) serviceLevel.value = levels.list?.[0]?.level || ''
        } catch {
          // 客户主档仍可独立展示，服务等级暂按未配置处理。
        }
      }
    } catch {
      if (sequence === loadSequence)
        customerError.value = '客户详细信息暂未获取，请稍后重试。'
    } finally {
      if (sequence === loadSequence) customerLoading.value = false
    }
  }
}

watch(() => [props.project.id, props.project.customerCode, props.project.version], load, {
  immediate: true
})
</script>

<style scoped>
.customer-dossier {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.customer-error {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.customer-error :deep(.el-alert) {
  flex: 1;
}

.customer-skeleton {
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.customer-identity {
  display: grid;
  grid-template-columns: auto minmax(180px, 1fr) minmax(260px, auto) auto;
  min-width: 0;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
}

.customer-monogram {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 10px;
  background: var(--el-color-primary);
  color: var(--el-color-white);
  font-size: 18px;
  font-weight: 700;
}

.identity-copy {
  width: 100%;
  min-width: 0;
}

.customer-name {
  margin: 0;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 17px;
  font-weight: 600;
  line-height: 24px;
  text-overflow: ellipsis;
}

.customer-code {
  margin-top: 2px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.customer-identity-meta {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  gap: 8px 10px;
  width: 100%;
  padding-left: 16px;
  border-left: 1px solid var(--el-border-color-lighter);
}

.meta-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 22px;
}

.meta-value {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 22px;
}

.maintain-button {
  margin-left: 4px;
}

.dossier-content {
  min-width: 0;
}

.dossier-group {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 18px;
  min-width: 0;
  padding: 14px 18px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.dossier-group:last-child {
  border-bottom: 0;
}

.dossier-group h3 {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0;
  align-self: start;
  padding-top: 1px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px 24px;
  margin: 0;
}

.field-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.field-item--wide {
  grid-column: 1 / -1;
  align-items: flex-start;
}

.field-item dt {
  flex: 0 0 68px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 22px;
}

.field-item dd {
  flex: 1;
  min-height: 22px;
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--el-text-color-primary);
  font-size: 14px;
  line-height: 22px;
}

.remark-value {
  color: var(--el-text-color-regular) !important;
  white-space: pre-wrap;
}

@media (max-width: 1199px) {
  .customer-identity {
    grid-template-columns: auto minmax(160px, 1fr) minmax(230px, auto) auto;
  }

  .field-grid {
    column-gap: 18px;
  }
}

@media (max-width: 991px) {
  .customer-identity {
    grid-template-columns: auto minmax(0, 1fr) auto;
  }

  .customer-identity-meta {
    grid-column: 1 / -1;
    padding: 10px 0 0;
    border-top: 1px solid var(--el-border-color-lighter);
    border-left: 0;
  }
}

@media (max-width: 767px) {
  .customer-identity {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .customer-monogram {
    width: 36px;
    height: 36px;
    font-size: 16px;
  }

  .customer-identity-meta {
    padding: 10px 0 0;
    border-top: 1px solid var(--el-border-color-lighter);
    border-left: 0;
  }

  .maintain-button {
    width: 100%;
    margin-left: 0;
  }

  .dossier-group {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .dossier-group h3 {
    padding-bottom: 8px;
    border-bottom: 1px solid var(--el-border-color-extra-light);
  }

  .field-grid {
    grid-template-columns: 1fr;
    gap: 5px;
  }

  .customer-error {
    align-items: stretch;
    flex-direction: column;
  }
}

</style>
