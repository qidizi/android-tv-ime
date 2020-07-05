document.getElementById('app').innerHTML = `
<i class="toast" v-html="toast"></i>
<i class="pad">
    <i class="pad_left" v-on:click.stop.prevent.self="send_key('KEYCODE_DPAD_LEFT');"></i>
    <i class="pad_up" v-on:click.stop.prevent.self="send_key('KEYCODE_DPAD_UP');"></i>
    <i class="pad_right" v-on:click.stop.prevent.self="send_key('KEYCODE_DPAD_RIGHT');"></i>
    <i class="pad_down" v-on:click.stop.prevent.self="send_key('KEYCODE_DPAD_DOWN');"></i>
    <i class="pad_center" v-on:click.stop.prevent.self="send_key('KEYCODE_DPAD_CENTER');"></i>
</i>
<i class="key" v-on:click.stop.prevent.self="send_key('KEYCODE_HOME');">主页</i>
<i class="key" v-on:click.stop.prevent.self="send_key('KEYCODE_SETTINGS');">设置</i>
<i class="key" v-on:click.stop.prevent.self="send_key('KEYCODE_POWER');">电源</i>
<i class="key" v-on:click.stop.prevent.self="send_key('KEYCODE_VOLUME_UP');">声+</i>
<br>
<i class="key" v-on:click.stop.prevent.self="send_key('KEYCODE_BACK');">返回</i>
<i class="key" v-on:click.stop.prevent.self="send_key('KEYCODE_MENU');">菜单</i>
<i class="key" v-on:click.stop.prevent.self="send_key('KEYCODE_VOLUME_MUTE');">静音</i>
<i class="key" v-on:click.stop.prevent.self="send_key('KEYCODE_VOLUME_DOWN');">声-</i>

<textarea v-model="text" class="text text_left" placeholder="输入上屏"></textarea>
<input type="button" class="btn_right" value="输入" v-on:click="send_text">

<textarea v-model.trim="media_url" class="media_url text_left" placeholder="如 http://a.com?a.mp3,需要提供后缀来识别"
></textarea>
<input type="button" class="btn_right" value="播放" v-on:click="play_url">

<input type="file" id="file_input" class="text_left file_input">
<input type="submit" v-on:click="upload" class="btn_right">
<ol class="media_list">
<template v-for="nu in media_list">
<li><a href="javascript:void(0);" v-html="nu[0]" v-on:click="play_url(nu[1]);"></a></li>
</template>
</ol>
</div>
`;