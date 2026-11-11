
'use strict';

(() => {
    const root = document.documentElement;
    const loginTab = document.getElementById('login-tab');
    const registerTab = document.getElementById('register-tab');
    const loginPanel = document.getElementById('login-panel');
    const registerPanel = document.getElementById('register-panel');
    const loginForm = document.getElementById('login-form');
    const registerForm = document.getElementById('register-form');
    const photoInput = document.getElementById('profile-photo');
    const photoPreview = document.getElementById('photo-preview');
    const avatarPlaceholder = document.getElementById('avatar-placeholder');
    const MAX_PHOTO_SIZE = 2 * 1024 * 1024;
    const ALLOWED_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
    let previewUrl = null;

    function showPanel(panelName) {
        const showLogin = panelName === 'login';
        loginPanel.hidden = !showLogin;
        registerPanel.hidden = showLogin;
        loginTab.classList.toggle('active', showLogin);
        registerTab.classList.toggle('active', !showLogin);
        loginTab.setAttribute('aria-selected', String(showLogin));
        registerTab.setAttribute('aria-selected', String(!showLogin));
        clearFeedback();
    }

    function clearFeedback() {
        for (const id of ['login-feedback', 'register-feedback']) {
            const element = document.getElementById(id);
            element.textContent = '';
            element.className = 'form-feedback';
        }
    }

    function setFeedback(id, message, type) {
        const element = document.getElementById(id);
        element.textContent = message;
        element.className = `form-feedback ${type}`;
    }

    function setBusy(form, busy, label) {
        const button = form.querySelector('.submit-button');
        if (!button) return;
        button.disabled = busy;
        button.querySelector('span').textContent = busy ? 'Aguarde...' : label;
    }

    async function sendForm(url, body) {
        const response = await fetch(url, {
            method: 'POST',
            body,
            credentials: 'same-origin',
            headers: { 'Accept': 'text/plain' }
        });
        const message = await response.text();
        return { ok: response.ok, status: response.status, message };
    }

    loginTab.addEventListener('click', () => showPanel('login'));
    registerTab.addEventListener('click', () => showPanel('register'));
    document.querySelectorAll('[data-open-register]').forEach(button =>
        button.addEventListener('click', () => showPanel('register')));
    document.querySelectorAll('[data-open-login]').forEach(button =>
        button.addEventListener('click', () => showPanel('login')));

    document.querySelectorAll('[data-password-target]').forEach(button => {
        button.addEventListener('click', () => {
            const input = document.getElementById(button.dataset.passwordTarget);
            if (!input) return;
            const reveal = input.type === 'password';
            input.type = reveal ? 'text' : 'password';
            button.setAttribute('aria-pressed', String(reveal));
            button.setAttribute('aria-label', reveal ? 'Ocultar senha' : 'Mostrar senha');
        });
    });

    photoInput.addEventListener('change', () => {
        const file = photoInput.files && photoInput.files[0];
        if (!file) {
            clearPhotoPreview();
            return;
        }
        if (!ALLOWED_TYPES.has(file.type)) {
            photoInput.value = '';
            clearPhotoPreview();
            setFeedback('register-feedback', 'Escolha uma imagem JPG, PNG ou WEBP.', 'error');
            return;
        }
        if (file.size > MAX_PHOTO_SIZE) {
            photoInput.value = '';
            clearPhotoPreview();
            setFeedback('register-feedback', 'A foto deve ter no máximo 2 MB.', 'error');
            return;
        }
        clearFeedback();
        if (previewUrl) URL.revokeObjectURL(previewUrl);
        previewUrl = URL.createObjectURL(file);
        photoPreview.src = previewUrl;
        photoPreview.hidden = false;
        avatarPlaceholder.hidden = true;
    });

    function clearPhotoPreview() {
        if (previewUrl) URL.revokeObjectURL(previewUrl);
        previewUrl = null;
        photoPreview.removeAttribute('src');
        photoPreview.hidden = true;
        avatarPlaceholder.hidden = false;
    }

    async function carregarFotoPerfil() {
        const profilePhoto = document.getElementById('logged-profile-photo');
        try {
            const response = await fetch('/user/foto', {
                method: 'GET',
                credentials: 'same-origin'
            });
            if (!response.ok) {
                profilePhoto.hidden = true;
                profilePhoto.removeAttribute('src');
                return;
            }
            const imageBlob = await response.blob();
            const imageUrl = URL.createObjectURL(imageBlob);
            if (profilePhoto.dataset.objectUrl) {
                URL.revokeObjectURL(profilePhoto.dataset.objectUrl);
            }
            profilePhoto.dataset.objectUrl = imageUrl;
            profilePhoto.src = imageUrl;
            profilePhoto.hidden = false;
        } catch {
            profilePhoto.hidden = true;
        }
    }

    carregarFotoPerfil();

    loginForm.addEventListener('submit', async event => {
        event.preventDefault();
        clearFeedback();
        const email = loginForm.elements.email.value.trim();
        const password = loginForm.elements.password.value;
        if (!email || !loginForm.elements.email.checkValidity() || !password) {
            setFeedback('login-feedback', 'Informe um e-mail válido e sua senha.', 'error');
            return;
        }

        const body = new URLSearchParams();
        body.set('email', email);
        body.set('password', password);
        setBusy(loginForm, true, 'Entrar na biblioteca');
        try {
            const result = await sendForm('/user/login', body);
            if (result.ok) {
                setFeedback('login-feedback', 'Acesso confirmado. Sua biblioteca está pronta!', 'success');
                loginForm.reset();
                await carregarFotoPerfil();
            } else {
                setFeedback('login-feedback', result.message || 'Não foi possível entrar. Confira seus dados.', 'error');
            }
        } catch {
            setFeedback('login-feedback', 'Não foi possível conectar ao servidor. Tente novamente.', 'error');
        } finally {
            setBusy(loginForm, false, 'Entrar na biblioteca');
        }
    });

    registerForm.addEventListener('submit', async event => {
        event.preventDefault();
        clearFeedback();
        const name = registerForm.elements.name.value.trim();
        const email = registerForm.elements.email.value.trim();
        const password = registerForm.elements.password.value;
        const photo = photoInput.files && photoInput.files[0];

        if (!name || name.length > 100) {
            setFeedback('register-feedback', 'Informe um nome com até 100 caracteres.', 'error');
            return;
        }
        if (!email || !registerForm.elements.email.checkValidity() || email.length > 254) {
            setFeedback('register-feedback', 'Informe um e-mail válido.', 'error');
            return;
        }
        if (password.length < 8 || password.length > 72) {
            setFeedback('register-feedback', 'A senha deve ter entre 8 e 72 caracteres.', 'error');
            return;
        }
        if (photo && (!ALLOWED_TYPES.has(photo.type) || photo.size > MAX_PHOTO_SIZE)) {
            setFeedback('register-feedback', 'A foto deve ser JPG, PNG ou WEBP e ter no máximo 2 MB.', 'error');
            return;
        }

        const body = new FormData();
        body.set('name', name);
        body.set('email', email);
        body.set('password', password);
        if (photo) body.set('foto', photo);

        setBusy(registerForm, true, 'Criar minha conta');
        try {
            const result = await sendForm('/user/register', body);
            if (result.ok) {
                setFeedback('register-feedback', 'Conta criada! Agora entre com seu e-mail e senha.', 'success');
                document.getElementById('login-email').value = email;
                registerForm.reset();
                clearPhotoPreview();
                showPanel('login');
                setFeedback('login-feedback', 'Cadastro concluído. Entre para acessar sua biblioteca.', 'success');
            } else {
                setFeedback('register-feedback', result.message || 'Não foi possível criar a conta.', 'error');
            }
        } catch {
            setFeedback('register-feedback', 'Não foi possível conectar ao servidor. Tente novamente.', 'error');
        } finally {
            setBusy(registerForm, false, 'Criar minha conta');
        }
    });

    const accessToggle = document.getElementById('access-toggle');
    const accessPanel = document.getElementById('access-panel');
    const closeAccess = document.getElementById('close-access');
    function setAccessOpen(open) {
        accessPanel.hidden = !open;
        accessToggle.setAttribute('aria-expanded', String(open));
        if (open) closeAccess.focus();
        else accessToggle.focus();
    }
    accessToggle.addEventListener('click', () => setAccessOpen(accessPanel.hidden));
    closeAccess.addEventListener('click', () => setAccessOpen(false));
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && !accessPanel.hidden) setAccessOpen(false);
    });

    document.querySelectorAll('[data-font-choice]').forEach(button => {
        button.addEventListener('click', () => {
            const choice = button.dataset.fontChoice;
            root.dataset.font = choice;
            document.querySelectorAll('[data-font-choice]').forEach(option =>
                option.setAttribute('aria-pressed', String(option === button)));
        });
    });

    document.querySelectorAll('[data-theme-choice]').forEach(button => {
        button.addEventListener('click', () => {
            root.dataset.theme = button.dataset.themeChoice;
            document.querySelectorAll('[data-theme-choice]').forEach(option =>
                option.setAttribute('aria-pressed', String(option === button)));
        });
    });

    const contrastToggle = document.getElementById('contrast-toggle');
    contrastToggle.addEventListener('click', () => {
        const enabled = root.dataset.contrast !== 'high';
        root.dataset.contrast = enabled ? 'high' : 'normal';
        contrastToggle.setAttribute('aria-pressed', String(enabled));
        contrastToggle.textContent = enabled ? 'Desativar alto contraste' : 'Ativar alto contraste';
    });
})();
