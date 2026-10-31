package com.groundmarkervariables.variables.support;

// Overlay icon tags for a variable's "icon" option. Off icons are drawn darkened.
public final class Icons
{
	private static final int OFF_BRIGHTNESS = 25;

	private Icons()
	{
	}

	public static String sprite(int spriteId)
	{
		return "<sprite=" + spriteId + ">";
	}

	public static String sprite(int spriteId, boolean on)
	{
		return on ? sprite(spriteId) : "<sprite=" + spriteId + "@" + OFF_BRIGHTNESS + "%>";
	}

	public static String item(int itemId)
	{
		return "<item=" + itemId + ">";
	}

	public static String item(int itemId, boolean on)
	{
		return on ? item(itemId) : "<item=" + itemId + "@" + OFF_BRIGHTNESS + "%>";
	}
}
