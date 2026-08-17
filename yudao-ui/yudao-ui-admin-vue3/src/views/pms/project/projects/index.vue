<template>
  <div>
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
          <el-input v-model="query.projectName" clearable class="!w-200px" @keyup.enter="handleSearch" />
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
          <el-select v-model="query.implementationMode" placeholder="全部" clearable class="!w-150px">
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
          <el-button
            type="primary"
            @click="openWizard"
            v-hasPermi="['pms:project:create']"
          >
            <Icon icon="ep:plus" />创建项目
          </el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <!-- ============ 项目列表 ============ -->
    <ContentWrap>
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
            <dict-tag v-if="row.signingMethod" :type="DICT_TYPE.PMS_SIGNING_METHOD" :value="row.signingMethod" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="项目类别" width="100">
          <template #default="{ row }">
            <dict-tag v-if="row.projectCategory" :type="DICT_TYPE.PMS_PROJECT_CATEGORY" :value="row.projectCategory" />
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
              <el-tag size="small" :type="row.templateLoadMethod === 'AUTO_DEFAULT' ? 'info' : 'primary'">
                {{ row.templateLoadMethod === 'AUTO_DEFAULT' ? '自动' : '人工' }}
              </el-tag>
            </span>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="managerName" label="负责人" width="100">
          <template #default="{ row }">{{ row.managerName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" :formatter="dateFormatter" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:project:query']">
              详情
            </el-button>
            <el-button link type="warning" @click="openEdit(row)" v-hasPermi="['pms:project:update']">
              编辑
            </el-button>
            <el-button link type="success" @click="openAssign(row)" v-hasPermi="['pms:project:assign']">
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
    <Dialog v-model="wizardVisible" title="手工创建项目" width="880px">
      <el-steps :active="wizardStep" finish-status="success" align-center class="mb-20px">
        <el-step title="① 基本信息" />
        <el-step title="② 模板匹配" />
        <el-step title="③ 确认提交" />
      </el-steps>

      <!-- 步骤①：基本信息 + 三维 -->
      <div v-show="wizardStep === 0">
        <el-form ref="wizardFormRef" :model="createForm" :rules="createRules" label-width="130px">
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
              <el-form-item label="办事处公司编码" prop="orderOfficeCompanyCode">
                <el-input v-model="createForm.orderOfficeCompanyCode" placeholder="COMP-SH（可选）" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="办事处部门编码" prop="orderOfficeDepartmentCode">
                <el-input v-model="createForm.orderOfficeDepartmentCode" placeholder="DEPT-SH-01（可选）" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="实施地点" prop="implementationLocation">
            <el-input v-model="createForm.implementationLocation" placeholder="上海（多地点拆分属 PM-02）" />
          </el-form-item>
          <el-divider content-position="left">项目分类三维（模板匹配依据）</el-divider>
          <el-row :gutter="16">
            <el-col :span="8">
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
            <el-col :span="8">
              <el-form-item label="项目类别" prop="projectCategory">
                <el-select v-model="createForm.projectCategory" placeholder="请选择" class="!w-full">
                  <el-option
                    v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PROJECT_CATEGORY)"
                    :key="dict.value"
                    :label="dict.label"
                    :value="dict.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="实施方式" prop="implementationMode">
                <el-select v-model="createForm.implementationMode" placeholder="请选择" class="!w-full">
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
          <el-form-item label="重大项目级别" prop="majorProjectLevel">
            <el-select v-model="createForm.majorProjectLevel" placeholder="不限（NULL）" clearable class="!w-240px">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
            <span class="ml-8px text-12px text-gray-400">CRM 属性映射值域，空 = 不限</span>
          </el-form-item>
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
              <el-radio :value="row.templateId" v-model="selectedTemplateId">&nbsp;</el-radio>
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
        <el-descriptions :column="2" border size="small" class="mb-16px">
          <el-descriptions-item label="项目名称">{{ createForm.projectName }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ createForm.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="签约方式">
            {{ dimLabel(createForm.signingMethod, DICT_TYPE.PMS_SIGNING_METHOD) }}
          </el-descriptions-item>
          <el-descriptions-item label="项目类别">
            {{ dimLabel(createForm.projectCategory, DICT_TYPE.PMS_PROJECT_CATEGORY) }}
          </el-descriptions-item>
          <el-descriptions-item label="实施方式">
            {{ dimLabel(createForm.implementationMode, DICT_TYPE.PMS_IMPLEMENTATION_METHOD) }}
          </el-descriptions-item>
          <el-descriptions-item label="重大项目级别">{{ createForm.majorProjectLevel || '不限' }}</el-descriptions-item>
          <el-descriptions-item label="选用模板">
            <span v-if="selectedTemplate">
              {{ selectedTemplate.name }}（#{{ selectedTemplate.templateId }} v{{ selectedTemplate.latestRevisionNo }}，人工选择）
            </span>
            <span v-else-if="matchResult?.outcome === 'MATCHED'">
              {{ matchCandidates[0]?.name }}（唯一默认命中，自动加载）
            </span>
            <span v-else class="text-red-500">不可达</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建原因" :span="2">{{ createForm.creationReason }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="130px">
          <el-form-item label="一级服务经理">
            <PmsEntitySelect
              v-model="createForm.serviceManagerUserId"
              :api="UserApi.getUserPage"
              label-field="nickname"
              value-field="id"
              query-field="nickname"
              placeholder="可选：创建时同步指派（SERVICE_MANAGER_L1）"
              clearable
              class="!w-320px"
            />
          </el-form-item>
        </el-form>
        <el-alert type="info" :closable="false" show-icon>
          提交将单事务完成：编码分配（PJT+年份+流水）→ 模板实例化（阶段/任务/里程碑/交付件/门禁冻结）→ 可选指派；失败整体回滚。
        </el-alert>
      </div>

      <template #footer>
        <el-button v-if="wizardStep > 0" @click="wizardStep--">上一步</el-button>
        <el-button v-if="wizardStep === 0" type="primary" @click="wizardNext0">下一步：匹配模板</el-button>
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
          <el-descriptions-item label="阶段数">{{ previewContent.stages.length }}</el-descriptions-item>
          <el-descriptions-item label="任务数">{{ previewContent.tasks.length }}</el-descriptions-item>
          <el-descriptions-item label="里程碑数">{{ previewContent.milestones.length }}</el-descriptions-item>
          <el-descriptions-item label="交付件数">{{ previewContent.deliverables.length }}</el-descriptions-item>
          <el-descriptions-item label="门禁数" :span="2">{{ previewContent.gates.length }}</el-descriptions-item>
        </el-descriptions>
        <el-collapse v-if="previewContent">
          <el-collapse-item
            v-for="stage in previewContent.stages"
            :key="stage.stageCode"
            :title="`${stage.stageCode} ${stage.name}`"
            :name="stage.stageCode"
          >
            <div class="preview-block">
              <div class="preview-block-title">任务（{{ stageTasks(stage.stageCode).length }}）</div>
              <div v-for="t in stageTasks(stage.stageCode)" :key="t.taskCode" class="preview-line">
                <el-tag size="small" type="info">{{ t.taskCode }}</el-tag> {{ t.name }}
              </div>
            </div>
            <div class="preview-block">
              <div class="preview-block-title">里程碑（{{ stageMilestones(stage.stageCode).length }}）</div>
              <div v-for="m in stageMilestones(stage.stageCode)" :key="m.milestoneCode" class="preview-line">
                <el-tag size="small" type="warning">{{ m.milestoneCode }}</el-tag> {{ m.name }}
              </div>
            </div>
            <div class="preview-block">
              <div class="preview-block-title">交付件（{{ stageDeliverables(stage.stageCode).length }}）</div>
              <div v-for="d in stageDeliverables(stage.stageCode)" :key="d.deliverableCode" class="preview-line">
                <el-tag size="small" :type="d.required ? 'danger' : 'info'">{{ d.deliverableCode }}</el-tag>
                {{ d.name }}
              </div>
            </div>
            <div class="preview-block">
              <div class="preview-block-title">门禁（{{ stageGates(stage.stageCode).length }}）</div>
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
    <el-drawer v-model="detailVisible" :title="`项目详情：${detail?.projectCode || ''}`" size="860px">
      <div v-loading="detailLoading">
        <el-tabs v-model="detailTab">
          <el-tab-pane label="基本信息" name="base">
            <el-descriptions v-if="detail" :column="2" border size="small">
              <el-descriptions-item label="项目编码">{{ detail.projectCode }}</el-descriptions-item>
              <el-descriptions-item label="编码命名空间">
                根 #{{ detail.codeRootId }} · 序号 {{ detail.projectSequence }} · 规则 {{ detail.codeRuleVersion }}
              </el-descriptions-item>
              <el-descriptions-item label="项目名称" :span="2">{{ detail.projectName }}</el-descriptions-item>
              <el-descriptions-item label="签约方式">
                <dict-tag :type="DICT_TYPE.PMS_SIGNING_METHOD" :value="detail.signingMethod!" />
              </el-descriptions-item>
              <el-descriptions-item label="项目类别">
                <dict-tag :type="DICT_TYPE.PMS_PROJECT_CATEGORY" :value="detail.projectCategory!" />
              </el-descriptions-item>
              <el-descriptions-item label="实施方式">
                <dict-tag :type="DICT_TYPE.PMS_IMPLEMENTATION_METHOD" :value="detail.implementationMode!" />
              </el-descriptions-item>
              <el-descriptions-item label="重大项目级别">
                <dict-tag
                  v-if="detail.majorProjectLevel"
                  :type="DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL"
                  :value="detail.majorProjectLevel"
                />
                <span v-else>不限</span>
              </el-descriptions-item>
              <el-descriptions-item label="客户">{{ detail.customerName || '-' }}（{{ detail.customerCode || '-' }}）</el-descriptions-item>
              <el-descriptions-item label="合同号">{{ detail.contractNo || '-' }}</el-descriptions-item>
              <el-descriptions-item label="实施地点">{{ detail.implementationLocation || '-' }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <dict-tag :type="DICT_TYPE.PMS_PROJECT_LIFECYCLE_STAGE" :value="detail.status!" />
              </el-descriptions-item>
              <el-descriptions-item label="创建来源">
                <dict-tag :type="DICT_TYPE.PMS_PROJECT_SOURCE_TYPE" :value="detail.sourceType!" />
              </el-descriptions-item>
              <el-descriptions-item label="创建原因" :span="2">{{ detail.creationReason || '-' }}</el-descriptions-item>
              <el-descriptions-item label="模板绑定" :span="2">
                <span v-if="detail.lifecycleTemplateId">
                  #{{ detail.lifecycleTemplateId }} v{{ detail.lifecycleTemplateRevisionNo }} ·
                  <dict-tag :type="DICT_TYPE.PMS_TEMPLATE_LOAD_METHOD" :value="detail.templateLoadMethod!" />
                  <span v-if="detail.processDefinitionKey" class="ml-8px text-12px text-gray-400">
                    流程 {{ detail.processDefinitionKey }}@{{ detail.processDefinitionVersion }}
                  </span>
                </span>
                <span v-else class="text-gray-400">-</span>
              </el-descriptions-item>
              <el-descriptions-item label="创建时间" :span="2">{{ formatDateTime(detail.createTime) }}</el-descriptions-item>
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
                  <div class="preview-block-title">里程碑（{{ instMilestones(stage.stageCode).length }}）</div>
                  <div v-for="m in instMilestones(stage.stageCode)" :key="m.milestoneCode" class="preview-line">
                    <dict-tag :type="DICT_TYPE.PMS_PROJECT_MILESTONE_STATUS" :value="m.status" />
                    <el-tag size="small" type="warning">{{ m.milestoneCode }}</el-tag>
                    {{ m.name }}
                    <span class="text-12px text-gray-400">{{ m.timing }}</span>
                  </div>
                </div>
                <div class="preview-block">
                  <div class="preview-block-title">交付件（{{ instDeliverables(stage.stageCode).length }}）</div>
                  <div v-for="d in instDeliverables(stage.stageCode)" :key="d.deliverableCode" class="preview-line">
                    <dict-tag :type="DICT_TYPE.PMS_PROJECT_DELIVERABLE_STATUS" :value="d.status" />
                    <el-tag size="small" :type="d.required ? 'danger' : 'info'">{{ d.deliverableCode }}</el-tag>
                    {{ d.name }}
                  </div>
                </div>
                <div class="preview-block">
                  <div class="preview-block-title">门禁（{{ instGates(stage.stageCode).length }}）</div>
                  <div v-for="g in instGates(stage.stageCode)" :key="g.gateCode" class="preview-line">
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
                <template #default="{ row }">{{ row.memberName || `#${row.userId}` }}</template>
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
          <el-input :model-value="`${assignTarget?.projectCode} ${assignTarget?.projectName}`" disabled />
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
        <el-form-item label="成员工号">
          <el-input v-model="assignForm.employeeNo" placeholder="可选（快照留痕）" />
        </el-form-item>
        <el-form-item label="成员姓名">
          <el-input v-model="assignForm.memberName" placeholder="可选（快照留痕）" />
        </el-form-item>
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
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getStrDictOptions, getDictLabel } from '@/utils/dict'
import { dateFormatter, formatDate } from '@/utils/formatTime'
import * as ProjectsApi from '@/api/pms/project/projects'
import type {
  ProjectMasterVO,
  ProjectInstancesVO,
  ProjectMemberAssignmentVO,
  ProjectMatchTemplatesRespVO,
  TemplateCandidateVO
} from '@/api/pms/project/projects'
import { getProjectTemplateRevision } from '@/api/pms/project/project-templates'
import type { TemplateDefinitionContent } from '@/api/pms/project/project-templates'
import * as UserApi from '@/api/system/user'

defineOptions({ name: 'PmsProjects' })

const message = useMessage()

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
/** 幂等键：每次打开向导生成一次，重试/双击共用同一键（同键同摘要重放返回原资源） */
let idempotencyKey = ''

const createForm = reactive({
  projectName: '',
  customerCode: '',
  customerName: '',
  contractNo: '',
  orderOfficeCompanyCode: '',
  orderOfficeDepartmentCode: '',
  implementationLocation: '',
  signingMethod: '',
  projectCategory: '',
  implementationMode: '',
  majorProjectLevel: '' as string,
  creationReason: '',
  serviceManagerUserId: undefined as number | undefined
})

const createRules = {
  projectName: [{ required: true, message: '项目名称不能为空', trigger: 'blur' }],
  signingMethod: [{ required: true, message: '签约方式不能为空', trigger: 'change' }],
  projectCategory: [{ required: true, message: '项目类别不能为空', trigger: 'change' }],
  implementationMode: [{ required: true, message: '实施方式不能为空', trigger: 'change' }],
  creationReason: [{ required: true, message: '手工创建原因不能为空（BR-2）', trigger: 'blur' }]
}

const openWizard = () => {
  wizardStep.value = 0
  Object.assign(createForm, {
    projectName: '',
    customerCode: '',
    customerName: '',
    contractNo: '',
    orderOfficeCompanyCode: '',
    orderOfficeDepartmentCode: '',
    implementationLocation: '',
    signingMethod: '',
    projectCategory: '',
    implementationMode: '',
    majorProjectLevel: '',
    creationReason: '',
    serviceManagerUserId: undefined
  })
  selectedTemplateId.value = undefined
  matchResult.value = null
  idempotencyKey = `pm01-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
  wizardVisible.value = true
}

const wizardNext0 = async () => {
  await wizardFormRef.value?.validate()
  await runMatch()
  wizardStep.value = 1
}

// ============ 模板匹配（步骤②） ============
const matchLoading = ref(false)
const matchResult = ref<ProjectMatchTemplatesRespVO | null>(null)
const selectedTemplateId = ref<number | undefined>(undefined)

const matchCandidates = computed<TemplateCandidateVO[]>(() => matchResult.value?.candidates || [])
const selectedTemplate = computed(
  () => matchCandidates.value.find((c) => c.templateId === selectedTemplateId.value) || null
)
/** 步骤② → ③ 门槛：MATCHED 自动放行；MULTI_MATCH 必选一个；NO_MATCH 阻断 */
const canGoStep2 = computed(() => {
  if (!matchResult.value) return false
  if (matchResult.value.outcome === 'MATCHED') return true
  if (matchResult.value.outcome === 'MULTI_MATCH') return !!selectedTemplateId.value
  return false
})

const runMatch = async () => {
  matchLoading.value = true
  selectedTemplateId.value = undefined
  try {
    matchResult.value = await ProjectsApi.matchTemplates({
      signingMethod: createForm.signingMethod || undefined,
      projectCategory: createForm.projectCategory || undefined,
      implementationMode: createForm.implementationMode || undefined,
      majorProjectLevel: createForm.majorProjectLevel || undefined
    })
  } finally {
    matchLoading.value = false
  }
}

const selectCandidate = (row: TemplateCandidateVO) => {
  if (matchResult.value?.outcome === 'MULTI_MATCH') {
    selectedTemplateId.value = row.templateId
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
    const detail = await getProjectTemplateRevision(candidate.templateId, candidate.latestRevisionNo)
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
  creating.value = true
  try {
    const created = await ProjectsApi.createProject(
      {
        projectName: createForm.projectName,
        customerCode: createForm.customerCode || undefined,
        customerName: createForm.customerName || undefined,
        contractNo: createForm.contractNo || undefined,
        orderOfficeCompanyCode: createForm.orderOfficeCompanyCode || undefined,
        orderOfficeDepartmentCode: createForm.orderOfficeDepartmentCode || undefined,
        implementationLocation: createForm.implementationLocation || undefined,
        signingMethod: createForm.signingMethod,
        projectCategory: createForm.projectCategory,
        implementationMode: createForm.implementationMode,
        majorProjectLevel: createForm.majorProjectLevel || null,
        creationReason: createForm.creationReason,
        templateId: selectedTemplateId.value,
        serviceManagerUserId: createForm.serviceManagerUserId || null
      },
      idempotencyKey
    )
    message.success(
      `创建成功：${created.projectCode}（实例：阶段${created.stageCount}/任务${created.taskCount}` +
        `/里程碑${created.milestoneCount}/交付件${created.deliverableCount}/门禁${created.gateCount}）`
    )
    wizardVisible.value = false
    await load()
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
  } finally {
    detailLoading.value = false
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
const assignForm = reactive({
  userId: undefined as number | undefined,
  employeeNo: '',
  memberName: '',
  effectiveFrom: ''
})
const assignRules = {
  userId: [{ required: true, message: '请选择服务经理用户', trigger: 'change' }]
}

const openAssign = (row: ProjectMasterVO) => {
  assignTarget.value = row
  Object.assign(assignForm, { userId: undefined, employeeNo: '', memberName: '', effectiveFrom: '' })
  assignVisible.value = true
}

const submitAssign = async () => {
  await assignFormRef.value?.validate()
  saving.value = true
  try {
    await ProjectsApi.assignManager(assignTarget.value!.id!, {
      userId: assignForm.userId!,
      employeeNo: assignForm.employeeNo || undefined,
      memberName: assignForm.memberName || undefined,
      effectiveFrom: assignForm.effectiveFrom || undefined
    })
    message.success('指派成功（旧区间已关闭，新区间生效）')
    assignVisible.value = false
    await load()
    if (detailVisible.value && detail.value?.id === assignTarget.value?.id) {
      members.value = await ProjectsApi.getProjectMembers(assignTarget.value!.id!)
    }
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  load()
})
</script>

<style lang="scss" scoped>
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
