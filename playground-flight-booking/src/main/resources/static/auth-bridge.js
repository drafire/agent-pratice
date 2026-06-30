(function() {
    var token = localStorage.getItem('token');
    var username = localStorage.getItem('username');

    if (!token) {
        window.location.href = '/login.html';
        return;
    }

    window.__AUTH__ = {
        token: token,
        username: username || ''
    };

    var originalFetch = window.fetch;
    window.fetch = function(url, options) {
        options = options || {};
        options.headers = options.headers || {};
        if (!options.headers['Authorization']) {
            options.headers['Authorization'] = 'Bearer ' + token;
        }
        return originalFetch.call(this, url, options);
    };

    var originalXHROpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url) {
        this._authUrl = url;
        return originalXHROpen.apply(this, arguments);
    };
    var originalXHRSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function(body) {
        try {
            this.setRequestHeader('Authorization', 'Bearer ' + token);
        } catch (e) {}
        return originalXHRSend.call(this, body);
    };

    var OriginalEventSource = window.EventSource;
    window.EventSource = function(url, config) {
        var separator = url.indexOf('?') !== -1 ? '&' : '?';
        var newUrl = url + separator + 'token=' + encodeURIComponent(token);
        return new OriginalEventSource(newUrl, config);
    };
    window.EventSource.prototype = OriginalEventSource.prototype;
    window.EventSource.CONNECTING = OriginalEventSource.CONNECTING;
    window.EventSource.OPEN = OriginalEventSource.OPEN;
    window.EventSource.CLOSED = OriginalEventSource.CLOSED;

    fetch('/api/auth/validate')
        .then(function(resp) {
            if (!resp.ok) {
                localStorage.clear();
                sessionStorage.clear();
                window.location.href = '/login.html';
            }
        })
        .catch(function() {});
})();