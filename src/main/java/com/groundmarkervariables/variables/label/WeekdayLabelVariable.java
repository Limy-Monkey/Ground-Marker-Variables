package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

// {weekday} -- today as a number, 1 (Monday) through 7 (Sunday) -- matches DayOfWeek's own
// ISO-8601 numbering, so no custom mapping needed.
public class WeekdayLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{weekday\\}", Pattern.CASE_INSENSITIVE);

	private final RichText richText;

	@Inject
	private WeekdayLabelVariable(RichText richText)
	{
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
		return String.valueOf(LocalDate.now().getDayOfWeek().getValue());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return richText.labeled("Weekday", LocalDate.now().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
	}
}
