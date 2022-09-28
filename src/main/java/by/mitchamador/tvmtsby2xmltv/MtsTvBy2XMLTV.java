package by.mitchamador.tvmtsby2xmltv;

import by.mitchamador.tvmtsby2xmltv.objects.MtsTvChannel;
import by.mitchamador.tvmtsby2xmltv.objects.MtsTvProgram;
import by.mitchamador.xmltv.*;
import org.apache.commons.cli.*;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.async.methods.*;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MtsTvBy2XMLTV {

    private String xmltvPath = null;
    private String m3uPath = null;
    private String username = null;
    private String password = null;
    private int xmltvDays = 0;

    public static void main(String[] args) {

        Options options = new Options();
        options.addOption(Option.builder("x").longOpt("xmltv").argName("filename for xmltv").hasArg().desc("Output filename for xmltv epg").build());
        options.addOption(Option.builder("d").longOpt("days").argName("number of days for xmltv").hasArg().desc("Get xmltv program for specified number of days").build());
        options.addOption(Option.builder("m").longOpt("m3u").argName("filename for m3u").hasArg().desc("Output filename for m3u playlist").build());
        options.addOption(Option.builder("u").longOpt("username").argName("username for tv.mts.by").hasArg().desc("Authentication name for tv.mts.by").build());
        options.addOption(Option.builder("p").longOpt("password").argName("password for tv.mts.by").hasArg().desc("Authentication password for tv.mts.by").build());

        try {
            CommandLine commandLine = new DefaultParser().parse(options, args);

            Map<String, String> params = new HashMap<>();

            params.put("xmltvPath", commandLine.getOptionValue("xmltv", null));
            params.put("m3uPath", commandLine.getOptionValue("m3u", null));
            params.put("username", commandLine.getOptionValue("username", null));
            params.put("password", commandLine.getOptionValue("password", null));
            params.put("sXmltvDays", commandLine.getOptionValue("days", null));

            MtsTvBy2XMLTV mtsTvBy2XMLTV = new MtsTvBy2XMLTV(params);
            mtsTvBy2XMLTV.run();

        } catch (ParseException exception) {
            System.out.print("Parse error: ");
            System.out.println(exception.getMessage());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }


    }

    public MtsTvBy2XMLTV(Map<String, String> params) {
        xmltvPath = params.get("xmltvPath");
        m3uPath = params.get("xmltvPath");
        username = params.get("xmltvPath");
        password = params.get("xmltvPath");
        String sXmltvDays = params.get("sXmltvDays");
        if (sXmltvDays != null) {
            try {
                xmltvDays = Integer.parseInt(sXmltvDays);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private HttpClientResponseHandler<String> getHttpClientResponseHandler() {
        // Create a custom response handler
        final HttpClientResponseHandler<String> responseHandler = new HttpClientResponseHandler<String>() {

            @Override
            public String handleResponse(
                    final ClassicHttpResponse response) throws IOException {
                final int status = response.getCode();
                if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
                    final HttpEntity entity = response.getEntity();
                    try {
                        return entity != null ? EntityUtils.toString(entity) : null;
                    } catch (org.apache.hc.core5.http.ParseException ex) {
                        throw new ClientProtocolException(ex);
                    }
                } else {
                    throw new ClientProtocolException("Unexpected response status: " + status);
                }
            }
        };
        return responseHandler;
    }

    public void run() throws ExecutionException, InterruptedException {
        /*if (xmltvPath != null || m3uPath != null)*/ {

            List<MtsTvChannel> channelList = null;

            try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {
                final HttpGet httpget = new HttpGet("https://fe.tv.mts.by/channels");
                String jsonChannels = httpclient.execute(httpget, getHttpClientResponseHandler());
                channelList = parseChannels(jsonChannels);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (channelList != null) {

                final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                        .setSoTimeout(Timeout.ofSeconds(5))
                        .build();

                final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                        .setIOReactorConfig(ioReactorConfig)
                        .build();

                client.start();

                final HttpHost target = new HttpHost("https", "fe.tv.mts.by");

                // https://fe.tv.mts.by/channels/53f30c5b4e2e670103007d82/programs?period=1663841214:1664758800&app.version=2.11.8&app.hash=cad85e92&app.buildDate=1660049409352&app.id=sequoia&app.buildType=portal&app.whitelabel=mtsby&device.type=pc&device.brand=pc&device.model=none&device.uuid=d697472b-8c0f-4925-8daa-86b209902a45

                long periodStart = Calendar.getInstance().getTime().getTime() / 1000;
                periodStart = (periodStart / TimeUnit.HOURS.toSeconds(6)) * TimeUnit.HOURS.toSeconds(6);
                long periodEnd = (periodStart + (xmltvDays == 0 ? 1 : xmltvDays) * TimeUnit.DAYS.toSeconds(1));

                Map<String, MtsTvChannel> channelsGuideMap = new ConcurrentHashMap<>();

                //ExecutorService pool = Executors.newFixedThreadPool(threads <= 0 ? Runtime.getRuntime().availableProcessors() : threads);
                List<Future<SimpleHttpResponse>> futures = new ArrayList<>();

                for (final MtsTvChannel channel : channelList) {
                    //if (channelList.indexOf(channel) > 0) break;

                    final SimpleHttpRequest request = SimpleRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/channels/" + channel.getId() + "/programs")
                            .addParameter("period", periodStart + ":" + periodEnd)
                            .build();

                    //System.out.println("Executing request " + request);

                    final Future<SimpleHttpResponse> future = client.execute(
                            SimpleRequestProducer.create(request),
                            SimpleResponseConsumer.create(),
                            new FutureCallback<SimpleHttpResponse>() {
                                @Override
                                public void completed(final SimpleHttpResponse response) {
                                    channel.setPrograms(parsePrograms(new String(response.getBodyBytes(), StandardCharsets.UTF_8)));
                                }

                                @Override
                                public void failed(Exception ex) {
                                }

                                @Override
                                public void cancelled() {
                                }
                            });
                    futures.add(future);
                }

                for (Future<SimpleHttpResponse> future : futures) {
                    future.get();
                }

                client.close(CloseMode.GRACEFUL);
            }

            XMLTV xmltv = new XMLTV();

            if (channelList != null) {
                for (MtsTvChannel channel : channelList) {
                    Channel xmlTvChannel = new Channel(channel.getId());

                    DisplayName displayName = new DisplayName();
                    displayName.setName(channel.getTitle());

                    xmlTvChannel.setName(new ArrayList<>(Collections.singletonList(displayName)));

                    if (channel.getThumbnailUrl() != null && !channel.getThumbnailUrl().isEmpty()) {
                        Icon icon = new Icon();
                        icon.setSrc(channel.getThumbnailUrl());
                        xmlTvChannel.setIcon(icon);
                    }

                    xmltv.getChannels().add(xmlTvChannel);

                    if (channel.getPrograms() != null) {
                        for (MtsTvProgram program : channel.getPrograms()) {
                            Programme xmlTvProgramme = new Programme(channel.getId());

                            xmlTvProgramme.setStart(program.getTimeStart());
                            xmlTvProgramme.setStop(program.getTimeEnd());

                            xmlTvProgramme.setTitle(new Title(program.getTitle()));
                            xmlTvProgramme.setDescription(new Title(program.getDescription()));

                            xmltv.getProgrammes().add(xmlTvProgramme);
                        }
                    }
                }
            }

            if (xmltvPath != null) {
                try (PrintStream ps = new PrintStream(xmltvPath)) {
                    ps.println(xmltv.toXML());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println(xmltv.toXML());
            }

        }
    }

    private List<MtsTvChannel> parseChannels(String json) {
        JSONArray jsonChannels = new JSONObject(json).optJSONArray("channels");
        return IntStream.range(0, jsonChannels.length()).mapToObj(jsonChannels::getJSONObject).parallel()
                .map(jsonChannel -> {
                    MtsTvChannel channel = new MtsTvChannel(jsonChannel.optString("id"));

                    JSONObject jsonInfo = jsonChannel.optJSONObject("info");

                    JSONObject metaInfo = jsonInfo.getJSONObject("metaInfo");
                    channel.setTitle(metaInfo.optString("title", null));

                    JSONObject mediaInfo = jsonInfo.getJSONObject("mediaInfo");
                    JSONArray thumbnails = mediaInfo.optJSONArray("thumbnails");
                    if (thumbnails != null && !thumbnails.isEmpty()) {
                        channel.setThumbnailUrl(thumbnails.getJSONObject(0).optString("url"));
                    }

                    JSONObject playbackInfo = jsonInfo.getJSONObject("playbackInfo");
                    channel.setPlayUrl(playbackInfo.optString("playUrl"));

                    JSONObject purchaseInfo = jsonInfo.getJSONObject("purchaseInfo");
                    channel.setBought(purchaseInfo.optBoolean("bought"));

                    return channel;
                })
                .filter(channel -> channel.getId() != null)
                .sorted(Comparator.comparing(MtsTvChannel::getTitle))
                .peek(ch -> {
                    if (ch.getTitle() != null && ch.getTitle().length() > 4) {
                        ch.setTitle(ch.getTitle().substring(4));
                    }
                })
                .collect(Collectors.toList());

    }

        /*
        {
          "id": "6324ecd59bcf9d072d704424",
          "metaInfo": {
            "title": "Универ. Новая общага Шанс",
            "description": "Ситком, повествующий о жизни студентов, проживающих в одном блоке общежития. Одно из продолжений телесериала \"Универ\"",
            "age_rating": 16
          },
          "scheduleInfo": {
            "start": 1663842300,
            "end": 1663843800,
            "duration": 1500
          },
          "mediaInfo": {
            "thumbnails": [
              {
                "url": "http://img.tv.mts.by:80/image/aHR0cDovLzEyNy4wLjAuMTo4MC90dmltYWdlcy90aHVtYi8xZWI5MWNmZmI1MmVmZGEyMWJiMzIyZmJiYzZlNDFlMl9vcmlnLmpwZw==",
                "md5": "1eb91cffb52efda21bb322fbbc6e41e2"
              }
            ]
          },
          "updateInfo": {
            "mtime": 1663364309
          },
          "playbackInfo": {
            "playUrl": "",
            "dvrRestriction": false
          }
        }
     */

    private List<MtsTvProgram> parsePrograms(String json) {
        JSONArray jsonPrograms = new JSONObject(json).optJSONArray("programs");
        //return jsonPrograms.toList().stream()
        return IntStream.range(0, jsonPrograms.length()).mapToObj(jsonPrograms::getJSONObject).parallel()
                .map(jsonChannel -> {
                    MtsTvProgram program = new MtsTvProgram(jsonChannel.optString("id"));

                    JSONObject metaInfo = jsonChannel.getJSONObject("metaInfo");
                    program.setTitle(metaInfo.optString("title", null));
                    program.setDescription(metaInfo.optString("description", null));

                    JSONObject scheduleInfo = jsonChannel.getJSONObject("scheduleInfo");
                    program.setTimeStart(new Date(1000 * scheduleInfo.optLong("start", 0)));
                    program.setTimeEnd(new Date(1000 * scheduleInfo.optLong("end", 0)));

                    return program;
                })
                .collect(Collectors.toList());
    }

}
