package com.groundmarkervariables.variables.support;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

// The latest region-names.json, boss-aliases.json, sprite-names.json and gmv-partners.json from GitHub's master, fetched in the
// background at startup and on Marker Sets' Check for updates; the bundled copies are used until (and unless) they arrive.
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class LatestData {
    private static final String BASE_URL =
        "https://raw.githubusercontent.com/Limy-Monkey/Ground-Marker-Variables/master/src/main/resources/com/groundmarkervariables/variables/support/";

    // Files kept here for others to read (see text()), by name.
    private static final Map<String, String> LATEST = new ConcurrentHashMap<>();

    private final OkHttpClient okHttpClient;
    private final IconNames iconNames;
    // The dev client (./gradlew run) keeps the bundled files, so local changes aren't replaced by GitHub's.
    @Inject
    @Named("developerMode")
    private boolean developerMode;

    public void update() {
        if (developerMode) {
            return;
        }
        fetch("region-names.json", RegionNames::update);
        fetch("boss-aliases.json", BossAliases::update);
        fetch("sprite-names.json", iconNames::updateSprites);
        fetch("gmv-partners.json", json -> LATEST.put("gmv-partners.json", json));
    }

    // file's latest text, else the bundled one's; "" if neither.
    public static String text(String file) {
        return LATEST.computeIfAbsent(file, bundled -> {
            try (InputStream in = LatestData.class.getResourceAsStream(bundled)) {
                return new String(in.readAllBytes());
            } catch (IOException | NullPointerException e) {
                return "";
            }
        });
    }

    // The time in the query gets past the raw host's 5-minute cache.
    private void fetch(String file, Consumer<String> update) {
        okHttpClient.newCall(new Request.Builder().url(BASE_URL + file + "?t=" + System.currentTimeMillis()).build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.debug("Couldn't fetch the latest {}", file, e);
            }

            // A damaged file is ignored.
            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    if (response.isSuccessful()) {
                        update.accept(response.body().string());
                    }
                } catch (Exception e) {
                    log.warn("Couldn't read the latest {}", file, e);
                }
            }
        });
    }
}
