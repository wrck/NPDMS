;(function initializePersistedTheme() {
  var root = document.documentElement

  function readCacheValue(key) {
    try {
      var rawValue = localStorage.getItem(key)
      if (!rawValue) return null

      var cacheItem = JSON.parse(rawValue)
      if (
        !cacheItem ||
        typeof cacheItem !== 'object' ||
        typeof cacheItem.v !== 'string' ||
        typeof cacheItem.e !== 'number'
      ) {
        return null
      }

      if (Date.now() >= cacheItem.e) {
        localStorage.removeItem(key)
        return null
      }

      return JSON.parse(cacheItem.v)
    } catch (_error) {
      return null
    }
  }

  var theme = readCacheValue('theme')
  if (theme && typeof theme === 'object' && !Array.isArray(theme)) {
    Object.keys(theme).forEach(function applyThemeProperty(key) {
      var value = theme[key]
      if (typeof value !== 'string' && typeof value !== 'number') return

      var cssVariable = '--' + key.replace(/([A-Z])/g, '-$1').toLowerCase()
      root.style.setProperty(cssVariable, String(value))
    })
  }

  var isDark = readCacheValue('isDark')
  if (typeof isDark === 'boolean') {
    root.classList.remove(isDark ? 'light' : 'dark')
    root.classList.add(isDark ? 'dark' : 'light')
  }
})()
