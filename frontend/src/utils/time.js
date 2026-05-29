const UNITS = [
  { max: 60, unit: 'second', div: 1 },
  { max: 3600, unit: 'minute', div: 60 },
  { max: 86400, unit: 'hour', div: 3600 },
  { max: 604800, unit: 'day', div: 86400 },
  { max: 2592000, unit: 'week', div: 604800 },
  { max: Infinity, unit: 'month', div: 2592000 }
]

export function timeAgo(dateStr) {
  const now = Date.now()
  const then = new Date(dateStr).getTime()
  const diff = Math.floor((now - then) / 1000)
  if (diff < 10) return 'just now'

  for (const { max, unit, div } of UNITS) {
    if (diff < max) {
      const n = Math.floor(diff / div)
      return n + ' ' + unit + (n !== 1 ? 's' : '') + ' ago'
    }
  }
}

export function dateGroup(dateStr) {
  const now = new Date()
  const d = new Date(dateStr)
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterdayStart = new Date(todayStart.getTime() - 86400000)
  const weekStart = new Date(todayStart.getTime() - 6 * 86400000)

  if (d >= todayStart) return '今天'
  if (d >= yesterdayStart) return '昨天'
  if (d >= weekStart) return '本周'
  return '更早'
}
