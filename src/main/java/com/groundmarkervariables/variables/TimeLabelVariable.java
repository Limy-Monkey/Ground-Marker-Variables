package com.groundmarkervariables.variables;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// {time} — current local (system clock) time, 12-hour h:mm with am/pm. {time24} is the
// same clock in 24-hour HH:mm instead.
class TimeLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{time(24)?\\}", Pattern.CASE_INSENSITIVE);
	private static final DateTimeFormatter FORMAT_12 = DateTimeFormatter.ofPattern("h:mm");
	private static final DateTimeFormatter FORMAT_24 = DateTimeFormatter.ofPattern("HH:mm");

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolve(Matcher matcher)
	{
		LocalTime now = LocalTime.now();
		if (matcher.group(1) != null)
		{
			return now.format(FORMAT_24);
		}

		return now.format(FORMAT_12) + (now.getHour() < 12 ? " am" : " pm");
	}
}
