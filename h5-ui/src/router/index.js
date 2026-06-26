import { createRouter, createWebHashHistory } from 'vue-router'
import ConnectView from '../views/ConnectView.vue'
import NoteListView from '../views/NoteListView.vue'
import NoteEditorView from '../views/NoteEditorView.vue'

export default createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/connect' },
    { path: '/connect', component: ConnectView },
    { path: '/notes', component: NoteListView },
    { path: '/notes/:id', component: NoteEditorView },
  ],
})
