import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      children: [
        {
          path: '',
          redirect: '/questions',
        },
        {
          path: 'reviews',
          name: 'daily-review',
          component: () => import('../views/DailyReviewView.vue'),
          meta: { title: '每日复习' },
        },
        {
          path: 'questions',
          name: 'question-list',
          component: () => import('../views/QuestionListView.vue'),
          meta: { title: '错题管理' },
        },
        {
          path: 'questions/new',
          name: 'question-create',
          component: () => import('../views/QuestionCreateView.vue'),
          meta: { title: '录入错题' },
        },
        {
          path: 'questions/:id',
          name: 'question-detail',
          component: () => import('../views/QuestionDetailView.vue'),
          meta: { title: '错题详情' },
        },
        {
          path: 'questions/:id/edit',
          name: 'question-edit',
          component: () => import('../views/QuestionEditView.vue'),
          meta: { title: '修改错题' },
        },
        {
          path: 'knowledge-points',
          name: 'knowledge-point-management',
          component: () => import('../views/KnowledgePointManagementView.vue'),
          meta: { title: '知识点管理' },
        },
        {
          path: ':pathMatch(.*)*',
          name: 'not-found',
          component: () => import('../views/NotFoundView.vue'),
          meta: { title: '页面不存在' },
        },
      ],
    },
  ],
})

router.afterEach((route) => {
  const title = typeof route.meta.title === 'string' ? route.meta.title : ''
  document.title = title
    ? `${title} · 错题整理与滚动复习`
    : '错题整理与滚动复习'
})

export default router
