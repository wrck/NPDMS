<template>
  <section class="inspection-page rules-page" aria-label="巡检规则库">
    <el-tabs v-model="activeTab" class="rules-tabs">
      <el-tab-pane label="规则库" name="rules">
        <ContentWrap class="inspection-card" :body-style="{}">
          <div class="inspection-card__header"><h2>规则筛选</h2></div>
          <el-alert
            v-if="!can('query')"
            title="没有巡检规则查询权限"
            type="warning"
            :closable="false"
          />
          <el-form
            v-else
            :model="query"
            label-position="top"
            class="rules-filters"
            @submit.prevent="search"
          >
            <el-form-item label="检测 ID"
              ><el-input v-model="query.detectionId" clearable @keyup.enter="search"
            /></el-form-item>
            <el-form-item label="规则名称"
              ><el-input v-model="query.ruleNameKeyword" clearable @keyup.enter="search"
            /></el-form-item>
            <el-form-item label="检测分类">
              <el-select v-model="query.categoryCode" clearable placeholder="全部分类">
                <el-option
                  v-for="item in categories"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="严重级别">
              <el-select v-model="query.severityCode" clearable placeholder="全部级别">
                <el-option
                  v-for="item in severities"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="产品类型编码"
              ><el-input v-model="query.productTypeCode" clearable @keyup.enter="search"
            /></el-form-item>
            <el-form-item label="状态">
              <el-select v-model="query.statusCode" clearable placeholder="全部状态">
                <el-option
                  v-for="item in statuses"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item class="rules-filter-actions">
              <el-button type="primary" @click="search"><Icon icon="ep:search" />查询</el-button>
              <el-button @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </ContentWrap>
        <ContentWrap v-if="can('query')" class="inspection-card" :body-style="{}">
          <div class="inspection-card__header">
            <h2>规则修订</h2>
            <div class="rules-list-actions"
              ><span class="help-text">共 {{ total }} 条</span>
              <el-button
                v-if="can('manage')"
                type="primary"
                :disabled="busy || !!pending"
                @click="openCreate"
              >
                <Icon icon="ep:plus" />新建规则
              </el-button>
            </div>
          </div>
          <el-alert
            v-if="listError"
            :title="listError"
            type="error"
            :closable="false"
            class="mb-16px"
          >
            <template #default
              ><el-button link type="primary" @click="loadList">重新加载</el-button></template
            >
          </el-alert>
          <el-table v-loading="listLoading" :data="rows" empty-text="暂无巡检规则修订">
            <el-table-column prop="detectionId" label="检测 ID" min-width="150" />
            <el-table-column
              prop="ruleNameSnapshot"
              label="规则名称"
              min-width="180"
              show-overflow-tooltip
            />
            <el-table-column
              prop="inspectionItem"
              label="检测项目"
              min-width="180"
              show-overflow-tooltip
            />
            <el-table-column label="分类" min-width="150"
              ><template #default="{ row }">{{
                row.categoryNameSnapshot || row.categoryCode || '未配置'
              }}</template></el-table-column
            >
            <el-table-column label="严重级别" min-width="100"
              ><template #default="{ row }">{{
                row.severityNameSnapshot || row.severityCode || '未配置'
              }}</template></el-table-column
            >
            <el-table-column prop="revisionNo" label="修订" width="80" align="center" />
            <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
            <el-table-column label="状态" width="100"
              ><template #default="{ row }"
                ><el-tag :type="statusType(row.statusCode)">{{
                  statusLabel(row.statusCode)
                }}</el-tag></template
              ></el-table-column
            >
            <el-table-column label="操作" width="180" :fixed="smallScreen ? false : 'right'">
              <template #default="{ row }">
                <el-button
                  link
                  type="primary"
                  :disabled="busy || !!pending"
                  @click="openDetail(row.revisionId)"
                  >{{ row.statusCode === 'DRAFT' && can('manage') ? '编辑' : '查看' }}</el-button
                >
                <el-button link type="primary" @click="openHistory(row)">修订历史</el-button>
              </template>
            </el-table-column>
          </el-table>
          <Pagination
            :total="total"
            v-model:page="query.pageNo"
            v-model:limit="query.pageSize"
            @pagination="loadList"
          />
        </ContentWrap>
      </el-tab-pane>
      <el-tab-pane
        v-if="checkPermi(['pms:inspection-feedback:query'])"
        label="反馈分析"
        name="feedback"
        lazy
      >
        <InspectionFeedbackPanel @open-rule-draft="openFeedbackDraft" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      class="inspection-page rules-dialog"
      v-model="createVisible"
      title="新建巡检规则"
      width="min(560px, 94vw)"
      :close-on-click-modal="false"
      :before-close="closeCreate"
    >
      <el-alert
        title="检测 ID 和规则名称创建后不可修改；其余配置可在草稿中逐步补充。"
        type="info"
        :closable="false"
        class="mb-16px"
      />
      <el-form label-width="90px" :model="identity" :disabled="busy || !!pending">
        <el-form-item label="检测 ID" required
          ><el-input v-model="identity.detectionId" maxlength="64"
        /></el-form-item>
        <el-form-item label="规则名称" required
          ><el-input v-model="identity.ruleName" maxlength="128"
        /></el-form-item>
      </el-form>
      <el-alert
        v-if="pending?.operation === 'create'"
        :title="pendingMessage"
        type="warning"
        :closable="false"
      />
      <template #footer>
        <el-button
          :disabled="busy || !!pending"
          @click="
            closeCreate(() => {
              createVisible = false
            })
          "
          >取消</el-button
        >
        <el-button
          v-if="pending?.operation === 'create'"
          type="warning"
          :loading="busy"
          @click="retryPending"
          >{{ pending.phase === 'refresh' ? '重新读取结果' : '重试原操作' }}</el-button
        >
        <el-button v-else type="primary" :loading="busy" @click="createRule">创建草稿</el-button>
      </template>
    </el-dialog>
    <el-drawer
      class="inspection-page rules-drawer"
      v-model="editorVisible"
      :title="editorTitle"
      :size="smallScreen ? '100%' : 'min(980px, 92vw)'"
      :before-close="closeEditor"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <el-alert
          v-if="detailError"
          :title="detailError"
          type="error"
          :closable="false"
          class="mb-16px"
        >
          <template #default
            ><el-button link type="primary" :disabled="busy" @click="refreshDetail"
              >重新读取详情</el-button
            ></template
          >
        </el-alert>
        <el-alert
          v-if="pending"
          :title="pendingMessage"
          type="warning"
          :closable="false"
          class="mb-16px"
        >
          <template #default
            ><el-button type="warning" :loading="busy" @click="retryPending">{{
              pending.phase === 'refresh' ? '重新读取结果' : '重试原操作'
            }}</el-button></template
          >
        </el-alert>
        <template v-if="detail">
          <el-descriptions :column="smallScreen ? 1 : 3" border class="mb-16px">
            <el-descriptions-item label="检测 ID">{{ detail.detectionId }}</el-descriptions-item>
            <el-descriptions-item label="规则名称">{{
              detail.ruleNameSnapshot
            }}</el-descriptions-item>
            <el-descriptions-item label="状态"
              ><el-tag :type="statusType(detail.statusCode)"
                >{{ statusLabel(detail.statusCode) }} / 修订 {{ detail.revisionNo }}</el-tag
              ></el-descriptions-item
            >
            <el-descriptions-item v-if="detail.publishedAt" label="发布时间">{{
              formatDate(detail.publishedAt)
            }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.disabledAt" label="停用时间">{{
              formatDate(detail.disabledAt)
            }}</el-descriptions-item>
          </el-descriptions>
          <el-alert
            v-if="detail.statusCode !== 'DRAFT'"
            title="历史修订内容只读。修改配置请复制为新草稿，历史版本继续保留。"
            type="info"
            :closable="false"
            class="mb-16px"
          />
          <el-alert
            v-if="!categories.length || !severities.length"
            title="巡检分类或严重级别字典尚未配置，请先完成平台字典配置后选择。"
            type="warning"
            :closable="false"
            class="mb-16px"
          />
          <el-form
            class="rule-editor-form"
            label-position="top"
            :model="form"
            :disabled="readonly || busy || !!pending || !fresh"
          >
            <section class="rule-editor-section">
              <h2 class="section-title inspection-section-title">基本配置</h2>
              <div class="field-grid">
                <el-form-item label="检测项目"
                  ><el-input v-model="form.inspectionItem"
                /></el-form-item>
                <el-form-item label="排序"
                  ><el-input-number
                    v-model="form.sortOrder"
                    :precision="0"
                    controls-position="right"
                /></el-form-item>
                <el-form-item label="检测分类">
                  <el-select
                    v-model="form.categoryCode"
                    clearable
                    :disabled="!categories.length"
                    @change="changeCategory"
                  >
                    <el-option
                      v-for="item in categories"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="严重级别">
                  <el-select
                    v-model="form.severityCode"
                    clearable
                    :disabled="!severities.length"
                    @change="changeSeverity"
                  >
                    <el-option
                      v-for="item in severities"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </el-select>
                </el-form-item>
              </div>
              <el-form-item label="规则描述"
                ><el-input v-model="form.description" type="textarea" :rows="3"
              /></el-form-item>
            </section>
            <section class="rule-editor-section">
              <div class="section-heading"
                ><h2 class="section-title inspection-section-title">命令列表</h2
                ><el-button v-if="!readonly" @click="addCommand">添加命令</el-button></div
              >
              <p class="help-text"
                >列表顺序即执行顺序，超时范围为 1～30
                秒。命令和正则内容需由有权限的审核人独立审核。</p
              >
              <el-empty v-if="!form.commands.length" description="尚未配置命令" :image-size="64" />
              <div
                v-for="(command, index) in form.commands"
                :key="command.stableCommandKey"
                class="command-row"
              >
                <div class="section-heading">
                  <h3 class="command-title">命令 {{ index + 1 }}</h3>
                  <div v-if="!readonly">
                    <el-button
                      link
                      :disabled="index === 0"
                      :aria-label="`上移命令 ${index + 1}`"
                      @click="moveCommand(index, -1)"
                      >上移</el-button
                    >
                    <el-button
                      link
                      :disabled="index === form.commands.length - 1"
                      :aria-label="`下移命令 ${index + 1}`"
                      @click="moveCommand(index, 1)"
                      >下移</el-button
                    >
                    <el-button
                      link
                      type="danger"
                      :aria-label="`删除命令 ${index + 1}`"
                      @click="removeCommand(index)"
                      >删除</el-button
                    >
                  </div>
                </div>
                <el-form-item :label="`命令 ${index + 1} 内容`"
                  ><el-input v-model="command.commandContent" type="textarea" :rows="2"
                /></el-form-item>
                <div class="command-options">
                  <el-form-item label="超时（秒）"
                    ><el-input-number
                      v-model="command.timeoutSeconds"
                      :min="1"
                      :max="30"
                      :precision="0"
                  /></el-form-item>
                  <el-form-item label="超时处理"
                    ><el-checkbox v-model="command.continueOnTimeout"
                      >超时后继续执行后续命令</el-checkbox
                    ></el-form-item
                  >
                </div>
              </div>
            </section>
            <section class="rule-editor-section">
              <h2 class="section-title inspection-section-title">判定规则</h2>
              <el-form-item label="预期结果正则"
                ><el-input v-model="form.expectedResultRegex" type="textarea" :rows="2"
              /></el-form-item>
              <div class="threshold-grid">
                <el-form-item label="阈值数据类型"
                  ><el-select v-model="form.thresholdDataType" clearable
                    ><el-option label="数值（NUMBER）" value="NUMBER" /></el-select
                ></el-form-item>
                <el-form-item label="运算符"
                  ><el-select v-model="form.thresholdOperator" clearable
                    ><el-option
                      v-for="operator in operators"
                      :key="operator"
                      :label="operator"
                      :value="operator" /></el-select
                ></el-form-item>
                <el-form-item label="阈值"
                  ><el-input
                    v-model="form.thresholdValue"
                    inputmode="decimal"
                    placeholder="填写数值"
                /></el-form-item>
                <el-form-item label="单位"><el-input v-model="form.thresholdUnit" /></el-form-item>
              </div>
            </section>
            <section class="rule-editor-section">
              <div class="section-heading"
                ><h2 class="section-title inspection-section-title">适用产品类型</h2
                ><el-button v-if="!readonly" @click="addProduct">添加产品类型</el-button></div
              >
              <p class="help-text"
                >填写资产产品类型的权威编码，发布预检返回对应名称后可应用名称候选并保存。</p
              >
              <el-empty
                v-if="!form.productTypes.length"
                description="尚未配置适用产品类型"
                :image-size="64"
              />
              <div v-for="(product, index) in form.productTypes" :key="index" class="product-row">
                <el-form-item :label="`产品类型 ${index + 1} 编码`"
                  ><el-input
                    v-model="product.productTypeCode"
                    @input="product.productTypeNameSnapshot = ''"
                /></el-form-item>
                <el-form-item label="权威名称"
                  ><el-input
                    :model-value="product.productTypeNameSnapshot || '待发布预检解析'"
                    readonly
                /></el-form-item>
                <el-button
                  v-if="!readonly"
                  link
                  type="danger"
                  :aria-label="`删除产品类型 ${index + 1}`"
                  @click="form.productTypes.splice(index, 1)"
                  >删除</el-button
                >
              </div>
            </section>
          </el-form>

          <section
            v-if="validation"
            ref="validationPanel"
            class="validation-panel rule-editor-section"
            aria-live="polite"
          >
            <h2 class="section-title inspection-section-title">发布预检</h2>
            <el-alert
              :type="validation.errors.length ? 'warning' : 'success'"
              :title="
                validation.errors.length
                  ? `存在 ${validation.errors.length} 项发布校验问题`
                  : '预检未发现字段问题，发布仍以服务端校验为准'
              "
              :closable="false"
            />
            <el-table v-if="validation.errors.length" :data="validation.errors" class="mt-12px">
              <el-table-column type="expand" width="42"
                ><template #default="{ row }">
                  <el-descriptions :column="1" class="validation-details">
                    <el-descriptions-item label="字段路径">{{ row.location }}</el-descriptions-item>
                    <el-descriptions-item label="问题代码">{{ row.code }}</el-descriptions-item>
                    <el-descriptions-item label="服务端说明">{{
                      row.message || row.code
                    }}</el-descriptions-item>
                  </el-descriptions>
                </template></el-table-column
              >
              <el-table-column label="配置项" min-width="180"
                ><template #default="{ row }">{{
                  validationFieldLabel(row.location)
                }}</template></el-table-column
              >
              <el-table-column label="问题说明" min-width="280"
                ><template #default="{ row }">{{
                  validationErrorText(row)
                }}</template></el-table-column
              >
            </el-table>
            <template v-if="hasCandidates">
              <p class="help-text">服务端返回以下权威名称候选。应用后需保存草稿，再次预检。</p>
              <ul class="candidate-list">
                <li v-for="item in validation.dictionaryNameCandidates" :key="item.field"
                  >{{ validationFieldLabel(item.field) }}：{{ item.value }} →
                  {{ item.authoritativeDisplayName }}</li
                >
                <li v-for="item in validation.productTypeNameCandidates" :key="item.productTypeCode"
                  >产品类型 {{ item.productTypeCode }} → {{ item.authoritativeDisplayName }}</li
                >
              </ul>
              <el-button
                v-if="can('manage') && detail.statusCode === 'DRAFT'"
                :disabled="busy || !!pending || !fresh"
                @click="applyCandidates"
                >应用权威名称候选</el-button
              >
            </template>
          </section>
          <section
            v-if="detail.statusCode === 'DRAFT' && can('security-review')"
            class="validation-panel rule-editor-section"
          >
            <h2 class="section-title inspection-section-title">命令与正则安全审核</h2>
            <p class="help-text"
              >审核针对已保存的当前修订内容追加结论。修改命令或正则后，发布须再次通过相应内容的安全审核。</p
            >
            <el-button
              type="success"
              plain
              :disabled="actionsBlocked || dirty"
              @click="review('PASSED')"
              >审核通过</el-button
            >
            <el-button
              type="danger"
              plain
              :disabled="actionsBlocked || dirty"
              @click="review('REJECTED')"
              >审核拒绝</el-button
            >
          </section>
        </template>
      </div>
      <template #footer>
        <div class="drawer-footer">
          <span v-if="dirty" class="help-text" role="status">有未保存修改</span>
          <el-button
            :disabled="busy || !!pending"
            @click="
              closeEditor(() => {
                editorVisible = false
              })
            "
            >关闭</el-button
          >
          <template v-if="detail">
            <el-button
              v-if="detail.statusCode === 'DRAFT' && can('manage')"
              :disabled="actionsBlocked || dirty"
              :loading="validating"
              @click="validate"
              >发布预检</el-button
            >
            <el-button
              v-if="detail.statusCode === 'DRAFT' && can('manage')"
              type="primary"
              :disabled="actionsBlocked"
              @click="save"
              >保存草稿</el-button
            >
            <el-button
              v-if="detail.statusCode === 'DRAFT' && can('publish')"
              type="success"
              :disabled="actionsBlocked || dirty"
              @click="publish"
              >发布修订</el-button
            >
            <el-button
              v-if="detail.statusCode !== 'DRAFT' && can('manage')"
              type="primary"
              :disabled="actionsBlocked"
              @click="copyRevision"
              >复制为新草稿</el-button
            >
            <el-button
              v-if="detail.statusCode === 'PUBLISHED' && can('disable')"
              type="danger"
              :disabled="actionsBlocked"
              @click="disable"
              >停用修订</el-button
            >
          </template>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      class="inspection-page rules-dialog"
      v-model="historyVisible"
      :title="historyTitle"
      width="min(900px, 94vw)"
    >
      <el-alert
        v-if="historyError"
        :title="historyError"
        type="error"
        :closable="false"
        class="mb-12px"
        ><template #default
          ><el-button link @click="loadHistory">重新加载</el-button></template
        ></el-alert
      >
      <el-table v-loading="historyLoading" :data="historyRows" empty-text="暂无修订历史">
        <el-table-column prop="revisionNo" label="修订" width="80" />
        <el-table-column label="状态" width="100"
          ><template #default="{ row }">{{
            statusLabel(row.statusCode)
          }}</template></el-table-column
        >
        <el-table-column
          prop="publishedAt"
          label="发布时间"
          min-width="170"
          :formatter="dateFormatter"
        />
        <el-table-column
          prop="disabledAt"
          label="停用时间"
          min-width="170"
          :formatter="dateFormatter"
        />
        <el-table-column label="操作" width="100"
          ><template #default="{ row }"
            ><el-button link type="primary" :disabled="busy || !!pending" @click="viewHistory(row)"
              >查看修订</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <Pagination
        :total="historyTotal"
        v-model:page="historyQuery.pageNo"
        v-model:limit="historyQuery.pageSize"
        @pagination="loadHistory"
      />
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useMediaQuery } from '@vueuse/core'
import { generateUUID } from '@/utils'
import { checkPermi } from '@/utils/permission'
import { getStrDictOptions } from '@/utils/dict'
import { formatDate, dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import * as RuleApi from '@/api/pms/inspection/rules'
import type { DraftInput, RuleView, ValidationResult, WireLong } from '@/api/pms/inspection/rules'
import InspectionFeedbackPanel from '../components/InspectionFeedbackPanel.vue'
import '../shared/inspection.css'

defineOptions({ name: 'PmsInspectionRules' })
const message = useMessage()
const activeTab = ref('rules')
const openFeedbackDraft = (revisionId: WireLong) => {
  activeTab.value = 'rules'
  void openDetail(revisionId)
}
const smallScreen = useMediaQuery('(max-width: 768px)')
const can = (action: string) => checkPermi([`pms:inspection-rule:${action}`])
const categories = computed(() => getStrDictOptions('pms_inspection_rule_category'))
const severities = computed(() => getStrDictOptions('pms_inspection_rule_severity'))
const statuses = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'DISABLED', label: '已停用' }
]
const operators = ['>', '<', '≥', '≤', '=', '≠']
const statusLabel = (status: string) =>
  statuses.find((item) => item.value === status)?.label || status
