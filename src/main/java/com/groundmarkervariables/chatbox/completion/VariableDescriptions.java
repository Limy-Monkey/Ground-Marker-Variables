package com.groundmarkervariables.chatbox.completion;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

// A variable's one-line description for its suggestion tooltip, from variable-descriptions.json, keyed by its
// completable name (as LabelCompleter lists it, e.g. "lvl_").
@Slf4j
final class VariableDescriptions
{
	// Loaded on first use.
	private static final class Descriptions
	{
		private static final Map<String, String> BY_NAME = load();
	}

	private VariableDescriptions()
	{
	}

	@Nullable
	static String of(String name)
	{
		return Descriptions.BY_NAME.get(name);
	}

	private static Map<String, String> load()
	{
		Map<String, String> descriptions = new HashMap<>();
		try (Reader reader = new InputStreamReader(VariableDescriptions.class.getResourceAsStream("variable-descriptions.json"), StandardCharsets.UTF_8))
		{
			for (Map.Entry<String, JsonElement> entry : new JsonParser().parse(reader).getAsJsonObject().entrySet())
			{
				descriptions.put(entry.getKey(), entry.getValue().getAsString());
			}
		}
		catch (Exception e)
		{
			log.warn("Couldn't load variable descriptions", e);
		}
		return descriptions;
	}
}
