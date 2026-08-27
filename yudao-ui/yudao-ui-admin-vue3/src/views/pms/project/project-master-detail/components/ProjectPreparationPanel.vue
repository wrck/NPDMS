<template>
  <ContentWrap>
    <div class="panel-heading">
      <div><h3>工勘准备</h3><span>逐项分工、固定表单、来源与实施就绪</span></div>
      <div class="heading-actions">
        <el-button v-if="preparation" @click="readinessRef?.open(preparation)">快照历史</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>
    <el-skeleton v-if="loading" :rows="6" animated />
    <el-alert v-else-if="unavailable" :title="unavailable" type="warning" :closable="false">
      <template #default
        >历史项目未冻结 PRE-02 工作绑定时仅展示该稳定阻断，不提供初始化按钮。</template
      >
    </el-alert>
    <el-empty v-else-if="!preparation" description="当前项目没有可查询的工勘准备" />
    <template v-else>
      <el-alert
        v-if="!preparation.snapshotCurrent && preparation.latestReadinessSnapshotId"
        title="当前输入已变化，旧 READY 快照不是当前事实，请重新评估。"
        type="warning"
        :closable="false"
      />
      <div class="summary-grid">
        <div
          ><span>业务版本</span><strong>V{{ preparation.businessVersion }}</strong></div
        >
        <div
          ><span>准备状态</span><strong>{{ preparation.status }}</strong></div
        >
        <div
          ><span>就绪状态</span><strong>{{ preparation.readinessStatus }}</strong></div
        >
        <div
          ><span>输入 / 就绪版本</span
          ><strong>{{ preparation.inputVersion }} / {{ preparation.readinessVersion }}</strong></div
        >
      </div>
      <div class="primary-actions">
        <el-button
          v-if="preparation.allowedActions.includes('SUBMIT')"
          type="primary"
          @click="submit"
          >提交确认</el-button
        >
        <el-button
          v-if="preparation.allowedActions.includes('EVALUATE_READINESS')"
          type="success"
          @click="evaluate"
          >显式评估就绪</el-button
        >
      </div>
      <el-table :data="items" class="desktop-items" row-key="itemId">
        <el-table-column prop="itemName" label="工勘项" min-width="130" />
        <el-table-column label="适用 / 确认" min-width="150">
          <template #default="{ row }"
            >{{ row.applicability }} · {{ row.confirmationStatus }}</template
          >
        </el-table-column>
        <el-table-column label="负责人" min-width="100"
          ><template #default="{ row }">{{
            row.assigneeUserId || '未指派'
          }}</template></el-table-column
        >
        <el-table-column label="来源事实" min-width="250">
          <template #default="{ row }"><SourceFacts :item="row" /></template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="250">
          <template #default="{ row }"><ItemActions :item="row" /></template>
        </el-table-column>
      </el-table>
      <div class="mobile-items">
        <article v-for="row in items" :key="row.itemId" class="item-card">
          <div class="item-heading"
            ><strong>{{ row.itemName }}</strong
            ><el-tag>{{ row.confirmationStatus }}</el-tag></div
          >
          <p>{{ row.applicability }} · 负责人 {{ row.assigneeUserId || '未指派' }}</p>
          <SourceFacts :item="row" />
          <ItemActions :item="row" />
        </article>
      </div>
    </template>
  </ContentWrap>
  <PreparationItemDrawer ref="itemRef" :project-version="project.version || 0" @saved="load" />
  <PreparationWaiverDrawer
    ref="waiverRef"
    :project-version="project.version || 0"
    @changed="load"
  />
  <PreparationReadinessDrawer ref="readinessRef" />
</template>

<script setup lang="tsx">
import { useMessage } from '@/hooks/web/useMessage'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import * as PreparationApi from '@/api/pms/engineering/preparation'
import type {
  PreparationItemVO,
  PreparationSourceVO,
  PreparationVO
} from '@/api/pms/engineering/preparation'
import PreparationItemDrawer from './PreparationItemDrawer.vue'
import PreparationWaiverDrawer from './PreparationWaiverDrawer.vue'
import PreparationReadinessDrawer from './PreparationReadinessDrawer.vue'

const props = defineProps<{ project: ProjectMasterVO }>()
const message = useMessage()
const loading = ref(false)
const unavailable = ref('')
const preparation = ref<PreparationVO | null>(null)
const items = ref<PreparationItemVO[]>([])
const itemRef = ref<InstanceType<typeof PreparationItemDrawer>>()
const waiverRef = ref<InstanceType<typeof PreparationWaiverDrawer>>()
const readinessRef = ref<InstanceType<typeof PreparationReadinessDrawer>>()

