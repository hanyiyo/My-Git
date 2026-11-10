export type HomeAction = 'online' | 'offline' | 'checkin' | 'payment' | 'report' | 'departments' | 'doctors' | 'registration' | 'orders' | 'notifications' | 'profile' | 'ai'
export const services: { action: HomeAction; icon: string; title: string; detail: string; color: string }[] = [
  { action: 'online', icon: 'chat', title: '线上问诊预约', detail: '预约医生，图文沟通', color: 'blue' },
  { action: 'offline', icon: 'hospital', title: '线下门诊挂号', detail: '选择时段，便捷到院', color: 'teal' },
  { action: 'checkin', icon: 'scan', title: '快速签到', detail: '签到入队，安心候诊', color: 'purple' },
  { action: 'payment', icon: 'wallet', title: '缴费中心', detail: '挂号费、药费一处办理', color: 'orange' },
  { action: 'report', icon: 'file', title: '查看报告', detail: '查看已完成就诊与病历', color: 'blue' },
  { action: 'departments', icon: 'grid', title: '科室信息', detail: '了解科室，找到方向', color: 'teal' },
  { action: 'doctors', icon: 'doctor', title: '医生列表', detail: '查看医生与出诊安排', color: 'purple' },
]
export const departments = [
  { name: '全科医学科', icon: 'cross', detail: '常见不适 · 综合诊疗', count: 6 },
  { name: '内科', icon: 'heart', detail: '呼吸消化 · 慢病管理', count: 8 },
  { name: '口腔科', icon: 'smile', detail: '口腔检查 · 牙齿保健', count: 3 },
  { name: '皮肤科', icon: 'leaf', detail: '皮肤问题 · 专业咨询', count: 4 },
]
export const notices = [
  { tag: '门诊公告', title: '秋冬季门诊就诊温馨提示', date: '11.10', body: '请按预约时段就诊，携带必要的既往检查资料；线上问诊可在签到后进入诊室留言。' },
  { tag: '健康科普', title: '换季守护，从日常小习惯开始', date: '11.09', body: '规律作息、均衡饮食。如有持续不适，请及时向医生咨询。' },
  { tag: '服务指南', title: '线上问诊与到院就诊，一文读懂', date: '11.08', body: '选择医生与时段，完成模拟支付，在签到窗口内签到后进入候诊队列。' },
]
export const banners = [
  { label: '校园健康 · 一站守护', greeting: '让每一次就医，都更从容一点。', detail: '线上图文问诊，线下安心就诊。\n从挂号到病历，把健康服务放在你身边。' },
  { label: '就诊指南 · 提前了解', greeting: '先预约，再签到，就诊安排心中有数。', detail: '签到窗口：开诊前 30 分钟至结束前 15 分钟。\n完成缴费与签到后，即可进入候诊队列。' },
  { label: '医院公告 · 温馨提醒', greeting: '关注门诊动态，安排好你的就诊时间。', detail: '停诊、替诊与签到提醒，可在站内通知查看。\n服务有变化，我们会及时告诉你。' },
]
