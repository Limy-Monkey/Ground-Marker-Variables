package com.groundmarkervariables.variables.label;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Icons;
import net.runelite.api.Client;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;

// {quickPrayers} -> the sprites of the quick prayers set, in prayer book order. {quickPrayers <name>}
// -> true if a set quick prayer's name contains <name>; Rich Text is its sprite, dimmed if not set.
// Standard prayer book only.
public class QuickPrayersLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{quickPrayers(?:\\s+([^{}?:<>=!,|&]+?))?\\s*\\}", Pattern.CASE_INSENSITIVE);

	// Prayer book order. Bits of QUICKPRAYER_SELECTED from Quick Prayer Preview (Hydrox6, BSD-2-Clause).
	private enum Prayer
	{
		THICK_SKIN(0, "Thick Skin", SpriteID.Prayeron.THICK_SKIN),
		BURST_OF_STRENGTH(1, "Burst of Strength", SpriteID.Prayeron.BURST_OF_STRENGTH),
		CLARITY_OF_THOUGHT(2, "Clarity of Thought", SpriteID.Prayeron.CLARITY_OF_THOUGHT),
		SHARP_EYE(18, "Sharp Eye", SpriteID.Prayeron.SHARP_EYE),
		MYSTIC_WILL(19, "Mystic Will", SpriteID.Prayeron.MYSTIC_WILL),
		ROCK_SKIN(3, "Rock Skin", SpriteID.Prayeron.ROCK_SKIN),
		SUPERHUMAN_STRENGTH(4, "Superhuman Strength", SpriteID.Prayeron.SUPERHUMAN_STRENGTH),
		IMPROVED_REFLEXES(5, "Improved Reflexes", SpriteID.Prayeron.IMPROVED_REFLEXES),
		RAPID_RESTORE(6, "Rapid Restore", SpriteID.Prayeron.RAPID_RESTORE),
		RAPID_HEAL(7, "Rapid Heal", SpriteID.Prayeron.RAPID_HEAL),
		PROTECT_ITEM(8, "Protect Item", SpriteID.Prayeron.PROTECT_ITEM),
		HAWK_EYE(20, "Hawk Eye", SpriteID.Prayeron.HAWK_EYE),
		MYSTIC_LORE(21, "Mystic Lore", SpriteID.Prayeron.MYSTIC_LORE),
		STEEL_SKIN(9, "Steel Skin", SpriteID.Prayeron.STEEL_SKIN),
		ULTIMATE_STRENGTH(10, "Ultimate Strength", SpriteID.Prayeron.ULTIMATE_STRENGTH),
		INCREDIBLE_REFLEXES(11, "Incredible Reflexes", SpriteID.Prayeron.INCREDIBLE_REFLEXES),
		PROTECT_FROM_MAGIC(12, "Protect from Magic", SpriteID.Prayeron.PROTECT_FROM_MAGIC),
		PROTECT_FROM_MISSILES(13, "Protect from Missiles", SpriteID.Prayeron.PROTECT_FROM_MISSILES),
		PROTECT_FROM_MELEE(14, "Protect from Melee", SpriteID.Prayeron.PROTECT_FROM_MELEE),
		EAGLE_EYE(22, "Eagle Eye", SpriteID.Prayeron.EAGLE_EYE),
		DEADEYE(22, "Deadeye", SpriteID.Prayeron.DEADEYE),
		MYSTIC_MIGHT(23, "Mystic Might", SpriteID.Prayeron.MYSTIC_MIGHT),
		MYSTIC_VIGOUR(23, "Mystic Vigour", SpriteID.Prayeron.MYSTIC_VIGOUR),
		RETRIBUTION(15, "Retribution", SpriteID.Prayeron.RETRIBUTION),
		REDEMPTION(16, "Redemption", SpriteID.Prayeron.REDEMPTION),
		SMITE(17, "Smite", SpriteID.Prayeron.SMITE),
		PRESERVE(28, "Preserve", SpriteID.Prayeron.PRESERVE),
		CHIVALRY(25, "Chivalry", SpriteID.Prayeron.CHIVALRY),
		PIETY(26, "Piety", SpriteID.Prayeron.PIETY),
		ZEAL(26, "Zeal", SpriteID.Prayeron.ZEAL),
		RIGOUR(24, "Rigour", SpriteID.Prayeron.RIGOUR),
		AUGURY(27, "Augury", SpriteID.Prayeron.AUGURY);

		private final int bit;
		private final String name;
		private final int spriteId;

		Prayer(int bit, String name, int spriteId)
		{
			this.bit = bit;
			this.name = name;
			this.spriteId = spriteId;
		}
	}

	private final Client client;
	private final RichText richText;

	@Inject
	private QuickPrayersLabelVariable(Client client, RichText richText)
	{
		this.client = client;
		this.richText = richText;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		if (matcher.group(1) == null)
		{
			return sprites(selected());
		}

		Prayer prayer = find(matcher.group(1));
		return prayer == null ? null : String.valueOf(selected().contains(asked(prayer)));
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		if (matcher.group(1) == null)
		{
			List<Prayer> selected = selected();
			return "Quick Prayers: " + (selected.isEmpty() ? richText.highlightValue("None") : sprites(selected));
		}

		Prayer prayer = find(matcher.group(1));
		if (prayer == null)
		{
			return null;
		}

		prayer = asked(prayer);
		return Icons.sprite(prayer.spriteId, selected().contains(prayer));
	}

	// The prayer to check for a named one: Eagle Eye / Mystic Might / Piety mean their replacement once it's
	// unlocked; Deadeye / Mystic Vigour / Zeal only ever mean themselves, never set while locked.
	private Prayer asked(Prayer prayer)
	{
		return prayer == Prayer.DEADEYE || prayer == Prayer.MYSTIC_VIGOUR || prayer == Prayer.ZEAL ? prayer : current(prayer);
	}

	// The set quick prayers.
	private List<Prayer> selected()
	{
		int bits = client.getVarbitValue(VarbitID.QUICKPRAYER_SELECTED);
		List<Prayer> selected = new ArrayList<>();
		for (Prayer prayer : Prayer.values())
		{
			if ((bits & (1 << prayer.bit)) != 0 && current(prayer) == prayer)
			{
				selected.add(prayer);
			}
		}
		return selected;
	}

	// Deadeye / Mystic Vigour / Zeal replace Eagle Eye / Mystic Might / Piety, sharing their bit, once unlocked.
	private Prayer current(Prayer prayer)
	{
		switch (prayer)
		{
			case EAGLE_EYE:
			case DEADEYE:
				return client.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED) == 1 ? Prayer.DEADEYE : Prayer.EAGLE_EYE;
			case MYSTIC_MIGHT:
			case MYSTIC_VIGOUR:
				return client.getVarbitValue(VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED) == 1 ? Prayer.MYSTIC_VIGOUR : Prayer.MYSTIC_MIGHT;
			case PIETY:
			case ZEAL:
				return client.getVarbitValue(VarbitID.PRAYER_ZEAL_UNLOCKED) == 1 ? Prayer.ZEAL : Prayer.PIETY;
			default:
				return prayer;
		}
	}

	// The first prayer whose name contains name, ignoring case.
	private static Prayer find(String name)
	{
		String search = name.trim().toLowerCase(Locale.ROOT);
		for (Prayer prayer : Prayer.values())
		{
			if (prayer.name.toLowerCase(Locale.ROOT).contains(search))
			{
				return prayer;
			}
		}
		return null;
	}

	private static String sprites(List<Prayer> prayers)
	{
		return prayers.stream().map(prayer -> Icons.sprite(prayer.spriteId)).collect(Collectors.joining(" "));
	}
}
