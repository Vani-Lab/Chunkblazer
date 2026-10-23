/*
 * Copyright (c) 2026, btwinnn
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
import java.awt.image.BufferedImage;

/**
 * The colours and materials of one {@link Theme}: what Skin paints windows, boxes, buttons
 * and bars with, the text colours the overlays use, and the side panel's (PanelStyle).
 * Old School's materials are the stone images in {@code ui/}; the other themes' are
 * small tiles made by the UI's Themes. Plain AWT only. ChunkBlazer itself only knows the
 * ChunkBlazer (original) palette; ChunkBlazer UI's Themes makes the rest and hands them to Skin.
 */
public final class ThemePalette
{
	public final Theme theme;

	// Windows and boxes: the material (a tile, or flat colour when null) and its darker header.
	public Color base;
	public Color baseDark;
	public BufferedImage tile;
	public BufferedImage tileDark;
	/** Corner radius of windows and boxes (0: square). */
	public int radius;
	/** Outer and inner frame lines, and the frame's width in pixels. */
	public Color frameOuter;
	public Color frameInner;
	public int frameWidth = 1;
	/** Gold corner flourishes (Royal) and a soft glow round the frame. */
	public boolean flourishes;
	public boolean glow;

	// Buttons, bevels, grooves and fields.
	public Color button;
	public Color buttonActive;
	public Color edge;
	public Color bevelLight;
	public Color bevelDark;
	public Color dividerDark;
	public Color dividerLight;
	public Color field;
	public Color barFill;

	// Text and highlights the overlays use.
	public Color title;
	public Color subtext;
	public Color label;
	public Color dark;
	public Color tracked;
	public Color sidebar;
	public Color itemSlot;
	public Color rowAlt;

	// The side panel.
	public Color panelBg;
	public Color panelSection;
	public Color panelBorder;
	public Color panelField;
	public Color panelAccent;
	public Color panelButton;

	public ThemePalette(Theme theme)
	{
		this.theme = theme;
	}

	/** btwinnn's original: flat dark brown, thin bronze lines, soft corners, orange. */
	public static ThemePalette original()
	{
		ThemePalette p = new ThemePalette(Theme.ORIGINAL);
			// btwinnn's original: flat dark brown, thin bronze lines, soft corners, orange.
			p.base = new Color(38, 33, 27, 242);
			p.baseDark = new Color(28, 24, 20, 250);
			p.radius = 7;
			p.frameOuter = new Color(110, 95, 65);
			p.button = new Color(48, 42, 34);
			p.buttonActive = new Color(70, 60, 45);
			p.edge = new Color(110, 95, 65);
			p.dividerDark = new Color(20, 18, 15);
			p.dividerLight = new Color(70, 60, 45);
			p.field = new Color(20, 18, 15);
			p.barFill = new Color(255, 140, 0);
			p.title = new Color(255, 152, 31);
			p.subtext = new Color(170, 160, 140);
			p.label = new Color(150, 140, 120);
			p.dark = new Color(20, 18, 15);
			p.tracked = new Color(255, 140, 0, 45);
			p.sidebar = new Color(0, 0, 0, 40);
			p.itemSlot = new Color(70, 60, 45);
			p.rowAlt = new Color(255, 255, 255, 10);
			// The side panel as it first was: RuneLite's own greys.
			p.panelBg = new Color(40, 40, 40);
			p.panelSection = new Color(30, 30, 30);
			p.panelBorder = new Color(77, 77, 77);
			p.panelField = new Color(30, 30, 30);
			p.panelAccent = new Color(255, 152, 0);
			p.panelButton = new Color(55, 55, 55);
		return p;
	}

	/** The palette ChunkBlazer UI sent for its theme (see its ThemePalette.toMap). */
	public static ThemePalette fromMap(java.util.Map<?, ?> m)
	{
		Theme theme;
		try
		{
			theme = Theme.valueOf(String.valueOf(m.get("theme")));
		}
		catch (IllegalArgumentException e)
		{
			theme = Theme.ORIGINAL;
		}
		ThemePalette p = new ThemePalette(theme);
		p.base = m.get("base") instanceof Color ? (Color) m.get("base") : null;
		p.baseDark = m.get("baseDark") instanceof Color ? (Color) m.get("baseDark") : null;
		p.tile = m.get("tile") instanceof BufferedImage ? (BufferedImage) m.get("tile") : null;
		p.tileDark = m.get("tileDark") instanceof BufferedImage ? (BufferedImage) m.get("tileDark") : null;
		p.radius = m.get("radius") instanceof Integer ? (Integer) m.get("radius") : p.radius;
		p.frameOuter = m.get("frameOuter") instanceof Color ? (Color) m.get("frameOuter") : null;
		p.frameInner = m.get("frameInner") instanceof Color ? (Color) m.get("frameInner") : null;
		p.frameWidth = m.get("frameWidth") instanceof Integer ? (Integer) m.get("frameWidth") : p.frameWidth;
		p.flourishes = m.get("flourishes") instanceof Boolean ? (Boolean) m.get("flourishes") : p.flourishes;
		p.glow = m.get("glow") instanceof Boolean ? (Boolean) m.get("glow") : p.glow;
		p.button = m.get("button") instanceof Color ? (Color) m.get("button") : null;
		p.buttonActive = m.get("buttonActive") instanceof Color ? (Color) m.get("buttonActive") : null;
		p.edge = m.get("edge") instanceof Color ? (Color) m.get("edge") : null;
		p.bevelLight = m.get("bevelLight") instanceof Color ? (Color) m.get("bevelLight") : null;
		p.bevelDark = m.get("bevelDark") instanceof Color ? (Color) m.get("bevelDark") : null;
		p.dividerDark = m.get("dividerDark") instanceof Color ? (Color) m.get("dividerDark") : null;
		p.dividerLight = m.get("dividerLight") instanceof Color ? (Color) m.get("dividerLight") : null;
		p.field = m.get("field") instanceof Color ? (Color) m.get("field") : null;
		p.barFill = m.get("barFill") instanceof Color ? (Color) m.get("barFill") : null;
		p.title = m.get("title") instanceof Color ? (Color) m.get("title") : null;
		p.subtext = m.get("subtext") instanceof Color ? (Color) m.get("subtext") : null;
		p.label = m.get("label") instanceof Color ? (Color) m.get("label") : null;
		p.dark = m.get("dark") instanceof Color ? (Color) m.get("dark") : null;
		p.tracked = m.get("tracked") instanceof Color ? (Color) m.get("tracked") : null;
		p.sidebar = m.get("sidebar") instanceof Color ? (Color) m.get("sidebar") : null;
		p.itemSlot = m.get("itemSlot") instanceof Color ? (Color) m.get("itemSlot") : null;
		p.rowAlt = m.get("rowAlt") instanceof Color ? (Color) m.get("rowAlt") : null;
		p.panelBg = m.get("panelBg") instanceof Color ? (Color) m.get("panelBg") : null;
		p.panelSection = m.get("panelSection") instanceof Color ? (Color) m.get("panelSection") : null;
		p.panelBorder = m.get("panelBorder") instanceof Color ? (Color) m.get("panelBorder") : null;
		p.panelField = m.get("panelField") instanceof Color ? (Color) m.get("panelField") : null;
		p.panelAccent = m.get("panelAccent") instanceof Color ? (Color) m.get("panelAccent") : null;
		p.panelButton = m.get("panelButton") instanceof Color ? (Color) m.get("panelButton") : null;
		return p;
	}
}
