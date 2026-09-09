import dayjs from 'dayjs'

/** Inclusive natural days, using the project deadline recorded by site survey. */
export const backwardDuration = (endDate: string, durationDays?: number) => {
  if (!endDate || !dayjs(endDate).isValid() || !Number.isInteger(durationDays) || durationDays! <= 0)
    return ''
  return dayjs(endDate).subtract(durationDays! - 1, 'day').format('YYYY-MM-DD')
}
