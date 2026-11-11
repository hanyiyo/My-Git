import { prototypeToday } from './prototype-date-options'

export type OrderStatus = 'PENDING_PAYMENT' | 'PENDING_VISIT' | 'WAITING' | 'MISSED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'EXPIRED'
export type OrderMode = '线上问诊' | '线下面诊'
export type OrderSource = '预约挂号' | '当日挂号'

export interface PrototypeOrder {
  id: string
  doctor: string
  title: string
  department: string
  specialty: string
  mode: OrderMode
  source: OrderSource
  status: OrderStatus
  date: string
  dateLabel: string
  time: string
  room: string
  fee: string
  credential: string
  checkinWindow: string
  checkinOpen?: boolean
  queueNumber?: string
  queuePosition?: string
  ahead?: number
  waitMinutes?: number
  roomAvailable?: boolean
  restoreUsed?: boolean
  reason?: string
  result?: string
}

export const prototypeOrders: PrototypeOrder[] = [
  {
    id: 'suspended-booking', doctor: '郭妤', title: '主治医师', department: '内科', specialty: '内科常见病咨询', mode: '线下面诊', source: '预约挂号', status: 'CANCELLED', date: '2026-11-11', dateLabel: '明天 · 11月11日', time: '14:00–18:00', room: '门诊二楼 · 205 诊室', fee: '¥12.00', credential: 'GH20261111001', checkinWindow: '13:30–17:45', checkinOpen: false, reason: '医生停诊，挂号已自动取消；挂号费 ¥12.00 模拟退款处理中。',
  },
  {
    id: 'today-offline-pending', doctor: '陈予安', title: '主治医师', department: '全科医学科', specialty: '常见不适与健康咨询', mode: '线下面诊', source: '预约挂号', status: 'PENDING_VISIT', date: prototypeToday, dateLabel: '今天 · 11月10日', time: '14:00–18:00', room: '门诊一楼 · 103 诊室', fee: '¥12.00', credential: 'GH20261110001', checkinWindow: '13:30–17:45', checkinOpen: true,
  },
  {
    id: 'today-online-waiting', doctor: '许知遥', title: '副主任医师', department: '内科', specialty: '呼吸消化 · 慢病管理', mode: '线上问诊', source: '当日挂号', status: 'WAITING', date: prototypeToday, dateLabel: '今天 · 11月10日', time: '09:00–11:30', room: '线上图文诊室', fee: '¥18.00', credential: 'GH20261110002', checkinWindow: '08:30–11:15', checkinOpen: true, queueNumber: 'B008', queuePosition: '第 4 位', ahead: 3, waitMinutes: 12, roomAvailable: true,
  },
  {
    id: 'today-online-missed', doctor: '周宁', title: '副主任医师', department: '内科', specialty: '慢病管理与复诊', mode: '线上问诊', source: '预约挂号', status: 'MISSED', date: prototypeToday, dateLabel: '今天 · 11月10日', time: '14:00–18:00', room: '线上图文诊室', fee: '¥18.00', credential: 'GH20261110003', checkinWindow: '13:30–17:45', queueNumber: 'A016', queuePosition: '已过号', ahead: 0, waitMinutes: 0, roomAvailable: false,
  },
  {
    id: 'future-offline-booking', doctor: '林牧', title: '住院医师', department: '全科医学科', specialty: '校园常见健康问题', mode: '线下面诊', source: '预约挂号', status: 'PENDING_VISIT', date: '2026-11-12', dateLabel: '周四 · 11月12日', time: '09:00–12:00', room: '门诊一楼 · 103 诊室', fee: '¥12.00', credential: 'GH20261112001', checkinWindow: '08:30–11:45', checkinOpen: false,
  },
  {
    id: 'pending-payment', doctor: '姜悦', title: '主治医师', department: '皮肤科', specialty: '常见皮肤问题咨询', mode: '线上问诊', source: '预约挂号', status: 'PENDING_PAYMENT', date: '2026-11-15', dateLabel: '周日 · 11月15日', time: '14:00–18:00', room: '线上图文诊室', fee: '¥16.00', credential: '待付款', checkinWindow: '13:30–17:45', checkinOpen: false,
  },
  {
    id: 'in-progress-offline', doctor: '马凌', title: '主治医师', department: '内科', specialty: '呼吸与消化系统', mode: '线下面诊', source: '预约挂号', status: 'IN_PROGRESS', date: prototypeToday, dateLabel: '今天 · 11月10日', time: '08:00–12:00', room: '门诊二楼 · 205 诊室', fee: '¥12.00', credential: 'GH20261110004', checkinWindow: '07:30–11:45', checkinOpen: false, queueNumber: 'C003', queuePosition: '医生接诊中', ahead: 0, waitMinutes: 0,
  },
  {
    id: 'completed', doctor: '宋欣', title: '主治医师', department: '口腔科', specialty: '牙齿保健与口腔检查', mode: '线下面诊', source: '预约挂号', status: 'COMPLETED', date: '2026-11-06', dateLabel: '11月6日', time: '09:00–12:00', room: '门诊一楼 · 108 诊室', fee: '¥20.00', credential: 'GH20261106001', checkinWindow: '08:30–11:45', checkinOpen: false, result: '本次口腔检查已完成，医生建议保持规律清洁并按需复诊。',
  },
  {
    id: 'cancelled', doctor: '郭妤', title: '主治医师', department: '内科', specialty: '内科常见病咨询', mode: '线下面诊', source: '预约挂号', status: 'CANCELLED', date: '2026-11-04', dateLabel: '11月4日', time: '14:00–18:00', room: '门诊二楼 · 205 诊室', fee: '¥12.00', credential: 'GH20261104001', checkinWindow: '13:30–17:45', checkinOpen: false, reason: '患者于 11 月 3 日主动取消挂号。',
  },
  {
    id: 'expired', doctor: '白璐', title: '住院医师', department: '口腔科', specialty: '牙周与常见口腔问题', mode: '线下面诊', source: '预约挂号', status: 'EXPIRED', date: '2026-11-02', dateLabel: '11月2日', time: '09:00–12:00', room: '门诊一楼 · 108 诊室', fee: '¥20.00', credential: 'GH20261102001', checkinWindow: '08:30–11:45', checkinOpen: false, reason: '未在签到窗口内完成签到，本次挂号已过期。',
  },
]

export const orderStatusTabs = ['全部', '待付款', '待就诊', '就诊中', '已完成', '已取消', '已过期'] as const
export type OrderTab = typeof orderStatusTabs[number]

export function statusForTab(order: PrototypeOrder): OrderTab {
  if (order.status === 'PENDING_PAYMENT') return '待付款'
  if (['PENDING_VISIT', 'WAITING', 'MISSED'].includes(order.status)) return '待就诊'
  if (order.status === 'IN_PROGRESS') return '就诊中'
  if (order.status === 'COMPLETED') return '已完成'
  if (order.status === 'CANCELLED') return '已取消'
  return '已过期'
}
