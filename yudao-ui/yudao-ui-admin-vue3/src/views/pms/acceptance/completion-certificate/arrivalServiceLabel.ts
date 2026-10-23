export const arrivalServiceLabel = (choice?: string, date?: string | null): string => {
  if (choice !== '是') return '完成到货验收'
  const parts = date?.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  const when = parts ? `${parts[1]}年${Number(parts[2])}月${Number(parts[3])}日` : '____年___月___日'
  return `于${when}完成到货验收`
}
