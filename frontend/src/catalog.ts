export interface Hospital { name: string; address: string; description: string }
export interface Department { id: string; name: string; description: string; active: boolean }
export interface Doctor { id: string; displayName: string; title: string; biography: string; credentialStatus: string; enabled: boolean; departmentId: string | null; departmentName: string | null }
export interface Schedule { id: string; doctorId: string; doctorName: string; departmentId: string; departmentName: string; clinicDate: string; session: string; serviceMode: string; startsAt: number; endsAt: number; zoneId: string; capacity: number; feeMinor: number; state: string; availableSlots: number; advanceAvailable: boolean; walkInAvailable: boolean }
export const credentialLabels: Record<string, string> = { PENDING: '待审核', CERTIFIED: '已认证', REJECTED: '未通过' }
