import { describe, expect, it } from 'vitest'
import { trainingPrintValues } from './trainingPrintForm'

describe('training print data binding', () => {
  it('loads the persisted signature and business values while retaining custom confirmation answers', () => {
    const signature = 'data:image/png;base64,c2lnbmF0dXJl'
    const data = trainingPrintValues(
      {
        name: '现场培训',
        trainingTypes: 'TECHNICAL_PRINCIPLE,PRODUCT_OPS',
        signatureImageDataUrl: signature,
        skillRating: '很好',
        confirmationValues: JSON.stringify({
          name: '不可覆盖',
          skillRating: '差',
          extraOpinion: '补充意见'
        })
      },
      '项目甲'
    )
    expect(data).toMatchObject({
      name: '现场培训',
      projectName: '项目甲',
      skillRating: '很好',
      extraOpinion: '补充意见',
      signatureImageDataUrl: signature,
      trainingTypes: ['TECHNICAL_PRINCIPLE', 'PRODUCT_OPS']
    })
  })
  it('keeps unsigned records empty instead of fabricating a signature or rating', () => {
    const data = trainingPrintValues({ name: '未签字' }, '')
    expect(data.signatureImageDataUrl).toBeUndefined()
    expect(data.skillRating).toBeUndefined()
    expect(data.trainingTypes).toEqual([])
  })
  it('formats API epoch milliseconds as a readable confirmation time', () => {
    const time = new Date(2026, 8, 20, 14, 30).getTime()
    expect(trainingPrintValues({ signTime: time }, '').signTime).toBe('2026-09-20 14:30')
  })
})