const load = async () => {
  if (!props.project.id) return
  loading.value = true
  unavailable.value = ''
  try {
    preparation.value = await PreparationApi.getCurrent(props.project.id)
    items.value = []
    if (!preparation.value) return
    let cursor: string | undefined
    do {
      const page = await PreparationApi.getItems(preparation.value.preparationId, {
        cursor,
        pageSize: 100
      })
      items.value.push(...page.items)
      cursor = page.hasMore ? page.nextCursor : undefined
    } while (cursor)
  } catch (error: any) {
    preparation.value = null
    items.value = []
    const code = String(error?.code || error?.data?.code || error?.message || '')
    if (code.includes('WORK_BINDING_NOT_AVAILABLE') || code.includes('1011024001')) {
      unavailable.value = 'WORK_BINDING_NOT_AVAILABLE：当前项目没有冻结的 PRE-02 工作绑定'
    } else throw error
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  if (!preparation.value) return
  await message.confirm('提交后固定表单将冻结并进入逐项确认，是否继续？')
  await PreparationApi.submit(preparation.value, props.project.version || 0, crypto.randomUUID())
  message.success('工勘准备已提交确认')
  await load()
}
const evaluate = async () => {
  if (!preparation.value) return
  await PreparationApi.evaluateReadiness(
    preparation.value,
    props.project.version || 0,
    crypto.randomUUID()
  )
  message.success('已按当前权威事实完成就绪评估')
  await load()
}
const review = async (
  item: PreparationItemVO,
  action: 'confirm' | 'confirm-not-applicable' | 'return'
) => {
  if (!preparation.value) return
  let reason: string | undefined
  if (action !== 'confirm')
    reason = (await message.prompt('请输入原因', action === 'return' ? '退回工勘项' : '确认不适用'))
      .value
  await PreparationApi.reviewItem(
    preparation.value,
    item,
    action,
    props.project.version || 0,
    reason,
    crypto.randomUUID()
  )
  message.success('工勘项状态已更新')
  await load()
}
const refreshSource = async (item: PreparationItemVO) => {
  if (!preparation.value) return
  const current = item.sources[0]
  const objectType =
    current?.sourceObjectType ||
    (await message.prompt('请输入 OA 来源对象类型', '刷新权威来源')).value
  const objectId =
    current?.sourceObjectId || (await message.prompt('请输入 OA 来源单号', '刷新权威来源')).value
  const referenceKey = current?.sourceReferenceKey || `oa-${objectType}-${objectId}`
  await PreparationApi.refreshSource(
    preparation.value.preparationId,
    item.itemId,
    {
      expectedPreparationVersion: preparation.value.version,
      expectedInputVersion: preparation.value.inputVersion,
      expectedReadinessVersion: preparation.value.readinessVersion,
      expectedItemVersion: item.version,
      expectedSourceVersion: current?.sourceVersion,
      expectedProjectVersion: props.project.version || 0,
      sourceTypeCode: current?.sourceTypeCode || 'OA',
      sourceObjectType: objectType,
      sourceObjectId: objectId,
      sourceReferenceKey: referenceKey
    },
    crypto.randomUUID()
  )
  message.success('来源刷新已完成')
  await load()
}

const SourceFacts = ({ item }: { item: PreparationItemVO }) => {
  if (!item.sources.length) return <span class="muted">尚无来源事实</span>
  return (
    <div class="source-list">
      {item.sources.map((source: PreparationSourceVO) => (
        <div
          class={['source-row', { 'source-row--error': source.syncStatusCode !== 'SYNCED' }]}
          key={source.sourceReferenceId}
        >
          <strong>
            {source.sourceReferenceKey} · {source.syncStatusCode}
          </strong>
          <span>当前：{source.normalizedResultCode || '未知'}</span>
          {source.syncStatusCode !== 'SYNCED' && (
            <small>
              最近成功：{source.lastSuccessResultCode || '无'}（仅历史显示，不可用于就绪）
            </small>
          )}
          {source.lastSyncErrorCode && (
            <small>异常：{source.lastSyncErrorCode}，不可用于就绪</small>
          )}
        </div>
      ))}
    </div>
  )
}
const ItemActions = ({ item }: { item: PreparationItemVO }) => (
  <div class="item-actions">
    <el-button link type="primary" onClick={() => itemRef.value?.open(preparation.value!, item)}>
      详情
    </el-button>
    {item.allowedActions.includes('REFRESH_SOURCE') && (
      <el-button link onClick={() => refreshSource(item)}>
        刷新来源
      </el-button>
    )}
    {item.allowedActions.includes('CREATE_WAIVER') && (
      <el-button link onClick={() => waiverRef.value?.open(preparation.value!, item)}>
        豁免
      </el-button>
    )}
    {item.allowedActions.includes('REVIEW_ITEM') && (
      <>
        <el-button
          link
          type="success"
          onClick={() =>
            review(item, item.applicability === 'REQUIRED' ? 'confirm' : 'confirm-not-applicable')
          }
        >
          确认
        </el-button>
        <el-button link type="danger" onClick={() => review(item, 'return')}>
          退回
        </el-button>
      </>
    )}
  </div>
)

watch(() => props.project.id, load, { immediate: true })
</script>

<style scoped lang="scss">
.panel-heading,
.heading-actions,
.primary-actions,
.item-heading,
.item-actions {
  display: flex;
  align-items: center;
}

.panel-heading {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-heading h3 {
  margin: 0 0 4px;
  color: var(--el-text-color-primary);
}

.panel-heading span,
.summary-grid span,
.item-card p,
.source-row span,
.source-row small,
.muted {
  color: var(--el-text-color-secondary);
}

.heading-actions,
.primary-actions,
.item-actions {
  gap: 8px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 12px 0;
}

.summary-grid > div {
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: var(--el-border-radius-base);
}

.summary-grid strong,
.summary-grid span {
  display: block;
}

.summary-grid strong {
  margin-top: 6px;
  color: var(--el-text-color-primary);
}

.primary-actions {
  justify-content: flex-end;
  margin-bottom: 12px;
}

.source-list {
  display: grid;
  gap: 6px;
}

.source-row {
  display: grid;
  gap: 2px;
}

.source-row--error {
  padding-left: 8px;
  border-left: 3px solid var(--el-color-warning);
}

.source-row small {
  display: block;
}

.mobile-items {
  display: none;
}

.item-card {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.item-heading {
  justify-content: space-between;
  gap: 8px;
}

@media (width <= 1023px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .desktop-items {
    display: none;
  }

  .mobile-items {
    display: grid;
    gap: 10px;
  }
}

@media (width <= 767px) {
  .panel-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .heading-actions,
  .primary-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .item-actions {
    flex-wrap: wrap;
  }
}
</style>
