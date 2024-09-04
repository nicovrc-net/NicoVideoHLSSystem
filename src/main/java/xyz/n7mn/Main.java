package xyz.n7mn;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final OkHttpClient.Builder builder = new OkHttpClient.Builder();

    private static final HashMap<String, VideoData> DataList = new HashMap<>();

    private static final Pattern matcher_1 = Pattern.compile("(\\d+)_(.+)");

    private static final Pattern matcher_2 = Pattern.compile("(GET|HEAD) /video/(.+) HTTP");
    private static final Pattern matcher_3 = Pattern.compile("HTTP/1\\.(\\d)");
    private static final Pattern matcher_4 = Pattern.compile("(GET|HEAD) (.+) HTTP");

    private static final Pattern matcher_5 = Pattern.compile("#EXT-X-MAP:URI=\"(.+)\"");
    private static final Pattern matcher_6 = Pattern.compile("#EXT-X-KEY:METHOD=AES-128,URI=\"(.+)\",IV=(.+)");
    private static final Pattern matcher_7 = Pattern.compile("#EXT-X-STREAM-INF:BANDWIDTH=(\\d+),AVERAGE-BANDWIDTH=(\\d+),CODECS=\"(.+)\",RESOLUTION=(.+),FRAME-RATE=(.+),AUDIO=\"(.+)\"");

    private static final Pattern matcher_8 = Pattern.compile("[uU]ser-[aA]gent: (VLC|vlc)");

    private static final Pattern matcher_9 = Pattern.compile("nico-hls:(\\d+)_(.+)");

    private static final Gson gson = new Gson();

    private static YamlMapping input;

    public static void main(String[] args) throws Exception {

        input = Yaml.createYamlInput(new File("./config.yml")).readYamlMapping();

        if (input.string("Refresh").toLowerCase(Locale.ROOT).equals("true")){

            // 定期お掃除
            new Thread(()->{
                Timer timer = new Timer();
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {

                        final HashMap<String, VideoData> temp = new HashMap<>(DataList);

                        temp.forEach((id, data)->{

                            long time = new Date().getTime();

                            if ((data.getExpiryDate() - time) <= 0L){
                                DataList.remove(id);
                            }

                        });

                        if (input.string("RefreshRedis").toLowerCase(Locale.ROOT).equals("true")){
                            new Thread(()->{
                                JedisPool jedisPool = new JedisPool(input.string("RedisServer"), input.integer("RedisPort"));
                                Jedis jedis = jedisPool.getResource();
                                if (!input.string("RedisPass").isEmpty()){
                                    jedis.auth(input.string("RedisPass"));
                                }

                                jedis.keys("nico-hls:*").forEach(key -> {
                                    Matcher matcher = matcher_9.matcher(key);
                                    if (matcher.find()){
                                        long time = new Date().getTime();
                                        //System.out.println("debug time : " + ((time - Long.parseLong(matcher.group(1)))));
                                        if ((time - Long.parseLong(matcher.group(1))) >= 86400000L){
                                            jedis.del(key);
                                        }
                                    }

                                });

                                jedis.close();
                                jedisPool.close();
                            }).start();
                        }
                    }
                }, 0L, 60000L);
            }).start();

        }
        // HTTP通信受け取り
        new Thread(()->{
            try {
                ServerSocket svSock = new ServerSocket(25251);

                while (true) {
                    Socket sock = svSock.accept();
                    new Thread(() -> {
                        try {
                            final InputStream in = sock.getInputStream();
                            final OutputStream out = sock.getOutputStream();

                            byte[] data = new byte[1000000];
                            int readSize = in.read(data);
                            if (readSize <= 0) {
                                sock.close();
                                return;
                            }
                            data = Arrays.copyOf(data, readSize);

                            final String httpRequest = new String(data, StandardCharsets.UTF_8);

                            Matcher matcher1 = matcher_2.matcher(httpRequest);
                            Matcher matcher2 = matcher_3.matcher(httpRequest);
                            Matcher matcher3 = matcher_4.matcher(httpRequest);
                            Matcher matcher5 = matcher_8.matcher(httpRequest);

                            final String httpVersion = "1." + (matcher2.find() ? matcher2.group(1) : "1");

                            System.out.println(httpRequest);

                            if (matcher1.find()) {

                                String group = matcher1.group(2);
                                String FileNameText = "./" + group.replaceAll("%22", "").replaceAll("\\./", "").replaceAll("\\.\\./", "");
                                Matcher matcher = matcher_1.matcher(FileNameText);


                                if (matcher.find()){
                                    String fileId = matcher.group(1) + "_" + matcher.group(2).split("/")[0];
                                    //System.out.println(fileId);
                                    // まずはHashmapを見に行く
                                    final VideoData videoData = DataList.get(fileId);
                                    if (videoData != null){
                                        //System.out.println("hashmap");
                                        Matcher matcher4 = matcher_7.matcher(videoData.getMainM3u8());
                                        String m3u8Text = "#EXTM3U\n/video/"+fileId+"/sub.m3u8";

                                        if (matcher4.find()){
                                            m3u8Text = "#EXTM3U\n" +
                                                    "#EXT-X-VERSION:6\n" +
                                                    "#EXT-X-INDEPENDENT-SEGMENTS\n" +
                                                    "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio-aac-64kbps\",NAME=\"Main Audio\",DEFAULT=YES,URI=\"/video/"+fileId+"/audio.m3u8\"\n" +
                                                    "#EXT-X-STREAM-INF:BANDWIDTH="+matcher4.group(1)+",AVERAGE-BANDWIDTH="+matcher4.group(2)+",CODECS=\""+matcher4.group(3)+"\",RESOLUTION="+matcher4.group(4)+",FRAME-RATE="+matcher4.group(5)+",AUDIO=\"audio-aac-64kbps\"\n" +
                                                    "/video/"+fileId+"/sub.m3u8";
                                        }

                                        if (group.endsWith("main.m3u8")) {
                                            out.write(("HTTP/" + httpVersion + " 200 OK\nContent-Type: application/vnd.apple.mpegurl;\n\n").getBytes(StandardCharsets.UTF_8));
                                            if (!matcher5.find() && DataList.get(fileId).isVRC()) {
                                                out.write(m3u8Text.getBytes(StandardCharsets.UTF_8));
                                            } else if (!DataList.get(fileId).isVRC()) {
                                                out.write(DataList.get(fileId).getMainM3u8().getBytes(StandardCharsets.UTF_8));
                                            } else {
                                                out.write(DataList.get(fileId).getMainM3u8().getBytes(StandardCharsets.UTF_8));
                                            }
                                        } else if (group.endsWith("sub.m3u8")) {
                                            out.write(("HTTP/"+httpVersion+" 200 OK\nContent-Type: application/vnd.apple.mpegurl;\n\n").getBytes(StandardCharsets.UTF_8));
                                            out.write(DataList.get(fileId).getMainM3u8().getBytes(StandardCharsets.UTF_8));
                                        } else if (group.endsWith("video.m3u8")) {
                                            out.write(("HTTP/"+httpVersion+" 200 OK\nContent-Type: application/vnd.apple.mpegurl;\n\n").getBytes(StandardCharsets.UTF_8));
                                            out.write(DataList.get(fileId).getVideoM3u8().getBytes(StandardCharsets.UTF_8));
                                        } else if (group.endsWith("audio.m3u8")) {
                                            out.write(("HTTP/"+httpVersion+" 200 OK\nContent-Type: application/vnd.apple.mpegurl;\n\n").getBytes(StandardCharsets.UTF_8));
                                            out.write(DataList.get(fileId).getAudioM3u8().getBytes(StandardCharsets.UTF_8));
                                        } else {
                                            out.write(("HTTP/"+httpVersion+" 404 Not Found\nContent-Type: text/plain; charset=utf-8\n\n404").getBytes(StandardCharsets.UTF_8));
                                        }

                                        out.flush();
                                        out.close();
                                        in.close();
                                        sock.close();

                                        return;
                                    }

                                    // HashMapにない場合はRedisを見に行く
                                    JedisPool jedisPool = new JedisPool(input.string("RedisServer"), input.integer("RedisPort"));
                                    Jedis jedis = jedisPool.getResource();
                                    if (!input.string("RedisPass").isEmpty()){
                                        jedis.auth(input.string("RedisPass"));
                                    }

                                    String s1 = jedis.get("nico-hls:" + fileId);
                                    if (s1 != null){

                                        VideoData json = gson.fromJson(s1, VideoData.class);
                                        DataList.put(fileId, json);
                                        Matcher matcher4 = matcher_7.matcher(json.getMainM3u8());
                                        String m3u8Text = "#EXTM3U\n/video/"+fileId+"/sub.m3u8";

                                        if (matcher4.find()){
                                            m3u8Text = "#EXTM3U\n" +
                                                    "#EXT-X-VERSION:6\n" +
                                                    "#EXT-X-INDEPENDENT-SEGMENTS\n" +
                                                    "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio-aac-64kbps\",NAME=\"Main Audio\",DEFAULT=YES,URI=\"/video/"+fileId+"/audio.m3u8\"\n" +
                                                    "#EXT-X-STREAM-INF:BANDWIDTH="+matcher4.group(1)+",AVERAGE-BANDWIDTH="+matcher4.group(2)+",CODECS=\""+matcher4.group(3)+"\",RESOLUTION="+matcher4.group(4)+",FRAME-RATE="+matcher4.group(5)+",AUDIO=\"audio-aac-64kbps\"\n" +
                                                    "/video/"+fileId+"/sub.m3u8";
                                        }

                                        if (group.endsWith("main.m3u8")){
                                            out.write(("HTTP/"+httpVersion+" 200 OK\nContent-Type: application/vnd.apple.mpegurl;\n\n").getBytes(StandardCharsets.UTF_8));
                                            if (!matcher5.find() && DataList.get(fileId).isVRC()) {
                                                out.write(m3u8Text.getBytes(StandardCharsets.UTF_8));
                                            } else if (!json.isVRC()){
                                                out.write(json.getMainM3u8().getBytes(StandardCharsets.UTF_8));
                                            } else {
                                                out.write(json.getMainM3u8().getBytes(StandardCharsets.UTF_8));
                                            }
                                        } else if (group.endsWith("sub.m3u8")) {
                                            out.write(("HTTP/"+httpVersion+" 200 OK\nContent-Type: application/vnd.apple.mpegurl;\n\n").getBytes(StandardCharsets.UTF_8));
                                            out.write(json.getMainM3u8().getBytes(StandardCharsets.UTF_8));
                                        } else if (group.endsWith("video.m3u8")) {
                                            out.write(("HTTP/"+httpVersion+" 200 OK\nContent-Type: application/vnd.apple.mpegurl;\n\n").getBytes(StandardCharsets.UTF_8));
                                            out.write(json.getVideoM3u8().getBytes(StandardCharsets.UTF_8));
                                        } else if (group.endsWith("audio.m3u8")) {
                                            out.write(("HTTP/"+httpVersion+" 200 OK\nContent-Type: application/vnd.apple.mpegurl;\n\n").getBytes(StandardCharsets.UTF_8));
                                            out.write(json.getAudioM3u8().getBytes(StandardCharsets.UTF_8));
                                        } else {
                                            out.write(("HTTP/"+httpVersion+" 404 Not Found\nContent-Type: text/plain; charset=utf-8\n\n404").getBytes(StandardCharsets.UTF_8));
                                        }

                                        out.flush();
                                        out.close();
                                        in.close();
                                        sock.close();

                                        return;
                                    }
                                    jedis.close();
                                    jedisPool.close();
                                }

                                // それでもない場合はファイルの存在確認をしてファイルの中身または404を返す

                                File file = new File(FileNameText);
                                //System.out.println("./" + group.replaceAll("%22","").replaceAll("\\./", "").replaceAll("\\.\\./", ""));
                                if (file.exists() && !file.isDirectory()){
                                    String ContentType = "application/octet-stream";
                                    if (file.getName().endsWith("m3u8")){
                                        ContentType = "application/vnd.apple.mpegurl";
                                    }
                                    if (file.getName().endsWith("ts")){
                                        ContentType = "video/mp2t";
                                    }
                                    if (file.getName().endsWith("mp4")){
                                        ContentType = "video/mp4";
                                    }
                                    out.write(("HTTP/"+httpVersion+" 200 OK\nContent-Type: "+ContentType+";\n\n").getBytes(StandardCharsets.UTF_8));

                                    if (matcher1.group(1).equals("GET")){
                                        FileInputStream stream = new FileInputStream(FileNameText);
                                        out.write(stream.readAllBytes());
                                        stream.close();
                                    }

                                    out.flush();
                                    out.close();
                                    in.close();
                                    sock.close();

                                    return;
                                }

                                if (file.exists() && file.isDirectory()){
                                    out.write(("HTTP/"+httpVersion+" 403 Forbidden\nContent-Type: text/plain; charset=utf-8\n\n403").getBytes(StandardCharsets.UTF_8));
                                    out.flush();
                                    out.close();
                                    in.close();
                                    sock.close();

                                    return;
                                }

                                out.write(("HTTP/"+httpVersion+" 404 Not Found\nContent-Type: text/plain; charset=utf-8\n\n404").getBytes(StandardCharsets.UTF_8));
                                out.flush();
                                out.close();
                                in.close();
                                sock.close();
                                return;
                            }

                            if (matcher3.find()){
                                final String RequestHeader = matcher3.group(1);
                                final String temp = matcher3.group(2);
                                final String[] split = temp.split("&HostURL=");
                                if (split.length != 2){
                                    out.write(("HTTP/"+httpVersion+" 404 Not Found\nContent-Type: text/plain; charset=utf-8\n\n404").getBytes(StandardCharsets.UTF_8));
                                    out.flush();
                                    out.close();
                                    in.close();
                                    sock.close();
                                    return;
                                }
                                final String RequestURI = split[0];
                                //System.out.println(RequestURI);
                                final String RequestHost = split[1];

                                final String[] URIText = RequestURI.split("/");

                                VideoData[] inputData = {null};
                                DataList.forEach((id, videoData)->{
                                    if (URIText[1].equals(videoData.getCookieID()) || URIText[2].equals(videoData.getCookieID())){
                                        inputData[0] = videoData;
                                    }
                                });

                                // HashMapにない場合はRedisを見に行く
                                if (inputData[0] == null){
                                    JedisPool jedisPool = new JedisPool(input.string("RedisServer"), input.integer("RedisPort"));
                                    Jedis jedis = jedisPool.getResource();
                                    if (!input.string("RedisPass").isEmpty()){
                                        jedis.auth(input.string("RedisPass"));
                                    }

                                    jedis.keys("nico-hls:*").forEach(key -> {
                                        if (inputData[0] != null){
                                            return;
                                        }

                                        VideoData json = gson.fromJson(jedis.get(key), VideoData.class);
                                        if (json.getCookieID().equals(URIText[1]) || json.getCookieID().equals(URIText[2])){
                                            inputData[0] = json;
                                            DataList.put(json.getID(), json);
                                        }
                                    });

                                    jedis.close();
                                    jedisPool.close();
                                }

                                if (inputData[0] == null){
                                    out.write(("HTTP/"+httpVersion+" 404 Not Found\nContent-Type: text/plain; charset=utf-8\n\n404").getBytes(StandardCharsets.UTF_8));
                                    out.flush();
                                    out.close();
                                    in.close();
                                    sock.close();
                                    return;
                                }
                                //System.out.println(inputData);
                                final OkHttpClient client = inputData[0].getProxyIP() != null ? builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(inputData[0].getProxyIP(), inputData[0].getProxyPort()))).build() : new OkHttpClient();
                                final String nicosid = inputData[0].getCookieNicosid();
                                final String domand_bid = inputData[0].getCookieDomand_bid();

                                Request request = new Request.Builder()
                                        .url("https://"+ RequestHost + RequestURI)
                                        .addHeader("Cookie", "nicosid="+nicosid+"; domand_bid=" + domand_bid)
                                        .build();
                                Response response = client.newCall(request).execute();
                                if (response.body() != null){
                                    if (response.code() == 200){
                                        out.write(("HTTP/"+httpVersion+" 200 OK\nContent-Type: "+response.header("Content-Type")+"\n\n").getBytes(StandardCharsets.UTF_8));

                                        if (RequestHeader.equals("GET")){
                                            out.write(response.body().bytes());
                                        }
                                        out.flush();

                                    } else {
                                        out.write(("HTTP/"+httpVersion+" 404 Not Found\nContent-Type: text/plain; charset=utf-8\n\n404").getBytes(StandardCharsets.UTF_8));
                                        out.flush();

                                        DataList.remove(inputData[0].getID());
                                        try {
                                            JedisPool jedisPool = new JedisPool(input.string("RedisServer"), input.integer("RedisPort"));
                                            Jedis jedis = jedisPool.getResource();
                                            if (!input.string("RedisPass").isEmpty()){
                                                jedis.auth(input.string("RedisPass"));
                                            }

                                            jedis.del("nico-hls:"+inputData[0].getID());

                                            jedis.close();
                                            jedisPool.close();
                                        } catch (Exception e){
                                            // e.printStackTrace();
                                        }
                                    }

                                    out.close();
                                    in.close();
                                    sock.close();
                                }
                                response.close();

                                return;
                            }

                            out.write(("HTTP/"+httpVersion+" 404 Not Found\nContent-Type: text/plain; charset=utf-8\n\n404").getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            out.close();
                            in.close();
                            sock.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            } catch (Exception e){
                //e.printStackTrace();
            }
        }).start();

        // 変換受付
        try {
            ServerSocket svSock = new ServerSocket(25250);
            while (true) {
                try {
                    //System.out.println("受信待機");
                    Socket sock = svSock.accept();
                    new Thread(() -> {
                        try {
                            //System.out.println("受信");
                            byte[] data = new byte[1024768];
                            int readSize = sock.getInputStream().read(data);
                            byte[] bytes = Arrays.copyOf(data, readSize);
                            //System.out.println(bytes.length);
                            //System.out.println(new String(bytes, StandardCharsets.UTF_8));
                            if (bytes.length == 0) {
                                sock.close();
                                return;
                            }

                            String s = new String(bytes, StandardCharsets.UTF_8);

                            // 前準備
                            String[] split = UUID.randomUUID().toString().split("-");
                            final String fileId = new Date().getTime() + "_" + split[0];
                            InputData inputData = new Gson().fromJson(s, InputData.class);
                            final OkHttpClient client = inputData.getProxy() != null ? builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(inputData.getProxy().split(":")[0], Integer.parseInt(inputData.getProxy().split(":")[1])))).build() : new OkHttpClient();

                            JsonElement json = new Gson().fromJson(inputData.getCookie(), JsonElement.class);

                            String nicosid = json.getAsJsonObject().get("nicosid").getAsString();
                            String domand_bid = json.getAsJsonObject().get("domand_bid").getAsString();

                            String video_m3u8 = "";
                            String audio_m3u8 = "";
                            try {
                                Request request_video_m3u8 = new Request.Builder()
                                        .url(inputData.getVideoURL())
                                        .addHeader("Cookie", "nicosid="+nicosid+"; domand_bid=" + domand_bid)
                                        .build();
                                Response response1 = client.newCall(request_video_m3u8).execute();

                                if (response1.body() != null){
                                    video_m3u8 = response1.body().string();
                                }
                                //System.out.println(video_m3u8);
                                response1.close();

                                Request request_audio_m3u8 = new Request.Builder()
                                        .url(inputData.getAudioURL())
                                        .addHeader("Cookie", "nicosid="+nicosid+"; domand_bid=" + domand_bid)
                                        .build();
                                Response response2 = client.newCall(request_audio_m3u8).execute();

                                if (response2.body() != null){
                                    audio_m3u8 = response2.body().string();
                                }
                                response2.close();
                            } catch (Exception e){
                                //e.printStackTrace();
                            }

                            String CookieID = null;

                            StringBuilder sb = new StringBuilder();
                            for (String str : video_m3u8.split("\n")){
                                if (str.startsWith("#")){
                                    Matcher matcher = matcher_5.matcher(str);
                                    Matcher matcher2 = matcher_6.matcher(str);
                                    if (matcher.find()){
                                        sb.append("#EXT-X-MAP:URI=\"").append(matcher.group(1).replaceAll("https://asset\\.domand\\.nicovideo\\.jp", "")).append("&HostURL=").append(str.split("/")[2]).append("\"\n");
                                        continue;
                                    }
                                    if (matcher2.find()){
                                        sb.append("#EXT-X-KEY:METHOD=AES-128,URI=\"").append(matcher2.group(1).replaceAll("https://delivery\\.domand\\.nicovideo\\.jp", "")).append("&HostURL=").append(str.split("/")[2]).append("\",IV=").append(matcher2.group(2)).append("\n");
                                        continue;
                                    }
                                    sb.append(str).append("\n");
                                    continue;
                                }

                                //System.out.println(str);
                                sb.append(str.replaceAll("https://asset\\.domand\\.nicovideo\\.jp", "")).append("&HostURL=").append(str.split("/")[2]).append("\n");
                                if (CookieID == null){
                                    CookieID = str.split("/")[3];
                                }
                            }

                            //System.out.println(sb.toString());
                            video_m3u8 = sb.toString();

                            StringBuilder sb2 = new StringBuilder();
                            for (String str : audio_m3u8.split("\n")){

                                if (str.startsWith("#")){
                                    Matcher matcher = matcher_5.matcher(str);
                                    Matcher matcher2 = matcher_6.matcher(str);
                                    if (matcher.find()){
                                        sb2.append("#EXT-X-MAP:URI=\"").append(matcher.group(1).replaceAll("https://asset\\.domand\\.nicovideo\\.jp", "")).append("&HostURL=").append(str.split("/")[2]).append("\"\n");
                                        continue;
                                    }
                                    if (matcher2.find()){
                                        sb2.append("#EXT-X-KEY:METHOD=AES-128,URI=\"").append(matcher2.group(1).replaceAll("https://delivery\\.domand\\.nicovideo\\.jp", "")).append("&HostURL=").append(str.split("/")[2]).append("\",IV=").append(matcher2.group(2)).append("\n");
                                        continue;
                                    }

                                    sb2.append(str).append("\n");
                                    continue;
                                }

                                //System.out.println(str);
                                sb2.append(str.replaceAll("https://asset\\.domand\\.nicovideo\\.jp", "")).append("&HostURL=").append(str.split("/")[2]).append("\n");
                            }
                            audio_m3u8 = sb2.toString();

                            VideoData videoData = new VideoData();
                            videoData.setExpiryDate(new Date().getTime() + 86400000);
                            videoData.setID(fileId);
                            videoData.setCookieID(CookieID);
                            videoData.setVideoM3u8(video_m3u8);
                            videoData.setAudioM3u8(audio_m3u8);
                            videoData.setProxyIP(inputData.getProxy() != null ? inputData.getProxy().split(":")[0] : null);
                            videoData.setProxyPort(inputData.getProxy() != null ? Integer.parseInt(inputData.getProxy().split(":")[1]) : 3128);
                            videoData.setCookieNicosid(nicosid);
                            videoData.setCookieDomand_bid(domand_bid);
                            videoData.setVRC(inputData.isVRC());

                            try {
                                //System.out.println(json.getAsJsonObject().get("MainM3U8").getAsString());
                                // くっつけたm3u8を用意
                                Matcher matcher = matcher_7.matcher(json.getAsJsonObject().get("MainM3U8").getAsString());

                                String m3u8 = "";

                                if (matcher.find()){
                                    m3u8 = "#EXTM3U\n" +
                                            "#EXT-X-VERSION:6\n" +
                                            "#EXT-X-INDEPENDENT-SEGMENTS\n" +
                                            "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio-aac-64kbps\",NAME=\"Main Audio\",DEFAULT=YES,URI=\"/video/"+fileId+"/audio.m3u8\"\n" +
                                            "#EXT-X-STREAM-INF:BANDWIDTH="+matcher.group(1)+",AVERAGE-BANDWIDTH="+matcher.group(2)+",CODECS=\""+matcher.group(3)+"\",RESOLUTION="+matcher.group(4)+",FRAME-RATE="+matcher.group(5)+",AUDIO=\"audio-aac-64kbps\"\n" +
                                            "/video/"+fileId+"/video.m3u8";
                                }
                                videoData.setMainM3u8(m3u8);

                            } catch (Exception e){
                                //e.printStackTrace();
                            }

                            //System.out.println("debug");
                            DataList.put(fileId, videoData);
                            new Thread(()->{
                                //System.out.println(input.string("RedisServer") + " / " + input.integer("RedisPort"));
                                //System.out.println("!");

                                JedisPool jedisPool = new JedisPool(input.string("RedisServer"), input.integer("RedisPort"));
                                Jedis jedis = jedisPool.getResource();
                                if (!input.string("RedisPass").isEmpty()){
                                    jedis.auth(input.string("RedisPass"));
                                }

                                jedis.set("nico-hls:"+videoData.getID(), gson.toJson(videoData));
                                jedis.close();
                                jedisPool.close();

                            }).start();

                            String host = "n.nicovrc.net";
                            if (new File("./host.txt").exists()){
                                StringBuilder lines = new StringBuilder();
                                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File("./host.txt")), StandardCharsets.UTF_8));){
                                    String str;

                                    while ((str = reader.readLine()) != null) {
                                        lines.append(str).append("\n");
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                host = lines.toString().replaceAll("\n","");
                            }

                            byte[] byte_o = ("https://"+host+"/video/"+fileId+"/main.m3u8").getBytes(StandardCharsets.UTF_8);
                            sock.getOutputStream().write(byte_o);
                            sock.getOutputStream().flush();
                            sock.close();
                        } catch (Exception e){
                            e.printStackTrace();
                        }
                    }).start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e){
            //e.printStackTrace();
        }

    }
}