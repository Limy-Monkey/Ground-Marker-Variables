package com.groundmarkervariables.variables.support;

import javax.annotation.Nullable;

// Overlay icon tags for a variable's "icon" option. Off icons are drawn darkened, and unsure ones
// (null, e.g. a party member's spellbook isn't known) less so.
public final class Icons
{
	private static final int OFF_BRIGHTNESS = 25;
	private static final int UNSURE_BRIGHTNESS = 75;

	private Icons()
	{
	}

	public static String sprite(int spriteId)
	{
		return "<sprite=" + spriteId + ">";
	}

	public static String sprite(int spriteId, @Nullable Boolean on)
	{
		return Boolean.TRUE.equals(on) ? sprite(spriteId) : "<sprite=" + spriteId + "@" + brightness(on) + "%>";
	}

	public static String item(int itemId)
	{
		return "<item=" + itemId + ">";
	}

	public static String item(int itemId, @Nullable Boolean on)
	{
		return Boolean.TRUE.equals(on) ? item(itemId) : "<item=" + itemId + "@" + brightness(on) + "%>";
	}

	private static int brightness(@Nullable Boolean on)
	{
		return on == null ? UNSURE_BRIGHTNESS : OFF_BRIGHTNESS;
	}
}
