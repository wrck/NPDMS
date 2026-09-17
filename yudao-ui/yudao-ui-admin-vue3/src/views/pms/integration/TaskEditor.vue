<template>
  <el-dialog
    v-model="visible"
    title="迁移与同步任务配置"
    width="min(960px, calc(100vw - 32px))"
    top="6vh"
    class="integration-editor"
    destroy-on-close
    :close-on-click-modal="false"
    @closed="emit('closed')"
  >
    <el-tabs v-if="draft" v-model="section" class="integration-config-tabs">
      <el-tab-pane label="基本信息" name="basic">
        <el-form
          ref="form"
          :model="draft"
          label-width="128px"
          class="integration-form"
          @submit.prevent
        >
          <el-form-item
            label="任务名称"
            prop="name"
            :rules="[{ required: true, message: '请输入任务名称' }]"
            ><el-input v-model="draft.name"
          /></el-form-item>
          <el-form-item label="来源连接"
            ><el-select v-model="draft.definition.connectionId"
              ><el-option
                v-for="c in connections"
                :key="c.id"
                :value="c.id"
                :label="c.name" /></el-select
          ></el-form-item>
          <el-form-item label="来源系统标识"
            ><el-input v-model="draft.definition.sourceSystem" placeholder="例如 DPPMS"
          /></el-form-item>
          <el-form-item label="业务适配器"
            ><el-select v-model="draft.definition.adapter" @change="changeAdapter"
              ><el-option
                v-for="a in adapters"
                :key="a.key"
                :value="a.key"
                :label="a.label" /></el-select
          ></el-form-item>
          <el-form-item label="运行方式"
            ><el-select v-model="draft.definition.mode"
              ><el-option label="一次性迁移" value="ONCE" /><el-option
                label="全量快照同步"
                value="SNAPSHOT" /><el-option label="时间游标增量" value="INCREMENTAL" /></el-select
          ></el-form-item>
          <el-form-item label="上游消失策略"
            ><el-select v-model="draft.definition.missingPolicy"
              ><el-option
                v-for="policy in selectedAdapter?.missingPolicies"
                :key="policy"
                :value="policy"
                :label="policy === 'DISABLE' ? '保留并停用' : '保留并报告'" /></el-select
          ></el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="来源与字段映射" name="sources">
        <SourceEditor
          ref="sourceEditor"
          v-if="selectedAdapter"
          :key="String(draft.definition.connectionId) + draft.definition.adapter"
          v-model="draft.definition"
          :adapter="selectedAdapter"
        />
      </el-tab-pane>
      <el-tab-pane label="加载策略" name="loading">
        <el-form label-width="128px" class="integration-form" @submit.prevent>
          <el-form-item label="同步前阶段">
            <el-checkbox
              v-model="draft.definition.clearBeforeLoad"
              class="integration-clear-option"
              :disabled="!selectedAdapter?.supportsTargetClear"
              aria-label="在加载前截断目标表"
              @change="
                draft.definition.clearBeforeLoad &&
                (draft.definition.resetMappingsBeforeLoad = false)
              "
              >在加载前截断目标表（当前租户全部公司和部门）</el-checkbox
            >
          </el-form-item>
          <el-form-item label="保留目标表">
            <el-checkbox
              v-model="draft.definition.resetMappingsBeforeLoad"
              class="integration-clear-option"
              aria-label="仅重置映射后按源主键追加更新"
              :disabled="
                !selectedAdapter?.objects.every((object) => object.supportsSourcePrimaryKey)
              "
              @change="selectMappingReset"
              >仅重置当前任务映射，按源主键追加更新（不清空目标表）</el-checkbox
            >
          </el-form-item>
          <el-alert
            v-if="draft.definition.resetMappingsBeforeLoad"
            title="已自动启用所有对象的源主键同步及追加更新。不删除或重新编号已有组织。先完整预览，再手动确认；成功时才重建当前任务映射。相同编码但不同主键仍报告冲突，失败保留旧映射。旧映射中未出现于本次来源的目标记录保留，不作缺失停用。"
            type="warning"
            :closable="false"
          />
          <el-alert
            v-if="draft.definition.clearBeforeLoad"
            title="高风险：先清空全部目标公司/部门及其同步映射，再加载来源；旧目标 ID 和本地字段不会保留。存在用户、授权、项目或流程引用时阻止执行。必须先全量预览，再手动确认；禁止自动调度和自动重试。"
            type="warning"
            :closable="false"
          />
          <el-form-item label="加载方式">
            <el-select v-model="draft.definition.loadingMode" aria-label="加载方式">
              <el-option
                v-for="mode in selectedAdapter?.loadingModes"
                :key="mode"
                :value="mode"
                :label="loadingLabels[mode] || mode"
              />
            </el-select>
          </el-form-item>
          <el-alert
            title="同步前：检查连接与配置 → 来源读取及转换 → 业务预检；正式执行按读取策略进入整批、分页或流式分块事务。"
            type="info"
            :closable="false"
          />
          <p class="integration-note"
            >追加遇到已存在记录会整批失败；跳过保留已存在记录；更新只覆盖适配器声明的同步字段。所有方式均保留租户、归属和关联校验。</p
          >
          <p class="integration-note"
            >未选择截断时，上游消失按独立策略处理。截断通过业务接口执行租户内事务清理，保留其他租户和历史运行证据，不关闭约束或执行原生
            REPLACE INTO。源主键同步在“来源与字段映射”中按对象设置。</p
          >
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="调度与性能" name="schedule">
        <el-form
          :model="draft.definition"
          label-width="156px"
          class="integration-form"
          @submit.prevent
        >
          <el-form-item label="执行周期（Cron）"
            ><el-input v-model="draft.definition.cron"
          /></el-form-item>
          <el-form-item label="全量对账 Cron"
            ><el-input v-model="draft.definition.fullCron"
          /></el-form-item>
          <el-form-item label="增量重叠窗口（秒）"
            ><el-input-number v-model="draft.definition.overlapSeconds" :min="0"
          /></el-form-item>
          <el-form-item label="自动重试次数"
            ><el-input-number v-model="draft.definition.retryCount" :min="0" :max="10"
          /></el-form-item>
          <el-form-item label="重试间隔（秒）"
            ><el-input-number v-model="draft.definition.retryIntervalSeconds" :min="1"
          /></el-form-item>
          <el-form-item label="来源读取策略">
            <el-select v-model="draft.definition.readStrategy" @change="syncLegacyPaging">
              <el-option label="整批快照（兼容模式）" value="SNAPSHOT" />
              <el-option label="主键游标分页" value="KEYSET_PAGING" />
              <el-option label="JDBC 流式游标（单次 SQL）" value="STREAMING_CURSOR" />
            </el-select>
          </el-form-item>
          <template v-if="draft.definition.readStrategy === 'STREAMING_CURSOR'">
            <el-form-item label="JDBC Fetch Size">
              <el-input-number v-model="draft.definition.fetchSize" :min="1" :max="10000" />
            </el-form-item>
            <el-form-item label="提交 Chunk Size">
              <el-input-number v-model="draft.definition.chunkSize" :min="1" :max="5000" />
            </el-form-item>
            <el-form-item label="失败恢复策略">
              <el-select v-model="draft.definition.restartPolicy">
                <el-option label="重新执行整条 SQL（幂等写入）" value="RESTART_ALL" />
                <el-option label="按来源主键从已提交断点继续" value="CHECKPOINT_KEY" />
                <el-option label="失败后禁止关联重试" value="NO_RESTART" />
              </el-select>
            </el-form-item>
            <el-form-item label="SQL 超时（秒）">
              <el-input-number v-model="draft.definition.queryTimeoutSeconds" :min="0" :max="3600" />
            </el-form-item>
            <el-form-item label="预览样本上限">
              <el-input-number v-model="draft.definition.maxRows" :min="1" :max="10000" />
            </el-form-item>
            <el-alert
              type="info"
              :closable="false"
              title="流式模式每个来源 SQL 正常执行只打开一次 ResultSet，边读边按 Chunk 提交；Fetch Size 只控制来源拉取，Chunk Size 控制目标事务。SQL 超时为 0 时使用驱动默认/不主动限制。CHECKPOINT_KEY 仅在恢复时追加 sourceKey > 已提交断点。"
            />
          </template>
          <template v-else-if="draft.definition.readStrategy === 'KEYSET_PAGING'">
            <el-form-item label="单页最大行数">
              <el-input-number v-model="draft.definition.maxRows" :min="1" :max="10000" />
            </el-form-item>
            <el-alert
              type="info"
              :closable="false"
              title="按来源主键分段重复执行查询，每页独立提交并可从未提交页继续。复杂 SQL 若重复执行代价高，请改用 JDBC 流式游标。"
            />
          </template>
          <template v-else>
            <el-form-item label="整批最大行数">
              <el-input-number v-model="draft.definition.maxRows" :min="1" :max="10000" />
            </el-form-item>
          </template>
          <el-form-item label="单批最大字节">
            <el-input-number
              v-model="draft.definition.maxBytes"
              :min="1048576"
              :max="67108864"
              :step="1048576"
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
    <el-alert
      v-if="configurationCheck && !configurationCheck.allowed"
      :title="configurationCheck.message"
      type="error"
      :closable="false"
      role="alert"
    >
      <el-button
        v-if="configurationCheck.existingTaskId"
        type="primary"
        link
        @click="emit('openExisting', configurationCheck.existingTaskId)"
        >打开已有任务：{{ configurationCheck.existingTaskName }}</el-button
      >
    </el-alert>
    <p class="integration-note">{{
      draft?.definition.clearBeforeLoad || draft?.definition.resetMappingsBeforeLoad
        ? '保存后任务保持暂停。截断或重置映射仅支持完整预览后的手动确认执行，不可启用调度。'
        : '保存后任务保持暂停。完成当前版本的全量预览后，可在任务列表启用调度。'
    }}</p>
    <template #footer
      ><el-button @click="visible = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save">保存配置</el-button></template
    >
  </el-dialog>
