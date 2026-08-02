import DefaultTheme from 'vitepress/theme'
import OryxHome from './components/OryxHome.vue'
import './custom.css'

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component('OryxHome', OryxHome)
  },
}
