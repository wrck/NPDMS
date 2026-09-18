// 业务中心四组内容块（工程实施/方案计划/实施部署/验收闭环），配置与 /pms/project-detail 的
// businessGroups + moduleConfigs 同口径复制承接；消费 engineering/acceptance 域真实 API，
// projectId 语义与sol_*/imp_*/验收域表一致（不校验项目链，inheritance 页已用新链 ID 消费同批 API）。
// 唯一 key 差异：参考页 'satisfaction' 在本页与既有满意度工作台页签冲突，改名 'satisfaction-survey'。
import type { DeliveryModuleConfig } from '@/views/pms/project/inheritance/detail/DeliveryModuleTable.vue'
import * as BriefingApi from '@/api/pms/engineering/briefing'
import * as SolutionApi from '@/api/pms/engineering/solution'
import * as ResourceApi from '@/api/pms/engineering/resource'
import * as ArrivalApi from '@/api/pms/engineering/arrival'
import * as InstallationApi from '@/api/pms/engineering/installation'
import * as ConfigurationApi from '@/api/pms/engineering/configuration'
import * as JointTestApi from '@/api/pms/engineering/joint-test'
import * as ScheduleBackwardApi from '@/api/pms/engineering/schedule-backward'
import * as MaterialExchangeApi from '@/api/pms/engineering/material-exch'
import * as CompletionCertApi from '@/api/pms/acceptance/completion-certificate'
import * as AcceptanceApi from '@/api/pms/acceptance/acceptance'
import * as DeliverableCheckApi from '@/api/pms/acceptance/deliverable-checklist'
import * as ProjectClosureApi from '@/api/pms/project/project-closure'
import * as ArchiveDocApi from '@/api/pms/acceptance/archive-document'
import * as SatisfactionApi from '@/api/pms/acceptance/satisfaction'

export interface BusinessNavItem {
  key: string
  label: string
  icon: string
}

export interface BusinessGroup {
  key: string
  title: string
  flowSteps: BusinessNavItem[]
}

export const businessGroups: BusinessGroup[] = [
  {
    key: 'engineering',
    title: '工程实施',
    flowSteps: [
      { key: 'site-survey', label: '现场工勘', icon: 'ep:position' },
      { key: 'material-exch', label: '物料换货', icon: 'ep:sort' },
      { key: 'requirement', label: '需求分析', icon: 'ep:document-copy' },
      { key: 'briefing', label: '工程交底', icon: 'ep:notebook-2' }
    ]
  },
  {
    key: 'planning',
    title: '方案计划',
    flowSteps: [
      { key: 'solution', label: '实施方案', icon: 'ep:files' },
      { key: 'resource', label: '资源就绪', icon: 'ep:box' },
      { key: 'schedule-backward', label: '工期倒排', icon: 'ep:timer' }
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
    ]
  },
  {
    key: 'acceptance',
    title: '验收闭环',
    flowSteps: [
      { key: 'completion-certificate', label: '完工证明', icon: 'ep:medal' },
      { key: 'satisfaction-survey', label: '满意度调查', icon: 'ep:star' },
      { key: 'acceptance', label: '验收管理', icon: 'ep:circle-check' },
      { key: 'deliverable-checklist', label: '交付件检查', icon: 'ep:folder-checked' },
      { key: 'project-closure', label: '项目闭环', icon: 'ep:lock' },
      { key: 'archive-document', label: '归档文档', icon: 'ep:archive' }
    ]
  }
]

export const businessNavItems: BusinessNavItem[] = businessGroups.flatMap((group) => group.flowSteps)