</template>
<script setup lang="ts">
import * as api from '@/api/pms/integration'
import SourceEditor from './SourceEditor.vue'
import type { FormInstance } from 'element-plus'
const emit = defineEmits<{ saved: []; closed: []; openExisting: [id: api.Id] }>()
const configurationCheck = ref<api.ConfigurationCheck>()
const section = ref('basic')
const loadingLabels: Record<string, string> = {
  UPSERT: '追加并更新已存在（默认）',
  INSERT_ONLY: '追加（已存在则报错）',
  INSERT_IGNORE: '追加并跳过已存在'
}
const sourceEditor = ref<InstanceType<typeof SourceEditor>>()
const message = useMessage()
const visible = ref(false),
  saving = ref(false),
  draft = ref<api.Task>(),
  form = ref<FormInstance>()
const connections = ref<api.Connection[]>([]),
  adapters = ref<api.Adapter[]>([])
const selectedAdapter = computed(() =>
  adapters.value.find((a) => a.key === draft.value?.definition.adapter)
)
const normalizePerformance = () => {
  if (!draft.value) return
  const definition = draft.value.definition
  if (definition.autoPaging) definition.readStrategy = 'KEYSET_PAGING'
  definition.readStrategy ??= 'SNAPSHOT'
  definition.fetchSize ??= 2000
  definition.chunkSize ??= 1000
  definition.restartPolicy ??= 'RESTART_ALL'
  definition.queryTimeoutSeconds ??= 0
  definition.autoPaging = definition.readStrategy === 'KEYSET_PAGING'
}
const syncLegacyPaging = () => {
  if (!draft.value) return
  draft.value.definition.autoPaging = draft.value.definition.readStrategy === 'KEYSET_PAGING'
  if (draft.value.definition.readStrategy !== 'STREAMING_CURSOR') {
    draft.value.definition.restartPolicy ??= 'RESTART_ALL'
  }
}
const open = async (id?: api.Id) => {
  configurationCheck.value = undefined
  section.value = 'basic'
  ;[connections.value, adapters.value] = await Promise.all([
    api.getConnections().then((p) => p.list),
    api.getAdapters()
  ])
  if (!connections.value.length) {
    message.warning('请先在连接管理中配置来源')
    return
  }
  draft.value = id
    ? await api.getTask(id)
    : {
        name: 'EHR 公司与部门同步',
        version: 0,
        definition: await api.getEhrTemplate(connections.value[0].id)
      }
  draft.value.definition.loadingMode ??= 'UPSERT'
  normalizePerformance()
  visible.value = true
}
const changeAdapter = async () => {
  if (!draft.value || !selectedAdapter.value) return
  if (selectedAdapter.value.key === 'DPPMS_ERP_ORDER') {
    draft.value.definition = await api.getDppmsOrderTemplate(draft.value.definition.connectionId)
    normalizePerformance()
    if (draft.value.name === 'EHR 公司与部门同步') draft.value.name = 'DPPMS 销售订单与订单行迁移'
    return
  }
  draft.value.definition.loadingMode = selectedAdapter.value.loadingModes[0]
  draft.value.definition.readStrategy = 'SNAPSHOT'
  draft.value.definition.autoPaging = false
  draft.value.definition.fetchSize = 2000
  draft.value.definition.chunkSize = 1000
  draft.value.definition.restartPolicy = 'RESTART_ALL'
  draft.value.definition.queryTimeoutSeconds = 0
  draft.value.definition.sources = selectedAdapter.value.objects.map((o) => ({
    object: o.name,
    sourceObject: o.name,
    readMode: 'TABLE',
    table: '',
    parameters: {},
    sourceKey: '',
    columns: [],
    filters: [],
    mappings: o.fields.map((f) => ({
      target: f.name,
      source: '',
      conversion: f.type === 'REFERENCE' ? 'REFERENCE' : 'DIRECT'
    }))
  }))
}
const selectMappingReset = () => {
  if (!draft.value?.definition.resetMappingsBeforeLoad) return
  draft.value.definition.clearBeforeLoad = false
  draft.value.definition.loadingMode = 'UPSERT'
  draft.value.definition.readStrategy = 'SNAPSHOT'
  draft.value.definition.autoPaging = false
  draft.value.definition.sources.forEach((source) => (source.syncPrimaryKey = true))
}
const save = async () => {
  if (!draft.value) return
  normalizePerformance()
  if (!(await form.value?.validate().catch(() => false))) {
    section.value = 'basic'
    return
  }
  if (!sourceEditor.value?.validate()) {
    section.value = 'sources'
    return
  }
  saving.value = true
  try {
    configurationCheck.value = await api.checkTaskConfiguration(draft.value)
    if (!configurationCheck.value.allowed) return
    await api.saveTask(draft.value)
    visible.value = false
    message.success('配置已保存，任务保持暂停')
    emit('saved')
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>
<style scoped>
.integration-clear-option {
  height: auto;
  align-items: flex-start;
  max-width: 100%;
}
.integration-clear-option :deep(.el-checkbox__input) {
  margin-top: 4px;
}
.integration-clear-option :deep(.el-checkbox__label) {
  white-space: normal;
  overflow-wrap: anywhere;
  line-height: 1.6;
}
</style>
