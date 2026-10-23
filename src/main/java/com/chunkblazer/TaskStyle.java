/*
 * Copyright (c) 2026, Vani-Lab
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.chunkblazer;

import java.awt.Color;
import java.awt.Shape;

/**
 * The task lists' shared look (ChunkBlazer's task window, side panel and quest/saved box):
 * text and highlight colours (following the Theme: see Skin.setPalette), sizes, the arrow
 * images, and small drawing helpers. Used through {@code import static}.
 */
public final class TaskStyle
{
	private TaskStyle()
	{
	}

	public static final String CONFIG_GROUP = "chunkblazer";

	// The game's interface colours (Skin).
	public static Color TITLE = new Color(255, 152, 31);
	public static Color ROW_ALT = new Color(0, 0, 0, 30);
	public static Color SUBTEXT = new Color(255, 176, 64);
	public static Color DARK = new Color(28, 24, 19);
	public static Color TRACKED_FILL = new Color(255, 152, 31, 46);
	public static Color SIDEBAR_FILL = new Color(0, 0, 0, 44);
	// Behind every task icon: lighter than the stone, so dark items and icons show.
	public static Color ITEM_SLOT = new Color(92, 84, 70);
	public static Color FIELD = new Color(30, 26, 21);
	public static Color LABEL = new Color(170, 150, 112);

	/** A clickable area drawn this frame. Checked in order, so earlier wins. */
	public static final class Hit
	{
		public final Shape area;
		public final Runnable action;

		public Hit(Shape area, Runnable action)
		{
			this.area = area;
			this.action = action;
		}
	}

	/** The theme's colours for this window's text and highlights (see Skin.setPalette). */
	private static void retheme(ThemePalette p)
	{
		TITLE = p.title;
		SUBTEXT = p.subtext;
		LABEL = p.label;
		DARK = p.dark;
		FIELD = p.field;
		TRACKED_FILL = p.tracked;
		SIDEBAR_FILL = p.sidebar;
		ITEM_SLOT = p.itemSlot;
		ROW_ALT = p.rowAlt;
	}

	// --- Small drawing helpers ------------------------------------------------

	static
	{
		Skin.onRetheme(TaskStyle::retheme);
	}
}
