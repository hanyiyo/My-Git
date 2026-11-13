import { createRouter, createWebHistory } from 'vue-router'
import LoginView from './views/LoginView.vue'
import HomeView from './views/HomeView.vue'
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
import LoginRegisterPrototypeView from './views/LoginRegisterPrototypeView.vue'
import AdminPharmacyView from './views/AdminPharmacyView.vue'
import AdminAccountsView from './views/AdminAccountsView.vue'
import { useSession } from './session'
export const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', redirect: '/login' }, { path: '/login', component: LoginView },
  { path: '/prototype/auth', component: LoginRegisterPrototypeView },
  ...(import.meta.env.DEV ? [
    { path: '/prototype/admin-home', component: () => import('./views/AdminHomePrototypeView.vue'), meta: { prototypeWorkspace: true } },
    { path: '/prototype/admin-orders', component: () => import('./views/AdminOrdersPrototypeView.vue'), meta: { prototypeWorkspace: true } },
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
  { path: '/admin/pharmacy', component: AdminPharmacyView, meta: { role: 'ADMIN' } },
  { path: '/admin/accounts', component: AdminAccountsView, meta: { role: 'ADMIN' } },
  { path: '/admin/settings', component: AdminAiView, meta: { role: 'ADMIN' } },
  { path: '/patient/preconsultation', redirect: '/assistant/business', meta: { role: 'PATIENT' } },
  { path: '/patient/catalog', component: PatientCatalogView, meta: { role: 'PATIENT' } },
  { path: '/patient/orders', component: PatientOrdersView, meta: { role: 'PATIENT' } },
  { path: '/patient/orders/:id/clinical', component: PatientClinicalView, meta: { role: 'PATIENT' } },
  { path: '/patient/substitutions', component: PatientSubstitutionView, meta: { role: 'PATIENT' } },
  { path: '/doctor/catalog', component: DoctorCatalogView, meta: { role: 'DOCTOR' } },
  { path: '/doctor/reception', component: DoctorReceptionView, meta: { role: 'DOCTOR' } },
  { path: '/doctor/clinical/:id', component: DoctorClinicalView, meta: { role: 'DOCTOR' } },
  ...['patient', 'doctor', 'admin'].map(role => ({ path: '/' + role, component: HomeView, meta: { role: role.toUpperCase() } }))
  ,{ path: '/notifications', component: HomeView, meta: { role: 'PATIENT' } }
  ,{ path: '/profile', component: HomeView, meta: { role: 'PATIENT' } }
] })
router.beforeEach(async to => {
  if (import.meta.env.DEV && (to.meta.prototypeWorkspace || to.path === '/prototype/auth')) return
  const session = useSession(); await session.restore()
  if (to.meta.role && !session.account) return '/login'
  if (to.meta.role && !session.account?.roles.includes(String(to.meta.role))) return '/' + session.account!.roles[0]!.toLowerCase()
})
