package com.groundmarkervariables.variables.support;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

// Boss NPC id -> name with its fight stage, e.g. "Phantom Muspah (Ranged)", for {dps bossReport}.
// Downloaded once from this plugin's GitHub repo; data/bosses.json is made by scripts/build-bosses.py.
@Slf4j
@Singleton
public class BossNames
{
	private static final HttpUrl URL = HttpUrl.get("https://raw.githubusercontent.com/Limy-Monkey/Ground-Marker-Variables/master/data/bosses.json");
	private static final Type TYPE = new TypeToken<Map<Integer, Boss>>()
	{
	}.getType();

	private static final class Boss
	{
		private String name;
	}

	private final OkHttpClient okHttpClient;
	private final Gson gson;

	private volatile Map<Integer, Boss> bosses = Map.of();
	private volatile boolean requested;

	@Inject
	private BossNames(OkHttpClient okHttpClient, Gson gson)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
	}

	// Once, unless it failed: then again on the next call.
	public void load()
	{
		if (requested)
		{
			return;
		}
		requested = true;

		okHttpClient.newCall(new Request.Builder().url(URL).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Couldn't download boss names", e);
				requested = false;
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						log.warn("Couldn't download boss names: HTTP {}", response.code());
						requested = false;
						return;
					}

					Map<Integer, Boss> loaded = gson.fromJson(body.charStream(), TYPE);
					bosses = loaded == null ? Map.of() : loaded;
				}
				catch (JsonParseException e)
				{
					log.warn("Couldn't read boss names", e);
				}
			}
		});
	}

	// Null if it isn't a boss, or the list hasn't downloaded.
	@Nullable
	public String name(int npcId)
	{
		Boss boss = bosses.get(npcId);
		return boss == null ? null : boss.name;
	}
}
