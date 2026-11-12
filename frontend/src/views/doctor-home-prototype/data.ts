// Fictional shell fixtures; reception-demo owns mutable clinical progress.
export const navigation = [
  { key: 'home', name: '首页', icon: 'home', caption: '今日工作概览' },
  { key: 'reception', name: '接诊工作台', icon: 'doctor', caption: '候诊与诊疗' },
  { key: 'schedules', name: '我的排班', icon: 'calendar', caption: '出诊安排' },
  { key: 'applications', name: '申请处理', icon: 'file', caption: '停诊与替诊' },
  { key: 'notifications', name: '站内通知', icon: 'bell', caption: '业务提醒' },
  { key: 'profile', name: '个人中心', icon: 'user', caption: '资料与账户' },
] as const
export type Section = typeof navigation[number]['key']
export type Scenario = 'current' | 'ongoing' | 'upcoming' | 'online' | 'finished'
export type DemoRole = 'doctor' | 'head'
export const scenarios: { key: Scenario; name: string; time: string }[] = [
  { key: 'current', name: '上午出诊中', time: '10:20' },
  { key: 'ongoing', name: '有未结束接诊', time: '10:20' },
  { key: 'upcoming', name: '等待下午出诊', time: '13:10' },
  { key: 'online', name: '下午线上出诊', time: '14:20' },
  { key: 'finished', name: '今日出诊结束', time: '18:30' },
]
export const schedules = [
  { id: 'am', period: '上午', time: '08:00–12:00', mode: '线下面诊', place: '门诊二楼 · 205 诊室', capacity: 20, registered: 14 },
  { id: 'pm', period: '下午', time: '14:00–18:00', mode: '线上问诊', place: '线上图文诊室', capacity: 16, registered: 9 },
]
export interface QueuePatient { id: string; number: string; name: string; age: number; sex: string; source: string; symptom: string; checkin: string; group: 'am' | 'pm' }
export const patients: QueuePatient[] = [
  { id: 'demo-001', number: 'A007', name: '林知夏', age: 21, sex: '女', source: '预约挂号', symptom: '咽部不适，伴轻微咳嗽两天', checkin: '09:42', group: 'am' },
  { id: 'demo-002', number: 'A008', name: '陈亦安', age: 22, sex: '男', source: '预约挂号', symptom: '餐后胃部不适', checkin: '09:46', group: 'am' },
  { id: 'demo-003', number: 'A009', name: '周予宁', age: 20, sex: '女', source: '预约挂号', symptom: '头晕、乏力，希望进一步咨询', checkin: '09:51', group: 'am' },
  { id: 'demo-004', number: 'B003', name: '许星野', age: 23, sex: '男', source: '当日挂号', symptom: '运动后踝部不适', checkin: '09:35', group: 'am' },
  { id: 'demo-005', number: 'B004', name: '宋清禾', age: 21, sex: '女', source: '当日挂号', symptom: '换季鼻塞、打喷嚏', checkin: '09:55', group: 'am' },
  { id: 'demo-006', number: 'B005', name: '江屿', age: 24, sex: '男', source: '当日挂号', symptom: '睡眠不佳，白天容易疲倦', checkin: '10:02', group: 'am' },
]
export const applications = [
  { id: 'invitation', kind: '替诊邀请', title: '陈予安医生邀请你替诊', detail: '11月16日 · 上午 · 线下面诊', status: '待你确认', actionable: true, headOnly: false, icon: 'doctor' },
  { id: 'mine', kind: '我的申请', title: '11月20日下午停诊申请', detail: '已提交，等待管理员复核', status: '审核中', actionable: false, headOnly: false, icon: 'file' },
  { id: 'department', kind: '科室审批', title: '林牧医生的停诊申请', detail: '11月18日 · 下午 · 线上问诊', status: '待你审批', actionable: true, headOnly: true, icon: 'shield' },
]
