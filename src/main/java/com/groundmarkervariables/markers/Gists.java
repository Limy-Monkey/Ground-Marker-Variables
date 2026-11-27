package com.groundmarkervariables.markers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.groundmarkervariables.variables.support.LatestData;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// Marker sets on GitHub gists: one set per gist, in its first *.gmv.json file. Reading is anonymous: sets come from their raw
// files, and only listing someone's gists uses the API (60 calls an hour per IP; 5,000 signed in). Signing in (device flow)
// is for publishing; its token is kept in a file in the plugin's own folder, not in config, which may sync. Blocking calls go
// through run(), off the client thread.
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class Gists {
    // The OAuth app's, with device flow on (not a secret).
    private static final String CLIENT_ID = "Ov23liplHyPXKKvrbnDE";
    private static final String API = "https://api.github.com/";
    // Someone's gists are listed newest first, PAGE_SIZE (the most) an API call, up to PAGES calls.
    private static final int PAGE_SIZE = 100;
    private static final int PAGES = 3;
    // A link alone: the gist's id (current ones hex, the oldest digits), anything after it ignored.
    private static final Pattern LINK = Pattern.compile(
        "(?i)(?:https?://)?(?:www\\.)?gist\\.github(?:usercontent)?\\.com/(?:[\\w-]+/)?([0-9a-f]{20,}|\\d+)(?:[/?#].*)?");
    public interface Task {
        void run() throws IOException;
    }

    private final OkHttpClient okHttpClient;
    private final Gson gson;
    private final ScheduledExecutorService executor;
    // Signed in: GitHub's name for you, and the token.
    @Getter
    @Nullable
    private volatile String login;
    @Nullable
    private volatile String token;
    // Where the sign-in is kept, inside the plugin's own folder (Plugin#getPluginDirectory, which Filepath keeps it in).
    @Nullable
    private Filepath tokenFile;

    public void startUp(Filepath tokenFile) {
        this.tokenFile = tokenFile;
        try (BufferedReader saved = tokenFile.exists() ? tokenFile.openBufferedReader() : null) {
            login = saved == null ? null : saved.readLine();
            token = saved == null ? null : saved.readLine();
            login = token == null ? null : login;
        } catch (IOException e) {
            log.warn("Couldn't read the GitHub sign-in", e);
        }
    }

    // A gist link's id if text is just one (whitespace aside), else null.
    @Nullable
    public static String linkId(String text) {
        Matcher link = LINK.matcher(text.strip());
        return link.matches() ? link.group(1) : null;
    }

    // login as gmv-partners.json's "names" shows it ({"<login>": "<name>"}), else as it is.
    public static String displayName(String login) {
        return named("\"" + Pattern.quote(login) + "\"\\s*:\\s*\"([^\"]+)\"", login);
    }

    // The login shown as name (see displayName), else name, as typed into Browse.
    public static String login(String name) {
        return named("\"([^\"]+)\"\\s*:\\s*\"" + Pattern.quote(name) + "\"", name);
    }

    // The group a "names" entry matching entry has, any case; else fallback.
    private static String named(String entry, String fallback) {
        Matcher names = Pattern.compile("\"names\"\\s*:\\s*\\{([^}]*)}").matcher(LatestData.text("gmv-partners.json"));
        Matcher found = names.find() ? Pattern.compile("(?i)" + entry).matcher(names.group(1)) : null;
        return found != null && found.find() ? found.group(1) : fallback;
    }

    // GMV partners' GitHub names in gmv-partners.json's list (partnersPage: the Partners page; othersMenu: Browse Others' Sets).
    public static List<String> partners(String list) {
        Matcher names = Pattern.compile("\"" + list + "\"\\s*:\\s*\\[([^\\]]*)]").matcher(LatestData.text("gmv-partners.json"));
        return !names.find() ? List.of() : Pattern.compile("\"([\\w-]+)\"").matcher(names.group(1)).results().map(match -> match.group(1))
            .collect(Collectors.toList());
    }

    // task off the client thread, on OkHttp's pool; onFail (if any) after a failure.
    public void run(Task task, @Nullable Runnable onFail) {
        okHttpClient.dispatcher().executorService().execute(() -> {
            try {
                task.run();
            } catch (IOException | RuntimeException e) {
                log.debug("GitHub request failed", e);
                if (onFail != null) {
                    onFail.run();
                }
            }
        });
    }

    // The gists holding a set: someone's newest PAGES pages of PAGE_SIZE (owner), or one (gists/<id>).
    public List<MarkerSet.Gist> list(String path) throws IOException {
        List<MarkerSet.Gist> found = new ArrayList<>();
        boolean owner = !path.startsWith("gists/");
        for (int page = 1; page <= (owner ? PAGES : 1); page++) {
            JsonElement listed = api("GET", owner ? "users/" + path + "/gists?per_page=" + PAGE_SIZE + "&page=" + page : path, null);
            JsonArray gists = listed == null ? new JsonArray() : listed.isJsonArray() ? listed.getAsJsonArray() : new JsonArray();
            if (listed != null && listed.isJsonObject()) {
                gists.add(listed);
            }
            for (JsonElement gist : gists) {
                MarkerSet.Gist set = gist(gist.getAsJsonObject());
                if (set != null) {
                    found.add(set);
                }
            }
            if (gists.size() < PAGE_SIZE) {
                break;
            }
        }
        return found;
    }

    // The set's text now; null once its author deleted it. The time in the query gets past the raw host's 5-minute cache,
    // which would serve a deleted or changed gist as it was.
    @Nullable
    public String text(MarkerSet.Gist gist) throws IOException {
        return call(new Request.Builder().url(HttpUrl.get("https://gist.githubusercontent.com/").newBuilder()
            .addPathSegments(gist.owner + "/" + gist.id + "/raw").addPathSegment(gist.file)
            .addQueryParameter("t", String.valueOf(System.currentTimeMillis())).build()));
    }

    // text as your gist: a new one (secret: by link only), or gist updated (GitHub can't change a gist's visibility). Returns
    // where it is.
    public MarkerSet.Gist save(@Nullable MarkerSet.Gist gist, String title, String text, boolean secret) throws IOException {
        JsonObject content = new JsonObject();
        content.addProperty("content", text);
        JsonObject files = new JsonObject();
        files.add(gist == null ? "marker-set.gmv.json" : gist.file, content);
        JsonObject body = new JsonObject();
        body.addProperty("description", title + " - a Ground Marker Variables marker set");
        if (gist == null) {
            body.addProperty("public", !secret);
        }
        body.add("files", files);
        return gist(api(gist == null ? "POST" : "PATCH", "gists" + (gist == null ? "" : "/" + gist.id), body).getAsJsonObject());
    }

    public void delete(MarkerSet.Gist gist) throws IOException {
        api("DELETE", "gists/" + gist.id, null);
    }

    // GitHub's device flow: showCode gets the page and code to enter there, then signedIn your GitHub name once it's done;
    // failed if it can't start.
    public void signIn(BiConsumer<String, String> showCode, Consumer<String> signedIn, Runnable failed) {
        run(() -> {
            JsonObject code = form("device/code", "scope=gist");
            showCode.accept(code.get("verification_uri").getAsString(), code.get("user_code").getAsString());
            poll(code.get("device_code").getAsString(), code.get("interval").getAsInt(), signedIn);
        }, failed);
    }

    // Asks every interval seconds (more when told to slow down) until it's allowed, denied or expired.
    private void poll(String device, int interval, Consumer<String> signedIn) {
        executor.schedule(() -> run(() -> {
            JsonObject reply = form("oauth/access_token", "device_code=" + device + "&grant_type=urn:ietf:params:oauth:grant-type:device_code");
            String error = reply.has("error") ? reply.get("error").getAsString() : null;
            if ("authorization_pending".equals(error) || "slow_down".equals(error)) {
                poll(device, interval + ("slow_down".equals(error) ? 5 : 0), signedIn);
            } else if (error == null) {
                token = reply.get("access_token").getAsString();
                login = api("GET", "user", null).getAsJsonObject().get("login").getAsString();
                tokenFile.getParent().createDirectories();
                tokenFile.write(login + "\n" + token);
                signedIn.accept(login);
            }
        }, null), interval, TimeUnit.SECONDS);
    }

    public void signOut() {
        login = null;
        token = null;
        try {
            tokenFile.deleteIfExists();
        } catch (IOException e) {
            log.warn("Couldn't delete the GitHub sign-in", e);
        }
    }

    // A gist's first set file, as a Gist; null if it has none.
    @Nullable
    private static MarkerSet.Gist gist(JsonObject gist) {
        for (String file : gist.getAsJsonObject("files").keySet()) {
            if (file.endsWith(".gmv.json")) {
                return new MarkerSet.Gist(gist.getAsJsonObject("owner").get("login").getAsString(), gist.get("id").getAsString(), file, 0,
                    false, !gist.get("public").getAsBoolean(), 0, null);
            }
        }
        return null;
    }

    private JsonObject form(String path, String fields) throws IOException {
        return gson.fromJson(call(new Request.Builder().url("https://github.com/login/" + path).header("Accept", "application/json")
            .post(RequestBody.create(MediaType.get("application/x-www-form-urlencoded"), "client_id=" + CLIENT_ID + "&" + fields))),
            JsonObject.class);
    }

    @Nullable
    private JsonElement api(String method, String path, @Nullable JsonObject body) throws IOException {
        Request.Builder request = new Request.Builder().url(API + path).method(method, body == null ? null : RequestBody.create(MediaType.get("application/json"), body.toString()));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return gson.fromJson(call(request), JsonElement.class);
    }

    // The response's text, checked fresh (not from RuneLite's cache); null for 404. Throws for any other failure.
    @Nullable
    private String call(Request.Builder request) throws IOException {
        try (Response response = okHttpClient.newCall(request.header("Cache-Control", "no-cache").build()).execute()) {
            if (response.code() == 404) {
                return null;
            }
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }
}
