<template>
  <div>
    <!-- ============ 顶部状态卡（生命周期阶段统计，可点击筛选） ============ -->
    <div class="status-cards">
      <div
        v-for="card in statusCards"
        :key="card.key"
        class="status-card"
        :class="[
          `status-card--${card.tone}`,
          { 'status-card--active': activeStatus === card.value }
        ]"
        @click="toggleStatusFilter(card.value)"
      >
        <div class="status-card-icon"><Icon :icon="card.icon" /></div>
        <div class="status-card-body">
          <div class="status-card-num">{{ card.count }}</div>
          <div class="status-card-label">{{ card.label }}</div>
        </div>
        <div class="status-card-strip"></div>
      </div>
    </div>

    <!-- ============ 筛选 + 创建入口 ============ -->
    <ContentWrap>
      <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
        <el-form-item label="项目编码" prop="projectCode">
          <el-input
            v-model="query.projectCode"
            placeholder="PJT2026000001"
            clearable
            class="!w-180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="项目名称" prop="projectName">
          <el-input
            v-model="query.projectName"
            clearable
            class="!w-200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="query.status" placeholder="全部" clearable class="!w-140px">
            <el-option
              v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PROJECT_LIFECYCLE_STAGE)"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="签约方式" prop="signingMethod">
          <el-select v-model="query.signingMethod" placeholder="全部" clearable class="!w-140px">
            <el-option
              v-for="dict in getStrDictOptions(DICT_TYPE.PMS_SIGNING_METHOD)"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="项目类别" prop="projectCategory">
          <el-select v-model="query.projectCategory" placeholder="全部" clearable class="!w-140px">
            <el-option
              v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PROJECT_CATEGORY)"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="实施方式" prop="implementationMode">
          <el-select
            v-model="query.implementationMode"
            placeholder="全部"
            clearable
            class="!w-150px"
          >
            <el-option
              v-for="dict in getStrDictOptions(DICT_TYPE.PMS_IMPLEMENTATION_METHOD)"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="handleSearch"><Icon icon="ep:search" />查询</el-button>
          <el-button @click="handleReset"><Icon icon="ep:refresh-left" />重置</el-button>
          <el-button type="primary" @click="openWizard" v-hasPermi="['pms:project:create']">
            <Icon icon="ep:plus" />创建项目
          </el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <!-- ============ 项目列表 ============ -->
    <ContentWrap>
      <div class="table-toolbar">
        <span class="table-title">
          <Icon icon="ep:folder-opened" /> 项目列表
          <span class="table-count">共 {{ total }} 条</span>
        </span>
        <el-button text bg @click="load"> <Icon icon="ep:refresh" />刷新 </el-button>
      </div>
      <el-table v-loading="loading" :data="rows" empty-text="暂无项目数据">
        <el-table-column prop="projectCode" label="项目编码" width="160" fixed="left">
          <template #default="{ row }">
            <span class="code-text">{{ row.projectCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="projectName" label="项目名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="name-link" @click="openDetail(row)">{{ row.projectName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户名称" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.customerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="签约方式" width="100">
          <template #default="{ row }">
            <dict-tag
              v-if="row.signingMethod"
              :type="DICT_TYPE.PMS_SIGNING_METHOD"
              :value="row.signingMethod"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="项目类别" width="100">
          <template #default="{ row }">
            <dict-tag
              v-if="row.projectCategory"
              :type="DICT_TYPE.PMS_PROJECT_CATEGORY"
              :value="row.projectCategory"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="实施方式" width="120">
          <template #default="{ row }">
            <dict-tag
              v-if="row.implementationMode"
              :type="DICT_TYPE.PMS_IMPLEMENTATION_METHOD"
              :value="row.implementationMode"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="重大项目级别" width="120">
          <template #default="{ row }">
            <dict-tag
              v-if="row.majorProjectLevel"
              :type="DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL"
              :value="row.majorProjectLevel"
            />
            <span v-else class="text-gray-400">不限</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <dict-tag :type="DICT_TYPE.PMS_PROJECT_LIFECYCLE_STAGE" :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="模板绑定" width="170">
          <template #default="{ row }">
            <span v-if="row.lifecycleTemplateId">
              #{{ row.lifecycleTemplateId }} v{{ row.lifecycleTemplateRevisionNo }}
              <el-tag
                size="small"
                :type="row.templateLoadMethod === 'AUTO_DEFAULT' ? 'info' : 'primary'"
              >
                {{ row.templateLoadMethod === 'AUTO_DEFAULT' ? '自动' : '人工' }}
              </el-tag>
            </span>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="managerName" label="负责人" width="100">
          <template #default="{ row }">{{ row.managerName || '-' }}</template>
        </el-table-column>
        <el-table-column
          prop="createTime"
          label="创建时间"
          width="170"
          :formatter="dateFormatter"
        />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="goDetail(row)"
              v-hasPermi="['pms:project:query']"
            >
              详情
            </el-button>
            <el-button
              link
              type="warning"
              @click="openEdit(row)"
              v-hasPermi="['pms:project:update']"
            >
              编辑
            </el-button>
            <el-button
              link
              type="success"
              @click="openAssign(row)"
              v-hasPermi="['pms:project:assign']"
            >
              指派服务经理
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        :total="total"
        v-model:page="query.pageNo"
        v-model:limit="query.pageSize"
        @pagination="load"
      />
    </ContentWrap>

    <!-- ============ 创建向导 ============ -->
    <Dialog v-model="wizardVisible" title="手工创建项目" :width="wizardWidth">
      <el-steps :active="wizardStep" finish-status="success" align-center class="mb-20px">
        <el-step title="① 基本信息" />
        <el-step title="② 模板匹配" />
        <el-step title="③ 确认提交" />
      </el-steps>

      <!-- 步骤①：基本信息 + 三维 -->
      <div v-show="wizardStep === 0">
        <el-form
          ref="wizardFormRef"
          :model="createForm"
          :rules="createRules"
          :label-width="mobile ? 'auto' : '130px'"
          :label-position="mobile ? 'top' : 'right'"
        >
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="项目名称" prop="projectName">
                <el-input v-model="createForm.projectName" placeholder="某客户网络优化工程" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="客户名称" prop="customerName">
                <el-input v-model="createForm.customerName" placeholder="某公司" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="客户编码" prop="customerCode">
                <el-input v-model="createForm.customerCode" placeholder="CUS-001" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="合同号" prop="contractNo">
                <el-input v-model="createForm.contractNo" placeholder="HT-2026-001（手工登记）" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="下单公司" prop="orderOfficeCompanyId">
                <el-select
                  v-model="createForm.orderOfficeCompanyId"
                  filterable
                  class="!w-full"
                  placeholder="请选择公司"
                >
                  <el-option
                    v-for="item in companies"
                    :key="item.id"
                    :label="`${item.code} ${item.name}`"
                    :value="item.id ?? 0"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="下单办事处" prop="orderOfficeDepartmentId">
                <el-select
                  v-model="createForm.orderOfficeDepartmentId"
                  filterable
                  class="!w-full"
                  placeholder="请选择部门"
                >
                  <el-option
                    v-for="item in departments"
                    :key="item.id"
                    :label="`${item.code} ${item.name}`"
                    :value="item.id ?? 0"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-divider content-position="left">订单实施范围</el-divider>
          <el-form-item label="地点录入方式">
            <el-radio-group v-model="createForm.locationMode">
              <el-radio-button value="sites">选择已维护站点</el-radio-button>
              <el-radio-button value="fallback">站点未维护</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <template v-if="createForm.locationMode === 'sites'">
            <div v-for="(siteRow, index) in createForm.sites" :key="index" class="site-row">
              <el-select
                v-model="siteRow.siteId"
                filterable
                class="site-select"
                placeholder="选择实施站点"
                @change="syncSiteVersion(siteRow)"
              >
                <el-option
                  v-for="item in availableSites"
                  :key="item.id"
                  :label="`${item.code} ${item.name}`"
                  :value="item.id ?? 0"
                />
              </el-select>
              <el-radio v-model="primarySiteIndex" :value="index">主站点</el-radio>
              <el-button
                link
                type="danger"
                :disabled="createForm.sites.length === 1"
                @click="removeSiteRow(index)"
                >移除</el-button
              >
            </div>
            <el-button type="primary" plain @click="addSiteRow"
              ><Icon icon="ep:plus" />增加站点</el-button
            >
          </template>
          <el-form-item v-else label="兼容实施地点" prop="implementationLocation">
            <el-input
              v-model="createForm.implementationLocation"
              placeholder="站点未维护时填写现场可识别地点"
            />
          </el-form-item>
          <el-alert
            v-if="createForm.locationMode === 'fallback'"
            type="warning"
            :closable="false"
            show-icon
            class="mb-16px"
          >
            项目将标记为 UNRESOLVED，可在工勘或安装环节补充结构化地点。
          </el-alert>
          <el-divider content-position="left">项目分类三维（模板匹配依据）</el-divider>
          <el-row :gutter="16">
            <el-col :xs="24" :sm="8">
              <el-form-item label="签约方式" prop="signingMethod">
                <el-select v-model="createForm.signingMethod" placeholder="请选择" class="!w-full">
                  <el-option
                    v-for="dict in getStrDictOptions(DICT_TYPE.PMS_SIGNING_METHOD)"
                    :key="dict.value"
                    :label="dict.label"
                    :value="dict.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="8">
              <el-form-item label="项目类别" prop="projectCategory">
                <el-select
                  v-model="createForm.projectCategory"
                  placeholder="请选择"
                  class="!w-full"
                >
                  <el-option
                    v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PROJECT_CATEGORY)"
                    :key="dict.value"
                    :label="dict.label"
                    :value="dict.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="8">
              <el-form-item label="实施方式" prop="implementationMode">
                <el-select
                  v-model="createForm.implementationMode"
                  placeholder="请选择"
                  class="!w-full"
                >
                  <el-option
                    v-for="dict in getStrDictOptions(DICT_TYPE.PMS_IMPLEMENTATION_METHOD)"
                    :key="dict.value"
                    :label="dict.label"
                    :value="dict.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-alert
            title="CRM重大项目级别：不适用"
            description="手工创建不维护该CRM权威属性，系统按空值参与模板匹配。"
            type="info"
            :closable="false"
            show-icon
            class="mb-16px"
          />
          <el-form-item label="创建原因" prop="creationReason">
            <el-input
              v-model="createForm.creationReason"
              type="textarea"
              :rows="2"
              placeholder="BR-2 必填：说明为何脱离 CRM/ERP 链路手工创建"
            />
          </el-form-item>
        </el-form>
      </div>

      <!-- 步骤②：模板匹配 -->
      <div v-show="wizardStep === 1" v-loading="matchLoading">
        <el-alert
          v-if="matchResult?.outcome === 'MATCHED'"
          type="success"
          :closable="false"
          show-icon
          class="mb-12px"
        >
          唯一命中生效模板，提交时将自动加载（AUTO_DEFAULT），无需人工选择。
        </el-alert>
        <el-alert
          v-if="matchResult?.outcome === 'MULTI_MATCH'"
          type="warning"
          :closable="false"
          show-icon
          class="mb-12px"
        >
          同优先级多匹配：请人工选择一个模板（MANUAL_SELECTED），否则无法提交。
        </el-alert>
        <el-alert
          v-if="matchResult?.outcome === 'NO_MATCH'"
          type="error"
          :closable="false"
          show-icon
          class="mb-12px"
        >
          <template #title>无匹配生效模板，创建阻断（PROJECT_TEMPLATE_NO_MATCH）</template>
          <div v-for="c in matchResult?.conflicts" :key="c" class="text-12px">{{ c }}</div>
        </el-alert>
        <el-table
          v-if="matchCandidates.length"
          :data="matchCandidates"
          highlight-current-row
          @row-click="selectCandidate"
        >
          <el-table-column v-if="matchResult?.outcome === 'MULTI_MATCH'" label="" width="50">
            <template #default="{ row }">
              <el-radio :value="row.templateRevisionId" v-model="selectedTemplateRevisionId">
                <span class="sr-only">选择{{ row.name }}版本{{ row.latestRevisionNo }}</span>
              </el-radio>
            </template>
          </el-table-column>
          <el-table-column prop="code" label="模板编码" width="170" />
          <el-table-column prop="name" label="模板名称" min-width="160" />
          <el-table-column prop="matchPriority" label="优先级" width="80" />
          <el-table-column prop="latestRevisionNo" label="最新发布版" width="100">
            <template #default="{ row }">v{{ row.latestRevisionNo }}</template>
          </el-table-column>
          <el-table-column label="匹配条件（空=不限）" min-width="220">
            <template #default="{ row }">
              <span class="text-12px">
                {{ dimLabel(row.signingMethod, DICT_TYPE.PMS_SIGNING_METHOD) }} /
                {{ dimLabel(row.projectCategory, DICT_TYPE.PMS_PROJECT_CATEGORY) }} /
                {{ dimLabel(row.implementationMethod, DICT_TYPE.PMS_IMPLEMENTATION_METHOD) }} /
                {{ row.majorProjectLevel || '不限' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="previewTemplate(row)">预览</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 步骤③：确认 + 可选服务经理 -->
      <div v-show="wizardStep === 2">
        <el-alert
          v-if="createErrorMessage"
          type="error"
          :closable="false"
          show-icon
          class="mb-16px"
          :title="createErrorMessage"
        />
        <el-descriptions :column="mobile ? 1 : 2" border size="small" class="mb-16px">
          <el-descriptions-item label="项目名称">{{ createForm.projectName }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{
            createForm.customerName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="签约方式">
            {{ dimLabel(createForm.signingMethod, DICT_TYPE.PMS_SIGNING_METHOD) }}
          </el-descriptions-item>
          <el-descriptions-item label="项目类别">
            {{ dimLabel(createForm.projectCategory, DICT_TYPE.PMS_PROJECT_CATEGORY) }}
          </el-descriptions-item>
          <el-descriptions-item label="实施方式">
            {{ dimLabel(createForm.implementationMode, DICT_TYPE.PMS_IMPLEMENTATION_METHOD) }}
          </el-descriptions-item>
          <el-descriptions-item label="重大项目级别">不适用</el-descriptions-item>
          <el-descriptions-item label="地点状态">
            {{ createForm.locationMode === 'sites' ? 'RESOLVED' : 'UNRESOLVED' }}
          </el-descriptions-item>
          <el-descriptions-item label="实施范围">
            {{
              createForm.locationMode === 'sites'
                ? `${createForm.sites.length} 个站点`
                : createForm.implementationLocation
            }}
          </el-descriptions-item>
          <el-descriptions-item label="选用模板">
            <span v-if="selectedTemplate">
              {{ selectedTemplate.name }}（revision #{{ selectedTemplate.templateRevisionId }} v{{
                selectedTemplate.latestRevisionNo
              }}，人工选择）
            </span>
            <span v-else-if="matchResult?.outcome === 'MATCHED'">
              {{ matchCandidates[0]?.name }}（唯一默认命中，自动加载）
            </span>
            <span v-else class="text-red-500">不可达</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建原因" :span="2">{{
            createForm.creationReason
          }}</el-descriptions-item>
        </el-descriptions>
        <el-alert type="info" :closable="false" show-icon>
          提交将单事务完成：编码分配（PJT+年份+流水）→
          模板实例化（阶段/任务/里程碑/交付件/门禁冻结）→ 实施站点绑定；失败整体回滚。
        </el-alert>
      </div>

      <template #footer>
        <el-button v-if="wizardStep > 0" @click="wizardStep--">上一步</el-button>
        <el-button v-if="wizardStep === 0" type="primary" @click="wizardNext0"
          >下一步：匹配模板</el-button
        >
        <el-button
          v-if="wizardStep === 1"
          type="primary"
          :disabled="!canGoStep2"
          @click="wizardStep = 2"
        >
          下一步：确认
        </el-button>
        <el-button v-if="wizardStep === 2" type="primary" :loading="creating" @click="submitCreate">
          <Icon icon="ep:circle-check" />提交创建
        </el-button>
        <el-button @click="wizardVisible = false">取消</el-button>
      </template>
    </Dialog>

    <!-- ============ 模板预览抽屉 ============ -->
    <el-drawer v-model="previewVisible" :title="`模板预览：${previewTitle}`" size="620px">
      <div v-loading="previewLoading">
        <el-descriptions v-if="previewContent" :column="2" border size="small" class="mb-12px">
          <el-descriptions-item label="阶段数">{{
            previewContent.stages.length
          }}</el-descriptions-item>
          <el-descriptions-item label="任务数">{{
            previewContent.tasks.length
          }}</el-descriptions-item>
          <el-descriptions-item label="里程碑数">{{
            previewContent.milestones.length
          }}</el-descriptions-item>
          <el-descriptions-item label="交付件数">{{
            previewContent.deliverables.length
          }}</el-descriptions-item>
          <el-descriptions-item label="门禁数" :span="2">{{
            previewContent.gates.length
          }}</el-descriptions-item>
        </el-descriptions>
        <el-collapse v-if="previewContent">
          <el-collapse-item
            v-for="stage in previewContent.stages"
            :key="stage.stageCode"
            :title="`${stage.stageCode} ${stage.name}`"
            :name="stage.stageCode"
          >
            <div class="preview-block">
              <div class="preview-block-title"
                >任务（{{ stageTasks(stage.stageCode).length }}）</div
              >
              <div v-for="t in stageTasks(stage.stageCode)" :key="t.taskCode" class="preview-line">
                <el-tag size="small" type="info">{{ t.taskCode }}</el-tag> {{ t.name }}
              </div>
            </div>
            <div class="preview-block">
              <div class="preview-block-title"
                >里程碑（{{ stageMilestones(stage.stageCode).length }}）</div
              >
              <div
                v-for="m in stageMilestones(stage.stageCode)"
                :key="m.milestoneCode"
                class="preview-line"
              >
                <el-tag size="small" type="warning">{{ m.milestoneCode }}</el-tag> {{ m.name }}
              </div>
            </div>
            <div class="preview-block">
              <div class="preview-block-title"
                >交付件（{{ stageDeliverables(stage.stageCode).length }}）</div
              >
              <div
                v-for="d in stageDeliverables(stage.stageCode)"
                :key="d.deliverableCode"
                class="preview-line"
              >
                <el-tag size="small" :type="d.required ? 'danger' : 'info'">{{
                  d.deliverableCode
                }}</el-tag>
                {{ d.name }}
              </div>
            </div>
            <div class="preview-block">
              <div class="preview-block-title"
                >门禁（{{ stageGates(stage.stageCode).length }}）</div
              >
              <div v-for="g in stageGates(stage.stageCode)" :key="g.gateCode" class="preview-line">
                <el-tag size="small" :type="g.gateType === 'ENTRY' ? 'success' : 'primary'">
                  {{ g.gateType === 'ENTRY' ? '准入' : '准出' }}
                </el-tag>
                {{ g.name }}
                <span class="text-12px text-gray-400">
                  引用：{{ g.references.map((r) => `${r.refType}:${r.refCode}`).join('、') || '-' }}
                </span>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-drawer>

    <!-- ============ 项目详情抽屉 ============ -->
    <el-drawer
      v-model="detailVisible"
      :title="`项目详情：${detail?.projectCode || ''}`"
      size="860px"
    >
      <div v-loading="detailLoading">
        <el-tabs v-model="detailTab">
          <el-tab-pane label="基本信息" name="base">
            <el-descriptions v-if="detail" :column="2" border size="small">
              <el-descriptions-item label="项目编码">{{ detail.projectCode }}</el-descriptions-item>
              <el-descriptions-item label="编码命名空间">
                根 #{{ detail.codeRootId }} · 序号 {{ detail.projectSequence }} · 规则
                {{ detail.codeRuleVersion }}
              </el-descriptions-item>
              <el-descriptions-item label="项目名称" :span="2">{{
                detail.projectName
              }}</el-descriptions-item>
              <el-descriptions-item label="签约方式">
                <dict-tag :type="DICT_TYPE.PMS_SIGNING_METHOD" :value="detail.signingMethod!" />
              </el-descriptions-item>
              <el-descriptions-item label="项目类别">
                <dict-tag :type="DICT_TYPE.PMS_PROJECT_CATEGORY" :value="detail.projectCategory!" />
              </el-descriptions-item>
              <el-descriptions-item label="实施方式">
                <dict-tag
                  :type="DICT_TYPE.PMS_IMPLEMENTATION_METHOD"
                  :value="detail.implementationMode!"
                />
              </el-descriptions-item>
              <el-descriptions-item label="重大项目级别">
                <dict-tag
                  v-if="detail.majorProjectLevel"
                  :type="DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL"
                  :value="detail.majorProjectLevel"
                />
                <span v-else>不限</span>
              </el-descriptions-item>
              <el-descriptions-item label="客户"
                >{{ detail.customerName || '-' }}（{{
                  detail.customerCode || '-'
                }}）</el-descriptions-item
              >
              <el-descriptions-item label="合同号">{{
                detail.contractNo || '-'
              }}</el-descriptions-item>
              <el-descriptions-item label="下单公司">
                {{ detail.companyCode || '-' }} {{ detail.companyName || '' }}
              </el-descriptions-item>
              <el-descriptions-item label="下单办事处">
                {{ detail.departmentCode || '-' }} {{ detail.departmentName || '' }}
              </el-descriptions-item>
              <el-descriptions-item label="实施地点">{{
                detail.implementationLocation || '-'
              }}</el-descriptions-item>
              <el-descriptions-item label="地点状态">
                <el-tag
                  :type="detail.locationResolutionStatus === 'RESOLVED' ? 'success' : 'warning'"
                >
                  {{ detail.locationResolutionStatus || 'UNRESOLVED' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <dict-tag :type="DICT_TYPE.PMS_PROJECT_LIFECYCLE_STAGE" :value="detail.status!" />
              </el-descriptions-item>
              <el-descriptions-item label="创建来源">
                <dict-tag :type="DICT_TYPE.PMS_PROJECT_SOURCE_TYPE" :value="detail.sourceType!" />
              </el-descriptions-item>
              <el-descriptions-item label="创建原因" :span="2">{{
                detail.creationReason || '-'
              }}</el-descriptions-item>
              <el-descriptions-item label="模板绑定" :span="2">
                <span v-if="detail.lifecycleTemplateId">
                  #{{ detail.lifecycleTemplateId }} v{{ detail.lifecycleTemplateRevisionNo }} ·
                  <dict-tag
                    :type="DICT_TYPE.PMS_TEMPLATE_LOAD_METHOD"
                    :value="detail.templateLoadMethod!"
                  />
                  <span v-if="detail.processDefinitionKey" class="ml-8px text-12px text-gray-400">
                    流程 {{ detail.processDefinitionKey }}@{{ detail.processDefinitionVersion }}
                  </span>
                </span>
                <span v-else class="text-gray-400">-</span>
              </el-descriptions-item>
              <el-descriptions-item label="创建时间" :span="2">{{
                formatDateTime(detail.createTime)
              }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <el-tab-pane label="生命周期实例" name="instances">
            <el-alert type="info" :closable="false" show-icon class="mb-12px">
              按创建时冻结的模板版本只读展示（阶段/任务/里程碑/交付件/门禁）。
            </el-alert>
            <el-collapse v-if="instances">
              <el-collapse-item
                v-for="stage in instances.stages"
                :key="stage.stageCode"
                :name="stage.stageCode"
              >
                <template #title>
                  <span class="stage-title">{{ stage.stageCode }} {{ stage.name }}</span>
                  <dict-tag :type="DICT_TYPE.PMS_PROJECT_STAGE_STATUS" :value="stage.status" />
                </template>
                <div class="preview-block">
                  <div class="preview-block-title">
                    任务（{{ instTasks(stage.stageCode).length }}，初始待分配）
                  </div>
                  <el-table :data="instTasks(stage.stageCode)" size="small" border>
                    <el-table-column prop="taskCode" label="任务码" width="120" />
                    <el-table-column prop="name" label="任务名称" min-width="140" :indent="24">
                      <template #default="{ row }">
                        <span :style="{ paddingLeft: row.parentTaskCode ? '18px' : '0' }">
                          {{ row.parentTaskCode ? '└ ' : '' }}{{ row.name }}
                        </span>
                      </template>
                    </el-table-column>
                    <el-table-column label="状态" width="90">
                      <template #default="{ row }">
                        <dict-tag :type="DICT_TYPE.PMS_PROJECT_TASK_STATUS" :value="row.status" />
                      </template>
                    </el-table-column>
                    <el-table-column prop="estimatedHours" label="预估工时" width="90" />
                    <el-table-column prop="satisfactionTiming" label="满意度时点" width="110" />
                  </el-table>
                </div>
                <div class="preview-block">
                  <div class="preview-block-title"
                    >里程碑（{{ instMilestones(stage.stageCode).length }}）</div
                  >
                  <div
                    v-for="m in instMilestones(stage.stageCode)"
                    :key="m.milestoneCode"
                    class="preview-line"
                  >
                    <dict-tag :type="DICT_TYPE.PMS_PROJECT_MILESTONE_STATUS" :value="m.status" />
                    <el-tag size="small" type="warning">{{ m.milestoneCode }}</el-tag>
                    {{ m.name }}
                    <span class="text-12px text-gray-400">{{ m.timing }}</span>
                  </div>
                </div>
                <div class="preview-block">
                  <div class="preview-block-title"
                    >交付件（{{ instDeliverables(stage.stageCode).length }}）</div
                  >
                  <div
                    v-for="d in instDeliverables(stage.stageCode)"
                    :key="d.deliverableCode"
                    class="preview-line"
                  >
                    <dict-tag :type="DICT_TYPE.PMS_PROJECT_DELIVERABLE_STATUS" :value="d.status" />
                    <el-tag size="small" :type="d.required ? 'danger' : 'info'">{{
                      d.deliverableCode
                    }}</el-tag>
                    {{ d.name }}
                  </div>
                </div>
                <div class="preview-block">
                  <div class="preview-block-title"
                    >门禁（{{ instGates(stage.stageCode).length }}）</div
                  >
                  <div
                    v-for="g in instGates(stage.stageCode)"
                    :key="g.gateCode"
                    class="preview-line"
                  >
                    <dict-tag :type="DICT_TYPE.PMS_PROJECT_GATE_STATUS" :value="g.status" />
                    <el-tag size="small" :type="g.gateType === 'ENTRY' ? 'success' : 'primary'">
                      {{ g.gateType === 'ENTRY' ? '准入' : '准出' }}
                    </el-tag>
                    {{ g.name }}
                    <span class="text-12px text-gray-400">
                      {{ g.references.map((r) => `${r.refType}:${r.refCode}`).join('、') || '-' }}
                    </span>
                  </div>
                </div>
              </el-collapse-item>
            </el-collapse>
            <el-empty v-else description="暂无实例数据" />
          </el-tab-pane>

          <el-tab-pane label="成员区间" name="members">
            <el-table v-if="members.length" :data="members" size="small" border>
              <el-table-column prop="memberName" label="姓名" width="100">
                <template #default="{ row }">
                  {{ row.memberName || userNickname(row.userId) || `#${row.userId}` }}
                </template>
              </el-table-column>
              <el-table-column prop="employeeNo" label="工号" width="100">
                <template #default="{ row }">{{ row.employeeNo || '-' }}</template>
              </el-table-column>
              <el-table-column label="角色" width="130">
                <template #default="{ row }">
                  <dict-tag :type="DICT_TYPE.PMS_PROJECT_MEMBER_ROLE" :value="row.memberRole" />
                </template>
              </el-table-column>
              <el-table-column label="生效时间" width="160">
                <template #default="{ row }">{{ formatDateTime(row.effectiveFrom) }}</template>
              </el-table-column>
              <el-table-column label="失效时间" width="160">
                <template #default="{ row }">
                  <span v-if="row.effectiveTo">{{ formatDateTime(row.effectiveTo) }}</span>
                  <el-tag v-else type="success" size="small">当前有效</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无成员（创建后指派一级服务经理）" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <!-- ============ 编辑弹窗（BR-7 可编辑属性） ============ -->
    <Dialog v-model="editVisible" title="编辑项目（可编辑属性）" width="520px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="项目编码">
          <el-input :model-value="editForm.projectCode" disabled />
        </el-form-item>
        <el-form-item label="项目名称">
          <el-input v-model="editForm.projectName" />
        </el-form-item>
        <el-form-item label="客户编码">
          <el-input v-model="editForm.customerCode" />
        </el-form-item>
        <el-form-item label="客户名称">
          <el-input v-model="editForm.customerName" />
        </el-form-item>
        <el-form-item label="合同号">
          <el-input v-model="editForm.contractNo" />
        </el-form-item>
        <el-form-item label="实施地点">
          <el-input v-model="editForm.implementationLocation" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </Dialog>

    <!-- ============ 指派服务经理弹窗 ============ -->
    <Dialog v-model="assignVisible" title="指派一级服务经理" width="520px">
      <el-form ref="assignFormRef" :model="assignForm" :rules="assignRules" label-width="110px">
        <el-form-item label="项目">
          <el-input
            :model-value="`${assignTarget?.projectCode} ${assignTarget?.projectName}`"
            disabled
          />
        </el-form-item>
        <el-form-item label="服务经理" prop="userId">
          <PmsEntitySelect
            v-model="assignForm.userId"
            :api="UserApi.getUserPage"
            label-field="nickname"
            value-field="id"
            query-field="nickname"
            placeholder="请选择用户"
            class="!w-full"
          />
        </el-form-item>
        <el-form-item label="服务层级" prop="levelCode">
          <el-select v-model="assignForm.levelCode" class="!w-full">
            <el-option label="一级服务经理（L1）" value="L1" />
            <el-option label="二级服务经理（L2）" value="L2" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="assignSites.length" label="实施站点" prop="siteId">
          <el-select
            v-model="assignForm.siteId"
            class="!w-full"
            placeholder="请选择项目实施站点"
            @change="suggestDepartment"
          >
            <el-option
              v-for="item in assignSites"
              :key="item.siteId"
              :label="`${item.siteCodeSnapshot || ''} ${item.siteNameSnapshot || ''}`"
              :value="item.siteId"
            />
          </el-select>
        </el-form-item>
        <el-alert
          v-else
          type="warning"
          :closable="false"
          show-icon
          class="mb-12px"
          title="站点待维护：本次仅按项目和办事处范围人工确认，不进行地点自动解析。"
        />
        <el-form-item label="服务办事处" prop="departmentCode">
          <el-select
            v-model="assignForm.departmentCode"
            filterable
            class="!w-full"
            placeholder="选择或人工确认办事处"
          >
            <el-option
              v-for="item in departments"
              :key="item.id"
              :label="`${item.code} ${item.name}`"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-alert
          :type="assignSuggestion ? 'success' : 'info'"
          :closable="false"
          show-icon
          class="mb-12px"
        >
          {{ assignSuggestion || '当前站点无区划映射建议，请人工选择服务办事处。' }}
        </el-alert>
        <el-form-item label="生效时间">
          <el-date-picker
            v-model="assignForm.effectiveFrom"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="空 = 当前时间"
            class="!w-full"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAssign">确认指派</el-button>
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * F-PM01 项目手工创建（PM-01）—— 新链页面（复数路由 /pms/projects）
 *
 * 列表（四维/状态/名称过滤）→ 创建向导（基本信息 → 实时模板匹配 → 确认+可选指派）
 * → 详情抽屉（基本信息/生命周期实例五要素/成员区间）→ 编辑（BR-7 可编辑属性）/ 指派服务经理。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useRouter } from 'vue-router'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getStrDictOptions, getDictLabel } from '@/utils/dict'
import { dateFormatter, formatDate } from '@/utils/formatTime'
import * as ProjectsApi from '@/api/pms/project/projects'
import type {
  ProjectMasterVO,
  ProjectInstancesVO,
  ProjectMemberAssignmentVO,
  ProjectMatchTemplatesRespVO,
  ProjectSiteReqVO,
  ProjectSiteVO,
  TemplateCandidateVO
} from '@/api/pms/project/projects'
import { getProjectTemplateRevision } from '@/api/pms/project/project-templates'
import type { TemplateDefinitionContent } from '@/api/pms/project/project-templates'
import * as UserApi from '@/api/system/user'
import * as CompanyApi from '@/api/system/company'
import type { CompanyVO } from '@/api/system/company'
import * as DeptApi from '@/api/system/dept'
import type { DeptVO } from '@/api/system/dept'
import * as LocationApi from '@/api/pms/asset/location'
import type { SiteVO } from '@/api/pms/asset/location'
import { createSubmissionIdempotencyState } from './submissionIdempotency'

defineOptions({ name: 'PmsProjects' })

const message = useMessage()
const router = useRouter()
const mobile = useMediaQuery('(max-width: 767px)')
const wizardWidth = computed(() => (mobile.value ? '96%' : '880px'))

// ============ 列表 ============
const loading = ref(false)
const rows = ref<ProjectMasterVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectCode: '',
  projectName: '',
  status: '',
  signingMethod: '',
  projectCategory: '',
  implementationMode: ''
})

