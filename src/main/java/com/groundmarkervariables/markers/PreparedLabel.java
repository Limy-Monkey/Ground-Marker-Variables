package com.groundmarkervariables.markers;

import java.util.List;
import javax.annotation.Nullable;

// A marker label made ready to resolve (see LabelResolver#prepareMarker): its aliases expanded and its {ground}/tile
// meta guarded, with the tile meta tokens set aside. Depends only on the label, so a marker keeps it.
public final class PreparedLabel
{
	@Nullable
	private final String guarded;
	private final List<String> tileMeta;

	public PreparedLabel(@Nullable String guarded, List<String> tileMeta)
	{
		this.guarded = guarded;
		this.tileMeta = List.copyOf(tileMeta);
	}

	@Nullable
	public String getGuarded()
	{
		return guarded;
	}

	public List<String> getTileMeta()
	{
		return tileMeta;
	}
}