const statusType = (status: string): 'info' | 'success' | 'warning' =>
  status === 'PUBLISHED' ? 'success' : status === 'DISABLED' ? 'warning' : 'info'
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  detectionId: '',
  ruleNameKeyword: '',
  categoryCode: '',
  severityCode: '',
  productTypeCode: '',
  statusCode: ''
})
const rows = ref<RuleView[]>([])
const total = ref(0)
const listLoading = ref(false)
const listError = ref('')
let listSequence = 0
const editorVisible = ref(false)
const createVisible = ref(false)
const identity = reactive({ detectionId: '', ruleName: '' })
const detail = ref<RuleView>()
const selectedId = ref<WireLong>()
const detailLoading = ref(false)
const detailError = ref('')
const fresh = ref(false)
let detailSequence = 0
const form = ref<DraftInput>(emptyDraft())
const baseline = ref('')
const dirty = computed(() => !!detail.value && JSON.stringify(form.value) !== baseline.value)
const readonly = computed(() => detail.value?.statusCode !== 'DRAFT' || !can('manage'))
const editorTitle = computed(() =>
  detail.value
    ? `巡检规则：${detail.value.ruleNameSnapshot} / 修订 ${detail.value.revisionNo}`
    : '巡检规则详情'
)
const busy = ref(false)
const validating = ref(false)
const validation = ref<ValidationResult>()
const validationPanel = ref<HTMLElement>()
const validationFields: Record<string, string> = {
  status: '修订状态',
  detectionId: '检测 ID',
  ruleName: '规则名称',
  inspectionItem: '检测项目',
  description: '规则描述',
  categoryCode: '检测分类',
  severityCode: '严重级别',
  sortOrder: '排序',
  commands: '命令列表',
  expectedResultRegex: '预期结果正则',
  threshold: '判定阈值',
  'threshold.dataType': '阈值数据类型',
  'threshold.operator': '阈值运算符',
  'threshold.value': '阈值',
  'threshold.unit': '阈值单位',
  productTypes: '适用产品类型'
}
function validationFieldLabel(location: string) {
  if (validationFields[location]) return validationFields[location]
  const command =
    /^commands\[(\d+)\](?:\.(stableCommandKey|content|executionOrder|timeoutSeconds))?$/.exec(
      location
    )
  if (command) {
    const fields: Record<string, string> = {
      stableCommandKey: '命令标识',
      content: '命令内容',
      executionOrder: '执行顺序',
      timeoutSeconds: '超时（秒）'
    }
    return `第 ${Number(command[1]) + 1} 条命令${command[2] ? ` · ${fields[command[2]]}` : ''}`
  }
  const product = /^productTypes\[(\d+)\]$/.exec(location)
  return product ? `第 ${Number(product[1]) + 1} 个产品类型编码` : location
}
const validationMessages: Record<string, string> = {
  NOT_EDITABLE: '当前修订不是可编辑的草稿。',
  REQUIRED: '此项为发布必填，请补充配置。',
  DUPLICATE: '存在重复配置，请检查。',
  NOT_CONTINUOUS: '命令顺序须从 1 开始连续排列，且不能重复。',
  OUT_OF_RANGE: '取值超出允许范围。',
  INVALID: '请选择有效的阈值运算符。',
  UNSUPPORTED_VALUE: '当前配置值不受支持，请核对。',
  REGEX_TOO_LONG: '正则表达式不能超过 1024 个字符，请缩短。',
  REGEX_UNSUPPORTED_FEATURE: '正则包含不支持的特性，请使用受支持的正则语法。',
  REGEX_COMPLEXITY_EXCEEDED: '正则的分组、嵌套、分支或量词超过复杂度限制，请简化表达式。',
  INVALID_SYNTAX: '正则表达式语法不正确，请检查。',
  SECRET_DETECTED: '检测到疑似密码、私钥或认证信息，请移除明文敏感内容。',
  DEPENDENCY_TEMPORARY: '权威信息查询暂不可用，请稍后重试发布预检。',
  DEPENDENCY_REJECTED: '当前配置未通过权威信息校验，请核对。'
}
function validationErrorText(error: ValidationResult['errors'][number]) {
  if (error.message && error.message !== error.code) return error.message
  const { location, code } = error
  if (code === 'REQUIRED' && location === 'commands') return '请至少添加一条巡检命令。'
  if (code === 'REQUIRED' && location === 'productTypes') return '请至少添加一个适用产品类型。'
  if (code === 'REQUIRED' && location === 'threshold') return '请补充判定阈值配置。'
  if (code === 'OUT_OF_RANGE' && /^commands\[\d+\]\.timeoutSeconds$/.test(location))
    return '命令超时须在 1～30 秒之间。'
  if (code === 'UNSUPPORTED_VALUE' && location === 'threshold.dataType')
    return '当前仅支持数值（NUMBER）阈值。'
  if (code === 'UNSUPPORTED_VALUE' && ['categoryCode', 'severityCode'].includes(location))
    return '请选择支持且已启用的字典值。'
  if (code === 'DEPENDENCY_REJECTED' && /^productTypes\[\d+\]$/.test(location))
    return '产品类型不存在、已停用或权威来源信息不完整，请核对资产产品类型。'
  if (code === 'DEPENDENCY_REJECTED' && ['categoryCode', 'severityCode'].includes(location))
    return '平台字典未返回可用名称，请检查字典配置。'
  return validationMessages[code] || error.message || code
}
const hasCandidates = computed(
  () =>
    !!validation.value &&
    !!(
      validation.value.dictionaryNameCandidates.length ||
      validation.value.productTypeNameCandidates.length
    )
)
const actionsBlocked = computed(
  () => busy.value || validating.value || detailLoading.value || !!pending.value || !fresh.value
)
type Operation = 'create' | 'save' | 'copy' | 'review' | 'publish' | 'disable'
interface PendingWrite {
  operation: Operation
  revisionId?: WireLong
  version?: number
  key: string
  body?:
    | DraftInput
    | { detectionId: string; ruleName: string }
    | { conclusionCode: 'PASSED' | 'REJECTED' }
  phase: 'write' | 'refresh'
  resultId?: WireLong
}
// 未知结果保留原请求，成功写入但读失败只重读，不再发起下一项写操作。
const pending = ref<PendingWrite>()
const pendingMessage = computed(() =>
  pending.value?.phase === 'refresh'
    ? '操作已返回成功，但详情读取失败。请重新读取结果后继续。'
    : '操作结果尚未确认，已保留本次请求。请重试原操作，避免重复写入。'
)
const historyVisible = ref(false)
const historyRows = ref<RuleView[]>([])
const historyTotal = ref(0)
const historyLoading = ref(false)
const historyError = ref('')
const historyTitle = ref('规则修订历史')
const historyQuery = reactive({
  ruleId: undefined as WireLong | undefined,
  pageNo: 1,
  pageSize: 10
})
let historySequence = 0

