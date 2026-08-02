import { defineConfig } from 'vitepress'

const github = 'https://github.com/oryxos/oryxos'
const base = process.env.GITHUB_ACTIONS ? `/${process.env.GITHUB_REPOSITORY.split('/')[1]}/` : '/'

export default defineConfig({
  lang: 'en-US',
  title: 'OryxOS',
  titleTemplate: ':title — OryxOS',
  description:
    'A private, auditable Agent OS for the enterprise — Java-native, self-hosted, built to run business agents you can fully govern.',
  base,
  cleanUrls: true,
  appearance: true,

  head: [
    ['link', { rel: 'preconnect', href: 'https://fonts.googleapis.com' }],
    ['link', { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' }],
    [
      'link',
      {
        rel: 'stylesheet',
        href: 'https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@500;700&family=Inter:wght@400;500;600;700;900&display=swap',
      },
    ],
    ['link', { rel: 'icon', type: 'image/svg+xml', href: `${base}favicon.svg` }],
    ['meta', { name: 'author', content: 'OryxOS' }],
    ['meta', { name: 'keywords', content: 'OryxOS, agent OS, enterprise agent, ReAct, MCP, Spring AI, LLM, auditable, self-hosted' }],
    ['meta', { name: 'robots', content: 'index, follow' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:site_name', content: 'OryxOS' }],
    ['meta', { property: 'og:title', content: 'OryxOS — Private, Auditable Agent OS for the Enterprise' }],
    ['meta', { property: 'og:description', content: 'A private, auditable Agent OS for the enterprise — Java-native, self-hosted, built to run business agents you can fully govern.' }],
  ],

  locales: {
    root: {
      label: 'English',
      lang: 'en-US',
      themeConfig: {
        nav: [
          { text: 'Home', link: '/' },
          { text: 'Docs', link: '/docs/what' },
          { text: 'GitHub', link: github },
        ],
        sidebar: {
          '/docs/': [
            {
              text: 'Getting Started',
              items: [
                { text: 'What is OryxOS', link: '/docs/what' },
                { text: 'Features', link: '/docs/features' },
                { text: 'Architecture', link: '/docs/architecture' },
                { text: 'Quick Start', link: '/docs/quick-start' },
                { text: 'Roadmap', link: '/docs/roadmap' },
              ],
            },
          ],
        },
      },
    },
    zh: {
      label: '中文',
      lang: 'zh-CN',
      link: '/zh/',
      themeConfig: {
        nav: [
          { text: '首页', link: '/zh/' },
          { text: '文档', link: '/zh/docs/what' },
          { text: 'GitHub', link: github },
        ],
        sidebar: {
          '/zh/docs/': [
            {
              text: '快速入门',
              items: [
                { text: 'OryxOS 是什么', link: '/zh/docs/what' },
                { text: '功能特性', link: '/zh/docs/features' },
                { text: '系统架构', link: '/zh/docs/architecture' },
                { text: '快速开始', link: '/zh/docs/quick-start' },
                { text: '路线图', link: '/zh/docs/roadmap' },
              ],
            },
          ],
        },
      },
    },
  },

  themeConfig: {
    siteTitle: false,
    logo: '/logo.svg',
    socialLinks: [{ icon: 'github', link: github }],
  },
})
