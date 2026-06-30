console.log('login.js loaded successfully');

let isLogin = true;

function toggleMode() {
    isLogin = !isLogin;
    document.getElementById('form-title').textContent = isLogin ? 'Login' : 'Register';
    document.getElementById('submit-btn').textContent = isLogin ? 'Login' : 'Register';
    document.getElementById('toggle-text').textContent = isLogin ? "Don't have an account?" : "Already have an account?";
    document.getElementById('toggle-btn').textContent = isLogin ? 'Register' : 'Login';
    document.getElementById('error-msg').classList.remove('show');
}

function showError(msg) {
    const el = document.getElementById('error-msg');
    el.textContent = msg;
    el.classList.add('show');
}

async function handleSubmit() {
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;

    if (!username || !password) {
        showError('Please enter username and password');
        return;
    }

    const btn = document.getElementById('submit-btn');
    btn.disabled = true;
    btn.textContent = 'Loading...';

    const endpoint = isLogin ? '/api/auth/login' : '/api/auth/register';

    try {
        const resp = await fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        const data = await resp.json();

        if (!resp.ok) {
            showError(data.error || 'Request failed');
            btn.disabled = false;
            btn.textContent = isLogin ? 'Login' : 'Register';
            return;
        }

        localStorage.setItem('token', data.token);
        localStorage.setItem('username', data.username);
        sessionStorage.setItem('token', data.token);
        sessionStorage.setItem('username', data.username);
        window.location.href = '/index.html';
    } catch (err) {
        showError('Network error, please try again');
        btn.disabled = false;
        btn.textContent = isLogin ? 'Login' : 'Register';
    }
}

document.getElementById('password').addEventListener('keydown', function(e) {
    if (e.key === 'Enter') handleSubmit();
});