import { defineStore } from 'pinia'
import * as api from '../api/notes'

export const useNotesStore = defineStore('notes', {
  state: () => ({
    notes: [],
    note: null,
    cards: [],
    loading: false,
    saving: false,
  }),
  actions: {
    async loadNotes() {
      this.loading = true
      try {
        this.notes = await api.listNotes()
      } finally {
        this.loading = false
      }
    },
    async createNote(title) {
      const note = await api.createNote(title || '未命名便签')
      await this.loadNotes()
      return note
    },
    async deleteNote(noteId) {
      await api.deleteNote(noteId)
      await this.loadNotes()
    },
    async loadNote(noteId) {
      this.loading = true
      try {
        const detail = await api.getNote(noteId)
        this.note = detail.note
        this.cards = detail.cards || []
      } finally {
        this.loading = false
      }
    },
    async saveCurrent() {
      if (!this.note) return
      this.saving = true
      try {
        this.note = await api.updateNote(this.note.id, this.note.title || '未命名便签')
        const detail = await api.replaceNoteContent(this.note.id, this.cards)
        this.note = detail.note
        this.cards = detail.cards || []
      } finally {
        this.saving = false
      }
    },
  },
})
