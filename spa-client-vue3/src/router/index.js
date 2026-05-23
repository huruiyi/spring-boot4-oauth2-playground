import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import CallbackView from '../views/CallbackView.vue'
import ProfileView from '../views/ProfileView.vue'
import { useOAuth2Store } from '../stores/oauth2.js'

const routes = [
  { path: '/', name: 'home', component: HomeView, meta: { public: true } },
  { path: '/callback', name: 'callback', component: CallbackView, meta: { public: true } },
  { path: '/profile', name: 'profile', component: ProfileView, meta: { auth: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const store = useOAuth2Store()
  store.restore()
  if (to.meta.auth && !store.isAuthenticated) {
    return { name: 'home' }
  }
  if (to.meta.public && to.path === '/' && store.isAuthenticated) {
    return { name: 'profile' }
  }
})

export default router
