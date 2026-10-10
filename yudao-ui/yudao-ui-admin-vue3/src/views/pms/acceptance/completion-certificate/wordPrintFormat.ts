// Print the saved calendar date without introducing a timezone shift or today's date.
export const wordPrintDate = (value?: string) => {
  const match = value?.match(/^(\d{4})-(\d{2})-(\d{2})/)
  return match ? `${match[1]}年 ${Number(match[2])}月 ${Number(match[3])}日` : value || '____年 ____月 ____日'
}
