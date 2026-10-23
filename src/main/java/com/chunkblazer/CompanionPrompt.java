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
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.LinkBrowser;

/**
 * The tutorial's first step, and the only part of it in ChunkBlazer itself: a box saying
 * whether ChunkBlazer UI and ChunkBlazer GPU are installed and on. A plugin that's off can be
 * turned on from here; one that isn't installed opens its Plugin Hub page. It hides while
 * both are on, and goes for good on Skip. Nothing else in ChunkBlazer depends on it.
 *
 * <p>While it shows, "companionCheckPending" is true, so ChunkBlazer UI's tutorial waits.
 */
@Slf4j
@Singleton
class CompanionPrompt extends Overlay
{
	private static final String GROUP = "chunkblazer";
	// Set by Skip only: with both plugins on it just stays hidden, so it comes back if one's
	// turned off or removed later.
	private static final String DONE = "companionCheckSkipped";
	private static final String PENDING = "companionCheckPending";
	private static final String HUB = "https://runelite.net/plugin-hub/show/";
	// Tutorial Island's progress once the Gielinor Guide is done with you (the door opens).
	private static final int GUIDE_DONE = 10;

	/** One of the plugins it checks: its name as installed, what it adds, its Plugin Hub page. */
	private static final String[][] COMPANIONS = {
		{"ChunkBlazer UI", "Task window, task cards, themes and the tutorial", "chunkblazer-ui"},
		{"ChunkBlazer GPU", "Chunk walls, shading and the unlock sweep", "chunkblazer-gpu"},
	};

	// Old School's colours (as in ChunkBlazer UI) and the stone frame and bevels from ui/.
	private static final Color TITLE = new Color(255, 152, 31);
	private static final Color TEXT = new Color(255, 176, 64);
	private static final Color LABEL = new Color(170, 150, 112);
	private static final Color ON = new Color(80, 220, 80);
	private static final Color RED = new Color(255, 90, 70);
	private static final Color GROOVE_DARK = new Color(36, 30, 24);
	private static final Color GROOVE_LIGHT = new Color(112, 99, 79);
	private static final java.awt.image.BufferedImage FRAME = Skin.image("frame");
	private static final java.awt.image.BufferedImage RAISED = Skin.image("raised");
	private static final java.awt.image.BufferedImage SUNK = Skin.image("sunk");
	private static final int WIDTH = 340;
	private static final int PAD = 12;

	private final Client client;
	private final ConfigManager configManager;
	private final PluginManager pluginManager;
	private final OverlayManager overlayManager;
	private final MouseManager mouseManager;
	private final EventBus eventBus;

	private volatile boolean showing;
	private volatile Rectangle box;
	// Clickable areas from the last frame, and what each does.
	private final List<Rectangle> spots = new ArrayList<>();
	private final List<Runnable> actions = new ArrayList<>();

