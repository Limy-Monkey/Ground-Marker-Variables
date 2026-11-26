package com.groundmarkervariables.markers.minify;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.groundmarkervariables.markers.GroundMarkerPointData;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

// Exports markers as core's JSON, but a label with variables goes in "gmv" (core ignores that field, so it still imports
// the tile, without the label); reads those back, and older copies' compressed "gmv":"<version>~<compressed>" too.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MarkerMinifier {
    private static final String FIELD = "gmv";
    private static final Pattern GMV = Pattern.compile("(\\d+)~(.+)");
    // The characters JSON allows after a backslash.
    private static final String JSON_ESCAPES = "\"\\/bfnrtu";

    private final Gson gson;

    // Core's JSON, every label as readable text: "=", "<", ">", "&" and "'" as themselves rather than Gson's unicode
    // escapes, which JSON doesn't need. A label's backslash-n still has to be written with its backslash doubled.
    public String exportPlain(List<GroundMarkerPointData> points) {
        return gson.newBuilder().disableHtmlEscaping().create().toJson(points);
    }

    // exportPlain's UTF-8 in base64, e.g. for runelite.net's tile map link.
    public String exportPlainBase64(List<GroundMarkerPointData> points) {
        return Base64.getEncoder().encodeToString(exportPlain(points).getBytes(StandardCharsets.UTF_8));
    }

    // Every copy's text: as exportPlain, but a label with variables goes in "gmv" as written, so core imports the tile
    // unlabeled; a line per marker (see lines).
    public String exportGmvPlain(List<GroundMarkerPointData> points) {
        Gson plain = gson.newBuilder().disableHtmlEscaping().create();
        List<String> lines = new ArrayList<>();
        for (JsonElement element : plain.toJsonTree(points).getAsJsonArray()) {
            JsonObject marker = element.getAsJsonObject();
            JsonElement label = marker.get("label");
            if (label != null && label.getAsString().contains("{")) {
                marker.remove("label");
                marker.add(FIELD, label);
            }
            lines.add(plain.toJson(marker));
        }
        return lines(lines);
    }

    // A JSON array of items, each starting a line.
    public static String lines(List<String> items) {
        return items.isEmpty() ? "[]" : "[\n  " + String.join(",\n  ", items) + "\n]\n";
    }

    // Throws JsonSyntaxException if text isn't an array of markers, IllegalArgumentException if a gmv
    // label is damaged.
    public List<GroundMarkerPointData> parse(String text) throws NewerFormatException {
        JsonElement json = gson.fromJson(normalizeStrings(text), JsonElement.class);
        if (json == null || json.isJsonNull()) {
            return List.of();
        }
        if (!json.isJsonArray()) {
            throw new JsonSyntaxException("Not an array of markers");
        }

        List<GroundMarkerPointData> points = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray()) {
            GroundMarkerPointData point = gson.fromJson(element, GroundMarkerPointData.class);
            if (point == null) {
                continue;
            }

            JsonElement gmv = element.getAsJsonObject().get(FIELD);
            if (gmv != null && gmv.isJsonPrimitive()) {
                point = point.withLabel(label(gmv.getAsString()));
            }
            points.add(point);
        }
        return points;
    }

    // Inside strings: \n and \t escapes and real tabs become the label's \n and \t, a backslash that isn't
    // a JSON escape (e.g. \s) is kept, and a real line break and the indentation after it are dropped.
    public static String normalizeStrings(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!inString) {
                inString = c == '"';
                out.append(c);
            } else if (c == '\\' && i + 1 < text.length()) {
                char escaped = text.charAt(++i);
                out.append(JSON_ESCAPES.indexOf(escaped) < 0 || escaped == 'n' || escaped == 't' ? "\\\\" : "\\").append(escaped);
            } else if (c == '"') {
                inString = false;
                out.append(c);
            } else if (c == '\t') {
                out.append("\\\\t");
            } else if (c == '\n' || c == '\r') {
                while (i + 1 < text.length() && Character.isWhitespace(text.charAt(i + 1))) {
                    i++;
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    // A compressed label, or one written as itself (see exportGmvPlain), which a compressed one never is: it has a "{".
    private static String label(String gmv) throws NewerFormatException {
        if (gmv.contains("{")) {
            return gmv;
        }
        Matcher matcher = GMV.matcher(gmv);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Damaged gmv label");
        }
        return MarkerCompression.decompress(GmvAlphabet.decode(matcher.group(2)), Integer.parseInt(matcher.group(1)));
    }
}
