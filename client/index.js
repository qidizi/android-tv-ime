+function new_app() {
    if (!window['Vue']) {
        setTimeout(new_app, 1);
        return;
    }

    window.app = new Vue({
        el: '#app',
        data: {
            toast: '',
            media_url: 'https://www.babakk.com/guochanju/meirenmubaishou/1-42.html?__web__',
            text: '',
            media_list: [],
            filter: ''
        },
        methods: {
            set_media_list(str) {
                let tmp = [];
                str.split('\n').forEach(function (v) {
                    v = v.trim();
                    if (!v) return;
                    let url = '';
                    v = v.replace(/\w+:\/+.+\r*$/g, function ($0) {
                        url = $0;
                        return '';
                    });
                    tmp.push([v.trim(), url]);
                });
                this.media_list = tmp;
            },
            xhr(data, callback) {
                let self = this;
                if (!window.XMLHttpRequest) {
                    this.toast = '浏览器不支持【XMLHttpRequest】';
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
                        try {
                            let json = JSON.parse(xhr.response);

                            if (200 !== json.code) {
                                self.toast = "连接电视端失败:" + json['msg'];
                                return;
                            }

                            if ('function' !== typeof callback) {
                                return self.toast = json['msg'] || JSON.stringify(json);
                            }

                            callback(json, obj);
                        } catch (e) {
                            self.toast = "电视端响应处理失败:" + e;
                        }
                    }
                };
                xhr.onabort = xhr.onerror = function () {
                    if (!done) {
                        done = true;
                        self.toast = "连接电视端失败,错误描述:" + xhr.statusText + '[' + xhr.status + ']';
                    }
                };
                let is_file = data instanceof File;
                let url = 'http://' + location.host + '/?';
                // 如果是上传必须拼接出 /?file=1&
                if (is_file)
                    url += 'file=1&';
                url += 'r=' + +new Date;
                xhr.open("POST", url);
                if (!is_file)
                    xhr.setRequestHeader("Content-type", "application/json;charset=utf-8");
                xhr.send(is_file ? data : JSON.stringify(data));
            },
            "upload"() {
                let self = this;
                let file = document.getElementById('file_input').files;

                if (!file.length) {
                    this.toast = '请选择文件';
                    return;
                }

                file = file[0];

                if (!/.apk$/i.test(file.name)) {
                    this.toast = '只允许上传安卓应用文件';
                    return;
                }

                self.xhr(file);
            },
            "send_key"(key) {
                this.xhr({action: 'send_key', key: key});
            },
            "send_text"() {
                this.xhr({action: 'send_text', text: this.text});
            },
            "play_url"(url) {
                url = String(url || this.media_url).trim();
                if (!url) return;
                url += url.indexOf('?') > -1 ? '' : '?';

                if (url.toLowerCase().indexOf('.m3u8') > -1) {
                    url += '&ext=m3u8';
                } else if (0 === url.toLowerCase().indexOf('rtmp')) {
                    url += '&ext=rtmp';
                } else if (url.toLowerCase().indexOf('__web__') < 0) {
                    // 如果不是网页
                    this.toast = '无法确定后缀名';
                    return;
                }

                // this.media_url = url;
                this.xhr({action: 'play_url', url: url});
            }
        }
    });
}();