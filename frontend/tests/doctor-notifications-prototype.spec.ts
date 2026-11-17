import { test, expect } from '@playwright/test'

test('申请页直接展示替诊待办，详情可切换类型且主导航回到列表', async ({ page }) => {
  await page.goto('/prototype/doctor-home/applications')
  const main = page.getByRole('main')
  const invitationTab = page.getByRole('tab', { name: /替诊邀请/ })
  await expect(invitationTab).toHaveAttribute('aria-selected', 'true')
  await expect(invitationTab).toHaveCSS('border-bottom-color', 'rgb(77, 132, 180)')
  await expect(main.getByText('申请处理', { exact: true })).toHaveCount(1)
  await page.getByRole('button', { name: /2026-11-21.*替诊邀请/ }).click()
  await expect(invitationTab).toBeVisible()
  await expect(page.getByRole('button', { name: '接受替诊', exact: true })).toBeVisible()
  await page.reload()
  await page.getByRole('button', { name: '返回申请列表', exact: true }).click()
  await expect(invitationTab).toHaveAttribute('aria-selected', 'true')
  await page.getByRole('tab', { name: /我的停诊申请/ }).click()
  await page.getByRole('region', { name: '我的停诊申请' }).locator('.ds-request-row').first().click()
  await expect(invitationTab).toBeVisible()
  await page.getByRole('navigation', { name: '医生端导航' }).getByRole('button', { name: '申请处理', exact: true }).click()
  await expect(page.getByRole('region', { name: '本场停诊申请' })).toHaveCount(0)
  await invitationTab.click()
  await page.reload()
  await expect(invitationTab).toHaveAttribute('aria-selected', 'true')
  await invitationTab.focus()
  await page.keyboard.press('ArrowLeft')
  await expect(page.getByRole('tab', { name: /我的停诊申请/ })).toBeFocused()
  await expect(page.getByRole('tab', { name: /我的停诊申请/ })).toHaveAttribute('aria-selected', 'true')
  await page.keyboard.press('End')
  await expect(invitationTab).toBeFocused()
  await expect(invitationTab).toHaveAttribute('aria-selected', 'true')
  for (const width of [1440, 375]) {
    await page.setViewportSize({ width, height: 850 })
    await expect(invitationTab).toBeInViewport()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  }
})

test('通知页保留单一标题并将未读数量整合到筛选区', async ({ page }) => {
  await page.goto('/prototype/doctor-home/notifications')
  await expect(page.getByRole('main').getByText('站内通知', { exact: true })).toHaveCount(1)
  await expect(page.getByText('未读 2', { exact: true })).toBeVisible()
  for (const width of [1440, 375]) {
    await page.setViewportSize({ width, height: 850 })
    await expect(page.getByRole('button', { name: '待处理 1', exact: true })).toBeInViewport()
    const row = page.getByTestId('doctor-notice-doctor-notice-invitation')
    const summary = (await row.locator('.dn-copy small').boundingBox())!
    const actions = (await row.locator('.dn-row-actions').boundingBox())!
    expect(actions.y).toBeGreaterThanOrEqual(summary.y + summary.height)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  }
})

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
