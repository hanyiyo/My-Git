import { test, expect } from '@playwright/test'

test('医生通知分待处理与普通通知，阅读和待办计数分开', async ({ page }) => {
  await page.goto('/prototype/doctor-home/notifications')
  await expect(page.getByText('未读 2')).toBeVisible()
  await expect(page.getByRole('button', { name: '待处理 1', exact: true })).toBeVisible()
  await expect(page.getByTestId('doctor-notice-doctor-notice-invitation')).toBeVisible()
  await page.getByTestId('doctor-notice-doctor-notice-invitation').getByRole('button', { name: /你收到一份替诊邀请/ }).click()
  await expect(page.getByRole('dialog', { name: '通知详情' })).toContainText('等待管理员复核')
  await page.keyboard.press('Escape')
  await expect(page.getByText('未读 1')).toBeVisible()
  await page.getByRole('navigation', { name: '通知分区' }).getByRole('button', { name: /普通通知/ }).click()
  await expect(page.getByText('停诊申请进度有更新')).toBeVisible()
})

test('替诊详情先核对后处理，并拦截已有排班的场次', async ({ page }) => {
  await page.goto('/prototype/doctor-home/applications')
  await page.getByRole('tab', { name: /替诊邀请/ }).click()
  await page.getByRole('button', { name: /2026-11-21.*替诊邀请/ }).click()
  await page.getByRole('button', { name: '接受替诊' }).click()
  await expect(page.getByRole('dialog', { name: '确认接受替诊' })).toContainText('等待管理员复核')
  await page.getByRole('dialog', { name: '确认接受替诊' }).getByRole('checkbox').check()
  await page.goto('/prototype/doctor-home/applications?invitation=substitution-conflict-001')
  await expect(page.getByRole('button', { name: '接受替诊' })).toBeDisabled()
  await expect(page.getByText('已有出诊安排')).toBeVisible()
})
