import { scenarios, type DemoRole, type Scenario } from './data'

export const demoSettingsKey = 'medical-doctor-home-demo-v1'

// Development-only fictional presentation options; authentication stays in the session module.
export function saveDemoSettings(settings: { scenario: Scenario; role: DemoRole }) {
  if (!import.meta.env.DEV) return
  const scenario = scenarios.find(item => item.key === settings.scenario)?.key ?? 'current'
  const role = settings.role === 'head' ? 'head' : 'doctor'
  localStorage.setItem(demoSettingsKey, JSON.stringify({ scenario, role }))
}
