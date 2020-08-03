import 'regenerator-runtime/runtime.js'

import Vue from 'vue'
import VueRouter from 'vue-router'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'

import TopPage from '../pages/TopPage'

import { store } from '../stores/index'
import NProgress from 'nprogress'

const ProjectDocs = import(/* webpackChunkName: "project-docs" */'../pages/project/ProjectDocs');

Vue.use(VueRouter)
Vue.component('FontAwesomeIcon', FontAwesomeIcon)

store.dispatch('global/loadUser')

const router = new VueRouter({
  base: '/',
  mode: 'history',
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import(/* webpackChunkName: "home" */'../pages/Home'),
    },
    {
      path: '/:user',
      component: () => import(/* webpackChunkName: "user" */'../pages/user/User'),
      props: true,
      children: [
        {
          path: '',
          name: 'user_projects',
          component: import(/* webpackChunkName: "user-projects" */'../pages/user/UserProjects'),
        },
      ],
    },
    {
      path: '/projects/new',
      name: 'new_project',
      component: () => import(/* webpackChunkName: "new-project" */'../pages/project/NewProject'),
    },
    {
      path: '/:owner/:slug/',
      component: () => import(/* webpackChunkName: "project" */'../pages/project/Project'),
      props: true,
      children: [
        {
          path: '',
          name: 'project_home',
          component: () => ProjectDocs,
          props: {
            page: ['Home'],
          },
        },
        {
          path: 'pages/:page+',
          name: 'pages',
          component: () => ProjectDocs,
          props: true,
        },
        {
          path: 'versions',
          name: 'versions',
          component: () => import(/* webpackChunkName: "project-versions" */'../pages/project/ProjectVersions'),
        },
        {
          path: 'versions/new',
          name: 'new_version',
          component: () => import(/* webpackChunkName: "project-version-new" */'../pages/project/NewVersion'),
        },
        {
          path: 'versions/:version',
          name: 'version',
          component: () => import(/* webpackChunkName: "project-version" */'../pages/project/VersionPage'),
          props: true,
        },
        {
          path: 'discuss',
          name: 'discussion',
          component: () => import(/* webpackChunkName: "project-discussion" */'../pages/project/ProjectDiscussion'),
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import(/* webpackChunkName: "project-settings" */'../pages/project/ProjectSettings'),
        },
      ],
    },
  ],
})

router.beforeEach((to, from, next) => {
  store.commit('dismissAllAlerts')
  next()
})

router.beforeResolve((to, from, next) => {
  if (to.name) {
    NProgress.start()
  }
  next()
})

router.afterEach((to, from) => {
  NProgress.done()
})

// eslint-disable-next-line no-new
new Vue({
  el: '#home',
  render: (createElement) => createElement(TopPage),
  router,
  store,
})