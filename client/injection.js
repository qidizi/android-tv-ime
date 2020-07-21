+function () {
    // 网页中的播放器
    +function play_web_video() {
        let video = document.getElementsByTagName('video');
        if (!video.length) {
            toast("延后"+ +new Date,1);
            return setTimeout(play_web_video, 500);
        }
        toast("进入");
        video = video[0];
        // video.style.display = 'block';
        // video.style.position = 'absolute';
        // video.style.top = '0';
        // video.style.left = '0';
        // video.style.width = '100%';
        // video.style.height = '100%';
        // video.style.zIndex = 9999999;
        // video.autoplay = true;

        try {
            video.play();
        } catch (e) {
            toast('播放视频出错:' + e);
        }
    }();

    function xhr_get(url, callback, data) {
        return xhr(url, 'GET', data, callback);
    }

    function xhr_post(url, callback, data) {
        return xhr(url, 'POST', data, callback);
    }

    function xhr(url, method, data, callback) {
        if (!window.XMLHttpRequest) {
            toast('浏览器不支持【XMLHttpRequest】');
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
                if ('function' !== typeof callback) {
                    return toast(xhr.response);
                }

                callback(xhr.response, obj);
            }
        };
        xhr.onabort = xhr.onerror = function () {
            if (!done) {
                done = true;
                toast("xhr请求失败,错误描述:" + xhr.statusText + '[' + xhr.status + ']');
            }
        };

        url += (url.indexOf('?') > -1 ? '&' : '?') + 'r=' + +new Date;
        xhr.open(method, url);
        //xhr.setRequestHeader("Content-type", "application/json;charset=utf-8");
        xhr.send(data);
    }

    function toast(msg, sec) {
        if ('string' !== typeof msg) msg = JSON.stringify(msg);
        let dom = document.createElement('div');
        dom.style.position = 'fixed';
        dom.style.top = '10px';
        dom.style.left = '0px';
        dom.style.width = '100%';
        dom.style.padding = '10px';
        dom.style.backgroundColor = 'black';
        dom.style.color = 'yellow';
        dom.style.fontSize = '30px';
        dom.style.textAlign = 'center';
        dom.style.opacity = '0.7';
        dom.style.whiteSpace = 'normal';
        dom.style.wordBreak = 'break-all';
        dom.style.zIndex = 999999;
        dom.innerHTML = msg;
        document.documentElement.appendChild(dom);
        setTimeout(function () {
            dom.remove();
        }, (sec || 5) * 1000);
    }
}();