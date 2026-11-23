package com.groundmarkervariables.variables.support;

import java.io.IOException;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

// The latest region-names.json and boss-aliases.json from GitHub's master, fetched once at startup in the background; the
// bundled copies are used until (and unless) they arrive.
@Slf4j
@Singleton
public class LatestData
{
	private static final String BASE_URL =
		"https://raw.githubusercontent.com/Limy-Monkey/Ground-Marker-Variables/master/src/main/resources/com/groundmarkervariables/variables/support/";

	private final OkHttpClient okHttpClient;

	@Inject
	private LatestData(OkHttpClient okHttpClient)
	{
		this.okHttpClient = okHttpClient;
	}

	public void startUp()
	{
		fetch("region-names.json", RegionNames::update);
		fetch("boss-aliases.json", BossAliases::update);
	}

	private void fetch(String file, Consumer<String> update)
	{
		okHttpClient.newCall(new Request.Builder().url(BASE_URL + file).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Couldn't fetch the latest {}", file, e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (response)
				{
					ResponseBody body = response.body();
					if (response.isSuccessful() && body != null)
					{
						update.accept(body.string());
					}
				}
			}
		});
	}
}
