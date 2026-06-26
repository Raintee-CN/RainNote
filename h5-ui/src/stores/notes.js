import { defineStore } from 'pinia'
import * as api from '../api/notes'

export const useNotesStore = defineStore('notes', {
  state: () => ({
    notes: [],
    note: null,
    cards: [],
    loading: false,
    saving: false,
    error: '',
  }),
  actions: {
    async loadNotes() {
      this.loading = true
      this.error = ''
      try {
        this.notes = await api.listNotes()
      } catch (error) {
        this.error = error
        throw error
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
      this.error = ''
      try {
        const detail = await api.getNote(noteId)
        this.note = detail.note
        this.cards = detail.cards || []
      } catch (error) {
        this.error = error
        throw error
      } finally {
        this.loading = false
      }
    },
    async saveCurrent() {
      if (!this.note) return
      this.saving = true
      this.error = ''
      try {
        this.note = await api.updateNote(this.note.id, this.note.title || '未命名便签')
        const detail = await api.replaceNoteContent(this.note.id, this.cards)
        this.note = detail.note
        this.cards = detail.cards || []
      } catch (error) {
        this.error = error
        throw error
      } finally {
        this.saving = false
      }
    },
  },
})
