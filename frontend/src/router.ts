import { createRouter, createWebHistory } from 'vue-router'
import LoginView from './views/LoginView.vue'
import HomeView from './views/HomeView.vue'
import PatientHomeView from './views/PatientHomeView.vue'
import DoctorGovernanceView from './views/DoctorGovernanceView.vue'
import AdminGovernanceView from './views/AdminGovernanceView.vue'
import AdminAiView from './views/AdminAiView.vue'
import AdminMedicalView from './views/AdminMedicalView.vue'
import AdminCatalogView from './views/AdminCatalogView.vue'
import PatientCatalogView from './views/PatientCatalogView.vue'
import DoctorCatalogView from './views/DoctorCatalogView.vue'
import PatientOrdersView from './views/PatientOrdersView.vue'
import DoctorReceptionView from './views/DoctorReceptionView.vue'
import PatientClinicalView from './views/PatientClinicalView.vue'
import DoctorClinicalView from './views/DoctorClinicalView.vue'
import BusinessAssistantView from './views/BusinessAssistantView.vue'
import PatientSubstitutionView from './views/PatientSubstitutionView.vue'
import AdminPharmacyView from './views/AdminPharmacyView.vue'
import AdminAccountsView from './views/AdminAccountsView.vue'
import DoctorWorkspaceView from './views/DoctorWorkspaceView.vue'
import DoctorHomeView from './views/DoctorHomeView.vue'
import DoctorModulePlaceholderView from './views/DoctorModulePlaceholderView.vue'
import DoctorNotificationsView from './views/DoctorNotificationsView.vue'
import DoctorProfileView from './views/DoctorProfileView.vue'
import { useSession } from './session'
export const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', redirect: '/login' }, { path: '/login', component: LoginView },
  ...(import.meta.env.DEV ? [
    { path: '/prototype/auth', component: () => import('./views/LoginRegisterPrototypeView.vue'), meta: { prototypeAuth: true } },
    { path: '/prototype/admin-home', component: () => import('./views/AdminHomePrototypeView.vue'), meta: { prototypeWorkspace: true } },
    { path: '/prototype/admin-orders', component: () => import('./views/AdminOrdersPrototypeView.vue'), meta: { prototypeWorkspace: true } },
    { path: '/prototype/admin-schedules', component: () => import('./views/AdminSchedulePrototypeView.vue'), meta: { prototypeWorkspace: true } },
    { path: '/prototype/admin-slots', component: () => import('./views/AdminSlotsPrototypeView.vue'), meta: { prototypeWorkspace: true } },
    { path: '/prototype/admin-accounts', redirect: '/prototype/admin-patients', meta: { prototypeWorkspace: true } },
    { path: '/prototype/admin-patients', component: () => import('./views/AdminResourcePrototypeView.vue'), meta: { prototypeWorkspace: true } },
    { path: '/prototype/admin-doctors', component: () => import('./views/AdminResourcePrototypeView.vue'), meta: { prototypeWorkspace: true } },
    { path: '/prototype/admin-departments', component: () => import('./views/AdminResourcePrototypeView.vue'), meta: { prototypeWorkspace: true } },
    { path: '/prototype/doctor-home/:section?', component: () => import('./views/DoctorHomePrototypeView.vue'), meta: { prototypeWorkspace: true } },
    { path: '/prototype/patient-home', component: () => import('./views/PatientHomePrototypeView.vue'), meta: { prototypeWorkspace: true } },
    { path: '/prototype/patient-home/departments', component: () => import('./views/PatientHomePrototypeView.vue'), meta: { prototypeWorkspace: true } },
    { path: '/prototype/patient-home/orders', component: () => import('./views/PatientHomePrototypeView.vue'), meta: { prototypeWorkspace: true } },
    { path: '/prototype/patient-home/consultation', component: () => import('./views/PatientHomePrototypeView.vue'), meta: { prototypeWorkspace: true } },
    { path: '/prototype/patient-home/notifications', component: () => import('./views/PatientHomePrototypeView.vue'), meta: { prototypeWorkspace: true } },
    { path: '/prototype/patient-home/profile', component: () => import('./views/PatientHomePrototypeView.vue'), meta: { prototypeWorkspace: true } },
  ] : []),
  { path: '/assistant/business', component: BusinessAssistantView },
  { path: '/admin/governance', component: AdminGovernanceView, meta: { role: 'ADMIN' } },
  { path: '/doctor/governance', component: DoctorGovernanceView, meta: { role: 'DOCTOR' } },
  { path: '/admin/ai', component: AdminAiView, meta: { role: 'ADMIN' } },
  { path: '/admin/medical', component: AdminMedicalView, meta: { role: 'ADMIN' } },
  { path: '/admin/catalog', component: AdminCatalogView, meta: { role: 'ADMIN' } },
  { path: '/admin/pharmacy', component: AdminPharmacyView, meta: { role: 'ADMIN', adminWorkspace: true } },
  { path: '/admin/accounts', component: AdminAccountsView, meta: { role: 'ADMIN' } },
  { path: '/doctor', component: DoctorWorkspaceView, meta: { role: 'DOCTOR', doctorWorkspace: true }, children: [
    { path: '', component: DoctorHomeView },
    { path: 'catalog', component: DoctorCatalogView },
    { path: 'reception', component: DoctorReceptionView },
    { path: 'clinical/:id', component: DoctorClinicalView },
    { path: 'governance', component: DoctorGovernanceView },
    { path: 'notifications', component: DoctorNotificationsView },
    { path: 'profile', component: DoctorProfileView },
  ] },
  { path: '/admin/settings', component: AdminAiView, meta: { role: 'ADMIN' } },
  { path: '/patient/preconsultation', redirect: '/assistant/business', meta: { role: 'PATIENT' } },
  { path: '/patient/catalog', component: PatientCatalogView, meta: { role: 'PATIENT' } },
  { path: '/patient/orders', component: PatientOrdersView, meta: { role: 'PATIENT' } },
  { path: '/patient/orders/:id/clinical', component: PatientClinicalView, meta: { role: 'PATIENT' } },
  { path: '/patient/substitutions', component: PatientSubstitutionView, meta: { role: 'PATIENT' } },
  { path: '/patient', component: PatientHomeView, meta: { role: 'PATIENT' } },
  { path: '/admin', component: HomeView, meta: { role: 'ADMIN' } }
  ,{ path: '/notifications', component: HomeView, meta: { role: 'PATIENT' } }
  ,{ path: '/profile', component: HomeView, meta: { role: 'PATIENT' } }
] })
router.beforeEach(async to => {
  if (import.meta.env.DEV && (to.meta.prototypeWorkspace || to.meta.prototypeAuth)) return
  const session = useSession(); await session.restore()
  if (to.meta.role && !session.account) return '/login'
  if (to.meta.role && !session.account?.roles.includes(String(to.meta.role))) return '/' + session.account!.roles[0]!.toLowerCase()
})
