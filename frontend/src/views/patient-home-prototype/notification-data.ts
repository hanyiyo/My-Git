import { notices } from './data'

export type NotificationTab = 'system' | 'news'
export interface PrototypeNotice {
  id: string
  tab: NotificationTab
  title: string
  summary: string
  body: string[]
  source: string
  category: string
  icon: string
  createdAt: string
  read: boolean
  deleted?: boolean
  priority?: 'high'
  importantUntil?: string
  orderId?: string
}

// shortcut: a fixed hospital clock keeps prototype reminders reproducible; use server time when connecting real notifications.
export const notificationNow = '2026-11-10T13:35:00+08:00'
export function createPrototypeNotices(): PrototypeNotice[] {
  return [
    { id: 'suspension', tab: 'system', title: '门诊停诊，请留意就诊安排', summary: '郭妤医生 11 月 11 日下午门诊停诊，关联挂号已取消，退款正在处理。', body: ['因门诊安排调整，郭妤医生 11 月 11 日 14:00–18:00 的内科门诊已停诊，你的关联挂号已自动取消。', '挂号费 ¥12.00 的模拟退款正在处理中。订单取消不代表退款已经到账，请以订单中的退款状态为准。', '如仍有就诊需要，可从“立即挂号”另行选择合适的医生和时段。'], source: '门诊服务中心', category: '停诊通知', icon: 'warning', createdAt: '2026-11-10T13:20:00+08:00', read: false, priority: 'high', importantUntil: '2026-11-11T18:00:00+08:00', orderId: 'suspended-booking' },
    { id: 'substitution', tab: 'system', title: '预约医生已调整，原预约继续有效', summary: '11 月 12 日全科门诊由林牧医生接诊，就诊时段与预约名额保持不变。', body: ['你预约的 11 月 12 日全科医学科门诊已由陈予安医生调整为林牧医生接诊，原预约已完成迁移。', '原预约继续有效，无需确认接受替诊。请按原时段就诊，并在开放窗口内完成签到。', '若需取消，请在“我的挂号”中按订单当前规则操作；阅读或删除本通知不会取消挂号。'], source: '门诊服务中心', category: '替诊通知', icon: 'doctor', createdAt: '2026-11-10T12:50:00+08:00', read: false, priority: 'high', importantUntil: '2026-11-12T12:00:00+08:00', orderId: 'future-offline-booking' },
    { id: 'checkin', tab: 'system', title: '下午门诊已开放签到', summary: '陈予安医生的门诊已开放签到，请在 17:45 前完成。', body: ['你预约的全科医学科下午门诊已开放签到。签到窗口为 13:30 至 17:45 前，不包含截止时刻。', '请前往“我的挂号”完成签到，签到后进入候诊队列；线下面诊请留意门诊一楼 103 诊室的叫号信息。'], source: '就诊服务', category: '签到提醒', icon: 'scan', createdAt: '2026-11-10T13:30:00+08:00', read: false, priority: 'high', importantUntil: '2026-11-10T17:45:00+08:00', orderId: 'today-offline-pending' },
    { id: 'payment', tab: 'system', title: '你的挂号待付款', summary: '姜悦医生的线上问诊挂号已提交，请前往订单查看付款状态。', body: ['已提交皮肤科姜悦医生的线上问诊挂号。支付前为临时占号，尚未确认预约。', '请在订单中查看当前支付截止时间与费用。若占号已失效，请以订单当前状态为准。'], source: '挂号服务', category: '缴费提醒', icon: 'wallet', createdAt: '2026-11-10T13:10:00+08:00', read: false, orderId: 'pending-payment' },
    { id: 'booking', tab: 'system', title: '挂号成功，就诊安排已就绪', summary: '陈予安 · 全科医学科 · 今天 14:00–18:00 · 线下面诊。', body: ['挂号费模拟支付已完成，本次预约已确认。医生为陈予安，就诊地点为门诊一楼 103 诊室。', '请按就诊时段安排出行，并在签到窗口内完成签到。预约凭证和当前状态可在订单中查看。'], source: '挂号服务', category: '挂号成功', icon: 'calendar', createdAt: '2026-11-10T09:10:00+08:00', read: true, orderId: 'today-offline-pending' },
    { id: 'refund', tab: 'system', title: '退款处理完成', summary: '已取消挂号的挂号费 ¥12.00 已完成模拟退款。', body: ['你于 11 月 3 日取消的内科挂号已完成模拟退款，金额为 ¥12.00。', '本条为历史处理结果，详细状态以关联订单为准。'], source: '费用服务', category: '退款结果', icon: 'wallet', createdAt: '2026-11-09T16:40:00+08:00', read: false, orderId: 'cancelled' },
    { id: 'called', tab: 'system', title: '医生已叫号，请留意接诊', summary: '马凌医生已叫号，请按线下门诊指引前往诊室。', body: ['内科马凌医生已叫到你的就诊号码 C003，请留意门诊二楼 205 诊室的安排。', '本条记录的是叫号时的信息，当前接诊状态请查看关联订单。'], source: '候诊服务', category: '叫号提醒', icon: 'bell', createdAt: '2026-11-10T11:15:00+08:00', read: true, orderId: 'in-progress-offline' },
    { id: 'missed', tab: 'system', title: '本次已过号，可查看恢复排队规则', summary: '周宁医生的线上门诊已过号，恢复排队需在门诊有效时段内申请。', body: ['医生已将本次叫号标记为过号。你可在门诊时段内从“我的挂号”主动申请一次恢复排队。', '是否仍可恢复、是否已使用恢复次数，以订单当前状态为准。通知页不自动恢复排队。'], source: '候诊服务', category: '过号提醒', icon: 'clock', createdAt: '2026-11-10T13:00:00+08:00', read: true, orderId: 'today-online-missed' },
    { id: 'cancellation', tab: 'system', title: '挂号已取消', summary: '你已主动取消内科挂号，费用处理结果请查看关联订单。', body: ['已收到你主动取消挂号的操作，原预约凭证已失效。', '取消预约与退款分别记录，后续退款结果将另行通知。'], source: '挂号服务', category: '取消通知', icon: 'file', createdAt: '2026-11-03T10:00:00+08:00', read: true, orderId: 'cancelled' },
    { id: 'security', tab: 'system', title: '账户安全温馨提示', summary: '请妥善保管账户信息，勿向他人提供密码和验证码。', body: ['医院工作人员不会通过站内通知向你索取登录密码或验证码。', '如需修改密码，请前往个人中心；请勿在公开渠道分享个人就诊信息。'], source: '账户服务', category: '账户安全', icon: 'shield', createdAt: '2026-11-08T10:00:00+08:00', read: true },
    { id: 'old-checkin', tab: 'system', title: '历史签到提醒', summary: '11 月 6 日口腔科门诊签到提醒，该次就诊现已完成。', body: ['这是一条历史签到提醒，对应就诊已完成。', '可查看关联订单中的就诊结果，无需再次签到。'], source: '就诊服务', category: '签到提醒', icon: 'scan', createdAt: '2026-11-06T08:30:00+08:00', read: true, priority: 'high', importantUntil: '2026-11-06T11:45:00+08:00', orderId: 'completed' },
    ...notices.map((item, index): PrototypeNotice => ({ id: `news-${index + 1}`, tab: 'news', title: item.title, summary: item.body, body: [item.body, '请根据个人就诊安排合理规划时间，具体服务信息以当日门诊安排为准。'], source: '校医院', category: item.tag, icon: 'file', createdAt: `2026-11-${String(10 - index).padStart(2, '0')}T08:00:00+08:00`, read: index === 0 })),
    { id: 'news-adjustment', tab: 'news', title: '周末门诊服务时间调整公告', summary: '本周末部分门诊服务时段调整，请出发前核对排班。', body: ['为配合门诊服务安排，本周末部分科室的出诊时段有所调整。', '已挂号患者请以个人订单中的通知为准；未挂号患者可在医生列表或立即挂号页面查询可用排班。'], source: '校医院门诊办公室', category: '门诊调整', icon: 'hospital', createdAt: '2026-11-09T10:30:00+08:00', read: false },
    { id: 'news-health', tab: 'news', title: '校园健康同行，秋冬健康咨询活动', summary: '关注规律作息与日常健康，了解校园健康服务。', body: ['校医院将开展秋冬季健康知识交流与咨询活动，欢迎关注后续活动安排。', '健康活动资讯不能替代医生接诊，如有持续不适请及时就医。'], source: '校医院', category: '健康活动', icon: 'heart', createdAt: '2026-11-07T09:00:00+08:00', read: false },
  ]
}
