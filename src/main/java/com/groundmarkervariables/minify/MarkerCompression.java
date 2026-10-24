package com.groundmarkervariables.minify;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

// Deflate primed with a versioned dictionary of our label syntax, for exports and party sharing.
public final class MarkerCompression
{
	public static final int VERSION = 1;
	// Version N uses entry N - 1. Never edit a dictionary; add a new version.
	private static final List<byte[]> DICTIONARIES = List.of(
		dictionary("dictionary-1.txt", "81eab1acbd9c88a475d263b11e4a851d09d61bcf1c0fafaa82fef37debfcbd44"));

	private MarkerCompression()
	{
	}

	// With the current VERSION.
	public static byte[] compress(String text)
	{
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
		deflater.setDictionary(DICTIONARIES.get(VERSION - 1));
		deflater.setInput(text.getBytes(StandardCharsets.UTF_8));
		deflater.finish();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		while (!deflater.finished())
		{
			out.write(buffer, 0, deflater.deflate(buffer));
		}
		deflater.end();
		return out.toByteArray();
	}

	// Throws IllegalArgumentException if data is damaged.
	public static String decompress(byte[] data, int version) throws NewerFormatException
	{
		if (version > DICTIONARIES.size())
		{
			throw new NewerFormatException();
		}
		if (version < 1)
		{
			throw new IllegalArgumentException("No version " + version);
		}

		Inflater inflater = new Inflater(true);
		inflater.setDictionary(DICTIONARIES.get(version - 1));
		inflater.setInput(data);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		try
		{
			while (!inflater.finished())
			{
				int count = inflater.inflate(buffer);
				if (count == 0 && inflater.needsInput())
				{
					throw new IllegalArgumentException("Truncated");
				}
				out.write(buffer, 0, count);
			}
			if (inflater.getRemaining() > 0)
			{
				throw new IllegalArgumentException("Trailing data");
			}
		}
		catch (DataFormatException e)
		{
			throw new IllegalArgumentException("Damaged", e);
		}
		finally
		{
			inflater.end();
		}
		return out.toString(StandardCharsets.UTF_8);
	}

	// Loads a dictionary-N.txt (format in its header) and checks it wasn't edited.
	private static byte[] dictionary(String file, String sha256)
	{
		try (InputStream in = MarkerCompression.class.getResourceAsStream(file))
		{
			if (in == null)
			{
				throw new IllegalStateException("Missing " + file);
			}

			StringBuilder dictionary = new StringBuilder();
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
			{
				line = line.strip();
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				// '·' at either end is a space.
				line = line.startsWith("·") ? " " + line.substring(1) : line;
				line = line.endsWith("·") ? line.substring(0, line.length() - 1) + " " : line;
				dictionary.append(line);
			}

			byte[] bytes = dictionary.toString().getBytes(StandardCharsets.UTF_8);
			String hash = sha256(bytes);
			if (!hash.equals(sha256))
			{
				throw new IllegalStateException(file + " was edited: its SHA-256 is " + hash);
			}
			return bytes;
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Couldn't load " + file, e);
		}
	}

	private static String sha256(byte[] bytes)
	{
		try
		{
			StringBuilder hex = new StringBuilder();
			for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
			{
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException(e);
		}
	}
}
