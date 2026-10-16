export const nativeBusinessKinds = {
  arrival: ['IMP_ARRIVAL', 'arrival'],
  installation: ['IMP_INSTALLATION', 'installation'],
  configuration: ['IMP_CONFIGURATION', 'configuration'],
  jointTest: ['IMP_JOINT_TEST', 'joint-test'],
  training: ['IMP_TRAINING', 'training'],
  survey: ['SOL_SITE_SURVEY', 'site-survey'],
  requirement: ['SOL_REQUIREMENT_ANALYSIS_REVISION', 'requirement-analysis'],
  duration: ['SOL_CONSTRUCTION_PLAN', 'duration'],
  stagePlan: ['SOL_STAGE_PLAN_BATCH', 'stage-plan'],
  solution: ['SOL_SOLUTION', 'solution'],
  contact: ['CUS_PROJECT_CONTACT', 'project-contact'],
  acceptance: ['ACC_ACCEPTANCE_ACTIVITY', 'acceptance'],
  outsource: ['RES_OUTSOURCE_REQUEST', 'outsource'],
  material: ['IMP_MATERIAL_REQUISITION', 'material-requisition'],
  procurement: ['IMP_EXTERNAL_PROCUREMENT', 'external-procurement'],
  exchange: ['IMP_MATERIAL_EXCHANGE', 'material-exchange']
} as const
export type NativeBusinessKind = keyof typeof nativeBusinessKinds
export const nativeBusinessKind = (stableCode: string): NativeBusinessKind | undefined =>
  (Object.keys(nativeBusinessKinds) as NativeBusinessKind[]).find(kind => nativeBusinessKinds[kind][0] === stableCode)
export const nativeBusinessApiBase = (kind: NativeBusinessKind) => `/api/v1/pms/${nativeBusinessKinds[kind][1]}-business`
