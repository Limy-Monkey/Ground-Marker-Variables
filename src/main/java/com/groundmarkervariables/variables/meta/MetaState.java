package com.groundmarkervariables.variables.meta;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;

// Render-affecting state collected from a label's meta variables (see MetaVariableResolver).
// A null field means "no override".
public class MetaState
{
	@Nullable
	private Color tileColor;
	@Nullable
	private Float tileOpacity;
	private final Map<HighlightCorner, Color> highlights = new EnumMap<>(HighlightCorner.class);

	@Nullable
	public Color getTileColor()
	{
		return tileColor;
	}

	public void setTileColor(Color tileColor)
	{
		this.tileColor = tileColor;
	}

	// 0 (transparent) to 1 (opaque) -- outline only, see {tileCol=50%}.
	@Nullable
	public Float getTileOpacity()
	{
		return tileOpacity;
	}

	public void setTileOpacity(Float tileOpacity)
	{
		this.tileOpacity = tileOpacity;
	}

	public Map<HighlightCorner, Color> getHighlights()
	{
		return highlights;
	}

	public void setHighlight(HighlightCorner corner, Color color)
	{
		highlights.put(corner, color);
	}
}
