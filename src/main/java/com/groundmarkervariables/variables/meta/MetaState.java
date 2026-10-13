package com.groundmarkervariables.variables.meta;

import java.awt.Color;
import java.util.EnumMap;
import java.util.List;
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
	@Nullable
	private Float borderWidth;
	private final Map<HighlightCorner, Color> highlights = new EnumMap<>(HighlightCorner.class);
	private List<TextAlign> lineAlignments = List.of();

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

	// Overrides the Border width config for the outline and {highlight} brackets.
	@Nullable
	public Float getBorderWidth()
	{
		return borderWidth;
	}

	public void setBorderWidth(Float borderWidth)
	{
		this.borderWidth = borderWidth;
	}

	public Map<HighlightCorner, Color> getHighlights()
	{
		return highlights;
	}

	public void setHighlight(HighlightCorner corner, Color color)
	{
		highlights.put(corner, color);
	}

	// One entry per rendered line; a line past the end of this list uses TextAlign.CENTER.
	public List<TextAlign> getLineAlignments()
	{
		return lineAlignments;
	}

	void setLineAlignments(List<TextAlign> lineAlignments)
	{
		this.lineAlignments = lineAlignments;
	}
}
