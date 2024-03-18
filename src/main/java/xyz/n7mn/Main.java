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

    private static final HashMap<String, InputData> CookieList = new HashMap<>();

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
                            Matcher matcher3 = Pattern.compile("(GET|HEAD) (.+) HTTP").matcher(httpRequest);

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

                                    if (matcher1.group(1).equals("GET")){
                                        FileInputStream stream = new FileInputStream("./" + group.replaceAll("%22","").replaceAll("\\./", "").replaceAll("\\.\\./", ""));
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
                                final String RequestURI = split[0];
                                final String RequestHost = split[1];
                                InputData inputData = CookieList.get(RequestURI.split("/")[1]);
                                if (inputData == null){
                                    inputData = CookieList.get(RequestURI.split("/")[2]);
                                }
                                //System.out.println(inputData);
                                final OkHttpClient client = inputData.getProxy() != null ? builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(inputData.getProxy().split(":")[0], Integer.parseInt(inputData.getProxy().split(":")[1])))).build() : new OkHttpClient();
                                final JsonElement json = new Gson().fromJson(inputData.getCookie(), JsonElement.class);
                                final String nicosid = json.getAsJsonObject().get("nicosid").getAsString();
                                final String domand_bid = json.getAsJsonObject().get("domand_bid").getAsString();

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
                            final String basePass = "./" + fileId + "/";
                            File file1 = new File(basePass);
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

                                String CookieID = null;

                                StringBuilder sb = new StringBuilder();
                                for (String str : video_m3u8.split("\n")){
                                    if (str.startsWith("#")){
                                        Matcher matcher = Pattern.compile("#EXT-X-MAP:URI=\"(.+)\"").matcher(str);
                                        Matcher matcher2 = Pattern.compile("#EXT-X-KEY:METHOD=AES-128,URI=\"(.+)\",IV=(.+)").matcher(str);
                                        if (matcher.find()){
                                            sb.append("#EXT-X-MAP:URI=\"").append(matcher.group(1).replaceAll("asset\\.domand\\.nicovideo\\.jp", "n.nicovrc.net")).append("&HostURL=").append(str.split("/")[2]).append("\"\n");
                                            continue;
                                        }
                                        if (matcher2.find()){
                                            sb.append("#EXT-X-KEY:METHOD=AES-128,URI=\"").append(matcher2.group(1).replaceAll("delivery\\.domand\\.nicovideo\\.jp", "n.nicovrc.net")).append("&HostURL=").append(str.split("/")[2]).append("\",IV=").append(matcher2.group(2)).append("\n");
                                            continue;
                                        }
                                        sb.append(str).append("\n");
                                        continue;
                                    }

                                    //System.out.println(str);
                                    sb.append(str.replaceAll("asset\\.domand\\.nicovideo\\.jp", "n.nicovrc.net")).append("&HostURL=").append(str.split("/")[2]).append("\n");
                                    if (CookieID == null){
                                        CookieID = str.split("/")[3];
                                    }
                                }

                                //System.out.println(sb.toString());
                                video_m3u8 = sb.toString();

                                StringBuilder sb2 = new StringBuilder();
                                for (String str : audio_m3u8.split("\n")){

                                    if (str.startsWith("#")){
                                        Matcher matcher = Pattern.compile("#EXT-X-MAP:URI=\"(.+)\"").matcher(str);
                                        Matcher matcher2 = Pattern.compile("#EXT-X-KEY:METHOD=AES-128,URI=\"(.+)\",IV=(.+)").matcher(str);
                                        if (matcher.find()){
                                            sb2.append("#EXT-X-MAP:URI=\"").append(matcher.group(1).replaceAll("asset\\.domand\\.nicovideo\\.jp", "n.nicovrc.net")).append("&HostURL=").append(str.split("/")[2]).append("\"\n");
                                            continue;
                                        }
                                        if (matcher2.find()){
                                            sb2.append("#EXT-X-KEY:METHOD=AES-128,URI=\"").append(matcher2.group(1).replaceAll("delivery\\.domand\\.nicovideo\\.jp", "n.nicovrc.net")).append("&HostURL=").append(str.split("/")[2]).append("\",IV=").append(matcher2.group(2)).append("\n");
                                            continue;
                                        }

                                        sb2.append(str).append("\n");
                                        continue;
                                    }

                                    //System.out.println(str);
                                    sb2.append(str.replaceAll("asset\\.domand\\.nicovideo\\.jp", "n.nicovrc.net")).append("&HostURL=").append(str.split("/")[2]).append("\n");
                                }
                                audio_m3u8 = sb2.toString();

                                try {

                                    FileOutputStream m3u8_stream = new FileOutputStream( basePass + "video.m3u8");
                                    m3u8_stream.write(video_m3u8.getBytes(StandardCharsets.UTF_8));
                                    m3u8_stream.flush();
                                    m3u8_stream.close();

                                    FileOutputStream m3u8_stream2 = new FileOutputStream(basePass + "audio.m3u8");
                                    m3u8_stream2.write(audio_m3u8.getBytes(StandardCharsets.UTF_8));
                                    m3u8_stream2.flush();
                                    m3u8_stream2.close();
                                } catch (Exception e){{
                                    // e.printStackTrace();
                                }}
                                //System.out.println("DL開始(proxy "+inputData.getProxy()+") : " + new Date().getTime());

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
                                                "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio-aac-64kbps\",NAME=\"Main Audio\",DEFAULT=YES,URI=\"/video/"+fileId+"/audio.m3u8\"\n" +
                                                "#EXT-X-STREAM-INF:BANDWIDTH="+matcher.group(1)+",AVERAGE-BANDWIDTH="+matcher.group(2)+",CODECS=\""+matcher.group(3)+"\",RESOLUTION="+matcher.group(4)+",FRAME-RATE="+matcher.group(5)+",AUDIO=\"audio-aac-64kbps\"\n" +
                                                "/video/"+fileId+"/video.m3u8";

                                        m3u8_2 = "#EXTM3U\n" +
                                                "#EXT-X-VERSION:6\n" +
                                                "#EXT-X-INDEPENDENT-SEGMENTS\n" +
                                                "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio-aac-64kbps\",NAME=\"Main Audio\",DEFAULT=YES,URI=\"/video/"+fileId+"/audio.m3u8\"\n" +
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

                                    CookieList.put(CookieID, inputData);
                                    //System.out.println("de1 : " + CookieID);

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