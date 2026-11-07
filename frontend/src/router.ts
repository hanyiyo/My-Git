import { createRouter, createWebHistory } from 'vue-router'
import LoginView from './views/LoginView.vue'
import HomeView from './views/HomeView.vue'
import AdminCatalogView from './views/AdminCatalogView.vue'
import PatientCatalogView from './views/PatientCatalogView.vue'
import DoctorCatalogView from './views/DoctorCatalogView.vue'
import PatientOrdersView from './views/PatientOrdersView.vue'
import { useSession } from './session'
export const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', redirect: '/login' }, { path: '/login', component: LoginView },
  { path: '/admin/catalog', component: AdminCatalogView, meta: { role: 'ADMIN' } },
  { path: '/patient/catalog', component: PatientCatalogView, meta: { role: 'PATIENT' } },
  { path: '/patient/orders', component: PatientOrdersView, meta: { role: 'PATIENT' } },
  { path: '/doctor/catalog', component: DoctorCatalogView, meta: { role: 'DOCTOR' } },
  ...['patient', 'doctor', 'admin'].map(role => ({ path: '/' + role, component: HomeView, meta: { role: role.toUpperCase() } }))
] })
router.beforeEach(async to => {
  const session = useSession(); await session.restore()
  if (to.meta.role && !session.account) return '/login'
  if (to.meta.role && !session.account?.roles.includes(String(to.meta.role))) return '/' + session.account!.roles[0]!.toLowerCase()
})
