import { createRouter, createWebHistory } from 'vue-router'

// 嵌入主应用时以主应用路由前缀为 base（由主应用 props 下发），独立运行时使用根路径
const embedded = !!window.__POWERED_BY_WUJIE__
const base = embedded ? window.$wujie?.props?.urlPrefix || '/sub/mp' : '/'

const router = createRouter({
  history: createWebHistory(base),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', component: () => import('../views/Dashboard.vue') },
    { path: '/account', component: () => import('../views/AccountList.vue') },
    { path: '/menu', component: () => import('../views/MenuEdit.vue') },
    { path: '/fan', component: () => import('../views/FanList.vue') },
    { path: '/message', component: () => import('../views/MessageList.vue') },
    { path: '/rule', component: () => import('../views/RuleList.vue') }
  ]
})

export default router
