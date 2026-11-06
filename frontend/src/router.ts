import { createRouter, createWebHistory } from 'vue-router'
import LoginView from './views/LoginView.vue'
import HomeView from './views/HomeView.vue'
import { useSession } from './session'
export const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', redirect: '/login' }, { path: '/login', component: LoginView },
  ...['patient', 'doctor', 'admin'].map(role => ({ path: '/' + role, component: HomeView, meta: { role: role.toUpperCase() } }))
] })
router.beforeEach(async to => {
  const session = useSession(); await session.restore()
  if (to.meta.role && !session.account) return '/login'
  if (to.meta.role && !session.account?.roles.includes(String(to.meta.role))) return '/' + session.account!.roles[0]!.toLowerCase()
})
