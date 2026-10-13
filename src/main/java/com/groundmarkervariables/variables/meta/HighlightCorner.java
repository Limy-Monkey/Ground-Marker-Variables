package com.groundmarkervariables.variables.meta;

// Ordinal order matches Perspective.getCanvasTilePoly()'s own vertex order (SW, NW, NE, SE) --
// see GroundMarkerVariablesOverlay's own highlight-drawing code, which indexes the poly by it.
public enum HighlightCorner
{
	SW, NW, NE, SE
}