function emptyDraft(): DraftInput {
  return {
    inspectionItem: '',
    description: '',
    categoryCode: '',
    categoryNameSnapshot: '',
    severityCode: '',
    severityNameSnapshot: '',
    sortOrder: undefined,
    expectedResultRegex: '',
    thresholdDataType: '',
    thresholdOperator: '',
    thresholdValue: undefined,
    thresholdUnit: '',
    commands: [],
    productTypes: []
  }
}
function draftOf(value: RuleView): DraftInput {
  return {
    inspectionItem: value.inspectionItem ?? '',
    description: value.description ?? '',
    categoryCode: value.categoryCode ?? '',
    categoryNameSnapshot: value.categoryNameSnapshot ?? '',
    severityCode: value.severityCode ?? '',
    severityNameSnapshot: value.severityNameSnapshot ?? '',
    sortOrder: value.sortOrder ?? undefined,
    expectedResultRegex: value.expectedResultRegex ?? '',
    thresholdDataType: value.thresholdDataType ?? '',
    thresholdOperator: value.thresholdOperator ?? '',
    thresholdValue: value.thresholdValue ?? undefined,
    thresholdUnit: value.thresholdUnit ?? '',
    commands: clone(value.commands || []),
    productTypes: clone(value.productTypes || [])
  }
}
function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value))
}
function errorMessage(error: unknown, fallback: string) {
  const responseMessage = (error as { response?: { data?: { msg?: string } } })?.response?.data?.msg
  return responseMessage || (error instanceof Error ? error.message : fallback)
}
async function loadList() {
  if (!can('query')) return
  const sequence = ++listSequence
  listLoading.value = true
  listError.value = ''
  try {
    const result = await RuleApi.getRulePage({ ...query })
    if (sequence !== listSequence) return
    rows.value = result.list
    total.value = result.total
  } catch (error) {
    if (sequence === listSequence) listError.value = errorMessage(error, '规则列表加载失败')
  } finally {
    if (sequence === listSequence) listLoading.value = false
  }
}
function search() {
  query.pageNo = 1
  void loadList()
}
function resetQuery() {
  Object.assign(query, {
    pageNo: 1,
    detectionId: '',
    ruleNameKeyword: '',
    categoryCode: '',
    severityCode: '',
    productTypeCode: '',
    statusCode: ''
  })
  void loadList()
}
async function confirmDiscard() {
  if (busy.value || pending.value || validating.value) {
    message.warning('请先完成当前操作或确认其结果')
    return false
  }
  if (!dirty.value && !(createVisible.value && (identity.detectionId || identity.ruleName)))
    return true
  try {
    await message.confirm('有未保存的修改，是否放弃并继续？')
    return true
  } catch {
    return false
  }
}
async function openCreate() {
  if (!can('manage') || !(await confirmDiscard())) return
  editorVisible.value = false
  ++detailSequence
  detail.value = undefined
  selectedId.value = undefined
  fresh.value = false
  validation.value = undefined
  Object.assign(identity, { detectionId: '', ruleName: '' })
  createVisible.value = true
}
async function closeCreate(done: () => void) {
  if (await confirmDiscard()) {
    Object.assign(identity, { detectionId: '', ruleName: '' })
    done()
  }
}
async function closeEditor(done: () => void) {
  if (!(await confirmDiscard())) return
  ++detailSequence
  detail.value = undefined
  selectedId.value = undefined
  fresh.value = false
  done()
}
async function openDetail(id: WireLong) {
  if (!can('query') || !(await confirmDiscard())) return false
  createVisible.value = false
  detail.value = undefined
  selectedId.value = id
  validation.value = undefined
  editorVisible.value = true
  return readDetail(id)
}
async function readDetail(id: WireLong) {
  const sequence = ++detailSequence
  selectedId.value = id
  fresh.value = false
  detailLoading.value = true
  detailError.value = ''
  try {
    const result = await RuleApi.getRuleRevision(id)
    if (sequence !== detailSequence || String(selectedId.value) !== String(id)) return false
    detail.value = result
    form.value = draftOf(result)
    baseline.value = JSON.stringify(form.value)
    fresh.value = true
    return true
  } catch (error) {
    if (sequence === detailSequence)
      detailError.value = errorMessage(error, '详情读取失败，继续写入前需重新读取')
    return false
  } finally {
    if (sequence === detailSequence) detailLoading.value = false
  }
}
async function refreshDetail() {
  if (pending.value) {
    await retryPending()
    return
  }
  if (selectedId.value === undefined || !(await confirmDiscard())) return
  validation.value = undefined
  await readDetail(selectedId.value)
}
function changeCategory(value: string) {
  form.value.categoryNameSnapshot =
    categories.value.find((item) => item.value === value)?.label || ''
}
function changeSeverity(value: string) {
  form.value.severityNameSnapshot =
    severities.value.find((item) => item.value === value)?.label || ''
}
function reorderCommands() {
  form.value.commands.forEach((command, index) => {
    command.executionOrder = index + 1
  })
}
function addCommand() {
  form.value.commands.push({
    stableCommandKey: generateUUID(),
    commandContent: '',
    executionOrder: form.value.commands.length + 1,
    timeoutSeconds: 30,
    continueOnTimeout: false
  })
}
function removeCommand(index: number) {
  form.value.commands.splice(index, 1)
  reorderCommands()
}
function moveCommand(index: number, direction: number) {
  const next = index + direction
  if (next < 0 || next >= form.value.commands.length) return
  const [command] = form.value.commands.splice(index, 1)
  form.value.commands.splice(next, 0, command)
  reorderCommands()
}
function addProduct() {
  form.value.productTypes.push({ productTypeCode: '', productTypeNameSnapshot: '' })
}
function applyCandidates() {
  if (!validation.value || readonly.value || actionsBlocked.value) return
  for (const candidate of validation.value.dictionaryNameCandidates) {
    if (candidate.field === 'categoryCode' && form.value.categoryCode === candidate.value)
      form.value.categoryNameSnapshot = candidate.authoritativeDisplayName
    if (candidate.field === 'severityCode' && form.value.severityCode === candidate.value)
      form.value.severityNameSnapshot = candidate.authoritativeDisplayName
  }
  for (const candidate of validation.value.productTypeNameCandidates) {
    form.value.productTypes
      .filter((item) => item.productTypeCode === candidate.productTypeCode)
      .forEach((item) => {
        item.productTypeNameSnapshot = candidate.authoritativeDisplayName
      })
  }
}
async function validate() {
  if (!detail.value || actionsBlocked.value || !can('manage')) return false
  if (dirty.value) {
    message.warning('请先保存草稿，再预检已保存的内容')
    return false
  }
  const id = detail.value.revisionId
  const sequence = detailSequence
  validating.value = true
  try {
    const result = await RuleApi.validateRuleRevision(id)
    if (sequence !== detailSequence || String(detail.value?.revisionId) !== String(id)) return false
    validation.value = result
    await nextTick()
    if (
      sequence !== detailSequence ||
      String(detail.value?.revisionId) !== String(id) ||
      String(selectedId.value) !== String(id) ||
      !editorVisible.value
    )
      return false
    validationPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return !result.errors.length
  } catch (error) {
    message.error(errorMessage(error, '发布预检失败'))
    return false
  } finally {
    validating.value = false
  }
}
function allowed(operation: Operation) {
  const permission =
    operation === 'review'
      ? 'security-review'
      : operation === 'publish'
        ? 'publish'
        : operation === 'disable'
          ? 'disable'
          : 'manage'
  return can(permission)
}
async function createRule() {
  if (busy.value || pending.value || !can('manage')) return
  if (!identity.detectionId.trim() || !identity.ruleName.trim()) {
    message.warning('请填写检测 ID 和规则名称')
    return
  }
  await startWrite({
    operation: 'create',
    body: { detectionId: identity.detectionId.trim(), ruleName: identity.ruleName.trim() }
  })
}
async function save() {
  if (!detail.value || actionsBlocked.value || readonly.value) return
  const draft = clone(form.value)
  if (draft.thresholdValue === '') draft.thresholdValue = undefined
  await startWrite({ operation: 'save', body: draft })
}
async function review(conclusionCode: 'PASSED' | 'REJECTED') {
  if (!detail.value || actionsBlocked.value || dirty.value || !can('security-review')) return
  try {
    await message.confirm(
      `确认对当前已保存的命令与正则追加“${conclusionCode === 'PASSED' ? '审核通过' : '审核拒绝'}”结论？`
    )
  } catch {
    return
  }
  await startWrite({ operation: 'review', body: { conclusionCode } })
}
async function publish() {
  if (!detail.value || actionsBlocked.value || dirty.value || !can('publish')) return
  if (can('manage') && !(await validate())) {
    message.warning('请处理发布预检问题后再发布')
    return
  }
  try {
    await message.confirm('确认发布此修订？发布后内容不可修改，新任务使用当前有效版本。')
  } catch {
    return
  }
  await startWrite({ operation: 'publish' })
}
async function copyRevision() {
  if (!detail.value || actionsBlocked.value || !can('manage')) return
  await startWrite({ operation: 'copy' })
}
async function disable() {
  if (!detail.value || actionsBlocked.value || !can('disable')) return
  try {
    await message.confirm('确认停用此已发布修订？历史修订继续保留。')
  } catch {
    return
  }
  await startWrite({ operation: 'disable' })
}
async function startWrite(input: Pick<PendingWrite, 'operation' | 'body'>) {
  if (busy.value || pending.value || !allowed(input.operation)) return
  if (input.operation !== 'create' && (!detail.value || !fresh.value)) return
  pending.value = {
    ...input,
    body: input.body ? clone(input.body) : undefined,
    revisionId: detail.value?.revisionId,
    version: detail.value?.version,
    key: generateUUID(),
    phase: 'write'
  }
  await retryPending()
}
function definitelyRejected(error: unknown) {
  // 平台响应拦截器把明确的业务拒绝转换为固定字符串。
  if (error === 'error') return true
  const value = error as {
    response?: { status?: number; data?: { data?: { reasonCode?: string; retryable?: boolean } } }
  }
  const result = value?.response?.data?.data
  if (result?.retryable || result?.reasonCode === 'IN_PROGRESS') return false
  const status = value?.response?.status
  return !!status && status >= 400 && status < 500 && status !== 408
}
async function retryPending() {
  const request = pending.value
  if (!request || busy.value || !allowed(request.operation)) return
  busy.value = true
  try {
    if (request.phase === 'write') {
      const id = request.revisionId as WireLong
      const version = request.version as number
      let result: { revisionId?: WireLong } | undefined
      switch (request.operation) {
        case 'create':
          result = await RuleApi.createRule(
            request.body as { detectionId: string; ruleName: string },
            request.key
          )
          break
        case 'save':
          result = await RuleApi.saveRuleRevision(
            id,
            request.body as DraftInput,
            version,
            request.key
          )
          break
        case 'copy':
          result = await RuleApi.copyRuleRevision(id, version, request.key)
          break
        case 'review':
          await RuleApi.recordRuleSecurityReview(
            id,
            request.body as { conclusionCode: 'PASSED' | 'REJECTED' },
            version,
            request.key
          )
          break
        case 'publish':
          await RuleApi.publishRuleRevision(id, version, request.key)
          break
        case 'disable':
          await RuleApi.disableRuleRevision(id, version, request.key)
          break
      }
      request.resultId = result?.revisionId ?? request.revisionId
      request.phase = 'refresh'
      fresh.value = false
      validation.value = undefined
    }
    if (request.resultId === undefined) {
      message.error('操作已返回，但没有返回修订 ID，无法读取结果')
      return
    }
    createVisible.value = false
    editorVisible.value = true
    if (!(await readDetail(request.resultId))) return
    pending.value = undefined
    createVisible.value = false
    Object.assign(identity, { detectionId: '', ruleName: '' })
    message.success('操作完成，已读取最新修订')
    void loadList()
    if (historyVisible.value) void loadHistory()
  } catch (error) {
    fresh.value = false
    if (request.phase === 'write' && definitelyRejected(error)) {
      pending.value = undefined
      detailError.value = '操作被拒绝，请重新读取当前修订后继续。未保存的内容仍保留。'
    }
    message.error(errorMessage(error, '操作失败，请确认原操作结果后继续'))
  } finally {
    busy.value = false
  }
}
async function openHistory(row: RuleView) {
  historyRows.value = []
  historyTotal.value = 0
  historyQuery.ruleId = row.ruleId
  historyQuery.pageNo = 1
  historyTitle.value = `修订历史：${row.ruleNameSnapshot}`
  historyVisible.value = true
  await loadHistory()
}
async function loadHistory() {
  if (historyQuery.ruleId === undefined || !can('query')) return
  const sequence = ++historySequence
  historyLoading.value = true
  historyError.value = ''
  try {
    const result = await RuleApi.getRulePage({ ...historyQuery })
    if (sequence !== historySequence) return
    historyRows.value = result.list
    historyTotal.value = result.total
  } catch (error) {
    if (sequence === historySequence) historyError.value = errorMessage(error, '修订历史读取失败')
  } finally {
    if (sequence === historySequence) historyLoading.value = false
  }
}
async function viewHistory(row: RuleView) {
  if (await openDetail(row.revisionId)) historyVisible.value = false
}
function beforeUnload(event: BeforeUnloadEvent) {
  if (
    !dirty.value &&
    !pending.value &&
    !(createVisible.value && (identity.detectionId || identity.ruleName))
  )
    return
  event.preventDefault()
  event.returnValue = ''
}
onBeforeRouteLeave(async () => confirmDiscard())
onMounted(() => {
  void loadList()
  window.addEventListener('beforeunload', beforeUnload)
})
onBeforeUnmount(() => {
  ++detailSequence
  ++listSequence
  ++historySequence
  window.removeEventListener('beforeunload', beforeUnload)
})
</script>

