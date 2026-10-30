package com.groundmarkervariables.variables.support;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

// :name: emojis from a bundled copy of RuneLite's emoji set (static.runelite.net/emoji), licensed
// CC BY 4.0: see LICENSE-GRAPHICS inside emoji/assets.zip.
@Slf4j
@Singleton
public class Emojis
{
	// ":name:" or ":name@color:", color a named color or 6-digit hex.
	private static final Pattern EMOJI = Pattern.compile(":([a-z0-9_!().\\-ñ]+)(?:@([a-z]+|[0-9a-fA-F]{6}))?:");

	private final Gson gson;

	@Nullable
	private Map<String, String> names;
	// codepoint -> PNG bytes, read from assets.zip on first use.
	@Nullable
	private Map<String, byte[]> pngs;
	private final Map<String, Optional<BufferedImage>> images = new HashMap<>();

	@Inject
	private Emojis(Gson gson)
	{
		this.gson = gson;
	}

	// ":balloon:" -> "<emoji=1f388>", ":balloon@red:" -> "<emoji=1f388@ff0000>": the overlay's own inline
	// image tag. Unknown names or colors are left as-is.
	public String expand(String label)
	{
		if (label == null || label.indexOf(':') < 0)
		{
			return label;
		}

		Map<String, String> names = nameMap();
		Matcher matcher = EMOJI.matcher(label);
		StringBuilder sb = new StringBuilder();
		while (matcher.find())
		{
			String codepoint = names.get(matcher.group(1));
			String color = matcher.group(2) == null ? "" : colorHex(matcher.group(2));
			String tag = codepoint == null || color == null ? matcher.group() : "<emoji=" + codepoint + (color.isEmpty() ? "" : "@" + color) + ">";
			matcher.appendReplacement(sb, Matcher.quoteReplacement(tag));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	// Lowercase 6-digit hex, or null if it isn't a color.
	@Nullable
	private static String colorHex(String color)
	{
		String hex = color.length() == 6 && color.matches("[0-9a-fA-F]{6}") ? color : NamedColors.HEX_BY_NAME.get(color.toLowerCase(Locale.ROOT));
		return hex == null ? null : hex.toLowerCase(Locale.ROOT);
	}

	public Set<String> names()
	{
		return nameMap().keySet();
	}

	// tintHex: 6-digit hex to tint with, or null for the emoji's own colors.
	@Nullable
	public synchronized BufferedImage image(String codepoint, @Nullable String tintHex)
	{
		if (tintHex == null)
		{
			return images.computeIfAbsent(codepoint, this::decode).orElse(null);
		}
		BufferedImage image = image(codepoint, null);
		return image == null ? null : images.computeIfAbsent(codepoint + "@" + tintHex,
			key -> Optional.of(tint(image, new Color(Integer.parseInt(tintHex, 16))))).orElse(null);
	}

	// Keeps the emoji's shading: each pixel is color scaled by its brightness. Pixels at the 80th
	// percentile of brightness and above get the full color, so small bright details don't leave the
	// rest dim. Alpha is unchanged.
	private static BufferedImage tint(BufferedImage image, Color color)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		int[] brightness = new int[width * height];
		List<Integer> opaque = new ArrayList<>();
		for (int i = 0; i < brightness.length; i++)
		{
			int argb = image.getRGB(i % width, i / width);
			brightness[i] = (((argb >> 16) & 0xFF) * 299 + ((argb >> 8) & 0xFF) * 587 + (argb & 0xFF) * 114) / 1000;
			if (argb >>> 24 != 0)
			{
				opaque.add(brightness[i]);
			}
		}
		Collections.sort(opaque);
		int brightest = opaque.isEmpty() ? 1 : Math.max(1, opaque.get(opaque.size() * 4 / 5));

		BufferedImage tinted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int i = 0; i < brightness.length; i++)
		{
			int scale = Math.min(255, brightness[i] * 255 / brightest);
			tinted.setRGB(i % width, i / width, image.getRGB(i % width, i / width) & 0xFF000000
				| color.getRed() * scale / 255 << 16
				| color.getGreen() * scale / 255 << 8
				| color.getBlue() * scale / 255);
		}
		return tinted;
	}

	private synchronized Map<String, String> nameMap()
	{
		if (names == null)
		{
			names = loadNames();
		}
		return names;
	}

	// RuneLite's names, plus emoji-toolkit shortnames (aliases.json) for the same emojis.
	private Map<String, String> loadNames()
	{
		Map<String, String> names = new HashMap<>(readNames("aliases.json", Index::aliases));
		names.putAll(readNames("index.json", Index::names));
		return names;
	}

	private Map<String, String> readNames(String file, Function<Index, Map<String, String>> field)
	{
		try (InputStream in = Emojis.class.getResourceAsStream("/com/groundmarkervariables/emoji/" + file);
			Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			Index index = gson.fromJson(reader, Index.class);
			Map<String, String> names = index == null ? null : field.apply(index);
			return names == null ? Collections.emptyMap() : names;
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Unable to read emoji {}", file, e);
			return Collections.emptyMap();
		}
	}

	private Optional<BufferedImage> decode(String codepoint)
	{
		if (pngs == null)
		{
			pngs = loadPngs();
		}

		byte[] png = pngs.get(codepoint);
		if (png == null)
		{
			return Optional.empty();
		}
		try
		{
			synchronized (ImageIO.class)
			{
				return Optional.ofNullable(ImageIO.read(new ByteArrayInputStream(png)));
			}
		}
		catch (IOException e)
		{
			log.debug("Unable to read emoji {}", codepoint, e);
			return Optional.empty();
		}
	}

	private static Map<String, byte[]> loadPngs()
	{
		Map<String, byte[]> pngs = new HashMap<>();
		try (ZipInputStream zip = new ZipInputStream(Emojis.class.getResourceAsStream("/com/groundmarkervariables/emoji/assets.zip")))
		{
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null)
			{
				String name = entry.getName();
				if (name.endsWith(".png"))
				{
					pngs.put(name.substring(0, name.length() - ".png".length()), zip.readAllBytes());
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Unable to read the emoji images", e);
		}
		return pngs;
	}

	// index.json has names, aliases.json has aliases.
	private static final class Index
	{
		Map<String, String> names;
		Map<String, String> aliases;

		Map<String, String> names()
		{
			return names;
		}

		Map<String, String> aliases()
		{
			return aliases;
		}
	}
}
