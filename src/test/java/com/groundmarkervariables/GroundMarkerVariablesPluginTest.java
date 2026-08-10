package com.groundmarkervariables;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GroundMarkerVariablesPluginTest
{
	public static void main(String[] args) throws Exception
	{
		applyDefaultHardwareAcceleration();

		ExternalPluginManager.loadBuiltin(GroundMarkerVariablesPlugin.class);
		RuneLite.main(args);
	}

	/**
	 * Mirrors the per-OS default hardware acceleration mode RuneLite's own launcher
	 * applies (net.runelite.launcher.HardwareAccelerationMode.defaultMode). Java2D
	 * properties must be set before the graphics environment starts, so this has to run
	 * before anything else in main() touches AWT. Without it (e.g. launching this class
	 * directly, bypassing the real launcher, as IntelliJ's run configuration does),
	 * Java2D's own OpenGL pipeline can conflict with LWJGL's separate native OpenGL
	 * context used by GPU-based plugins (117hd, gpu/gpu-experimental), producing a black
	 * screen that never gets past the login screen.
	 */
	private static void applyDefaultHardwareAcceleration()
	{
		String osName = System.getProperty("os.name", "").toLowerCase();
		if (osName.contains("win"))
		{
			System.setProperty("sun.java2d.d3d", "true");
			// The opengl prop overrides the d3d prop, so explicitly disable it.
			System.setProperty("sun.java2d.opengl", "false");
		}
		else if (osName.contains("mac"))
		{
			System.setProperty("sun.java2d.opengl", "true");
		}
		else
		{
			System.setProperty("sun.java2d.opengl", "false");
		}
	}
}
