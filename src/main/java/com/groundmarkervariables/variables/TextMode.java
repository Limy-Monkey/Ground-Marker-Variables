package com.groundmarkervariables.variables;

// Plain (today's concise output) vs Rich (see RichText) — LabelResolver picks one per
// occurrence from the Rich Text config default and an optional ^/&/* prefix; * inverts the
// default. A conditional's <expr> is always Plain regardless — see ConditionalVariable.
enum TextMode
{
	PLAIN, RICH;

	static TextMode forPrefix(Character prefixChar, boolean richByDefault)
	{
		if (prefixChar == null)
		{
			return richByDefault ? RICH : PLAIN;
		}

		switch (prefixChar)
		{
			case '^':
				return PLAIN;
			case '&':
				return RICH;
			case '*':
				return richByDefault ? PLAIN : RICH;
			default:
				return richByDefault ? RICH : PLAIN;
		}
	}
}
