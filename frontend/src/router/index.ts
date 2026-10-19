import { createWebHistory, type RouterHistory, createRouter, type RouteRecordRaw } from 'vue-router'
import RoutePlaceholder from '@/views/RoutePlaceholder.vue'
import HomeView from '@/views/home/HomeView.vue'
import LoginView from '@/views/auth/LoginView.vue'
import PasswordLoginView from '@/views/auth/PasswordLoginView.vue'
import RegisterView from '@/views/auth/RegisterView.vue'
import ShopListView from '@/views/shop/ShopListView.vue'
import ShopDetailView from '@/views/shop/ShopDetailView.vue'
import ShopSectionListView from '@/views/shop/ShopSectionListView.vue'
import BlogDetailView from '@/views/blog/BlogDetailView.vue'
import PublishBlogView from '@/views/blog/PublishBlogView.vue'
import MyProfileView from '@/views/user/MyProfileView.vue'
import UserProfileView from '@/views/user/UserProfileView.vue'
import EditProfileView from '@/views/user/EditProfileView.vue'
export const routes: RouteRecordRaw[] = [
  { path: '/shops/:id/lists/:section', name: 'shop-section-list', component: ShopSectionListView },
  { path: '/', name: 'home', component: HomeView }, { path: '/login', name: 'login', component: LoginView }, { path: '/login/password', name: 'password-login', component: PasswordLoginView }, { path: '/register', name: 'register', component: RegisterView }, { path: '/shops', name: 'shops', component: ShopListView }, { path: '/shops/:id', name: 'shop-detail', component: ShopDetailView }, { path: '/blogs/:id', name: 'blog-detail', component: BlogDetailView }, { path: '/publish', name: 'publish', component: PublishBlogView, meta: { requiresAuth: true } }, { path: '/me', name: 'me', component: MyProfileView, meta: { requiresAuth: true } }, { path: '/me/edit', name: 'edit-profile', component: EditProfileView, meta: { requiresAuth: true } }, { path: '/users/:id', name: 'user-profile', component: UserProfileView }, { path: '/:pathMatch(.*)*', name: 'not-found', component: RoutePlaceholder },
]
export function createAppRouter(history: RouterHistory = createWebHistory(), getToken: () => string | null = () => sessionStorage.getItem('token')) { const router = createRouter({ history, routes }); router.beforeEach((to) => to.meta.requiresAuth && !getToken() ? { name: 'login', query: { redirect: to.fullPath } } : true); return router }
