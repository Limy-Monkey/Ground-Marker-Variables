package com.groundmarkervariables.variables.support;

import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

// :name: emojis, using the files RuneLite's own Emojis plugin downloads -- nothing works until
// that plugin has been enabled at least once. Client thread only.
@Slf4j
@Singleton
public class Emojis
{
	private static final File EMOJI_DIR = new File(RuneLite.CACHE_DIR, "emojis");
	private static final Pattern EMOJI = Pattern.compile(":([a-z0-9_!().\\-ñ]+):");
	private static final long RETRY_MILLIS = 60_000;

	private final Gson gson;

	private Map<String, String> names = Collections.emptyMap();
	private long lastLoadAttempt = -RETRY_MILLIS;
	private final Map<String, Optional<BufferedImage>> images = new HashMap<>();

	@Inject
	private Emojis(Gson gson)
	{
		this.gson = gson;
	}

	// ":balloon:" -> "<emoji=1f388>", the overlay's own inline image tag. Unknown names are left as-is.
	public String expand(String label)
	{
		if (label == null || label.indexOf(':') < 0 || names().isEmpty())
		{
			return label;
		}

		Matcher matcher = EMOJI.matcher(label);
		StringBuilder sb = new StringBuilder();
		while (matcher.find())
		{
			String codepoint = names.get(matcher.group(1));
			matcher.appendReplacement(sb, Matcher.quoteReplacement(codepoint == null ? matcher.group() : "<emoji=" + codepoint + ">"));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	public Set<String> names()
	{
		if (names.isEmpty() && System.currentTimeMillis() - lastLoadAttempt >= RETRY_MILLIS)
		{
			lastLoadAttempt = System.currentTimeMillis();
			names = loadNames();
		}
		return names.keySet();
	}

	@Nullable
	public BufferedImage image(String codepoint)
	{
		return images.computeIfAbsent(codepoint, Emojis::loadImage).orElse(null);
	}

	private Map<String, String> loadNames()
	{
		File indexFile = new File(EMOJI_DIR, "index.json");
		if (!indexFile.exists())
		{
			return Collections.emptyMap();
		}

		try (Reader reader = new FileReader(indexFile))
		{
			Index index = gson.fromJson(reader, Index.class);
			return index == null || index.names == null ? Collections.emptyMap() : index.names;
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Unable to read emoji index", e);
			return Collections.emptyMap();
		}
	}

	private static Optional<BufferedImage> loadImage(String codepoint)
	{
		try (ZipFile zip = new ZipFile(new File(EMOJI_DIR, "assets.zip")))
		{
			ZipEntry entry = zip.getEntry(codepoint + ".png");
			if (entry == null)
			{
				return Optional.empty();
			}

			try (InputStream in = zip.getInputStream(entry))
			{
				synchronized (ImageIO.class)
				{
					return Optional.ofNullable(ImageIO.read(in));
				}
			}
		}
		catch (IOException e)
		{
			log.debug("Unable to read emoji {}", codepoint, e);
			return Optional.empty();
		}
	}

	// Matches the Emojis plugin's own index.json.
	private static final class Index
	{
		Map<String, String> names;
	}
}
