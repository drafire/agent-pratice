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

    function injectAuth(url) {
        var qIndex = url.indexOf('?');
        var base = qIndex === -1 ? url : url.substring(0, qIndex);
        var query = qIndex === -1 ? '' : url.substring(qIndex + 1);

        var params = {};
        if (query) {
            var pairs = query.split('&');
            for (var i = 0; i < pairs.length; i++) {
                var eq = pairs[i].indexOf('=');
                if (eq !== -1) {
                    var key = decodeURIComponent(pairs[i].substring(0, eq));
                    var val = pairs[i].substring(eq + 1);
                    params[key] = val;
                }
            }
        }

        params.chatId = username;
        params.token = token;

        var newQuery = [];
        for (var k in params) {
            if (params.hasOwnProperty(k)) {
                newQuery.push(encodeURIComponent(k) + '=' + params[k]);
            }
        }
        return base + '?' + newQuery.join('&');
    }

    var originalFetch = window.fetch;
    window.fetch = function(url, options) {
        options = options || {};
        options.headers = options.headers || {};
        if (!options.headers['Authorization']) {
            options.headers['Authorization'] = 'Bearer ' + token;
        }
        return originalFetch.call(this, injectAuth(url), options);
    };

    var originalXHROpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url) {
        this._authUrl = url;
        return originalXHROpen.call(this, method, injectAuth(url));
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
        return new OriginalEventSource(injectAuth(url), config);
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