// ============ 顶部状态卡（生命周期阶段统计，逐阶段全展开） ============
const LIFECYCLE_STAGES: { value: string; label: string; tone: string; icon: string }[] = [
  { value: 'S0', label: 'S0 待开始', tone: 'gray', icon: 'ep:clock' },
  { value: 'S1', label: 'S1 工前准备', tone: 'blue', icon: 'ep:tools' },
  { value: 'S2', label: 'S2 施工计划', tone: 'blue', icon: 'ep:calendar' },
  { value: 'S3', label: 'S3 实施方案', tone: 'blue', icon: 'ep:files' },
  { value: 'S4', label: 'S4 实施部署', tone: 'blue', icon: 'ep:setting' },
  { value: 'S5', label: 'S5 验收交维', tone: 'yellow', icon: 'ep:circle-check' },
  { value: 'S6', label: 'S6 闭环', tone: 'green', icon: 'ep:lock' },
  { value: 'MAINT', label: 'MAINT 维护', tone: 'gray', icon: 'ep:refresh' }
]

const stats = reactive<Record<string, number>>({ total: 0 })
for (const stage of LIFECYCLE_STAGES) stats[stage.value] = 0

/** 当前选中的状态卡（'' = 全部） */
const activeStatus = ref('')

const statusCards = computed(() => [
  {
    key: 'total',
    label: '项目总数',
    value: '',
    count: stats.total,
    tone: 'blue',
    icon: 'ep:folder-opened'
  },
  ...LIFECYCLE_STAGES.map((stage) => ({
    key: stage.value,
    label: stage.label,
    value: stage.value,
    count: stats[stage.value],
    tone: stage.tone,
    icon: stage.icon
  }))
])

