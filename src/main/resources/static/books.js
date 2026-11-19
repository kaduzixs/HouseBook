'use strict';
(() => {
    const API='/api/books', $=s=>document.querySelector(s);
    const state={library:[],discoveries:[],filter:'all',libraryQuery:'',csrf:null,csrfHeader:null,profilePhotoFile:null,reader:{userBookId:null,chapters:[],index:0,font:19,remote:false}};
    const libraryGrid=$('#library-grid'),discoverGrid=$('#discover-grid'),historyGrid=$('#history-grid'),feedback=$('#page-feedback'),bookDialog=$('#book-dialog');
    const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[c]));
    function msg(text,type=''){feedback.textContent=text;feedback.className=`page-feedback ${type}`}
    async function ensureCsrf(){if(state.csrf)return;try{const r=await fetch('/user/csrf',{credentials:'same-origin',headers:{Accept:'application/json'}});if(r.ok){const d=await r.json();state.csrf=d.token;state.csrfHeader=d.headerName||'X-CSRF-TOKEN'}}catch(e){console.warn('Não foi possível obter o token CSRF.',e)}}
    async function request(url,options={}){const method=(options.method||'GET').toUpperCase();const headers=new Headers(options.headers||{});headers.set('Accept','application/json');if(!['GET','HEAD','OPTIONS'].includes(method)){await ensureCsrf();if(state.csrf&&state.csrfHeader)headers.set(state.csrfHeader,state.csrf)}const response=await fetch(url,{credentials:'same-origin',...options,headers});if(!response.ok){let message=`Falha na operação (${response.status}).`;try{const ct=response.headers.get('content-type')||'';if(ct.includes('json')){const d=await response.json();message=d.detail||d.message||d.error||message}else{const t=await response.text();if(t)message=t}}catch{}if(response.status===401)throw new Error('Sua sessão expirou. Entre novamente.');throw new Error(response.status===403?'A solicitação foi bloqueada pela proteção CSRF. Atualize a página e tente novamente.':message)}if(response.status===204)return null;const ct=response.headers.get('content-type')||'';return ct.includes('json')?response.json():response.text()}
    function normalizeGoogle(item){const authors=Array.isArray(item.authors)?item.authors.map(a=>a.name).join(', '):'Autor desconhecido';const subjects=Array.isArray(item.subjects)?item.subjects.join(' • '):'';const formats=item.formats||{};return{googleBookId:String(item.id),title:item.title||'Título não informado',authors,description:subjects||'Obra gratuita do catálogo Project Gutenberg.',publisher:'Project Gutenberg',thumbnail:formats['image/jpeg']||'',isbn:'',language:Array.isArray(item.languages)?item.languages.join(', '):'',subjects,downloadCount:item.download_count||0,contentAvailable:!!(formats['text/plain; charset=utf-8']||formats['text/plain; charset=us-ascii']||formats['text/plain']),formats}}
    function normalizeBook(b){return{userBookId:b.userBookId,bookId:b.bookId,googleBookId:b.googleBookId,title:b.title||'Título não informado',authors:b.authors||'Autor desconhecido',description:b.description||'Descrição não disponível.',publisher:b.publisher||'',thumbnail:(b.thumbnail||''),language:b.language||'',subjects:b.subjects||'',downloadCount:b.downloadCount||0,contentAvailable:!!b.contentAvailable,formats:(()=>{try{return b.formatsJson?JSON.parse(b.formatsJson):{}}catch{return {}}})(),readingStatus:b.readingStatus||'NAO_INICIADO',personalRating:b.personalRating,currentChapter:b.currentChapter,currentParagraph:b.currentParagraph}}
    function googleItems(d){return Array.isArray(d?.results)?d.results.map(normalizeGoogle):[]}
    const statusLabel=s=>({WANT_TO_READ:'Quero ler',READING:'Em andamento',COMPLETED:'Concluído',PAUSED:'Pausado',NAO_INICIADO:'Não iniciado',EM_ANDAMENTO:'Em andamento',LIDO:'Concluído'}[s]||'Na biblioteca');
    function cover(b){return b.thumbnail?`<img class="book-cover" src="${esc(b.thumbnail)}" alt="Capa de ${esc(b.title)}" loading="lazy" referrerpolicy="no-referrer" onerror="this.outerHTML='<div class=&quot;cover-placeholder&quot;><span>✧</span><strong>${esc(b.title)}</strong></div>'">`:`<div class="cover-placeholder"><span>✧</span><strong>${esc(b.title)}</strong></div>`}
    function card(b, mode) {
        const owned = mode !== 'discover';

        const alreadyAdded = state.library.some(
            book => book.googleBookId === b.googleBookId
        );

        let actions = '';

        if (mode === 'discover') {
            actions = alreadyAdded
                ? `<button class="book-action" disabled>
                   ✓ Já adicionado
               </button>`
                : `<button class="book-action"
                       data-action="add"
                       data-id="${esc(b.googleBookId)}">
                   ＋ Adicionar
               </button>`;
        } else {
            actions += `
            <button class="book-action"
                    data-action="open-reader"
                    data-id="${esc(b.userBookId)}">
                📖 Ler agora
            </button>`;

            if (b.readingStatus !== 'COMPLETED') {
                actions += `
                <button class="book-action"
                        data-action="read"
                        data-id="${esc(b.userBookId)}">
                    ✓ Concluir
                </button>`;
            }

            actions += `
            <button class="book-action danger"
                    data-action="remove"
                    data-id="${esc(b.userBookId)}">
                Remover
            </button>`;
        }

        return `
        <article class="book-card">
            <div class="book-cover-wrap"
                 data-action="details"
                 data-id="${esc(owned ? b.userBookId : b.googleBookId)}"
                 data-mode="${mode}">
                ${cover(b)}
                ${owned ? `
                    <span class="book-status">
                        ${esc(statusLabel(b.readingStatus))}
                    </span>` : ''}
            </div>

            <div class="book-info">
                <h3 class="book-title">${esc(b.title)}</h3>
                <p class="book-author">${esc(b.authors)}</p>
                <div class="book-actions">${actions}</div>
            </div>
        </article>
    `;
    }
    function updateStats() {
        $('#total-books').textContent = state.library.length;

        $('#reading-books').textContent =
            state.library.filter(
                b => b.readingStatus === 'READING'
            ).length;

        $('#finished-books').textContent =
            state.library.filter(
                b => b.readingStatus === 'COMPLETED'
            ).length;
    }
    function renderLibrary(){const q=state.libraryQuery.toLocaleLowerCase('pt-BR');const list=state.library.filter(b=>(state.filter==='all'||b.readingStatus===state.filter)&&(!q||b.title.toLocaleLowerCase('pt-BR').includes(q)||b.authors.toLocaleLowerCase('pt-BR').includes(q)));libraryGrid.innerHTML=list.map(b=>card(b,'library')).join('');$('#library-empty').hidden=list.length>0;updateStats()}
    function renderDiscoveries(emptyTitle='Nenhum livro encontrado.',emptyText='Tente pesquisar por outro título ou autor.') {
        $('#discover-grid').innerHTML=state.discoveries.map(b=>card(b,'discover')).join('');
        $('#discover-empty').hidden=state.discoveries.length>0;
        $('#discover-empty h3').textContent=emptyTitle;
        $('#discover-empty p').textContent=emptyText;
    }
    function renderHistory() {
        const list = state.library.filter(
            b => b.readingStatus === 'COMPLETED'
        );

        historyGrid.innerHTML = list
            .map(b => card(b, 'history'))
            .join('');

        $('#history-empty').hidden = list.length > 0;
    }    function renderAll(){renderLibrary();renderDiscoveries();renderHistory()}
    async function loadLibrary(){try{const d=await request(`${API}/mine`);state.library=Array.isArray(d)?d.map(normalizeBook):[];renderAll()}catch(e){msg(`${e.message} (GET ${API}/mine)`, 'error')}}
    async function loadNewBooks(){
        msg('Preparando uma seleção de livros...');
        renderDiscoveries('Carregando descobertas...', 'Estamos consultando o catálogo de livros.');
        try {
            const data=await request(`${API}/new`);
            state.discoveries=googleItems(data);
            renderDiscoveries(
                state.discoveries.length ? '' : 'Nenhum livro disponível neste momento.',
                state.discoveries.length ? '' : 'A Gutendex não retornou obras para a seleção inicial. Tente novamente em instantes.'
            );
            if(state.discoveries.length) msg(`${state.discoveries.length} livros disponíveis para descobrir.`);
            else msg('A consulta foi concluída, mas não retornou livros.','error');
        } catch(e) {
            state.discoveries=[];
            renderDiscoveries('Não foi possível carregar os livros.', 'Confira sua conexão e tente novamente.');
            msg(`${e.message} (GET ${API}/new)`, 'error');
        }
    }
    async function searchBooks(q){
        if(!q.trim()) return;
        showView('discover');
        $('#book-search').value=q;
        msg('Pesquisando no catálogo gratuito Gutendex...');
        $('#discover-title').textContent='Resultados da pesquisa';
        $('#discover-eyebrow').textContent='RESULTADOS ENCONTRADOS';
        try {
            const params=new URLSearchParams({q:q.trim()});
            if($('#book-language')?.value)params.set('language',$('#book-language').value);
            if($('#book-format')?.value)params.set('mimeType',$('#book-format').value);
            if($('#book-topic')?.value)params.set('topic',$('#book-topic').value);
            state.discoveries=googleItems(await request(`${API}/search?${params.toString()}`));
            renderDiscoveries(
                'Nenhum livro encontrado.',
                'Não encontramos resultados para essa pesquisa. Tente outro título, autor ou assunto.'
            );
            msg(state.discoveries.length?`${state.discoveries.length} resultado(s) encontrado(s).`:'Nenhum livro encontrado.');
        } catch(e) {
            state.discoveries=[];
            renderDiscoveries('A pesquisa não pôde ser concluída.', 'Confira sua conexão e tente novamente.');
            msg(`${e.message} (pesquisa)`, 'error');
        }
    }
    async function acquire(id){try{await request(`${API}/acquire/${encodeURIComponent(id)}`,{method:'POST'});msg('Livro adicionado à sua biblioteca!','success');await loadLibrary()}catch(e){msg(e.message,'error')}}
    async function markRead(id){try{await request(`${API}/history/${encodeURIComponent(id)}`,{method:'POST'});msg('Livro marcado como concluído!','success');await loadLibrary()}catch(e){msg(e.message,'error')}}
    async function removeBook(id){if(!confirm('Deseja remover este livro da sua biblioteca?'))return;try{await request(`${API}/${encodeURIComponent(id)}`,{method:'DELETE'});msg('Livro removido da sua biblioteca.','success');await loadLibrary()}catch(e){msg(e.message,'error')}}
    function showDetails(book){
        const image=book.thumbnail?`<img src="${esc(book.thumbnail)}" alt="Capa de ${esc(book.title)}">`:`<div class="cover-placeholder"><span>✧</span><strong>${esc(book.title)}</strong></div>`;
        const formats=Object.entries(book.formats||{}).filter(([type,url])=>/^https:\/\//i.test(url)).map(([type,url])=>`<a href="${esc(url)}" target="_blank" rel="noopener noreferrer">${esc(type)}</a>`).join(' · ');
        $('#dialog-content').innerHTML=`<div class="dialog-book">${image}<div><p class="eyebrow">DETALHES DA OBRA</p><h2>${esc(book.title)}</h2><p><strong>Autor:</strong> ${esc(book.authors)}</p><p><strong>Fonte:</strong> ${esc(book.publisher||'Project Gutenberg')}</p><p><strong>Idioma:</strong> ${esc(book.language||'Não informado')}</p><p><strong>Downloads:</strong> ${Number(book.downloadCount||0).toLocaleString('pt-BR')}</p><p><strong>Assuntos:</strong> ${esc(book.subjects||book.description||'Não informado')}</p><p><strong>Formatos disponíveis:</strong> ${formats||'Não informado'}</p><p><strong>Leitura no site:</strong> ${book.contentAvailable?'Texto integral disponível':'Sem texto TXT compatível'}</p></div></div>`;
        bookDialog.showModal();
    }
    function showView(name){const views={library:$('#library-view'),discover:$('#discover-view'),history:$('#history-view')};Object.entries(views).forEach(([k,v])=>v.hidden=k!==name);document.querySelectorAll('.nav-link').forEach(b=>b.classList.toggle('active',b.dataset.view===name));if(name==='library')renderLibrary();if(name==='history')renderHistory()}
    const readerDialog=$('#reader-dialog');
    function renderReader(){
        const r=state.reader, chapter=r.chapters[r.index];
        $('#reader-chapter-list').innerHTML=r.chapters.map((c,i)=>`<li><button type="button" data-chapter-index="${i}" class="${i===r.index?'active':''}">${esc(c.title)}</button></li>`).join('');
        $('#reader-empty').hidden=r.chapters.length>0;$('#reader-content').hidden=!chapter;
        $('#reader-chapter-label').textContent=chapter?`CAPÍTULO ${chapter.chapterNumber}`:'LEITURA';
        $('#reader-chapter-title').textContent=chapter?.title||'Conteúdo não disponível';
        $('#reader-content').textContent=chapter?.content||'';
        $('#reader-position').textContent=chapter?`Capítulo ${r.index+1} de ${r.chapters.length}`:'Sem conteúdo';
        $('#reader-previous').disabled=!chapter||r.index===0;$('#reader-next').disabled=!chapter||r.index>=r.chapters.length-1;
        $('#reader-book-title').textContent=state.library.find(b=>String(b.userBookId)===String(r.userBookId))?.title||'HouseBook Reader';
    }
    async function openReader(id){
        state.reader.userBookId=id;state.reader.index=0;state.reader.chapters=[];state.reader.remote=true;
        readerDialog.showModal();renderReader();
        try{
            const text=await request(`${API}/content/${encodeURIComponent(id)}`);
            const clean=String(text||'').replace(/\\r/g,'').trim();
            const chunks=clean.match(/[\\s\\S]{1,18000}/g)||[];
            state.reader.chapters=chunks.map((content,index)=>({chapterNumber:index+1,title:chunks.length===1?'Texto integral':`Parte ${index+1}`,content}));
            renderReader();
            if(!state.reader.chapters.length)msg('O texto integral não foi disponibilizado para esta obra.','error');
        }catch(e){$('#reader-empty').hidden=false;$('#reader-empty h2').textContent='Texto integral indisponível';$('#reader-empty p').textContent=e.message;msg(e.message,'error')}
    }
    async function saveReaderProgress(){const c=state.reader.chapters[state.reader.index];if(!c||state.reader.remote)return;try{await request(`/api/reader/${state.reader.userBookId}/progress`,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({currentChapter:c.chapterNumber,currentParagraph:0})})}catch(e){msg(`Não foi possível salvar o progresso: ${e.message}`,'error')}}
    async function importReaderText(e){e.preventDefault();const file=$('#reader-file').files[0];if(!file)return;const data=new FormData();data.append('file',file);try{state.reader.chapters=await request(`/api/reader/${state.reader.userBookId}/content`,{method:'POST',body:data});state.reader.remote=false;state.reader.index=0;renderReader();await loadLibrary();msg('Texto importado. Boa leitura!','success')}catch(err){msg(err.message,'error')}}
    $('#reader-upload-form').addEventListener('submit',importReaderText);
    $('#reader-chapter-list').addEventListener('click',async e=>{const b=e.target.closest('[data-chapter-index]');if(!b)return;state.reader.index=Number(b.dataset.chapterIndex);renderReader();await saveReaderProgress()});
    $('#reader-previous').addEventListener('click',async()=>{if(state.reader.index>0){state.reader.index--;renderReader();await saveReaderProgress()}});
    $('#reader-next').addEventListener('click',async()=>{if(state.reader.index<state.reader.chapters.length-1){state.reader.index++;renderReader();await saveReaderProgress()}});
    $('#reader-smaller').addEventListener('click',()=>{state.reader.font=Math.max(14,state.reader.font-1);$('.reader-content').style.fontSize=`${state.reader.font}px`});
    $('#reader-larger').addEventListener('click',()=>{state.reader.font=Math.min(30,state.reader.font+1);$('.reader-content').style.fontSize=`${state.reader.font}px`});
    $('#reader-theme').addEventListener('change',e=>$('.reader-page').dataset.theme=e.target.value);
    $('#reader-close').addEventListener('click',()=>readerDialog.close());$('#reader-back').addEventListener('click',()=>readerDialog.close());
    async function loadUser(){try{const u=await request('/user/me');const name=u.name||'Aventureiro';$('#profile-name').textContent=name;$('#menu-profile-name').textContent=name;$('#profile-email').textContent=u.email||'';$('#profile-initial').textContent=name.trim().charAt(0).toUpperCase()||'H';$('#profile-preview-initial').textContent=$('#profile-initial').textContent;$('#edit-name').value=name;if(u.hasPhoto)await loadPhoto()}catch(e){msg(e.message,'error');if(e.message.includes('sessão'))location.replace('/index.html')}}
    async function loadPhoto(){try{const r=await fetch('/user/foto',{credentials:'same-origin'});if(!r.ok)return;const blob=await r.blob();const url=URL.createObjectURL(blob);['#profile-photo','#profile-preview'].forEach(sel=>{const img=$(sel);if(img.dataset.url)URL.revokeObjectURL(img.dataset.url);img.dataset.url=url;img.src=url;img.hidden=false});$('#profile-initial').hidden=true;$('#profile-preview-initial').hidden=true}catch{}}
    async function saveProfile(e){e.preventDefault();const name=$('#edit-name').value.trim();if(!name){$('#profile-feedback').textContent='Informe um nome.';return}const data=new FormData();data.append('name',name);if(state.profilePhotoFile)data.append('foto',state.profilePhotoFile);const btn=$('#profile-form').querySelector('button[type=submit]');btn.disabled=true;try{await request('/user/profile',{method:'POST',body:data});$('#profile-feedback').textContent='Perfil atualizado com sucesso.';$('#profile-feedback').className='form-feedback success';await loadUser();setTimeout(()=>$('#profile-dialog').close(),650)}catch(err){$('#profile-feedback').textContent=err.message;$('#profile-feedback').className='form-feedback error'}finally{btn.disabled=false}}
    async function logout(){try{await request('/user/logout',{method:'POST'});location.replace('/index.html')}catch(e){msg(e.message,'error')}}
    function setupAccessibility(){const panel=$('#access-panel'),toggle=$('#access-toggle');function open(){panel.hidden=false;toggle.setAttribute('aria-expanded','true')}function close(){panel.hidden=true;toggle.setAttribute('aria-expanded','false')}toggle.addEventListener('click',()=>panel.hidden?open():close());$('#menu-access-button').addEventListener('click',()=>{open();$('#profile-menu').hidden=true;$('#profile-menu-toggle').setAttribute('aria-expanded','false')});$('#close-access').addEventListener('click',close);document.addEventListener('keydown',e=>{if(e.key==='Escape'){close();$('#profile-menu').hidden=true;$('#profile-menu-toggle').setAttribute('aria-expanded','false')}});document.querySelectorAll('[data-font-choice]').forEach(b=>b.addEventListener('click',()=>{document.documentElement.style.setProperty('--font-scale',b.dataset.fontChoice==='large'?'1.15':'1');document.querySelectorAll('[data-font-choice]').forEach(x=>x.setAttribute('aria-pressed',String(x===b)))}));$('#contrast-toggle').addEventListener('click',e=>{const on=document.documentElement.dataset.contrast!=='high';document.documentElement.dataset.contrast=on?'high':'normal';e.currentTarget.setAttribute('aria-pressed',String(on));e.currentTarget.textContent=on?'Desativar alto contraste':'Ativar alto contraste'});document.querySelectorAll('[data-theme-choice]').forEach(b=>b.addEventListener('click',()=>{document.documentElement.dataset.theme=b.dataset.themeChoice;document.querySelectorAll('[data-theme-choice]').forEach(x=>x.setAttribute('aria-pressed',String(x===b)))}))}
    $('#profile-menu-toggle').addEventListener('click',()=>{const menu=$('#profile-menu');menu.hidden=!menu.hidden;$('#profile-menu-toggle').setAttribute('aria-expanded',String(!menu.hidden))});$('#edit-profile-button').addEventListener('click',()=>{$('#profile-menu').hidden=true;$('#profile-menu-toggle').setAttribute('aria-expanded','false');$('#profile-feedback').textContent='';$('#profile-dialog').showModal()});$('#profile-photo-input').addEventListener('change',e=>{const f=e.target.files[0];if(!f)return;if(!['image/jpeg','image/png','image/webp'].includes(f.type)||f.size>2*1024*1024){$('#profile-feedback').textContent='Escolha JPG, PNG ou WEBP com até 2 MB.';e.target.value='';state.profilePhotoFile=null;return}state.profilePhotoFile=f;const url=URL.createObjectURL(f);$('#profile-preview').src=url;$('#profile-preview').hidden=false;$('#profile-preview-initial').hidden=true});$('#profile-form').addEventListener('submit',saveProfile);document.querySelectorAll('[data-close-dialog]').forEach(b=>b.addEventListener('click',()=>$('#'+b.dataset.closeDialog).close()));
    document.querySelectorAll('.nav-link').forEach(b=>b.addEventListener('click',()=>showView(b.dataset.view)));$('#discover-button').addEventListener('click',()=>showView('discover'));document.querySelectorAll('[data-open-discover]').forEach(b=>b.addEventListener('click',()=>showView('discover')));document.querySelectorAll('.filter-button').forEach(b=>b.addEventListener('click',()=>{state.filter=b.dataset.filter;document.querySelectorAll('.filter-button').forEach(x=>x.classList.toggle('active',x===b));renderLibrary()}));$('#library-search').addEventListener('input',e=>{state.libraryQuery=e.target.value;renderLibrary()});$('#book-search-form').addEventListener('submit',e=>{e.preventDefault();searchBooks($('#book-search').value)});$('#retry-discover').addEventListener('click',loadNewBooks);$('#global-search-form').addEventListener('submit',e=>{e.preventDefault();searchBooks($('#global-search').value)});
    document.addEventListener('click',async e=>{const el=e.target.closest('[data-action]');if(!el)return;const {action,id,mode}=el.dataset;if(action==='add')return acquire(id);if(action==='read')return markRead(id);if(action==='open-reader')return openReader(id);if(action==='remove')return removeBook(id);if(action==='details'){const list=mode==='discover'?state.discoveries:state.library;const book=mode==='discover'?list.find(b=>b.googleBookId===id):list.find(b=>String(b.userBookId)===id);if(book)showDetails(book)}});$('#dialog-close').addEventListener('click',()=>bookDialog.close());$('#logout-button').addEventListener('click',logout);setupAccessibility();
    async function init(){await ensureCsrf();await loadUser();await Promise.all([loadLibrary(),loadNewBooks()])}init();
})();