<style scoped>
.rules-tabs :deep(> .el-tabs__header) {
  padding: 0 20px;
  margin: 0 0 16px;
  background: #fff;
  border-radius: 8px;
}
.rules-tabs :deep(> .el-tabs__header .el-tabs__nav-wrap::after) {
  height: 1px;
  background: #f0f2f5;
}
.rules-tabs :deep(> .el-tabs__header .el-tabs__item) {
  height: 44px;
  font-size: 13px;
}
.rules-filters {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 16px;
}
.rules-filters :deep(.el-form-item) {
  margin-bottom: 14px;
}
.rules-filters :deep(.el-select) {
  width: 100%;
}
.rules-filter-actions {
  grid-column: 1 / -1;
  margin-bottom: 0 !important;
}
.rules-filter-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
.rules-list-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.rules-drawer :deep(.el-drawer__header) {
  padding: 16px 20px;
  margin-bottom: 0;
  background: #fff;
  border-bottom: 1px solid #f0f2f5;
}
.rules-drawer :deep(.el-drawer__title),
.rules-dialog :deep(.el-dialog__title) {
  color: #1a2332;
  font-size: 15px;
  font-weight: 600;
}
.rules-drawer :deep(.el-drawer__body) {
  padding: 20px;
}
.rules-drawer :deep(.el-drawer__footer) {
  padding: 14px 20px;
  background: #fff;
  border-top: 1px solid #f0f2f5;
}
.rules-drawer :deep(.el-descriptions__body) {
  overflow: hidden;
  background: #fff;
  border-radius: 8px;
}
.rule-editor-section {
  padding: 20px;
  margin-bottom: 16px;
  background: #fff;
  border-radius: 8px;
}
.rule-editor-section :deep(.el-form-item) {
  margin-bottom: 14px;
}
.section-title {
  margin: 0 0 16px;
  font-size: 14px;
  font-weight: 600;
  color: var(--inspection-title);
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}
.threshold-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0 12px;
}
.help-text {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
  margin: 8px 0 12px;
}
.command-row {
  border: 1px solid #e8ecf1;
  border-radius: 4px;
  background: #fafbfc;
  padding: 12px 16px;
  margin-bottom: 12px;
}
.command-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}
.command-options {
  display: flex;
  flex-wrap: wrap;
  gap: 0 24px;
}
.product-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
}
.validation-panel {
  margin-top: 0;
  scroll-margin-top: 16px;
}
.validation-details {
  padding: 12px 16px;
  overflow-wrap: anywhere;
}
.candidate-list {
  padding-left: 24px;
  font-size: 13px;
  line-height: 1.8;
}
.drawer-footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
}
.drawer-footer :deep(.el-button + .el-button) {
  margin-left: 0;
}
.field-grid :deep(.el-input-number),
.threshold-grid :deep(.el-select),
.field-grid :deep(.el-select) {
  width: 100%;
}
@media (max-width: 768px) {
  .rules-filters,
  .field-grid,
  .threshold-grid,
  .product-row {
    grid-template-columns: minmax(0, 1fr);
  }
  .product-row {
    gap: 0;
    margin-bottom: 16px;
  }
  .product-row > .el-button {
    justify-self: start;
  }
  .command-row {
    padding: 12px;
  }
  .rule-editor-section {
    padding: 16px;
  }
  .rules-drawer :deep(.el-drawer__body) {
    padding: 12px;
  }
}
</style>
