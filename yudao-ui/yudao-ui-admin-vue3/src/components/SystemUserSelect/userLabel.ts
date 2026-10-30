// Presentation only: preserve the stored user ID and nickname.
export const formatSystemUserLabel = (user: { nickname?: string; username?: string }) => {
  const username = user.username?.trim()
  const name = user.nickname?.trim()
  if (name && username && name !== username && !name.startsWith(`${username}-`))
    return `${username}-${name}`
  return name || username || ''
}
