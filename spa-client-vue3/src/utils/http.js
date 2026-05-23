import axios from 'axios'
import { useOAuth2Store } from '../stores/oauth2.js'

const AUTH_SERVER = 'http://localhost:9000'
const RESOURCE_SERVER = 'http://localhost:9001'

const authServerClient = axios.create({
  baseURL: AUTH_SERVER,
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
})

const resourceServerClient = axios.create({
  baseURL: RESOURCE_SERVER
})

resourceServerClient.interceptors.request.use((config) => {
  const store = useOAuth2Store()
  if (store.accessToken) {
    config.headers.Authorization = `Bearer ${store.accessToken}`
  }
  return config
})

resourceServerClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const store = useOAuth2Store()
      store.clear()
    }
    return Promise.reject(error)
  }
)

export { authServerClient, resourceServerClient, AUTH_SERVER, RESOURCE_SERVER }
