package com.groundmarkervariables.graphics;

import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.annotation.Nullable;

// {split}'s icons drawn as one: each part centered on a shared square, cropped to its half (2 parts), 120 degree
// wedge (3) or quadrant (4).
public final class SplitIcons {
	public static final int MAX_PARTS = 4;
	// A 3-part split's wedge start angles: 12 to 8 o'clock, 4 to 12, 8 to 4.
	private static final float[] WEDGE_STARTS = {90, 330, 210};

	private SplitIcons() {
	}

	// Null parts are skipped; null if all are.
	@Nullable
	public static BufferedImage compose(List<BufferedImage> parts) {
		int size = 0;
		for (BufferedImage part : parts) {
			if (part != null) {
				size = Math.max(size, Math.max(part.getWidth(), part.getHeight()));
			}
		}
		if (size == 0) {
			return null;
		}

		BufferedImage split = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = split.createGraphics();
		int half = size / 2;
		for (int i = 0; i < parts.size(); i++) {
			BufferedImage part = parts.get(i);
			if (part == null) {
				continue;
			}

			if (parts.size() == 3) {
				// 120 degree wedges from the center: top-left, top-right, then bottom. Arc angles run counterclockwise from 3 o'clock.
				graphics.setClip(new Arc2D.Float(-size / 2f, -size / 2f, size * 2f, size * 2f, WEDGE_STARTS[i], 120, Arc2D.PIE));
			} else {
				boolean right = parts.size() == 2 ? i == 1 : i % 2 == 1;
				boolean bottom = parts.size() != 2 && i >= 2;
				int height = parts.size() == 2 ? size : bottom ? size - half : half;
				graphics.setClip(right ? half : 0, bottom ? half : 0, right ? size - half : half, height);
			}
			graphics.drawImage(part, (size - part.getWidth()) / 2, (size - part.getHeight()) / 2, null);
		}
		graphics.dispose();
		return split;
	}
}
