import { getSiteSurvey } from '@/api/pms/engineering/site-survey'
import { getDevice } from '@/api/pms/asset/device'
import type { SurveyMaterialSelection } from './surveyBusinessForm'

export const loadSurveyActionContext = async (
  surveyId: number,
  kind: 'material' | 'procurement' | 'exchange',
  sn?: string
) => {
  const survey = await getSiteSurvey(surveyId)
  if (!survey || survey.status !== 0) throw new Error('请从可编辑的工勘草稿进入')
  const base = {
    projectId: survey.projectId,
    triggerSource: 'SITE_SURVEY',
    triggerRefId: surveyId,
    name: `工勘${{ material: '领料', procurement: '外采', exchange: '换货' }[kind]}：${survey.name}`,
    remark: `来源工勘 #${surveyId}`
  }
  if (kind !== 'exchange') {
    if (survey.formExtraValues?.extra_railTrayRequired !== true)
      throw new Error('工勘尚未选择需要导轨、托盘')
    return base
  }
  const selected = (survey.formExtraValues?.extra_selectedMaterials ||
    []) as SurveyMaterialSelection[]
  const item = selected.find((row) => row.sn === sn && row.reason?.trim())
  if (survey.formExtraValues?.extra_materialMatches !== false || !item)
    throw new Error('请选择物料并填写不符合项说明')
  const device = (await getDevice(item.deviceId)).summary
  if (device.projectId !== survey.projectId || device.sn !== item.sn)
    throw new Error('设备项目归属已变化，请重新选择')
  // AST deviceId is not a legacy equipmentId. Carry real product data and SN, never interchange IDs.
  return {
    ...base,
    materialName: device.productName || '',
    materialCode: device.productCode || '',
    specification: device.productModel || '',
    reason: item.reason,
    remark: `${base.remark}；设备SN：${device.sn}`
  }
}
