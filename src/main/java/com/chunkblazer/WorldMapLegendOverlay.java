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
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The chunk colour key in the bottom-left corner of the world map. Its own overlay on
 * the always-on-top layer, so map icons and other overlays never cover it. Uses the
 * same colours as the map (ChunkBlazerWorldMapOverlay.fillFor). Registered by
 * TaskBrowserOverlay. The button in its corner folds it down to a small "Legend" tab.
 */
@Singleton
public class WorldMapLegendOverlay extends Overlay
{
	private static final Color SWATCH_BORDER = new Color(255, 255, 255, 70);
	private static final ChunkUnlockType[] ROWS = {
		ChunkUnlockType.UNLOCKED, ChunkUnlockType.PAID, ChunkUnlockType.FREE,
		ChunkUnlockType.CHARTER, ChunkUnlockType.BOSS, ChunkUnlockType.LOCKED
	};

	private static final String MINIMIZED_KEY = "legendMinimized";

	private final Client client;
	private final ChunkBlazerConfig config;
	private final ChunkBlazerWorldMapOverlay worldMap;
	private final ConfigManager configManager;
	private volatile boolean minimized;
	// The fold button where it was last drawn (null when the legend isn't showing).
	private volatile Rectangle button;

	/** Clicking the fold button folds or unfolds the legend; the click doesn't reach the map. */
	final MouseAdapter mouse = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			Rectangle b = button;
			if (b != null && event.getButton() == MouseEvent.BUTTON1 && b.contains(event.getPoint()))
			{
				minimized = !minimized;
				configManager.setConfiguration("chunkblazer", MINIMIZED_KEY, minimized);
				event.consume();
			}
			return event;
		}
	};

	@Inject
	public WorldMapLegendOverlay(Client client, ChunkBlazerConfig config, ChunkBlazerWorldMapOverlay worldMap,
		ConfigManager configManager)
	{
		this.client = client;
		this.config = config;
		this.worldMap = worldMap;
		this.configManager = configManager;
		minimized = Boolean.parseBoolean(configManager.getConfiguration("chunkblazer", MINIMIZED_KEY));
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		setPriority(PRIORITY_HIGHEST);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		button = null;
		if (map == null || map.isHidden())
		{
			return null;
		}
		Rectangle worldMapRect = map.getBounds();
		if (worldMapRect == null || worldMapRect.width <= 0)
		{
			return null;
		}
		if (config.showWorldMapChunks() && config.showChunkLegend())
		{
			if (minimized)
			{
				drawFolded(graphics, worldMapRect);
			}
			else
			{
				drawLegend(graphics, worldMapRect);
			}
		}
		// The map's hover tooltip goes on top too, over the legend and map icons.
		worldMap.drawTooltip(graphics);
		return null;
	}

	private void drawLegend(Graphics2D graphics, Rectangle worldMapRect)
	{
		graphics.setFont(Skin.small());
		FontMetrics fm = graphics.getFontMetrics();

		int padding = 6;
		int swatch = 10;
		int lineHeight = Math.max(fm.getHeight(), swatch + 4);
		int textWidth = 0;
		for (ChunkUnlockType row : ROWS)
		{
			textWidth = Math.max(textWidth, fm.stringWidth(row.legend));
		}
		// Room on the right for the fold button.
		int width = padding * 4 + swatch + textWidth + 12;
		int height = padding * 2 + lineHeight * ROWS.length;
		int x = (int) worldMapRect.getX() + 8;
		int y = (int) (worldMapRect.getY() + worldMapRect.getHeight()) - height - 8;

		Skin.panel(graphics, new Rectangle(x - 2, y - 2, width + 5, height + 5), 0, null);
		drawButton(graphics, new Rectangle(x + width - 13, y + 2, 13, 13), false);

		int rowY = y + padding;
		for (ChunkUnlockType row : ROWS)
		{
			int sx = x + padding;
			int sy = rowY + (lineHeight - swatch) / 2;
			// The swatch is drawn over a map-like backing so it matches the tint
			// players see on the chunks.
			graphics.setColor(new Color(110, 110, 90));
			graphics.fillRect(sx, sy, swatch, swatch);
			graphics.setColor(ChunkBlazerWorldMapOverlay.fillFor(config, row));
			graphics.fillRect(sx, sy, swatch, swatch);
			graphics.setColor(SWATCH_BORDER);
			graphics.drawRect(sx, sy, swatch, swatch);

			graphics.setColor(new Color(255, 152, 31));
			Skin.text(graphics, row.legend, sx + swatch + padding, rowY + (lineHeight + fm.getAscent()) / 2 - 2);
			rowY += lineHeight;
		}
	}

	/** Folded: just a small "Legend" tab with a button to open it again. */
	private void drawFolded(Graphics2D graphics, Rectangle worldMapRect)
	{
		graphics.setFont(Skin.small());
		FontMetrics fm = graphics.getFontMetrics();
		int width = fm.stringWidth("Legend") + 31;
		int height = 19;
		int x = (int) worldMapRect.getX() + 8;
		int y = (int) (worldMapRect.getY() + worldMapRect.getHeight()) - height - 8;
		Skin.panel(graphics, new Rectangle(x - 2, y - 2, width + 5, height + 5), 0, null);
		Skin.text(graphics, "Legend", x + 6, y + (height + fm.getAscent()) / 2 - 1, new Color(255, 152, 31));
		drawButton(graphics, new Rectangle(x + width - 15, y + 3, 13, 13), true);
	}

	/** The fold button: a minus to fold, a plus to unfold. */
	private void drawButton(Graphics2D graphics, Rectangle r, boolean plus)
	{
		Point mouse = client.getMouseCanvasPosition();
		boolean hover = mouse != null && r.contains(mouse.getX(), mouse.getY());
		Skin.button(graphics, r, hover, false);
		graphics.setColor(hover ? Color.WHITE : new Color(255, 152, 31));
		int cx = r.x + r.width / 2;
		int cy = r.y + r.height / 2;
		graphics.fillRect(cx - 3, cy, 7, 1);
		if (plus)
		{
			graphics.fillRect(cx, cy - 3, 1, 7);
		}
		button = r;
	}
}
