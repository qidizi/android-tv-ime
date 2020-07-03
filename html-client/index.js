let tv_server = 'http://127.0.0.1:11111/'
let app = new Vue({
    el: '#app',
    data: {
        tv: false,
        message: '控制器',
        media_url: 'http://dpv.videocc.net/e785b2c81c/5/e785b2c81c9e018296671a1287e99615_2.mp4?pid=1593798817820X1508665&a.mp4',
        text: ''
    },
    watch: {
        tv: function (newer, old) {
            tv_server = this.tv ? 'http://10.10.10.147:11111/' : 'http://127.0.0.1:11111/';
        }
    },
    methods: {
        send_key(key) {
            let self = this;
            post({action: 'send_key', key: key}, function (txt) {
                self.message = txt;
            });
        },
        commit_text() {
            let self = this;
            post({action: 'commit_text', text: this.text}, function (txt) {
                self.message = txt;
            });
        },
        play_media() {
            let self = this;
            post({action: 'play_media', url: this.media_url}, function (txt) {
                self.message = txt;
            });
        }
    }
});

function post(json, callback) {
    json = JSON.stringify(json);
    if (!window.XMLHttpRequest) {
        document.body.innerHTML = '浏览器不支持【XMLHttpRequest】';
        return;
    }

    let done = false;
    let xhr = new window.XMLHttpRequest();
    xhr.onreadystatechange = function () {
        if (this.readyState === 4 && !done) {
            done = true;
            let obj = {
                code: xhr.status,
                headers: xhr.getAllResponseHeaders()
            };
            callback(xhr.response, obj);
        }
    };
    xhr.onabort = xhr.onerror = function () {
        if (!done) {
            done = true;
            let obj = {
                code: xhr.status,
                headers: xhr.getAllResponseHeaders()
            };
            callback(xhr.statusText, obj);
        }
    };
    let url = tv_server;
    url += (url.indexOf('?') > -1 ? '&' : '?') + 'r=' + +new Date;
    xhr.open('POST', url);
    xhr.setRequestHeader("Content-type", "application/json;charset=utf-8");
    xhr.send(json);
}