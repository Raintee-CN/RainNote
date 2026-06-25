(function () {
  const editor = document.getElementById('editor');
  let state = { cards: [] };
  let saveTimer = 0;
  let ready = false;

  function uid(prefix) {
    return prefix + '-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8);
  }

  function escapeAttr(value) {
    return String(value || '').replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  function render(payload) {
    state = payload || { cards: [] };
    if (!state.cards || state.cards.length === 0) state.cards = [emptyCard()];
    editor.innerHTML = state.cards.map(cardHtml).join('');
    ready = true;
  }

  function emptyCard() {
    return { id: uid('card'), title: '新卡片', blocks: [emptyBlock('plain_text')] };
  }

  function emptyBlock(type) {
    return { id: uid('block'), type: type || 'plain_text', content: '' };
  }

  function cardHtml(card) {
    const blocks = card.blocks && card.blocks.length ? card.blocks : [emptyBlock('plain_text')];
    return `<section class="card" data-card-id="${escapeAttr(card.id)}">
      <div class="card-title" contenteditable="true" data-placeholder="卡片标题">${card.title || ''}</div>
      <div class="blocks">${blocks.map(blockHtml).join('')}</div>
    </section>`;
  }

  function blockHtml(block) {
    const placeholder = block.type === 'code_block' ? '输入代码' : '输入内容';
    return `<div class="block" contenteditable="true" data-block-id="${escapeAttr(block.id)}" data-type="${escapeAttr(block.type || 'plain_text')}" data-placeholder="${placeholder}">${block.content || ''}</div>`;
  }

  function collect() {
    return {
      cards: Array.from(editor.querySelectorAll('.card')).map((card, cardIndex) => ({
        id: card.dataset.cardId || uid('card'),
        title: card.querySelector('.card-title').innerText.trim() || '未命名卡片',
        sortOrder: cardIndex,
        blocks: Array.from(card.querySelectorAll('.block')).map((block, blockIndex) => ({
          id: block.dataset.blockId || uid('block'),
          type: block.dataset.type || 'plain_text',
          content: block.innerHTML.trim(),
          plainText: block.innerText,
          sortOrder: blockIndex
        }))
      }))
    };
  }

  function scheduleSave() {
    if (!ready) return;
    clearTimeout(saveTimer);
    saveTimer = setTimeout(saveNow, 350);
  }

  function saveNow() {
    if (!ready || !window.AndroidEditor) return;
    window.AndroidEditor.save(JSON.stringify(collect()));
  }

  function currentBlock() {
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return null;
    let node = selection.anchorNode;
    while (node && node !== editor) {
      if (node.nodeType === 1 && node.classList.contains('block')) return node;
      node = node.parentNode;
    }
    return null;
  }

  function currentCard() {
    const block = currentBlock();
    return block ? block.closest('.card') : editor.querySelector('.card');
  }

  function addCard() {
    editor.insertAdjacentHTML('beforeend', cardHtml(emptyCard()));
    const title = editor.lastElementChild.querySelector('.card-title');
    title.focus();
    scheduleSave();
  }

  function addBlock(type) {
    const card = currentCard() || editor.querySelector('.card');
    if (!card) return addCard();
    const blocks = card.querySelector('.blocks');
    blocks.insertAdjacentHTML('beforeend', blockHtml(emptyBlock(type)));
    blocks.lastElementChild.focus();
    scheduleSave();
  }

  editor.addEventListener('input', scheduleSave);
  editor.addEventListener('blur', saveNow, true);
  editor.addEventListener('keydown', function (event) {
    if (event.key === 'Enter' && !event.shiftKey) {
      const block = currentBlock();
      if (block && block.dataset.type !== 'code_block') {
        event.preventDefault();
        block.insertAdjacentHTML('afterend', blockHtml(emptyBlock('plain_text')));
        block.nextElementSibling.focus();
        scheduleSave();
      }
    }
  });

  document.getElementById('toolbar').addEventListener('click', function (event) {
    const button = event.target.closest('button');
    if (!button) return;
    if (button.id === 'addCard') return addCard();
    if (button.id === 'addBlock') return addBlock('plain_text');
    if (button.id === 'addCode') return addBlock('code_block');
    document.execCommand(button.dataset.cmd, false, button.dataset.value || null);
    scheduleSave();
  });

  window.RainNoteEditor = { render, saveNow };
  render({ cards: [] });
})();
