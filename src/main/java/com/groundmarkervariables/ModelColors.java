package com.groundmarkervariables;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Texture;
import net.runelite.api.TextureProvider;

// A lit model's face colors, and its textures for drawing them scrolling. A textured face stores only brightness, so
// its color is the texture's average at that brightness. Client thread only.
@Singleton
class ModelColors
{
	private static final double BRIGHTNESS = 0.8;
	// A textured face's brightness at which its texture shows at full color.
	private static final double TEXTURE_FULL_BRIGHTNESS = 96;

	private final Client client;
	// Texture id -> average color as RGB.
	private final Map<Integer, Integer> textureColors = new HashMap<>();
	private final Map<Integer, Optional<TextureFill>> textureFills = new HashMap<>();

	@Inject
	private ModelColors(Client client)
	{
		this.client = client;
	}

	// A texture as a tiled paint, size px square. Scrolls speed px per game cycle: direction 1 up, 2 left, 3 down, 4 right.
	static final class TextureFill
	{
		final TexturePaint paint;
		final int size;
		final int direction;
		final int speed;

		private TextureFill(TexturePaint paint, int size, int direction, int speed)
		{
			this.paint = paint;
			this.size = size;
			this.direction = direction;
			this.speed = speed;
		}

		// Scrolled px at this game cycle.
		double offsetU(int cycle)
		{
			return direction == 2 ? -scrolled(cycle) : direction == 4 ? scrolled(cycle) : 0;
		}

		double offsetV(int cycle)
		{
			return direction == 1 ? -scrolled(cycle) : direction == 3 ? scrolled(cycle) : 0;
		}

		private double scrolled(int cycle)
		{
			return (long) speed * cycle % size;
		}
	}

	// Null if it can't be loaded (yet).
	@Nullable
	TextureFill textureFill(int textureId)
	{
		Optional<TextureFill> cached = textureFills.get(textureId);
		if (cached != null)
		{
			return cached.orElse(null);
		}

		TextureFill fill = loadTexture(textureId);
		// Not cached when missing, so it's retried.
		if (fill != null)
		{
			textureFills.put(textureId, Optional.of(fill));
		}
		return fill;
	}

	@Nullable
	private TextureFill loadTexture(int textureId)
	{
		TextureProvider provider = client.getTextureProvider();
		Texture[] textures = provider == null ? null : provider.getTextures();
		if (textures == null || textureId < 0 || textureId >= textures.length || textures[textureId] == null)
		{
			return null;
		}

		int[] pixels = provider.load(textureId);
		int size = pixels == null ? 0 : (int) Math.sqrt(pixels.length);
		if (size == 0 || size * size != pixels.length)
		{
			return null;
		}

		// A 0 pixel is a hole in the texture.
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		for (int i = 0; i < pixels.length; i++)
		{
			image.setRGB(i % size, i / size, pixels[i] == 0 ? 0 : 0xFF000000 | pixels[i]);
		}
		Texture texture = textures[textureId];
		return new TextureFill(new TexturePaint(image, new Rectangle(0, 0, size, size)), size,
			texture.getAnimationDirection(), texture.getAnimationSpeed());
	}

	// A textured face's brightness, 1 for full color (its c1..c3 hold brightness; c3 is -1 for flat).
	static double textureBrightness(int c1, int c2, int c3)
	{
		return (c3 == -1 ? c1 : (c1 + c2 + c3) / 3.0) / TEXTURE_FULL_BRIGHTNESS;
	}

	// Per face, null for faces the game doesn't draw.
	Color[] faceColors(Model model)
	{
		int faceCount = model.getFaceCount();
		int[] c1 = model.getFaceColors1(), c2 = model.getFaceColors2(), c3 = model.getFaceColors3();
		byte[] alphas = model.getFaceTransparencies();
		short[] textures = model.getFaceTextures();
		Color[] colors = new Color[faceCount];
		for (int f = 0; f < faceCount; f++)
		{
			if (c3[f] == -2 || (alphas != null && (alphas[f] & 0xFF) >= 254))
			{
				continue;
			}
			int alpha = alphas == null ? 255 : 255 - (alphas[f] & 0xFF);
			Integer texture = textures == null || textures[f] == -1 ? null : textureColor(textures[f]);
			colors[f] = texture != null ? textured(texture, c1[f], c2[f], c3[f], alpha) : colored(c1[f], c2[f], c3[f], alpha);
		}
		return colors;
	}

