<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="模板编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="模板名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option value="DRAFT" label="草稿" />
          <el-option value="ACTIVE" label="生效" />
          <el-option value="RETIRED" label="停用" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="reload">
          <Icon icon="ep:search" />查询
        </el-button>
        <el-button type="primary" @click="openCreate" v-hasPermi="['pms:project-template:create']">
          <Icon icon="ep:plus" />新增模板
        </el-button>
        <el-button type="warning" plain @click="openMatchPreview" v-hasPermi="['pms:project-template:query']">
          <Icon icon="ep:magic-stick" />匹配预演
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无项目模板数据">
      <el-table-column prop="code" label="模板编码" min-width="150" />
      <el-table-column prop="name" label="模板名称" min-width="160" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="matchPriority" label="匹配优先级" width="100" />
      <el-table-column label="系统保留" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.systemReserved" type="danger">保留</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:project-template:query']">
            详情
          </el-button>
          <el-button
            link
            type="success"
            :disabled="row.status === 'RETIRED'"
            @click="publish(row)"
            v-hasPermi="['pms:project-template:publish']"
          >
            发布
          </el-button>
          <el-button
            link
            type="warning"
            :disabled="row.status !== 'ACTIVE'"
            @click="disable(row)"
            v-hasPermi="['pms:project-template:disable']"
          >
            停用
          </el-button>
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:project-template:delete']">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新增模板 Dialog（生成 DRAFT 草稿） -->
  <Dialog v-model="createVisible" title="新增项目模板" width="620px">
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="110px">
      <el-form-item label="模板编码" prop="code">
        <el-input v-model="createForm.code" placeholder="如 TPL-STD-DELIVERY（创建后不可修改）" />
      </el-form-item>
      <el-form-item label="模板名称" prop="name">
        <el-input v-model="createForm.name" />
      </el-form-item>
      <el-form-item label="匹配优先级" prop="matchPriority">
        <el-input-number v-model="createForm.matchPriority" :min="1" controls-position="right" />
        <span class="ml-8px text-12px text-gray-400">数值小者先命中</span>
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input v-model="createForm.description" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveCreate">创建</el-button>
    </template>
  </Dialog>

  <!-- 模板详情抽屉 -->
  <el-drawer v-model="detailVisible" :title="`模板详情：${detail?.code ?? ''}`" size="72%">
    <template v-if="detail">
      <el-tabs v-model="detailTab">
        <!-- ========== 基本信息 ========== -->
        <el-tab-pane label="基本信息" name="identity">
          <el-form :model="identityForm" label-width="110px" class="max-w-560px">
            <el-form-item label="模板编码">
              <el-input :model-value="detail.code" disabled />
            </el-form-item>
            <el-form-item label="状态">
              <el-tag :type="statusTagType(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
            </el-form-item>
            <el-form-item label="模板名称">
              <el-input v-model="identityForm.name" :disabled="detail.status === 'RETIRED'" />
            </el-form-item>
            <el-form-item label="匹配优先级">
              <el-input-number
                v-model="identityForm.matchPriority"
                :min="1"
                controls-position="right"
                :disabled="detail.status === 'RETIRED'"
              />
            </el-form-item>
            <el-form-item label="描述">
              <el-input
                v-model="identityForm.description"
                type="textarea"
                :rows="2"
                :disabled="detail.status === 'RETIRED'"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="saving"
                :disabled="detail.status === 'RETIRED'"
                @click="saveIdentity"
                v-hasPermi="['pms:project-template:update']"
              >
                保存基本信息
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- ========== 草稿内容 ========== -->
        <el-tab-pane label="草稿内容" name="draft">
          <el-alert
            v-if="detail.status === 'RETIRED'"
            title="模板已停用，草稿冻结只读（重新供给需新建模板）"
            type="warning"
            :closable="false"
            class="mb-12px"
          />
          <el-form label-width="110px">
            <el-divider content-position="left">四维匹配条件（留空=不限）</el-divider>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="签约方式">
                  <el-select v-model="draft.signingMethod" clearable class="!w-full" :disabled="draftReadonly">
                    <el-option v-for="d in getStrDictOptions(DICT_TYPE.PMS_SIGNING_METHOD)" :key="d.value" :label="d.label" :value="d.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="项目类别">
                  <el-select v-model="draft.projectCategory" clearable class="!w-full" :disabled="draftReadonly">
                    <el-option v-for="d in getStrDictOptions(DICT_TYPE.PMS_PROJECT_CATEGORY)" :key="d.value" :label="d.label" :value="d.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="实施方式">
                  <el-select v-model="draft.implementationMethod" clearable class="!w-full" :disabled="draftReadonly">
                    <el-option v-for="d in getStrDictOptions(DICT_TYPE.PMS_IMPLEMENTATION_METHOD)" :key="d.value" :label="d.label" :value="d.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="重大项目级别">
                  <el-select v-model="draft.majorProjectLevel" clearable class="!w-full" :disabled="draftReadonly">
                    <el-option v-for="d in getStrDictOptions(DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL)" :key="d.value" :label="d.label" :value="d.value" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-divider content-position="left">流程定义引用</el-divider>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="流程定义Key">
                  <el-input v-model="draft.processDefinitionKey" :disabled="draftReadonly" placeholder="如 project_delivery_flow" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="流程版本">
                  <el-input v-model="draft.processDefinitionVersion" :disabled="draftReadonly" />
                </el-form-item>
              </el-col>
            </el-row>

            <!-- 阶段定义 -->
            <el-divider content-position="left">阶段定义（S0～S6）</el-divider>
            <el-table :data="draft.stages" border size="small">
              <el-table-column label="阶段码" width="140">
                <template #default="{ row }"><el-input v-model="row.stageCode" :disabled="draftReadonly" /></template>
              </el-table-column>
              <el-table-column label="名称" min-width="140">
                <template #default="{ row }"><el-input v-model="row.name" :disabled="draftReadonly" /></template>
              </el-table-column>
              <el-table-column label="顺序" width="120">
                <template #default="{ row }"><el-input-number v-model="row.sortOrder" :min="0" controls-position="right" :disabled="draftReadonly" class="!w-full" /></template>
              </el-table-column>
              <el-table-column label="准入条件" min-width="150">
                <template #default="{ row }"><el-input v-model="row.entryCriteria" :disabled="draftReadonly" /></template>
              </el-table-column>
              <el-table-column label="准出条件" min-width="150">
                <template #default="{ row }"><el-input v-model="row.exitCriteria" :disabled="draftReadonly" /></template>
              </el-table-column>
              <el-table-column v-if="!draftReadonly" label="操作" width="70">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="draft.stages.splice($index, 1)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button v-if="!draftReadonly" class="mt-4px" size="small" @click="draft.stages.push({ stageCode: '', name: '', sortOrder: draft.stages.length })">
              <Icon icon="ep:plus" />新增阶段
            </el-button>

            <!-- 任务定义 -->
            <el-divider content-position="left">任务定义（可父子 WBS 初始化清单）</el-divider>
            <el-table :data="draft.tasks" border size="small">
              <el-table-column label="任务码" width="140">
                <template #default="{ row }"><el-input v-model="row.taskCode" :disabled="draftReadonly" /></template>
              </el-table-column>
              <el-table-column label="名称" min-width="140">
                <template #default="{ row }"><el-input v-model="row.name" :disabled="draftReadonly" /></template>
              </el-table-column>
              <el-table-column label="父任务码" width="130">
                <template #default="{ row }"><el-input v-model="row.parentTaskCode" :disabled="draftReadonly" /></template>
              </el-table-column>
              <el-table-column label="所属阶段" width="120">
                <template #default="{ row }">
                  <el-select v-model="row.stageCode" clearable :disabled="draftReadonly" class="!w-full">
                    <el-option v-for="s in draft.stages" :key="s.stageCode" :label="s.name" :value="s.stageCode" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="优先级" width="110">
                <template #default="{ row }"><el-input-number v-model="row.priority" :min="0" controls-position="right" :disabled="draftReadonly" class="!w-full" /></template>
              </el-table-column>
              <el-table-column label="排序" width="110">
                <template #default="{ row }"><el-input-number v-model="row.sortOrder" :min="0" controls-position="right" :disabled="draftReadonly" class="!w-full" /></template>
              </el-table-column>
              <el-table-column label="预估工时" width="120">
                <template #default="{ row }"><el-input-number v-model="row.estimatedHours" :min="0" controls-position="right" :disabled="draftReadonly" class="!w-full" /></template>
              </el-table-column>
              <el-table-column label="满意度时点" width="130">
                <template #default="{ row }"><el-input v-model="row.satisfactionTiming" :disabled="draftReadonly" /></template>
              </el-table-column>
              <el-table-column v-if="!draftReadonly" label="操作" width="70">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="draft.tasks.splice($index, 1)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button v-if="!draftReadonly" class="mt-4px" size="small" @click="draft.tasks.push({ taskCode: '', name: '' })">
              <Icon icon="ep:plus" />新增任务
            </el-button>

            <!-- 里程碑定义 -->
            <el-divider content-position="left">里程碑定义</el-divider>
            <el-table :data="draft.milestones" border size="small">
              <el-table-column label="里程碑码" width="150">
                <template #default="{ row }"><el-input v-model="row.milestoneCode" :disabled="draftReadonly" /></template>
              </el-table-column>
              <el-table-column label="名称" min-width="140">
                <template #default="{ row }"><el-input v-model="row.name" :disabled="draftReadonly" /></template>
              </el-table-column>
              <el-table-column label="所属阶段" width="130">
                <template #default="{ row }">
                  <el-select v-model="row.stageCode" clearable :disabled="draftReadonly" class="!w-full">
                    <el-option v-for="s in draft.stages" :key="s.stageCode" :label="s.name" :value="s.stageCode" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="时点" min-width="130">
                <template #default="{ row }"><el-input v-model="row.timing" :disabled="draftReadonly" /></template>
              </el-table-column>
              <el-table-column label="达成标准" min-width="150">
                <template #default="{ row }"><el-input v-model="row.criteria" :disabled="draftReadonly" /></template>
              </el-table-column>
              <el-table-column v-if="!draftReadonly" label="操作" width="70">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="draft.milestones.splice($index, 1)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button v-if="!draftReadonly" class="mt-4px" size="small" @click="draft.milestones.push({ milestoneCode: '', name: '' })">
              <Icon icon="ep:plus" />新增里程碑
            </el-button>

            <!-- 交付件定义 -->
            <el-divider content-position="left">交付件定义</el-divider>
            <el-table :data="draft.deliverables" border size="small">
              <el-table-column label="交付件码" width="150">
                <template #default="{ row }"><el-input v-model="row.deliverableCode" :disabled="draftReadonly" /></template>
              </el-table-column>
              <el-table-column label="名称" min-width="140">
                <template #default="{ row }"><el-input v-model="row.name" :disabled="draftReadonly" /></template>
              </el-table-column>
              <el-table-column label="所属阶段" width="130">
                <template #default="{ row }">
                  <el-select v-model="row.stageCode" clearable :disabled="draftReadonly" class="!w-full">
                    <el-option v-for="s in draft.stages" :key="s.stageCode" :label="s.name" :value="s.stageCode" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="关联任务" width="130">
                <template #default="{ row }">
                  <el-select v-model="row.taskCode" clearable :disabled="draftReadonly" class="!w-full">
                    <el-option v-for="t in draft.tasks" :key="t.taskCode" :label="t.name" :value="t.taskCode" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="必需" width="80">
                <template #default="{ row }">
                  <el-checkbox v-model="row.required" :disabled="draftReadonly" />
                </template>
              </el-table-column>
              <el-table-column v-if="!draftReadonly" label="操作" width="70">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="draft.deliverables.splice($index, 1)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button v-if="!draftReadonly" class="mt-4px" size="small" @click="draft.deliverables.push({ deliverableCode: '', name: '' })">
              <Icon icon="ep:plus" />新增交付件
            </el-button>

            <!-- 门禁定义 -->
            <el-divider content-position="left">门禁定义（ENTRY 准入 / EXIT 准出，结构化引用）</el-divider>
            <el-table :data="draft.gates" border size="small">
              <el-table-column label="门禁码" width="140">
                <template #default="{ row }"><el-input v-model="row.gateCode" :disabled="draftReadonly" /></template>
              </el-table-column>
              <el-table-column label="名称" min-width="130">
                <template #default="{ row }"><el-input v-model="row.name" :disabled="draftReadonly" /></template>
              </el-table-column>
              <el-table-column label="类型" width="110">
                <template #default="{ row }">
                  <el-select v-model="row.gateType" :disabled="draftReadonly" class="!w-full">
                    <el-option value="ENTRY" label="准入" />
                    <el-option value="EXIT" label="准出" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="所属阶段" width="130">
                <template #default="{ row }">
                  <el-select v-model="row.stageCode" clearable :disabled="draftReadonly" class="!w-full">
                    <el-option v-for="s in draft.stages" :key="s.stageCode" :label="s.name" :value="s.stageCode" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="引用（类型:编码@版本）" min-width="220">
                <template #default="{ row }">
                  <div v-for="(ref, idx) in row.references" :key="idx" class="gate-ref-row">
                    <el-select v-model="ref.refType" size="small" class="!w-110px" :disabled="draftReadonly">
                      <el-option value="TASK" label="任务" />
                      <el-option value="DELIVERABLE" label="交付件" />
                      <el-option value="STATE" label="状态码" />
                      <el-option value="PROCESS" label="流程" />
                    </el-select>
                    <el-input v-model="ref.refCode" size="small" class="!w-130px" :disabled="draftReadonly" />
                    <el-input v-model="ref.refVersion" size="small" class="!w-80px" placeholder="版本" :disabled="draftReadonly" />
                    <el-button v-if="!draftReadonly" link type="danger" size="small" @click="row.references.splice(idx, 1)">删</el-button>
                  </div>
                  <el-button v-if="!draftReadonly" link type="primary" size="small" @click="(row.references ??= []).push({ refType: 'TASK', refCode: '' })">
                    +引用
                  </el-button>
                </template>
              </el-table-column>
              <el-table-column v-if="!draftReadonly" label="操作" width="70">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="draft.gates.splice($index, 1)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button v-if="!draftReadonly" class="mt-4px" size="small" @click="draft.gates.push({ gateCode: '', name: '', gateType: 'ENTRY', references: [] })">
              <Icon icon="ep:plus" />新增门禁
            </el-button>

            <div class="mt-16px">
              <el-button
                type="primary"
                :loading="saving"
                :disabled="draftReadonly"
                @click="saveDraft"
                v-hasPermi="['pms:project-template:update']"
              >
                保存草稿
              </el-button>
              <el-button
                type="success"
                :loading="saving"
                :disabled="draftReadonly"
                @click="publish(detail)"
                v-hasPermi="['pms:project-template:publish']"
              >
                发布
              </el-button>
            </div>
          </el-form>
        </el-tab-pane>

        <!-- ========== 版本历史 ========== -->
        <el-tab-pane label="版本历史" name="revisions">
          <el-table :data="detail.revisions" border size="small">
            <el-table-column prop="revisionNo" label="版本号" width="90">
              <template #default="{ row }">
                {{ row.revisionNo === 0 ? '草稿(0)' : row.revisionNo }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
                  {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="四维条件" min-width="240">
              <template #default="{ row }">
                {{ dimText(row) }}
              </template>
            </el-table-column>
            <el-table-column prop="validationSummary" label="校验摘要" min-width="200" show-overflow-tooltip />
            <el-table-column prop="publishedBy" label="发布人" width="90" />
            <el-table-column prop="publishedTime" label="发布时间" min-width="160" :formatter="dateFormatter" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'PUBLISHED'"
                  link
                  type="primary"
                  @click="viewRevision(row)"
                  v-hasPermi="['pms:project-template:query']"
                >
                  查看快照
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </template>
  </el-drawer>

  <!-- 已发布版本快照（只读） -->
  <Dialog v-model="snapshotVisible" :title="`已发布版本快照 v${snapshot?.revisionNo ?? ''}`" width="860px">
    <template v-if="snapshot">
      <el-descriptions :column="2" border size="small" class="mb-12px">
        <el-descriptions-item label="四维条件" :span="2">{{ dimText(snapshot) }}</el-descriptions-item>
        <el-descriptions-item label="流程引用">
          {{ snapshot.processDefinitionKey ?? '-' }}{{ snapshot.processDefinitionVersion ? ` (v${snapshot.processDefinitionVersion})` : '' }}
        </el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ formatDate(snapshot.publishedTime) }}</el-descriptions-item>
      </el-descriptions>
      <el-tabs>
        <el-tab-pane :label="`阶段(${snapshot.content.stages.length})`">
          <el-table :data="snapshot.content.stages" border size="small">
            <el-table-column prop="stageCode" label="阶段码" width="110" />
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column prop="sortOrder" label="顺序" width="70" />
            <el-table-column prop="entryCriteria" label="准入条件" min-width="140" />
            <el-table-column prop="exitCriteria" label="准出条件" min-width="140" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`任务(${snapshot.content.tasks.length})`">
          <el-table :data="snapshot.content.tasks" border size="small">
            <el-table-column prop="taskCode" label="任务码" width="120" />
            <el-table-column prop="name" label="名称" min-width="130" />
            <el-table-column prop="parentTaskCode" label="父任务" width="110" />
            <el-table-column prop="stageCode" label="阶段" width="100" />
            <el-table-column prop="estimatedHours" label="预估工时" width="90" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`里程碑(${snapshot.content.milestones.length})`">
          <el-table :data="snapshot.content.milestones" border size="small">
            <el-table-column prop="milestoneCode" label="里程碑码" width="130" />
            <el-table-column prop="name" label="名称" min-width="130" />
            <el-table-column prop="stageCode" label="阶段" width="100" />
            <el-table-column prop="timing" label="时点" min-width="120" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`交付件(${snapshot.content.deliverables.length})`">
          <el-table :data="snapshot.content.deliverables" border size="small">
            <el-table-column prop="deliverableCode" label="交付件码" width="130" />
            <el-table-column prop="name" label="名称" min-width="130" />
            <el-table-column prop="stageCode" label="阶段" width="100" />
            <el-table-column prop="taskCode" label="任务" width="110" />
            <el-table-column label="必需" width="70">
              <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`门禁(${snapshot.content.gates.length})`">
          <el-table :data="snapshot.content.gates" border size="small">
            <el-table-column prop="gateCode" label="门禁码" width="120" />
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column prop="gateType" label="类型" width="80" />
            <el-table-column prop="stageCode" label="阶段" width="100" />
            <el-table-column label="引用" min-width="220">
              <template #default="{ row }">
                {{ (row.references ?? []).map((r) => `${r.refType}:${r.refCode}${r.refVersion ? '@' + r.refVersion : ''}`).join('、') || '-' }}
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </template>
  </Dialog>

  <!-- 四维匹配预演 -->
  <Dialog v-model="matchVisible" title="四维匹配预演" width="640px">
    <el-form :model="matchForm" label-width="110px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="签约方式">
            <el-select v-model="matchForm.signingMethod" clearable class="!w-full">
              <el-option v-for="d in getStrDictOptions(DICT_TYPE.PMS_SIGNING_METHOD)" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="项目类别">
            <el-select v-model="matchForm.projectCategory" clearable class="!w-full">
              <el-option v-for="d in getStrDictOptions(DICT_TYPE.PMS_PROJECT_CATEGORY)" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="实施方式">
            <el-select v-model="matchForm.implementationMethod" clearable class="!w-full">
              <el-option v-for="d in getStrDictOptions(DICT_TYPE.PMS_IMPLEMENTATION_METHOD)" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="重大项目级别">
            <el-select v-model="matchForm.majorProjectLevel" clearable class="!w-full">
              <el-option v-for="d in getStrDictOptions(DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL)" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item>
        <el-button type="primary" :loading="matching" @click="runMatchPreview">
          <Icon icon="ep:search" />执行预演
        </el-button>
      </el-form-item>
    </el-form>
    <template v-if="matchResult">
      <el-result
        v-if="matchResult.outcome === 'MATCHED' && matchResult.matched"
        icon="success"
        :title="`唯一命中：${matchResult.matched.code} - ${matchResult.matched.name}`"
        :sub-title="`匹配优先级 ${matchResult.matched.matchPriority ?? '-'}`"
      />
      <el-result v-else-if="matchResult.outcome === 'NO_MATCH'" icon="warning" title="无匹配模板" />
      <template v-else>
        <el-alert title="同优先级多匹配（冲突清单，需人工处理，不静默选模）" type="error" :closable="false" class="mb-8px" />
        <ul class="conflict-list">
          <li v-for="(c, i) in matchResult.conflicts" :key="i">{{ c }}</li>
        </ul>
      </template>
      <el-alert
        v-if="matchResult.outcome === 'NO_MATCH' && matchResult.conflicts.length"
        type="info"
        :closable="false"
        class="mt-8px"
      >
        <ul class="conflict-list">
          <li v-for="(c, i) in matchResult.conflicts" :key="i">{{ c }}</li>
        </ul>
      </el-alert>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { dateFormatter, formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import * as TemplateApi from '@/api/pms/project/project-templates'
import type {
  MatchPreviewReqVO,
  MatchRespVO,
  ProjectTemplateDetailVO,
  ProjectTemplateRevisionDetailVO,
  ProjectTemplateVO,
  TemplateDefinitionContent
} from '@/api/pms/project/project-templates'

defineOptions({ name: 'PmsProjectTemplate' })

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<ProjectTemplateVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  code: '',
  name: '',
  status: undefined as string | undefined
})