export const businessModuleConfigs: Record<string, DeliveryModuleConfig> = {
  // --- 工程实施 ---
  // 物料换货（提交后审批，通过后推送CRM）
  'material-exch': {
    label: '物料换货',
    icon: 'ep:sort',
    load: (pid, pageNo, pageSize) => MaterialExchangeApi.getMaterialExchangePage({ projectId: pid, pageNo, pageSize }),
    create: (data) => MaterialExchangeApi.createMaterialExchange(data),
    update: (data) => MaterialExchangeApi.updateMaterialExchange(data),
    delete: (id) => MaterialExchangeApi.deleteMaterialExchange(id),
    columns: [
      { prop: 'code', label: '编码', width: 130 },
      { prop: 'materialName', label: '物料名称', minWidth: 140 },
      { prop: 'materialCode', label: '物料编码', width: 120 },
      { prop: 'quantity', label: '数量', width: 70 },
      { prop: 'reason', label: '不符合项说明', minWidth: 150 },
      { prop: 'crmPushStatus', label: 'CRM推送', width: 90 },
      { prop: 'status', label: '状态', width: 90, type: 'status' }
    ],
    statusMap: { 0: { label: '草稿', tone: 'gray' }, 1: { label: '已提交', tone: 'blue' }, 2: { label: '审批中', tone: 'yellow' }, 3: { label: '已通过', tone: 'green' }, 4: { label: '已驳回', tone: 'red' }, 5: { label: '已撤回', tone: 'gray' }, 6: { label: '已终止', tone: 'gray' } },
    actions: [
      { label: '提交', type: 'primary', show: (r) => r.status === 0, run: (r) => MaterialExchangeApi.submitMaterialExchange(r.id), confirm: '提交该换货申请？' },
      { label: '推送CRM', type: 'success', show: (r) => r.status === 3 && !r.crmPushStatus, run: (r) => MaterialExchangeApi.pushCrmMaterialExchange(r.id), confirm: '推送该换货单到CRM？' }
    ]
  },
  briefing: {
    label: '工程交底',
    icon: 'ep:notebook-2',
    load: (pid, pageNo, pageSize) => BriefingApi.getBriefingPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => BriefingApi.createBriefing(data),
    update: (data) => BriefingApi.updateBriefing(data),
    delete: (id) => BriefingApi.deleteBriefing(id),
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
  // --- 方案计划 ---
  solution: {
    label: '实施方案',
    icon: 'ep:files',
    load: (pid, pageNo, pageSize) => SolutionApi.getSolutionPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => SolutionApi.createSolution(data),
    update: (data) => SolutionApi.updateSolution(data),
    delete: (id) => SolutionApi.deleteSolution(id),
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
  resource: {
    label: '资源就绪',
    icon: 'ep:box',
    load: (pid, pageNo, pageSize) => ResourceApi.getResourceReadyPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => ResourceApi.createResourceReady(data),
    update: (data) => ResourceApi.updateResourceReady(data),
    delete: (id) => ResourceApi.deleteResourceReady(id),
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
  // --- 实施部署 ---
  arrival: {
    label: '到货签收',
    icon: 'ep:takeaway-box',
    load: (pid, pageNo, pageSize) => ArrivalApi.getArrivalPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => ArrivalApi.createArrival(data),
    update: (data) => ArrivalApi.updateArrival(data),
    delete: (id) => ArrivalApi.deleteArrival(id),
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
  installation: {
    label: '硬件安装',
    icon: 'ep:setting',
    load: (pid, pageNo, pageSize) => InstallationApi.getInstallationPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => InstallationApi.createInstallation(data),
    update: (data) => InstallationApi.updateInstallation(data),
    delete: (id) => InstallationApi.deleteInstallation(id),
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
  configuration: {
    label: '配置调试',
    icon: 'ep:tools',
    load: (pid, pageNo, pageSize) => ConfigurationApi.getConfigurationPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => ConfigurationApi.createConfiguration(data),
    update: (data) => ConfigurationApi.updateConfiguration(data),
    delete: (id) => ConfigurationApi.deleteConfiguration(id),
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
    label: '业务联调',
    icon: 'ep:connection',
    load: (pid, pageNo, pageSize) => JointTestApi.getJointTestPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => JointTestApi.createJointTest(data),
    update: (data) => JointTestApi.updateJointTest(data),
    delete: (id) => JointTestApi.deleteJointTest(id),
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
  // --- 验收闭环 ---
  'completion-certificate': {
    label: '完工证明',
    icon: 'ep:medal',
    load: (pid, pageNo, pageSize) => CompletionCertApi.getCompletionCertificatePage({ projectId: pid, pageNo, pageSize }),
    create: (data) => CompletionCertApi.createCompletionCertificate(data),
    update: (data) => CompletionCertApi.updateCompletionCertificate(data),
    delete: (id) => CompletionCertApi.deleteCompletionCertificate(id),
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
  // 满意度调查（后端为结果视图数组接口，包装为 {list,total}；未纳入项目树治理的项目按空数据处理）
  'satisfaction-survey': {
    label: '满意度调查',
    icon: 'ep:star',
    load: async (pid, pageNo, pageSize) => {
      const all = (await SatisfactionApi.listResults(pid).catch(() => [] as any[])) || []
      return { list: all.slice((pageNo - 1) * pageSize, pageNo * pageSize), total: all.length }
    },
    columns: [
      { prop: 'taskId', label: '任务编号', width: 90 },
      { prop: 'questionnaireId', label: '问卷编号', width: 90 },
      { prop: 'score', label: '得分', width: 70 },
      { prop: 'threshold', label: '达标线', width: 70 },
      { prop: 'resultStatus', label: '结果状态', width: 90 },
      { prop: 'passed', label: '是否达标', width: 80 }
    ],
    actions: [{ label: '下载报告', show: (r) => !!r.resultId, run: (r) => SatisfactionApi.getResultDownload(r.resultId, 1) }]
  },
  acceptance: {
    label: '验收管理',
    icon: 'ep:circle-check',
    load: (pid, pageNo, pageSize) => AcceptanceApi.getAcceptancePage({ projectId: pid, pageNo, pageSize }),
    create: (data) => AcceptanceApi.createAcceptance(data),
    update: (data) => AcceptanceApi.updateAcceptance(data),
    delete: (id) => AcceptanceApi.deleteAcceptance(id),
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
    label: '交付件检查',
    icon: 'ep:folder-checked',
    load: (pid, pageNo, pageSize) => DeliverableCheckApi.getDeliverableChecklistPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => DeliverableCheckApi.createDeliverableChecklist(data),
    update: (data) => DeliverableCheckApi.updateDeliverableChecklist(data),
    delete: (id) => DeliverableCheckApi.deleteDeliverableChecklist(id),
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
    label: '项目闭环',
    icon: 'ep:lock',
    load: (pid, pageNo, pageSize) => ProjectClosureApi.getProjectClosurePage({ projectId: pid, pageNo, pageSize }),
    create: (data) => ProjectClosureApi.createProjectClosure(data),
    update: (data) => ProjectClosureApi.updateProjectClosure(data),
    delete: (id) => ProjectClosureApi.deleteProjectClosure(id),
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
    label: '归档文档',
    icon: 'ep:archive',
    load: (pid, pageNo, pageSize) => ArchiveDocApi.getArchiveDocumentPage({ projectId: pid, pageNo, pageSize }),
    create: (data) => ArchiveDocApi.createArchiveDocument(data),
    update: (data) => ArchiveDocApi.updateArchiveDocument(data),
    delete: (id) => ArchiveDocApi.deleteArchiveDocument(id),
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
