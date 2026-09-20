// 业务中心全流程五组导航（工前准备/制定施工计划/实施方案/实施部署/验收交维），
// 分组口径对齐 DemoV2 左侧导航 + PRD S0~S6 阶段主线；key 指向本页既有工作台页签或 businessModuleConfigs 模块面板。
// 原基本信息组（项目信息/配置Log/团队成员/用户联系人）与项目概览重复已移除：
// base/members 由项目概览承接，配置Log 并入项目概览（序列号详情之后），用户联系人内嵌于客户信息面板。
// 消费 engineering/acceptance/cutover 域真实 API，projectId 语义与 sol_*/imp_*/验收域表一致。
// 满意度导航项 key 'satisfaction' 直接接既有满意度工作台页签（模板/任务/结果一体），不另设结果面板。
import type { DeliveryModuleConfig } from '@/views/pms/project/inheritance/detail/DeliveryModuleTable.vue'
import * as BriefingApi from '@/api/pms/engineering/briefing'
import * as ResourceApi from '@/api/pms/engineering/resource'
import * as ScheduleBackwardApi from '@/api/pms/engineering/schedule-backward'
import * as AcceptanceReportApi from '@/api/pms/acceptance/acceptance-report'
import * as DeliverableCheckApi from '@/api/pms/acceptance/deliverable-checklist'
import * as ProjectClosureApi from '@/api/pms/project/project-closure'
import * as ArchiveDocApi from '@/api/pms/acceptance/archive-document'

export interface BusinessNavItem {
  key: string
  label: string
  icon: string
  /** DemoV2 页面编号（如 1.1），仅导航展示 */
  code?: string
  /** 无权限时导航项隐藏（checkPermi 任一命中即可见，口径同 v-hasPermi） */
  permission?: string[]
}

export interface BusinessFlowGroup {
  key: string
  title: string
  items: BusinessNavItem[]
}

// 全流程业务阶段导航（点击右侧承接完整业务操作界面；workbench key 由 index.vue 接专用面板，模块 key 走 DeliveryModuleTable）
export const businessFlowGroups: BusinessFlowGroup[] = [
  {
    key: 'preparation',
    title: '工前准备',
    items: [
      { key: 'preparation', label: '工勘分工', icon: 'ep:compass', code: '2.2', permission: ['pms:sol-site-survey:query'] },
      { key: 'requirement-analysis', label: '需求分析', icon: 'ep:edit-pen', code: '2.3', permission: ['pms:requirement-analysis:query', 'pms:requirement-analysis:manage'] },
      { key: 'material-exch', label: '物料换货', icon: 'ep:sort', code: '2.2.1' },
      { key: 'briefing', label: '工程交底', icon: 'ep:notebook-2', code: '2.4' }
    ]
  },
  {
    key: 'plan',
    title: '制定施工计划',
    items: [
      // 项目工期(3.1)/阶段施工计划(3.2)/工期倒排(3.4)为同一完整功能（基于项目阶段和任务），
      // 由 index.vue 的 ProjectSchedulePanel 整合承接；资源就绪(3.3)独立
      { key: 'schedule', label: '施工计划', icon: 'ep:calendar', code: '3.1-3.4' },
      { key: 'resource', label: '资源就绪', icon: 'ep:box', code: '3.3' }
    ]
  },
  {
    key: 'solution',
    title: '实施方案',
    items: [{ key: 'solution', label: '实施方案', icon: 'ep:files', code: '4.1' }]
  },
  {
    key: 'deploy',
    title: '实施部署',
    items: [
      { key: 'arrival', label: '到货签收', icon: 'ep:takeaway-box', code: '5.1' },
      { key: 'installation', label: '硬件安装', icon: 'ep:setting', code: '5.2' },
      { key: 'configuration', label: '配置调试', icon: 'ep:tools', code: '5.3' },
      { key: 'joint-test', label: '业务联调', icon: 'ep:connection', code: '5.4' },
      { key: 'cutover', label: '割接上线', icon: 'ep:promotion', code: '5.5' }
    ]
  },
  {
    key: 'acceptance',
    title: '验收交维',
    items: [
      { key: 'training', label: '现场培训', icon: 'ep:reading', code: '6.1' },
      { key: 'satisfaction', label: '满意度调查', icon: 'ep:chat-dot-round', code: '6.2', permission: ['pms:acceptance:satisfaction:query'] },
      { key: 'acceptance-reports', label: '初验&终验', icon: 'ep:document-checked', code: '6.3', permission: ['pms:acceptance:report:query'] },
      { key: 'acceptance', label: '验收记录', icon: 'ep:circle-check', code: '6.3' },
      { key: 'completion-certificate', label: '完工证明', icon: 'ep:medal', code: '6.4' },
      { key: 'deliverable-summary', label: '交付件汇总', icon: 'ep:folder-opened', code: '6.5' },
      { key: 'deliverable-checklist', label: '交付件检查', icon: 'ep:folder-checked', code: '6.6' },
      { key: 'archive-document', label: '归档文档', icon: 'ep:archive', code: '6.7' },
      { key: 'project-closure', label: '闭环记录', icon: 'ep:lock', code: '6.8' },
      { key: 'closure', label: '项目闭环', icon: 'ep:circle-close', code: '6.9' }
    ]
  }
]

