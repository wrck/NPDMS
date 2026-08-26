<template>
  <div>
    <!-- ============ 顶部项目档案区 ============ -->
    <ContentWrap>
      <div class="project-header">
        <div class="project-header-left">
          <div class="project-title-row">
            <span class="project-code">{{ project.code || '—' }}</span>
            <h2 class="project-name">{{ project.name || '未选择项目' }}</h2>
            <span class="project-status" :class="`project-status--${projectStatusTone}`">
              {{ projectStatusLabel }}
            </span>
            <el-tag v-if="project.majorProjectFlag" type="warning" size="small" effect="dark">
              <Icon icon="ep:star-filled" /> 重大项目
            </el-tag>
            <el-tag v-if="project.category" type="info" size="small">{{ categoryLabel }}</el-tag>
          </div>
          <div class="project-meta-row">
            <span class="meta-item"><Icon icon="ep:office-building" />{{ project.customerName || panoramic.customerName || '-' }}</span>
            <span class="meta-item"><Icon icon="ep:user" /><UserTag :user-id="project.managerUserId" /></span>
            <span class="meta-item"><Icon icon="ep:folder" />{{ project.projectType || '-' }}</span>
            <span class="meta-item"><Icon icon="ep:calendar" />{{ formatDate(project.createTime) }}</span>
          </div>
        </div>
        <div class="project-header-right">
          <PmsEntitySelect
            v-model="currentProjectId"
            :api="ProjectApi.getProjectPage"
            :label-field="['code', 'name']"
            value-field="id"
            query-field="name"
            placeholder="切换项目…"
            class="!w-280px"
            @change="onProjectChange"
          />
          <el-button type="primary" plain @click="goBackToList">
            <Icon icon="ep:back" />返回列表
          </el-button>
        </div>
      </div>
    </ContentWrap>

    <!-- ============ 主体：左侧导轨 + 右侧内容区 ============ -->
    <div class="detail-body">
      <!-- 左侧导轨：三段式结构（项目概览 + 项目阶段 + 业务中心） -->
      <ContentWrap class="rail-wrap">
        <!-- ① 项目概览（固定 5 项） -->
        <div class="rail-stage">
          <div class="rail-stage-title">项目概览</div>
          <button
            v-for="(step, idx) in overviewSteps"
            :key="step.key"
            class="rail-item rail-item--flow"
            :class="{ 'rail-item--active': activeTab === step.key }"
            @click="switchTab(step.key)"
          >
            <span class="step-no">{{ idx + 1 }}</span>
            <Icon :icon="step.icon" class="rail-icon" />
            <span class="rail-label">{{ step.label }}</span>
          </button>
        </div>

        <!-- ② 项目阶段（动态，按项目类型定制生命周期） -->
        <div class="rail-stage">
          <div class="rail-stage-title">项目阶段</div>
          <div v-if="!phases.length" class="rail-empty">暂无阶段数据</div>
          <button
            v-for="(phase, idx) in phases"
            :key="'phase-' + phase.id"
            class="rail-item rail-item--flow"
            :class="{ 'rail-item--active': activeTab === 'phase-' + phase.id }"
            @click="switchToPhase(phase)"
          >
            <span class="step-no">{{ idx + 1 }}</span>
            <Icon icon="ep:milestone" class="rail-icon" />
            <span class="rail-label">{{ phase.name }}</span>
            <span class="rail-phase-progress">{{ phaseProgress(phase.id!) }}%</span>
          </button>
        </div>

        <!-- ③ 业务中心（按功能分组） -->
        <div class="rail-stage">
          <div class="rail-stage-title">业务中心</div>
          <div v-for="group in businessGroups" :key="group.key" class="rail-group">
            <div class="rail-group-title">{{ group.title }}</div>
            <!-- 流程步骤（带序号） -->
            <button
              v-for="(step, idx) in group.flowSteps"
              :key="step.key"
              class="rail-item rail-item--flow"
              :class="{ 'rail-item--active': activeTab === step.key }"
              @click="switchTab(step.key)"
            >
              <span class="step-no">{{ idx + 1 }}</span>
              <Icon :icon="step.icon" class="rail-icon" />
              <span class="rail-label">{{ step.label }}</span>
            </button>
            <!-- 并行事项（虚线分隔） -->
            <template v-if="group.parallelItems?.length">
              <div class="parallel-divider">并行事项</div>
              <button
                v-for="item in group.parallelItems"
                :key="item.key"
                class="rail-item rail-item--parallel"
                :class="{ 'rail-item--active': activeTab === item.key }"
                @click="switchTab(item.key)"
              >
                <Icon :icon="item.icon" class="rail-icon" />
                <span class="rail-label">{{ item.label }}</span>
              </button>
            </template>
          </div>
        </div>
      </ContentWrap>

      <!-- 右侧内容区 -->
      <div class="canvas">
        <!-- ============ 项目概览：项目主数据（旧链只读，F-PM01 冻结编辑） ============ -->
        <ContentWrap v-show="activeTab === 'project'">
          <div class="panel-header">
            <span class="panel-title"><Icon icon="ep:document" /> 项目主数据</span>
            <div class="panel-header-actions">
              <el-button link type="primary" @click="goPage('/pms/project-management/project')">前往项目列表</el-button>
            </div>
          </div>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="项目编号">{{ project.id || '-' }}</el-descriptions-item>
            <el-descriptions-item label="项目编码">{{ project.code || '-' }}</el-descriptions-item>
            <el-descriptions-item label="项目名称">{{ project.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="项目类型">{{ project.projectType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="项目分类">{{ categoryLabel }}</el-descriptions-item>
            <el-descriptions-item label="重大项目">{{ project.majorProjectFlag ? '是' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="客户名称">{{ project.customerName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="项目经理"><UserTag :user-id="project.managerUserId" /></el-descriptions-item>
            <el-descriptions-item label="项目状态">{{ projectStatusLabel }}</el-descriptions-item>
            <el-descriptions-item label="创建时间" :span="3">{{ formatDate(project.createTime) }}</el-descriptions-item>
          </el-descriptions>
        </ContentWrap>

        <!-- ============ 项目概览：客户与联系人 ============ -->
        <ContentWrap v-show="activeTab === 'customer'">
          <div class="panel-header">
            <span class="panel-title"><Icon icon="ep:office-building" /> 客户与联系人</span>
            <el-button link type="primary" @click="goPage('/customer-asset/customer-contact')">前往客户联系人</el-button>
          </div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="客户编号"><CustomerTag :customer-id="panoramic.customerId" /></el-descriptions-item>
            <el-descriptions-item label="客户编码">{{ panoramic.customerCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="客户名称" :span="2">{{ panoramic.customerName || project.customerName || '-' }}</el-descriptions-item>
          </el-descriptions>
        </ContentWrap>

        <!-- ============ 项目概览：项目团队 ============ -->
        <ContentWrap v-show="activeTab === 'team'">
          <div class="panel-header">
            <span class="panel-title"><Icon icon="ep:user-filled" /> 项目团队</span>
            <el-button link type="primary" @click="goPage('/pms/project-management/project-team')">前往团队管理</el-button>
          </div>
          <el-table :data="teamMembers" empty-text="暂无团队成员" size="small">
            <el-table-column prop="userId" label="用户编号" width="100">
              <template #default="{ row }">
                <UserTag :user-id="row.userId" />
              </template>
            </el-table-column>
            <el-table-column prop="roleCode" label="角色编码" min-width="140" />
            <el-table-column prop="roleName" label="角色名称" min-width="120" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="row.status" />
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
          </el-table>
        </ContentWrap>

        <!-- ============ 项目概览：任务WBS ============ -->
        <ContentWrap v-show="activeTab === 'task'">
          <div class="panel-header">
            <span class="panel-title"><Icon icon="ep:tickets" /> 任务 WBS</span>
            <el-button link type="primary" @click="goPage('/pms/project-management/schedule/project-task')">前往任务管理</el-button>
          </div>
          <el-table
            :data="taskTree"
            row-key="id"
            default-expand-all
            :tree-props="{ children: 'children' }"
            empty-text="暂无任务数据"
            size="small"
          >
            <el-table-column prop="name" label="任务名称" min-width="220" />
            <el-table-column prop="code" label="编码" width="120" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <span class="status-pill" :class="`status-pill--${taskStatusTone(row.status)}`">{{ taskStatusLabel(row.status) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="进度" width="140">
              <template #default="{ row }">
                <el-progress :percentage="row.progress ?? 0" :stroke-width="6" :show-text="false" />
                <span class="progress-text">{{ row.progress ?? 0 }}%</span>
              </template>
            </el-table-column>
            <el-table-column prop="assigneeUserId" label="负责人" width="90">
              <template #default="{ row }">
                <UserTag :user-id="row.assigneeUserId" />
              </template>
            </el-table-column>
          </el-table>
        </ContentWrap>

        <!-- ============ 项目概览：项目风险 ============ -->
        <ContentWrap v-show="activeTab === 'risk'">
          <div class="panel-header">
            <span class="panel-title"><Icon icon="ep:warning" /> 项目风险</span>
            <el-button link type="primary" @click="goPage('/pms/project-management/project-risk')">前往风险台账</el-button>
          </div>
          <el-table :data="risks" empty-text="暂无风险数据" size="small" :row-class-name="riskRowClass">
            <el-table-column prop="title" label="风险标题" min-width="180" show-overflow-tooltip />
            <el-table-column label="等级" width="80">
              <template #default="{ row }">
                <span class="status-pill" :class="`status-pill--${riskLevelTone(row.riskLevel)}`">{{ row.riskLevel || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <span class="status-pill" :class="`status-pill--${riskStatusTone(row.status)}`">{{ riskStatusLabel(row.status) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="ownerUserId" label="负责人" width="90">
              <template #default="{ row }">
                <UserTag :user-id="row.ownerUserId" />
              </template>
            </el-table-column>
            <el-table-column prop="mitigation" label="应对措施" min-width="180" show-overflow-tooltip />
          </el-table>
        </ContentWrap>

        <!-- ============ 项目阶段详情面板（动态，key='phase-{id}'） ============ -->
        <ContentWrap v-show="isPhaseTab(activeTab)">
          <div class="panel-header">
            <span class="panel-title">
              <Icon icon="ep:milestone" /> 阶段详情<template v-if="activePhase"> - {{ activePhase.name }}</template>
            </span>
            <el-button link type="primary" @click="goPage('/pms/project-management/schedule/project-phase')">前往阶段管理</el-button>
          </div>
          <el-empty v-if="!activePhase" description="请从左侧选择阶段" />
          <template v-else>
            <!-- 阶段信息卡 -->
            <el-descriptions :column="3" border size="small">
              <el-descriptions-item label="阶段名称">{{ activePhase.name || '-' }}</el-descriptions-item>
              <el-descriptions-item label="编码">{{ activePhase.code || '-' }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <span class="status-pill" :class="`status-pill--${phaseStatusTone(activePhase.status)}`">{{ phaseStatusLabel(activePhase.status) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="计划开始">{{ formatDate(activePhase.planStartTime) }}</el-descriptions-item>
              <el-descriptions-item label="计划结束">{{ formatDate(activePhase.planEndTime) }}</el-descriptions-item>
              <el-descriptions-item label="实际开始">{{ formatDate(activePhase.actualStartTime) }}</el-descriptions-item>
              <el-descriptions-item label="实际结束">{{ formatDate(activePhase.actualEndTime) }}</el-descriptions-item>
              <el-descriptions-item label="负责角色">{{ activePhase.responsibleRole || '-' }}</el-descriptions-item>
              <el-descriptions-item label="负责人"><UserTag :user-id="activePhase.responsibleUserId" /></el-descriptions-item>
              <el-descriptions-item label="准入条件" :span="3">{{ activePhase.entryCriteria || '-' }}</el-descriptions-item>
              <el-descriptions-item label="出口门禁" :span="3">{{ activePhase.exitCriteria || '-' }}</el-descriptions-item>
              <el-descriptions-item v-if="activePhase.deviationReason" label="偏差原因" :span="3">{{ activePhase.deviationReason }}</el-descriptions-item>
              <el-descriptions-item label="阶段进度" :span="3">
                <el-progress :percentage="phaseProgress(activePhase.id!)" :stroke-width="10" />
              </el-descriptions-item>
            </el-descriptions>
            <!-- 阶段任务子集 -->
            <div class="hierarchy-section">
              <div class="hierarchy-section-title">
                <Icon icon="ep:tickets" /> 阶段任务
                <span class="hierarchy-hint">按阶段编码/序号匹配任务，无匹配时显示全部任务</span>
              </div>
              <el-table :data="phaseTasks(activePhase.id!)" empty-text="该阶段下暂无任务" size="small" row-key="id" default-expand-all :tree-props="{ children: 'children' }">
                <el-table-column prop="name" label="任务名称" min-width="220" show-overflow-tooltip />
                <el-table-column prop="code" label="编码" width="140" />
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <span class="status-pill" :class="`status-pill--${taskStatusTone(row.status)}`">{{ taskStatusLabel(row.status) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="进度" width="140">
                  <template #default="{ row }">
                    <el-progress :percentage="row.progress ?? 0" :stroke-width="6" :show-text="false" />
                    <span class="progress-text">{{ row.progress ?? 0 }}%</span>
                  </template>
                </el-table-column>
                <el-table-column prop="assigneeUserId" label="负责人" width="90">
                  <template #default="{ row }">
                    <UserTag :user-id="row.assigneeUserId" />
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <!-- 阶段操作 -->
            <div class="phase-actions">
              <el-button
                v-if="activePhase.status === 0 || activePhase.status === 1"
                type="success"
                @click="completePhase(activePhase)"
              >
                <Icon icon="ep:circle-check" /> 完成阶段
              </el-button>
              <el-button type="primary" plain @click="goPage('/pms/project-management/schedule/project-task')">
                <Icon icon="ep:edit" /> 管理任务
              </el-button>
            </div>
          </template>
        </ContentWrap>

        <!-- ============ 通用模块面板（配置驱动，按 projectId 加载真实数据） ============ -->
        <ContentWrap v-show="isGenericTab">
          <div class="panel-header">
            <span class="panel-title">
              <Icon :icon="currentModule?.icon || 'ep:menu'" /> {{ currentModule?.label }}
            </span>
            <div class="panel-header-actions">
              <el-button v-if="currentModule?.create" type="primary" size="small" @click="openCreate">
                <Icon icon="ep:plus" /> 新增
              </el-button>
              <el-button
                v-if="currentModule?.create && isTemplatedModule"
                type="success"
                size="small"
                @click="openCreateWithTemplate"
              >
                <Icon icon="ep:document-copy" /> 使用模板新增
              </el-button>
              <el-button v-if="currentModule?.path" link type="primary" @click="goPage(currentModule.path)">
                前往完整页面
              </el-button>
            </div>
          </div>
          <el-table
            :data="moduleData"
            v-loading="moduleLoading"
            empty-text="暂无数据"
            size="small"
            max-height="520"
            class="clickable-table"
            @row-click="openDetail"
          >
            <el-table-column
              v-for="col in currentModule?.columns"
              :key="col.prop"
              :prop="col.prop"
              :label="col.label"
              :width="col.width"
              :min-width="col.minWidth"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <span v-if="col.type === 'status'" class="status-pill" :class="`status-pill--${getModuleStatusTone(row, col.prop || moduleStatusField)}`">
                  {{ getModuleStatusLabel(row, col.prop || moduleStatusField) }}
                </span>
                <span v-else-if="col.type === 'time'">{{ formatDate(row[col.prop]) }}</span>
                <span v-else>{{ row[col.prop] ?? '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="hasActions"
              label="操作"
              :width="actionColumnWidth"
              fixed="right"
            >
              <template #default="{ row }">
                <el-button
                  v-for="act in getVisibleActions(row)"
                  :key="act.label"
                  :type="act.type || 'primary'"
                  link
                  size="small"
                  @click.stop="runModuleAction(act, row)"
                >{{ act.label }}</el-button>
                <el-button v-if="currentModule?.update" type="primary" link size="small" @click.stop="openEdit(row)">编辑</el-button>
                <el-button v-if="currentModule?.delete" type="danger" link size="small" @click.stop="deleteRow(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="moduleTotal > modulePageSize" class="panel-pagination">
            <el-pagination
              v-model:current-page="modulePageNo"
              :page-size="modulePageSize"
              :total="moduleTotal"
              layout="total, prev, pager, next"
              small
              @current-change="loadModuleData"
            />
          </div>
        </ContentWrap>

        <!-- ============ 割接管理（父子层级：任务→四件套） ============ -->
        <ContentWrap v-show="activeTab === 'cutover'">
          <div class="panel-header">
            <span class="panel-title"><Icon icon="ep:switch" /> 割接管理</span>
            <el-button link type="primary" @click="goPage('/pms/cutover/cut-task')">前往割接管理</el-button>
          </div>
          <!-- 第一层：割接任务列表 -->
          <div class="hierarchy-section">
            <div class="hierarchy-section-title">
              <Icon icon="ep:document" /> 割接任务
              <span class="hierarchy-hint">选择任务后查看其风险/方案/执行/观察</span>
            </div>
            <el-table
              :data="cutTasks"
              v-loading="cutLoading"
              empty-text="暂无割接任务"
              size="small"
              highlight-current-row
              @current-change="onCutTaskSelect"
            >
              <el-table-column prop="code" label="编码" width="140" />
              <el-table-column prop="name" label="任务名称" min-width="180" show-overflow-tooltip />
              <el-table-column prop="cutoverType" label="割接类型" width="100" />
              <el-table-column prop="riskLevel" label="风险等级" width="90" />
              <el-table-column label="计划时间" width="150">
                <template #default="{ row }">{{ formatDate(row.scheduledTime) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <span class="status-pill" :class="`status-pill--${cutTaskStatusTone(row.status)}`">{{ cutTaskStatusLabel(row.status) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="240" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="row.status === 0" type="primary" link size="small" @click="doCutAction(submitForReview, row)">提交评审</el-button>
                  <el-button v-if="row.status === 2" type="success" link size="small" @click="doCutActionWithOpinion(approveCutTask, row)">审批通过</el-button>
                  <el-button v-if="row.status === 2" type="danger" link size="small" @click="doCutActionWithOpinion(rejectCutTask, row)">驳回</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <!-- 第二层：选中任务的四件套 -->
          <div v-if="selectedCutTask" class="hierarchy-children">
            <el-tabs v-model="activeCutSubTab" class="cut-sub-tabs">
              <el-tab-pane label="割接风险" name="cut-risk">
                <el-table :data="cutRisks" v-loading="cutSubLoading" empty-text="暂无风险" size="small">
                  <el-table-column prop="code" label="编码" width="120" />
                  <el-table-column prop="name" label="风险名称" min-width="160" show-overflow-tooltip />
                  <el-table-column prop="riskType" label="类型" width="100" />
                  <el-table-column label="状态" width="90">
                    <template #default="{ row }">
                      <span class="status-pill" :class="`status-pill--${commonStatusTone(row.status)}`">{{ commonStatusLabel(row.status) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="ownerUserId" label="负责人" width="90">
                    <template #default="{ row }">
                      <UserTag :user-id="row.ownerUserId" />
                    </template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>
              <el-tab-pane label="割接方案" name="cut-plan">
                <el-table :data="cutPlans" v-loading="cutSubLoading" empty-text="暂无方案" size="small">
                  <el-table-column prop="code" label="编码" width="120" />
                  <el-table-column prop="name" label="方案名称" min-width="160" show-overflow-tooltip />
                  <el-table-column prop="level" label="级别" width="80" />
                  <el-table-column label="状态" width="90">
                    <template #default="{ row }">
                      <span class="status-pill" :class="`status-pill--${commonStatusTone(row.status)}`">{{ commonStatusLabel(row.status) }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>
            </el-tabs>
          </div>
          <el-empty v-else description="请在上方选择割接任务，查看其风险和方案" />
        </ContentWrap>

        <!-- ============ 巡检管理（父子层级：任务→报告/问题） ============ -->
        <ContentWrap v-show="activeTab === 'inspection'">
          <div class="panel-header">
            <span class="panel-title"><Icon icon="ep:aim" /> 巡检管理</span>
            <el-button link type="primary" @click="goPage('/pms/service/srv-task')">前往巡检管理</el-button>
          </div>
          <!-- 第一层：巡检任务列表 -->
          <div class="hierarchy-section">
            <div class="hierarchy-section-title">
              <Icon icon="ep:document" /> 巡检任务
              <span class="hierarchy-hint">选择任务后查看其报告与问题</span>
            </div>
            <el-table
              :data="srvTasks"
              v-loading="srvLoading"
              empty-text="暂无巡检任务"
              size="small"
              highlight-current-row
              @current-change="onSrvTaskSelect"
            >
              <el-table-column prop="code" label="编码" width="140" />
              <el-table-column prop="name" label="任务名称" min-width="180" show-overflow-tooltip />
              <el-table-column prop="inspectionMode" label="巡检方式" width="100" />
              <el-table-column label="计划时间" width="150">
                <template #default="{ row }">{{ formatDate(row.scheduledTime) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <span class="status-pill" :class="`status-pill--${commonStatusTone(row.status)}`">{{ commonStatusLabel(row.status) }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <!-- 第二层：选中任务的报告与问题 -->
          <div v-if="selectedSrvTask" class="hierarchy-children">
            <el-tabs v-model="activeSrvSubTab" class="cut-sub-tabs">
              <el-tab-pane label="巡检报告" name="srv-report">
                <el-table :data="srvReports" v-loading="srvSubLoading" empty-text="暂无报告" size="small">
                  <el-table-column prop="code" label="编码" width="140" />
                  <el-table-column prop="reportType" label="报告类型" width="120" />
                  <el-table-column label="生成时间" width="150">
                    <template #default="{ row }">{{ formatDate(row.generatedTime) }}</template>
                  </el-table-column>
                  <el-table-column label="状态" width="90">
                    <template #default="{ row }">
                      <span class="status-pill" :class="`status-pill--${commonStatusTone(row.status)}`">{{ commonStatusLabel(row.status) }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>
              <el-tab-pane label="巡检问题" name="srv-issue">
                <el-table :data="srvIssues" v-loading="srvSubLoading" empty-text="暂无问题" size="small">
                  <el-table-column prop="code" label="编码" width="120" />
                  <el-table-column prop="name" label="问题名称" min-width="160" show-overflow-tooltip />
                  <el-table-column prop="severity" label="严重度" width="80" />
                  <el-table-column label="状态" width="90">
                    <template #default="{ row }">
                      <span class="status-pill" :class="`status-pill--${commonStatusTone(row.status)}`">{{ commonStatusLabel(row.status) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="ownerUserId" label="负责人" width="90">
                    <template #default="{ row }">
                      <UserTag :user-id="row.ownerUserId" />
                    </template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>
            </el-tabs>
          </div>
          <el-empty v-else description="请在上方选择巡检任务，查看其报告与问题" />
        </ContentWrap>
      </div>
    </div>

    <!-- ============ 通用详情抽屉（点击行查看完整字段） ============ -->
    <el-drawer
      v-model="detailVisible"
      size="50%"
      :title="`${currentModule?.label || ''}详情${detailRow?.code ? ' · ' + detailRow.code : ''}`"
    >
      <el-descriptions v-if="detailRow" :column="2" border size="small">
        <el-descriptions-item label="ID">{{ detailRow.id ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="编码">{{ detailRow.code ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="名称" :span="2">{{ detailRow.name ?? '-' }}</el-descriptions-item>
        <template v-for="col in currentModule?.columns" :key="col.prop">
          <el-descriptions-item
            v-if="col.prop && col.prop !== 'id' && col.prop !== 'code' && col.prop !== 'name'"
            :label="col.label"
          >
            <span v-if="col.type === 'status'" class="status-pill" :class="`status-pill--${getModuleStatusTone(detailRow, col.prop || moduleStatusField)}`">
              {{ getModuleStatusLabel(detailRow, col.prop || moduleStatusField) }}
            </span>
            <span v-else-if="col.type === 'time'">{{ formatDate(detailRow[col.prop]) }}</span>
            <span v-else>{{ detailRow[col.prop] ?? '-' }}</span>
          </el-descriptions-item>
        </template>
        <el-descriptions-item label="备注" :span="2">{{ detailRow.remark ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ detailRow.version ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDate(detailRow.createTime) }}</el-descriptions-item>
      </el-descriptions>
      <!-- 抽屉底部：状态流转操作按钮 -->
      <div v-if="detailRow && currentModule?.actions?.length" class="detail-actions">
        <span class="detail-actions-label">状态流转：</span>
        <el-button
          v-for="act in getVisibleActions(detailRow)"
          :key="act.label"
          :type="act.type || 'primary'"
          size="small"
          @click="runModuleAction(act, detailRow)"
        >{{ act.label }}</el-button>
      </div>
    </el-drawer>

    <!-- ============ 通用新增/编辑弹窗（基于columns自动生成表单） ============ -->
    <el-dialog
      v-model="formVisible"
      :title="formMode === 'create' ? `新增${currentModule?.label || ''}` : `编辑${currentModule?.label || ''}`"
      width="600px"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
        v-loading="formLoading"
      >
        <el-form-item v-for="col in formColumns" :key="col.prop" :label="col.label" :prop="col.prop">
          <el-select v-if="col.type === 'status'" v-model="formData[col.prop]" placeholder="请选择" clearable>
            <el-option
              v-for="(opt, val) in currentModule?.statusMap"
              :key="val"
              :label="opt.label"
              :value="Number(val)"
            />
          </el-select>
          <el-date-picker
            v-else-if="col.type === 'time'"
            v-model="formData[col.prop]"
            type="datetime"
            placeholder="请选择时间"
            class="!w-100percent"
          />
          <el-input
            v-else-if="isLongTextField(col.prop)"
            v-model="formData[col.prop]"
            type="textarea"
            :rows="3"
            placeholder="请输入"
          />
          <el-input v-else v-model="formData[col.prop]" placeholder="请输入" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- ============ 模板选择弹窗（需求分析/实施方案） ============ -->
    <el-dialog
      v-model="templateSelectVisible"
      :title="`选择${templateDocCategoryLabel}模板`"
      width="780px"
      :close-on-click-modal="false"
    >
      <div v-loading="templateSelectLoading" class="template-select-body">
        <el-alert
          v-if="templateSelectHint"
          :title="templateSelectHint"
          type="info"
          :closable="false"
          show-icon
          class="mb-12px"
        />
        <el-empty v-if="!templateSelectLoading && !applicableTemplates.length" description="未匹配到合适模板，请先在「工程文档模板」中维护并发布模板" />
        <div v-else class="template-card-list">
          <div
            v-for="tpl in applicableTemplates"
            :key="tpl.id"
            class="template-card"
            :class="{ 'template-card--active': selectedTemplateId === tpl.id }"
            @click="selectedTemplateId = tpl.id"
          >
            <div class="template-card-header">
              <span class="template-card-name">{{ tpl.name }}</span>
              <el-tag v-if="isDefaultTemplate(tpl)" type="success" size="small">默认</el-tag>
              <el-tag v-if="tpl.parentTemplateId" type="warning" size="small">继承</el-tag>
            </div>
            <div class="template-card-code">{{ tpl.code }}</div>
            <div v-if="tpl.description" class="template-card-desc">{{ tpl.description }}</div>
            <div class="template-card-applicability">
              <span v-if="parseApplicability(tpl).projectType?.length" class="applicability-tag">
                项目类型: {{ parseApplicability(tpl).projectType.join(' / ') }}
              </span>
              <span v-if="parseApplicability(tpl).networkType?.length" class="applicability-tag">
                网络类型: {{ parseApplicability(tpl).networkType.join(' / ') }}
              </span>
              <span v-if="parseApplicability(tpl).implementMode?.length" class="applicability-tag">
                实施模式: {{ parseApplicability(tpl).implementMode.join(' / ') }}
              </span>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="templateSelectVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!selectedTemplateId"
          :loading="templateConfirmLoading"
          @click="confirmTemplateSelection"
        >
          下一步：填写内容
        </el-button>
      </template>
    </el-dialog>

    <!-- ============ 基于模板的新增弹窗（按章节渲染） ============ -->
    <el-dialog
      v-model="templateFormVisible"
      :title="`新增${templateDocCategoryLabel}（按模板章节填写）`"
      width="880px"
      :close-on-click-modal="false"
      :destroy-on-close="true"
    >
      <el-form
        ref="templateFormRef"
        :model="templateFormData"
        :rules="templateFormRules"
        label-width="120px"
        v-loading="templateFormLoading"
      >
        <div class="template-form-meta">
          <el-form-item label="编码" prop="code" required>
            <el-input v-model="templateFormData.code" placeholder="请输入编码" />
          </el-form-item>
          <el-form-item label="名称" prop="name" required>
            <el-input v-model="templateFormData.name" placeholder="请输入名称" />
          </el-form-item>
          <el-form-item v-if="templateDocCategory === 'REQUIREMENT'" label="需求类型">
            <el-input v-model="templateFormData.requirementType" placeholder="如：业务需求/网络需求" />
          </el-form-item>
          <el-form-item v-else label="方案类型">
            <el-input v-model="templateFormData.solutionType" placeholder="如：总体方案/详细方案" />
          </el-form-item>
          <el-form-item v-if="templateDocCategory === 'SOLUTION'" label="评审级别">
            <el-select v-model="templateFormData.reviewLevel" placeholder="请选择" clearable class="!w-100percent">
              <el-option
                v-for="dict in getIntDictOptions(DICT_TYPE.PMS_REVIEW_LEVEL)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </div>
        <el-divider content-position="left">
          <Icon icon="ep:document" /> 模板章节（{{ templateSections.length }} 章）
        </el-divider>
        <div v-if="templateSections.length === 0" class="template-empty-sections">
          模板无章节定义，请检查模板版本配置
        </div>
        <div v-for="section in templateSections" :key="section.code" class="template-section">
          <div class="template-section-title">
            <span class="template-section-no">{{ section.order }}</span>
            <Icon icon="ep:folder" />
            <span>{{ section.title }}</span>
            <el-tag v-if="isSectionRequired(section)" type="danger" size="small">必填</el-tag>
            <el-tag v-else type="info" size="small">可选</el-tag>
          </div>
          <el-form-item
            v-for="field in section.fields"
            :key="field.field"
            :label="field.title"
            :prop="field.field"
            :rules="getFieldRules(section, field)"
          >
            <el-input
              v-if="field.type === 'input' && field.props?.type === 'textarea'"
              v-model="templateFormData[field.field]"
              type="textarea"
              :rows="field.props?.rows || 3"
              :placeholder="`请输入${field.title}`"
            />
            <el-input
              v-else-if="field.type === 'input'"
              v-model="templateFormData[field.field]"
              :placeholder="`请输入${field.title}`"
            />
            <el-input
              v-else-if="field.type === 'number'"
              v-model="templateFormData[field.field]"
              type="number"
              :placeholder="`请输入${field.title}`"
            />
            <el-upload
              v-else-if="field.type === 'uploadFile'"
              :auto-upload="false"
              :limit="5"
            >
              <el-button type="primary" plain size="small">
                <Icon icon="ep:upload" /> 选择文件
              </el-button>
              <template #tip>
                <div class="el-upload__tip">{{ field.title }}（上传后在备注中记录文件链接）</div>
              </template>
            </el-upload>
            <el-input
              v-else
              v-model="templateFormData[field.field]"
              :placeholder="`请输入${field.title}`"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="templateFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="templateFormLoading" @click="submitTemplateForm">
          提交并创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import { formatDate } from '@/utils/formatTime'
import * as ProjectApi from '@/api/pms/project/project'
import UserTag from '@/components/UserTag/index.vue'
import CustomerTag from '@/components/CustomerTag/index.vue'
import { getProjectPanoramic } from '@/api/pms/project/project-panoramic'
import * as PhaseApi from '@/api/pms/project/project-phase'
import * as TaskApi from '@/api/pms/project/project-task'
import * as RiskApi from '@/api/pms/project/project-risk'
import * as TeamApi from '@/api/pms/project/project-team'
import type { ProjectPanoramicVO } from '@/api/pms/project/project-panoramic'
import type { ProjectPhaseVO } from '@/api/pms/project/project-phase'
import type { ProjectTaskTreeVO } from '@/api/pms/project/project-task'
import type { ProjectRiskVO } from '@/api/pms/project/project-risk'
import type { ProjectTeamMemberVO } from '@/api/pms/project/project-team'
// 工程实施域
import * as SiteSurveyApi from '@/api/pms/engineering/site-survey'
import * as RequirementApi from '@/api/pms/engineering/requirement'
import * as BriefingApi from '@/api/pms/engineering/briefing'
import * as EngRiskApi from '@/api/pms/engineering/risk'
import * as AnnouncementApi from '@/api/pms/engineering/announcement'
import * as AuthorizationApi from '@/api/pms/engineering/authorization'
import * as SolutionApi from '@/api/pms/engineering/solution'
import * as ResourceApi from '@/api/pms/engineering/resource'
import * as ArrivalApi from '@/api/pms/engineering/arrival'
import * as InstallationApi from '@/api/pms/engineering/installation'
import * as ConfigurationApi from '@/api/pms/engineering/configuration'
import * as JointTestApi from '@/api/pms/engineering/joint-test'
import * as IssueApi from '@/api/pms/engineering/issue'
import * as DeliverableApi from '@/api/pms/engineering/deliverable'
// 计划/变更
import * as ScheduleBackwardApi from '@/api/pms/project/schedule-backward'
import * as PlanChangeApi from '@/api/pms/project/plan-change'
// 验收闭环域
import * as CompletionCertApi from '@/api/pms/project/completion-certificate'
import * as AcceptanceApi from '@/api/pms/project/acceptance'
import * as DeliverableCheckApi from '@/api/pms/project/deliverable-checklist'
import * as ProjectClosureApi from '@/api/pms/project/project-closure'
import * as ArchiveDocApi from '@/api/pms/project/archive-document'
// 割接域
import * as CutTaskApi from '@/api/pms/cutover/cut-task'
import * as CutRiskApi from '@/api/pms/cutover/cut-risk'
import * as CutPlanApi from '@/api/pms/cutover/cut-plan'
// 巡检维保域
import * as SrvTaskApi from '@/api/pms/service/srv-task'
import * as SrvReportApi from '@/api/pms/service/srv-report'
import * as SrvIssueApi from '@/api/pms/service/srv-issue'
import * as DocTemplateApi from '@/api/pms/engineering/doc-template'

defineOptions({ name: 'PmsProjectDetail' })

const route = useRoute()
const router = useRouter()
const message = useMessage()

// ============ 通用类型 ============
interface ModuleAction {
  label: string
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  show: (row: any) => boolean
  run: (row: any, opinion?: string) => Promise<any>
  confirm?: string
  needOpinion?: boolean
}
interface ModuleColumn {
  prop: string
  label: string
  width?: number | string
  minWidth?: number | string
  type?: 'status' | 'time' | 'text'
}
interface ModuleConfig {
  key: string
  label: string
  icon: string
  path?: string
  desc?: string
  load: (projectId: number, pageNo: number, pageSize: number) => Promise<any>
  columns: ModuleColumn[]
  statusField?: string
  statusMap?: Record<number, { label: string; tone: string }>
  actions?: ModuleAction[]
  create?: (data: any) => Promise<any>
  update?: (data: any) => Promise<any>
  delete?: (id: number) => Promise<any>
  get?: (id: number) => Promise<any>
}

// ============ 通用状态映射 ============
const COMMON_STATUS_MAP: Record<number, { label: string; tone: string }> = {
  0: { label: '草稿', tone: 'gray' },
  1: { label: '待处理', tone: 'yellow' },
  2: { label: '进行中', tone: 'blue' },
  3: { label: '已完成', tone: 'green' },
  4: { label: '已驳回', tone: 'red' },
  5: { label: '已归档', tone: 'gray' }
}
const commonStatusLabel = (s?: number) => COMMON_STATUS_MAP[s ?? -1]?.label ?? '-'
const commonStatusTone = (s?: number) => COMMON_STATUS_MAP[s ?? -1]?.tone ?? 'gray'

// ============ 左侧导轨：① 项目概览（固定 5 项） ============
const overviewSteps = [
  { key: 'project', label: '项目信息', icon: 'ep:document' },
  { key: 'customer', label: '客户与联系人', icon: 'ep:office-building' },
  { key: 'team', label: '项目团队', icon: 'ep:user-filled' },
  { key: 'task', label: '任务WBS', icon: 'ep:tickets' },
  { key: 'risk', label: '项目风险', icon: 'ep:warning' }
]

// ============ 左侧导轨：③ 业务中心（按功能分组） ============
const businessGroups = [
  {
    key: 'engineering',
    title: '工程实施',
    flowSteps: [
      { key: 'site-survey', label: '现场工勘', icon: 'ep:position' },
      { key: 'requirement', label: '需求分析', icon: 'ep:document-copy' },
      { key: 'briefing', label: '工程交底', icon: 'ep:notebook-2' }
    ],
    parallelItems: [
      { key: 'eng-risk', label: '单机风险', icon: 'ep:cpu' },
      { key: 'eng-announcement', label: '技术公告', icon: 'ep:bell' },
      { key: 'eng-authorization', label: '授权借货', icon: 'ep:document-checked' }
    ]
  },
  {
    key: 'planning',
    title: '方案计划',
    flowSteps: [
      { key: 'solution', label: '实施方案', icon: 'ep:files' },
      { key: 'resource', label: '资源就绪', icon: 'ep:box' },
      { key: 'schedule-backward', label: '工期倒排', icon: 'ep:timer' }
    ],
    parallelItems: [
      { key: 'plan-change', label: '计划变更', icon: 'ep:edit' }
    ]
  },
  {
    key: 'deploy',
    title: '实施部署',
    flowSteps: [
      { key: 'arrival', label: '到货签收', icon: 'ep:takeaway-box' },
      { key: 'installation', label: '硬件安装', icon: 'ep:setting' },
      { key: 'configuration', label: '配置调试', icon: 'ep:tools' },
      { key: 'joint-test', label: '业务联调', icon: 'ep:connection' }
    ],
    parallelItems: [
      { key: 'issue', label: '实施问题', icon: 'ep:warning-filled' },
      { key: 'deliverable', label: '交付件', icon: 'ep:folder' }
    ]
  },
  {
    key: 'cutover',
    title: '割接交付',
    flowSteps: [
      { key: 'cutover', label: '割接管理', icon: 'ep:switch' }
    ],
    parallelItems: []
  },
  {
    key: 'acceptance',
    title: '验收收尾',
    flowSteps: [
      { key: 'completion-certificate', label: '完工证明', icon: 'ep:medal' },
      { key: 'acceptance', label: '验收管理', icon: 'ep:circle-check' },
      { key: 'deliverable-checklist', label: '交付件检查', icon: 'ep:folder-checked' },
      { key: 'project-closure', label: '项目闭环', icon: 'ep:lock' },
      { key: 'archive-document', label: '归档文档', icon: 'ep:archive' }
    ],
    parallelItems: []
  },
  {
    key: 'maintenance',
    title: '维保服务',
    flowSteps: [
      { key: 'inspection', label: '巡检管理', icon: 'ep:aim' }
    ],
    parallelItems: []
  }
]

// ============ 通用模块配置（配置驱动，按 projectId 加载真实数据） ============
const moduleConfigs: Record<string, ModuleConfig> = {
  // --- 工程实施：流程步骤 ---
  'site-survey': {
    key: 'site-survey', label: '现场工勘', icon: 'ep:position', path: '/pms/engineering/preparation/eng-site-survey',
    load: (pid, pageNo, pageSize) => SiteSurveyApi.getSiteSurveyPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => SiteSurveyApi.createSiteSurvey(data),
    update: (data) => SiteSurveyApi.updateSiteSurvey(data),
    delete: (id) => SiteSurveyApi.deleteSiteSurvey(id),
    get: (id) => SiteSurveyApi.getSiteSurvey(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'name', label: '工勘名称', minWidth: 180 },
      { prop: 'location', label: '位置', width: 120 },
      { prop: 'surveyDate', label: '工勘日期', width: 120, type: 'time' },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '草稿', tone: 'gray' }, 1: { label: '待确认', tone: 'yellow' }, 2: { label: '已确认', tone: 'blue' }, 3: { label: '已归档', tone: 'green' } },
    actions: [
      { label: '确认', type: 'success', show: (r) => r.status === 1, run: (r) => SiteSurveyApi.confirmSiteSurvey(r.id), confirm: '确认该工勘记录？' },
      { label: '驳回', type: 'danger', show: (r) => r.status === 1, run: (r) => SiteSurveyApi.rejectSiteSurvey(r.id), confirm: '驳回该工勘记录？' },
      { label: '归档', type: 'info', show: (r) => r.status === 2, run: (r) => SiteSurveyApi.archiveSiteSurvey(r.id), confirm: '归档该工勘记录？' }
    ]
  },
  'requirement': {
    key: 'requirement', label: '需求分析', icon: 'ep:document-copy', path: '/pms/engineering/preparation/eng-requirement',
    load: (pid, pageNo, pageSize) => RequirementApi.getRequirementPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => RequirementApi.createRequirement(data),
    update: (data) => RequirementApi.updateRequirement(data),
    delete: (id) => RequirementApi.deleteRequirement(id),
    get: (id) => RequirementApi.getRequirement(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'name', label: '需求名称', minWidth: 180 },
      { prop: 'requirementType', label: '类型', width: 100 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '草稿', tone: 'gray' }, 1: { label: '已提交', tone: 'yellow' }, 2: { label: '已生效', tone: 'blue' }, 3: { label: '已归档', tone: 'green' } },
    actions: [
      { label: '提交', type: 'primary', show: (r) => r.status === 0, run: (r) => RequirementApi.submitRequirement(r.id), confirm: '提交该需求？' },
      { label: '生效', type: 'success', show: (r) => r.status === 1, run: (r) => RequirementApi.markEffectiveRequirement(r.id), confirm: '标记该需求为生效？' },
      { label: '归档', type: 'info', show: (r) => r.status === 2, run: (r) => RequirementApi.archiveRequirement(r.id), confirm: '归档该需求？' }
    ]
  },
  'briefing': {
    key: 'briefing', label: '工程交底', icon: 'ep:notebook-2', path: '/pms/engineering/preparation/eng-briefing',
    load: (pid, pageNo, pageSize) => BriefingApi.getBriefingPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => BriefingApi.createBriefing(data),
    update: (data) => BriefingApi.updateBriefing(data),
    delete: (id) => BriefingApi.deleteBriefing(id),
    get: (id) => BriefingApi.getBriefing(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'name', label: '交底名称', minWidth: 180 },
      { prop: 'briefingType', label: '类型', width: 100 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '草稿', tone: 'gray' }, 1: { label: '待审批', tone: 'yellow' }, 2: { label: '已发布', tone: 'blue' }, 3: { label: '已终止', tone: 'red' } },
    actions: [
      { label: '生成', type: 'primary', show: (r) => r.status === 0, run: (r) => BriefingApi.generateBriefing(r.id), confirm: '生成该交底书？' },
      { label: '审批', type: 'success', show: (r) => r.status === 1, run: (r) => BriefingApi.approveBriefing({ id: r.id, approveAction: 'approve' }), needOpinion: true },
      { label: '发布', type: 'success', show: (r) => r.status === 1, run: (r) => BriefingApi.publishBriefing(r.id), confirm: '发布该交底书？' }
    ]
  },
  // --- 工程实施：并行事项 ---
  'eng-risk': {
    key: 'eng-risk', label: '单机风险', icon: 'ep:cpu', path: '/pms/engineering/safeguard/eng-risk',
    load: (pid, pageNo, pageSize) => EngRiskApi.getRiskPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => EngRiskApi.createRisk(data),
    update: (data) => EngRiskApi.updateRisk(data),
    delete: (id) => EngRiskApi.deleteRisk(id),
    get: (id) => EngRiskApi.getRisk(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'name', label: '风险名称', minWidth: 180 },
      { prop: 'deviceSerial', label: '设备序列号', width: 140 },
      { prop: 'riskLevel', label: '等级', width: 80 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '已识别', tone: 'yellow' }, 1: { label: '处理中', tone: 'blue' }, 2: { label: '已关闭', tone: 'green' }, 3: { label: '已发生', tone: 'red' } },
    actions: [
      { label: '确认', type: 'primary', show: (r) => r.status === 0, run: (r) => EngRiskApi.confirmRisk(r.id), confirm: '确认该风险？' },
      { label: '同步CRM', type: 'info', show: (r) => r.status === 1, run: (r) => EngRiskApi.syncCrmRisk(r.id), confirm: '同步到CRM？' },
      { label: '关闭', type: 'success', show: (r) => r.status === 1, run: (r) => EngRiskApi.closeRisk(r.id), confirm: '关闭该风险？' }
    ]
  },
  'eng-announcement': {
    key: 'eng-announcement', label: '技术公告', icon: 'ep:bell', path: '/pms/engineering/safeguard/eng-announcement',
    load: (_pid, pageNo, pageSize) => AnnouncementApi.getAnnouncementPage({ pageNo, pageSize }),
    create: (data) => AnnouncementApi.createAnnouncement(data),
    update: (data) => AnnouncementApi.updateAnnouncement(data),
    delete: (id) => AnnouncementApi.deleteAnnouncement(id),
    get: (id) => AnnouncementApi.getAnnouncement(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'title', label: '公告标题', minWidth: 200 },
      { prop: 'productModel', label: '产品型号', width: 120 },
      { prop: 'severity', label: '严重度', width: 80 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '草稿', tone: 'gray' }, 1: { label: '已发布', tone: 'blue' }, 2: { label: '已停用', tone: 'red' } },
    actions: [
      { label: '发布', type: 'success', show: (r) => r.status === 0, run: (r) => AnnouncementApi.publishAnnouncement(r.id), confirm: '发布该公告？' },
      { label: '停用', type: 'danger', show: (r) => r.status === 1, run: (r) => AnnouncementApi.disableAnnouncement(r.id), confirm: '停用该公告？' }
    ]
  },
  'eng-authorization': {
    key: 'eng-authorization', label: '授权借货', icon: 'ep:document-checked', path: '/pms/engineering/safeguard/eng-authorization',
    load: (pid, pageNo, pageSize) => AuthorizationApi.getAuthorizationPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => AuthorizationApi.createAuthorization(data),
    update: (data) => AuthorizationApi.updateAuthorization(data),
    delete: (id) => AuthorizationApi.deleteAuthorization(id),
    get: (id) => AuthorizationApi.getAuthorization(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'name', label: '授权名称', minWidth: 180 },
      { prop: 'authorizationType', label: '类型', width: 100 },
      { prop: 'deviceSerial', label: '设备序列号', width: 140 },
      { prop: 'applyStartDate', label: '开始日期', width: 120, type: 'time' },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '草稿', tone: 'gray' }, 1: { label: '待审批', tone: 'yellow' }, 2: { label: '已批准', tone: 'blue' }, 3: { label: '已召回', tone: 'red' }, 4: { label: '已终止', tone: 'gray' } },
    actions: [
      { label: '提交', type: 'primary', show: (r) => r.status === 0, run: (r) => AuthorizationApi.submitAuthorization(r.id), confirm: '提交该授权申请？' },
      { label: '审批', type: 'success', show: (r) => r.status === 1, run: (r) => AuthorizationApi.approveAuthorization({ id: r.id, approveAction: 'approve' }), needOpinion: true },
      { label: '召回', type: 'warning', show: (r) => r.status === 2, run: (r) => AuthorizationApi.recallAuthorization(r.id), confirm: '召回该授权？' }
    ]
  },
  // --- 方案计划：流程步骤 ---
  'solution': {
    key: 'solution', label: '实施方案', icon: 'ep:files', path: '/pms/engineering/preparation/eng-solution',
    load: (pid, pageNo, pageSize) => SolutionApi.getSolutionPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => SolutionApi.createSolution(data),
    update: (data) => SolutionApi.updateSolution(data),
    delete: (id) => SolutionApi.deleteSolution(id),
    get: (id) => SolutionApi.getSolution(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'name', label: '方案名称', minWidth: 180 },
      { prop: 'solutionType', label: '类型', width: 100 },
      { prop: 'reviewLevel', label: '评审级别', width: 100 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '草稿', tone: 'gray' }, 1: { label: '评审中', tone: 'yellow' }, 2: { label: '已通过', tone: 'blue' }, 3: { label: '已驳回', tone: 'red' }, 4: { label: '已撤回', tone: 'gray' }, 5: { label: '已终止', tone: 'gray' } },
    actions: [
      { label: '提交评审', type: 'primary', show: (r) => r.status === 0, run: (r) => SolutionApi.submitSolution(r.id), confirm: '提交该方案评审？' },
      { label: '通过', type: 'success', show: (r) => r.status === 1, run: (r) => SolutionApi.approveSolution({ id: r.id, approvalOpinion: '同意' }), needOpinion: true },
      { label: '驳回', type: 'danger', show: (r) => r.status === 1, run: (r) => SolutionApi.rejectSolution({ id: r.id, approvalOpinion: '不同意' }), needOpinion: true },
      { label: '撤回', type: 'warning', show: (r) => r.status === 1, run: (r) => SolutionApi.withdrawSolution(r.id), confirm: '撤回该方案？' }
    ]
  },
  'resource': {
    key: 'resource', label: '资源就绪', icon: 'ep:box', path: '/pms/engineering/execution/eng-resource',
    load: (pid, pageNo, pageSize) => ResourceApi.getResourceReadyPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => ResourceApi.createResourceReady(data),
    update: (data) => ResourceApi.updateResourceReady(data),
    delete: (id) => ResourceApi.deleteResourceReady(id),
    get: (id) => ResourceApi.getResourceReady(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'name', label: '资源名称', minWidth: 180 },
      { prop: 'resourceType', label: '类型', width: 100 },
      { prop: 'quantity', label: '数量', width: 80 },
      { prop: 'readyStatus', label: '就绪状态', width: 100, type: 'status' }
    ],
    statusField: 'readyStatus',
    statusMap: { 0: { label: '未就绪', tone: 'gray' }, 1: { label: '已就绪', tone: 'green' }, 2: { label: '异常', tone: 'red' } },
    actions: [
      { label: '标记就绪', type: 'success', show: (r) => r.readyStatus === 0, run: (r) => ResourceApi.markReady(r.id), confirm: '标记该资源为已就绪？' },
      { label: '标记异常', type: 'danger', show: (r) => r.readyStatus === 0 || r.readyStatus === 1, run: (r) => ResourceApi.markAbnormal(r.id), confirm: '标记该资源为异常？' },
      { label: '重置', type: 'info', show: (r) => r.readyStatus === 2, run: (r) => ResourceApi.resetToNotReady(r.id), confirm: '重置为未就绪？' }
    ]
  },
  'schedule-backward': {
    key: 'schedule-backward', label: '历史工期倒排', icon: 'ep:timer', path: '/pms/project-management/schedule/schedule-backward',
    load: (pid, pageNo, pageSize) => ScheduleBackwardApi.getScheduleBackwardPage({ projectId: pid, pageNo, pageSize }),
    get: (id) => ScheduleBackwardApi.getScheduleBackward(id),
    columns: [
      { prop: 'targetDate', label: '目标日期', width: 130, type: 'time' },
      { prop: 'projectType', label: '项目类型', width: 100 },
      { prop: 'conflictSummary', label: '冲突摘要', minWidth: 200 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ]
  },
  // --- 方案计划：并行事项 ---
  'plan-change': {
    key: 'plan-change', label: '历史计划变更', icon: 'ep:edit', path: '/pms/project-management/schedule/plan-change',
    load: (pid, pageNo, pageSize) => PlanChangeApi.getPlanChangePage({ projectId: pid, pageNo, pageSize }),
    get: (id) => PlanChangeApi.getPlanChange(id),
    columns: [
      { prop: 'changeNo', label: '变更编号', width: 130 },
      { prop: 'title', label: '变更标题', minWidth: 180 },
      { prop: 'changeType', label: '类型', width: 100 },
      { prop: 'applyTime', label: '申请时间', width: 130, type: 'time' },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '草稿', tone: 'gray' }, 1: { label: '待审批', tone: 'yellow' }, 2: { label: '已通过', tone: 'blue' }, 3: { label: '已驳回', tone: 'red' }, 4: { label: '已撤回', tone: 'gray' }, 5: { label: '已应用', tone: 'green' } }
  },
  // --- 实施部署：流程步骤 ---
  'arrival': {
    key: 'arrival', label: '到货签收', icon: 'ep:takeaway-box', path: '/pms/engineering/execution/eng-arrival',
    load: (pid, pageNo, pageSize) => ArrivalApi.getArrivalPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => ArrivalApi.createArrival(data),
    update: (data) => ArrivalApi.updateArrival(data),
    delete: (id) => ArrivalApi.deleteArrival(id),
    get: (id) => ArrivalApi.getArrival(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'arrivalTime', label: '到货时间', width: 130, type: 'time' },
      { prop: 'quantity', label: '数量', width: 80 },
      { prop: 'inspectionResult', label: '检验结果', width: 100 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '待签收', tone: 'yellow' }, 1: { label: '已签收', tone: 'green' }, 2: { label: '异常', tone: 'red' } },
    actions: [
      { label: '签收', type: 'success', show: (r) => r.status === 0, run: (r) => ArrivalApi.signArrival(r.id), confirm: '签收该到货记录？' },
      { label: '标记异常', type: 'danger', show: (r) => r.status === 0, run: (r) => ArrivalApi.markAbnormalArrival(r.id), confirm: '标记为异常？' }
    ]
  },
  'installation': {
    key: 'installation', label: '硬件安装', icon: 'ep:setting', path: '/pms/engineering/execution/eng-installation',
    load: (pid, pageNo, pageSize) => InstallationApi.getInstallationPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => InstallationApi.createInstallation(data),
    update: (data) => InstallationApi.updateInstallation(data),
    delete: (id) => InstallationApi.deleteInstallation(id),
    get: (id) => InstallationApi.getInstallation(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'installLocation', label: '安装位置', minWidth: 140 },
      { prop: 'installTime', label: '安装时间', width: 130, type: 'time' },
      { prop: 'result', label: '结果', width: 100 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '待开始', tone: 'gray' }, 1: { label: '进行中', tone: 'blue' }, 2: { label: '已完成', tone: 'green' }, 3: { label: '异常', tone: 'red' } },
    actions: [
      { label: '开始', type: 'primary', show: (r) => r.status === 0, run: (r) => InstallationApi.startInstallation(r.id), confirm: '开始该安装任务？' },
      { label: '完成', type: 'success', show: (r) => r.status === 1, run: (r) => InstallationApi.completeInstallation(r.id), confirm: '完成该安装任务？' },
      { label: '异常', type: 'danger', show: (r) => r.status === 0 || r.status === 1, run: (r) => InstallationApi.markAbnormalInstallation(r.id), confirm: '标记为异常？' }
    ]
  },
  'configuration': {
    key: 'configuration', label: '配置调试', icon: 'ep:tools', path: '/pms/engineering/execution/eng-configuration',
    load: (pid, pageNo, pageSize) => ConfigurationApi.getConfigurationPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => ConfigurationApi.createConfiguration(data),
    update: (data) => ConfigurationApi.updateConfiguration(data),
    delete: (id) => ConfigurationApi.deleteConfiguration(id),
    get: (id) => ConfigurationApi.getConfiguration(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'debugResult', label: '调试结果', minWidth: 140 },
      { prop: 'debugTime', label: '调试时间', width: 130, type: 'time' },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '待开始', tone: 'gray' }, 1: { label: '进行中', tone: 'blue' }, 2: { label: '已完成', tone: 'green' }, 3: { label: '异常', tone: 'red' } },
    actions: [
      { label: '开始', type: 'primary', show: (r) => r.status === 0, run: (r) => ConfigurationApi.startConfiguration(r.id), confirm: '开始该配置调试？' },
      { label: '完成', type: 'success', show: (r) => r.status === 1, run: (r) => ConfigurationApi.completeConfiguration(r.id), confirm: '完成该配置调试？' },
      { label: '异常', type: 'danger', show: (r) => r.status === 0 || r.status === 1, run: (r) => ConfigurationApi.markAbnormalConfiguration(r.id), confirm: '标记为异常？' }
    ]
  },
  'joint-test': {
    key: 'joint-test', label: '业务联调', icon: 'ep:connection', path: '/pms/engineering/execution/eng-joint-test',
    load: (pid, pageNo, pageSize) => JointTestApi.getJointTestPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => JointTestApi.createJointTest(data),
    update: (data) => JointTestApi.updateJointTest(data),
    delete: (id) => JointTestApi.deleteJointTest(id),
    get: (id) => JointTestApi.getJointTest(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'testCase', label: '测试用例', minWidth: 180 },
      { prop: 'testTime', label: '测试时间', width: 130, type: 'time' },
      { prop: 'result', label: '结果', width: 100 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '待开始', tone: 'gray' }, 1: { label: '进行中', tone: 'blue' }, 2: { label: '已通过', tone: 'green' }, 3: { label: '未通过', tone: 'red' } },
    actions: [
      { label: '开始', type: 'primary', show: (r) => r.status === 0, run: (r) => JointTestApi.startJointTest(r.id), confirm: '开始该联调测试？' },
      { label: '通过', type: 'success', show: (r) => r.status === 1, run: (r) => JointTestApi.passJointTest(r.id), confirm: '标记该联调为通过？' },
      { label: '未通过', type: 'danger', show: (r) => r.status === 1, run: (r) => JointTestApi.failJointTest(r.id, ''), confirm: '标记该联调为未通过？' }
    ]
  },
  // --- 实施部署：并行事项 ---
  'issue': {
    key: 'issue', label: '实施问题', icon: 'ep:warning-filled', path: '/pms/engineering/execution/eng-issue',
    load: (pid, pageNo, pageSize) => IssueApi.getIssuePage({ projectId: pid, pageNo, pageSize }),
    create: (data) => IssueApi.createIssue(data),
    update: (data) => IssueApi.updateIssue(data),
    delete: (id) => IssueApi.deleteIssue(id),
    get: (id) => IssueApi.getIssue(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'name', label: '问题名称', minWidth: 180 },
      { prop: 'severity', label: '严重度', width: 80 },
      { prop: 'deadline', label: '截止时间', width: 130, type: 'time' },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '待整改', tone: 'yellow' }, 1: { label: '整改中', tone: 'blue' }, 2: { label: '待验证', tone: 'purple' }, 3: { label: '已关闭', tone: 'green' }, 4: { label: '已驳回', tone: 'red' }, 5: { label: '已挂起', tone: 'gray' } },
    actions: [
      { label: '开始整改', type: 'primary', show: (r) => r.status === 0, run: (r) => IssueApi.startRectifyIssue(r.id), confirm: '开始整改该问题？' },
      { label: '提交验证', type: 'info', show: (r) => r.status === 1, run: (r) => IssueApi.submitForVerifyIssue(r.id), confirm: '提交该问题验证？' },
      { label: '关闭', type: 'success', show: (r) => r.status === 2, run: (r) => IssueApi.closeIssue({ id: r.id, action: 'close' }), needOpinion: true }
    ]
  },
  'deliverable': {
    key: 'deliverable', label: '交付件', icon: 'ep:folder', path: '/pms/engineering/execution/eng-deliverable',
    load: (pid, pageNo, pageSize) => DeliverableApi.getDeliverablePage({ projectId: pid, pageNo, pageSize }),
    create: (data) => DeliverableApi.createDeliverable(data),
    update: (data) => DeliverableApi.updateDeliverable(data),
    delete: (id) => DeliverableApi.deleteDeliverable(id),
    get: (id) => DeliverableApi.getDeliverable(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'name', label: '交付件名称', minWidth: 180 },
      { prop: 'deliverableType', label: '类型', width: 100 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '待归档', tone: 'yellow' }, 1: { label: '已归档', tone: 'green' }, 2: { label: '已作废', tone: 'red' } },
    actions: [
      { label: '归档', type: 'success', show: (r) => r.status === 0, run: (r) => DeliverableApi.archiveDeliverable(r.id), confirm: '归档该交付件？' },
      { label: '作废', type: 'danger', show: (r) => r.status === 0 || r.status === 1, run: (r) => DeliverableApi.voidDeliverable(r.id), confirm: '作废该交付件？' }
    ]
  },
  // --- 验收收尾 ---
  'completion-certificate': {
    key: 'completion-certificate', label: '完工证明', icon: 'ep:medal', path: '/pms/acceptance/completion-certificate',
    load: (pid, pageNo, pageSize) => CompletionCertApi.getCompletionCertificatePage({ projectId: pid, pageNo, pageSize }),
    create: (data) => CompletionCertApi.createCompletionCertificate(data),
    update: (data) => CompletionCertApi.updateCompletionCertificate(data),
    delete: (id) => CompletionCertApi.deleteCompletionCertificate(id),
    get: (id) => CompletionCertApi.getCompletionCertificate(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'name', label: '证明名称', minWidth: 180 },
      { prop: 'certificateNo', label: '证书编号', width: 140 },
      { prop: 'signedDate', label: '签署日期', width: 120, type: 'time' },
      { prop: 'satisfactionScore', label: '满意度', width: 80 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '草稿', tone: 'gray' }, 1: { label: '待客户确认', tone: 'yellow' }, 2: { label: '客户已确认', tone: 'blue' }, 3: { label: '已归档', tone: 'green' }, 4: { label: '已驳回', tone: 'red' } },
    actions: [
      { label: '提交', type: 'primary', show: (r) => r.status === 0, run: (r) => CompletionCertApi.submitCompletionCertificate(r.id), confirm: '提交该完工证明？' },
      { label: '客户确认', type: 'success', show: (r) => r.status === 1, run: (r) => CompletionCertApi.customerConfirmCompletionCertificate(r.id), confirm: '客户确认该完工证明？' },
      { label: '归档', type: 'info', show: (r) => r.status === 2, run: (r) => CompletionCertApi.archiveCompletionCertificate(r.id), confirm: '归档该完工证明？' }
    ]
  },
  'acceptance': {
    key: 'acceptance', label: '验收管理', icon: 'ep:circle-check', path: '/pms/acceptance/acceptance',
    load: (pid, pageNo, pageSize) => AcceptanceApi.getAcceptancePage({ projectId: pid, pageNo, pageSize }),
    create: (data) => AcceptanceApi.createAcceptance(data),
    update: (data) => AcceptanceApi.updateAcceptance(data),
    delete: (id) => AcceptanceApi.deleteAcceptance(id),
    get: (id) => AcceptanceApi.getAcceptance(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'name', label: '验收名称', minWidth: 180 },
      { prop: 'acceptanceType', label: '类型', width: 100 },
      { prop: 'signedDate', label: '签署日期', width: 120, type: 'time' },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '草稿', tone: 'gray' }, 1: { label: '待提交', tone: 'yellow' }, 2: { label: '审批中', tone: 'blue' }, 3: { label: '已通过', tone: 'green' }, 4: { label: '已驳回', tone: 'red' }, 5: { label: '已归档', tone: 'gray' } },
    actions: [
      { label: '提交', type: 'primary', show: (r) => r.status === 0, run: (r) => AcceptanceApi.submitAcceptance(r.id), confirm: '提交该验收？' },
      { label: '通过', type: 'success', show: (r) => r.status === 2, run: (r) => AcceptanceApi.passAcceptance(r.id), confirm: '通过该验收？' },
      { label: '驳回', type: 'danger', show: (r) => r.status === 2, run: (r) => AcceptanceApi.rejectAcceptance(r.id), confirm: '驳回该验收？' }
    ]
  },
  'deliverable-checklist': {
    key: 'deliverable-checklist', label: '交付件检查', icon: 'ep:folder-checked', path: '/pms/acceptance/deliverable-checklist',
    load: (pid, pageNo, pageSize) => DeliverableCheckApi.getDeliverableChecklistPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => DeliverableCheckApi.createDeliverableChecklist(data),
    update: (data) => DeliverableCheckApi.updateDeliverableChecklist(data),
    delete: (id) => DeliverableCheckApi.deleteDeliverableChecklist(id),
    get: (id) => DeliverableCheckApi.getDeliverableChecklist(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'name', label: '交付件名称', minWidth: 180 },
      { prop: 'deliverableType', label: '类型', width: 100 },
      { prop: 'signedFlag', label: '已签署', width: 80 },
      { prop: 'validFlag', label: '有效', width: 80 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '草稿', tone: 'gray' }, 1: { label: '已提交', tone: 'yellow' }, 2: { label: '已通过', tone: 'green' }, 3: { label: '已驳回', tone: 'red' } },
    actions: [
      { label: '提交', type: 'primary', show: (r) => r.status === 0, run: (r) => DeliverableCheckApi.submitDeliverableChecklist(r.id), confirm: '提交该检查项？' },
      { label: '通过', type: 'success', show: (r) => r.status === 1, run: (r) => DeliverableCheckApi.passDeliverableChecklist(r.id), confirm: '通过该检查项？' },
      { label: '驳回', type: 'danger', show: (r) => r.status === 1, run: (r) => DeliverableCheckApi.rejectDeliverableChecklist(r.id), confirm: '驳回该检查项？' }
    ]
  },
  'project-closure': {
    key: 'project-closure', label: '项目闭环', icon: 'ep:lock', path: '/pms/acceptance/project-closure',
    load: (pid, pageNo, pageSize) => ProjectClosureApi.getProjectClosurePage({ projectId: pid, pageNo, pageSize }),
    create: (data) => ProjectClosureApi.createProjectClosure(data),
    update: (data) => ProjectClosureApi.updateProjectClosure(data),
    delete: (id) => ProjectClosureApi.deleteProjectClosure(id),
    get: (id) => ProjectClosureApi.getProjectClosure(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'name', label: '闭环名称', minWidth: 180 },
      { prop: 'applicationDate', label: '申请日期', width: 120, type: 'time' },
      { prop: 'carryoverIssues', label: '遗留问题', minWidth: 160 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '草稿', tone: 'gray' }, 1: { label: '待审批', tone: 'yellow' }, 2: { label: '审批中', tone: 'blue' }, 3: { label: '已通过', tone: 'green' }, 4: { label: '已驳回', tone: 'red' }, 5: { label: '已归档', tone: 'gray' } },
    actions: [
      { label: '提交', type: 'primary', show: (r) => r.status === 0, run: (r) => ProjectClosureApi.submitProjectClosure(r.id), confirm: '提交该闭环申请？' },
      { label: '通过', type: 'success', show: (r) => r.status === 2, run: (r) => ProjectClosureApi.passProjectClosure(r.id), confirm: '通过该闭环申请？' },
      { label: '归档', type: 'info', show: (r) => r.status === 3, run: (r) => ProjectClosureApi.archiveProjectClosure(r.id), confirm: '归档该闭环记录？' }
    ]
  },
  'archive-document': {
    key: 'archive-document', label: '归档文档', icon: 'ep:archive', path: '/pms/acceptance/archive-document',
    load: (pid, pageNo, pageSize) => ArchiveDocApi.getArchiveDocumentPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => ArchiveDocApi.createArchiveDocument(data),
    update: (data) => ArchiveDocApi.updateArchiveDocument(data),
    delete: (id) => ArchiveDocApi.deleteArchiveDocument(id),
    get: (id) => ArchiveDocApi.getArchiveDocument(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'name', label: '文档名称', minWidth: 180 },
      { prop: 'documentType', label: '类型', width: 100 },
      { prop: 'version', label: '版本', width: 80 },
      { prop: 'uploadedDate', label: '上传日期', width: 120, type: 'time' },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '草稿', tone: 'gray' }, 1: { label: '待归档', tone: 'yellow' }, 2: { label: '已归档', tone: 'green' } },
    actions: [
      { label: '提交', type: 'primary', show: (r) => r.status === 0, run: (r) => ArchiveDocApi.submitArchiveDocument(r.id), confirm: '提交该归档文档？' },
      { label: '归档', type: 'success', show: (r) => r.status === 1, run: (r) => ArchiveDocApi.archiveArchiveDocument(r.id), confirm: '归档该文档？' }
    ]
  }
}

// ============ 组件状态 ============
const loading = ref(false)
const currentProjectId = ref<number | undefined>(undefined)

// 立项阶段数据
const project = reactive<any>({})
const panoramic = reactive<ProjectPanoramicVO>({})
const phases = ref<ProjectPhaseVO[]>([])
const taskTree = ref<ProjectTaskTreeVO[]>([])
const risks = ref<ProjectRiskVO[]>([])
const teamMembers = ref<ProjectTeamMemberVO[]>([])

// 当前 Tab
const activeTab = ref('project')

// 通用模块面板状态
const moduleData = ref<any[]>([])
const moduleLoading = ref(false)
const moduleTotal = ref(0)
const modulePageNo = ref(1)
const modulePageSize = 10

// 通用详情抽屉状态
const detailVisible = ref(false)
const detailRow = ref<any>(null)

// 通用新增/编辑弹窗状态
const formRef = ref()
const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formLoading = ref(false)
const formData = reactive<any>({})
const formRules = ref<Record<string, any[]>>({})

// 阶段详情状态
const activePhaseId = ref<number | undefined>(undefined)

// ============ 模板化新增状态（需求分析/实施方案） ============
// 模板选择弹窗
const templateSelectVisible = ref(false)
const templateSelectLoading = ref(false)
const templateSelectHint = ref('')
const applicableTemplates = ref<any[]>([])
const selectedTemplateId = ref<number | undefined>(undefined)
const templateConfirmLoading = ref(false)
// 模板章节表单弹窗
const templateFormVisible = ref(false)
const templateFormLoading = ref(false)
const templateFormRef = ref()
const templateFormData = reactive<any>({})
const templateFormRules = ref<Record<string, any[]>>({})
// 当前模板上下文
const templateDocCategory = ref<'REQUIREMENT' | 'SOLUTION'>('REQUIREMENT')
const currentTemplateId = ref<number | undefined>(undefined)
const currentTemplateVersionId = ref<number | undefined>(undefined)
const currentTemplateSnapshot = ref<string>('')
const templateSections = ref<any[]>([])
const templateSectionOverrides = ref<Record<string, any>>({})
const templateExcludedSections = ref<string[]>([])

// 割接面板状态
const cutTasks = ref<any[]>([])
const cutLoading = ref(false)
const selectedCutTask = ref<any>(null)
const activeCutSubTab = ref('cut-risk')
const cutRisks = ref<any[]>([])
const cutPlans = ref<any[]>([])
const cutSubLoading = ref(false)

// 巡检面板状态
const srvTasks = ref<any[]>([])
const srvLoading = ref(false)
const selectedSrvTask = ref<any>(null)
const activeSrvSubTab = ref('srv-report')
const srvReports = ref<any[]>([])
const srvIssues = ref<any[]>([])
const srvSubLoading = ref(false)

// 割接任务操作 API 引用
const { submitForReview, approveCutTask, rejectCutTask } = CutTaskApi

// ============ 计算属性 ============
const projectStatusLabel = computed(() => {
  const map: Record<number, string> = { 0: '立项待指派', 1: '进行中', 2: '已完成', 3: '已关闭' }
  return map[project.status as number] ?? (project.status !== undefined ? String(project.status) : '-')
})
const projectStatusTone = computed(() => {
  const map: Record<number, string> = { 0: 'gray', 1: 'green', 2: 'blue', 3: 'gray' }
  return map[project.status as number] ?? 'gray'
})
const categoryLabel = computed(() => {
  const map: Record<string, string> = { MAIN: '主项目', SUB: '子项目' }
  return map[project.category as string] ?? (project.category || '-')
})

// 通用模块配置相关
const genericTabKeys = Object.keys(moduleConfigs)
const isGenericTab = computed(() => genericTabKeys.includes(activeTab.value))
const currentModule = computed(() => moduleConfigs[activeTab.value])
const moduleStatusField = computed(() => currentModule.value?.statusField || 'status')

// 通用模块操作列：是否有操作按钮（状态流转 + 编辑 + 删除）
const hasActions = computed(() => {
  const cfg = currentModule.value
  if (!cfg) return false
  return !!(cfg.actions?.length || cfg.update || cfg.delete)
})
const actionColumnWidth = computed(() => {
  const cfg = currentModule.value
  if (!cfg) return 80
  let count = cfg.actions?.length || 0
  if (cfg.update) count += 1
  if (cfg.delete) count += 1
  return Math.max(count * 60, 80)
})

// 阶段详情：当前选中的阶段对象
const activePhase = computed(() => phases.value.find((p) => p.id === activePhaseId.value))

// 表单字段：基于 columns 自动生成，跳过系统字段
const SYSTEM_FORM_PROPS = ['id', 'createTime', 'updateTime', 'version', 'status']
const formColumns = computed(() => {
  const cfg = currentModule.value
  if (!cfg) return []
  return cfg.columns.filter((col) => !SYSTEM_FORM_PROPS.includes(col.prop))
})

// ============ 模板化相关计算属性 ============
// 仅需求分析与实施方案两个模块启用模板化
const TEMPLATED_MODULE_KEYS = ['requirement', 'solution']
const isTemplatedModule = computed(() => TEMPLATED_MODULE_KEYS.includes(activeTab.value))
const templateDocCategoryLabel = computed(() =>
  templateDocCategory.value === 'REQUIREMENT' ? '需求分析' : '实施方案'
)

// ============ 模板适用性解析与判断 ============
const parseApplicability = (tpl: any): any => {
  if (!tpl?.applicability) return {}
  if (typeof tpl.applicability === 'string') {
    try { return JSON.parse(tpl.applicability) } catch { return {} }
  }
  return tpl.applicability
}
const isDefaultTemplate = (tpl: any) => !!parseApplicability(tpl).isDefault

// 章节是否必填：综合 sectionOverrides 与字段默认 required
const isSectionRequired = (section: any): boolean => {
  const override = templateSectionOverrides.value[section.code]
  if (override?.required !== undefined) return !!override.required
  // 默认章节中带 background/code/name 等关键字段视为必填
  return false
}
const getFieldRules = (section: any, field: any): any[] => {
  const rules: any[] = []
  const override = templateSectionOverrides.value[section.code]
  const isRequired = override?.required === true || field.required === true
  if (isRequired) {
    rules.push({ required: true, message: `请输入${field.title}`, trigger: 'blur' })
  }
  return rules
}

// ============ 状态映射（立项阶段专用） ============
const phaseStatusLabel = (status?: number) => {
  const map: Record<number, string> = { 0: '未开始', 1: '进行中', 2: '已完成', 3: '已跳过' }
  return map[status ?? -1] ?? '-'
}
const phaseStatusTone = (status?: number) => {
  const map: Record<number, string> = { 0: 'gray', 1: 'blue', 2: 'green', 3: 'yellow' }
  return map[status ?? -1] ?? 'gray'
}
const taskStatusLabel = (status?: number) => {
  const map: Record<number, string> = { 0: '草稿', 1: '待处理', 2: '进行中', 3: '受阻', 4: '待验证', 5: '已完成', 6: '已取消' }
  return map[status ?? -1] ?? '-'
}
const taskStatusTone = (status?: number) => {
  const map: Record<number, string> = { 0: 'gray', 1: 'yellow', 2: 'blue', 3: 'red', 4: 'purple', 5: 'green', 6: 'gray' }
  return map[status ?? -1] ?? 'gray'
}
const riskStatusLabel = (status?: number) => {
  const map: Record<number, string> = { 0: '已识别', 1: '处理中', 2: '已关闭', 3: '已发生' }
  return map[status ?? -1] ?? '-'
}
const riskStatusTone = (status?: number) => {
  const map: Record<number, string> = { 0: 'yellow', 1: 'blue', 2: 'green', 3: 'red' }
  return map[status ?? -1] ?? 'gray'
}
const riskLevelTone = (level?: string) => {
  const l = (level || '').toUpperCase()
  if (l === 'HIGH') return 'red'
  if (l === 'MEDIUM') return 'yellow'
  if (l === 'LOW') return 'green'
  return 'gray'
}
const riskRowClass = ({ row }: { row: ProjectRiskVO }) => {
  if ((row.riskLevel || '').toUpperCase() === 'HIGH' && row.status !== 2) return 'risk-row--high'
  return ''
}

// 割接任务状态映射
const cutTaskStatusLabel = (status?: number) => {
  const map: Record<number, string> = { 0: '草稿', 1: '准备中', 2: '待评审', 3: '闭环中', 4: '历史状态', 5: '历史状态', 6: '历史完成', 7: '历史回退', 8: '历史终止' }
  return map[status ?? -1] ?? '-'
}
const cutTaskStatusTone = (status?: number) => {
  const map: Record<number, string> = { 0: 'gray', 1: 'yellow', 2: 'yellow', 3: 'blue', 4: 'blue', 5: 'blue', 6: 'green', 7: 'red', 8: 'red' }
  return map[status ?? -1] ?? 'gray'
}

// 通用模块状态映射
const getModuleStatusTone = (row: any, field: string) => {
  const cfg = currentModule.value
  const val = row[field]
  if (cfg?.statusMap) return cfg.statusMap[val]?.tone ?? 'gray'
  return commonStatusTone(val)
}
const getModuleStatusLabel = (row: any, field: string) => {
  const cfg = currentModule.value
  const val = row[field]
  if (cfg?.statusMap) return cfg.statusMap[val]?.label ?? '-'
  return commonStatusLabel(val)
}

// ============ 阶段与任务衔接 ============
const isPhaseTab = (key: string) => key.startsWith('phase-')

// 扁平化任务树
const flattenTasks = (tasks: ProjectTaskTreeVO[]): ProjectTaskTreeVO[] => {
  const result: ProjectTaskTreeVO[] = []
  const walk = (list: ProjectTaskTreeVO[]) => {
    for (const t of list) {
      result.push(t)
      if (t.children?.length) walk(t.children)
    }
  }
  walk(tasks)
  return result
}

// 计算阶段任务平均进度
const phaseProgress = (phaseId: number): number => {
  const tasks = phaseTasks(phaseId)
  if (!tasks.length) return 0
  const total = tasks.reduce((sum, t) => sum + (t.progress ?? 0), 0)
  return Math.round(total / tasks.length)
}

// 阶段任务匹配：依次尝试 code 前缀、sort 对应顶级任务、兜底全部
const phaseTasks = (phaseId: number): ProjectTaskTreeVO[] => {
  const phase = phases.value.find((p) => p.id === phaseId)
  if (!phase) return []
  const topLevel = taskTree.value || []
  if (!topLevel.length) return []
  // 策略1：按 phase.code 匹配任务 code 或 path 前缀
  const phaseCode = (phase.code || '').toUpperCase()
  if (phaseCode) {
    const byCode: ProjectTaskTreeVO[] = []
    const walk = (t: ProjectTaskTreeVO) => {
      const tc = (t.code || '').toUpperCase()
      const tp = (t.path || '').toUpperCase()
      if (tc === phaseCode || tc.startsWith(phaseCode + '-') || tp.startsWith('/' + phaseCode) || tp.startsWith(phaseCode + '/')) {
        byCode.push(t)
      }
      if (t.children?.length) t.children.forEach(walk)
    }
    topLevel.forEach(walk)
    if (byCode.length) return byCode
  }
  // 策略2：按 phase.sort 对应顶级任务及其子树
  const phaseSort = phase.sort ?? 0
  if (phaseSort > 0 && phaseSort <= topLevel.length) {
    const matched = topLevel[phaseSort - 1]
    const subset: ProjectTaskTreeVO[] = []
    const walk = (t: ProjectTaskTreeVO) => {
      subset.push(t)
      if (t.children?.length) t.children.forEach(walk)
    }
    walk(matched)
    return subset
  }
  // 兜底：返回全部扁平任务
  return flattenTasks(topLevel)
}

// ============ Tab 切换 ============
const switchTab = (key: string) => {
  activeTab.value = key
  // 阶段 Tab
  if (isPhaseTab(key)) {
    const id = Number(key.replace('phase-', ''))
    activePhaseId.value = id
    return
  }
  // 通用模块：懒加载
  if (genericTabKeys.includes(key)) {
    modulePageNo.value = 1
    loadModuleData()
  }
  // 割接面板：首次进入加载任务列表
  if (key === 'cutover' && cutTasks.value.length === 0) {
    loadCutTasks()
  }
  // 巡检面板：首次进入加载任务列表
  if (key === 'inspection' && srvTasks.value.length === 0) {
    loadSrvTasks()
  }
}

// 切换到阶段详情面板
const switchToPhase = (phase: ProjectPhaseVO) => {
  activeTab.value = 'phase-' + phase.id
  activePhaseId.value = phase.id
}

const onProjectChange = (id: any) => {
  if (id) {
    router.replace({ path: '/pms/project-detail', query: { projectId: id } })
    loadAll(id)
  }
}

const goBackToList = () => router.push('/pms/project-management/project')
const goPage = (path?: string) => { if (path) router.push(path) }

// ============ 立项阶段数据加载 ============
const loadProject = async (id: number) => {
  const data = await ProjectApi.getProject(id)
  Object.keys(project).forEach((k) => delete project[k])
  Object.assign(project, data || {})
}
const loadPanoramic = async (id: number) => {
  try {
    const data = await getProjectPanoramic(id)
    Object.keys(panoramic).forEach((k) => delete (panoramic as any)[k])
    Object.assign(panoramic, data || {})
  } catch (e) { /* 全景失败不阻断 */ }
}
const loadPhases = async () => {
  if (!currentProjectId.value) return
  try { phases.value = await PhaseApi.getProjectPhaseListByProjectId(currentProjectId.value) } catch { phases.value = [] }
}
const loadTaskTree = async () => {
  if (!currentProjectId.value) return
  try { taskTree.value = await TaskApi.getProjectTaskTree(currentProjectId.value) || [] } catch { taskTree.value = [] }
}
const loadRisks = async () => {
  if (!currentProjectId.value) return
  try { risks.value = await RiskApi.getProjectRiskListByProjectId(currentProjectId.value) } catch { risks.value = [] }
}
const loadTeam = async () => {
  if (!currentProjectId.value) return
  try { teamMembers.value = await TeamApi.getProjectTeamListByProjectId(currentProjectId.value) } catch { teamMembers.value = [] }
}

// ============ 通用模块数据加载 ============
const loadModuleData = async () => {
  const cfg = currentModule.value
  if (!cfg || !currentProjectId.value) return
  moduleLoading.value = true
  try {
    const res = await cfg.load(currentProjectId.value, modulePageNo.value, modulePageSize)
    moduleData.value = res.list || []
    moduleTotal.value = res.total || 0
  } catch (e) {
    moduleData.value = []
    moduleTotal.value = 0
  } finally {
    moduleLoading.value = false
  }
}

// ============ 通用模块操作 ============
const getVisibleActions = (row: any) => {
  return (currentModule.value?.actions || []).filter((a) => a.show(row))
}
const runModuleAction = async (act: ModuleAction, row: any) => {
  try {
    let opinion: string | undefined
    if (act.needOpinion) {
      const { value } = await ElMessageBox.prompt('请输入审批意见', act.label, {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputType: 'textarea'
      })
      opinion = value
    } else if (act.confirm) {
      await ElMessageBox.confirm(act.confirm, '提示', { type: 'warning' })
    }
    await act.run(row, opinion)
    message.success('操作成功')
    await loadModuleData()
    // 若详情抽屉打开，同步刷新详情行
    if (detailVisible.value && detailRow.value) {
      const updated = moduleData.value.find((r) => r.id === detailRow.value?.id)
      if (updated) detailRow.value = updated
      else detailVisible.value = false
    }
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') message.error('操作失败')
  }
}

// ============ 通用详情抽屉 ============
const openDetail = (row: any) => {
  detailRow.value = row
  detailVisible.value = true
}

// ============ 通用新增/编辑弹窗 ============
const isLongTextField = (prop: string): boolean => {
  const longTextKeywords = ['remark', 'description', 'conclusion', 'mitigation', 'opinion', 'reason', 'summary', 'background']
  const lower = prop.toLowerCase()
  return longTextKeywords.some((k) => lower.includes(k))
}

const buildFormRules = () => {
  const rules: Record<string, any[]> = {}
  formColumns.value.forEach((col) => {
    if (col.prop === 'code' || col.prop === 'name' || col.prop === 'title') {
      rules[col.prop] = [{ required: true, message: `请输入${col.label}`, trigger: 'blur' }]
    }
  })
  formRules.value = rules
}

const resetFormData = () => {
  Object.keys(formData).forEach((k) => delete formData[k])
}

const openCreate = () => {
  resetFormData()
  formData.projectId = currentProjectId.value
  formMode.value = 'create'
  buildFormRules()
  formVisible.value = true
}

const openEdit = (row: any) => {
  resetFormData()
  Object.assign(formData, row)
  formMode.value = 'edit'
  buildFormRules()
  formVisible.value = true
}

const submitForm = async () => {
  const cfg = currentModule.value
  if (!cfg) return
  const valid = await formRef.value?.validate?.().catch(() => false)
  if (!valid) return
  formLoading.value = true
  try {
    if (formData.projectId == null) formData.projectId = currentProjectId.value
    if (formMode.value === 'create') {
      await cfg.create!(formData)
      message.success('新增成功')
    } else {
      await cfg.update!(formData)
      message.success('修改成功')
    }
    formVisible.value = false
    await loadModuleData()
  } catch (e) {
    // 表单提交失败不关闭弹窗
  } finally {
    formLoading.value = false
  }
}

// ============ 模板化新增：选择模板 → 加载章节 → 填写并提交 ============
const resetTemplateFormData = () => {
  Object.keys(templateFormData).forEach((k) => delete templateFormData[k])
}

// 1) 打开模板选择弹窗：按当前模块与项目属性拉取适用模板
const openCreateWithTemplate = async () => {
  if (!currentProjectId.value) {
    message.warning('请先选择项目')
    return
  }
  // 根据当前 Tab 确定文档类别
  templateDocCategory.value = activeTab.value === 'solution' ? 'SOLUTION' : 'REQUIREMENT'
  selectedTemplateId.value = undefined
  applicableTemplates.value = []
  templateSelectVisible.value = true
  templateSelectLoading.value = true
  templateSelectHint.value = ''
  try {
    // 按项目属性做三级降级匹配：项目类型 + 实施模式（network/product 维度留空，由后端默认模板兜底）
    const params: any = {
      docCategory: templateDocCategory.value,
      projectType: project.projectType,
      implementMode: project.implementationMode
    }
    const list = await DocTemplateApi.selectDocTemplates(params)
    applicableTemplates.value = Array.isArray(list) ? list : []
    if (applicableTemplates.value.length === 0) {
      templateSelectHint.value = '当前项目属性未匹配到已发布模板，请先在「工程文档模板」中维护对应模板并发布'
    } else if (applicableTemplates.value.length > 1) {
      templateSelectHint.value = `共匹配到 ${applicableTemplates.value.length} 个适用模板，已按优先级排序，默认模板排在最后`
    } else {
      templateSelectHint.value = '已匹配到 1 个适用模板'
    }
    // 默认选中第一个（优先级最高）
    if (applicableTemplates.value.length > 0) {
      selectedTemplateId.value = applicableTemplates.value[0].id
    }
  } catch (e) {
    templateSelectHint.value = '模板加载失败，请检查工程文档模板服务是否可用'
  } finally {
    templateSelectLoading.value = false
  }
}

// 2) 确认模板选择：拉取已发布版本的章节定义 + 构建快照，打开章节表单弹窗
const confirmTemplateSelection = async () => {
  if (!selectedTemplateId.value) return
  templateConfirmLoading.value = true
  try {
    const tpl = applicableTemplates.value.find((t) => t.id === selectedTemplateId.value)
    if (!tpl) {
      message.error('模板不存在')
      return
    }
    if (!tpl.currentVersionId) {
      message.error('该模板尚无已发布版本，无法使用')
      return
    }
    // 拉取已发布版本详情
    const version = await DocTemplateApi.getDocTemplateVersion(tpl.currentVersionId)
    if (!version || !version.sections) {
      message.error('模板版本章节为空，请先在模板管理中维护章节')
      return
    }
    // 构建模板快照（创建时锁定结构）
    let snapshot = ''
    try {
      snapshot = await DocTemplateApi.buildDocTemplateSnapshot(tpl.currentVersionId)
    } catch {
      // 快照构建失败不阻断流程，使用本地兜底
      snapshot = JSON.stringify({
        id: tpl.id,
        code: tpl.code,
        name: tpl.name,
        docCategory: tpl.docCategory,
        sections: version.sections,
        sectionOverrides: version.sectionOverrides,
        excludedSections: version.excludedSections
      })
    }
    // 解析章节、覆盖声明、排除列表
    let sections: any[] = []
    try {
      sections = typeof version.sections === 'string' ? JSON.parse(version.sections) : version.sections
    } catch { sections = [] }
    let overrides: Record<string, any> = {}
    if (version.sectionOverrides) {
      try {
        overrides = typeof version.sectionOverrides === 'string'
          ? JSON.parse(version.sectionOverrides)
          : (version.sectionOverrides || {})
      } catch { overrides = {} }
    }
    let excluded: string[] = []
    if (version.excludedSections) {
      try {
        excluded = typeof version.excludedSections === 'string'
          ? JSON.parse(version.excludedSections)
          : (version.excludedSections || [])
      } catch { excluded = [] }
    }
    // 应用排除规则
    const filteredSections = sections
      .filter((s) => !excluded.includes(s.code))
      .sort((a, b) => (a.order || 0) - (b.order || 0))
    // 写入上下文
    currentTemplateId.value = tpl.id
    currentTemplateVersionId.value = tpl.currentVersionId
    currentTemplateSnapshot.value = snapshot
    templateSections.value = filteredSections
    templateSectionOverrides.value = overrides
    templateExcludedSections.value = excluded
    // 初始化表单数据
    resetTemplateFormData()
    templateFormData.projectId = currentProjectId.value
    templateFormData.templateId = tpl.id
    templateFormData.templateVersionId = tpl.currentVersionId
    // 为章节字段预置空值，避免 v-model 未定义
    filteredSections.forEach((section) => {
      section.fields?.forEach((field: any) => {
        if (templateFormData[field.field] === undefined) {
          templateFormData[field.field] = ''
        }
      })
    })
    // 构建校验规则
    const rules: Record<string, any[]> = {
      code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
      name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
    }
    templateFormRules.value = rules
    // 切换到章节表单弹窗
    templateSelectVisible.value = false
    templateFormVisible.value = true
  } catch (e) {
    message.error('加载模板章节失败')
  } finally {
    templateConfirmLoading.value = false
  }
}

// 3) 提交模板章节表单：聚合 section_data 并调用对应模块 create API
const submitTemplateForm = async () => {
  const cfg = currentModule.value
  if (!cfg?.create) return
  const valid = await templateFormRef.value?.validate?.().catch(() => false)
  if (!valid) return
  templateFormLoading.value = true
  try {
    // 聚合章节字段值到 sectionData（按章节编码分组）
    const sectionData: Record<string, any> = {}
    templateSections.value.forEach((section) => {
      const sectionValues: Record<string, any> = {}
      section.fields?.forEach((field: any) => {
        const val = templateFormData[field.field]
        if (val !== undefined && val !== null && val !== '') {
          sectionValues[field.field] = val
        }
      })
      if (Object.keys(sectionValues).length > 0) {
        sectionData[section.code] = sectionValues
      }
    })
    // 同时把字段值平铺到顶层（兼容后端固定列双写）
    const flatData: Record<string, any> = { ...templateFormData }
    // 写入模板关联字段
    flatData.templateId = currentTemplateId.value
    flatData.templateVersionId = currentTemplateVersionId.value
    flatData.templateSnapshot = currentTemplateSnapshot.value
    flatData.sectionData = JSON.stringify(sectionData)
    // projectId 兜底
    if (flatData.projectId == null) flatData.projectId = currentProjectId.value
    await cfg.create(flatData)
    message.success('基于模板新增成功')
    templateFormVisible.value = false
    await loadModuleData()
  } catch (e) {
    // 失败不关闭弹窗
  } finally {
    templateFormLoading.value = false
  }
}

const deleteRow = async (row: any) => {
  const cfg = currentModule.value
  if (!cfg?.delete) return
  try {
    await ElMessageBox.confirm('确认删除该记录？', '提示', { type: 'warning' })
    await cfg.delete(row.id)
    message.success('删除成功')
    await loadModuleData()
  } catch (e) {
    if (e !== 'cancel') message.error('删除失败')
  }
}

// ============ 阶段操作 ============
const completePhase = async (phase: ProjectPhaseVO) => {
  try {
    await ElMessageBox.confirm(`确认完成阶段「${phase.name}」？`, '提示', { type: 'warning' })
    await PhaseApi.completeProjectPhase({ phaseId: phase.id!, version: phase.version })
    message.success('阶段已完成')
    await loadPhases()
  } catch (e) {
    if (e !== 'cancel') message.error('操作失败')
  }
}

// ============ 割接面板数据加载 ============
const loadCutTasks = async () => {
  if (!currentProjectId.value) return
  cutLoading.value = true
  try {
    const res = await CutTaskApi.getCutTaskPage({ projectId: currentProjectId.value, pageNo: 1, pageSize: 50 })
    cutTasks.value = res.list || []
  } catch { cutTasks.value = [] } finally { cutLoading.value = false }
}
const onCutTaskSelect = async (row: any) => {
  selectedCutTask.value = row
  if (!row) return
  await loadCutSubData(row.id)
}
const loadCutSubData = async (taskId: number) => {
  cutSubLoading.value = true
  try {
    const [risks, plans] = await Promise.all([
      CutRiskApi.getCutRiskPage({ taskId, pageNo: 1, pageSize: 50 }).catch(() => ({ list: [] })),
      CutPlanApi.getCutPlanPage({ taskId, pageNo: 1, pageSize: 50 }).catch(() => ({ list: [] }))
    ])
    cutRisks.value = risks.list || []
    cutPlans.value = plans.list || []
  } finally { cutSubLoading.value = false }
}
const doCutAction = async (api: (id: number) => Promise<any>, row: any) => {
  try {
    await ElMessageBox.confirm('确认执行该操作？', '提示', { type: 'warning' })
    await api(row.id)
    message.success('操作成功')
    await loadCutTasks()
    if (selectedCutTask.value?.id === row.id) await loadCutSubData(row.id)
  } catch (e) {
    if (e !== 'cancel') message.error('操作失败')
  }
}
const doCutActionWithOpinion = async (api: (data: any) => Promise<any>, row: any) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入审批意见', '审批', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputType: 'textarea'
    })
    await api({ id: row.id, approvalOpinion: value, version: row.version })
    message.success('操作成功')
    await loadCutTasks()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') message.error('操作失败')
  }
}

// ============ 巡检面板数据加载 ============
const loadSrvTasks = async () => {
  if (!currentProjectId.value) return
  srvLoading.value = true
  try {
    const res = await SrvTaskApi.getSrvTaskPage({ projectId: currentProjectId.value, pageNo: 1, pageSize: 50 })
    srvTasks.value = res.list || []
  } catch { srvTasks.value = [] } finally { srvLoading.value = false }
}
const onSrvTaskSelect = async (row: any) => {
  selectedSrvTask.value = row
  if (!row) return
  await loadSrvSubData(row.id)
}
const loadSrvSubData = async (taskId: number) => {
  srvSubLoading.value = true
  try {
    const [reports, issues] = await Promise.all([
      SrvReportApi.getSrvReportPage({ taskId, pageNo: 1, pageSize: 50 }).catch(() => ({ list: [] })),
      SrvIssueApi.getSrvIssuePage({ taskId, pageNo: 1, pageSize: 50 }).catch(() => ({ list: [] }))
    ])
    srvReports.value = reports.list || []
    srvIssues.value = issues.list || []
  } finally { srvSubLoading.value = false }
}

// ============ 总加载 ============
const loadAll = async (id: number) => {
  loading.value = true
  // 重置所有面板数据
  selectedCutTask.value = null
  selectedSrvTask.value = null
  cutTasks.value = []
  srvTasks.value = []
  moduleData.value = []
  activePhaseId.value = undefined
  try {
    await Promise.all([
      loadProject(id),
      loadPanoramic(id),
      loadPhases(),
      loadTaskTree(),
      loadRisks(),
      loadTeam()
    ])
  } finally {
    loading.value = false
  }
}

// ============ 生命周期 ============
onMounted(async () => {
  const pid = route.query.projectId
  if (pid) {
    currentProjectId.value = Number(pid)
    await loadAll(currentProjectId.value)
  } else {
    const data = await ProjectApi.getProjectPage({ pageNo: 1, pageSize: 30 })
    const list = data.list || []
    if (list.length > 0) {
      currentProjectId.value = list[0].id
      await loadAll(currentProjectId.value!)
    } else {
      message.warning('当前无可见项目')
    }
  }
})
</script>

<style lang="scss" scoped>
$primary: #1e3a5f;
$border: #e5e7eb;

/* ============ 顶部项目档案区 ============ */
.project-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.project-header-left { flex: 1; min-width: 0; }
.project-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.project-code {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: #6b7280;
  background: #f3f4f6;
  padding: 2px 8px;
  border-radius: 4px;
}
.project-name { margin: 0; font-size: 18px; font-weight: 600; color: #1f2937; }
.project-status {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  &--gray { background: rgba(148, 163, 184, 0.15); color: #64748b; }
  &--green { background: rgba(52, 211, 153, 0.15); color: #059669; }
  &--blue { background: rgba(96, 165, 250, 0.15); color: #2563eb; }
}
.project-meta-row {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  font-size: 13px;
  color: #6b7280;
}
.meta-item { display: inline-flex; align-items: center; gap: 4px; }
.project-header-right { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }

/* ============ 主体布局 ============ */
.detail-body {
  display: flex;
  align-items: flex-start;
  gap: 15px;
}

/* ============ 左侧导轨 ============ */
.rail-wrap {
  flex: 0 0 220px;
  :deep(.el-card__body) { padding: 8px 6px; }
}
.rail-stage {
  margin-bottom: 14px;
  &:last-child { margin-bottom: 0; }
}
.rail-stage-title {
  font-size: 11px;
  letter-spacing: 1px;
  color: #9ca3af;
  padding: 6px 10px 4px;
  font-weight: 600;
  text-transform: uppercase;
}
.rail-empty {
  font-size: 12px;
  color: #d1d5db;
  padding: 8px 10px;
  font-style: italic;
}
.rail-group {
  margin-bottom: 8px;
  &:last-child { margin-bottom: 0; }
}
.rail-group-title {
  font-size: 11px;
  color: #6b7280;
  padding: 4px 10px 2px;
  font-weight: 600;
  border-left: 2px solid #e5e7eb;
  margin-left: 4px;
}
.rail-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  color: #374151;
  font-size: 13px;
  text-align: left;
  transition: all 0.15s ease;
  &:hover { background: #f3f4f6; }
  &--active {
    background: rgba(30, 58, 95, 0.08);
    color: #1e3a5f;
    font-weight: 600;
  }
  &--flow .step-no {
    flex-shrink: 0;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: #e5e7eb;
    color: #6b7280;
    font-size: 11px;
    font-weight: 600;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }
  &--active.flow .step-no,
  &--active .step-no {
    background: #1e3a5f;
    color: #fff;
  }
}
.rail-icon { font-size: 15px; flex-shrink: 0; }
.rail-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rail-phase-progress {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 600;
  color: #6b7280;
  background: #f3f4f6;
  padding: 1px 6px;
  border-radius: 8px;
  font-family: 'JetBrains Mono', monospace;
}
.parallel-divider {
  font-size: 10px;
  color: #d1d5db;
  padding: 8px 10px 4px;
  margin-top: 4px;
  border-top: 1px dashed #e5e7eb;
  letter-spacing: 0.5px;
}

/* ============ 右侧内容区 ============ */
.canvas { flex: 1; min-width: 0; }
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}
.panel-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}
.panel-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

/* ============ 父子层级样式 ============ */
.hierarchy-section {
  margin-bottom: 16px;
}
.hierarchy-section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 8px;
}
.hierarchy-hint {
  font-size: 11px;
  color: #9ca3af;
  font-weight: 400;
  margin-left: 8px;
}
.hierarchy-children {
  margin-top: 12px;
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
}
.cut-sub-tabs {
  :deep(.el-tabs__header) { margin-bottom: 8px; }
  :deep(.el-tabs__nav-wrap::after) { display: none; }
}

/* ============ 阶段操作 ============ */
.phase-actions {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

/* ============ 详情抽屉 ============ */
.detail-actions {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.detail-actions-label {
  font-size: 13px;
  font-weight: 600;
  color: #6b7280;
}

/* ============ 可点击表格 ============ */
.clickable-table {
  :deep(.el-table__row) {
    cursor: pointer;
  }
}

/* ============ 状态标签 ============ */
.status-pill {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 600;
  &--gray { background: rgba(148, 163, 184, 0.15); color: #64748b; }
  &--green { background: rgba(52, 211, 153, 0.15); color: #059669; }
  &--blue { background: rgba(96, 165, 250, 0.15); color: #2563eb; }
  &--yellow { background: rgba(251, 191, 36, 0.15); color: #d97706; }
  &--red { background: rgba(248, 113, 113, 0.15); color: #dc2626; }
  &--purple { background: rgba(167, 139, 250, 0.15); color: #7c3aed; }
}
.progress-text {
  font-size: 11px;
  color: #6b7280;
  margin-left: 6px;
  font-family: 'JetBrains Mono', monospace;
}
:deep(.risk-row--high) {
  background: rgba(248, 113, 113, 0.04) !important;
  td { background: transparent !important; }
}

/* ============ 响应式 ============ */
@media (max-width: 1024px) {
  .detail-body { flex-direction: column; }
  .rail-wrap {
    flex: 1 1 auto;
    :deep(.el-card__body) {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      padding: 8px;
    }
    .rail-stage { width: 100%; }
    .rail-item { width: auto; }
  }
}

/* ============ 模板选择卡片 ============ */
.template-select-body {
  min-height: 200px;
  max-height: 520px;
  overflow-y: auto;
}
.template-card-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.template-card {
  border: 1.5px solid $border;
  border-radius: 6px;
  padding: 12px 14px;
  cursor: pointer;
  transition: all 0.18s ease;
  background: #fafbfc;
  &:hover {
    border-color: $primary;
    background: #fff;
    box-shadow: 0 2px 8px rgba(30, 58, 95, 0.08);
  }
  &--active {
    border-color: $primary;
    background: #eef3f9;
    box-shadow: 0 0 0 2px rgba(30, 58, 95, 0.15);
  }
}
.template-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.template-card-name {
  font-weight: 600;
  font-size: 14px;
  color: $primary;
}
.template-card-code {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
  font-family: 'Courier New', monospace;
}
.template-card-desc {
  font-size: 12px;
  color: #4b5563;
  margin-bottom: 6px;
  line-height: 1.5;
}
.template-card-applicability {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.applicability-tag {
  display: inline-block;
  padding: 2px 8px;
  background: #e5e7eb;
  border-radius: 10px;
  font-size: 11px;
  color: #4b5563;
}

/* ============ 模板章节表单 ============ */
.template-form-meta {
  margin-bottom: 8px;
}
.template-empty-sections {
  padding: 24px;
  text-align: center;
  color: #9ca3af;
  background: #f9fafb;
  border-radius: 4px;
}
.template-section {
  border: 1px solid $border;
  border-radius: 6px;
  padding: 12px 14px 4px;
  margin-bottom: 12px;
  background: #fcfcfd;
}
.template-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 13px;
  color: $primary;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px dashed $border;
}
.template-section-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: $primary;
  color: #fff;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 700;
}
</style>
