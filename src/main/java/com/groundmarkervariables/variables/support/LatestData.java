package com.groundmarkervariables.variables.support;

import java.io.IOException;
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

// The latest region-names.json, boss-aliases.json and sprite-names.json from GitHub's master, fetched once at startup in
// the background; the bundled copies are used until (and unless) they arrive.
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class LatestData {
    private static final String BASE_URL =
        "https://raw.githubusercontent.com/Limy-Monkey/Ground-Marker-Variables/master/src/main/resources/com/groundmarkervariables/variables/support/";

    private final OkHttpClient okHttpClient;
    private final IconNames iconNames;
    // The dev client (./gradlew run) keeps the bundled files, so local changes aren't replaced by GitHub's.
    @Inject
    @Named("developerMode")
    private boolean developerMode;

    public void startUp() {
        if (developerMode) {
            return;
        }
        fetch("region-names.json", RegionNames::update);
        fetch("boss-aliases.json", BossAliases::update);
        fetch("sprite-names.json", iconNames::updateSprites);
    }

    private void fetch(String file, Consumer<String> update) {
        okHttpClient.newCall(new Request.Builder().url(BASE_URL + file).build()).enqueue(new Callback() {
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
