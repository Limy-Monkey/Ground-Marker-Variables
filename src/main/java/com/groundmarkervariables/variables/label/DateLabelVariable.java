package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

// {date} -- today's date (midnight, no time component), e.g. "October 12th 2026". Usable in
// MathVariable for a countdown, e.g. {Oct 20 - date}.
public class DateLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{date\\}", Pattern.CASE_INSENSITIVE);
	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH);

	private final RichText richText;

	@Inject
	private DateLabelVariable(RichText richText)
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
		LocalDate today = LocalDate.now();
		return today.format(FORMAT) + ordinalSuffix(today.getDayOfMonth()) + " " + today.getYear();
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return richText.labeled("Date", resolvePlain(matcher));
	}

	// 11th/12th/13th are always "th"; otherwise it follows the last digit (1st, 2nd, 3rd, 4th...).
	private static String ordinalSuffix(int day)
	{
		if (day >= 11 && day <= 13)
		{
			return "th";
		}

		switch (day % 10)
		{
			case 1:
				return "st";
			case 2:
				return "nd";
			case 3:
				return "rd";
			default:
				return "th";
		}
	}
}
