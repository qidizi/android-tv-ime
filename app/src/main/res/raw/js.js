+function new_app() {
    if (!window['Vue']) {
        setTimeout(new_app, 1);
        return;
    }

    window.app = new Vue({
        el: '#app',
        data: {
            toast: '',
            media_position: 0,
            media_url: 'https://www.hobiao.com/vodplay/787437-2-33/'
        },
        methods: {
            xhr(action, json) {
                let self = this;

                if (!window.XMLHttpRequest) {
                    this.toast = '浏览器不支持【XMLHttpRequest】';
                    return;
                }

                let xhr = new window.XMLHttpRequest();
                xhr.timeout = 1000 * 5;
                xhr.onloadend = function () {
                    self.toast = xhr.responseText;
                };
                this.toast = '请求中...';
                xhr.open('POST', '/' + action + '?r=' + +new Date, true);
                xhr.responseType = 'text';
                let form = [];
                Object.keys(json).forEach((k) => {
                    form.push(encodeURIComponent(k) + '=' + encodeURIComponent(json[k]));
                });
                xhr.send(form.join('&'));
            },
            play_pause_toggle() {
                this.xhr('play_pause_toggle', {seek: this.get_seek()});
            },
            seek() {
                this.xhr('seek', {seek: this.get_seek()});
            },
            "send_url"(url) {
                url = String(url || this.media_url).trim();
                if (!url) return;
                let json = {
                    seek: this.get_seek(),
                    url: url
                };
                this.xhr('send_url', json);
            },
            get_seek() {
                return +this.media_position || 0;
            }
        }
    });
}();