	private final MouseAdapter mouse = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			Rectangle b = box;
			if (!showing || b == null || !b.contains(event.getPoint()))
			{
				return event;
			}
			synchronized (spots)
			{
				for (int i = 0; i < spots.size(); i++)
				{
					if (spots.get(i).contains(event.getPoint()))
					{
						Runnable action = actions.get(i);
						SwingUtilities.invokeLater(action);
						break;
					}
				}
			}
			event.consume();
			return event;
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent event)
		{
			return consumeInBox(event);
		}

		@Override
		public MouseEvent mouseClicked(MouseEvent event)
		{
			return consumeInBox(event);
		}
	};

	@Inject
	CompanionPrompt(Client client, ConfigManager configManager, PluginManager pluginManager, OverlayManager overlayManager,
		MouseManager mouseManager, EventBus eventBus)
	{
		this.client = client;
		this.configManager = configManager;
		this.pluginManager = pluginManager;
		this.overlayManager = overlayManager;
		this.mouseManager = mouseManager;
		this.eventBus = eventBus;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGHEST);
	}

	void start()
	{
		overlayManager.add(this);
		mouseManager.registerMouseListener(mouse);
		eventBus.register(this);
	}

	void stop()
	{
		eventBus.unregister(this);
		mouseManager.unregisterMouseListener(mouse);
		overlayManager.remove(this);
		setShowing(false);
	}

	private MouseEvent consumeInBox(MouseEvent event)
	{
		Rectangle b = box;
		if (showing && b != null && b.contains(event.getPoint()))
		{
			event.consume();
		}
		return event;
	}

	// Whether ChunkBlazer UI or GPU was on at the last look (for the ground borders' Auto).
	private volatile boolean anyOn;

	/** Whether ChunkBlazer UI or ChunkBlazer GPU is on (as of the last game tick). */
	boolean anyOn()
	{
		return anyOn;
	}

	/** Each tick in game: show it unless both plugins are on or it's been skipped. */
	@Subscribe
	public void onGameTick(GameTick tick)
	{
		boolean any = false;
		for (String[] companion : COMPANIONS)
		{
			Plugin plugin = find(companion[0]);
			any |= plugin != null && pluginManager.isPluginActive(plugin);
		}
		anyOn = any;
		if (Boolean.parseBoolean(configManager.getConfiguration(GROUP, DONE)))
		{
			setShowing(false);
			return;
		}
		boolean allOn = true;
		for (String[] companion : COMPANIONS)
		{
			Plugin plugin = find(companion[0]);
			allOn &= plugin != null && pluginManager.isPluginActive(plugin);
		}
		// On Tutorial Island, once the Gielinor Guide has sent you on your way.
		setShowing(!allOn && client.getGameState() == GameState.LOGGED_IN
			&& client.getVarpValue(net.runelite.api.gameval.VarPlayerID.TUTORIAL) >= GUIDE_DONE);
	}

	/** Whether the box is up (ChunkBlazer's own Lumbridge prompts wait for it). */
	boolean isShowing()
	{
		return showing;
	}

	private void setShowing(boolean show)
	{
		if (show != showing)
		{
			showing = show;
			configManager.setConfiguration(GROUP, PENDING, show);
		}
	}

	/** An installed plugin by its name, or null. */
	private Plugin find(String name)
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
			if (descriptor != null && name.equals(descriptor.name()))
			{
				return plugin;
			}
		}
		return null;
	}

	/** Turns a plugin on, as its switch in RuneLite's plugin list would (anything it conflicts with goes off first). */
	private void turnOn(Plugin plugin)
	{
		try
		{
			for (Plugin conflict : pluginManager.conflictsForPlugin(plugin))
			{
				pluginManager.setPluginEnabled(conflict, false);
				pluginManager.stopPlugin(conflict);
			}
			pluginManager.setPluginEnabled(plugin, true);
			pluginManager.startPlugin(plugin);
		}
		catch (PluginInstantiationException e)
		{
			log.warn("Could not turn on {}", plugin.getName(), e);
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!showing)
		{
			box = null;
			return null;
		}
		List<Rectangle> frameSpots = new ArrayList<>();
		List<Runnable> frameActions = new ArrayList<>();
		int header = 26;
		int rowHeight = 44;
		int height = header + 40 + COMPANIONS.length * rowHeight + 20 + 34;
		int x = client.getViewportXOffset() + (client.getViewportWidth() - WIDTH) / 2;
		int y = client.getViewportYOffset() + 30;
		Rectangle b = new Rectangle(x, y, WIDTH, height);
		frame(graphics, b, header);

		graphics.setFont(FontManager.getRunescapeBoldFont());
		Skin.centred(graphics, "Before you start", b.x + WIDTH / 2, b.y + 18, TITLE);
		graphics.setFont(FontManager.getRunescapeFont());
		Skin.text(graphics, "ChunkBlazer is best with its two add-on plugins.", b.x + PAD, b.y + header + 18, TEXT);
		Skin.text(graphics, "Turn them on or install them here, then carry on.", b.x + PAD, b.y + header + 33, TEXT);

		net.runelite.api.Point m = client.getMouseCanvasPosition();
		int rowY = b.y + header + 42;
		for (String[] companion : COMPANIONS)
		{
			Plugin plugin = find(companion[0]);
			boolean active = plugin != null && pluginManager.isPluginActive(plugin);
			// Each add-on on a sunk stone strip, like a row in the task window.
			Rectangle row = new Rectangle(b.x + 8, rowY, WIDTH - 16, rowHeight - 6);
			graphics.setColor(new Color(0, 0, 0, 60));
			graphics.fillRect(row.x, row.y, row.width, row.height);
			Skin.nine(graphics, SUNK, row, 2);
			Rectangle button = new Rectangle(row.x + row.width - 88, row.y + (row.height - 22) / 2, 80, 22);
			graphics.setFont(FontManager.getRunescapeBoldFont());
			Skin.text(graphics, companion[0], row.x + 8, row.y + 16, Color.WHITE);
			int nameEnd = row.x + 8 + graphics.getFontMetrics().stringWidth(companion[0]);
			graphics.setFont(FontManager.getRunescapeSmallFont());
			Skin.text(graphics, Skin.fit(graphics.getFontMetrics(), companion[1], button.x - 8 - (row.x + 8)), row.x + 8, row.y + 30, LABEL);
			// How it is, beside its name.
			String state = active ? "On" : plugin != null ? "Installed, off" : "Not installed";
			Skin.text(graphics, state, nameEnd + 6, row.y + 16, active ? ON : RED);
			if (!active)
			{
				String label = plugin != null ? "Turn on" : "Plugin Hub";
				Runnable action = plugin != null ? () -> turnOn(plugin) : () -> LinkBrowser.browse(HUB + companion[2]);
				button(graphics, button, label, m);
				frameSpots.add(button);
				frameActions.add(action);
			}
			rowY += rowHeight;
		}

		graphics.setFont(FontManager.getRunescapeSmallFont());
		Skin.text(graphics, "Or search \"chunkblazer\" in RuneLite's Plugin Hub.", b.x + PAD, rowY + 8, LABEL);
		Rectangle skip = new Rectangle(b.x + (WIDTH - 100) / 2, b.y + height - 30, 100, 22);
		button(graphics, skip, "Skip", m);
		frameSpots.add(skip);
		frameActions.add(() -> configManager.setConfiguration(GROUP, DONE, true));

		synchronized (spots)
		{
			spots.clear();
			spots.addAll(frameSpots);
			actions.clear();
			actions.addAll(frameActions);
		}
		box = b;
		return null;
	}

	/**
	 * The box the way ChunkBlazer UI draws its Old School boxes: a drop shadow, stone, a darker
	 * header band with a groove under it, the steel frame round it all and an orange line on top.
	 */
	private static void frame(Graphics2D graphics, Rectangle b, int header)
	{
		java.awt.Paint old = graphics.getPaint();
		graphics.setColor(new Color(0, 0, 0, 110));
		graphics.fillRect(b.x + 3, b.y + 3, b.width, b.height);
		graphics.setPaint(Skin.stone(false));
		graphics.fillRect(b.x, b.y, b.width, b.height);
		graphics.setPaint(Skin.stone(true));
		graphics.fillRect(b.x + 3, b.y + 3, b.width - 6, header - 3);
		graphics.setPaint(old);
		graphics.setColor(GROOVE_DARK);
		graphics.drawLine(b.x + 3, b.y + header, b.x + b.width - 4, b.y + header);
		graphics.setColor(GROOVE_LIGHT);
		graphics.drawLine(b.x + 3, b.y + header + 1, b.x + b.width - 4, b.y + header + 1);
		Skin.nine(graphics, FRAME, b, 6);
		graphics.setColor(TITLE);
		graphics.fillRect(b.x + 4, b.y + 4, b.width - 8, 2);
	}

	/** A stone button: raised, sunk under the mouse, its label orange (white when hovered). */
	private static void button(Graphics2D graphics, Rectangle area, String label, net.runelite.api.Point mouse)
	{
		boolean hover = mouse != null && area.contains(mouse.getX(), mouse.getY());
		java.awt.Paint old = graphics.getPaint();
		graphics.setPaint(Skin.stone(!hover));
		graphics.fillRect(area.x, area.y, area.width, area.height);
		graphics.setPaint(old);
		Skin.nine(graphics, hover ? SUNK : RAISED, area, 2);
		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = graphics.getFontMetrics();
		Skin.text(graphics, label, area.x + (area.width - fm.stringWidth(label)) / 2, area.y + 15, hover ? Color.WHITE : TITLE);
	}
}
