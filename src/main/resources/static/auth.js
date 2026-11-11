
'use strict';

document.addEventListener('DOMContentLoaded', () => {

    // ==========================================
    // ELEMENTOS
    // ==========================================

    const loginTab = document.getElementById('login-tab');
    const registerTab = document.getElementById('register-tab');

    const loginPanel = document.getElementById('login-panel');
    const registerPanel = document.getElementById('register-panel');

    const loginForm = document.getElementById('login-form');
    const registerForm = document.getElementById('register-form');

    const loginFeedback = document.getElementById('login-feedback');
    const registerFeedback = document.getElementById('register-feedback');

    const photoInput = document.getElementById('profile-photo');
    const photoPreview = document.getElementById('photo-preview');
    const avatarPlaceholder = document.getElementById('avatar-placeholder');

    const accessToggle = document.getElementById('access-toggle');
    const accessPanel = document.getElementById('access-panel');
    const closeAccess = document.getElementById('close-access');

    let previewUrl = null;

    // ==========================================
    // TROCA ENTRE LOGIN E CADASTRO
    // ==========================================

    function switchAuth(mode) {
        const isLogin = mode === 'login';

        loginPanel.hidden = !isLogin;
        registerPanel.hidden = isLogin;

        loginTab.classList.toggle('active', isLogin);
        registerTab.classList.toggle('active', !isLogin);

        loginTab.setAttribute('aria-selected', String(isLogin));
        registerTab.setAttribute('aria-selected', String(!isLogin));

        loginTab.tabIndex = isLogin ? 0 : -1;
        registerTab.tabIndex = isLogin ? -1 : 0;

        clearFeedback();
    }

    loginTab.addEventListener('click', () => {
        switchAuth('login');
    });

    registerTab.addEventListener('click', () => {
        switchAuth('register');
    });

    document.querySelectorAll('[data-open-register]').forEach(button => {
        button.addEventListener('click', () => {
            switchAuth('register');
        });
    });

    document.querySelectorAll('[data-open-login]').forEach(button => {
        button.addEventListener('click', () => {
            switchAuth('login');
        });
    });

    // Navegação por teclado nas abas
    document.querySelectorAll('.switch-tab').forEach(tab => {
        tab.addEventListener('keydown', event => {
            if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
                return;
            }

            event.preventDefault();

            if (tab === loginTab) {
                switchAuth('register');
                registerTab.focus();
            } else {
                switchAuth('login');
                loginTab.focus();
            }
        });
    });

    // ==========================================
    // MENSAGENS
    // ==========================================

    function clearFeedback() {
        [loginFeedback, registerFeedback].forEach(element => {
            element.textContent = '';
            element.className = 'form-feedback';
        });
    }

    function setFeedback(element, message, type = '') {
        element.textContent = message;
        element.className = `form-feedback ${type}`;
    }

    function setBusy(form, busy, text) {
        const button = form.querySelector('.submit-button');
        const label = button.querySelector('span');

        button.disabled = busy;
        label.textContent = busy ? 'Aguarde...' : text;
    }

    // ==========================================
    // RESPOSTAS DO SERVIDOR
    // ==========================================

    async function readResponse(response) {
        const contentType = response.headers.get('content-type') || '';
        let data = null;

        if (contentType.includes('application/json')) {
            data = await response.json();
        } else {
            data = await response.text();
        }

        const serverMessage = typeof data === 'string'
            ? data
            : data?.message || data?.detail || data?.error;

        if (!response.ok || (data && typeof data === 'object' && data.success === false)) {
            throw new Error(
                serverMessage || `Não foi possível concluir a operação (${response.status}).`
            );
        }

        return {
            ok: true,
            message: serverMessage || 'Operação realizada com sucesso.',
            data
        };
    }

    function csrfHeaders() {
        const headers = {};

        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content;

        if (token && header) {
            headers[header] = token;
        }

        return headers;
    }

    // ==========================================
    // MOSTRAR E OCULTAR SENHAS
    // ==========================================

    document.querySelectorAll('[data-password-target]').forEach(button => {
        button.addEventListener('click', () => {
            const input = document.getElementById(button.dataset.passwordTarget);

            const showing = input.type === 'password';

            input.type = showing ? 'text' : 'password';

            button.setAttribute('aria-pressed', String(showing));
            button.setAttribute(
                'aria-label',
                showing ? 'Ocultar senha' : 'Mostrar senha'
            );
        });
    });

    // ==========================================
    // PRÉ-VISUALIZAÇÃO DA FOTO
    // ==========================================

    photoInput.addEventListener('change', () => {
        const file = photoInput.files[0];

        if (!file) {
            return;
        }

        const allowedTypes = [
            'image/jpeg',
            'image/png',
            'image/webp'
        ];

        if (!allowedTypes.includes(file.type)) {
            photoInput.value = '';

            setFeedback(
                registerFeedback,
                'Escolha uma imagem JPG, PNG ou WEBP.',
                'error'
            );

            return;
        }

        if (file.size > 2 * 1024 * 1024) {
            photoInput.value = '';

            setFeedback(
                registerFeedback,
                'A imagem deve ter no máximo 2 MB.',
                'error'
            );

            return;
        }

        if (previewUrl) {
            URL.revokeObjectURL(previewUrl);
        }

        previewUrl = URL.createObjectURL(file);

        photoPreview.src = previewUrl;
        photoPreview.hidden = false;
        avatarPlaceholder.hidden = true;

        clearFeedback();
    });

    // ==========================================
    // LOGIN
    // ==========================================

    loginForm.addEventListener('submit', async event => {
        event.preventDefault();
        clearFeedback();

        const email = loginForm.elements.email.value.trim();
        const password = loginForm.elements.password.value;

        if (!email || !loginForm.elements.email.checkValidity()) {
            setFeedback(
                loginFeedback,
                'Informe um endereço de e-mail válido.',
                'error'
            );

            return;
        }

        if (!password) {
            setFeedback(
                loginFeedback,
                'Informe sua senha.',
                'error'
            );

            return;
        }

        const body = new URLSearchParams();

        body.set('email', email);
        body.set('password', password);

        setBusy(loginForm, true, 'Entrar na biblioteca');

        try {
            const response = await fetch('/user/login', {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                    ...csrfHeaders()
                },
                body
            });

            await readResponse(response);

            setFeedback(
                loginFeedback,
                'Acesso confirmado! Abrindo sua biblioteca...',
                'success'
            );

            window.location.replace('/books.html');

        } catch (error) {
            setFeedback(
                loginFeedback,
                error.message || 'Não foi possível entrar. Confira seus dados.',
                'error'
            );

            setBusy(loginForm, false, 'Entrar na biblioteca');
        }
    });

    // ==========================================
    // CADASTRO
    // ==========================================

    registerForm.addEventListener('submit', async event => {
        event.preventDefault();
        clearFeedback();

        const name = registerForm.elements.name.value.trim();
        const email = registerForm.elements.email.value.trim();
        const password = registerForm.elements.password.value;

        if (!name) {
            setFeedback(
                registerFeedback,
                'Informe seu nome.',
                'error'
            );

            return;
        }

        if (!email || !registerForm.elements.email.checkValidity()) {
            setFeedback(
                registerFeedback,
                'Informe um endereço de e-mail válido.',
                'error'
            );

            return;
        }

        if (password.length < 8) {
            setFeedback(
                registerFeedback,
                'A senha precisa ter pelo menos 8 caracteres.',
                'error'
            );

            return;
        }

        const formData = new FormData();

        formData.append('name', name);
        formData.append('email', email);
        formData.append('password', password);

        const photo = photoInput.files[0];

        if (photo) {
            formData.append('foto', photo);
        }

        setBusy(registerForm, true, 'Criar minha conta');

        try {
            const response = await fetch('/user/register', {
                method: 'POST',
                credentials: 'same-origin',
                headers: csrfHeaders(),
                body: formData
            });

            const result = await readResponse(response);

            setFeedback(
                registerFeedback,
                result.message || 'Conta criada com sucesso! Agora entre na sua biblioteca.',
                'success'
            );

            registerForm.reset();

            if (previewUrl) {
                URL.revokeObjectURL(previewUrl);
                previewUrl = null;
            }

            photoPreview.removeAttribute('src');
            photoPreview.hidden = true;
            avatarPlaceholder.hidden = false;

            loginForm.elements.email.value = email;

            switchAuth('login');

            setFeedback(
                loginFeedback,
                'Sua conta foi criada! Entre para acessar sua biblioteca.',
                'success'
            );

        } catch (error) {
            setFeedback(
                registerFeedback,
                error.message || 'Não foi possível criar sua conta.',
                'error'
            );

        } finally {
            setBusy(registerForm, false, 'Criar minha conta');
        }
    });

    // ==========================================
    // ACESSIBILIDADE
    // ==========================================

    accessToggle.addEventListener('click', () => {
        const opening = accessPanel.hidden;

        accessPanel.hidden = !opening;

        accessToggle.setAttribute('aria-expanded', String(opening));
    });

    closeAccess.addEventListener('click', () => {
        accessPanel.hidden = true;
        accessToggle.setAttribute('aria-expanded', 'false');
        accessToggle.focus();
    });

    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && !accessPanel.hidden) {
            accessPanel.hidden = true;
            accessToggle.setAttribute('aria-expanded', 'false');
        }
    });

    // Tamanho do texto
    document.querySelectorAll('[data-font-choice]').forEach(button => {
        button.addEventListener('click', () => {
            const size = button.dataset.fontChoice;

            document.documentElement.dataset.font = size;
            document.documentElement.style.setProperty(
                '--font-scale',
                size === 'large' ? '1.15' : '1'
            );

            document.querySelectorAll('[data-font-choice]').forEach(item => {
                item.setAttribute(
                    'aria-pressed',
                    String(item === button)
                );
            });
        });
    });

    // Alto contraste
    const contrastToggle = document.getElementById('contrast-toggle');

    contrastToggle.addEventListener('click', () => {
        const enabled = document.documentElement.dataset.contrast !== 'high';

        document.documentElement.dataset.contrast = enabled ? 'high' : 'normal';

        contrastToggle.setAttribute('aria-pressed', String(enabled));

        contrastToggle.textContent = enabled
            ? 'Desativar alto contraste'
            : 'Ativar alto contraste';
    });

    // Temas
    document.querySelectorAll('[data-theme-choice]').forEach(button => {
        button.addEventListener('click', () => {
            const theme = button.dataset.themeChoice;

            document.documentElement.dataset.theme = theme;

            document.querySelectorAll('[data-theme-choice]').forEach(item => {
                item.setAttribute(
                    'aria-pressed',
                    String(item === button)
                );
            });
        });
    });

    // Estado inicial
    switchAuth('login');

});
