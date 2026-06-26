import { apiClient } from './client'

export async function health() {
  return (await apiClient().get('/api/health')).data
}

export async function listNotes() {
  return (await apiClient().get('/api/notes')).data.notes || []
}

export async function createNote(title) {
  return (await apiClient().post('/api/notes', { title })).data.note
}

export async function updateNote(noteId, title) {
  return (await apiClient().put(`/api/notes/${noteId}`, { title })).data.note
}

export async function deleteNote(noteId) {
  return (await apiClient().delete(`/api/notes/${noteId}`)).data
}

export async function getNote(noteId) {
  return (await apiClient().get(`/api/notes/${noteId}`)).data
}

export async function replaceNoteContent(noteId, cards) {
  return (await apiClient().put(`/api/notes/${noteId}/content`, { cards })).data
}
