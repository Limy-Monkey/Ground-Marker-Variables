package com.groundmarkervariables.variables.support;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Graphics2D;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ImageUtil;

// :name: images from two sets, both used as :name:. emoji/: a bundled copy of RuneLite's emoji set
// (static.runelite.net/emoji), licensed CC BY 4.0: see LICENSE-GRAPHICS inside emoji/assets.zip. unicode/: unicode
// symbols that set lacks, rendered for this plugin from the DejaVu fonts as white glyphs (their shadow is added when
// drawn): see LICENSE-FONTS inside unicode/images.zip.
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class Emojis
{
	// ":name:" or ":name@color:", color a named color or 6-digit hex.
	private static final Pattern EMOJI = Pattern.compile(":([a-z0-9_!().\\-ñ]+)(?:@([a-z]+|[0-9a-fA-F]{6}))?:");
	private static final String RESOURCES = "/com/groundmarkervariables/";

	// One set's names (name -> codepoint) and images (codepoint -> PNG bytes, read from its zip on first use).
	private static final class ImageSet
	{
		private final Map<String, String> names;
		private final String zip;
		@Nullable
		private Map<String, byte[]> pngs;

		private ImageSet(Map<String, String> names, String zip)
		{
			this.names = names;
			this.zip = zip;
		}

		@Nullable
		private byte[] png(String codepoint)
		{
			if (pngs == null)
			{
				pngs = loadPngs(zip);
			}
			return pngs.get(codepoint);
		}
	}

	private final Gson gson;

	// Loaded on first use. Every set's names together.
	@Nullable
	private List<ImageSet> sets;
	@Nullable
	private Map<String, String> names;
	private final Map<String, Optional<BufferedImage>> images = new HashMap<>();

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

	// A unicode symbol over a black copy of it, 1/16 of its size down-right; null for an emoji.
	@Nullable
	public synchronized BufferedImage shadowed(String codepoint, @Nullable String tintHex)
	{
		BufferedImage image = image(codepoint, tintHex);
		if (image == null || sets().get(0).png(codepoint) != null)
		{
			return null;
		}
		return images.computeIfAbsent(codepoint + "@" + tintHex + "/shadowed", key -> Optional.of(withShadow(image))).orElse(null);
	}

	private static BufferedImage withShadow(BufferedImage image)
	{
		int offset = Math.max(1, image.getHeight() / 16);
		BufferedImage shadowed = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = shadowed.createGraphics();
		graphics.drawImage(ImageUtil.luminanceScale(image, 0f), offset, offset, null);
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();
		return shadowed;
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
			names = new HashMap<>();
			for (ImageSet set : sets())
			{
				names.putAll(set.names);
			}
		}
		return names;
	}

	// RuneLite's emoji names, plus emoji-toolkit shortnames (aliases.json) for the same emojis; then the unicode symbols.
	private synchronized List<ImageSet> sets()
	{
		if (sets == null)
		{
			Map<String, String> emoji = new HashMap<>(readNames("emoji/aliases.json", Index::aliases));
			emoji.putAll(readNames("emoji/index.json", Index::names));
			sets = List.of(new ImageSet(emoji, "emoji/assets.zip"), new ImageSet(readNames("unicode/names.json", Index::names), "unicode/images.zip"));
		}
		return sets;
	}

	private Map<String, String> readNames(String file, Function<Index, Map<String, String>> field)
	{
		try (InputStream in = Emojis.class.getResourceAsStream(RESOURCES + file);
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

	// From whichever set has the codepoint.
	private Optional<BufferedImage> decode(String codepoint)
	{
		for (ImageSet set : sets())
		{
			byte[] png = set.png(codepoint);
			if (png == null)
			{
				continue;
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
		return Optional.empty();
	}

	private static Map<String, byte[]> loadPngs(String zipFile)
	{
		Map<String, byte[]> pngs = new HashMap<>();
		try (ZipInputStream zip = new ZipInputStream(Emojis.class.getResourceAsStream(RESOURCES + zipFile)))
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
			log.warn("Unable to read the emoji images {}", zipFile, e);
		}
		return pngs;
	}

	// The names and aliases files: "names" (index.json, unicode/names.json) or "aliases" (aliases.json).
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