export const businessNavItems: BusinessNavItem[] = businessFlowGroups.flatMap((group) => group.items)

// 内嵌完整页面的导航 key（创建/编辑/审批等操作直接在详情页内完成，不走通用模块面板）
// solution/material-exch/arrival/installation/configuration/joint-test/cutover/completion-certificate/config-log
// 内嵌各域真实工作台（Demo 操作界面），其余 key 走 businessModuleConfigs 通用模块面板。
export const flowEmbedKeys = new Set([
  // 施工计划整合工作台（schedule）不在此列：不在 businessModuleConfigs，由专用面板直接承接
  'training',
  'solution',
  'material-exch',
  'arrival',
  'installation',
  'configuration',
  'joint-test',
  'cutover',
  'completion-certificate',
  'config-log'
])

export const businessModuleConfigs: Record<string, DeliveryModuleConfig> = {
  // --- 工程实施 ---
  // 物料换货/实施方案/到货签收/硬件安装/配置调试/业务联调 已内嵌各域工作台（见 flowEmbedKeys），不走通用面板
  briefing: {
    label: '工程交底',
    icon: 'ep:notebook-2',
    load: (pid, pageNo, pageSize) => BriefingApi.getBriefingPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => BriefingApi.createBriefing(data),
    update: (data) => BriefingApi.updateBriefing(data),
    delete: (id) => BriefingApi.deleteBriefing(id),
    columns: [
      { prop: 'name', label: '交底名称', minWidth: 180 },
      { prop: 'briefingType', label: '类型', width: 100 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '草稿', tone: 'gray' }, 1: { label: '已生成', tone: 'yellow' }, 2: { label: '已审核', tone: 'blue' }, 3: { label: '已发布', tone: 'green' }, 4: { label: '已作废', tone: 'red' } },
    // 后端口径：仅草稿可改可删
    canEdit: (r) => r.status === 0,
    canDelete: (r) => r.status === 0,
    actions: [
      { label: '生成', type: 'primary', show: (r) => r.status === 0, run: (r) => BriefingApi.generateBriefing({ id: r.id, version: r.version }), confirm: '生成该交底书？' },
      { label: '审批', type: 'success', show: (r) => r.status === 1, run: (r, opinion) => BriefingApi.approveBriefing({ id: r.id, approveAction: 'PASS', approveOpinion: opinion, version: r.version }), needOpinion: true },
      { label: '发布', type: 'success', show: (r) => r.status === 2, run: (r) => BriefingApi.publishBriefing(r.id), confirm: '发布该交底书？' }
    ]
  },
  // --- 方案计划 ---
  resource: {
    label: '资源就绪',
    icon: 'ep:box',
    load: (pid, pageNo, pageSize) => ResourceApi.getResourceReadyPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => ResourceApi.createResourceReady(data),
    update: (data) => ResourceApi.updateResourceReady(data),
    delete: (id) => ResourceApi.deleteResourceReady(id),
    columns: [
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
    label: '历史工期倒排',
    icon: 'ep:timer',
    load: (pid, pageNo, pageSize) => ScheduleBackwardApi.getScheduleBackwardPage({ projectId: pid, pageNo, pageSize }),
    columns: [
      { prop: 'targetDate', label: '目标日期', width: 130, type: 'time' },
      { prop: 'projectType', label: '项目类型', width: 100 },
      { prop: 'conflictSummary', label: '冲突摘要', minWidth: 200 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ]
  },
  // --- 实施部署（到货/安装/配置/联调/割接均为内嵌工作台） ---
  // --- 验收闭环 ---
  // 完工证明（Demo 6.2 表单）已内嵌 CompletionCertWorkbench，不走通用面板
  acceptance: {
    label: '验收记录',
    icon: 'ep:circle-check',
    // 旧V17单行验收接口已随 pms_* 旧域退役：数据源切换为验收活动（新报告制载体 /api/v1/pms/acceptances），
    // 面板只读嵌入，验收操作统一在新验收报告页面完成
    path: '/customer-asset/acceptance-reports',
    load: async (pid, pageNo, pageSize) => {
      const activities = await AcceptanceReportApi.getActivities(pid)
      const start = (pageNo - 1) * pageSize
      return { list: activities.slice(start, start + pageSize), total: activities.length }
    },
    columns: [
      { prop: 'acceptanceType', label: '类型', width: 120 },
      { prop: 'activityStatus', label: '活动状态', width: 110, type: 'status' },
      { prop: 'currentReportVersionId', label: '当前报告版本', width: 130 },
      { prop: 'version', label: '数据版本', width: 90 }
    ],
    statusField: 'activityStatus',
    statusMap: { PENDING: { label: '待完成', tone: 'yellow' }, COMPLETED: { label: '已完成', tone: 'green' } }
  },
  'deliverable-checklist': {
    label: '交付件检查',
    icon: 'ep:folder-checked',
    load: (pid, pageNo, pageSize) => DeliverableCheckApi.getDeliverableChecklistPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => DeliverableCheckApi.createDeliverableChecklist(data),
    update: (data) => DeliverableCheckApi.updateDeliverableChecklist(data),
    delete: (id) => DeliverableCheckApi.deleteDeliverableChecklist(id),
    columns: [
      { prop: 'name', label: '交付件名称', minWidth: 180 },
      { prop: 'deliverableType', label: '类型', width: 100 },
      { prop: 'signedFlag', label: '已签署', width: 80 },
      { prop: 'validFlag', label: '有效', width: 80 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '草稿', tone: 'gray' }, 1: { label: '已提交', tone: 'yellow' }, 2: { label: '已通过', tone: 'green' }, 3: { label: '已驳回', tone: 'red' } },
    // 后端口径：仅草稿可改；草稿/已驳回可删
    canEdit: (r) => r.status === 0,
    canDelete: (r) => r.status === 0 || r.status === 3,
    actions: [
      { label: '提交', type: 'primary', show: (r) => r.status === 0, run: (r) => DeliverableCheckApi.submitDeliverableChecklist(r.id), confirm: '提交该检查项？' },
      { label: '通过', type: 'success', show: (r) => r.status === 1, run: (r) => DeliverableCheckApi.passDeliverableChecklist(r.id), confirm: '通过该检查项？' },
      { label: '驳回', type: 'danger', show: (r) => r.status === 1, run: (r) => DeliverableCheckApi.rejectDeliverableChecklist(r.id), confirm: '驳回该检查项？' }
    ]
  },
  'project-closure': {
    label: '项目闭环',
    icon: 'ep:lock',
    load: (pid, pageNo, pageSize) => ProjectClosureApi.getProjectClosurePage({ projectId: pid, pageNo, pageSize }),
    create: (data) => ProjectClosureApi.createProjectClosure(data),
    update: (data) => ProjectClosureApi.updateProjectClosure(data),
    delete: (id) => ProjectClosureApi.deleteProjectClosure(id),
    columns: [
      { prop: 'name', label: '闭环名称', minWidth: 180 },
      { prop: 'applicationDate', label: '申请日期', width: 120, type: 'time' },
      { prop: 'carryoverIssues', label: '遗留问题', minWidth: 160 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '草稿', tone: 'gray' }, 1: { label: '待审批', tone: 'yellow' }, 2: { label: '审批中', tone: 'blue' }, 3: { label: '已通过', tone: 'green' }, 4: { label: '已驳回', tone: 'red' }, 5: { label: '已归档', tone: 'gray' } },
    // 后端口径：仅草稿可改；草稿/已驳回可删
    canEdit: (r) => r.status === 0,
    canDelete: (r) => r.status === 0 || r.status === 4,
    actions: [
      { label: '提交', type: 'primary', show: (r) => r.status === 0, run: (r) => ProjectClosureApi.submitProjectClosure(r.id, r.projectId), confirm: '提交该闭环申请？' },
      { label: '开始审批', type: 'success', show: (r) => r.status === 1, run: (r) => ProjectClosureApi.startApproveProjectClosure(r.id), confirm: '开始审批该闭环申请？' },
      { label: '通过', type: 'success', show: (r) => r.status === 2, run: (r) => ProjectClosureApi.passProjectClosure(r.id), confirm: '通过该闭环申请？' },
      { label: '驳回', type: 'danger', show: (r) => r.status === 2, run: (r) => ProjectClosureApi.rejectProjectClosure(r.id), confirm: '驳回该闭环申请？' },
      { label: '归档', type: 'info', show: (r) => r.status === 3, run: (r) => ProjectClosureApi.archiveProjectClosure(r.id), confirm: '归档该闭环记录？' }
    ]
  },
  'archive-document': {
    label: '归档文档',
    icon: 'ep:archive',
    load: (pid, pageNo, pageSize) => ArchiveDocApi.getArchiveDocumentPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => ArchiveDocApi.createArchiveDocument(data),
    update: (data) => ArchiveDocApi.updateArchiveDocument(data),
    delete: (id) => ArchiveDocApi.deleteArchiveDocument(id),
    columns: [
      { prop: 'name', label: '文档名称', minWidth: 180 },
      { prop: 'documentType', label: '类型', width: 100 },
      { prop: 'version', label: '版本', width: 80 },
      { prop: 'uploadedDate', label: '上传日期', width: 120, type: 'time' },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '草稿', tone: 'gray' }, 1: { label: '待归档', tone: 'yellow' }, 2: { label: '已归档', tone: 'green' } },
    // 后端口径：仅草稿可改可删，已归档为不可变历史
    canEdit: (r) => r.status === 0,
    canDelete: (r) => r.status === 0,
    actions: [
      { label: '提交', type: 'primary', show: (r) => r.status === 0, run: (r) => ArchiveDocApi.submitArchiveDocument(r.id), confirm: '提交该归档文档？' },
      { label: '归档', type: 'success', show: (r) => r.status === 1, run: (r) => ArchiveDocApi.archiveArchiveDocument(r.id), confirm: '归档该文档？' }
    ]
  }
}
