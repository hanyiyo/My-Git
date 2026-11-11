export interface PrototypeDateOption {
  value: string
  label: string
  sub: string
}

export const prototypeToday = '2026-11-10'

export function parsePrototypeDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

export function formatPrototypeDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function addPrototypeDays(value: string, days: number): string {
  const date = parsePrototypeDate(value)
  date.setDate(date.getDate() + days)
  return formatPrototypeDate(date)
}

export const prototypeDateEnd = addPrototypeDays(prototypeToday, 30)

/** Keep prototype date choices deterministic while showing a complete month. */
export function createPrototypeDateOptions(start = prototypeToday, days = 31): PrototypeDateOption[] {
  const startDate = parsePrototypeDate(start)
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

  return Array.from({ length: days }, (_, index) => {
    const current = new Date(startDate)
    current.setDate(startDate.getDate() + index)
    const value = formatPrototypeDate(current)
    const month = current.getMonth() + 1
    const day = current.getDate()

    return {
      value,
      label: index === 0 ? '今天' : index === 1 ? '明天' : weekdays[current.getDay()],
      sub: `${month}月${day}日`,
    }
  })
}
