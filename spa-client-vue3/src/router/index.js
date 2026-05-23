import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import CallbackView from '../views/CallbackView.vue'
import ProfileView from '../views/ProfileView.vue'

const routes = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/callback', name: 'callback', component: CallbackView },
  { path: '/profile', name: 'profile', component: ProfileView }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.path === '/profile') {
    const token = sessionStorage.getItem('access_token')
    if (!token) return { name: 'home' }
  }
})

export default router
