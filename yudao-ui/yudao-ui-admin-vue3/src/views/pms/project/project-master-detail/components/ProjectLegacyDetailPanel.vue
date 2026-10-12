<template>
  <ContentWrap class="legacy-detail" data-testid="project-legacy-detail" v-loading="loading">
    <ProjectAssociatedDataPanel :project-id="projectId" />
    <el-divider />
    <div class="panel-heading"
      ><h3>DPPMS 关联资料</h3><el-button @click="refresh++">刷新资料</el-button></div
    >
    <el-alert
      v-if="sourceError"
      title="关联资料加载失败，请重试。"
      type="error"
      :closable="false"
    />
    <el-empty v-else-if="!loading && !source" description="尚未同步 DPPMS 关联资料" />
    <template v-if="source">
      <el-select v-if="sources.length > 1" v-model="sourceId" aria-label="选择来源项目">
        <el-option
          v-for="item in sources"
          :key="String(item.id)"
          :value="item.id"
          :label="`${item.sourceContractNo} · 原项目 ${item.sourceProjectKey}`"
        />
      </el-select>
      <p class="source-meta"
        >原项目 {{ source.sourceProjectKey }} · 合同 {{ source.sourceContractNo }} · 资料读取时间
        {{ source.sourceReadAt.replace('T', ' ') }}</p
      >
      <el-alert
        title="以下为旧系统历史资料，原状态和人员关系按历史展示。"
        description="业务承接正在复核，已有目标记录不代表承接完成。"
        type="info"
        :closable="false"
      />
      <p v-for="note in source.deferredDomains" :key="note" class="deferred-note">{{ note }}</p>
      <p v-if="nativeReport" role="status" class="source-meta" data-testid="legacy-native-summary">
        已有目标记录 {{ nativeReport.mappedCount }} 条 · 保留来源资料
        {{ nativeReport.retainedCount }} 条
        <span v-if="nativeReport.pendingCount"> · 待承接 {{ nativeReport.pendingCount }} 条</span>
      </p>
      <el-alert
        v-if="nativeReportError"
        title="业务承接状态加载失败，请刷新资料重试。"
        type="error"
        :closable="false"
      />
      <el-collapse v-if="nativeReport?.retainedCount" aria-label="保留历史的缺项">
        <el-collapse-item title="查看保留历史的缺项及原因" name="retained">
          <p
            v-for="item in nativeReport.domains.filter((d) => d.retainedCount > 0)"
            :key="item.domain"
          >
            {{ item.label }}（{{ item.retainedCount }} 条）：
            {{
              Object.entries(item.retentionReasons)
                .map(([reason, count]) => `${reason}（${count}）`)
                .join('；')
            }}
          </p>
        </el-collapse-item>
      </el-collapse>
      <div class="group-tabs" role="group" aria-label="关联资料类别">
        <button
          v-for="name in groups"
          :key="name"
          :aria-pressed="group === name"
          :class="{ selected: group === name }"
          @click="selectGroup(name)"
          >{{ name }}</button
        >
      </div>
      <div class="domain-layout">
        <nav aria-label="资料类型" class="domain-nav">
          <button
            v-for="item in groupDomains"
            :key="item.code"
            :class="{ selected: domainCode === item.code }"
            :aria-current="domainCode === item.code ? 'true' : undefined"
            @click="selectDomain(item.code)"
          >
            <span>{{ item.label }}</span
            ><span>{{ source.counts[item.code] ?? 0 }}</span>
          </button>
        </nav>
        <section class="records" v-loading="recordsLoading" :aria-label="domain?.label">
          <div class="records-heading"
            ><h4>{{ domain?.label }}</h4
            ><span>共 {{ total }} 条</span></div
          >
          <p v-if="filter.sourceKey || filter.parentKey" class="filter-note"
            >当前展示关联记录
            <el-button link type="primary" @click="filter = {}">显示此类全部记录</el-button></p
          >
          <el-alert
            v-if="recordError"
            title="资料记录加载失败，请重试。"
            type="error"
            :closable="false"
            ><el-button @click="recordRefresh++">重新加载</el-button></el-alert
          >
          <template v-else>
            <el-alert
              v-if="nativeRecordError"
              title="业务承接状态暂不可用，历史资料仍可查看。"
              type="warning"
              :closable="false"
            />
            <el-table
              :data="records"
              row-key="sourceKey"
              empty-text="旧系统无此类关联记录"
              @row-dblclick="selected = $event"
            >
              <el-table-column
                prop="sourceKey"
                label="来源编号"
                min-width="130"
                show-overflow-tooltip
              />
              <el-table-column label="业务承接" min-width="120">
                <template #default="{ row }">{{
                  nativeRecordError ? '状态暂不可用' : legacyNativeStatusText(row.nativeStatus)
                }}</template>
              </el-table-column>
              <el-table-column
                v-for="field in summaryFields"
                :key="field.key"
                :label="field.label"
                min-width="150"
                show-overflow-tooltip
              >
                <template #default="{ row }">{{ legacyValueText(row.values[field.key]) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="100" fixed="right"
                ><template #default="{ row }"
                  ><el-button link type="primary" @click="selected = row"
                    >查看详情</el-button
                  ></template
                ></el-table-column
              >
            </el-table>
            <Pagination
              v-model:page="pageNo"
              v-model:limit="pageSize"
              :total="total"
              :page-sizes="[20, 50, 100]"
            />
          </template>
        </section>
      </div>
      <ProjectLegacyRecordDetails
        :record="selected"
        :domain="domain"
        :domains="source.domains"
        @close="selected = undefined"
        @parent="showParent"
        @children="showChildren"
        @reference="showReference"
      />
    </template>
  </ContentWrap>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  getLegacySources,
  getLegacyRecords,
  getLegacyNativeReport,
  getLegacyNativeRecordStatus,
  legacyNativeStatusText,
  legacyValueText,
  type LegacyId,
  type LegacyRecord,
  type LegacySource,
  type LegacyNativeReport
} from '@/api/pms/project/legacy-detail'
import ProjectLegacyRecordDetails from './ProjectLegacyRecordDetails.vue'
import ProjectAssociatedDataPanel from './ProjectAssociatedDataPanel.vue'
const props = defineProps<{ projectId: LegacyId }>()
const sources = ref<LegacySource[]>([])
const sourceId = ref<LegacyId>()
const source = computed(() =>
  sources.value.find((item) => String(item.id) === String(sourceId.value))
)
const loading = ref(false)
const sourceError = ref(false)
const nativeReport = ref<LegacyNativeReport>()
const nativeReportError = ref(false)
const refresh = ref(0)
const group = ref('')
const domainCode = ref('project')
const groups = computed(() => [...new Set(source.value?.domains.map((item) => item.group) || [])])
const groupDomains = computed(
  () => source.value?.domains.filter((item) => item.group === group.value) || []
)
const domain = computed(() => source.value?.domains.find((item) => item.code === domainCode.value))
const summaryFields = computed(
  () =>
    domain.value?.fields
      .filter(
        (field) =>
          ![
            'id',
            'projectId',
            'projectIds',
            'createBy',
            'updateBy',
            domain.value?.keyField
          ].includes(field.key)
      )
      .slice(0, 4) || []
)
const records = ref<LegacyRecord[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const recordsLoading = ref(false)
const recordError = ref(false)
const nativeRecordError = ref(false)
const recordRefresh = ref(0)
const selected = ref<LegacyRecord>()
const filter = ref<{ sourceKey?: string; parentDomain?: string; parentKey?: string }>({})

// Vue watcher cleanup prevents a late response from another project/domain replacing the current panel.
// https://vuejs.org/guide/essentials/watchers.html#side-effect-cleanup
watch(
  [() => props.projectId, refresh],
  async ([projectId], _old, onCleanup) => {
    let current = true
    onCleanup(() => {
      current = false
    })
    sources.value = []
    sourceId.value = undefined
    selected.value = undefined
    loading.value = true
    sourceError.value = false
    try {
      const result = await getLegacySources(projectId)
      if (current) {
        sources.value = result
        sourceId.value = result[0]?.id
      }
    } catch {
      if (current) sourceError.value = true
    } finally {
      if (current) loading.value = false
    }
  },
  { immediate: true }
)
watch(source, () => {
  group.value = source.value?.domains[0]?.group || ''
  selectDomain('project')
})
watch(source, async (currentSource, _old, onCleanup) => {
  let current = true
  onCleanup(() => {
    current = false
  })
  nativeReport.value = undefined
  nativeReportError.value = false
  if (!currentSource) return
  try {
    const report = await getLegacyNativeReport(props.projectId, {
      sourceId: currentSource.id,
      snapshotId: currentSource.snapshotId
    })
    if (current) nativeReport.value = report
  } catch {
    if (current) nativeReportError.value = true
  }
})
watch(
  [source, domainCode, filter, pageNo, pageSize, recordRefresh],
  async (_new, _old, onCleanup) => {
    let current = true
    onCleanup(() => {
      current = false
    })
    const currentSource = source.value
    records.value = []
    // Keep the immutable scope's count during pagination; a temporary zero clamps page two to page one.
    if (_new[0] !== _old?.[0] || _new[1] !== _old?.[1] || _new[2] !== _old?.[2]) total.value = 0
    recordError.value = false
    nativeRecordError.value = false
    if (!currentSource) {
      total.value = 0
      recordsLoading.value = false
      return
    }
    recordsLoading.value = true
    try {
      const query = {
        sourceId: currentSource.id,
        snapshotId: currentSource.snapshotId,
        domain: domainCode.value,
        ...filter.value,
        pageNo: pageNo.value,
        pageSize: pageSize.value
      }
      const [recordsResult, statusResult] = await Promise.allSettled([
        getLegacyRecords(props.projectId, query),
        getLegacyNativeRecordStatus(props.projectId, query)
      ])
      if (recordsResult.status === 'rejected') throw recordsResult.reason
      const result = recordsResult.value
      const statuses = statusResult.status === 'fulfilled' ? statusResult.value : {}
      if (current) {
        nativeRecordError.value = statusResult.status === 'rejected'
        records.value = result.list.map((row) => ({
          ...row,
          nativeStatus: statuses[row.sourceKey]
        }))
        total.value = result.total
      }
    } catch {
      if (current) recordError.value = true
    } finally {
      if (current) recordsLoading.value = false
    }
  },
  { immediate: true }
)
const selectDomain = (code: string) => {
  domainCode.value = code
  pageNo.value = 1
  filter.value = {}
  selected.value = undefined
}
const selectGroup = (name: string) => {
  group.value = name
  selectDomain(groupDomains.value[0]?.code || 'project')
}
const navigate = (code: string) => {
  selectDomain(code)
  group.value = source.value?.domains.find((item) => item.code === code)?.group || ''
}
const showParent = (record: LegacyRecord) => {
  if (record.parentDomain) {
    navigate(record.parentDomain)
    filter.value = { sourceKey: record.parentSourceKey }
  }
}
const showChildren = (record: LegacyRecord, code: string) => {
  navigate(code)
  filter.value = { parentDomain: record.domain, parentKey: record.sourceKey }
}
const showReference = (code: string, sourceKey: string) => {
  navigate(code)
  filter.value = { sourceKey }
}
</script>

<style scoped>
.panel-heading,
.records-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
h3,
h4 {
  margin: 0;
}
.source-meta,
.deferred-note,
.filter-note {
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}
.deferred-note {
  font-size: 13px;
}
.group-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 20px 0 16px;
}
button {
  font: inherit;
  cursor: pointer;
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  padding: 8px 12px;
}
button.selected {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
button:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
.domain-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 20px;
}
.domain-nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.domain-nav button {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  text-align: left;
}
.records {
  min-width: 0;
}
.records-heading {
  margin-bottom: 16px;
}
@media (max-width: 768px) {
  .domain-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .domain-nav {
    flex-direction: row;
    flex-wrap: wrap;
  }
  .domain-nav button {
    flex: 1 1 180px;
  }
}
</style>
