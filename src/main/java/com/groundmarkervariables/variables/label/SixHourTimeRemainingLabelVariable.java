package com.groundmarkervariables.variables.label;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import net.runelite.api.GameState;

// {6HourTimeRemaining} -> time left before OSRS force-logs-out a continuous session, tracked
// client-side via an Instant reset on login/hop -- no direct game signal exists for this.
// LOGGED_IN also fires on ordinary loading-line crossings (see IdleNotifierPlugin), so only
// reset when it was preceded by LOGGING_IN/HOPPING/CONNECTION_LOST.
@Singleton
public class SixHourTimeRemainingLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{6HourTimeRemaining\\}", Pattern.CASE_INSENSITIVE);
	private static final Duration SESSION_LIMIT = Duration.ofHours(6);
	private static final String LABEL = "Six hour log in";

	private final RichText richText;
	private volatile Instant loginInstant = Instant.now();
	private volatile boolean ready;

	@Inject
	private SixHourTimeRemainingLabelVariable(RichText richText)
	{
		this.richText = richText;
	}

	public void onGameStateChanged(GameState state)
	{
		switch (state)
		{
			case LOGGING_IN:
			case HOPPING:
			case CONNECTION_LOST:
				ready = true;
				break;
			case LOGGED_IN:
				if (ready)
				{
					loginInstant = Instant.now();
					ready = false;
				}
				break;
			default:
				break;
		}
	}

	public Duration remaining()
	{
		Duration remaining = SESSION_LIMIT.minus(Duration.between(loginInstant, Instant.now()));
		return remaining.isNegative() ? Duration.ZERO : remaining;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		return formatPlain();
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return LABEL + ": " + richText.highlightValue(formatRich());
	}

	// Used by BooleanVariable's warning special case.
	String richTextWithColor(Color color)
	{
		return LABEL + ": " + RichText.colored(color, formatRich());
	}

	// Always h:mm, e.g. "1:45".
	String formatPlain()
	{
		Duration remaining = remaining();
		return remaining.toHours() + ":" + String.format("%02d", remaining.toMinutesPart());
	}

	// h:mm at an hour or more remaining, mm:ss under an hour.
	private String formatRich()
	{
		Duration remaining = remaining();
		if (remaining.toHours() >= 1)
		{
			return remaining.toHours() + ":" + String.format("%02d", remaining.toMinutesPart());
		}

		return remaining.toMinutesPart() + ":" + String.format("%02d", remaining.toSecondsPart());
	}
}
