package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

// {kc <boss>} -> the player's tracked kill count for <boss> (case-insensitive), or 0 if none
// has been recorded yet. <boss> may be a short alias (e.g. "cg") — see BossAliases — as well
// as the canonical name. See BossKillCountTracker for how kill counts are learned/stored.
class KcLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{kc\\s+(.+?)\\}", Pattern.CASE_INSENSITIVE);

	private final BossKillCountTracker tracker;

	@Inject
	private KcLabelVariable(BossKillCountTracker tracker)
	{
		this.tracker = tracker;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolve(Matcher matcher)
	{
		String boss = matcher.group(1).trim();
		if (boss.isEmpty())
		{
			return null;
		}

		Integer kc = tracker.getKc(BossAliases.resolve(boss));
		return String.valueOf(kc == null ? 0 : kc);
	}
}
