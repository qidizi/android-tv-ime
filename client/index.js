+function new_app() {
    if (!window['Vue']) {
        setTimeout(new_app, 1);
        return;
    }

    const MEDIA_KEY = 'media';

    window.app = new Vue({
        el: '#app',
        data: {
            toast: '',
            media_start: 0,
            media_url: '',
            text: '',
            media_list: [],
            filter: '',
            mask_on: false,
            tv: '',
            media_list_tmp: [],
            prompt: false,
            prompt_ok_show: true,
            prompt_title: '标题',
            prompt_data: '',
            prompt_tip: '请输入',
            prompt_ok_cb: null,
        },
        watch: {},
        created() {
            this.clean_cache();
        },
        mounted() {
            this.tv = this.get_param('tv', '').trim();
            this.media_list_tmp_show();
        },
        methods: {
            clean_cache() {
                let now = +new Date;
                if (now - (+this.get_param('_t', 0) || 0) > 1000 * 60 * 60) {
                    let url = location.href.replace(/&*_t=[^=&]*/gi, '');
                    location.href = url + (url.indexOf('?') > -1 ? '&' : '?') + '_t=' + now;
                }
            },
            prompt_ok() {
                if (!this.prompt_ok_cb) return;
                this.prompt_ok_cb();
            },
            media_list_tmp_share() {
                let media = localStorage.getItem(MEDIA_KEY) || '';
                if (!media) {
                    this.toast = '暂无自定义视频';
                    return;
                }

                this.prompt_ok_show = false;
                this.prompt = true;
                this.prompt_title = '请复制以下内容分享给好友';
                this.prompt_data = media;
            },
            media_list_tmp_append() {
                this.prompt_title = '请输入追加的视频';
                this.prompt_tip = '视频输入内容格式示例:\n\n片名1 视频地址1\n片名2 视频地址2';
                this.prompt = true;
                this.prompt_ok_show = true;
                let me = this;
                this.prompt_ok_cb = function () {
                    let media = (localStorage.getItem(MEDIA_KEY) || '') + "\n" + me.prompt_data;
                    media = media
                        .replace(/[ \t\r]+/, ' ')
                        .replace(/\n+/gm, '\n')
                        .trim();
                    localStorage.setItem(MEDIA_KEY, media);
                    me.media_list_tmp_show();
                    me.prompt = false;
                };
            },
            media_list_tmp_show() {
                let media = localStorage.getItem(MEDIA_KEY) || '';
                let tmp = [];
                media.split('\n').forEach(function (v, k) {
                    let m = v.match(/(.+)\s(\w+:\/\/.+)/);
                    if (!m) return;
                    tmp.push([m[1], m[2]]);
                });

                this.media_list_tmp = tmp;
            },
            media_list_tmp_del(index) {
                let tmp = [];
                this.media_list_tmp.forEach(function (v, k) {
                    if (index === k) return;
                    tmp.push(v.join(' '));
                });
                localStorage.setItem(MEDIA_KEY, tmp.join('\n'));
                this.media_list_tmp_show();
            },
            get_param(name, def) {
                location.search.replace(
                    new RegExp('[?&]' + name + "=([^&]*)", 'gi'),
                    function ($0, $1) {
                        def = decodeURIComponent($1);
                    });

                return def;
            },
            set_media_list(str) {
                let tmp = [];
                let unique = {};
                // 把多行编写的url合并成一行(多个源)
                str.replace(/[\n\r]+\s*(\w+:\/+)/gm, " $1").split('\n')
                    .sort(function (a, b) {
                        return String(a).localeCompare(String(b));
                    }).forEach(function (v) {
                    v = v.trim();
                    if (!v) return;
                    let urls = [];
                    v = v.replace(/\w+:\/+[^\s]+/g, function ($0) {
                        // 排重
                        if (unique[$0]) return console.log('重复的视频列表:\n' + $0);
                        unique[$0] = 1;
                        urls.push($0);
                        return '';
                    });
                    tmp.push([v.trim(), urls]);
                });
                this.media_list = tmp;
            },
            xhr(action, data, query) {
                let port = 11111;
                let self = this;

                if (!/^\d{1,3}(\.\d{1,3}){3}$/.test(this.tv)) {
                    this.toast = '电视ip无效';
                    return;
                }

                if (!window.XMLHttpRequest) {
                    this.toast = '浏览器不支持【XMLHttpRequest】';
                    return;
                }

                let done = false;
                let xhr = new window.XMLHttpRequest();
                xhr.timeout = 1000 * 5;
                xhr.onreadystatechange = function () {
                    if (this.readyState !== 4 && done) return;
                    done = true;
                    let msg = xhr.response;

                    switch (xhr.status) {
                        case 200:
                            break;
                        case 0:
                            msg = '无法连接电视';
                            break;
                        case 404:
                            msg = '找不到页面';
                            break;
                        default:
                            msg = '未定义状态:' + xhr.status + ';' + msg;
                    }

                    self.toast = msg + ' ' + new Date().toLocaleString();
                };
                xhr.onabort = xhr.onerror = function () {
                    if (done) return;
                    done = true;
                    self.toast = "连接电视端失败,错误描述:" + xhr.statusText + '[' + xhr.status + ']';
                };
                xhr.onloadend = function () {
                    self.mask_on = false;
                };
                xhr.upload.addEventListener('progress', function (e) {
                    if (!is_file) return;
                    self.toast = '上传 ' + (e.loaded / e.total).toFixed(2) * 100 + '%';
                })
                let is_file = data instanceof File;
                let content_length = 0;

                if ('send_file' === action) {
                    content_length = data.size;
                } else {
                    content_length = data.length;
                }

                let rnd = +new Date;
                let url = `http://${this.tv}:${port}/?_size=${content_length}&_do=${action}&r=${rnd}`;

                if (query)
                    url += '&' + query;

                this.toast = '请求中...';
                this.mask_on = true;
                xhr.open('POST', url);
                xhr.send(data);
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

                let max_mb = 200;
                if (file.size > 1024 * 1024 * max_mb) {
                    this.toast = '上传文件必须小于 ' + max_mb + 'MB';
                    return;
                }

                self.xhr('send_file', file);
            },
            "send_key"(key) {
                this.xhr('send_key', key);
            },
            "send_text"() {
                this.xhr('send_text', this.text);
            },
            "play_url"(url) {
                url = String(url || this.media_url).trim();
                if (!url) return;

                if (url.indexOf('ext=') < 0) {
                    url += url.indexOf('?') > -1 ? '&ext=' : '?ext=';

                    if (0 === url.toLowerCase().indexOf('rtmp')) {
                        url += 'rtmp';
                    } else {
                        let videos = ' mp2 mpa mpe mpeg mpg mpv2 mov qt lsf lsx asf asr asx avi movie mp4 m3u8 ts 3gp ' +
                            'mov wmv m4v webm h264 h263 ';
                        let ext = url.split('?')[0].replace(/^.*\.(\w{2,5})$/, '$1');

                        if (videos.indexOf(' ' + ext + ' ') < 0) {
                            this.toast = "无法判断视频格式,请手动指定,示例: http://a.com/?ext=mp4";
                            return;
                        }

                        url += ext;
                    }
                }
                let query = +this.media_start > 0 ? 'seek=' + +this.media_start : '';
                this.xhr('send_url', url, query);
            }
        }
    });
}();