/** 状态卡计数：总数 pageSize=1 取 total，各阶段按 status 精确筛选取 total */
const loadStats = async () => {
  try {
    const totalRes = await ProjectsApi.getProjectPage({ pageNo: 1, pageSize: 1 })
    stats.total = totalRes.total
    const stageRes = await Promise.all(
      LIFECYCLE_STAGES.map((stage) => {
        const params = { pageNo: 1, pageSize: 1, status: stage.value }
        return ProjectsApi.getProjectPage(params)
      })
    )
    LIFECYCLE_STAGES.forEach((stage, index) => {
      stats[stage.value] = stageRes[index].total
    })
  } catch {
    // 统计失败不阻断列表
  }
}

/** 点击状态卡：选中/取消该阶段筛选；再次点击同一卡回到全部 */
const toggleStatusFilter = (value: string) => {
  activeStatus.value = activeStatus.value === value ? '' : value
  query.status = activeStatus.value
  handleSearch()
}

const load = async () => {
  loading.value = true
  try {
    const data = await ProjectsApi.getProjectPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  query.pageNo = 1
  load()
}

const handleReset = () => {
  query.projectCode = ''
  query.projectName = ''
  query.status = ''
  query.signingMethod = ''
  query.projectCategory = ''
  query.implementationMode = ''
  activeStatus.value = ''
  handleSearch()
}

const dimLabel = (value?: string | null, dict?: DICT_TYPE) =>
  value ? getDictLabel(dict!, value) : '不限'

const formatDateTime = (v?: any) => (v ? formatDate(v) : '-')

// ============ 创建向导 ============
const wizardVisible = ref(false)
const wizardStep = ref(0)
const wizardFormRef = ref()
const creating = ref(false)
const createIdempotency = createSubmissionIdempotencyState()
const createErrorMessage = ref('')
const companies = ref<CompanyVO[]>([])
const departments = ref<DeptVO[]>([])
const availableSites = ref<SiteVO[]>([])
const primarySiteIndex = ref(0)

const createForm = reactive({
  projectName: '',
  customerCode: '',
  customerName: '',
  contractNo: '',
  orderOfficeCompanyId: undefined as number | undefined,
  orderOfficeDepartmentId: undefined as number | undefined,
  locationMode: 'sites' as 'sites' | 'fallback',
  sites: [{ siteId: undefined, siteVersion: undefined }] as Array<{
    siteId?: number
    siteVersion?: number
  }>,
  implementationLocation: '',
  signingMethod: '',
  projectCategory: '',
  implementationMode: '',
  creationReason: ''
})

const createRules = {
  projectName: [{ required: true, message: '项目名称不能为空', trigger: 'blur' }],
  orderOfficeCompanyId: [{ required: true, message: '请选择下单公司', trigger: 'change' }],
  orderOfficeDepartmentId: [{ required: true, message: '请选择下单办事处', trigger: 'change' }],
  signingMethod: [{ required: true, message: '签约方式不能为空', trigger: 'change' }],
  projectCategory: [{ required: true, message: '项目类别不能为空', trigger: 'change' }],
  implementationMode: [{ required: true, message: '实施方式不能为空', trigger: 'change' }],
  creationReason: [
    { required: true, whitespace: true, message: '手工创建原因不能为空（BR-2）', trigger: 'blur' }
  ]
}

const syncSiteVersion = (row: { siteId?: number; siteVersion?: number }) => {
  row.siteVersion = availableSites.value.find((site) => site.id === row.siteId)?.version
}

const addSiteRow = () => createForm.sites.push({ siteId: undefined, siteVersion: undefined })

const removeSiteRow = (index: number) => {
  createForm.sites.splice(index, 1)
  if (primarySiteIndex.value >= createForm.sites.length) primarySiteIndex.value = 0
}

const loadOrganizationAndSites = async () => {
  const [companyList, deptList, sitePage] = await Promise.all([
    CompanyApi.getSimpleCompanyList(),
    DeptApi.getSimpleDeptList(),
    LocationApi.getSitePage({ pageNo: 1, pageSize: 100 })
  ])
  companies.value = companyList || []
  departments.value = deptList || []
  availableSites.value = sitePage.list || []
}

const openWizard = () => {
  wizardStep.value = 0
  Object.assign(createForm, {
    projectName: '',
    customerCode: '',
    customerName: '',
    contractNo: '',
    orderOfficeCompanyId: undefined,
    orderOfficeDepartmentId: undefined,
    locationMode: 'sites',
    sites: [{ siteId: undefined, siteVersion: undefined }],
    implementationLocation: '',
    signingMethod: '',
    projectCategory: '',
    implementationMode: '',
    creationReason: ''
  })
  primarySiteIndex.value = 0
  selectedTemplateRevisionId.value = undefined
  matchResult.value = null
  createErrorMessage.value = ''
  createIdempotency.reset()
  wizardVisible.value = true
}

const wizardNext0 = async () => {
  await wizardFormRef.value?.validate()
  if (createForm.locationMode === 'sites') {
    if (createForm.sites.some((item) => !item.siteId || item.siteVersion === undefined)) {
      message.error('请完整选择实施站点')
      return
    }
    if (new Set(createForm.sites.map((item) => item.siteId)).size !== createForm.sites.length) {
      message.error('实施站点不能重复')
      return
    }
  } else if (!createForm.implementationLocation.trim()) {
    message.error('站点未维护时必须填写兼容实施地点')
    return
  }
  await runMatch()
  wizardStep.value = 1
}

// ============ 模板匹配（步骤②） ============
const matchLoading = ref(false)
const matchResult = ref<ProjectMatchTemplatesRespVO | null>(null)
const selectedTemplateRevisionId = ref<number | undefined>(undefined)

const matchCandidates = computed<TemplateCandidateVO[]>(() => matchResult.value?.candidates || [])
const selectedTemplate = computed(
  () =>
    matchCandidates.value.find((c) => c.templateRevisionId === selectedTemplateRevisionId.value) ||
    null
)
/** 步骤② → ③ 门槛：MATCHED 自动放行；MULTI_MATCH 必选一个；NO_MATCH 阻断 */
const canGoStep2 = computed(() => {
  if (!matchResult.value) return false
  if (!matchResult.value.candidateWatermark) return false
  if (matchResult.value.outcome === 'MATCHED') return matchCandidates.value.length === 1
  if (matchResult.value.outcome === 'MULTI_MATCH') return !!selectedTemplateRevisionId.value
  return false
})

const runMatch = async () => {
  matchLoading.value = true
  selectedTemplateRevisionId.value = undefined
  try {
    matchResult.value = await ProjectsApi.matchTemplates({
      signingMethod: createForm.signingMethod || undefined,
      projectCategory: createForm.projectCategory || undefined,
      implementationMode: createForm.implementationMode || undefined
    })
  } finally {
    matchLoading.value = false
  }
}

const selectCandidate = (row: TemplateCandidateVO) => {
  if (matchResult.value?.outcome === 'MULTI_MATCH') {
    selectedTemplateRevisionId.value = row.templateRevisionId
  }
}

// ============ 模板预览抽屉 ============
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewTitle = ref('')
const previewContent = ref<TemplateDefinitionContent | null>(null)

const previewTemplate = async (candidate: TemplateCandidateVO) => {
  previewTitle.value = `${candidate.code} v${candidate.latestRevisionNo}`
  previewVisible.value = true
  previewLoading.value = true
  try {
    const detail = await getProjectTemplateRevision(
      candidate.templateId,
      candidate.latestRevisionNo
    )
    previewContent.value = detail?.content || null
  } catch {
    previewContent.value = null
  } finally {
    previewLoading.value = false
  }
}

const stageTasks = (code: string) =>
  previewContent.value?.tasks.filter((t) => t.stageCode === code) || []
const stageMilestones = (code: string) =>
  previewContent.value?.milestones.filter((m) => m.stageCode === code) || []
const stageDeliverables = (code: string) =>
  previewContent.value?.deliverables.filter((d) => d.stageCode === code) || []
const stageGates = (code: string) =>
  previewContent.value?.gates.filter((g) => g.stageCode === code) || []

// ============ 提交创建（步骤③） ============
const submitCreate = async () => {
  const sites: ProjectSiteReqVO[] | undefined =
    createForm.locationMode === 'sites'
      ? createForm.sites.map((item, index) => ({
          siteId: item.siteId!,
          siteVersion: item.siteVersion!,
          primarySite: index === primarySiteIndex.value
        }))
      : undefined
  const payload: ProjectsApi.ProjectCreateReqVO = {
    projectName: createForm.projectName,
    customerCode: createForm.customerCode || undefined,
    customerName: createForm.customerName || undefined,
    contractNo: createForm.contractNo || undefined,
    orderOfficeCompanyId: createForm.orderOfficeCompanyId!,
    orderOfficeDepartmentId: createForm.orderOfficeDepartmentId!,
    sites,
    implementationLocation:
      createForm.locationMode === 'fallback' ? createForm.implementationLocation : undefined,
    signingMethod: createForm.signingMethod,
    projectCategory: createForm.projectCategory,
    implementationMode: createForm.implementationMode,
    creationReason: createForm.creationReason.trim(),
    templateRevisionId: selectedTemplateRevisionId.value,
    candidateWatermark: matchResult.value?.candidateWatermark || ''
  }
  const idempotencyKey = createIdempotency.keyFor(payload)
  creating.value = true
  createErrorMessage.value = ''
  try {
    const created = await ProjectsApi.createProject(payload, idempotencyKey)
    message.success(
      `创建成功：${created.projectCode}；匹配${created.matchResult || '-'} / ` +
        `${created.matchDecisionMode || '-'}；operationId ${created.matchOperationId || '-'}`
    )
    wizardVisible.value = false
    await load()
  } catch (error: any) {
    createErrorMessage.value =
      error?.response?.data?.msg || error?.message || '创建失败，请修正提示项后重新提交'
  } finally {
    creating.value = false
  }
}

// ============ 详情抽屉 ============
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailTab = ref('base')
const detail = ref<ProjectMasterVO | null>(null)
const instances = ref<ProjectInstancesVO | null>(null)
const members = ref<ProjectMemberAssignmentVO[]>([])

/** 跳转独立详情页（F-PM02 项目详情工作台） */
const goDetail = (row: ProjectMasterVO) => {
  router.push({
    path: '/pms/project-management/project-master-detail',
    query: { projectId: row.id }
  })
}

const openDetail = async (row: ProjectMasterVO) => {
  detailTab.value = 'base'
  detailVisible.value = true
  detailLoading.value = true
  detail.value = row
  instances.value = null
  members.value = []
  try {
    const [base, inst, mem] = await Promise.all([
      ProjectsApi.getProject(row.id!),
      ProjectsApi.getProjectInstances(row.id!),
      ProjectsApi.getProjectMembers(row.id!)
    ])
    detail.value = base
    instances.value = inst
    members.value = mem || []
    // memberName 快照缺失的行按 userId 回查昵称
    const unnamed = (mem || [])
      .filter((m: ProjectMemberAssignmentVO) => !m.memberName)
      .map((m: ProjectMemberAssignmentVO) => m.userId!)
    if (unnamed.length) {
      await loadUserNicknames(unnamed)
    }
  } finally {
    detailLoading.value = false
  }
}

// ============ 成员昵称回退（memberName 快照为空时按 userId 查询） ============
const userNicknames = ref<Record<number, string>>({})
const userNickname = (userId?: number) => (userId ? userNicknames.value[userId] : '')

const loadUserNicknames = async (userIds: number[]) => {
  const missing = userIds.filter((id) => id && !userNicknames.value[id])
  if (!missing.length) return
  try {
    const data = await UserApi.getUserPage({ pageNo: 1, pageSize: 100 })
    const map: Record<number, string> = {}
    for (const u of data?.list || []) {
      if (u?.id != null) map[u.id] = u.nickname || u.username || ''
    }
    userNicknames.value = { ...userNicknames.value, ...map }
  } catch {
    // 回退查询失败不阻断详情展示（仍显示 #userId 占位）
  }
}

const instTasks = (code: string) => instances.value?.tasks.filter((t) => t.stageCode === code) || []
const instMilestones = (code: string) =>
  instances.value?.milestones.filter((m) => m.stageCode === code) || []
const instDeliverables = (code: string) =>
  instances.value?.deliverables.filter((d) => d.stageCode === code) || []
const instGates = (code: string) => instances.value?.gates.filter((g) => g.stageCode === code) || []

// ============ 编辑（BR-7） ============
const editVisible = ref(false)
const saving = ref(false)
const editForm = reactive({
  id: 0,
  projectCode: '',
  projectName: '',
  customerCode: '',
  customerName: '',
  contractNo: '',
  implementationLocation: ''
})

const openEdit = (row: ProjectMasterVO) => {
  Object.assign(editForm, {
    id: row.id,
    projectCode: row.projectCode,
    projectName: row.projectName || '',
    customerCode: row.customerCode || '',
    customerName: row.customerName || '',
    contractNo: row.contractNo || '',
    implementationLocation: row.implementationLocation || ''
  })
  editVisible.value = true
}

const submitEdit = async () => {
  saving.value = true
  try {
    await ProjectsApi.updateProject({
      id: editForm.id,
      projectName: editForm.projectName,
      customerCode: editForm.customerCode,
      customerName: editForm.customerName,
      contractNo: editForm.contractNo,
      implementationLocation: editForm.implementationLocation
    })
    message.success('更新成功')
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// ============ 指派服务经理 ============
const assignVisible = ref(false)
const assignFormRef = ref()
const assignTarget = ref<ProjectMasterVO | null>(null)
const assignSites = ref<ProjectSiteVO[]>([])
const assignSuggestion = ref('')
const assignForm = reactive({
  userId: undefined as number | undefined,
  levelCode: 'L1' as 'L1' | 'L2',
  siteId: undefined as number | undefined,
  departmentCode: '',
  effectiveFrom: ''
})
const assignRules = {
  userId: [{ required: true, message: '请选择服务经理用户', trigger: 'change' }],
  levelCode: [{ required: true, message: '请选择服务层级', trigger: 'change' }],
  siteId: [
    {
      validator: (_rule: unknown, value: number | undefined, callback: (error?: Error) => void) => {
        if (assignSites.value.length && !value) callback(new Error('请选择实施站点'))
        else callback()
      },
      trigger: 'change'
    }
  ],
  departmentCode: [{ required: true, message: '请选择服务办事处', trigger: 'change' }]
}
const PROJECT_VERSION_CONFLICT_CODE = 1014024014
const assignIdempotency = createSubmissionIdempotencyState()

const openAssign = async (row: ProjectMasterVO) => {
  const [project, sites] = await Promise.all([
    ProjectsApi.getProject(row.id!),
    ProjectsApi.getProjectSites(row.id!)
  ])
  assignTarget.value = project
  assignSites.value = sites || []
  Object.assign(assignForm, {
    userId: undefined,
    levelCode: 'L1',
    siteId: assignSites.value.find((item) => item.primarySite)?.siteId,
    departmentCode: '',
    effectiveFrom: ''
  })
  assignIdempotency.reset()
  assignVisible.value = true
  await suggestDepartment(assignForm.siteId)
}

const suggestDepartment = async (siteId?: number) => {
  assignSuggestion.value = ''
  const site = assignSites.value.find((item) => item.siteId === siteId)
  if (!site?.addressSnapshot) return
  try {
    const address = JSON.parse(site.addressSnapshot) as { districtCode?: string }
    if (!address.districtCode) return
    const mapping = await LocationApi.resolveAreaDepartment(address.districtCode, 'DISTRICT')
    if (!mapping?.departmentCode) return
    assignForm.departmentCode = mapping.departmentCode
    assignSuggestion.value = `已按区县 ${address.districtCode} 精确建议 ${mapping.departmentName || mapping.departmentCode}，可手动调整。`
  } catch {
    // 快照不可解析或无有效映射时保留人工指派。
  }
}

const submitAssign = async () => {
  await assignFormRef.value?.validate()
  if (assignTarget.value?.version === undefined) {
    message.error('Project版本缺失，请重新加载项目后再指派')
    return
  }
  const payload = {
    roleCode: 'SERVICE_MANAGER' as const,
    levelCode: assignForm.levelCode,
    managerId: assignForm.userId!,
    siteId: assignForm.siteId,
    departmentCode: assignForm.departmentCode,
    effectiveFrom: assignForm.effectiveFrom || undefined
  }
  const requestIdentity = {
    projectId: assignTarget.value.id,
    expectedVersion: assignTarget.value.version,
    payload
  }
  saving.value = true
  try {
    const result = await ProjectsApi.assignManager(
      assignTarget.value.id!,
      payload,
      assignTarget.value.version,
      assignIdempotency.keyFor(requestIdentity)
    )
    assignTarget.value.version = result.version
    message.success('指派成功（旧区间已关闭，新区间生效）')
    assignVisible.value = false
    await load()
    if (detailVisible.value && detail.value?.id === assignTarget.value?.id) {
      members.value = await ProjectsApi.getProjectMembers(assignTarget.value!.id!)
    }
  } catch (error: any) {
    if (error?.response?.data?.code === PROJECT_VERSION_CONFLICT_CODE && assignTarget.value?.id) {
      assignTarget.value = await ProjectsApi.getProject(assignTarget.value.id)
      assignIdempotency.reset()
      message.warning('Project版本已变化，已重新加载，请确认后再次提交')
    }
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadStats()
  load()
  loadOrganizationAndSites()
})
</script>

<style lang="scss" scoped>
.site-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;

  .site-select {
    flex: 1;
  }
}

/* ============ 顶部状态卡 ============ */
.status-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 15px;
}
.status-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
  overflow: hidden;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(15, 23, 42, 0.08);
  }
  &--active {
    border-color: #1e3a5f;
    box-shadow: 0 0 0 2px rgba(30, 58, 95, 0.12);
  }

  .status-card-icon {
    flex-shrink: 0;
    width: 42px;
    height: 42px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
    color: #fff;
  }
  &--blue .status-card-icon {
    background: linear-gradient(135deg, #3b82f6, #60a5fa);
  }
  &--green .status-card-icon {
    background: linear-gradient(135deg, #10b981, #34d399);
  }
  &--gray .status-card-icon {
    background: linear-gradient(135deg, #64748b, #94a3b8);
  }
  &--yellow .status-card-icon {
    background: linear-gradient(135deg, #f59e0b, #fbbf24);
  }

  .status-card-body {
    flex: 1;
    min-width: 0;
  }
  .status-card-num {
    font-size: 24px;
    font-weight: 700;
    color: #1f2937;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    line-height: 1.1;
  }
  .status-card-label {
    font-size: 12px;
    color: #6b7280;
    margin-top: 2px;
  }
  .status-card-strip {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 3px;
  }
  &--blue .status-card-strip {
    background: #3b82f6;
  }
  &--green .status-card-strip {
    background: #10b981;
  }
  &--gray .status-card-strip {
    background: #94a3b8;
  }
  &--yellow .status-card-strip {
    background: #f59e0b;
  }
}

/* ============ 表格工具栏 ============ */
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.table-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}
.table-count {
  font-size: 12px;
  color: #6b7280;
  font-weight: 400;
  margin-left: 4px;
}

.code-text {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: #6b7280;
}
.name-link {
  color: var(--el-color-primary);
  cursor: pointer;
  font-weight: 500;
  &:hover {
    text-decoration: underline;
  }
}
.stage-title {
  margin-right: 8px;
  font-weight: 600;
}
.preview-block {
  margin-bottom: 10px;
  .preview-block-title {
    font-size: 13px;
    font-weight: 600;
    color: #1f2937;
    margin-bottom: 4px;
  }
  .preview-line {
    padding: 2px 0;
    font-size: 13px;
  }
}
</style>
