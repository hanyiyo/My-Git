export type DepartmentMode = 'ONLINE' | 'OFFLINE'

export interface PrototypeDepartmentDoctor {
  id: string
  name: string
  title: string
  specialty: string
  today: boolean
  mode: DepartmentMode | null
  time: string
  place: string
  slots: number | null
}

export const departmentProfiles: Record<string, { description: string; specialties: string[]; hint: string }> = {
  '全科医学科': { description: '覆盖常见不适、健康咨询与初次就诊导诊，适合还不确定挂什么科的情况。', specialties: ['常见不适', '健康咨询', '初次导诊'], hint: '首次就诊或不确定科室时，可以先从全科开始。' },
  '内科': { description: '关注呼吸、消化和慢病管理等常见内科问题，提供线上与线下就诊安排。', specialties: ['呼吸系统', '消化系统', '慢病管理'], hint: '请按预约时段签到，带好既往检查资料。' },
  '口腔科': { description: '提供牙齿保健、口腔检查和常见口腔问题咨询，按预约时段到院就诊。', specialties: ['口腔检查', '牙齿保健', '牙周咨询'], hint: '线下就诊请提前 10 分钟到达门诊一楼。' },
  '皮肤科': { description: '针对常见皮肤问题提供专业咨询，具体诊疗与检查以医生判断为准。', specialties: ['皮肤咨询', '过敏问题', '日常护理'], hint: '如有既往用药或检查结果，建议一并带到诊室。' },
}

export const departmentDoctors: Record<string, PrototypeDepartmentDoctor[]> = {
  '全科医学科': [
    { id: 'chen-yuan-an', name: '陈予安', title: '主治医师', specialty: '常见不适与健康咨询', today: true, mode: 'OFFLINE', time: '今天 14:00–18:00', place: '门诊一楼 · 103 诊室', slots: 8 },
    { id: 'xu-zhi-yao', name: '许知遥', title: '主治医师', specialty: '初次就诊与综合导诊', today: true, mode: 'ONLINE', time: '今天 09:00–12:00', place: '线上图文问诊', slots: 5 },
    { id: 'lin-mu', name: '林牧', title: '住院医师', specialty: '校园常见健康问题', today: false, mode: null, time: '11 月 12 日 09:00–12:00', place: '线下门诊', slots: null },
    { id: 'he-qing', name: '何清', title: '主治医师', specialty: '慢病随访与健康管理', today: false, mode: null, time: '11 月 13 日 14:00–18:00', place: '线上图文问诊', slots: null },
  ],
  '内科': [
    { id: 'ma-ling', name: '马凌', title: '主治医师', specialty: '呼吸与消化系统', today: true, mode: 'OFFLINE', time: '今天 08:00–12:00', place: '门诊二楼 · 205 诊室', slots: 6 },
    { id: 'zhou-ning', name: '周宁', title: '副主任医师', specialty: '慢病管理与复诊', today: true, mode: 'ONLINE', time: '今天 14:00–18:00', place: '线上图文问诊', slots: 4 },
    { id: 'guo-yu', name: '郭妤', title: '主治医师', specialty: '内科常见病咨询', today: false, mode: null, time: '11 月 12 日 14:00–18:00', place: '线下门诊', slots: null },
  ],
  '口腔科': [
    { id: 'song-xin', name: '宋欣', title: '主治医师', specialty: '牙齿保健与口腔检查', today: true, mode: 'OFFLINE', time: '今天 09:00–12:00', place: '门诊一楼 · 108 诊室', slots: 3 },
    { id: 'bai-lu', name: '白璐', title: '住院医师', specialty: '牙周与常见口腔问题', today: false, mode: null, time: '11 月 14 日 09:00–12:00', place: '线下门诊', slots: null },
  ],
  '皮肤科': [
    { id: 'jiang-yue', name: '姜悦', title: '主治医师', specialty: '常见皮肤问题咨询', today: true, mode: 'ONLINE', time: '今天 10:00–12:00', place: '线上图文问诊', slots: 7 },
    { id: 'luo-an', name: '罗安', title: '主治医师', specialty: '过敏问题与日常护理', today: false, mode: null, time: '11 月 15 日 14:00–18:00', place: '线下门诊', slots: null },
  ],
}
