export interface SurveyMaterialSelection {
  deviceId: number
  sn: string
  productCode?: string
  productName?: string
  productModel?: string
  projectId: number
  reason: string
}
export const surveyProcurementRoute = (
  kind: 'material' | 'procurement' | 'exchange',
  surveyId: number,
  sn?: string
) => ({
  path: `/pms/engineering/procurement/${{ material: 'imp-material-req', procurement: 'imp-ext-proc', exchange: 'imp-material-exch' }[kind]}`,
  query: { surveyId: String(surveyId), ...(sn ? { deviceSn: sn } : {}) }
})
