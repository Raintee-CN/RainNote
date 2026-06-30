export const CODE_LANGUAGE_DEFAULT = 'plain'

export function normalizeBlockContent(block) {
  const type = block.type || 'plain_text'
  if (type === 'code_block') return stringifyCodeContent(parseCodeContent(block.content))
  if (type === 'rich_text') return sanitizeHtml(textToHtml(block.content))
  return sanitizeHtml(textToHtml(block.content, { plain: true }))
}

export function textToHtml(content, options = {}) {
  const value = content || ''
  if (looksLikeHtml(value)) return sanitizeHtml(value)
  return value
    .split('\n')
    .map((line) => `<p>${escapeHtml(line) || '<br>'}</p>`)
    .join('') || '<p><br></p>'
}

export function htmlToText(html) {
  const template = document.createElement('template')
  template.innerHTML = html || ''
  return template.content.textContent || ''
}

export function sanitizeHtml(html) {
  const template = document.createElement('template')
  template.innerHTML = html || ''
  cleanNode(template.content)
  return template.innerHTML.trim() || '<p><br></p>'
}

export function parseCodeContent(content) {
  if (!content) return { language: CODE_LANGUAGE_DEFAULT, code: '' }
  try {
    const parsed = JSON.parse(content)
    return {
      language: typeof parsed.language === 'string' && parsed.language.trim() ? parsed.language.trim() : CODE_LANGUAGE_DEFAULT,
      code: typeof parsed.code === 'string' ? parsed.code : '',
    }
  } catch (error) {
    return { language: CODE_LANGUAGE_DEFAULT, code: content }
  }
}

export function stringifyCodeContent(value) {
  return JSON.stringify({
    language: value?.language || CODE_LANGUAGE_DEFAULT,
    code: value?.code || '',
  })
}

export function codeText(content) {
  return parseCodeContent(content).code
}

export function updateCodeText(block, code) {
  const current = parseCodeContent(block.content)
  block.content = stringifyCodeContent({ ...current, code })
}

export function codeLanguage(content) {
  return parseCodeContent(content).language
}

export function updateCodeLanguage(block, language) {
  const current = parseCodeContent(block.content)
  block.content = stringifyCodeContent({ ...current, language })
}

function cleanNode(node) {
  const allowedTags = new Set(['P', 'BR', 'STRONG', 'B', 'EM', 'I', 'U', 'H1', 'H2', 'H3', 'BLOCKQUOTE', 'UL', 'OL', 'LI', 'CODE', 'PRE'])
  Array.from(node.childNodes).forEach((child) => {
    if (child.nodeType === Node.TEXT_NODE) return
    if (child.nodeType !== Node.ELEMENT_NODE) {
      child.remove()
      return
    }
    if (!allowedTags.has(child.tagName)) {
      child.replaceWith(document.createTextNode(child.textContent || ''))
      return
    }
    Array.from(child.attributes).forEach((attribute) => child.removeAttribute(attribute.name))
    cleanNode(child)
  })
}

function looksLikeHtml(value) {
  return /<[a-z][\s\S]*>/i.test(value)
}

function escapeHtml(value) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
