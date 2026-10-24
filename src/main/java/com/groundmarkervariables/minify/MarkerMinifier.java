package com.groundmarkervariables.minify;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.groundmarkervariables.GroundMarkerPointData;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

// Exports markers as core's JSON, but a label with variables becomes "gmv":"<version>~<compressed>".
// Core ignores that field, so it still imports the tile, without the label.
public class MarkerMinifier
{
	private static final String FIELD = "gmv";
	private static final Pattern GMV = Pattern.compile("(\\d+)~(.+)");

	private final Gson gson;

	@Inject
	private MarkerMinifier(Gson gson)
	{
		this.gson = gson;
	}

	public String export(List<GroundMarkerPointData> points)
	{
		JsonArray array = gson.toJsonTree(points).getAsJsonArray();
		for (int i = 0; i < points.size(); i++)
		{
			String label = points.get(i).getLabel();
			if (label != null && label.contains("{"))
			{
				JsonObject marker = array.get(i).getAsJsonObject();
				marker.remove("label");
				marker.addProperty(FIELD, MarkerCompression.VERSION + "~" + GmvAlphabet.encode(MarkerCompression.compress(label)));
			}
		}
		return gson.toJson(array);
	}

	// Throws JsonSyntaxException if text isn't an array of markers, IllegalArgumentException if a gmv
	// label is damaged.
	public List<GroundMarkerPointData> parse(String text) throws NewerFormatException
	{
		JsonElement json = gson.fromJson(text, JsonElement.class);
		if (json == null || json.isJsonNull())
		{
			return List.of();
		}
		if (!json.isJsonArray())
		{
			throw new JsonSyntaxException("Not an array of markers");
		}

		List<GroundMarkerPointData> points = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray())
		{
			GroundMarkerPointData point = gson.fromJson(element, GroundMarkerPointData.class);
			if (point == null)
			{
				continue;
			}

			JsonElement gmv = element.getAsJsonObject().get(FIELD);
			if (gmv != null && gmv.isJsonPrimitive())
			{
				point = new GroundMarkerPointData(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ(),
					point.getColor(), label(gmv.getAsString()));
			}
			points.add(point);
		}
		return points;
	}

	private static String label(String gmv) throws NewerFormatException
	{
		Matcher matcher = GMV.matcher(gmv);
		if (!matcher.matches())
		{
			throw new IllegalArgumentException("Damaged gmv label");
		}
		return MarkerCompression.decompress(GmvAlphabet.decode(matcher.group(2)), Integer.parseInt(matcher.group(1)));
	}
}
