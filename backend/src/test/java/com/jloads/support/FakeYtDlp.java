package com.jloads.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Substituto do yt-dlp para testes automatizados. O comportamento é escolhido pelo ID do vídeo:
 * <ul>
 *   <li>{@code okvideo0001}: sucesso rápido</li>
 *   <li>{@code slowvideo01}: download lento (para cancelamento/timeout)</li>
 *   <li>{@code unavailable}: vídeo indisponível</li>
 *   <li>{@code forbidden01}: HTTP 403 durante o download</li>
 *   <li>{@code livestream1}: transmissão ao vivo</li>
 *   <li>{@code hugevideo01}: arquivo maior que o limite</li>
 * </ul>
 */
public final class FakeYtDlp {

    public static final String OK = "okvideo0001";
    public static final String SLOW = "slowvideo01";
    public static final String UNAVAILABLE = "unavailable";
    public static final String FORBIDDEN = "forbidden01";
    public static final String LIVE = "livestream1";
    public static final String HUGE = "hugevideo01";

    private FakeYtDlp() {
    }

    public static void main(String[] args) throws Exception {
        List<String> arguments = Arrays.asList(args);
        String url = arguments.getLast();
        String videoId = url.substring(url.indexOf("v=") + 2);

        if (UNAVAILABLE.equals(videoId)) {
            System.err.println("ERROR: [youtube] " + videoId + ": This video is unavailable");
            System.exit(1);
        }
        if (arguments.contains("--dump-single-json")) {
            System.out.println(metadataJson(videoId));
            return;
        }
        download(arguments, videoId);
    }

    private static void download(List<String> arguments, String videoId) throws IOException, InterruptedException {
        Path directory = Path.of(valueAfter(arguments, "--paths"));
        boolean audio = arguments.contains("--extract-audio");
        System.out.println("[youtube] Extracting URL: https://www.youtube.com/watch?v=" + videoId);

        if (FORBIDDEN.equals(videoId)) {
            System.out.println("ERROR: unable to download video data: HTTP Error 403: Forbidden");
            System.exit(1);
        }
        if (HUGE.equals(videoId)) {
            System.out.println("[download] File is larger than max-filesize (999999999 bytes > 1000 bytes). Aborting.");
            return;
        }

        int steps = SLOW.equals(videoId) ? 600 : 5;
        long total = 50_000;
        Path part = directory.resolve("media.f1.part");
        Files.writeString(part, "partial");
        for (int i = 1; i <= steps; i++) {
            long done = total * i / steps;
            System.out.println("YTDLP_PROGRESS|downloading|" + done + "|" + total + "|NA|1048576.0|" + (steps - i));
            System.out.flush();
            Thread.sleep(SLOW.equals(videoId) ? 100 : 20);
        }
        System.out.println("YTDLP_PROGRESS|finished|" + total + "|" + total + "|NA|NA|NA");
        Files.delete(part);

        String processor = audio ? "ExtractAudio" : "Merger";
        System.out.println("YTDLP_POSTPROCESS|started|" + processor);
        Files.write(directory.resolve(audio ? "media.mp3" : "media.mp4"), new byte[4096]);
        System.out.println("YTDLP_POSTPROCESS|finished|" + processor);
        System.out.println("YTDLP_POSTPROCESS|started|MoveFiles");
        System.out.println("YTDLP_POSTPROCESS|finished|MoveFiles");
    }

    private static String metadataJson(String videoId) {
        boolean live = LIVE.equals(videoId);
        return """
                {"id":"%s","_type":"video","title":"Vídeo de teste: <script>/..\\\\CON?","channel":"Canal Teste",
                 "duration":125,"thumbnail":"https://i.ytimg.com/vi/%s/hqdefault.jpg","is_live":%s,
                 "live_status":"%s","formats":[
                  {"format_id":"sb0","ext":"mhtml","vcodec":"none","acodec":"none"},
                  {"format_id":"140","ext":"m4a","vcodec":"none","acodec":"mp4a.40.2","filesize":2000000},
                  {"format_id":"18","ext":"mp4","vcodec":"avc1","acodec":"mp4a.40.2","height":360,"filesize":9000000},
                  {"format_id":"136","ext":"mp4","vcodec":"avc1","acodec":"none","height":720,"filesize":30000000},
                  {"format_id":"137","ext":"mp4","vcodec":"avc1","acodec":"none","height":1080,"filesize_approx":60000000}
                 ]}
                """.formatted(videoId, videoId, live, live ? "is_live" : "not_live");
    }

    private static String valueAfter(List<String> arguments, String option) {
        int index = arguments.indexOf(option);
        if (index < 0 || index + 1 >= arguments.size()) {
            throw new IllegalArgumentException("missing " + option);
        }
        return arguments.get(index + 1);
    }
}
