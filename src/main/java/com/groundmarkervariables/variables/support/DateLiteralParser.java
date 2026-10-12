package com.groundmarkervariables.variables.support;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.chrono.Chronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Parses a bare date literal for MathVariable's date countdown -- "Oct 20"/"October 20",
// optionally with an explicit year, or slash/dash numeric ("10/20", "10-20-2026") using the
// system locale's own day/month order. No year specified always means the current year.
public final class DateLiteralParser
{
	private static final DateTimeFormatter[] MONTH_DAY_FORMATS = {
		caseInsensitive("MMM d"),
		caseInsensitive("MMMM d"),
	};
	private static final DateTimeFormatter[] MONTH_DAY_YEAR_FORMATS = {
		caseInsensitive("MMM d yyyy"),
		caseInsensitive("MMMM d yyyy"),
	};
	private static final Pattern NUMERIC_DATE = Pattern.compile("(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{4}))?");

	private DateLiteralParser()
	{
	}

	private static DateTimeFormatter caseInsensitive(String pattern)
	{
		return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH);
	}

	public static LocalDate parse(String text)
	{
		String trimmed = text.trim().replace(",", "");

		LocalDate date = parseWithFormats(trimmed, MONTH_DAY_YEAR_FORMATS);
		if (date != null)
		{
			return date;
		}

		date = parseMonthDay(trimmed);
		if (date != null)
		{
			return date;
		}

		return parseNumeric(trimmed);
	}

	private static LocalDate parseWithFormats(String text, DateTimeFormatter[] formats)
	{
		for (DateTimeFormatter format : formats)
		{
			try
			{
				return LocalDate.parse(text, format);
			}
			catch (DateTimeParseException e)
			{
				// Try the next format.
			}
		}
		return null;
	}

	// "MMM d"/"MMMM d" alone can't parse to a LocalDate (no year) -- read the fields back out
	// and combine with the current year instead.
	private static LocalDate parseMonthDay(String text)
	{
		for (DateTimeFormatter format : MONTH_DAY_FORMATS)
		{
			try
			{
				TemporalAccessor parsed = format.parse(text);
				return LocalDate.of(LocalDate.now().getYear(),
					parsed.get(ChronoField.MONTH_OF_YEAR), parsed.get(ChronoField.DAY_OF_MONTH));
			}
			catch (DateTimeException e)
			{
				// Try the next format.
			}
		}
		return null;
	}

	private static LocalDate parseNumeric(String text)
	{
		Matcher matcher = NUMERIC_DATE.matcher(text);
		if (!matcher.matches())
		{
			return null;
		}

		int first = Integer.parseInt(matcher.group(1));
		int second = Integer.parseInt(matcher.group(2));
		boolean dayFirst = isDayFirstLocale();
		int month = dayFirst ? second : first;
		int day = dayFirst ? first : second;
		int year = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : LocalDate.now().getYear();

		try
		{
			return LocalDate.of(year, month, day);
		}
		catch (DateTimeException e)
		{
			return null;
		}
	}

	private static boolean isDayFirstLocale()
	{
		Locale locale = Locale.getDefault();
		String pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
			FormatStyle.SHORT, null, Chronology.ofLocale(locale), locale);
		int dayIndex = pattern.indexOf('d');
		int monthIndex = pattern.indexOf('M');
		return dayIndex >= 0 && monthIndex >= 0 && dayIndex < monthIndex;
	}
}
