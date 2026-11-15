package com.groundmarkervariables.chatbox.completion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

// What the editor knows about each variable, from variables.json (the docs site's source too), keyed by its
// completable name (e.g. "lvl_"). An entry with variants (has<Spell>) stands for each of them.
@Slf4j
final class VariableCatalog
{
	// Suggested after "{": every entry whose syntax starts with "{", in the file's order.
	static final List<String> NAMES = new ArrayList<>();
	// Not complete without more typed, e.g. "kc", "lvl_".
	static final Set<String> REQUIRING_ARGUMENT = new LinkedHashSet<>();
	// Taking "icon" right after the name.
	static final Set<String> WITH_ICON = new LinkedHashSet<>();
	// Other names a variant is also written as, e.g. hasFreezes.
	static final Map<String, List<String>> ALIASES = new HashMap<>();
	private static final Map<String, String> DESCRIPTIONS = new HashMap<>();
	// Each entry's parameters' "complete" words, in order.
	private static final Map<String, List<String>> COMPLETIONS = new HashMap<>();
	// Each entry's parameters with nothing to complete (typed freely), as "name: description".
	private static final Map<String, List<String>> HINTS = new HashMap<>();
	// A parameter that's one word, optionally "=<value>" after it: the word (with its "=").
	private static final Pattern WORD_OPTION = Pattern.compile("([A-Za-z]+=?)(?:<[^>]*>)?");
	// Lowercase entry name -> lowercase completed word -> its parameter's description.
	private static final Map<String, Map<String, String>> COMPLETION_DESCRIPTIONS = new HashMap<>();

	static
	{
		try (Reader reader = new InputStreamReader(VariableCatalog.class.getResourceAsStream("variables.json"), StandardCharsets.UTF_8))
		{
			for (Map.Entry<String, JsonElement> entry : new JsonParser().parse(reader).getAsJsonObject().entrySet())
			{
				add(entry.getKey(), entry.getValue().getAsJsonObject());
			}
		}
		catch (Exception e)
		{
			log.warn("Couldn't load variables.json", e);
		}
	}

	private VariableCatalog()
	{
	}

	@Nullable
	static String description(String name)
	{
		return DESCRIPTIONS.get(name);
	}

	static List<String> completions(String name)
	{
		return COMPLETIONS.getOrDefault(name, Collections.emptyList());
	}

	// The description of the parameter word (a suggestion's label, e.g. " icon" or "itemsOnly") completes for
	// variable, any case; null if none.
	@Nullable
	static String completionDescription(String variable, String word)
	{
		Map<String, String> words = COMPLETION_DESCRIPTIONS.get(variable.trim().toLowerCase(Locale.ROOT));
		return words == null ? null : words.get(word.replaceFirst("^[\\s,]+", "").toLowerCase(Locale.ROOT));
	}

	// The free-typed parameters of the variable partial (what follows "{") is giving arguments to, e.g. "showOneItem "
	// or "loot Vorkath"; empty if none. Names ending "_" or "=" take theirs straight after.
	static List<String> hints(String partial)
	{
		String lower = partial.toLowerCase(Locale.ROOT);
		String best = null;
		for (String key : HINTS.keySet())
		{
			String name = key.toLowerCase(Locale.ROOT);
			boolean joined = name.endsWith("_") || name.endsWith("=");
			if ((joined ? lower.startsWith(name) : lower.startsWith(name + " ") || lower.startsWith(name + ","))
				&& (best == null || key.length() > best.length()))
			{
				best = key;
			}
		}
		return best == null ? Collections.emptyList() : HINTS.get(best);
	}

	private static void add(String key, JsonObject entry)
	{
		putDescription(key, entry);
		List<String> completions = new ArrayList<>();
		List<String> hints = new ArrayList<>();
		if (entry.has("parameters"))
		{
			for (JsonElement element : entry.getAsJsonArray("parameters"))
			{
				JsonObject parameter = element.getAsJsonObject();
				String name = parameter.get("name").getAsString();
				// Described when suggested: its "complete" words (a word, or {word, description} with its own), or a lone
				// word option, e.g. icon, hideWorld, weapon=.
				String description = parameter.has("description") ? parameter.get("description").getAsString() : null;
				Map<String, String> byWord = COMPLETION_DESCRIPTIONS.computeIfAbsent(key.toLowerCase(Locale.ROOT), k -> new HashMap<>());
				Matcher word = WORD_OPTION.matcher(name);
				if (parameter.has("complete"))
				{
					for (JsonElement option : parameter.getAsJsonArray("complete"))
					{
						JsonObject own = option.isJsonObject() ? option.getAsJsonObject() : null;
						String completed = own != null ? own.get("word").getAsString() : option.getAsString();
						String completedDescription = own != null && own.has("description") ? own.get("description").getAsString() : description;
						completions.add(completed);
						if (completedDescription != null)
						{
							byWord.put(completed.toLowerCase(Locale.ROOT), completedDescription);
						}
					}
					continue;
				}
				if (word.matches() && description != null)
				{
					byWord.put(word.group(1).toLowerCase(Locale.ROOT), description);
				}
				if (description != null && !name.equals("icon") && !name.equals("...") && !name.startsWith("{/"))
				{
					hints.add(name + ": " + description);
				}
			}
		}
		COMPLETIONS.put(key, completions);
		if (!hints.isEmpty())
		{
			HINTS.put(key, hints);
		}

		if (!entry.get("syntax").getAsString().startsWith("{"))
		{
			return;
		}
		boolean requiresArgument = entry.has("requiresArgument") && entry.get("requiresArgument").getAsBoolean();
		boolean icon = entry.has("icon") && "yes".equals(entry.get("icon").getAsString());
		List<String> names = new ArrayList<>();
		if (entry.has("variants"))
		{
			for (JsonElement element : entry.getAsJsonArray("variants"))
			{
				JsonObject variant = element.getAsJsonObject();
				String name = variant.get("name").getAsString();
				names.add(name);
				putDescription(name, variant);
				// Its parameters are the entry's.
				COMPLETION_DESCRIPTIONS.put(name.toLowerCase(Locale.ROOT), COMPLETION_DESCRIPTIONS.get(key.toLowerCase(Locale.ROOT)));
				if (variant.has("aliases"))
				{
					ALIASES.put(name, strings(variant.getAsJsonArray("aliases")));
				}
			}
		}
		else
		{
			names.add(key);
		}

		for (String name : names)
		{
			NAMES.add(name);
			if (requiresArgument)
			{
				REQUIRING_ARGUMENT.add(name);
			}
			else if (icon)
			{
				WITH_ICON.add(name);
			}
		}
	}

	private static void putDescription(String name, JsonObject entry)
	{
		if (entry.has("description"))
		{
			DESCRIPTIONS.put(name, entry.get("description").getAsString());
		}
	}

	private static List<String> strings(JsonArray array)
	{
		List<String> out = new ArrayList<>(array.size());
		array.forEach(element -> out.add(element.getAsString()));
		return out;
	}
}
