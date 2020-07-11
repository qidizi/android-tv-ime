+function () {
    let host = location.search.indexOf('?avd=1') > -1 ?
        "php.local.qidizi.com" : "www-public.qidizi.com";
    document.write(`
<!DOCTYPE html>
<html lang="zh">
<head>
<base href="http://${host}/android-tv-ime/client/" />
<meta charset="UTF-8">
    <meta name="viewport"
        content="width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="ie=edge">
    <title>安卓tv控制器</title>
    <link rel="icon" type="image/png" href="data:image/png;base64,iVBORw0KGgo=">
    <link rel="stylesheet" href="index.css" />
    <!--用异步加载js,否则chrome会阻止,通过document.write写入的-->
    <script src="https://cn.vuejs.org/js/vue.min.js" async"></script>
</head>
<body>
<div id="app">
    <i class="toast" v-html="toast"> </i>
    <i class="pad">
        <i class="pad_left" @click.stop.prevent.self="send_key('KEYCODE_DPAD_LEFT')">左</i>
        <i class="pad_up" @click.stop.prevent.self="send_key('KEYCODE_DPAD_UP')">上</i>
        <i class="pad_right" @click.stop.prevent.self="send_key('KEYCODE_DPAD_RIGHT')">右</i>
        <i class="pad_down" @click.stop.prevent.self="send_key('KEYCODE_DPAD_DOWN')">下</i>
        <i class="pad_center" @click.stop.prevent.self="send_key('KEYCODE_DPAD_CENTER')">OK</i>
    </i>
    <i class="key" @click.stop.prevent.self="send_key('KEYCODE_POWER')">电源</i>
    <i class="key" @click.stop.prevent.self="send_key('KEYCODE_MENU')">菜单</i>
    <i class="key home" @click.stop.prevent.self="send_key('KEYCODE_HOME')">主页</i>
    <i class="key back" @click.stop.prevent.self="send_key('KEYCODE_BACK')">返回</i>
    <i class="key" @click.stop.prevent.self="send_key('KEYCODE_VOLUME_DOWN')">声-</i>
    <i class="key" @click.stop.prevent.self="send_key('KEYCODE_VOLUME_MUTE')">静音</i>
    <i class="key" @click.stop.prevent.self="send_key('KEYCODE_VOLUME_UP')">声+</i>

    <textarea v-model="text" class="text text_left" placeholder="输入上屏"> </textarea>
    <input type="button" class="btn_right" value="输入" @click="send_text">

    <textarea v-model.trim="media_url" class="media_url text_left"
              placeholder="如 http://a.com?a.mp3,需要提供后缀来识别"> </textarea>
    <input type="button" class="btn_right" value="播放" @click="play_url">
    <input type="file" id="file_input" class="text_left file_input">
    <input type="submit" class="btn_right" @click="upload">
    <input v-model.trim="filter" class="filter" placeholder="输入关键字快速查找">
    <ol class="media_list">
        <template v-for="nu in media_list">
            <li class="url" v-if="!filter || nu[0].toLowerCase().indexOf(filter.toLowerCase()) > -1">
                <a href="javascript:void(0)" v-html="nu[0]" @click="play_url(nu[1])"> </a>
            </li>
        </template>
    </ol>
    <i class="to_top" onclick="window.scrollTo(0, 0);">顶部</i>
</div>
<script src="index.js" type="text/javascript" async></script>
<script src="media.js" type="text/javascript" async></script>
</body>
</html>
`);
    document.close();
}();