	// Three RGB corner colors per face, for Gouraud shading (equal for a flat face). Zero for textured and hidden faces.
	int[] cornerColors(Model model)
	{
		int faceCount = model.getFaceCount();
		int[] c1 = model.getFaceColors1(), c2 = model.getFaceColors2(), c3 = model.getFaceColors3();
		short[] textures = model.getFaceTextures();
		int[] corners = new int[faceCount * 3];
		for (int f = 0; f < faceCount; f++)
		{
			if (c3[f] == -2 || (textures != null && textures[f] != -1))
			{
				continue;
			}
			int rgb1 = hslToRgb(c1[f]);
			corners[f * 3] = rgb1;
			corners[f * 3 + 1] = c3[f] == -1 ? rgb1 : hslToRgb(c2[f]);
			corners[f * 3 + 2] = c3[f] == -1 ? rgb1 : hslToRgb(c3[f]);
		}
		return corners;
	}

	// Null without a texture provider.
	@Nullable
	private Integer textureColor(int textureId)
	{
		Integer cached = textureColors.get(textureId);
		if (cached == null)
		{
			TextureProvider provider = client.getTextureProvider();
			if (provider == null)
			{
				return null;
			}
			cached = hslToRgb(provider.getDefaultColor(textureId));
			textureColors.put(textureId, cached);
		}
		return cached;
	}

	private static Color textured(int rgb, int c1, int c2, int c3, int alpha)
	{
		double brightness = textureBrightness(c1, c2, c3);
		return new Color(channel(rgb >> 16, brightness), channel(rgb >> 8, brightness), channel(rgb, brightness), alpha);
	}

	private static int channel(int value, double brightness)
	{
		return (int) Math.min(255, (value & 0xFF) * brightness);
	}

	// The face's lit colors averaged (flat if c3 is -1).
	private static Color colored(int c1, int c2, int c3, int alpha)
	{
		int rgb1 = hslToRgb(c1);
		if (c3 == -1)
		{
			return new Color(rgb1 >> 16 & 0xFF, rgb1 >> 8 & 0xFF, rgb1 & 0xFF, alpha);
		}
		int rgb2 = hslToRgb(c2), rgb3 = hslToRgb(c3);
		return new Color(((rgb1 >> 16 & 0xFF) + (rgb2 >> 16 & 0xFF) + (rgb3 >> 16 & 0xFF)) / 3,
			((rgb1 >> 8 & 0xFF) + (rgb2 >> 8 & 0xFF) + (rgb3 >> 8 & 0xFF)) / 3,
			((rgb1 & 0xFF) + (rgb2 & 0xFF) + (rgb3 & 0xFF)) / 3, alpha);
	}

	// The game's packed HSL (6 hue, 3 saturation, 7 lightness bits) to RGB, as its palette builds it.
	private static int hslToRgb(int hsl)
	{
		double hue = (hsl >> 10 & 63) / 64.0 + 0.0078125;
		double saturation = (hsl >> 7 & 7) / 8.0 + 0.0625;
		double lightness = (hsl & 127) / 128.0;
		double r = lightness, g = lightness, b = lightness;
		if (saturation != 0)
		{
			double q = lightness < 0.5 ? lightness * (1 + saturation) : lightness + saturation - lightness * saturation;
			double p = 2 * lightness - q;
			r = hueToChannel(p, q, hue + 1 / 3.0);
			g = hueToChannel(p, q, hue);
			b = hueToChannel(p, q, hue - 1 / 3.0);
		}
		int ri = (int) (Math.pow(r, BRIGHTNESS) * 256), gi = (int) (Math.pow(g, BRIGHTNESS) * 256), bi = (int) (Math.pow(b, BRIGHTNESS) * 256);
		return Math.min(255, ri) << 16 | Math.min(255, gi) << 8 | Math.min(255, bi);
	}

	private static double hueToChannel(double p, double q, double t)
	{
		t = t < 0 ? t + 1 : t > 1 ? t - 1 : t;
		if (t < 1 / 6.0)
		{
			return p + (q - p) * 6 * t;
		}
		if (t < 0.5)
		{
			return q;
		}
		if (t < 2 / 3.0)
		{
			return p + (q - p) * (2 / 3.0 - t) * 6;
		}
		return p;
	}
}
