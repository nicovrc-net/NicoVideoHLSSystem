package xyz.n7mn;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final OkHttpClient.Builder builder = new OkHttpClient.Builder();

    public static void main(String[] args) {

        // 定期お掃除
        new Thread(()->{
            Timer timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    File file = new File("./");
                    for (File f : Objects.requireNonNull(file.listFiles())) {
                        Matcher matcher = Pattern.compile("(\\d+)_(.+)").matcher(f.getName());
                        if (matcher.find()){
                            Long l = Long.parseLong(matcher.group(1));
                            long time = new Date().getTime();

                            if ((time - l) >= 86400000L){
                                //System.out.println(time - l);
                                if (f.isFile()){
                                    continue;
                                }

                                File[] files = f.listFiles();
                                for (File fi : files){
                                    if (fi.getName().startsWith(".")){
                                        continue;
                                    }

                                    if (fi.isDirectory()){
                                        for (File fil : fi.listFiles()){
                                            if (fil.isDirectory()){
                                                for (File file1 : fi.listFiles()){
                                                    file1.delete();
                                                }
                                                fi.delete();
                                            } else {
                                                fil.delete();
                                            }
                                        }
                                        fi.delete();
                                    } else {
                                        fi.delete();
                                    }

                                    f.delete();
                                }
                                f.delete();
                            }
                        }
                    }
                }
            }, 0L, 10000L);
        }).start();

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

                            Matcher matcher1 = Pattern.compile("(GET|HEAD) /video/(.+) HTTP").matcher(httpRequest);
                            Matcher matcher2 = Pattern.compile("HTTP/1\\.(\\d)").matcher(httpRequest);

                            final String httpVersion = "1." + (matcher2.find() ? matcher2.group(1) : "1");

                            //System.out.println(httpRequest);

                            if (matcher1.find()) {

                                String group = matcher1.group(2);
                                File file = new File("./" + group.replaceAll("%22","").replaceAll("\\./", "").replaceAll("\\.\\./", ""));
                                System.out.println("./" + group.replaceAll("%22","").replaceAll("\\./", "").replaceAll("\\.\\./", ""));
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
                                    FileInputStream stream = new FileInputStream("./" + group.replaceAll("%22","").replaceAll("\\./", "").replaceAll("\\.\\./", ""));
                                    out.write(stream.readAllBytes());
                                    stream.close();
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
                            final String basePass = "./" + fileId + "/";
                            final String videoPass = basePass + "video/";
                            final String audioPass = basePass + "audio/";
                            File file1 = new File(basePass);
                            File file2 = new File(videoPass);
                            File file3 = new File(audioPass);

                            new Thread(()->{

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

                                if (!file1.exists()){
                                    file1.mkdir();
                                }
                                if (!file2.exists()){
                                    file2.mkdir();
                                }
                                if (!file3.exists()){
                                    file3.mkdir();
                                }

                                Matcher matcher1 = Pattern.compile("#EXT-X-KEY:METHOD=(.+),URI=\"(.+)\",IV=([a-z0-9A-Z]+)").matcher(video_m3u8);
                                Matcher matcher1_1 = Pattern.compile("#EXT-X-MAP:URI=\"(.+)\"").matcher(video_m3u8);
                                Matcher matcher2 = Pattern.compile("#EXT-X-KEY:METHOD=(.+),URI=\"(.+)\",IV=([a-z0-9A-Z]+)").matcher(audio_m3u8);
                                Matcher matcher2_1 = Pattern.compile("#EXT-X-MAP:URI=\"(.+)\"").matcher(audio_m3u8);

                                final String VideoKeyURL;
                                final String VideoInitURL;
                                final String VideoKeyIV;
                                final String AudioKeyURL;
                                final String AudioKeyIV;
                                final String AudioInitURL;


                                if (matcher1.find()){
                                    VideoKeyURL = matcher1.group(2);
                                    VideoKeyIV = matcher1.group(3);
                                } else {
                                    VideoKeyURL = "";
                                    VideoKeyIV = "";
                                }
                                if (matcher1_1.find()){
                                    VideoInitURL = matcher1_1.group(1);
                                } else {
                                    VideoInitURL = "";
                                }
                                if (matcher2.find()){
                                    AudioKeyURL = matcher2.group(2);
                                    AudioKeyIV = matcher2.group(3);
                                } else {
                                    AudioKeyURL = "";
                                    AudioKeyIV = "";
                                }
                                if (matcher2_1.find()){
                                    AudioInitURL = matcher2_1.group(1);
                                } else {
                                    AudioInitURL = "";
                                }

                                // 動画
                                List<String> videoUrl = new ArrayList<>();
                                StringBuilder sb = new StringBuilder();
                                int i = 1;
                                for (String str : video_m3u8.split("\n")){
                                    if (str.startsWith("#")){
                                        if (str.startsWith("#EXT-X-MAP")){
                                            sb.append("#EXT-X-MAP:URI=\"https://n.nicovrc.net/video/"+fileId+"/video/init01.cmfv\"\n");
                                            continue;
                                        }
                                        if (str.startsWith("#EXT-X-KEY")){
                                            sb.append("#EXT-X-KEY:METHOD=AES-128,URI=\"https://n.nicovrc.net/video/"+fileId+"/video/key.key\",IV=").append(VideoKeyIV).append("\n");
                                            continue;
                                        }
                                        sb.append(str).append("\n");
                                        continue;
                                    }

                                    videoUrl.add(str);
                                    sb.append(i).append(".cmfv\n");
                                    i++;
                                }
                                // 音声
                                List<String> audioUrl = new ArrayList<>();
                                StringBuilder sb2 = new StringBuilder();
                                i = 1;
                                for (String str : audio_m3u8.split("\n")){
                                    if (str.startsWith("#")){
                                        if (str.startsWith("#EXT-X-MAP")){
                                            sb2.append("#EXT-X-MAP:URI=\"https://n.nicovrc.net/video/"+fileId+"/audio/init01.cmfa\"\n");
                                            continue;
                                        }
                                        if (str.startsWith("#EXT-X-KEY")){
                                            sb2.append("#EXT-X-KEY:METHOD=AES-128,URI=\"https://n.nicovrc.net/video/"+fileId+"/audio/key.key\",IV=").append(AudioKeyIV).append("\n");
                                            continue;
                                        }
                                        sb2.append(str).append("\n");
                                        continue;
                                    }

                                    audioUrl.add(str);
                                    sb2.append(i).append(".cmfa\n");
                                    i++;
                                }

                                try {
                                    FileOutputStream m3u8_stream = new FileOutputStream(videoPass + "video.m3u8");
                                    m3u8_stream.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                                    m3u8_stream.flush();
                                    m3u8_stream.close();

                                    FileOutputStream m3u8_stream2 = new FileOutputStream(audioPass + "audio.m3u8");
                                    m3u8_stream2.write(sb2.toString().getBytes(StandardCharsets.UTF_8));
                                    m3u8_stream2.flush();
                                    m3u8_stream2.close();
                                } catch (Exception e){{
                                    // e.printStackTrace();
                                }}
                                //System.out.println("DL開始(proxy "+inputData.getProxy()+") : " + new Date().getTime());

                                new Thread(()->{
                                    try {
                                        Request request_init = new Request.Builder()
                                                .url(VideoInitURL)
                                                .addHeader("Cookie", "nicosid="+nicosid+"; domand_bid=" + domand_bid)
                                                .build();
                                        Response response_init = client.newCall(request_init).execute();
                                        if (response_init.body() != null){
                                            byte[] byte_o = response_init.body().bytes();
                                            FileOutputStream stream = new FileOutputStream(videoPass + "init01.cmfv");
                                            stream.write(byte_o);
                                            stream.close();
                                        }
                                        response_init.close();

                                        Request request_key = new Request.Builder()
                                                .url(VideoKeyURL)
                                                .addHeader("Cookie", "nicosid="+nicosid+"; domand_bid=" + domand_bid)
                                                .build();
                                        Response response_key = client.newCall(request_key).execute();
                                        if (response_key.body() != null){
                                            byte[] byte_o = response_key.body().bytes();
                                            FileOutputStream stream = new FileOutputStream(videoPass + "key.key");
                                            stream.write(byte_o);
                                            stream.close();
                                        }
                                        response_key.close();
                                    } catch (Exception e){
                                        //e.printStackTrace();
                                    }

                                    int x = 1;
                                    for (String url : videoUrl){
                                        try {
                                            //System.out.println("- "+url+" --");
                                            // https://asset.domand.nicovideo.jp/655c8569f16a0601757053f4/video/12/video-h264-720p/01.cmfv
                                            try {
                                                Request request = new Request.Builder()
                                                        .url(url)
                                                        .addHeader("Cookie", "nicosid="+nicosid+"; domand_bid=" + domand_bid)
                                                        .build();
                                                Response response = client.newCall(request).execute();
                                                if (response.body() != null){
                                                    //System.out.println(response.code());
                                                    byte[] byte_o = response.body().bytes();
                                                    FileOutputStream stream = new FileOutputStream(videoPass + x + ".cmfv");
                                                    stream.write(byte_o);
                                                    stream.close();
                                                }
                                                response.close();
                                            } catch (Exception e){
                                                e.printStackTrace();
                                            }
                                            x++;
                                        } catch (Exception e){
                                            //e.printStackTrace();
                                        }
                                    }
                                }).start();

                                // 音声
                                new Thread(()->{
                                    try {
                                        Request request_init = new Request.Builder()
                                                .url(AudioInitURL)
                                                .addHeader("Cookie", "nicosid="+nicosid+"; domand_bid=" + domand_bid)
                                                .build();
                                        Response response_init = client.newCall(request_init).execute();
                                        if (response_init.body() != null){
                                            byte[] byte_o= response_init.body().bytes();
                                            FileOutputStream stream = new FileOutputStream(audioPass + "init01.cmfa");
                                            stream.write(byte_o);
                                            stream.close();
                                        }
                                        response_init.close();

                                        Request request_key = new Request.Builder()
                                                .url(AudioKeyURL)
                                                .addHeader("Cookie", "nicosid="+nicosid+"; domand_bid=" + domand_bid)
                                                .build();
                                        Response response_key = client.newCall(request_key).execute();
                                        if (response_key.body() != null){
                                            byte[] byte_o = response_key.body().bytes();
                                            FileOutputStream stream = new FileOutputStream(audioPass + "key.key");
                                            stream.write(byte_o);
                                            stream.close();
                                        }
                                        response_key.close();

                                    } catch (Exception e){
                                        //e.printStackTrace();
                                    }

                                    int y = 1;
                                    for (String url : audioUrl){
                                        try {
                                            //System.out.println("- "+url+" --");
                                            // https://asset.domand.nicovideo.jp/655c8569f16a0601757053f4/video/12/video-h264-720p/01.cmfv
                                            try {
                                                try {
                                                    Request request = new Request.Builder()
                                                            .url(url)
                                                            .addHeader("Cookie", "nicosid="+nicosid+"; domand_bid=" + domand_bid)
                                                            .build();
                                                    Response response = client.newCall(request).execute();
                                                    if (response.body() != null){
                                                        //System.out.println(response.code());
                                                        byte[] byte_o = response.body().bytes();
                                                        FileOutputStream stream = new FileOutputStream(audioPass + y + ".cmfa");
                                                        stream.write(byte_o);
                                                        stream.close();
                                                    }
                                                    response.close();
                                                } catch (Exception e){
                                                    e.printStackTrace();
                                                }
                                            } catch (Exception e){
                                                //e.printStackTrace();
                                            }

                                            y++;
                                        } catch (Exception e){
                                            //e.printStackTrace();
                                        }
                                    }
                                }).start();

                                try {
                                    //System.out.println(json.getAsJsonObject().get("MainM3U8").getAsString());
                                    // くっつけたm3u8を用意
                                    Matcher matcher = Pattern.compile("#EXT-X-STREAM-INF:BANDWIDTH=(\\d+),AVERAGE-BANDWIDTH=(\\d+),CODECS=\"(.+)\",RESOLUTION=(.+),FRAME-RATE=(.+),AUDIO=\"(.+)\"").matcher(json.getAsJsonObject().get("MainM3U8").getAsString());

                                    String m3u8 = "";
                                    String m3u8_2 = "#EXTM3U\n" +
                                            "\n" +
                                            "https://n.nicovrc.net/video/"+fileId+"/sub.m3u8";

                                    if (matcher.find()){
                                        m3u8 = "#EXTM3U\n" +
                                                "#EXT-X-VERSION:6\n" +
                                                "#EXT-X-INDEPENDENT-SEGMENTS\n" +
                                                "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio-aac-64kbps\",NAME=\"Main Audio\",DEFAULT=YES,URI=\"https://n.nicovrc.net/video/"+fileId+"/audio/audio.m3u8\"\n" +
                                                "#EXT-X-STREAM-INF:BANDWIDTH="+matcher.group(1)+",AVERAGE-BANDWIDTH="+matcher.group(2)+",CODECS=\""+matcher.group(3)+"\",RESOLUTION="+matcher.group(4)+",FRAME-RATE="+matcher.group(5)+",AUDIO=\"audio-aac-64kbps\"\n" +
                                                "https://n.nicovrc.net/video/"+fileId+"/video/video.m3u8";

                                        m3u8_2 = "#EXTM3U\n" +
                                                "#EXT-X-VERSION:6\n" +
                                                "#EXT-X-INDEPENDENT-SEGMENTS\n" +
                                                "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio-aac-64kbps\",NAME=\"Main Audio\",DEFAULT=YES,URI=\"https://n.nicovrc.net/video/"+fileId+"/audio/audio.m3u8\"\n" +
                                                "#EXT-X-STREAM-INF:BANDWIDTH="+matcher.group(1)+",AVERAGE-BANDWIDTH="+matcher.group(2)+",CODECS=\""+matcher.group(3)+"\",RESOLUTION="+matcher.group(4)+",FRAME-RATE="+matcher.group(5)+",AUDIO=\"audio-aac-64kbps\"\n" +
                                                "https://n.nicovrc.net/video/"+fileId+"/sub.m3u8";
                                    }

                                    //System.out.println(m3u8);
                                    FileOutputStream stream = new FileOutputStream(basePass + "sub.m3u8");
                                    stream.write(m3u8.getBytes(StandardCharsets.UTF_8));
                                    stream.close();

                                    // VRC上で再生する用のdummyなm3u8を生成する
                                    FileOutputStream stream2 = new FileOutputStream(basePass + "main.m3u8");
                                    stream2.write(m3u8_2.getBytes(StandardCharsets.UTF_8));
                                    stream2.close();
                                } catch (Exception e){
                                    //e.printStackTrace();
                                }
                            }).start();

                            Thread.sleep(1000L);
                            byte[] byte_o = ("https://n.nicovrc.net/video/"+fileId+"/main.m3u8").getBytes(StandardCharsets.UTF_8);
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

        }
    }
}