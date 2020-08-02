+function show_media() {
    if (!window['app'] || !app.set_media_list) {
        setTimeout(show_media, 1);
        return;
    }
    app.set_media_list(`
[央视]CCTV综合 http://111.40.205.87/PLTV/88888888/224/3221225710/index.m3u8 
http://117.148.187.37/PLTV/88888888/224/3221226154/index.m3u8
http://117.169.120.140:8080/live/cctv-1/.m3u8
http://121.31.30.90:8085/ysten-business/live/cctv-1/1.m3u8
http://125.210.152.10:8060/live/CCTV1HD_H265.m3u8
http://ivi.bupt.edu.cn/hls/cctv1hd.m3u8
[央视]CCTV科教 http://111.40.205.87/PLTV/88888888/224/3221225730/index.m3u8
http://117.169.120.140:8080/live/cctv-10/.m3u8
http://121.31.30.90:8085/ysten-business/live/cctv-10/yst.m3u8
http://39.135.32.24:6610/000000001000/1000000001000023734/index.m3u8?i
http://hwottcdn.ln.chinamobile.com/PLTV/88888890/224/3221225984/index.m3u8
[央视]CCTV新闻 http://112.50.243.8/PLTV/88888888/224/3221225817/1.m3u8
http://117.148.187.37/PLTV/88888888/224/3221226193/index.m3u8
http://117.169.120.140:8080/live/cctv-13/.m3u8
http://121.31.30.90:8085/ysten-business/live/cctv-13/1.m3u8
[央视]CCTV综艺 http://117.169.120.140:8080/live/cctv-3/.m3u8
http://121.31.30.90:8085/ysten-business/live/cctv-3/1.m3u8
http://39.135.32.29:6610/000000001000/1000000001000011218/index.m3u8?i
http://ivi.bupt.edu.cn/hls/cctv3hd.m3u8
[央视]CCTV纪录 http://111.40.205.76/PLTV/88888888/224/3221225734/index.m3u8
http://111.40.205.87/PLTV/88888888/224/3221225734/index.m3u8
http://112.50.243.8/PLTV/88888888/224/3221225820/1.m3u8
http://117.148.187.37/PLTV/88888888/224/3221226156/index.m3u8
http://ivi.bupt.edu.cn/hls/cctv9.m3u8
Channel[V] rtmp://ivi.bupt.edu.cn:1935/livetv/channelv
Channel-U https://d3inlz9elsutjl.cloudfront.net/hls/chuctv/master.m3u8
NHK-Chinese-Vision https://nhkw-zh-hlscomp.akamaized.net/8thz5iufork8wjip/playlist.m3u8
TVBS新聞台 http://60.199.188.65/HLS/WG_TVBS-N/index.m3u8
TVBS歡樂台 http://220.158.149.14:9999/live/TV00000000000000000079@HHZT;LIVE
亚太台 http://174.127.67.246/live330/playlist.m3u8
亚旅衛視 http://hls.jingchangkan.tv/jingchangkan/156722438_0HaM/index.m3u8
广东卫视 http://111.40.205.87/PLTV/88888888/224/3221225641/index.m3u8
http://111.40.205.87/PLTV/88888888/224/3221225699/index.m3u8
http://111.40.205.87/PLTV/88888888/224/3221225736/index.m3u8
http://112.50.243.8/PLTV/88888888/224/3221225942/3.m3u8
http://121.31.30.90:8085/ysten-business/live/guangdongstv/1.m3u8
http://121.31.30.90:8085/ysten-business/live/guangdongstv/yst.m3u8
http://m-tvlmedia.public.bcs.ysten.com/ysten-business/live/hdguangdongstv/1.m3u8
广东卫视HD http://121.31.30.90:8085/ysten-business/live/hdguangdongstv/1.m3u8
http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221225670/index.m3u8
广东卫视高清 http://112.50.243.8/PLTV/88888888/224/3221225824/1.m3u8
http://39.134.52.173/hwottcdn.ln.chinamobile.com/PLTV/88888890/224/3221225985/index.m3u8
`);
}();