// ============ 列表 ============
const load = async () => {
  loading.value = true
  try {
    const data = await TemplateApi.getProjectTemplatePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const reload = () => {
  query.pageNo = 1
  load()
}

const statusLabel = (status?: string) =>
  ({ DRAFT: '草稿', ACTIVE: '生效', RETIRED: '停用' })[status ?? ''] ?? status
type ElTagType = 'primary' | 'success' | 'warning' | 'danger' | 'info' | undefined
const statusTagTypes: Record<string, ElTagType> = {
  DRAFT: 'info',
  ACTIVE: 'success',
  RETIRED: 'danger'
}
const statusTagType = (status?: string): ElTagType =>
  statusTagTypes[status ?? ''] ?? 'info'

// ============ 新增 ============
const createVisible = ref(false)
const createFormRef = ref()
const createForm = reactive({ code: '', name: '', matchPriority: 100, description: '' })
const createRules = {
  code: [{ required: true, message: '请输入模板编码' }],
  name: [{ required: true, message: '请输入模板名称' }]
}
const openCreate = () => {
  Object.assign(createForm, { code: '', name: '', matchPriority: 100, description: '' })
  createVisible.value = true
}
const saveCreate = async () => {
  await createFormRef.value.validate()
  saving.value = true
  try {
    await TemplateApi.createProjectTemplate(createForm)
    message.success('模板创建成功（已生成草稿版本）')
    createVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// ============ 详情抽屉 ============
const detailVisible = ref(false)
const detailTab = ref('identity')
const detail = ref<ProjectTemplateDetailVO>()
const identityForm = reactive({ name: '', matchPriority: 100, description: '' })
const draft = reactive<TemplateDefinitionContent>({
  signingMethod: undefined,
  projectCategory: undefined,
  implementationMethod: undefined,
  majorProjectLevel: undefined,
  processDefinitionKey: undefined,
  processDefinitionVersion: undefined,
  stages: [],
  tasks: [],
  milestones: [],
  deliverables: [],
  gates: []
})
const draftReadonly = computed(() => detail.value?.status === 'RETIRED')

const openDetail = async (row: ProjectTemplateVO) => {
  detail.value = await TemplateApi.getProjectTemplate(row.id!)
  identityForm.name = detail.value.name
  identityForm.matchPriority = detail.value.matchPriority ?? 100
  identityForm.description = detail.value.description ?? ''
  const content = detail.value.draftContent
  Object.assign(draft, {
    signingMethod: content?.signingMethod,
    projectCategory: content?.projectCategory,
    implementationMethod: content?.implementationMethod,
    majorProjectLevel: content?.majorProjectLevel,
    processDefinitionKey: content?.processDefinitionKey,
    processDefinitionVersion: content?.processDefinitionVersion,
    stages: content?.stages ?? [],
    tasks: content?.tasks ?? [],
    milestones: content?.milestones ?? [],
    deliverables: content?.deliverables ?? [],
    gates: (content?.gates ?? []).map((g) => ({ ...g, references: g.references ?? [] }))
  })
  detailTab.value = 'identity'
  detailVisible.value = true
}

const saveIdentity = async () => {
  if (!detail.value?.id) return
  saving.value = true
  try {
    await TemplateApi.updateProjectTemplate(detail.value.id, {
      name: identityForm.name,
      matchPriority: identityForm.matchPriority,
      description: identityForm.description
    })
    message.success('基本信息已保存')
    await openDetail(detail.value)
  } finally {
    saving.value = false
  }
}

const saveDraft = async () => {
  if (!detail.value?.id) return
  saving.value = true
  try {
    await TemplateApi.updateProjectTemplate(detail.value.id, { content: { ...draft } })
    message.success('草稿已保存')
    await openDetail(detail.value)
    detailTab.value = 'draft'
  } finally {
    saving.value = false
  }
}

// ============ 发布 / 停用 / 删除 ============
const publish = async (row: ProjectTemplateVO) => {
  await message.confirm(`确认发布模板「${row.code}」？发布将校验草稿并冻结为只读版本。`)
  await TemplateApi.publishProjectTemplate(row.id!)
  message.success('发布成功')
  detailVisible.value = false
  await load()
}
const disable = async (row: ProjectTemplateVO) => {
  await message.confirm(`确认停用模板「${row.code}」？停用后不再匹配新项目，已建项目绑定不受影响。`)
  await TemplateApi.disableProjectTemplate(row.id!)
  message.success('已停用')
  detailVisible.value = false
  await load()
}
const remove = async (row: ProjectTemplateVO) => {
  await message.delConfirm(`确认删除模板「${row.code}」？仅无已发布版本且非系统保留的模板可删除。`)
  await TemplateApi.deleteProjectTemplate(row.id!)
  message.success('删除成功')
  detailVisible.value = false
  await load()
}

// ============ 版本快照 ============
const snapshotVisible = ref(false)
const snapshot = ref<ProjectTemplateRevisionDetailVO>()
const viewRevision = async (revision: { revisionNo: number }) => {
  if (!detail.value?.id) return
  snapshot.value = await TemplateApi.getProjectTemplateRevision(detail.value.id, revision.revisionNo)
  snapshotVisible.value = true
}

const dimText = (row: {
  signingMethod?: string
  projectCategory?: string
  implementationMethod?: string
  majorProjectLevel?: string
}) => {
  const parts = [
    row.signingMethod,
    row.projectCategory,
    row.implementationMethod,
    row.majorProjectLevel
  ].filter((v) => v)
  return parts.length ? parts.join(' / ') : '全部不限'
}

// ============ 匹配预演 ============
const matchVisible = ref(false)
const matching = ref(false)
const matchResult = ref<MatchRespVO>()
const matchForm = reactive<MatchPreviewReqVO>({
  signingMethod: undefined,
  projectCategory: undefined,
  implementationMethod: undefined,
  majorProjectLevel: undefined
})
const openMatchPreview = () => {
  matchResult.value = undefined
  matchVisible.value = true
}
const runMatchPreview = async () => {
  matching.value = true
  try {
    matchResult.value = await TemplateApi.matchPreview({ ...matchForm })
  } finally {
    matching.value = false
  }
}

onMounted(() => {
  load()
})
</script>

<style lang="scss" scoped>
.gate-ref-row {
  display: flex;
  gap: 4px;
  align-items: center;
  margin-bottom: 4px;
}

.conflict-list {
  padding-left: 18px;
  margin: 0;

  li {
    line-height: 22px;
  }
}

.max-w-560px {
  max-width: 560px;
}
</style>
