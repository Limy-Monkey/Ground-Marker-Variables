package com.groundmarkervariables.variables.meta;

import java.awt.Color;
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
}
