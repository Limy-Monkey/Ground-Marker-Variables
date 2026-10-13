package com.groundmarkervariables.variables.meta;

import java.util.List;
import javax.inject.Inject;

// Every registered MetaVariable -- add a new implementation here as it's built.
public class MetaVariableRegistry
{
	private final List<MetaVariable> variables;

	@Inject
	private MetaVariableRegistry(TileColMetaVariable tileCol, HighlightMetaVariable highlight, BorderWidthMetaVariable borderWidth)
	{
		this.variables = List.of(tileCol, highlight, borderWidth);
	}

	public List<MetaVariable> all()
	{
		return variables;
	}
}
