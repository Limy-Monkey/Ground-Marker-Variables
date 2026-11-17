package com.groundmarkervariables.graphics;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.runelite.client.util.AsyncBufferedImage;

// Runs onLoad once an item image finishes loading. onLoaded() also fires for loaded images, so those are remembered.
public final class LoadingImages
{
	private static final Set<BufferedImage> LOADED = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

	private LoadingImages()
	{
	}

	public static void whenLoaded(BufferedImage image, Runnable onLoad)
	{
		if (image instanceof AsyncBufferedImage && !LOADED.contains(image))
		{
			((AsyncBufferedImage) image).onLoaded(() ->
			{
				LOADED.add(image);
				onLoad.run();
			});
		}
	}
}
