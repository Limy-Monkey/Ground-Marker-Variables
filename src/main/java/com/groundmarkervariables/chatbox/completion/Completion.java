package com.groundmarkervariables.chatbox.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

// What can complete the token at the cursor: every option, and the preferred one (the ghost text and Tab), if any.
public final class Completion
{
	// One completion: its list label (null for a bare closer), the text it inserts over the replace chars before the
	// cursor, its icon syntax, and its tooltip description.
	public static final class Option
	{
		@Nullable
		public final String label;
		public final String insert;
		public final int replace;
		@Nullable
		public final String icon;
		@Nullable
		public final String description;

		Option(@Nullable String label, String insert, int replace, @Nullable String icon)
		{
			this(label, insert, replace, icon, null);
		}

		Option(@Nullable String label, String insert, int replace, @Nullable String icon, @Nullable String description)
		{
			this.label = label;
			this.insert = insert;
			this.replace = replace;
			this.icon = icon;
			this.description = description;
		}
	}

	// A resolved icon: a sprite, an item's image, an NPC's, an emoji or a color swatch. id is the number, for an emoji
	// its hex codepoint, or for a color its RRGGBB.
	public static final class Icon
	{
		public enum Kind
		{
			SPRITE, ITEM, NPC, EMOJI, COLOR
		}

		public final Kind kind;
		public final String id;

		Icon(Kind kind, String id)
		{
			this.kind = kind;
			this.id = id;
		}
	}

	public final List<Option> options;
	// Index into options, or -1 for no ghost (e.g. nothing typed yet).
	public final int preferred;

	Completion(List<Option> options, int preferred)
	{
		this.options = options;
		this.preferred = preferred;
	}

	// Just a closer, e.g. "}" once a name is exactly typed.
	static Completion closer(String closer)
	{
		return new Completion(List.of(new Option(null, closer, 0, null)), 0);
	}

	// A single option, labeled.
	static Completion single(String label, String insert, @Nullable String icon)
	{
		return new Completion(List.of(new Option(label, insert, 0, icon)), 0);
	}

	// Each candidate (which starts with the typed partial) completing to the rest of itself, then append.
	static Completion of(List<String> candidates, int typedLength, String append, @Nullable String preferred)
	{
		List<Option> options = new ArrayList<>(candidates.size());
		for (String candidate : candidates)
		{
			options.add(new Option(candidate, candidate.substring(typedLength) + append, 0, null));
		}
		return new Completion(options, preferred == null ? -1 : candidates.indexOf(preferred));
	}

	// This, then names merely containing the typed partial, which replace it. Listed only, never preferred.
	Completion withContained(List<String> contained, int typedLength, String append)
	{
		List<Option> all = new ArrayList<>(options);
		for (String name : contained)
		{
			all.add(new Option(name, name + append, typedLength, null));
		}
		return new Completion(all, preferred);
	}

	// arguments' listed options (what can follow a space) first, each after a space, then this; still preferring the
	// same option. arguments only (no ghost) if this is null.
	static Completion withArguments(@Nullable Completion completion, Completion arguments)
	{
		List<Option> all = new ArrayList<>();
		for (Option option : arguments.options)
		{
			if (option.label != null)
			{
				all.add(new Option(" " + option.label, " " + option.insert, option.replace, option.icon, option.description));
			}
		}
		int shift = all.size();
		if (completion == null)
		{
			return new Completion(all, -1);
		}
		all.addAll(completion.options);
		return new Completion(all, completion.preferred < 0 ? -1 : completion.preferred + shift);
	}

	// This, each labeled option given icon(label) as its icon (null for none).
	Completion withIcons(Function<String, String> icon)
	{
		List<Option> withIcons = new ArrayList<>(options.size());
		for (Option option : options)
		{
			withIcons.add(option.label == null ? option
				: new Option(option.label, option.insert, option.replace, icon.apply(option.label), option.description));
		}
		return new Completion(withIcons, preferred);
	}

	// This, each labeled option given description(label) as its description (null for none).
	Completion withDescriptions(Function<String, String> description)
	{
		List<Option> described = new ArrayList<>(options.size());
		for (Option option : options)
		{
			described.add(option.label == null ? option
				: new Option(option.label, option.insert, option.replace, option.icon, description.apply(option.label)));
		}
		return new Completion(described, preferred);
	}

	// The ghost text / Tab insertion, or null.
	@Nullable
	public String preferredInsert()
	{
		return preferred < 0 ? null : options.get(preferred).insert;
	}

	// Whether the suggestion list has anything to show.
	public boolean hasListed()
	{
		return options.stream().anyMatch(option -> option.label != null);
	}
}
