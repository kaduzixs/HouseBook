'use strict';

(() => {
    const savedTheme = localStorage.getItem('housebook.site.theme');
    if (['forest', 'ember', 'violet', 'ocean'].includes(savedTheme)) {
        document.documentElement.dataset.theme = savedTheme;
    }

    const API = '/api/books';
    const READER_API = '/api/reader';
    const PROGRESS_DELAY_MS = 1200;

    const $ = selector => document.querySelector(selector);

    const state = {
        library: [],
        discoveries: [],
        filter: 'all',
        libraryQuery: '',
        csrf: null,
        csrfHeader: null,
        profilePhotoFile: null,
    };

    const reader = {
        userBookId: null,
        book: null,
        chapters: [],
        index: 0,
        cache: new Map(),
        token: 0,
        font: 19,
        lastSaved: '',
        pending: null,
        saveTimer: null,
        scrollQueued: false,
    };

    const libraryGrid = $('#library-grid');
    const historyGrid = $('#history-grid');
    const feedback = $('#page-feedback');
    const bookDialog = $('#book-dialog');
    const readerDialog = $('#reader-dialog');
    const readerPage = $('#reader-page');
    const readerContent = $('#reader-content');

    // ------------------------------------------------------------------ utilidades

    const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
    const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ESCAPES[char]);

    let feedbackTimer = null;

    function msg(text, type = '') {
        clearTimeout(feedbackTimer);
        feedback.textContent = text;
        feedback.className = `page-feedback ${type}`.trim();
        if (!text) {
            return;
        }
        feedback.classList.add('visible');
        feedbackTimer = setTimeout(() => feedback.classList.remove('visible'), type === 'error' ? 7000 : 4500);
    }

    async function ensureCsrf() {
        if (state.csrf) {
            return;
        }
        try {
            const response = await fetch('/user/csrf', {
                credentials: 'same-origin',
                headers: { Accept: 'application/json' },
            });
            if (response.ok) {
                const data = await response.json();
                state.csrf = data.token;
                state.csrfHeader = data.headerName || 'X-CSRF-TOKEN';
            }
        } catch (error) {
            console.warn('Não foi possível obter o token CSRF.', error);
        }
    }

    const FRIENDLY_STATUS = {
        401: 'Sua sessão expirou. Entre novamente.',
        403: 'A solicitação foi bloqueada pela proteção CSRF. Atualize a página e tente novamente.',
        404: 'Não encontramos o que você pediu.',
        409: 'Esta ação já foi realizada.',
        413: 'O arquivo é maior que o limite permitido.',
        502: 'O serviço externo não respondeu. Tente novamente em instantes.',
    };

    async function request(url, options = {}) {
        const method = (options.method || 'GET').toUpperCase();
        const headers = new Headers(options.headers || {});
        headers.set('Accept', 'application/json');

        if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
            await ensureCsrf();
            if (state.csrf && state.csrfHeader) {
                headers.set(state.csrfHeader, state.csrf);
            }
        }

        const response = await fetch(url, { credentials: 'same-origin', ...options, headers });

        if (!response.ok) {
            let message = FRIENDLY_STATUS[response.status] || `Falha na operação (${response.status}).`;
            try {
                const type = response.headers.get('content-type') || '';
                if (type.includes('json')) {
                    const data = await response.json();
                    message = data.message || data.detail || message;
                } else {
                    const text = await response.text();
                    if (text) {
                        message = text;
                    }
                }
            } catch {
                // mantém a mensagem padrão
            }
            if (response.status === 401) {
                throw new Error(FRIENDLY_STATUS[401]);
            }
            if (response.status === 403) {
                throw new Error(FRIENDLY_STATUS[403]);
            }
            throw new Error(message);
        }

        if (response.status === 204) {
            return null;
        }
        const type = response.headers.get('content-type') || '';
        return type.includes('json') ? response.json() : response.text();
    }

    // ------------------------------------------------------------ normalização

    // O Gutenberg cadastra autores como "Austen, Jane"; exibimos "Jane Austen".
    function displayName(name) {
        const parts = String(name || '').split(',');
        return parts.length === 2 ? `${parts[1].trim()} ${parts[0].trim()}`.trim() : String(name || '').trim();
    }

    // Texto puro em qualquer codificação (utf-8, us-ascii, iso-8859-1...) ou edição em HTML/EPUB,
    // a partir da qual o Gutenberg gera o texto padrão. Mantém a mesma regra do backend.
    function hasReadableText(formats) {
        const keys = Object.keys(formats || {});
        return keys.some(key => key.startsWith('text/plain') && !String(formats[key]).toLowerCase().endsWith('.zip'))
            || keys.includes('text/html')
            || keys.includes('application/epub+zip');
    }

    // As capas são servidas pelo próprio backend para evitar bloqueios de hotlink,
    // políticas de rede do navegador e falhas de carregamento direto do Gutenberg.
    function coverProxyUrl(bookId) {
        const id = String(bookId || '');
        return /^\\d+$/.test(id) ? `/api/books/cover/${encodeURIComponent(id)}` : '';
    }

    // Item cru da Gutendex (resultado de busca) -> formato usado pelos cartões.
    function normalizeCatalogItem(item) {
        const formats = item.formats || {};
        const subjects = Array.isArray(item.subjects) ? item.subjects : [];
        return {
            googleBookId: String(item.id),
            title: item.title || 'Título não informado',
            authors: Array.isArray(item.authors) && item.authors.length
                ? item.authors.map(author => displayName(author.name)).join(', ')
                : 'Autor desconhecido',
            description: subjects.join(' • ') || 'Obra gratuita do catálogo Project Gutenberg.',
            publisher: 'Project Gutenberg',
            // O backend busca a imagem no endereço image/jpeg da Gutendex e a entrega no mesmo domínio.
            thumbnail: coverProxyUrl(item.id),
            language: Array.isArray(item.languages) ? item.languages.join(', ') : '',
            subjects: subjects.join(' | '),
            downloadCount: item.download_count || 0,
            contentAvailable: hasReadableText(formats),
            formats,
        };
    }

    const LEGACY_STATUS = { NAO_INICIADO: 'WANT_TO_READ', EM_ANDAMENTO: 'READING', LIDO: 'COMPLETED' };

    function parseFormats(json) {
        try {
            return json ? JSON.parse(json) : {};
        } catch {
            return {};
        }
    }

    // Livro da biblioteca (BookResponseDTO) -> formato usado pelos cartões.
    function normalizeBook(book) {
        const status = book.readingStatus || 'WANT_TO_READ';
        return {
            userBookId: book.userBookId,
            bookId: book.bookId,
            googleBookId: book.googleBookId,
            title: book.title || 'Título não informado',
            authors: book.authors || 'Autor desconhecido',
            description: book.description || 'Descrição não disponível.',
            publisher: book.publisher || '',
            thumbnail: coverProxyUrl(book.googleBookId) || book.thumbnail || '',
            language: book.language || '',
            subjects: book.subjects || '',
            downloadCount: book.downloadCount || 0,
            contentAvailable: Boolean(book.contentAvailable),
            formats: parseFormats(book.formatsJson),
            readingStatus: LEGACY_STATUS[status] || status,
            personalRating: book.personalRating,
            currentChapter: book.currentChapter,
            currentParagraph: book.currentParagraph,
        };
    }

    const STATUS_LABELS = {
        WANT_TO_READ: 'Quero ler',
        READING: 'Em andamento',
        COMPLETED: 'Concluído',
        PAUSED: 'Pausado',
    };
    const statusLabel = status => STATUS_LABELS[status] || 'Na biblioteca';

    // ------------------------------------------------------------------- capas

    function coverPlaceholder(title) {
        const box = document.createElement('div');
        box.className = 'cover-placeholder';
        const mark = document.createElement('span');
        mark.textContent = '✧';
        const name = document.createElement('strong');
        name.textContent = title;
        box.append(mark, name);
        return box;
    }

    function coverMarkup(book) {
        if (!book.thumbnail) {
            return `<div class="cover-placeholder"><span>✧</span><strong>${esc(book.title)}</strong></div>`;
        }
        return `<img class="book-cover" src="${esc(book.thumbnail)}" alt="Capa de ${esc(book.title)}" `
            + `data-title="${esc(book.title)}" loading="lazy" decoding="async" referrerpolicy="no-referrer">`;
    }

    // Se a imagem não carregar (404, bloqueio de rede...), troca por uma capa estilizada com o título.
    document.addEventListener('error', event => {
        const image = event.target;
        if (image instanceof HTMLImageElement && image.classList.contains('book-cover')) {
            image.replaceWith(coverPlaceholder(image.dataset.title || ''));
        }
    }, true);

    // ----------------------------------------------------------------- cartões

    function libraryEntryFor(book) {
        return state.library.find(item => item.googleBookId === book.googleBookId);
    }

    function actionButton(label, attributes, extraClass = '') {
        const attrs = Object.entries(attributes)
            .map(([name, value]) => `${name}="${esc(value)}"`)
            .join(' ');
        return `<button class="book-action ${extraClass}" type="button" ${attrs}>${label}</button>`;
    }

    function discoverActions(book) {
        const owned = libraryEntryFor(book);
        if (!owned) {
            return actionButton('＋ Adicionar', { 'data-action': 'add', 'data-id': book.googleBookId }, 'primary');
        }
        return actionButton('📖 Ler agora', { 'data-action': 'open-reader', 'data-id': owned.userBookId }, 'primary');
    }

    function libraryActions(book) {
        const started = book.readingStatus === 'READING' || book.readingStatus === 'COMPLETED';
        let html = actionButton(
            started ? '📖 Continuar' : '📖 Ler agora',
            { 'data-action': 'open-reader', 'data-id': book.userBookId },
            'primary',
        );
        if (book.readingStatus !== 'COMPLETED') {
            html += actionButton('✓ Concluir', { 'data-action': 'read', 'data-id': book.userBookId });
        }
        html += actionButton('Remover', { 'data-action': 'remove', 'data-id': book.userBookId }, 'danger');
        return html;
    }

    function progressNote(book) {
        if (book.readingStatus === 'READING' && book.currentChapter) {
            return `<p class="book-meta">Parou no capítulo ${esc(book.currentChapter)}</p>`;
        }
        return '<p class="book-meta"></p>';
    }

    function card(book, mode) {
        const owned = mode !== 'discover';
        const detailsId = owned ? book.userBookId : book.googleBookId;
        const status = owned ? `<span class="book-status">${esc(statusLabel(book.readingStatus))}</span>` : '';
        const actions = owned ? libraryActions(book) : discoverActions(book);

        return `
            <article class="book-card">
                <div class="book-cover-wrap" data-action="details" data-id="${esc(detailsId)}" data-mode="${mode}">
                    ${coverMarkup(book)}
                    ${status}
                </div>
                <div class="book-info">
                    <h3 class="book-title">${esc(book.title)}</h3>
                    <p class="book-author">${esc(book.authors)}</p>
                    ${owned ? progressNote(book) : ''}
                    <div class="book-actions">${actions}</div>
                </div>
            </article>`;
    }

    // ------------------------------------------------------------- renderização

    function updateStats() {
        const count = status => state.library.filter(book => book.readingStatus === status).length;
        $('#total-books').textContent = state.library.length;
        $('#reading-books').textContent = count('READING');
        $('#finished-books').textContent = count('COMPLETED');
    }

    function renderLibrary() {
        const query = state.libraryQuery.toLocaleLowerCase('pt-BR');
        const list = state.library.filter(book => {
            const matchesFilter = state.filter === 'all' || book.readingStatus === state.filter;
            const matchesQuery = !query
                || book.title.toLocaleLowerCase('pt-BR').includes(query)
                || book.authors.toLocaleLowerCase('pt-BR').includes(query);
            return matchesFilter && matchesQuery;
        });

        libraryGrid.innerHTML = list.map(book => card(book, 'library')).join('');
        $('#library-empty').hidden = list.length > 0;
        updateStats();
    }

    // Atualiza só os cartões (usado quando a biblioteca muda e o botão "Adicionar" vira "Ler agora").
    function renderDiscoverCards() {
        $('#discover-grid').innerHTML = state.discoveries.map(book => card(book, 'discover')).join('');
    }

    function renderDiscoveries(emptyTitle = 'Nenhum livro encontrado.', emptyText = 'Tente pesquisar por outro título ou autor.') {
        renderDiscoverCards();
        $('#discover-empty').hidden = state.discoveries.length > 0;
        $('#discover-empty h3').textContent = emptyTitle;
        $('#discover-empty p').textContent = emptyText;
    }

    function renderHistory() {
        const list = state.library.filter(book => book.readingStatus === 'COMPLETED');
        historyGrid.innerHTML = list.map(book => card(book, 'history')).join('');
        $('#history-empty').hidden = list.length > 0;
    }

    function renderAll() {
        renderLibrary();
        renderDiscoverCards();
        renderHistory();
    }

    function showView(name) {
        const views = {
            library: $('#library-view'),
            discover: $('#discover-view'),
            history: $('#history-view'),
        };
        Object.entries(views).forEach(([key, view]) => {
            view.hidden = key !== name;
        });
        document.querySelectorAll('.nav-link').forEach(button => {
            button.classList.toggle('active', button.dataset.view === name);
        });
        if (name === 'library') {
            renderLibrary();
        }
        if (name === 'history') {
            renderHistory();
        }
    }

    // --------------------------------------------------------------- carregamento

    async function loadLibrary() {
        try {
            const data = await request(`${API}/mine`);
            state.library = Array.isArray(data) ? data.map(normalizeBook) : [];
            renderAll();
        } catch (error) {
            msg(error.message, 'error');
        }
    }

    async function loadNewBooks() {
        $('#discover-title').textContent = 'Novas descobertas';
        $('#discover-eyebrow').textContent = 'SELEÇÃO HOUSEBOOK';
        renderDiscoveries('Carregando descobertas...', 'Estamos consultando o catálogo de livros.');
        try {
            const data = await request(`${API}/new`);
            state.discoveries = Array.isArray(data?.results) ? data.results.map(normalizeCatalogItem) : [];
            renderDiscoveries(
                'Nenhum livro disponível neste momento.',
                'A Gutendex não retornou obras para a seleção inicial. Tente novamente em instantes.',
            );
        } catch (error) {
            state.discoveries = [];
            renderDiscoveries('Não foi possível carregar os livros.', 'Confira sua conexão e tente novamente.');
            msg(error.message, 'error');
        }
    }

    async function searchBooks(query) {
        if (!query.trim()) {
            return;
        }
        showView('discover');
        $('#book-search').value = query;
        $('#discover-title').textContent = 'Resultados da pesquisa';
        $('#discover-eyebrow').textContent = 'RESULTADOS ENCONTRADOS';
        msg('Pesquisando no catálogo gratuito...');

        const params = new URLSearchParams({ q: query.trim() });
        if ($('#book-language').value) {
            params.set('language', $('#book-language').value);
        }
        if ($('#book-format').value) {
            params.set('mimeType', $('#book-format').value);
        }
        if ($('#book-topic').value) {
            params.set('topic', $('#book-topic').value);
        }

        try {
            const data = await request(`${API}/search?${params}`);
            state.discoveries = Array.isArray(data?.results) ? data.results.map(normalizeCatalogItem) : [];
            renderDiscoveries(
                'Nenhum livro encontrado.',
                'Não encontramos resultados para essa pesquisa. Tente outro título, autor ou assunto.',
            );
            msg(state.discoveries.length ? `${state.discoveries.length} resultado(s) encontrado(s).` : 'Nenhum livro encontrado.');
        } catch (error) {
            state.discoveries = [];
            renderDiscoveries('A pesquisa não pôde ser concluída.', 'Confira sua conexão e tente novamente.');
            msg(error.message, 'error');
        }
    }

    // -------------------------------------------------------------------- ações

    async function acquire(id) {
        try {
            await request(`${API}/acquire/${encodeURIComponent(id)}`, { method: 'POST' });
            msg('Livro adicionado à sua biblioteca! Toque em “Ler agora” para começar.', 'success');
            await loadLibrary();
        } catch (error) {
            msg(error.message, 'error');
        }
    }

    async function markRead(id) {
        try {
            await request(`${API}/history/${encodeURIComponent(id)}`, { method: 'POST' });
            msg('Livro marcado como concluído!', 'success');
            await loadLibrary();
        } catch (error) {
            msg(error.message, 'error');
        }
    }

    async function removeBook(id) {
        if (!confirm('Deseja remover este livro da sua biblioteca?')) {
            return;
        }
        try {
            await request(`${API}/${encodeURIComponent(id)}`, { method: 'DELETE' });
            msg('Livro removido da sua biblioteca.', 'success');
            await loadLibrary();
        } catch (error) {
            msg(error.message, 'error');
        }
    }

    // ------------------------------------------------------------------ detalhes

    const FORMAT_LABELS = {
        'text/plain': 'Texto',
        'text/html': 'HTML',
        'application/epub+zip': 'EPUB',
        'application/x-mobipocket-ebook': 'Kindle',
        'application/pdf': 'PDF',
    };

    function formatLinks(formats) {
        const seen = new Set();
        const links = [];
        Object.entries(formats || {}).forEach(([type, url]) => {
            const label = FORMAT_LABELS[type.split(';')[0].trim()];
            if (label && !seen.has(label) && /^https:\/\//i.test(url)) {
                seen.add(label);
                links.push(`<a href="${esc(url)}" target="_blank" rel="noopener noreferrer">${label}</a>`);
            }
        });
        return links.length ? `<div class="format-links">${links.join('')}</div>` : 'Não informado';
    }

    function showDetails(book) {
        const owned = book.userBookId ?? libraryEntryFor(book)?.userBookId;
        const action = owned
            ? actionButton('📖 Ler agora', { 'data-action': 'open-reader', 'data-id': owned }, 'primary')
            : actionButton('＋ Adicionar à biblioteca', { 'data-action': 'add', 'data-id': book.googleBookId }, 'primary');

        $('#dialog-content').innerHTML = `
            <div class="dialog-book">
                ${coverMarkup(book)}
                <div>
                    <p class="eyebrow">DETALHES DA OBRA</p>
                    <h2>${esc(book.title)}</h2>
                    <p><strong>Autor:</strong> ${esc(book.authors)}</p>
                    <p><strong>Fonte:</strong> ${esc(book.publisher || 'Project Gutenberg')}</p>
                    <p><strong>Idioma:</strong> ${esc(book.language || 'Não informado')}</p>
                    <p><strong>Downloads:</strong> ${Number(book.downloadCount || 0).toLocaleString('pt-BR')}</p>
                    <p><strong>Assuntos:</strong> ${esc(book.subjects || book.description || 'Não informado')}</p>
                    <p><strong>Baixar em outros formatos:</strong> ${formatLinks(book.formats)}</p>
                    <p><strong>Leitura no site:</strong> ${book.contentAvailable ? 'Texto integral disponível' : 'Sem texto compatível (você pode importar um TXT)'}</p>
                    <div class="dialog-actions">${action}</div>
                </div>
            </div>`;
        bookDialog.showModal();
    }

    // -------------------------------------------------------------------- leitor

    const READER_PREF_KEYS = { font: 'housebook.reader.font', theme: 'housebook.reader.theme' };

    function loadReaderPrefs() {
        try {
            const font = Number(localStorage.getItem(READER_PREF_KEYS.font));
            if (font >= 14 && font <= 30) {
                reader.font = font;
            }
            const theme = localStorage.getItem(READER_PREF_KEYS.theme);
            if (['paper', 'sepia', 'night'].includes(theme)) {
                readerPage.dataset.theme = theme;
                $('#reader-theme').value = theme;
            }
        } catch {
            // preferências são opcionais
        }
        readerPage.style.setProperty('--reader-size', `${reader.font}px`);
    }

    function saveReaderPref(key, value) {
        try {
            localStorage.setItem(READER_PREF_KEYS[key], String(value));
        } catch {
            // sem armazenamento disponível
        }
    }

    function setReaderLoading(loading, text = 'Preparando seu livro...') {
        $('#reader-loading').hidden = !loading;
        $('#reader-loading-text').textContent = text;
        if (loading) {
            readerContent.hidden = true;
            $('#reader-empty').hidden = true;
        }
    }

    function showReaderEmpty(title, text, { error = false } = {}) {
        setReaderLoading(false);
        readerContent.hidden = true;
        $('#reader-empty').hidden = false;
        $('#reader-empty-title').textContent = title;
        $('#reader-empty-text').textContent = text;
        $('#reader-retry').hidden = !error;
        $('#reader-chapter-label').textContent = 'LEITURA';
        $('#reader-chapter-title').textContent = reader.book?.title || 'Seu livro';
        updatePagination();
    }

    function resetReader(book) {
        reader.userBookId = book?.userBookId ?? reader.userBookId;
        reader.book = book || null;
        reader.chapters = [];
        reader.index = 0;
        reader.cache.clear();
        reader.lastSaved = '';
        reader.pending = null;
        reader.token += 1;

        $('#reader-book-title').textContent = book?.title || 'HouseBook Reader';
        $('#reader-book-author').textContent = book?.authors || '';
        $('#reader-chapter-list').innerHTML = '';
        $('#reader-progress-bar').style.width = '0%';
        readerContent.replaceChildren();
        readerContent.lang = book?.language || 'pt';
        readerPage.scrollTop = 0;

        const cover = $('#reader-cover');
        cover.hidden = !book?.thumbnail;
        cover.src = book?.thumbnail || '';
        cover.alt = book ? `Capa de ${book.title}` : '';
    }

    async function openReader(userBookId) {
        const book = state.library.find(item => String(item.userBookId) === String(userBookId));
        resetReader(book || { userBookId });
        bookDialog.close();
        if (!readerDialog.open) {
            readerDialog.showModal();
        }

        const token = reader.token;
        setReaderLoading(true, 'Preparando seu livro... na primeira vez pode levar alguns segundos.');
        try {
            const data = await request(`${READER_API}/${encodeURIComponent(userBookId)}`);
            if (token !== reader.token) {
                return;
            }
            reader.chapters = data.chapters || [];
            $('#reader-book-title').textContent = data.title || reader.book?.title || 'HouseBook Reader';
            $('#reader-book-author').textContent = data.authors || reader.book?.authors || '';
            renderChapterList();

            if (!reader.chapters.length) {
                showReaderEmpty(
                    'Adicione o conteúdo desta obra',
                    data.officialTextAvailable
                        ? 'Não foi possível preparar o texto deste livro. Você pode importar um arquivo TXT.'
                        : 'O Project Gutenberg não fornece o texto integral deste título. Importe um arquivo TXT de uma obra que você tenha direito de utilizar.',
                );
                return;
            }
            await showChapter(Math.max(0, (data.currentChapter || 1) - 1), { paragraph: data.currentParagraph || 0 });
        } catch (error) {
            if (token === reader.token) {
                showReaderEmpty('Não foi possível abrir o livro', error.message, { error: true });
            }
        }
    }

    function renderChapterList() {
        $('#reader-chapter-list').innerHTML = reader.chapters
            .map((chapter, index) => `
                <li>
                    <button type="button" data-chapter-index="${index}" title="${esc(chapter.title)}"
                            class="${index === reader.index ? 'active' : ''}"
                            ${index === reader.index ? 'aria-current="true"' : ''}>${esc(chapter.title)}</button>
                </li>`)
            .join('');
        $('#reader-chapter-list .active')?.scrollIntoView({ block: 'nearest' });
    }

    async function fetchChapter(number) {
        if (reader.cache.has(number)) {
            return reader.cache.get(number);
        }
        const chapter = await request(`${READER_API}/${encodeURIComponent(reader.userBookId)}/chapters/${number}`);
        reader.cache.set(number, chapter);
        return chapter;
    }

    async function showChapter(index, { paragraph = 0 } = {}) {
        if (index < 0 || index >= reader.chapters.length) {
            return;
        }
        const token = reader.token;
        const number = reader.chapters[index].chapterNumber;
        const needsFetch = !reader.cache.has(number);
        if (needsFetch) {
            setReaderLoading(true, 'Carregando capítulo...');
        }

        try {
            const chapter = await fetchChapter(number);
            if (token !== reader.token) {
                return;
            }
            reader.index = index;
            renderChapter(chapter, paragraph);
            renderChapterList();
            updatePagination();
            updateProgressBar();
            schedulePrefetch(index + 1);
            if (paragraph === 0) {
                queueProgress(number, 0, true);
            }
        } catch (error) {
            if (token === reader.token) {
                showReaderEmpty('Não foi possível carregar o capítulo', error.message, { error: true });
            }
        }
    }

    function renderChapter(chapter, paragraph) {
        setReaderLoading(false);
        $('#reader-empty').hidden = true;
        readerContent.hidden = false;
        $('#reader-chapter-label').textContent = `CAPÍTULO ${chapter.chapterNumber} DE ${reader.chapters.length}`;
        $('#reader-chapter-title').textContent = chapter.title;

        const fragment = document.createDocumentFragment();
        String(chapter.content || '').split(/\n{2,}/).forEach((text, position) => {
            const element = document.createElement('p');
            element.dataset.index = position;
            element.textContent = text;
            fragment.append(element);
        });
        readerContent.replaceChildren(fragment);

        const first = readerContent.firstElementChild;
        readerContent.classList.toggle('has-dropcap', Boolean(first) && first.textContent.length > 120 && !first.textContent.includes('\n'));

        readerPage.scrollTop = 0;
        if (paragraph > 0) {
            requestAnimationFrame(() => {
                const target = readerContent.children[paragraph];
                if (target) {
                    readerPage.scrollTop += target.getBoundingClientRect().top - readerPage.getBoundingClientRect().top - 24;
                }
            });
        }
    }

    function schedulePrefetch(index) {
        const next = reader.chapters[index];
        if (next && !reader.cache.has(next.chapterNumber)) {
            fetchChapter(next.chapterNumber).catch(() => {});
        }
    }

    function updatePagination() {
        const total = reader.chapters.length;
        const last = total > 0 && reader.index >= total - 1;
        const next = $('#reader-next');

        $('#reader-previous').disabled = total === 0 || reader.index === 0;
        next.disabled = total === 0;
        next.textContent = last ? 'Concluir leitura ✓' : 'Próximo →';
        next.classList.toggle('is-finish', last);
        $('#reader-position').textContent = total ? `Capítulo ${reader.index + 1} de ${total}` : 'Sem conteúdo';
    }

    function currentParagraph() {
        const top = readerPage.getBoundingClientRect().top + 40;
        const paragraphs = readerContent.children;
        for (let i = 0; i < paragraphs.length; i += 1) {
            if (paragraphs[i].getBoundingClientRect().bottom > top) {
                return i;
            }
        }
        return Math.max(0, paragraphs.length - 1);
    }

    function updateProgressBar() {
        const total = reader.chapters.length;
        if (!total) {
            return;
        }
        const scrollable = readerPage.scrollHeight - readerPage.clientHeight;
        const inChapter = scrollable > 0 ? Math.min(1, readerPage.scrollTop / scrollable) : 0;
        const percent = Math.round(((reader.index + inChapter) / total) * 100);
        $('#reader-progress-bar').style.width = `${percent}%`;
        $('.reader-progress').setAttribute('aria-valuenow', String(percent));
    }

    // Guarda a posição de leitura com um pequeno atraso, para não enviar uma requisição a cada rolagem.
    function queueProgress(chapter, paragraph, immediate = false) {
        const key = `${chapter}:${paragraph}`;
        if (key === reader.lastSaved) {
            return;
        }
        reader.pending = { chapter, paragraph, key, userBookId: reader.userBookId };
        clearTimeout(reader.saveTimer);
        if (immediate) {
            flushProgress();
        } else {
            reader.saveTimer = setTimeout(flushProgress, PROGRESS_DELAY_MS);
        }
    }

    async function flushProgress() {
        clearTimeout(reader.saveTimer);
        const pending = reader.pending;
        if (!pending) {
            return;
        }
        reader.pending = null;
        try {
            await request(`${READER_API}/${encodeURIComponent(pending.userBookId)}/progress`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ currentChapter: pending.chapter, currentParagraph: pending.paragraph }),
                keepalive: true,
            });
            reader.lastSaved = pending.key;
        } catch (error) {
            console.warn('Não foi possível salvar o progresso.', error);
        }
    }

    function onReaderScroll() {
        if (reader.scrollQueued || readerContent.hidden) {
            return;
        }
        reader.scrollQueued = true;
        requestAnimationFrame(() => {
            reader.scrollQueued = false;
            updateProgressBar();
            const chapter = reader.chapters[reader.index];
            if (chapter) {
                queueProgress(chapter.chapterNumber, currentParagraph());
            }
        });
    }

    async function goToChapter(index) {
        await flushProgress();
        await showChapter(index);
    }

    async function nextOrFinish() {
        if (reader.index < reader.chapters.length - 1) {
            await goToChapter(reader.index + 1);
            return;
        }
        await flushProgress();
        try {
            await request(`${READER_API}/${encodeURIComponent(reader.userBookId)}/complete`, { method: 'POST' });
            readerDialog.close();
            msg('Parabéns! Livro concluído e guardado no seu histórico.', 'success');
        } catch (error) {
            msg(error.message, 'error');
        }
    }

    async function importReaderText(event) {
        event.preventDefault();
        const file = $('#reader-file').files[0];
        if (!file) {
            return;
        }
        const data = new FormData();
        data.append('file', file);

        const token = reader.token;
        setReaderLoading(true, 'Importando o texto...');
        try {
            const result = await request(`${READER_API}/${encodeURIComponent(reader.userBookId)}/content`, {
                method: 'POST',
                body: data,
            });
            if (token !== reader.token) {
                return;
            }
            reader.cache.clear();
            reader.chapters = result.chapters || [];
            reader.index = 0;
            renderChapterList();
            $('#reader-file').value = '';
            await showChapter(0);
            msg('Texto importado. Boa leitura!', 'success');
        } catch (error) {
            showReaderEmpty('Não foi possível importar o texto', error.message, { error: false });
            msg(error.message, 'error');
        }
    }

    function changeFont(delta) {
        reader.font = Math.min(30, Math.max(14, reader.font + delta));
        readerPage.style.setProperty('--reader-size', `${reader.font}px`);
        saveReaderPref('font', reader.font);
        requestAnimationFrame(updateProgressBar);
    }

    function setupReader() {
        loadReaderPrefs();

        $('#reader-upload-form').addEventListener('submit', importReaderText);
        $('#reader-retry').addEventListener('click', () => openReader(reader.userBookId));
        $('#reader-previous').addEventListener('click', () => goToChapter(reader.index - 1));
        $('#reader-next').addEventListener('click', nextOrFinish);
        $('#reader-smaller').addEventListener('click', () => changeFont(-1));
        $('#reader-larger').addEventListener('click', () => changeFont(1));
        $('#reader-close').addEventListener('click', () => readerDialog.close());
        $('#reader-back').addEventListener('click', () => readerDialog.close());
        readerPage.addEventListener('scroll', onReaderScroll, { passive: true });

        $('#reader-theme').addEventListener('change', event => {
            readerPage.dataset.theme = event.target.value;
            saveReaderPref('theme', event.target.value);
        });

        $('#reader-chapter-list').addEventListener('click', event => {
            const button = event.target.closest('[data-chapter-index]');
            if (button) {
                goToChapter(Number(button.dataset.chapterIndex));
            }
        });

        // Setas do teclado viram a página (exceto ao usar o seletor de tema ou o campo de arquivo).
        readerDialog.addEventListener('keydown', event => {
            if (event.target.closest('select, input') || event.altKey || event.ctrlKey || event.metaKey) {
                return;
            }
            if (event.key === 'ArrowRight' && reader.index < reader.chapters.length - 1) {
                goToChapter(reader.index + 1);
            } else if (event.key === 'ArrowLeft' && reader.index > 0) {
                goToChapter(reader.index - 1);
            }
        });

        // Ao fechar o leitor (botão, Esc...), salva onde parou e atualiza a estante.
        readerDialog.addEventListener('close', async () => {
            const chapter = reader.chapters[reader.index];
            if (chapter && !readerContent.hidden) {
                queueProgress(chapter.chapterNumber, currentParagraph(), true);
            }
            await flushProgress();
            reader.token += 1;
            await loadLibrary();
        });

        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'hidden') {
                flushProgress();
            }
        });
    }

    // --------------------------------------------------------------------- perfil

    async function loadUser() {
        try {
            const user = await request('/user/me');
            const name = user.name || 'Aventureiro';
            const initial = name.trim().charAt(0).toUpperCase() || 'H';

            $('#profile-name').textContent = name;
            $('#menu-profile-name').textContent = name;
            $('#profile-email').textContent = user.email || '';
            $('#profile-initial').textContent = initial;
            $('#profile-preview-initial').textContent = initial;
            $('#edit-name').value = name;

            if (user.hasPhoto) {
                await loadPhoto();
            }
        } catch (error) {
            msg(error.message, 'error');
            if (error.message.includes('sessão')) {
                location.replace('/index.html');
            }
        }
    }

    async function loadPhoto() {
        try {
            const response = await fetch('/user/foto', { credentials: 'same-origin' });
            if (!response.ok) {
                return;
            }
            const url = URL.createObjectURL(await response.blob());
            ['#profile-photo', '#profile-preview'].forEach(selector => {
                const image = $(selector);
                if (image.dataset.url) {
                    URL.revokeObjectURL(image.dataset.url);
                }
                image.dataset.url = url;
                image.src = url;
                image.hidden = false;
            });
            $('#profile-initial').hidden = true;
            $('#profile-preview-initial').hidden = true;
        } catch {
            // a foto é opcional
        }
    }

    async function saveProfile(event) {
        event.preventDefault();
        const feedbackBox = $('#profile-feedback');
        const name = $('#edit-name').value.trim();
        if (!name) {
            feedbackBox.textContent = 'Informe um nome.';
            return;
        }

        const data = new FormData();
        data.append('name', name);
        if (state.profilePhotoFile) {
            data.append('foto', state.profilePhotoFile);
        }

        const button = $('#profile-form').querySelector('button[type=submit]');
        button.disabled = true;
        try {
            await request('/user/profile', { method: 'POST', body: data });
            feedbackBox.textContent = 'Perfil atualizado com sucesso.';
            feedbackBox.className = 'form-feedback success';
            await loadUser();
            setTimeout(() => $('#profile-dialog').close(), 650);
        } catch (error) {
            feedbackBox.textContent = error.message;
            feedbackBox.className = 'form-feedback error';
        } finally {
            button.disabled = false;
        }
    }

    async function logout() {
        try {
            await request('/user/logout', { method: 'POST' });
            location.replace('/index.html');
        } catch (error) {
            msg(error.message, 'error');
        }
    }

    function closeProfileMenu() {
        $('#profile-menu').hidden = true;
        $('#profile-menu-toggle').setAttribute('aria-expanded', 'false');
    }

    function setupProfile() {
        $('#profile-menu-toggle').addEventListener('click', () => {
            const menu = $('#profile-menu');
            menu.hidden = !menu.hidden;
            $('#profile-menu-toggle').setAttribute('aria-expanded', String(!menu.hidden));
        });

        $('#edit-profile-button').addEventListener('click', () => {
            closeProfileMenu();
            $('#profile-feedback').textContent = '';
            $('#profile-dialog').showModal();
        });

        $('#profile-photo-input').addEventListener('change', event => {
            const file = event.target.files[0];
            if (!file) {
                return;
            }
            const validType = ['image/jpeg', 'image/png', 'image/webp'].includes(file.type);
            if (!validType || file.size > 2 * 1024 * 1024) {
                $('#profile-feedback').textContent = 'Escolha JPG, PNG ou WEBP com até 2 MB.';
                event.target.value = '';
                state.profilePhotoFile = null;
                return;
            }
            state.profilePhotoFile = file;
            $('#profile-preview').src = URL.createObjectURL(file);
            $('#profile-preview').hidden = false;
            $('#profile-preview-initial').hidden = true;
        });

        $('#profile-form').addEventListener('submit', saveProfile);
        $('#logout-button').addEventListener('click', logout);
        document.querySelectorAll('[data-close-dialog]').forEach(button => {
            button.addEventListener('click', () => $(`#${button.dataset.closeDialog}`).close());
        });
    }

    // -------------------------------------------------------------- acessibilidade

    function setupAccessibility() {
        const panel = $('#access-panel');
        const toggle = $('#access-toggle');

        const open = () => {
            panel.hidden = false;
            toggle.setAttribute('aria-expanded', 'true');
        };
        const close = () => {
            panel.hidden = true;
            toggle.setAttribute('aria-expanded', 'false');
        };

        toggle.addEventListener('click', () => (panel.hidden ? open() : close()));
        $('#menu-access-button').addEventListener('click', () => {
            open();
            closeProfileMenu();
        });
        $('#close-access').addEventListener('click', close);
        document.addEventListener('keydown', event => {
            if (event.key === 'Escape') {
                close();
                closeProfileMenu();
            }
        });

        document.querySelectorAll('[data-font-choice]').forEach(button => {
            button.addEventListener('click', () => {
                document.documentElement.style.setProperty('--font-scale', button.dataset.fontChoice === 'large' ? '1.15' : '1');
                document.querySelectorAll('[data-font-choice]').forEach(other => {
                    other.setAttribute('aria-pressed', String(other === button));
                });
            });
        });

        $('#contrast-toggle').addEventListener('click', event => {
            const enable = document.documentElement.dataset.contrast !== 'high';
            document.documentElement.dataset.contrast = enable ? 'high' : 'normal';
            event.currentTarget.setAttribute('aria-pressed', String(enable));
            event.currentTarget.textContent = enable ? 'Desativar alto contraste' : 'Ativar alto contraste';
        });

        document.querySelectorAll('[data-theme-choice]').forEach(button => {
            button.addEventListener('click', () => {
                document.documentElement.dataset.theme = button.dataset.themeChoice;
                localStorage.setItem('housebook.site.theme', button.dataset.themeChoice);
                document.querySelectorAll('[data-theme-choice]').forEach(other => {
                    other.setAttribute('aria-pressed', String(other === button));
                });
            });
        });
    }

    // ------------------------------------------------------------------- eventos

    function setupNavigation() {
        document.querySelectorAll('.nav-link').forEach(button => {
            button.addEventListener('click', () => showView(button.dataset.view));
        });
        $('#discover-button').addEventListener('click', () => showView('discover'));
        document.querySelectorAll('[data-open-discover]').forEach(button => {
            button.addEventListener('click', () => showView('discover'));
        });

        document.querySelectorAll('.filter-button').forEach(button => {
            button.addEventListener('click', () => {
                state.filter = button.dataset.filter;
                document.querySelectorAll('.filter-button').forEach(other => {
                    other.classList.toggle('active', other === button);
                });
                renderLibrary();
            });
        });

        $('#library-search').addEventListener('input', event => {
            state.libraryQuery = event.target.value;
            renderLibrary();
        });
        $('#book-search-form').addEventListener('submit', event => {
            event.preventDefault();
            searchBooks($('#book-search').value);
        });
        $('#global-search-form').addEventListener('submit', event => {
            event.preventDefault();
            searchBooks($('#global-search').value);
        });
        $('#retry-discover').addEventListener('click', loadNewBooks);
        $('#dialog-close').addEventListener('click', () => bookDialog.close());
    }

    function findForDetails(mode, id) {
        return mode === 'discover'
            ? state.discoveries.find(book => book.googleBookId === id)
            : state.library.find(book => String(book.userBookId) === id);
    }

    // Um único ouvinte trata todos os botões dos cartões e da janela de detalhes.
    document.addEventListener('click', async event => {
        const element = event.target.closest('[data-action]');
        if (!element) {
            return;
        }
        const { action, id, mode } = element.dataset;

        if (action === 'details') {
            const book = findForDetails(mode, id);
            if (book) {
                showDetails(book);
            }
            return;
        }

        if (bookDialog.open) {
            bookDialog.close();
        }
        if (action === 'add') {
            await acquire(id);
        } else if (action === 'read') {
            await markRead(id);
        } else if (action === 'open-reader') {
            await openReader(id);
        } else if (action === 'remove') {
            await removeBook(id);
        }
    });

    async function init() {
        setupNavigation();
        setupProfile();
        setupAccessibility();
        setupReader();
        await ensureCsrf();
        await loadUser();
        await Promise.all([loadLibrary(), loadNewBooks()]);
    }

    init();
})();