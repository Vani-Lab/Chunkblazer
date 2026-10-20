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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ListModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.util.LinkBrowser;

/**
 * Leagues-style task window, opened from the Points orb (ChunkBlazerOrbWidget).
 *
 * Tabs: Active, Saved (starred), Archived (book button) and, after new tasks arrive,
 * New. Search matches name, description, category and chunk; "Current chunk" follows
 * you. The tasks key + click on an unlocked chunk on the world map opens its tasks.
 * Filter: a skill, a tier, task types (any ticked type) and conditions (all ticked
 * conditions); the eye button hides a type or condition. Sort only changes the order.
 * Settings live in RuneLite's plugin config. Drag the title to move the window.
 *
 * Hover is worked out while drawing, from the game's own mouse position, so clicks
 * line up in stretched and resized modes.
 */
@Singleton
public class TaskBrowserOverlay extends Overlay
{
	private static final String CONFIG_GROUP = "chunkblazer";
	private static final String SEEN_KEY = "seenTasks";

	// New-task alert: ticks to wait after login, and the glow's pulse speed.
	private static final int SETTLE_TICKS = 10;
	private static final long PULSE_MS = 1200;

	private static final int MAX_WIDTH = 600;
	private static final int MAX_HEIGHT = 440;
	private static final int HEADER = 40;
	private static final int ROW = 40;
	// Each extra line of a long task name.
	private static final int NAME_LINE = 15;
	// Between task cards; the tabs and tier filter down the left; the hint line at the foot.
	private static final int ROW_GAP = 4;
	private static final int SIDEBAR = 132;
	private static final int FOOTER = 20;
	private static final int PAD = 8;
	private static final long ROW_REFRESH_MS = 1000;
	private static final int DETAIL_LINE = 13;
	private static final int SECTION_TEXT = 11;
	private static final int QUEST_CHECKS_PER_TICK = 15;

	// The game's interface colours (Skin).
	private static final Color TITLE = new Color(255, 152, 31);
	private static final Color ROW_ALT = new Color(0, 0, 0, 30);
	private static final Color ROW_HOVER = new Color(255, 255, 255, 30);
	private static final Color SUBTEXT = new Color(255, 176, 64);
	private static final Color POINTS = new Color(255, 255, 0);
	private static final Color NO_LEVEL = new Color(255, 70, 60);
	// Star, book and arrow icons (ui/*.png): on, hovered and off.
	private static final BufferedImage STAR_ON = Skin.image("star_on");
	private static final BufferedImage STAR_HOVER = Skin.image("star_hover");
	private static final BufferedImage STAR_OFF = Skin.image("star_off");
	private static final BufferedImage BOOK_ON = Skin.image("book_on");
	private static final BufferedImage BOOK_HOVER = Skin.image("book_hover");
	private static final BufferedImage BOOK_OFF = Skin.image("book_off");
	private static final BufferedImage[] ARROWS = {Skin.image("arrow_right"), Skin.image("arrow_right_hover"),
		Skin.image("arrow_down"), Skin.image("arrow_down_hover")};
	private static final Color DARK = new Color(28, 24, 19);
	private static final Color DETAIL = Color.WHITE;
	private static final Color TRACKED_FILL = new Color(255, 152, 31, 46);
	private static final Color SIDEBAR_FILL = new Color(0, 0, 0, 44);
	// Behind every task icon: lighter than the stone, so dark items and icons show.
	private static final Color ITEM_SLOT = new Color(92, 84, 70);
	private static final Color CARD_HOVER = new Color(255, 255, 255, 34);
	private static final Color HERE = new Color(0, 230, 0);
	private static final Color FIELD = new Color(30, 26, 21);
	private static final Color LABEL = new Color(170, 150, 112);
	// Wiki links: the light blue of a link.
	private static final Color WIKI_LINK = new Color(110, 180, 255);
	// The where/how note under an Obtain or Equip task's source: a softer grey.
	private static final Color SOURCE_HOW = new Color(200, 195, 180);

	enum Tab
	{
		ACTIVE, SAVED, ARCHIVED, NEW
	}

	enum SortField
	{
		POINTS("Points", false),
		PROGRESS("Progress", false),
		LEVEL("Level needed", true),
		CHUNK("Chunk", true),
		NAME("Name", true);

		final String label;
		final boolean ascendingByDefault;

		SortField(String label, boolean ascendingByDefault)
		{
			this.label = label;
			this.ascendingByDefault = ascendingByDefault;
		}
	}

	/**
	 * Tickable filters. Types are what a task is, so ticking several shows any of them
	 * (Combat + Talk to); conditions narrow whatever is shown, so all ticked ones must
	 * hold (Quests + Can do = ready quests). Hiding either removes those tasks.
	 */
	enum Filter
	{
		COMBAT("Kills & combat", "Combat", false),
		OBTAIN("Obtain items", "Obtain", false),
		EQUIP("Equip items", "Equip", false),
		TALK("Talk to", "Talk to", false),
		ACHIEVEMENTS("Raids & combat achievements", "Raids & CAs", false),
		QUESTS("Quests (global)", "Quests", false),
		PROGRESSION("Level ups (global)", "Level ups", false),
		CAN_DO("Can do now", "Can do", true),
		OFFLINE("Works offline (mobile)", "Offline", true),
		BOSS("Boss chunks", "Bosses", true);

		final String label;
		final String shortLabel;
		final boolean condition;

		Filter(String label, String shortLabel, boolean condition)
		{
			this.label = label;
			this.shortLabel = shortLabel;
			this.condition = condition;
		}
	}

	/** A list line: a task, or the header of one skill's level-up tasks (group set). */
	private static final class Entry
	{
		final NuzlockeTask task;
		final String group;
		final List<NuzlockeTask> members;
		final boolean child;

		Entry(NuzlockeTask task, String group, List<NuzlockeTask> members, boolean child)
		{
			this.task = task;
			this.group = group;
			this.members = members;
			this.child = child;
		}
	}

	/** A clickable area drawn this frame. Checked in order, so earlier wins. */
	private static final class Hit
	{
		final Shape area;
		final Runnable action;

		Hit(Shape area, Runnable action)
		{
			this.area = area;
			this.action = action;
		}
	}

	private final Client client;
	private final ChunkBlazerPlugin plugin;
	private final ConfigManager configManager;
	private final OverlayManager overlayManager;
	private final MouseManager mouseManager;
	private final SkillIconManager skillIcons;
	// For Obtain and Equip tasks' item pictures (field-injected, so the constructor is unchanged).
	@Inject
	private ItemManager itemManager;
	@Inject
	private SpriteManager spriteManager;
	private final Map<Integer, BufferedImage> sprites = new HashMap<>();
	// Every task icon's slot, in pixels (quest, item, skill, combat or letter).
	private static final int ICON_SLOT = 27;
	private final KeyManager keyManager;
	private final EventBus eventBus;
	private final ChunkBlazerConfig config;
	private final ChunkBlazerWorldMapOverlay worldMap;
	private final TaskArchive archive;
	private final ClientThread clientThread;
	private final ChatboxPanelManager chatboxPanelManager;
	private final SavedTaskTracker savedTracker;
	private final TaskItemOverlay itemOverlay;
	private final ChunkNameBanner banner;
	private final WorldMapLegendOverlay legend;
	private final QuestRequirements quests;

	private volatile boolean open;
	private volatile String search = "";
	private volatile boolean currentChunkOnly;
	// A chunk picked on the world map; while set, only its tasks are listed.
	private volatile String pinnedChunk;
	private volatile boolean tasksKeyHeld;
	private int lastRegionSeen = -1;
	private final Set<String> expanded = ConcurrentHashMap.newKeySet();
	private final Set<String> expandedGroups = ConcurrentHashMap.newKeySet();
	private volatile Tab tab = Tab.ACTIVE;
	private volatile SortField sortField = SortField.POINTS;
	private volatile boolean ascending = SortField.POINTS.ascendingByDefault;
	// Filters. The sets are replaced, never changed in place, so a list build sees one state.
	private volatile Set<Filter> filterOn = Collections.emptySet();
	private volatile Set<Filter> filterHidden = Collections.emptySet();
	private volatile Skill filterSkill;
	private volatile TaskCardTier filterTier;
	// The window's Filters dropdown (the same filters as the side panel's), and where it was drawn.
	private boolean filtersOpen;
	private Rectangle filterPop;
	// The window's Sort dropdown (the same sort as the side panel's).
	private boolean sortOpen;
	// The window's search box, where it was drawn, and whether typing goes into it.
	private volatile Rectangle searchBox;
	// The X at the right of the search box that clears it (null when there's nothing to clear).
	private volatile Rectangle searchClear;
	// The game's chatbox search (like the Grand Exchange's) is open for the window's search.
	private volatile boolean searchFocused;
	// The chatbox search this window opened, while it's up (client thread).
	private ChatboxTextInput searchInput;
	private volatile int scroll;
	// The Patch notes tab (in the header): shown in place of the task list while on.
	private volatile boolean patchNotes;
	// The Settings tab (below the lists): every in-game UI setting, in place of the task list.
	private volatile boolean settingsOpen;
	private List<Setting> settings;
	private volatile int patchScroll;
	private static List<String> patchNoteLines;
	// A quest link was clicked: scroll to and briefly outline this task.
	// The quest whose requirements are shown over the side panel (null when closed).
	private volatile NuzlockeTask questPane;
	// Stands in for the saved-tasks list in the pane, so Back and closing work the same.
	private static final NuzlockeTask SAVED_PANE = new NuzlockeTask();
	private long questPaneAt;
	// Quests opened from inside the pane, newest first, for its Back link.
	private final Deque<NuzlockeTask> questBack = new ConcurrentLinkedDeque<>();
	// The saved-list row under the mouse, for right-click to unsave.
	private volatile String paneRowId;
	// The task-list row under the mouse, for right-click to save, as in the side panel.
	private volatile String listRowId;
	// Where the side panel was when the box was last drawn, to notice it moving.
	private Rectangle paneSide;

	// New-task alert: unseen active tasks, the snapshot the New tab shows, the Points orb.
	private final Set<String> newIds = ConcurrentHashMap.newKeySet();
	private volatile Set<String> shownNew = Collections.emptySet();
	private volatile Rectangle taskButton;
	// The task list's area in the last frame drawn, for the tutorial to point at.
	private volatile Rectangle listBounds;
	// Each task row on screen and its star, and the first row, from the last frame: for the
	// tutorial to point at.
	private final Map<String, Rectangle[]> rowSpots = new ConcurrentHashMap<>();
	private volatile String firstRowId;
	// The Active tab on screen (null when it isn't drawn), for the tutorial to point at.
	private volatile Rectangle activeTabBounds;
	private int ticksLoggedIn;

	private final List<Hit> hits = new ArrayList<>();
	private volatile Runnable hoveredAction;
	private volatile boolean mouseInWindow;
	// Where the window or box was last drawn, on screen (null when neither is shown).
	private volatile Rectangle shownArea;
	// The quest/saved box can be up with the window open: its own scroll, area and hover.
	private volatile int paneScroll;
	private volatile boolean paneIn;
	private volatile Rectangle paneArea;
	// A hint to show by the mouse this frame (set while drawing, drawn last by drawTip).
	private String tip;
	private String tipQuest;
	private long tipQuestAt;
	private boolean tipQuestFound;

	// Dragging the window by its title. Drag events are swallowed, so movement is read
	// from the events, not the game's mouse position.
	private volatile boolean titleHovered;
	private volatile boolean dragging;
	private volatile boolean moved;
	private volatile int pressX;
	private volatile int pressY;
	private volatile int startX;
	private volatile int startY;
	private volatile int windowX;
	private volatile int windowY;
	private volatile int customX;
	private volatile int customY;
	// The window's Interface Size while it's being drawn (1 for the quest/saved box).

	// The redesigned window: where the sidebar's tab marker is (it slides to the chosen tab),
	// the header's numbers and the tier counts (read once a second), and the ChunkBlazer icon.
	private float tabMarkerY = -1;
	private long statsAt;
	private int statDone;
	private int statChunks;
	private final Map<TaskCardTier, Integer> tierCounts = new EnumMap<>(TaskCardTier.class);
	private static BufferedImage logo;

	private List<Entry> rows = new ArrayList<>();
	private final Map<String, String> chunkNames = new HashMap<>();
	private final Map<String, List<String>> requirements = new HashMap<>();
	private long rowsBuiltAt;

	// The mouse as the window and pane see it; the game stops following it over them.
	private volatile java.awt.Point paneMouse = new java.awt.Point(-1, -1);

	private final MouseAdapter mouse = new MouseAdapter()
	{
		// Over the task window or the quest/saved box, the game is told the mouse left the
		// screen, so nothing under it (inventory items, NPCs...) keeps hover text or a tooltip.
		// (Swallowing the move left the game's mouse where it entered, stuck on what was there.)
		// The last drawn area is checked too: mouseInWindow is a frame behind on the way in.
		@Override
		public MouseEvent mouseMoved(MouseEvent event)
		{
			paneMouse = event.getPoint();
			Rectangle area = shownArea;
			Rectangle box = paneArea;
			if ((open || questPane != null) && (mouseInWindow || area != null && area.contains(event.getPoint())
				|| box != null && questPane != null && box.contains(event.getPoint())))
			{
				return new MouseEvent(event.getComponent(), event.getID(), event.getWhen(), event.getModifiersEx(),
					-1, -1, 0, false);
			}
			return event;
		}

		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			// The X in the search box clears the search (and closes the chatbox search if open).
			Rectangle clear = searchClear;
			if (open && event.getButton() == MouseEvent.BUTTON1 && clear != null && clear.contains(event.getPoint()))
			{
				clientThread.invokeLater(TaskBrowserOverlay.this::clearSearch);
				event.consume();
				return event;
			}
			// A click in the search box opens the game's chatbox search, like the Grand Exchange's,
			// so typing never fights with key binds.
			Rectangle box = searchBox;
			if (open && event.getButton() == MouseEvent.BUTTON1 && box != null && box.contains(event.getPoint()))
			{
				clientThread.invokeLater(TaskBrowserOverlay.this::openSearch);
				event.consume();
				return event;
			}
			// A click anywhere else (the list, another tab, the game) finishes the search: the
			// chatbox closes and the keys go back to the game. The search itself stays.
			if (searchFocused)
			{
				clientThread.invokeLater(TaskBrowserOverlay.this::closeSearchInput);
			}
			// Alt + click is RuneLite moving the free-standing quest/saved box.
			if (!open && questPane == null || !mouseInWindow || event.isAltDown())
			{
				return event;
			}
			if (event.getButton() == MouseEvent.BUTTON1 && titleHovered)
			{
				pressX = event.getX();
				pressY = event.getY();
				startX = windowX;
				startY = windowY;
				dragging = true;
			}
			else if (event.getButton() == MouseEvent.BUTTON1 && hoveredAction != null)
			{
				hoveredAction.run();
			}
			else if (event.getButton() == MouseEvent.BUTTON3 && (!open || paneIn) && paneRowId != null)
			{
				// Right-click unsaves in the saved list, as it unstars in the side panel.
				toggleSaved(paneRowId);
			}
			else if (event.getButton() == MouseEvent.BUTTON3 && open && listRowId != null)
			{
				// Right-click saves (or unsaves) a task in the list, as it stars it in the side panel.
				toggleSaved(listRowId);
			}
			// Clicks inside the window never reach the game.
			event.consume();
			return event;
		}

		@Override
		public MouseEvent mouseDragged(MouseEvent event)
		{
			if (dragging)
			{
				customX = startX + event.getX() - pressX;
				customY = startY + event.getY() - pressY;
				moved = true;
				event.consume();
			}
			return event;
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent event)
		{
			if (dragging)
			{
				dragging = false;
				event.consume();
			}
			return event;
		}
	};

	private final MouseWheelListener wheel = event ->
	{
		if ((open || questPane != null) && mouseInWindow)
		{
			if (paneIn && questPane != null)
			{
				paneScroll += event.getWheelRotation() * ROW;
			}
			else if (patchNotes || settingsOpen)
			{
				patchScroll += event.getWheelRotation() * ROW;
			}
			else
			{
				scroll += event.getWheelRotation() * ROW;
			}
			event.consume();
		}
		return event;
	};

	// Esc closes the window and the quest/saved box (F-keys close the box too).
	private final KeyListener keys = new KeyListener()
	{
		@Override
		public void keyTyped(KeyEvent event)
		{
		}

		@Override
		public void keyPressed(KeyEvent event)
		{
			if (config.worldMapTasksKey().matches(event))
			{
				tasksKeyHeld = true;
			}
			// Esc closes the quest/saved box, and so do the F-key tab hotkeys while it covers the
			// inventory, so it never blocks a prayer switch. Typing in chat and moving the camera
			// leave it open (switching tab any other way closes it too: onVarClientIntChanged).
			int code = event.getKeyCode();
			boolean hadPane = questPane != null;
			if (code == KeyEvent.VK_ESCAPE || code >= KeyEvent.VK_F1 && code <= KeyEvent.VK_F12 && !config.movableTaskBox())
			{
				questPane = null;
			}
			if (open && event.getKeyCode() == KeyEvent.VK_ESCAPE)
			{
				// With the quest box up too, Esc closes just the box first.
				if (!hadPane)
				{
					close();
				}
				event.consume();
			}
		}

		@Override
		public void keyReleased(KeyEvent event)
		{
			if (config.worldMapTasksKey().matches(event))
			{
				tasksKeyHeld = false;
			}
		}
	};

	@Inject
	public TaskBrowserOverlay(Client client, ChunkBlazerPlugin plugin, ConfigManager configManager,
		OverlayManager overlayManager, MouseManager mouseManager, SkillIconManager skillIcons, KeyManager keyManager,
		EventBus eventBus, ChunkBlazerConfig config, ChunkBlazerWorldMapOverlay worldMap, TaskArchive archive,
		ClientThread clientThread, SavedTaskTracker savedTracker,
		TaskItemOverlay itemOverlay, ChunkNameBanner banner, WorldMapLegendOverlay legend,
		QuestRequirements quests, ChatboxPanelManager chatboxPanelManager)
	{
		this.chatboxPanelManager = chatboxPanelManager;
		this.client = client;
		this.plugin = plugin;
		this.configManager = configManager;
		this.overlayManager = overlayManager;
		this.mouseManager = mouseManager;
		this.skillIcons = skillIcons;
		this.keyManager = keyManager;
		this.eventBus = eventBus;
		this.config = config;
		this.worldMap = worldMap;
		this.archive = archive;
		this.clientThread = clientThread;
		this.savedTracker = savedTracker;
		this.itemOverlay = itemOverlay;
		this.banner = banner;
		this.legend = legend;
		this.quests = quests;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(OverlayPriority.HIGH);
		freeBox = new Overlay(plugin)
		{
			@Override
			public String getName()
			{
				return "ChunkBlazerTaskBox";
			}

			@Override
			public Dimension render(Graphics2D graphics)
			{
				Rectangle at = getBounds();
				if (!config.movableTaskBox() || questPane == null || at == null
					|| client.getGameState() != GameState.LOGGED_IN)
				{
					return null;
				}
				Rectangle size = sidePanel();
				drawQuestPane(graphics, new Rectangle(0, 0, size.width, size.height), at.x, at.y);
				return new Dimension(size.width, size.height);
			}
		};
		freeBox.setPosition(OverlayPosition.BOTTOM_RIGHT);
		freeBox.setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	// With "Movable Quest/Saved Box" on, the box is its own overlay, so RuneLite's
	// Alt + drag moves it and remembers where; it keeps the inventory's size.
	private final Overlay freeBox;

	/** Registers this window and the other UI overlays (item outlines, banner, map legend). */
	public void startUp()
	{
		overlayManager.add(this);
		overlayManager.add(freeBox);
		mouseManager.registerMouseListener(mouse);
		mouseManager.registerMouseListener(legend.mouse);
		mouseManager.registerMouseWheelListener(wheel);
		keyManager.registerKeyListener(keys);
		eventBus.register(this);
		quests.load();
		savedTracker.startUp();
		overlayManager.add(itemOverlay);
		overlayManager.add(banner);
		overlayManager.add(legend);
	}

	public void shutDown()
	{
		open = false;
		clientThread.invokeLater(this::closeSearchInput);
		overlayManager.remove(this);
		overlayManager.remove(freeBox);
		mouseManager.unregisterMouseListener(mouse);
		mouseManager.unregisterMouseListener(legend.mouse);
		mouseManager.unregisterMouseWheelListener(wheel);
		keyManager.unregisterKeyListener(keys);
		eventBus.unregister(this);
		savedTracker.shutDown();
		overlayManager.remove(itemOverlay);
		overlayManager.remove(banner);
		overlayManager.remove(legend);
	}

	/** Open or close the window (the Points orb's "Tasks" option). */
	public void toggle()
	{
		if (open)
		{
			close();
			return;
		}
		open = true;
		questPane = null;
		pinnedChunk = null;
		refresh();
		if (!newIds.isEmpty())
		{
			shownNew = Collections.unmodifiableSet(new LinkedHashSet<>(newIds));
			tab = Tab.NEW;
		}
	}

	private void close()
	{
		open = false;
		clientThread.invokeLater(this::closeSearchInput);
		pinnedChunk = null;
		finishNew();
	}

	/** Leaving the New tab: its tasks are now seen. */
	private void finishNew()
	{
		if (tab != Tab.NEW)
		{
			return;
		}
		markSeen(shownNew);
		newIds.removeAll(shownNew);
		shownNew = Collections.emptySet();
		tab = Tab.ACTIVE;
		rowsBuiltAt = 0;
	}

	/** Where the Points orb is on screen (null if hidden), for the new-task alert. */
	public void setTaskButtonBounds(Rectangle bounds)
	{
		taskButton = bounds;
	}

	/** The Points orb's bounds, and whether the window is open, for the tutorial. */
	Rectangle getTaskButtonBounds()
	{
		return taskButton;
	}

	boolean isOpen()
	{
		return open;
	}

	/** A task's row on screen (0) or its star (1), or null when it isn't showing. */
	Rectangle getRowSpot(String taskId, int part)
	{
		Rectangle[] spots = open && taskId != null ? rowSpots.get(taskId) : null;
		return spots == null ? null : spots[part];
	}

	// The window's close cross, from the last frame drawn.
	private volatile Rectangle closeBounds;

	/** The window's close cross on screen, or null while it's shut. */
	Rectangle getCloseBounds()
	{
		return open ? closeBounds : null;
	}

	/** True while the window shows the Active list (not Saved, Archived, Settings or patch notes). */
	boolean isShowingActive()
	{
		return open && tab == Tab.ACTIVE && !settingsOpen && !patchNotes;
	}

	/** The Active tab on screen, or null while the window's shut. */
	Rectangle getActiveTabBounds()
	{
		return open ? activeTabBounds : null;
	}

	/** The first task row on screen, or null. */
	String getFirstRowId()
	{
		return open ? firstRowId : null;
	}

	Set<String> savedIdsCopy()
	{
		return new HashSet<>(savedIds());
	}

	boolean isSavedPaneOpen()
	{
		return questPane == SAVED_PANE;
	}

	/** The task list on screen, or null while the window is shut. */
	Rectangle getListBounds()
	{
		return open ? listBounds : null;
	}

	/**
	 * Tasks key + click on an unlocked chunk on the world map: show that chunk's tasks.
	 * Any other click out in the game world (walk, attack, chop...) closes the window,
	 * like the game's own interfaces do. Clicks on interfaces, like the orb, don't.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (open && !tasksKeyHeld && event.getMenuAction() != MenuAction.CANCEL
			&& event.getMenuEntry().getWidget() == null)
		{
			close();
		}
		if (!tasksKeyHeld || client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER) == null)
		{
			return;
		}
		int regionId = worldMap.getHoveredRegionId();
		if (regionId <= 0 || !plugin.isRegionUnlocked(regionId) || plugin.isFreeRegion(regionId))
		{
			return;
		}
		String name = chunkNameFor(regionId);
		if (name.isEmpty())
		{
			return;
		}
		event.consume();
		finishNew();
		pinnedChunk = name;
		currentChunkOnly = false;
		tab = Tab.ACTIVE;
		clearFilters();
		search = "";
		SwingUtilities.invokeLater(() -> sideSearch.setText(""));
		open = true;
		refresh();
	}

	@Subscribe
	public void onFocusChanged(FocusChanged event)
	{
		if (!event.isFocused())
		{
			tasksKeyHeld = false;
		}
	}

	// --- New tasks -----------------------------------------------------------

	/**
	 * Find active chunk tasks not seen yet. With nothing stored (first run on an account),
	 * everything present counts as seen, so there's no flood of "new" tasks.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN || configManager.getRSProfileKey() == null)
		{
			return;
		}
		// Every tick from login, so the side panel fills as soon as the tasks arrive.
		updateSideList();
		if (++ticksLoggedIn < SETTLE_TICKS)
		{
			return;
		}
		if (open || questPane != null)
		{
			quests.refreshStates(QUEST_CHECKS_PER_TICK);
		}
		Set<String> active = new LinkedHashSet<>();
		for (NuzlockeTask task : pool(false))
		{
			active.add(task.getTaskId());
		}
		Set<String> seen = idSet(SEEN_KEY);
		if (seen == null)
		{
			markSeen(active);
			return;
		}
		for (String id : active)
		{
			if (!seen.contains(id))
			{
				newIds.add(id);
			}
		}
		newIds.retainAll(active);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			ticksLoggedIn = 0;
			newIds.clear();
			quests.reset();
		}
	}

	// --- Side panel list --------------------------------------------------------

	// The side panel's task list: stats, the selected task, then Saved, Chunk, Active and
	// Global sections of stone task cards. Click a heading to fold it. It uses the window's task data, so none of that logic is repeated here.
	private final JList<Object> sideList = new JList<Object>()
	{
		// Full panel width even when every section is folded (BoxLayout stops at preferred).
		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	};
	private final Set<String> sideFolded = ConcurrentHashMap.newKeySet();
	private String sideKey = "";
	private boolean sideAdded;
	private volatile int sideHover = -1;
	// Points, Chunks, Tasks and Tokens, each in its own black-edged box.
	private static final String[] STAT_NAMES = {"Points", "Chunks", "Tasks", "Tokens"};
	private final JLabel[] sideStats = new JLabel[STAT_NAMES.length];
	// Finished cards, by what they show, so a repaint doesn't lay out their HTML again.
	private final Map<String, JLabel> sideCards = new HashMap<>();
	// The saved ids the list was last built with (read once a build, not once a card).
	private volatile Set<String> sideSaved = Collections.emptySet();
	// The filters and sort live here; the window shows what's set. sideSync keeps them in step.
	// Skill filter: a button that opens the skills in the game's Skills tab layout, as icons.
	private final JButton sideSkill = smooth(new JButton());
	private final JPopupMenu sideSkillMenu = new JPopupMenu();
	private long sideSkillMenuClosed;
	private final Map<Skill, JButton> sideSkillButtons = new EnumMap<>(Skill.class);
	private final JComboBox<String> sideTier = new JComboBox<>();
	private final JComboBox<String> sideSort = new JComboBox<>();
	private final IconTextField sideSearch = new IconTextField();
	private final Map<Filter, JButton> sideBoxes = new EnumMap<>(Filter.class);
	private final JButton sideFold = new JButton();
	private final JButton sideClear = new JButton("Clear");
	// The list's text size (A- / A+), remembered per account.
	// Text size step (A- / A+): 0 the small font, 1 the regular font, 2 the small font doubled.
	private volatile int sideSize;
	private final JPanel sideFilterBox = new JPanel(new GridLayout(0, 2, 4, 4));
	private boolean sideSyncing;

	/** Once a tick: add the list to the side panel, and refresh it while it's on screen. */
	private void updateSideList()
	{
		ChunkBlazerPanel panel = plugin.getPanel();
		if (!sideAdded && panel != null)
		{
			sideAdded = true;
			String folded = configManager.getRSProfileConfiguration(CONFIG_GROUP, "sideFolded");
			sideFolded.addAll(Arrays.asList((folded == null ? "Active,Global" : folded).split(",")));
			loadFilters();
			sideList.setCellRenderer((list, item, index, selected, focus) -> sideRow(item, index == sideHover));
			java.awt.event.MouseAdapter sideMouse = new java.awt.event.MouseAdapter()
			{
				@Override
				public void mouseMoved(MouseEvent e)
				{
					hoverRow(sideList.locationToIndex(e.getPoint()));
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hoverRow(-1);
				}

				// On press, not click: a click is lost if the mouse moves at all in between.
				@Override
				public void mousePressed(MouseEvent e)
				{
					int i = sideList.locationToIndex(e.getPoint());
					Object item = i < 0 ? null : sideList.getModel().getElementAt(i);
					// Keep the clicked row under the mouse when the list rebuilds (see keepAnchor).
					sideAnchor = anchorAt(i);
					if (item instanceof String)
					{
						String section = ((String) item).replaceAll(" \\(.*", "");
						if (!sideFolded.remove(section))
						{
							sideFolded.add(section);
						}
						configManager.setRSProfileConfiguration(CONFIG_GROUP, "sideFolded", String.join(",", sideFolded));
					}
					else if (item instanceof NuzlockeTask && SwingUtilities.isRightMouseButton(e))
					{
						toggleSaved(((NuzlockeTask) item).getTaskId());
					}
					else if (item instanceof NuzlockeTask)
					{
						trackOrOpen((NuzlockeTask) item, TaskBrowserOverlay.this::openQuestPane);
					}
					// Redraw now rather than on the next game tick (up to 0.6s away).
					sideKey = "";
					clientThread.invoke(TaskBrowserOverlay.this::updateSideList);
				}
			};
			sideList.addMouseListener(sideMouse);
			sideList.addMouseMotionListener(sideMouse);
			sideList.setBackground(new Color(40, 40, 40));
			JPanel stats = new JPanel(new GridLayout(1, STAT_NAMES.length, 4, 0));
			stats.setOpaque(false);
			for (int i = 0; i < sideStats.length; i++)
			{
				// Plain labels, not HTML, so the game's pixel fonts stay their real size.
				JPanel box = new JPanel(new GridLayout(2, 1));
				box.setBackground(PanelStyle.FIELD);
				box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.BLACK),
					new EmptyBorder(2, 0, 3, 0)));
				sideStats[i] = statLabel("0", FontManager.getRunescapeBoldFont(), PanelStyle.YELLOW);
				box.add(sideStats[i]);
				box.add(statLabel(STAT_NAMES[i], FontManager.getRunescapeSmallFont(), PanelStyle.ORANGE));
				stats.add(box);
			}
			stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, stats.getPreferredSize().height));
			panel.addTaskList(stats);
			panel.addTaskList(sideControls());
			sideFold.addActionListener(e ->
			{
				sideFilterBox.setVisible(!sideFilterBox.isVisible());
				sideSync();
				panel.revalidate();
			});
			panel.addTaskList(sideList);
			panel.setOldSchool();
		}
		if (!sideList.isShowing())
		{
			return;
		}
		Set<String> saved = savedIds();
		Set<String> archived = archive.ids();
		String here = currentChunkName();
		List<NuzlockeTask> chunk = new ArrayList<>();
		List<NuzlockeTask> globals = new ArrayList<>();
		String query = search.trim().toLowerCase();
		// A search looks past the filters until the box is cleared.
		Predicate<NuzlockeTask> test = query.isEmpty() ? filterTest(filterOn, filterHidden, filterSkill, filterTier)
			: t -> matchesSearch(t, query);
		for (NuzlockeTask task : pool(true))
		{
			chunkNames.computeIfAbsent(task.getTaskId(), k -> chunkName(task));
			if (!archived.contains(task.getTaskId()) && test.test(task))
			{
				(plugin.isGlobalTask(task.getTaskId()) ? globals : chunk).add(task);
			}
		}
		Comparator<NuzlockeTask> order = sorter(sortField, ascending);
		chunk.sort(order);
		globals.sort(order);
		NuzlockeTask tracked = plugin.getSelectedTask();
		List<Object> items = new ArrayList<>();
		// Only while something is tracked.
		if (tracked != null)
		{
			addSection(items, "Selected", Collections.singletonList(tracked));
		}
		List<NuzlockeTask> savedTasks = new ArrayList<>(chunk);
		savedTasks.addAll(globals);
		savedTasks.removeIf(t -> !saved.contains(t.getTaskId()));
		addSection(items, "Saved", savedTasks);
		// Saved tasks are listed once, under Saved.
		chunk.removeIf(t -> saved.contains(t.getTaskId()));
		globals.removeIf(t -> saved.contains(t.getTaskId()));
		List<NuzlockeTask> inChunk = new ArrayList<>(chunk);
		inChunk.removeIf(t -> !here.equals(chunkNames.get(t.getTaskId())));
		addSection(items, "Chunk", inChunk);
		addSection(items, "Active", chunk);
		addSection(items, "Global", globals);

		// The stat boxes from the original panel's top row.
		int[] numbers = {plugin.getTotalPoints(), paidChunks(), plugin.getCompletedTaskIdSet().size(), plugin.getBossTokens()};
		String stats = Arrays.toString(numbers);
		// Only rebuild when something changed, so the panel doesn't jump while scrolling.
		StringBuilder key = new StringBuilder(stats + sideSize + filterLabel() + sortField + ascending + saved + query);
		items.forEach(o -> key.append(o instanceof NuzlockeTask
			? ((NuzlockeTask) o).getTaskId() + ((NuzlockeTask) o).getCurrentProgress() : o));
		if (key.toString().equals(sideKey))
		{
			return;
		}
		sideKey = key.toString();
		SwingUtilities.invokeLater(() ->
		{
			// A whole new model swapped in at once: clearing the old one flashed an empty list.
			DefaultListModel<Object> model = new DefaultListModel<>();
			items.forEach(model::addElement);
			int height = sideList.getPreferredSize().height;
			// What to hold still: the row just clicked, else (when scrolled down) the top row in view.
			Anchor anchor = sideAnchor != null ? sideAnchor : topAnchor();
			sideAnchor = null;
			sideSaved = saved;
			sideList.setModel(model);
			for (int i = 0; i < numbers.length; i++)
			{
				sideStats[i].setText(String.valueOf(numbers[i]));
			}
			sideSync();
			// The panel is only laid out again when the list changed height.
			if (sideList.getPreferredSize().height != height)
			{
				panel.revalidate();
			}
			keepAnchor(anchor);
			sideList.repaint();
		});
	}

	/**
	 * The Skill and Tier dropdowns and a button per type or condition, two to a row and folded
	 * under one button. A click cycles a button off, on (\u2713) and hidden (\u2717). Sort sits below.
	 */
	private JPanel sideFilters()
	{
		sideFilterBox.setOpaque(false);
		sideFilterBox.setVisible(false);
		sideSkill.setFont(FontManager.getRunescapeSmallFont());
		sideSkill.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		sideSkill.setForeground(Color.WHITE);
		sideSkill.setFocusPainted(false);
		// Clicking the button while the grid is open closes it: the press already hid the popup, so
		// don't reopen it straight away.
		sideSkillMenu.addPopupMenuListener(new PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e)
			{
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
			{
				sideSkillMenuClosed = System.currentTimeMillis();
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e)
			{
			}
		});
		sideSkill.addActionListener(e ->
		{
			if (System.currentTimeMillis() - sideSkillMenuClosed > 250)
			{
				showSkillMenu();
			}
		});
		sideTier.addItem("Tiers");
		Arrays.stream(TaskCardTier.values()).forEach(t -> sideTier.addItem(t.getDisplayName()));
		// Each sort field twice: its usual direction first, so "Points" high to low leads.
		for (SortField field : SortField.values())
		{
			for (boolean asc : new boolean[]{field.ascendingByDefault, !field.ascendingByDefault})
			{
				sideSort.addItem("Sort: " + field.label + (asc ? " \u25B2" : " \u25BC"));
			}
		}
		sideFilterBox.add(sideSkill);
		sideFilterBox.add(sideTier);
		// Every option on screen at once (no scroll bar), the number on the right past a divider.
		sideTier.setMaximumRowCount(sideTier.getItemCount());
		sideSort.setMaximumRowCount(sideSort.getItemCount());
		widenPopup(sideTier, 130);
		widenPopup(sideSort, 140);
		sideTier.setRenderer((list, item, index, selected, focus) ->
		{
			int tier = sideTier.getSelectedIndex();
			int i = index >= 0 ? index : tier;
			if (i < 1)
			{
				return splitRow(index < 0 ? "Tiers" : "All tiers", null, Color.WHITE, selected && index >= 0);
			}
			TaskCardTier t = TaskCardTier.values()[i - 1];
			// The closed box is narrow: just the name there, the points only in the open list.
			return splitRow(t.getDisplayName(), index < 0 ? null : i + (i == 1 ? " pt" : " pts"), t.getAccent(), selected && index >= 0);
		});
		sideSort.setRenderer((list, item, index, selected, focus) ->
		{
			String text = String.valueOf(item);
			String name = text.replaceFirst("^Sort: ", "").replaceAll(" [\u25B2\u25BC]$", "");
			String arrow = text.endsWith("\u25B2") ? "\u25B2" : "\u25BC";
			// Closed: "Sort" until a sort is picked, then just the pick ("Chunk \u25B2").
			if (index < 0)
			{
				return splitRow(sideSort.getSelectedIndex() <= 0 ? "Sort" : name + " " + arrow, null, Color.WHITE, false);
			}
			return splitRow(name, arrow, Color.WHITE, selected, 18);
		});
		for (JComboBox<String> combo : Arrays.asList(sideTier, sideSort))
		{
			combo.setFont(FontManager.getRunescapeSmallFont());
			combo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			combo.setForeground(Color.WHITE);
			combo.addActionListener(e ->
			{
				if (sideSyncing)
				{
					return;
				}
				int tier = sideTier.getSelectedIndex();
				int sort = sideSort.getSelectedIndex();
				filterTier = tier < 1 ? null : TaskCardTier.values()[tier - 1];
				sortField = SortField.values()[sort / 2];
				ascending = sortField.ascendingByDefault == (sort % 2 == 0);
				saveFilters();
				refresh();
				sideChanged();
			});
		}
		for (Filter f : Filter.values())
		{
			JButton button = smooth(new JButton(f.shortLabel));
			button.setFont(FontManager.getRunescapeSmallFont());
			sideBoxes.put(f, button);
			button.addActionListener(e ->
			{
				toggleFilter(f, filterOn.contains(f) || filterHidden.contains(f));
				sideChanged();
			});
			sideFilterBox.add(button);
		}
		sideFold.setFont(FontManager.getRunescapeSmallFont());
		return sideFilterBox;
	}

	/** Opens a dropdown's list at least this wide, so the number column isn't cut off by a narrow box. */
	private static void widenPopup(JComboBox<String> combo, int width)
	{
		combo.addPopupMenuListener(new PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e)
			{
				Object popup = combo.getUI().getAccessibleChild(combo, 0);
				if (popup instanceof JPopupMenu && ((JPopupMenu) popup).getComponentCount() > 0)
				{
					JComponent scroll = (JComponent) ((JPopupMenu) popup).getComponent(0);
					Dimension size = new Dimension(Math.max(combo.getWidth(), width), scroll.getPreferredSize().height);
					scroll.setPreferredSize(size);
					scroll.setMaximumSize(size);
				}
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
			{
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e)
			{
			}
		});
	}

	/** A dropdown row: the name on the left, a fixed-width number (or arrow) on the right past a divider. */
	private static JComponent splitRow(String left, String right, Color color, boolean highlighted)
	{
		return splitRow(left, right, color, highlighted, 42);
	}

	/** splitRow with the right column this wide (an arrow needs far less room than "5 pts"). */
	private static JComponent splitRow(String left, String right, Color color, boolean highlighted, int rightWidth)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBorder(new EmptyBorder(3, 6, 3, 6));
		row.setBackground(highlighted ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
		JLabel name = smooth(new JLabel(left));
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(color);
		row.add(name, BorderLayout.CENTER);
		if (right != null)
		{
			boolean arrow = right.equals("\u25B2") || right.equals("\u25BC");
			// Arrows are drawn, not typed: the small font's arrow glyph is wider than its column and
			// came out as "...". Numbers stay text.
			JLabel number = arrow ? new JLabel(new ImageIcon(Skin.image(right.equals("\u25B2") ? "sort_up" : "sort_down")), SwingConstants.CENTER)
				: smooth(new JLabel(right, SwingConstants.RIGHT));
			number.setFont(FontManager.getRunescapeSmallFont());
			number.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			number.setBorder(new CompoundBorder(new MatteBorder(0, 1, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
				new EmptyBorder(0, arrow ? 1 : 6, 0, 0)));
			number.setPreferredSize(new Dimension(rightWidth, name.getPreferredSize().height));
			row.add(number, BorderLayout.EAST);
		}
		return row;
	}

	/** The skills as the game's Skills tab lays them out: three columns, read row by row. */
	private static final Skill[] SKILL_TAB = {
		Skill.ATTACK, Skill.HITPOINTS, Skill.MINING,
		Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING,
		Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING,
		Skill.RANGED, Skill.THIEVING, Skill.COOKING,
		Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING,
		Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING,
		Skill.RUNECRAFT, Skill.SLAYER, Skill.FARMING,
		Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING,
	};

	/**
	 * The skill filter's dropdown: each skill's icon and level, in the Skills tab's grid, with
	 * "All skills" where the tab shows the total level. Built on first open; the levels and the
	 * highlighted pick are refreshed each time it opens.
	 */
	private void showSkillMenu()
	{
		if (sideSkillButtons.isEmpty())
		{
			JPanel grid = new JPanel(new GridLayout(0, 3, 2, 2));
			grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			for (Skill skill : SKILL_TAB)
			{
				JButton button = smooth(new JButton());
				BufferedImage icon = skillIcons.getSkillImage(skill, false);
				button.setIcon(icon == null ? null : new ImageIcon(icon));
				button.setToolTipText(skill.getName());
				button.setFont(FontManager.getRunescapeSmallFont());
				button.setHorizontalAlignment(SwingConstants.LEFT);
				button.setIconTextGap(6);
				button.setFocusPainted(false);
				button.setBorder(new EmptyBorder(3, 5, 3, 5));
				button.addActionListener(e -> pickSkill(skill));
				sideSkillButtons.put(skill, button);
				grid.add(button);
			}
			JButton all = smooth(new JButton("All skills"));
			all.setFont(FontManager.getRunescapeSmallFont());
			all.setBackground(ColorScheme.DARK_GRAY_COLOR);
			all.setForeground(Color.WHITE);
			all.setFocusPainted(false);
			all.addActionListener(e -> pickSkill(null));
			JPanel menu = new JPanel(new BorderLayout(0, 4));
			menu.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			menu.setBorder(new EmptyBorder(4, 4, 4, 4));
			menu.add(grid, BorderLayout.CENTER);
			menu.add(all, BorderLayout.SOUTH);
			sideSkillMenu.add(menu);
		}
		for (Map.Entry<Skill, JButton> e : sideSkillButtons.entrySet())
		{
			int level = client.getRealSkillLevel(e.getKey());
			boolean picked = e.getKey() == filterSkill;
			JButton button = e.getValue();
			button.setText(level > 0 ? String.valueOf(level) : "");
			button.setBackground(picked ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARK_GRAY_COLOR);
			button.setForeground(picked ? ColorScheme.BRAND_ORANGE : Color.YELLOW);
		}
		sideSkillMenu.show(sideSkill, 0, sideSkill.getHeight());
	}

	private void pickSkill(Skill skill)
	{
		sideSkillMenu.setVisible(false);
		filterSkill = skill;
		saveFilters();
		refresh();
		sideChanged();
	}

	/** Search, Filters (with Clear beside it), the filter buttons, then Sort with A- / A+ beside it, spaced out. */
	private JPanel sideControls()
	{
		// Search matches name, description, category and chunk, in the side panel and the window alike.
		sideSearch.setIcon(IconTextField.Icon.SEARCH);
		sideSearch.setPreferredSize(new Dimension(0, 30));
		sideSearch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		sideSearch.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				searched();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				searched();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				searched();
			}
		});
		sideClear.setFont(FontManager.getRunescapeSmallFont());
		sideClear.addActionListener(e ->
		{
			clearFilters();
			refresh();
			sideChanged();
		});
		JPanel sizes = new JPanel(new GridLayout(1, 2, 2, 0));
		sizes.setOpaque(false);
		String size = configManager.getRSProfileConfiguration(CONFIG_GROUP, "sideTextStep");
		sideSize = size == null ? 0 : Integer.parseInt(size);
		for (int step : new int[]{-1, 1})
		{
			JButton button = smooth(new JButton(step < 0 ? "A-" : "A+"));
			button.setFont(FontManager.getRunescapeSmallFont());
			button.addActionListener(e ->
			{
				sideSize = Math.max(0, Math.min(2, sideSize + step));
				configManager.setRSProfileConfiguration(CONFIG_GROUP, "sideTextStep", sideSize);
				sideChanged();
			});
			sizes.add(button);
		}
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setOpaque(false);
		controls.setBorder(new EmptyBorder(6, 0, 8, 0));
		for (JComponent row : Arrays.asList(sideSearch, row(sideFold, sideClear), sideFilters(), row(sideSort, sizes)))
		{
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.setBorder(new EmptyBorder(0, 0, 5, 0));
			controls.add(row);
		}
		return controls;
	}

	private void searched()
	{
		search = sideSearch.getText();
		refresh();
		sideChanged();
	}

	/** Show every task again: no search, filters or chunk limit. */
	private void showAll()
	{
		setSearch("");
		clearFilters();
		currentChunkOnly = false;
		pinnedChunk = null;
		refresh();
		sideChanged();
	}

	/**
	 * The window's search, typed in the game's chatbox like the Grand Exchange's: the list
	 * filters as you type, and Enter or Esc closes it (the search stays). Client thread.
	 */
	private void openSearch()
	{
		if (!open)
		{
			return;
		}
		searchFocused = true;
		searchInput = chatboxPanelManager.openTextInput("Search tasks")
			.value(search)
			.onChanged(text -> setSearch(text == null ? "" : text))
			.onDone((String text) ->
			{
				searchFocused = false;
				searchInput = null;
			})
			.onClose(() ->
			{
				searchFocused = false;
				searchInput = null;
			})
			.build();
	}

	/**
	 * Closes this window's chatbox search if it's still the chatbox's open input (never
	 * another plugin's or the game's), so the chatbox stops taking keys and the movement
	 * keys work again. Client thread.
	 */
	private void closeSearchInput()
	{
		ChatboxTextInput input = searchInput;
		searchInput = null;
		searchFocused = false;
		if (input == null)
		{
			return;
		}
		if (chatboxPanelManager.getCurrentInput() == input)
		{
			chatboxPanelManager.close();
			return;
		}
		// The chatbox opens a moment after it's asked to: if it hasn't yet, close it once it has.
		clientThread.invokeLater(() ->
		{
			if (chatboxPanelManager.getCurrentInput() == input)
			{
				chatboxPanelManager.close();
			}
		});
	}

	/** Typed in the window's search: search, and show it in the side panel's box too. */
	private void setSearch(String text)
	{
		search = text;
		refresh();
		SwingUtilities.invokeLater(() ->
		{
			if (!sideSearch.getText().equals(text))
			{
				sideSearch.setText(text);
			}
		});
	}

	/** {@code main} filling the width with {@code side} at its right. */
	private static JPanel row(JComponent main, JComponent side)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.add(main);
		row.add(side, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(main.getPreferredSize().height, side.getPreferredSize().height) + 5));
		return row;
	}

	/** Show the current filters and sort on the side panel's controls (the window's tier list sets them too). */
	private void sideSync()
	{
		sideSyncing = true;
		Skill skill = filterSkill;
		sideSkill.setText(skill == null ? "Skills \u25BE" : skill.getName());
		BufferedImage icon = skill == null ? null : skillIcons.getSkillImage(skill, true);
		sideSkill.setIcon(icon == null ? null : new ImageIcon(icon));
		sideTier.setSelectedIndex(filterTier == null ? 0 : filterTier.ordinal() + 1);
		sideSort.setSelectedIndex(sortField.ordinal() * 2 + (ascending == sortField.ascendingByDefault ? 0 : 1));
		sideBoxes.forEach((f, box) ->
		{
			box.setText((filterOn.contains(f) ? "\u2713 " : filterHidden.contains(f) ? "\u2717 " : "") + f.shortLabel);
			box.setForeground(filterOn.contains(f) ? new Color(120, 220, 120)
				: filterHidden.contains(f) ? new Color(255, 110, 110) : Color.WHITE);
		});
		sideClear.setEnabled(anyFilter());
		sideFold.setText((sideFilterBox.isVisible() ? "\u25BC Filters" : "\u25B6 Filters") + " (" + (filterOn.size()
			+ filterHidden.size() + (filterSkill != null ? 1 : 0) + (filterTier != null ? 1 : 0)) + ")");
		sideSyncing = false;
	}

	/** A filter changed in the side panel: rebuild the list now rather than on the next game tick. */
	private void sideChanged()
	{
		sideKey = "";
		clientThread.invoke(this::updateSideList);
	}

	/**
	 * The RuneScape fonts are pixel fonts on a 16-pixel grid, so they're only crisp when their
	 * size on screen is 16 (or 32) pixels. Any other size, or Windows display scaling (150% on a
	 * 4K screen), squeezes the grid unevenly; drawn as plain pixels, rows and columns drop out
	 * or double up. Smoothed (greyscale) text stays whole at any size and is just as sharp on
	 * the grid. Greyscale, not the coloured ClearType edges Windows would pick.
	 */
	private static <T extends JComponent> T smooth(T component)
	{
		component.putClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		return component;
	}

	private static JLabel statLabel(String text, Font font, Color color)
	{
		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setFont(font);
		label.setForeground(color);
		return label;
	}

	/** Chunks paid for with points or a boss token; free, charter and starting chunks don't count. */
	private int paidChunks()
	{
		return plugin.countPayableUnlockedChunks() + (int) plugin.getUnlockedRegionIds().stream()
			.filter(id -> id.trim().matches("\\d+") && plugin.isBossRegion(Integer.parseInt(id.trim()))).count();
	}

	/** A header with its count, then (unless folded) all its tasks. */
	private void addSection(List<Object> items, String title, List<NuzlockeTask> tasks)
	{
		// An empty section is left out entirely; its heading comes back once it has a task.
		if (tasks.isEmpty())
		{
			return;
		}
		items.add(title + " (" + tasks.size() + ")");
		if (sideFolded.contains(title))
		{
			return;
		}
		items.addAll(tasks);
	}

	/**
	 * A row the side list holds still on screen while it rebuilds: a task (by id) or a heading
	 * (by title), the section it was in, and how far down the visible area it was.
	 */
	private static final class Anchor
	{
		final String key;
		final String section;
		final int y;
		// Where its section's heading was, for when the row itself leaves that section.
		final int sectionY;

		Anchor(String key, String section, int y, int sectionY)
		{
			this.key = key;
			this.section = section;
			this.y = y;
			this.sectionY = sectionY;
		}
	}

	private volatile Anchor sideAnchor;

	private JViewport sideViewport()
	{
		return (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, sideList);
	}

	/** The row's key: a task's id, or a heading's title without its count. */
	private static String rowKey(Object item)
	{
		return item instanceof NuzlockeTask ? ((NuzlockeTask) item).getTaskId() : String.valueOf(item).replaceAll(" \\(.*", "");
	}

	/** The index of the section heading a row sits under, or -1. */
	private static int headingOf(ListModel<Object> model, int index)
	{
		for (int i = index; i >= 0; i--)
		{
			if (model.getElementAt(i) instanceof String)
			{
				return i;
			}
		}
		return -1;
	}

	private static String sectionOf(ListModel<Object> model, int index)
	{
		int heading = headingOf(model, index);
		return heading < 0 ? "" : rowKey(model.getElementAt(heading));
	}

	private Anchor anchorAt(int index)
	{
		JViewport view = sideViewport();
		Rectangle cell = index < 0 ? null : sideList.getCellBounds(index, index);
		if (view == null || cell == null)
		{
			return null;
		}
		ListModel<Object> model = sideList.getModel();
		int y = SwingUtilities.convertPoint(sideList, 0, cell.y, view).y;
		int heading = headingOf(model, index);
		Rectangle headingCell = heading < 0 ? cell : sideList.getCellBounds(heading, heading);
		int headingY = SwingUtilities.convertPoint(sideList, 0, headingCell.y, view).y;
		return new Anchor(rowKey(model.getElementAt(index)), sectionOf(model, index), y, headingY);
	}

	/** The first row in view, but only when scrolled down: at the top, new sections just appear. */
	private Anchor topAnchor()
	{
		JViewport view = sideViewport();
		if (view == null || view.getViewPosition().y == 0)
		{
			return null;
		}
		Point top = SwingUtilities.convertPoint(view, 0, 0, sideList);
		return anchorAt(sideList.locationToIndex(new Point(0, Math.max(0, top.y))));
	}

	/**
	 * After a rebuild, scroll so the anchored row is back where it was. If the row left its
	 * section (a task just saved moves to Saved), its old section's heading is held instead,
	 * so the view doesn't jump to wherever the row went.
	 */
	private void keepAnchor(Anchor anchor)
	{
		JViewport view = sideViewport();
		if (anchor == null || view == null)
		{
			return;
		}
		JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, view);
		if (scroll != null)
		{
			scroll.validate(); // lay out now, so the rows' new positions are final
		}
		ListModel<Object> model = sideList.getModel();
		int found = -1;
		int heading = -1;
		for (int i = 0; i < model.getSize() && found < 0; i++)
		{
			Object item = model.getElementAt(i);
			String key = rowKey(item);
			if (item instanceof String && key.equals(anchor.section))
			{
				heading = i;
			}
			if (key.equals(anchor.key) && sectionOf(model, i).equals(anchor.section))
			{
				found = i;
			}
		}
		int index = found >= 0 ? found : heading;
		Rectangle cell = index < 0 ? null : sideList.getCellBounds(index, index);
		if (cell == null)
		{
			return;
		}
		// The row back where it was; or, if it left its section, that section's heading back
		// where it was (the rows under it just close the gap).
		int was = found >= 0 ? anchor.y : anchor.sectionY;
		int now = SwingUtilities.convertPoint(sideList, 0, cell.y, view).y;
		Point position = view.getViewPosition();
		int max = Math.max(0, view.getView().getHeight() - view.getHeight());
		position.y = Math.max(0, Math.min(max, position.y + now - was));
		view.setViewPosition(position);
	}

	/** Move the hover highlight, repainting just the row it left and the one it's on. */
	private void hoverRow(int index)
	{
		int old = sideHover;
		sideHover = index;
		for (int i : new int[]{old, index})
		{
			Rectangle cell = i < 0 || i == old && i == index ? null : sideList.getCellBounds(i, i);
			if (cell != null)
			{
				sideList.repaint(cell);
			}
		}
	}

	/**
	 * A section heading (a stone bar) or a task card (stone, with its tier and points), made
	 * once for each thing it can show and reused after that.
	 */
	/**
	 * The side panel's card font at the A- / A+ step. The game fonts are pixel fonts drawn on
	 * a 16px grid, so the size lands on a whole multiple of 16 screen pixels, Windows display
	 * scaling included: any other size splits their pixels and the text looks choppy. The
	 * multiple is the one nearest the scaling (2x at 200%, so 4K text isn't tiny). Every step
	 * grows every line: the first two by font (small and regular text, regular and bold names),
	 * the last one multiple up. {@code kind}: 0 card text, 1 a task's name, 2 a heading.
	 */
	private Font sideFont(int kind)
	{
		GraphicsConfiguration screen = sideList.getGraphicsConfiguration();
		double scale = screen == null ? 1 : screen.getDefaultTransform().getScaleX();
		long grid = Math.max(1, Math.round(scale)) + (sideSize == 2 ? 1 : 0);
		Font font = kind == 2 || kind == 1 && sideSize > 0 ? FontManager.getRunescapeBoldFont()
			: kind == 1 || sideSize > 0 ? FontManager.getRunescapeFont() : FontManager.getRunescapeSmallFont();
		return font.deriveFont((float) (16 * grid / scale));
	}

	private JLabel sideRow(Object item, boolean hover)
	{
		String key = item instanceof String ? item + "|" + sideFolded.contains(((String) item).replaceAll(" \\(.*", ""))
			: ((NuzlockeTask) item).getTaskId() + "|" + ((NuzlockeTask) item).getCurrentProgress() + "|"
				+ isTracked((NuzlockeTask) item) + sideSaved.contains(((NuzlockeTask) item).getTaskId());
		Font small = sideFont(0);
		key += "|" + hover + "|" + small + sideSize;
		if (sideCards.size() > 2000)
		{
			sideCards.clear();
		}
		return sideCards.computeIfAbsent(key, k ->
		{
			return item instanceof String ? sideHeader((String) item, hover, small) : sideCard((NuzlockeTask) item, hover, small);
		});
	}

	/**
	 * Old School heading, like the panel's buttons: light raised stone in a black edge, the
	 * name in yellow, and its count in a dark box at the right.
	 */
	private JLabel sideHeader(String title, boolean hover, Font small)
	{
		String name = title.replaceAll(" \\(.*", "");
		String count = title.substring(name.length()).replaceAll("[ ()]", "");
		boolean folded = sideFolded.contains(name);
		JLabel header = new JLabel(name)
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				Rectangle bar = new Rectangle(0, 6, getWidth(), getHeight() - 8);
				g2.setPaint(Skin.stone(false));
				g2.fillRect(bar.x, bar.y, bar.width, bar.height);
				if (hover)
				{
					g2.setColor(new Color(255, 255, 255, 30));
					g2.fillRect(bar.x, bar.y, bar.width, bar.height);
				}
				g2.setColor(Color.BLACK);
				g2.drawRect(bar.x, bar.y, bar.width - 1, bar.height - 1);
				Skin.bevel(g2, new Rectangle(bar.x + 1, bar.y + 1, bar.width - 2, bar.height - 2), true);
				// Drawn, not a text arrow, as the game's fonts have no arrow glyphs.
				int cy = bar.y + bar.height / 2;
				g2.setColor(getForeground());
				g2.fillPolygon(folded ? new int[]{9, 9, 15} : new int[]{8, 16, 12}, folded ? new int[]{cy - 4, cy + 4, cy}
					: new int[]{cy - 2, cy - 2, cy + 3}, 3);
				g2.setFont(small);
				FontMetrics fm = g2.getFontMetrics();
				Rectangle chip = new Rectangle(bar.x + bar.width - fm.stringWidth(count) - 16, bar.y + 4,
					fm.stringWidth(count) + 10, bar.height - 8);
				g2.setColor(PanelStyle.FIELD);
				g2.fillRect(chip.x, chip.y, chip.width, chip.height);
				g2.setColor(Color.BLACK);
				g2.drawRect(chip.x, chip.y, chip.width - 1, chip.height - 1);
				g2.setColor(PanelStyle.ORANGE);
				g2.drawString(count, chip.x + 5, chip.y + (chip.height + fm.getAscent()) / 2 - 1);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		header.setFont(sideFont(2));
		header.setForeground(name.startsWith("Selected") ? Color.WHITE : PanelStyle.YELLOW);
		header.setBorder(new EmptyBorder(9, 22, 4, 4));
		return smooth(header);
	}

	/**
	 * Old School card: dark stone in a bevelled frame, the tier down the left, white name,
	 * yellow category, orange chunk, and a soft green bar in a dark groove.
	 */
	private JLabel sideCard(NuzlockeTask task, boolean hover, Font small)
	{
		boolean selected = isTracked(task);
		boolean global = plugin.isGlobalTask(task.getTaskId());
		int target = task.getTargetQuantity();
		int progress = Math.min(task.getCurrentProgress(), target);
		Color tier = TaskCardTier.fromTask(task).getAccent();
		String description = task.getDescription() == null ? "" : task.getDescription().trim();
		boolean saved = sideSaved.contains(task.getTaskId());
		// Drawn line by line, not as HTML: HTML rounds the font to whole points, which undoes
		// sideFont's exact size (and the name kept one size whatever the step).
		List<Object[]> lines = new ArrayList<>();
		sideLines(lines, task.getName() == null ? task.getTaskId() : task.getName(), Color.WHITE, sideFont(1));
		sideLines(lines, NuzlockeTask.displayCategory(task.getCategory())
			+ (task.getLevelRequirement() > 1 ? "  Lvl " + task.getLevelRequirement() : ""), Color.YELLOW, small);
		if (!global)
		{
			sideLines(lines, chunkNames.getOrDefault(task.getTaskId(), ""), new Color(255, 152, 31), small);
		}
		sideLines(lines, description, new Color(200, 180, 140), small);
		JLabel card = new JLabel()
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				int w = getWidth();
				int h = getHeight() - 3;
				Rectangle box = new Rectangle(0, 0, w, h);
				g2.setPaint(Skin.stone(true));
				g2.fillRect(0, 0, w, h);
				// Saved: a warm gold wash over the whole card, easy to spot down the list.
				if (saved)
				{
					g2.setColor(new Color(255, 200, 40, 34));
					g2.fillRect(0, 0, w, h);
				}
				if (selected || hover)
				{
					g2.setColor(selected ? new Color(255, 152, 31, 40) : new Color(255, 255, 255, 22));
					g2.fillRect(0, 0, w, h);
				}
				Skin.bevel(g2, box, true);
				g2.setColor(tier);
				g2.fillRect(3, 3, 3, h - 6);
				banner(g2, w - 20, 3, tier, task.getBasePoints());
				if (selected)
				{
					g2.setColor(PanelStyle.ORANGE);
					g2.drawRect(0, 0, w - 1, h - 1);
				}
				if (!global)
				{
					String count = progress + "/" + target;
					g2.setFont(getFont());
					int textWidth = g2.getFontMetrics().stringWidth(count);
					int barWidth = w - 32 - textWidth;
					Skin.bar(g2, 13, h - 15, barWidth, 7, (float) (progress / (double) Math.max(1, target)), null, 0, false);
					g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
					g2.setColor(Color.BLACK);
					g2.drawString(count, w - 11 - textWidth, h - 6);
					g2.setColor(PanelStyle.YELLOW);
					g2.drawString(count, w - 12 - textWidth, h - 7);
				}
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				int y = 6;
				for (Object[] line : lines)
				{
					FontMetrics fm = g2.getFontMetrics((Font) line[2]);
					g2.setFont((Font) line[2]);
					g2.setColor((Color) line[1]);
					g2.drawString((String) line[0], 12, y + fm.getAscent());
					y += fm.getHeight();
				}
				g2.dispose();
			}
		};
		card.setFont(small);
		int height = 6 + (global ? 9 : 25);
		for (Object[] line : lines)
		{
			height += card.getFontMetrics((Font) line[2]).getHeight();
		}
		card.setPreferredSize(new Dimension(168, height));
		return card;
	}

	/** Text wrapped to a card's width, each line with its colour and font, for sideCard to draw. */
	private void sideLines(List<Object[]> lines, String text, Color color, Font font)
	{
		if (!text.isEmpty())
		{
			for (String line : wrap(sideList.getFontMetrics(font), text, 150))
			{
				lines.add(new Object[]{line, color, font});
			}
		}
	}

	/** A small hanging banner in the tier's colour with the task's points on it (side panel cards). */
	private static void banner(Graphics2D g, int x, int y, Color tier, int points)
	{
		int w = 14;
		int h = 18;
		Polygon flag = new Polygon(new int[]{x, x + w, x + w, x + w / 2, x}, new int[]{y, y, y + h, y + h - 5, y + h}, 5);
		g.setColor(tier);
		g.fillPolygon(flag);
		g.setColor(new Color(255, 255, 255, 60));
		g.fillRect(x + 1, y + 1, w - 2, 2);
		g.setColor(Color.BLACK);
		g.drawPolygon(flag);
		g.setFont(FontManager.getRunescapeSmallFont());
		String text = String.valueOf(points);
		int tx = x + (w - g.getFontMetrics().stringWidth(text)) / 2 + 1;
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		g.drawString(text, tx + 1, y + 12);
		g.setColor(Color.WHITE);
		g.drawString(text, tx, y + 11);
	}

	/** Saved tasks still to do, in the order they were starred. */
	List<NuzlockeTask> savedTasks()
	{
		Map<String, NuzlockeTask> byId = new HashMap<>();
		pool(true).forEach(t -> byId.put(t.getTaskId(), t));
		Set<String> archived = archive.ids();
		List<NuzlockeTask> list = new ArrayList<>();
		for (String id : savedIds())
		{
			if (byId.containsKey(id) && !archived.contains(id))
			{
				list.add(byId.get(id));
			}
		}
		return list;
	}

	/** The saved bubble: open the saved list over the side panel, or close it. */
	void toggleSavedPane()
	{
		if (questPane == SAVED_PANE)
		{
			questPane = null;
		}
		else
		{
			openQuestPane(SAVED_PANE);
		}
	}

	/** A comma-separated id set stored per account, or null if never stored. */
	private Set<String> idSet(String key)
	{
		String raw = configManager.getRSProfileConfiguration(CONFIG_GROUP, key);
		if (raw == null)
		{
			return null;
		}
		Set<String> ids = new LinkedHashSet<>();
		for (String id : raw.split(","))
		{
			if (!id.trim().isEmpty())
			{
				ids.add(id.trim());
			}
		}
		return ids;
	}

	private void storeIds(String key, Set<String> ids)
	{
		if (configManager.getRSProfileKey() != null)
		{
			configManager.setRSProfileConfiguration(CONFIG_GROUP, key, String.join(",", ids));
		}
	}

	private void markSeen(Set<String> ids)
	{
		Set<String> seen = idSet(SEEN_KEY);
		Set<String> updated = seen == null ? new LinkedHashSet<>() : seen;
		updated.addAll(ids);
		storeIds(SEEN_KEY, updated);
	}

	private Set<String> savedIds()
	{
		return archive.savedIds();
	}

	private void toggleSaved(String taskId)
	{
		archive.toggleSaved(taskId);
		rowsBuiltAt = 0;
	}

	/**
	 * A click on a task in any list: a quest opens its requirements ({@code openQuest}),
	 * the tracked task stops being tracked, and any other task is tracked.
	 */
	private void trackOrOpen(NuzlockeTask task, Consumer<NuzlockeTask> openQuest)
	{
		if (QuestRequirements.isQuestTask(task))
		{
			openQuest.accept(task);
		}
		else if (isTracked(task))
		{
			plugin.clearSelectedTask();
		}
		else
		{
			plugin.selectTaskFromGame(task);
		}
	}

	/** True if this is the task being tracked. */
	private boolean isTracked(NuzlockeTask task)
	{
		NuzlockeTask tracked = plugin.getSelectedTask();
		return tracked != null && task != null && tracked.getTaskId().equals(task.getTaskId());
	}

	/** Archive or restore a task. Archiving the tracked task also stops tracking it. */
	private void toggleArchived(NuzlockeTask task)
	{
		boolean archiving = !archive.isArchived(task);
		archive.toggle(task.getTaskId());
		if (archiving && isTracked(task))
		{
			plugin.clearSelectedTask();
		}
		rowsBuiltAt = 0;
	}

	private void refresh()
	{
		scroll = 0;
		rowsBuiltAt = 0;
	}

	// --- Which tasks, in what order -----------------------------------------

	/** Unfinished chunk tasks, plus unfinished Global tasks (quests, level-ups) if asked. */
	private List<NuzlockeTask> pool(boolean includeGlobal)
	{
		List<NuzlockeTask> pool = new ArrayList<>();
		for (NuzlockeTask task : plugin.getActiveTasks())
		{
			if (task != null && task.getTaskId() != null && !task.isCompleted())
			{
				pool.add(task);
			}
		}
		if (includeGlobal)
		{
			Set<String> completed = plugin.getCompletedTaskIdSet();
			for (NuzlockeTask task : plugin.getVisibleGlobalTasks())
			{
				if (task != null && task.getTaskId() != null && !completed.contains(task.getTaskId()))
				{
					pool.add(task);
				}
			}
		}
		return pool;
	}

	private int count(boolean includeGlobal, Predicate<String> test)
	{
		int count = 0;
		for (NuzlockeTask task : pool(includeGlobal))
		{
			count += test.test(task.getTaskId()) ? 1 : 0;
		}
		return count;
	}

	/**
	 * Skill and tier must match, at least one ticked type (if any), every ticked
	 * condition, and no hidden filter.
	 */
	private Predicate<NuzlockeTask> filterTest(Set<Filter> on, Set<Filter> hidden, Skill skill, TaskCardTier tier)
	{
		boolean anyType = on.stream().anyMatch(f -> !f.condition);
		return task -> (skill == null || isSkillTask(task, skill))
			&& (tier == null || TaskCardTier.fromTask(task) == tier)
			&& on.stream().allMatch(f -> !f.condition || matches(f, task))
			&& (!anyType || on.stream().anyMatch(f -> !f.condition && matches(f, task)))
			&& hidden.stream().noneMatch(f -> matches(f, task));
	}

	private boolean matches(Filter f, NuzlockeTask task)
	{
		switch (f)
		{
			case COMBAT:
				return typeIs(task, "NPC_KILL", "SLAYER")
					|| (task.getCategory() != null && task.getCategory().toLowerCase().contains("combat"));
			case OBTAIN:
				// Includes items that come from a quest (Obtain a Black Candle).
				return typeIs(task, "OBTAIN") || "Obtain".equalsIgnoreCase(task.getCategory());
			case EQUIP:
				return typeIs(task, "EQUIP");
			case TALK:
				return typeIs(task, "NPC_DIALOGUE");
			case ACHIEVEMENTS:
				return typeIs(task, "COMBAT_ACHIEVEMENT", "RAID_CHALLENGE");
			case QUESTS:
				return QuestRequirements.isQuestTask(task);
			case PROGRESSION:
				return typeIs(task, "SKILL_THRESHOLD");
			case CAN_DO:
				return canDo(task);
			case OFFLINE:
				return isOfflineTrackable(task);
			default:
				return plugin.isBossTask(task);
		}
	}

	/**
	 * True if a task done away from RuneLite (on mobile) still completes at the next
	 * RuneLite login: it's checked against saved game state. Quests (QuestCheckModule
	 * sweep), real levels (ProgressionModule), combat achievement varps, persistent unlock
	 * varbits, items held in inventory/bank/worn (ObtainModule; the bank once opened) and
	 * worn equipment (EquipModule). Kills, skilling XP drops, dialogue and the active-prayer
	 * varbit (it resets) need RuneLite watching.
	 */
	static boolean isOfflineTrackable(NuzlockeTask task)
	{
		return typeIs(task, "QUEST_CHECK", "SKILL_THRESHOLD", "COMBAT_ACHIEVEMENT", "EQUIP", "OBTAIN")
			|| (typeIs(task, "VARBIT_CHECK", "VARP_CHECK") && "unlock".equalsIgnoreCase(task.getCategory()));
	}

	private boolean anyFilter()
	{
		return filterSkill != null || filterTier != null || !filterOn.isEmpty() || !filterHidden.isEmpty();
	}

	/** How many filters are set: each ticked or hidden type and condition, the skill and the tier. */
	private int filterCount()
	{
		return filterOn.size() + filterHidden.size() + (filterSkill != null ? 1 : 0) + (filterTier != null ? 1 : 0);
	}

	// The Filters dropdown's layout: skills in a grid, then types and conditions as buttons.
	private static final int POP_WIDTH = 252;
	private static final int POP_PAD = 8;
	private static final int SKILL_COLUMNS = 8;
	private static final int SKILL_CELL_H = 24;
	private static final int CHIP_COLUMNS = 3;
	private static final int CHIP_H = 18;

	// The Sort dropdown: each field both ways, its usual direction first (as the side panel's).
	private static final int SORT_POP_WIDTH = 170;
	private static final int SORT_ROW_H = 18;

	private Rectangle sortPopBounds(Rectangle window, int left, int top)
	{
		int height = 8 + SortField.values().length * 2 * SORT_ROW_H + 6;
		return new Rectangle(Math.min(left, window.x + window.width - SORT_POP_WIDTH - 6), top, SORT_POP_WIDTH,
			Math.min(height, window.y + window.height - 4 - top));
	}

	/** The Sort dropdown: pick a field and direction; the side panel's Sort follows. */
	private void drawSortPop(Graphics2D graphics, Rectangle window, Rectangle pop, int mx, int my)
	{
		List<Hit> popHits = new ArrayList<>();
		Shape old = graphics.getClip();
		graphics.setColor(new Color(0, 0, 0, 90));
		graphics.fillRect(pop.x + 3, pop.y + 3, pop.width, pop.height);
		Skin.stoneFrame(graphics, pop, 0);
		graphics.clip(pop);
		graphics.setFont(Skin.small());
		int y = pop.y + 6;
		for (SortField field : SortField.values())
		{
			for (boolean asc : new boolean[]{field.ascendingByDefault, !field.ascendingByDefault})
			{
				Rectangle row = new Rectangle(pop.x + 4, y, pop.width - 8, SORT_ROW_H - 2);
				boolean picked = field == sortField && asc == ascending;
				boolean hover = row.contains(mx, my);
				Skin.button(graphics, row, hover, picked);
				graphics.setColor(picked ? new Color(120, 220, 120) : hover ? Color.WHITE : SUBTEXT);
				Skin.text(graphics, field.label, row.x + 6, row.y + 12);
				String arrow = asc ? "\u25B2" : "\u25BC";
				Skin.text(graphics, arrow, row.x + row.width - 6 - graphics.getFontMetrics().stringWidth(arrow), row.y + 12);
				popHits.add(new Hit(row, () ->
				{
					sortField = field;
					ascending = asc;
					sortOpen = false;
					saveFilters();
					refresh();
					sideChanged();
				}));
				y += SORT_ROW_H;
			}
		}
		graphics.setClip(old);
		// Clicks in the dropdown stay in it; anywhere else in the window closes it.
		popHits.add(new Hit(pop, () ->
		{
		}));
		java.awt.geom.Area outside = new java.awt.geom.Area(window);
		outside.subtract(new java.awt.geom.Area(pop));
		popHits.add(new Hit(outside, () -> sortOpen = false));
		hits.addAll(0, popHits);
	}

	/**
	 * The search box in the list's heading: matches name, description, category and chunk, and
	 * looks past the filters until it's cleared. The side panel's search box shows the same text.
	 */
	private void drawSearchBox(Graphics2D graphics, Rectangle box, int mx, int my)
	{
		searchBox = box;
		boolean hover = box.contains(mx, my);
		boolean focused = searchFocused;
		graphics.setColor(new Color(0, 0, 0, focused ? 150 : hover ? 110 : 80));
		graphics.fillRect(box.x, box.y, box.width, box.height);
		graphics.setColor(focused ? TITLE : hover ? SUBTEXT : LABEL.darker());
		graphics.drawRect(box.x, box.y, box.width - 1, box.height - 1);
		FontMetrics fm = graphics.getFontMetrics();
		String text = search;
		int textY = box.y + box.height - 3;
		// An X to clear it, once there's something typed.
		Rectangle clear = text.isEmpty() ? null : new Rectangle(box.x + box.width - 16, box.y + 2, 14, box.height - 4);
		searchClear = clear;
		boolean clearHover = clear != null && clear.contains(mx, my);
		int room = clear == null ? box.width - 10 : box.width - 26;
		if (text.isEmpty())
		{
			graphics.setColor(LABEL);
			Skin.text(graphics, Skin.fit(fm, "Search tasks", box.width - 8), box.x + 4, textY);
		}
		else
		{
			// The end of the text when it's too long to fit, and a blinking caret while typing.
			String shown = text;
			while (!shown.isEmpty() && fm.stringWidth(shown) > room)
			{
				shown = shown.substring(1);
			}
			graphics.setColor(Color.WHITE);
			Skin.text(graphics, shown, box.x + 4, textY);
		}
		if (clear != null)
		{
			Skin.close(graphics, clear, clearHover);
		}
		if (clearHover)
		{
			tip = "Clear the search";
		}
		else if (hover && !focused)
		{
			tip = "Click to search in the chatbox, like the Grand Exchange";
		}
	}

	/** The search box's X: empties the search, and closes the chatbox search if it's open. Client thread. */
	private void clearSearch()
	{
		closeSearchInput();
		setSearch("");
	}

	/** Where the dropdown goes: under the Filters tab, kept inside the window. */
	private Rectangle filterPopBounds(Rectangle window, int left, int top)
	{
		int types = (int) Arrays.stream(Filter.values()).filter(f -> !f.condition).count();
		int conditions = Filter.values().length - types;
		int skillRows = (SKILL_TAB.length + SKILL_COLUMNS - 1) / SKILL_COLUMNS;
		int chipRows = (types + CHIP_COLUMNS - 1) / CHIP_COLUMNS + (conditions + CHIP_COLUMNS - 1) / CHIP_COLUMNS;
		int height = 22 + 3 * 14 + skillRows * SKILL_CELL_H + chipRows * (CHIP_H + 3) + 12 + 16;
		int x = Math.min(left, window.x + window.width - POP_WIDTH - 6);
		int y = top;
		height = Math.min(height, window.y + window.height - 4 - y);
		return new Rectangle(x, y, POP_WIDTH, height);
	}

	/**
	 * The Filters dropdown: the same filters as the side panel, set from either. A skill (click
	 * again to clear), then the types and conditions: click once to show only those, again to
	 * hide them, again to clear. A click anywhere else in the window closes it.
	 */
	private void drawFilterPop(Graphics2D graphics, Rectangle window, Rectangle pop, int mx, int my)
	{
		List<Hit> popHits = new ArrayList<>();
		Shape old = graphics.getClip();
		graphics.setColor(new Color(0, 0, 0, 90));
		graphics.fillRect(pop.x + 3, pop.y + 3, pop.width, pop.height);
		Skin.stoneFrame(graphics, pop, 0);
		graphics.clip(pop);
		Font small = Skin.small();
		graphics.setFont(small);
		FontMetrics fm = graphics.getFontMetrics();
		int left = pop.x + POP_PAD;
		int inner = pop.width - 2 * POP_PAD;
		int y = pop.y + 6;

		// Title, and Clear when anything is set.
		graphics.setColor(TITLE);
		Skin.text(graphics, "Filters", left, y + 11);
		if (anyFilter())
		{
			String clear = "Clear all";
			Rectangle clearArea = new Rectangle(pop.x + pop.width - POP_PAD - fm.stringWidth(clear) - 4, y, fm.stringWidth(clear) + 4, 14);
			boolean hover = clearArea.contains(mx, my);
			graphics.setColor(hover ? Color.WHITE : NO_LEVEL);
			Skin.text(graphics, clear, clearArea.x + 2, y + 11);
			popHits.add(new Hit(clearArea, () ->
			{
				clearFilters();
				refresh();
				sideChanged();
			}));
		}
		y += 18;

		// Skills, in the Skills tab's order.
		graphics.setColor(LABEL);
		Skin.text(graphics, "SKILL" + (filterSkill != null ? ": " + filterSkill.getName() : ""), left, y + 10);
		y += 14;
		int cellW = inner / SKILL_COLUMNS;
		for (int i = 0; i < SKILL_TAB.length; i++)
		{
			Skill skill = SKILL_TAB[i];
			Rectangle cell = new Rectangle(left + (i % SKILL_COLUMNS) * cellW, y + (i / SKILL_COLUMNS) * SKILL_CELL_H,
				cellW - 2, SKILL_CELL_H - 2);
			boolean picked = skill == filterSkill;
			boolean hover = cell.contains(mx, my);
			Skin.button(graphics, cell, hover, picked);
			BufferedImage icon = skillIcons.getSkillImage(skill, true);
			if (icon != null)
			{
				graphics.drawImage(icon, cell.x + (cell.width - icon.getWidth()) / 2, cell.y + (cell.height - icon.getHeight()) / 2, null);
			}
			if (picked)
			{
				graphics.setColor(TITLE);
				graphics.drawRect(cell.x, cell.y, cell.width - 1, cell.height - 1);
			}
			if (hover)
			{
				tip = skill.getName() + (picked ? " (click to clear)" : "");
			}
			popHits.add(new Hit(cell, () ->
			{
				filterSkill = filterSkill == skill ? null : skill;
				saveFilters();
				refresh();
				sideChanged();
			}));
		}
		y += ((SKILL_TAB.length + SKILL_COLUMNS - 1) / SKILL_COLUMNS) * SKILL_CELL_H + 4;

		// Types, then conditions.
		for (boolean conditions : new boolean[]{false, true})
		{
			graphics.setColor(LABEL);
			Skin.text(graphics, conditions ? "ONLY IF" : "TYPES", left, y + 10);
			y += 14;
			int chipW = (inner - (CHIP_COLUMNS - 1) * 3) / CHIP_COLUMNS;
			int n = 0;
			for (Filter f : Filter.values())
			{
				if (f.condition != conditions)
				{
					continue;
				}
				Rectangle chip = new Rectangle(left + (n % CHIP_COLUMNS) * (chipW + 3), y + (n / CHIP_COLUMNS) * (CHIP_H + 3),
					chipW, CHIP_H);
				boolean on = filterOn.contains(f);
				boolean hidden = filterHidden.contains(f);
				boolean hover = chip.contains(mx, my);
				Skin.button(graphics, chip, hover, on || hidden);
				String label = (on ? "\u2713 " : hidden ? "\u2717 " : "") + f.shortLabel;
				graphics.setColor(on ? new Color(120, 220, 120) : hidden ? new Color(255, 110, 110) : hover ? Color.WHITE : SUBTEXT);
				Skin.text(graphics, Skin.fit(fm, label, chip.width - 6), chip.x + 4, chip.y + 13);
				if (hover)
				{
					tip = f.label + (on ? ": showing only these. Click to hide them" : hidden ? ": hidden. Click to clear"
						: ": click to show only these");
				}
				popHits.add(new Hit(chip, () ->
				{
					toggleFilter(f, filterOn.contains(f) || filterHidden.contains(f));
					sideChanged();
				}));
				n++;
			}
			y += ((n + CHIP_COLUMNS - 1) / CHIP_COLUMNS) * (CHIP_H + 3) + 4;
		}

		graphics.setColor(LABEL);
		Skin.text(graphics, Skin.fit(fm, "Click: show only  \u00B7  again: hide  \u00B7  again: off", inner), left, y + 10);
		graphics.setClip(old);

		// Clicks in the dropdown stay in it; anywhere else in the window closes it.
		popHits.add(new Hit(pop, () ->
		{
		}));
		java.awt.geom.Area outside = new java.awt.geom.Area(window);
		outside.subtract(new java.awt.geom.Area(pop));
		popHits.add(new Hit(outside, () -> filtersOpen = false));
		hits.addAll(0, popHits);
	}

	private void clearFilters()
	{
		filterOn = Collections.emptySet();
		filterHidden = Collections.emptySet();
		filterSkill = null;
		filterTier = null;
		saveFilters();
	}

	/** Tick or untick a filter (show) or its eye (hide). One undoes the other. */
	private void toggleFilter(Filter f, boolean hide)
	{
		Set<Filter> on = EnumSet.noneOf(Filter.class);
		on.addAll(filterOn);
		Set<Filter> hidden = EnumSet.noneOf(Filter.class);
		hidden.addAll(filterHidden);
		Set<Filter> mine = hide ? hidden : on;
		if (!mine.remove(f))
		{
			mine.add(f);
			(hide ? on : hidden).remove(f);
		}
		filterOn = Collections.unmodifiableSet(on);
		filterHidden = Collections.unmodifiableSet(hidden);
		saveFilters();
		refresh();
	}

	/** Filters and sort are shared by the window and side panel, and remembered per account. */
	private void saveFilters()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		configManager.setRSProfileConfiguration(CONFIG_GROUP, "taskFilters", (filterSkill == null ? "" : filterSkill.name())
			+ ";" + (filterTier == null ? "" : filterTier.name()) + ";" + names(filterOn) + ";" + names(filterHidden)
			+ ";" + sortField.name() + ";" + ascending);
	}

	private static String names(Set<Filter> filters)
	{
		return filters.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(","));
	}

	private void loadFilters()
	{
		String raw = configManager.getRSProfileConfiguration(CONFIG_GROUP, "taskFilters");
		String[] p = raw == null ? new String[0] : raw.split(";", -1);
		if (p.length < 6)
		{
			return;
		}
		try
		{
			filterSkill = p[0].isEmpty() ? null : Skill.valueOf(p[0]);
			filterTier = p[1].isEmpty() ? null : TaskCardTier.valueOf(p[1]);
			filterOn = Collections.unmodifiableSet(filterSet(p[2]));
			filterHidden = Collections.unmodifiableSet(filterSet(p[3]));
			sortField = SortField.valueOf(p[4]);
			ascending = Boolean.parseBoolean(p[5]);
		}
		catch (IllegalArgumentException e)
		{
			clearFilters();
		}
	}

	private static Set<Filter> filterSet(String csv)
	{
		Set<Filter> set = EnumSet.noneOf(Filter.class);
		for (String name : csv.split(","))
		{
			if (!name.isEmpty())
			{
				set.add(Filter.valueOf(name));
			}
		}
		return set;
	}

	/** "Mining + Tier 5 + Combat + no Bosses": every active filter. */
	private String filterLabel()
	{
		List<String> parts = new ArrayList<>();
		if (filterSkill != null)
		{
			parts.add(filterSkill.getName());
		}
		if (filterTier != null)
		{
			parts.add(filterTier.getDisplayName());
		}
		filterOn.forEach(f -> parts.add(f.shortLabel));
		filterHidden.forEach(f -> parts.add("no " + f.shortLabel));
		return parts.isEmpty() ? "All" : String.join(" + ", parts);
	}

	private static boolean typeIs(NuzlockeTask task, String... types)
	{
		String type = task.getCompletionType();
		return type != null && Arrays.stream(types).anyMatch(type::equalsIgnoreCase);
	}

	/** The task trains or needs this skill (exact match: Runecrafting isn't Crafting). */
	private static boolean isSkillTask(NuzlockeTask task, Skill skill)
	{
		if (TaskTargetExtras.categorySkill(task.getCategory()) == skill
			|| TaskTargetExtras.requirements(task).containsKey(skill))
		{
			return true;
		}
		String type = task.getCompletionType();
		if (type != null && type.toLowerCase().startsWith(skill.getName().toLowerCase()))
		{
			return true;
		}
		TaskConstraints c = task.getConstraints();
		return c != null && skill.name().equalsIgnoreCase(c.getRequiredSkill());
	}

	private Comparator<NuzlockeTask> sorter(SortField by, boolean asc)
	{
		Comparator<NuzlockeTask> byName = Comparator.comparing(t -> t.getName() == null ? "" : t.getName());
		Comparator<NuzlockeTask> main;
		switch (by)
		{
			case PROGRESS:
				main = Comparator.comparingDouble(TaskTargetExtras::progress);
				break;
			case LEVEL:
				main = Comparator.comparingInt(NuzlockeTask::getLevelRequirement);
				break;
			case CHUNK:
				main = Comparator.comparing((NuzlockeTask t) -> chunkNames.getOrDefault(t.getTaskId(), ""));
				break;
			case NAME:
				main = byName;
				break;
			default:
				main = Comparator.comparingInt(NuzlockeTask::getBasePoints);
				break;
		}
		return (asc ? main : main.reversed()).thenComparing(byName);
	}

	/** The list for the current tab, search, chunk and filters; rebuilt at most once a second. */
	private List<Entry> currentRows()
	{
		long now = System.currentTimeMillis();
		if (now - rowsBuiltAt < ROW_REFRESH_MS)
		{
			return rows;
		}
		rowsBuiltAt = now;
		rowNames.clear(); // levels or readiness may have changed: names re-wrap with the new rows

		String query = search.trim().toLowerCase();
		Set<String> saved = savedIds();
		Set<String> archived = archive.ids();
		Set<String> shown = shownNew;
		Set<Filter> on = filterOn;
		Predicate<NuzlockeTask> test = filterTest(filterOn, filterHidden, filterSkill, filterTier);
		String here = pinnedChunk != null ? pinnedChunk : currentChunkOnly ? currentChunkName() : null;
		// Global tasks (quests, level-ups) join the Active list when asked for, or when searching.
		boolean global = tab == Tab.SAVED || tab == Tab.ARCHIVED || (tab == Tab.ACTIVE && (filterSkill != null
			|| !query.isEmpty() || on.contains(Filter.QUESTS) || on.contains(Filter.PROGRESSION)
			|| on.contains(Filter.OFFLINE)));

		List<NuzlockeTask> list = new ArrayList<>();
		for (NuzlockeTask task : pool(global))
		{
			String id = task.getTaskId();
			chunkNames.computeIfAbsent(id, k -> chunkName(task));
			boolean keep;
			if (tab == Tab.NEW)
			{
				// Just the newly arrived tasks: no search, filter, archive or chunk limits.
				keep = shown.contains(id);
			}
			else if (!query.isEmpty())
			{
				// Searching looks past the filters and the chunk limit until the box is cleared.
				keep = (tab == Tab.ARCHIVED) == archived.contains(id)
					&& (tab != Tab.SAVED || saved.contains(id))
					&& matchesSearch(task, query);
			}
			else
			{
				keep = (tab == Tab.ARCHIVED) == archived.contains(id)
					&& (tab != Tab.SAVED || saved.contains(id))
					&& (tab != Tab.ACTIVE || test.test(task))
					&& (here == null || here.equals(chunkNames.get(id)));
			}
			if (keep)
			{
				list.add(task);
			}
		}
		list.sort(sorter(sortField, ascending));
		rows = group(list);
		return rows;
	}

	/** The skill a level-up task belongs to (e.g. "MINING"), or null for any other task. */
	private static String levelUpSkill(NuzlockeTask task)
	{
		TaskConstraints c = task.getConstraints();
		return typeIs(task, "SKILL_THRESHOLD") && c != null && c.getRequiredSkill() != null
			? c.getRequiredSkill().toUpperCase() : null;
	}

	private static int requiredLevel(NuzlockeTask task)
	{
		return task.getConstraints() == null ? 0 : task.getConstraints().getRequiredLevel();
	}

	/** Fold each skill's level-ups into one header where its first rung sorted; rungs under it. */
	private List<Entry> group(List<NuzlockeTask> sorted)
	{
		Map<String, List<NuzlockeTask>> bySkill = new LinkedHashMap<>();
		for (NuzlockeTask task : sorted)
		{
			String skill = levelUpSkill(task);
			if (skill != null)
			{
				bySkill.computeIfAbsent(skill, k -> new ArrayList<>()).add(task);
			}
		}
		bySkill.values().forEach(m -> m.sort(Comparator.comparingInt(TaskBrowserOverlay::requiredLevel)));

		List<Entry> entries = new ArrayList<>();
		Set<String> placed = new HashSet<>();
		for (NuzlockeTask task : sorted)
		{
			String skill = levelUpSkill(task);
			if (skill == null)
			{
				entries.add(new Entry(task, null, null, false));
			}
			else if (placed.add(skill))
			{
				List<NuzlockeTask> members = bySkill.get(skill);
				entries.add(new Entry(null, skill, members, false));
				if (expandedGroups.contains(skill))
				{
					members.forEach(m -> entries.add(new Entry(m, null, null, true)));
				}
			}
		}
		return entries;
	}

	private String currentChunkName()
	{
		return chunkNameFor(plugin.getCurrentRegionId());
	}

	/** Chunk name without its "(region id)" suffix; "" if it isn't a known chunk. */
	private String chunkNameFor(int regionId)
	{
		String name = plugin.getRegionName(regionId);
		return name == null || name.startsWith("Unknown Region") ? "" : stripRegionId(name);
	}

	private String chunkName(NuzlockeTask task)
	{
		if (plugin.isGlobalTask(task.getTaskId()))
		{
			return "Global";
		}
		String name = plugin.getTaskRegionName(task);
		return name == null ? "" : stripRegionId(name);
	}

	private static String stripRegionId(String name)
	{
		return name.replaceAll("\\s*\\(\\d+\\)$", "").trim();
	}

	private boolean matchesSearch(NuzlockeTask task, String query)
	{
		for (String text : new String[]{task.getName(), task.getDescription(),
			NuzlockeTask.displayCategory(task.getCategory()), chunkNames.get(task.getTaskId())})
		{
			if (text != null && text.toLowerCase().contains(query))
			{
				return true;
			}
		}
		return false;
	}

	private List<String> requirementsFor(NuzlockeTask task)
	{
		return requirements.computeIfAbsent(task.getTaskId(), id -> SelectedTaskOverlay.requirementLines(task));
	}

	/**
	 * Everything under an expanded task: its requirements, where an Obtain or Equip task's
	 * item comes from (source, part, drop rate, cost, then how), then a quest's requirement
	 * sections. The item's wiki page is on its picture.
	 */
	private List<QuestRequirements.Line> detailLines(NuzlockeTask task)
	{
		List<QuestRequirements.Line> lines = new ArrayList<>();
		requirementsFor(task).forEach(line -> lines.add(new QuestRequirements.Line(line, DETAIL)));
		TaskSources.Source source = TaskSources.get(task);
		List<String> sourceLines = TaskSources.lines(task);
		for (int i = 0; i < sourceLines.size(); i++)
		{
			String text = sourceLines.get(i);
			boolean how = source != null && text.equals(source.how);
			Color color = how ? SOURCE_HOW : DETAIL;
			lines.add(i == 0 && !lines.isEmpty() || how && i > 0
				? QuestRequirements.Line.spaced(text, color)
				: new QuestRequirements.Line(text, color));
		}
		if (sourceLines.isEmpty())
		{
			boolean first = true;
			for (String[] item : wikiItems(task))
			{
				lines.add(QuestRequirements.Line.wikiLink("Wiki: " + item[0], WIKI_LINK, item[1],
					first && !lines.isEmpty() ? QuestRequirements.SECTION_GAP : 0));
				first = false;
			}
		}
		lines.addAll(quests.lines(task, this::chunkNameFor));
		return lines;
	}

	/** Whether this is an Obtain or Equip task with items to link to the wiki. */
	private static boolean isItemTask(NuzlockeTask task)
	{
		String type = task == null || task.getCompletionType() == null ? "" : task.getCompletionType().toUpperCase();
		return (type.equals("OBTAIN") || type.equals("EQUIP"))
			&& task.getRequiredItems() != null && !task.getRequiredItems().isEmpty();
	}

	/**
	 * An Obtain or Equip task's items as {name, OSRS Wiki link}, one per item (its first id
	 * picks the page); empty for any other task. Item names are looked up on the client
	 * thread, where overlays draw.
	 */
	private List<String[]> wikiItems(NuzlockeTask task)
	{
		List<String[]> items = new ArrayList<>();
		if (!isItemTask(task))
		{
			return items;
		}
		for (RequiredItem item : task.getRequiredItems())
		{
			if (item == null)
			{
				continue;
			}
			Integer id = item.getItemIds() == null || item.getItemIds().isEmpty() ? null : item.getItemIds().get(0);
			String name = item.getItem();
			if ((name == null || name.trim().isEmpty()) && id != null && itemManager != null)
			{
				name = itemManager.getItemComposition(id).getName();
			}
			if (name == null || name.trim().isEmpty() || "null".equalsIgnoreCase(name.trim()))
			{
				continue;
			}
			name = name.trim();
			String url = id != null
				? "https://oldschool.runescape.wiki/w/Special:Lookup?type=item&id=" + id
				: "https://oldschool.runescape.wiki/?title=Special:Search&go=Go&search="
					+ name.replace("&", "%26").replace(' ', '+');
			items.add(new String[]{name, url});
		}
		return items;
	}

	// Finished task ids for the header's count, read at most every two seconds.
	private Set<String> doneIds = Collections.emptySet();
	private long doneAt;

	private Set<String> doneIds()
	{
		long now = System.currentTimeMillis();
		if (now - doneAt > 2000)
		{
			doneAt = now;
			doneIds = plugin.getCompletedTaskIdSet();
		}
		return doneIds;
	}

	/** Level checks, real requirements (TaskTargetExtras) and, for quests, their requirements. */
	private boolean canDo(NuzlockeTask task)
	{
		return TaskTargetExtras.canDo(client, plugin, task) && quests.isReady(task);
	}

	/** "(20 Defence, 20 Ranged)", "(Lvl 40)" or "(Not ready)" for a task you can't do yet. */
	private String levelNote(NuzlockeTask task)
	{
		String note = TaskTargetExtras.levelNote(client, plugin, task);
		return note != null ? note : "(Not ready)";
	}

	// --- Drawing --------------------------------------------------------------

	@Override
	public Dimension render(Graphics2D graphics)
	{
		listRowId = null;
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		if (questPane == null)
		{
			paneSide = null;
			paneIn = false;
			paneArea = null;
		}
		if (!open && questPane != null && loggedIn)
		{
			shownArea = null;
			drawSidePane(graphics);
			return null;
		}
		if (!open || !loggedIn)
		{
			hoveredAction = null;
			mouseInWindow = false;
			shownArea = null;
			if (!open && loggedIn && !newIds.isEmpty())
			{
				drawNewTaskAlert(graphics);
			}
			return null;
		}

		int mx = paneMouse.x;
		int my = paneMouse.y;
		hits.clear();

		// Walked into another chunk: rebuild now if the list follows you.
		int region = plugin.getCurrentRegionId();
		if (region != lastRegionSeen)
		{
			lastRegionSeen = region;
			if (currentChunkOnly)
			{
				refresh();
			}
		}

		// Centred over the game view; in fixed mode that's always the 512 x 334 box at the top left.
		Rectangle view = client.isResized() ? new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(),
			client.getViewportWidth(), client.getViewportHeight()) : new Rectangle(4, 4, 512, 334);
		int width = Math.min(MAX_WIDTH, view.width - 20);
		int height = Math.min(MAX_HEIGHT, view.height - 20);
		int x = view.x + (view.width - width) / 2;
		int y = view.y + (view.height - height) / 2;
		if (moved)
		{
			x = Math.max(0, Math.min(customX, client.getCanvasWidth() - width - 1));
			y = Math.max(0, Math.min(customY, client.getCanvasHeight() - height - 1));
		}
		windowX = x;
		windowY = y;
		Rectangle window = new Rectangle(x, y, width, height);

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Font bold = Skin.bold();
		Font regular = Skin.regular();
		Font small = Skin.small();

		// The window: a soft shadow, a rounded body darkening downwards, a lighter header
		// (Classic; Old School's stone frame comes from Skin).
		Skin.stoneFrame(graphics, window, HEADER);

		// Header: the ChunkBlazer icon and title (the drag handle), the account's numbers, close.
		BufferedImage icon = logo();
		int titleX = x + PAD + (icon != null ? 22 : 0);
		if (icon != null)
		{
			graphics.drawImage(icon, x + PAD, y + (HEADER - 16) / 2, null);
		}
		graphics.setFont(bold);
		FontMetrics bm = graphics.getFontMetrics();
		titleHovered = new Rectangle(x, y, titleX - x + bm.stringWidth("ChunkBlazer Tasks") + 10, HEADER).contains(mx, my);
		graphics.setColor(titleHovered || dragging ? Color.WHITE : TITLE);
		Skin.text(graphics, "ChunkBlazer", titleX, y + 25);
		graphics.setColor(new Color(255, 255, 0));
		Skin.text(graphics, "Tasks", titleX + bm.stringWidth("ChunkBlazer "), y + 25);
		// The Patch notes tab, beside the title.
		graphics.setFont(Skin.small());
		String notesLabel = "Patch notes";
		Rectangle notesTab = new Rectangle(titleX + bm.stringWidth("ChunkBlazer Tasks") + 14, y + 11,
			graphics.getFontMetrics().stringWidth(notesLabel) + 16, 18);
		hits.add(new Hit(notesTab, () ->
		{
			patchNotes = !patchNotes;
			settingsOpen = false;
			filtersOpen = false;
			sortOpen = false;
			patchScroll = 0;
		}));
		boolean notesHover = notesTab.contains(mx, my);
		Skin.button(graphics, notesTab, notesHover, patchNotes);
		graphics.setColor(patchNotes || notesHover ? Color.WHITE : SUBTEXT);
		Skin.text(graphics, notesLabel, notesTab.x + 8, notesTab.y + 13);
		graphics.setFont(bold);
		Rectangle close = new Rectangle(x + width - 30, y + 10, 20, 20);
		closeBounds = close;
		hits.add(new Hit(close, this::close));
		Skin.close(graphics, close, close.contains(mx, my));
		graphics.setFont(small);
		FontMetrics sm = graphics.getFontMetrics();
		long now = System.currentTimeMillis();
		if (now - statsAt > 1000)
		{
			statsAt = now;
			statDone = doneIds().size();
			statChunks = paidChunks();
			countTiers();
		}
		int chipX = close.x - 6;
		for (String[] stat : new String[][]{{statChunks + "", "chunks"}, {statDone + "", "done"},
			{plugin.getTotalPoints() + "", "pts"}})
		{
			chipX -= statChip(graphics, chipX, y + 11, stat[0], stat[1]) + 5;
		}

		// Down the left: the tabs, then a quick tier filter and the current-chunk switch.
		// (A little narrower in the fixed-size client.)
		Rectangle side = new Rectangle(x + 1, y + HEADER + 1, width < 540 ? SIDEBAR - 20 : SIDEBAR, height - HEADER - 2);
		// A sunken stone column, inside the frame.
		Rectangle column = new Rectangle(side.x + 3, side.y + 1, side.width - 3, side.height - 4);
		graphics.setColor(SIDEBAR_FILL);
		graphics.fillRect(column.x, column.y, column.width, column.height);
		Skin.bevel(graphics, column, false);
		int sy = side.y + 10;
		graphics.setFont(small);
		graphics.setColor(LABEL);
		Skin.text(graphics, "LISTS", side.x + PAD + 2, sy + 8);
		sy += 14;
		List<Object[]> tabs = new ArrayList<>();
		if (tab == Tab.NEW)
		{
			tabs.add(new Object[]{Tab.NEW, "New tasks", shownNew.size()});
		}
		else
		{
			Set<String> archived = archive.ids();
			Set<String> saved = savedIds();
			tabs.add(new Object[]{Tab.ACTIVE, "Active", count(false, id -> !archived.contains(id))});
			tabs.add(new Object[]{Tab.SAVED, "Saved", count(true, id -> saved.contains(id) && !archived.contains(id))});
			tabs.add(new Object[]{Tab.ARCHIVED, "Archived", count(true, archived::contains)});
		}
		int chosenY = sy;
		activeTabBounds = null;
		for (Object[] t : tabs)
		{
			if (t[0] == tab)
			{
				chosenY = sy;
			}
			if (t[0] == Tab.ACTIVE)
			{
				activeTabBounds = new Rectangle(side.x + 6, sy, side.width - 12, 24);
			}
			drawTab(graphics, new Rectangle(side.x + 6, sy, side.width - 12, 24), (String) t[1], (Integer) t[2],
				(Tab) t[0], mx, my);
			sy += 27;
		}
		// Settings, under the lists.
		sy += 4;
		Rectangle settingsTab = new Rectangle(side.x + 6, sy, side.width - 12, 24);
		hits.add(new Hit(settingsTab, () ->
		{
			settingsOpen = true;
			filtersOpen = false;
			sortOpen = false;
			patchNotes = false;
			patchScroll = 0;
		}));
		Skin.button(graphics, settingsTab, settingsTab.contains(mx, my), settingsOpen);
		graphics.setFont(Skin.regular());
		graphics.setColor(settingsOpen || settingsTab.contains(mx, my) ? Color.WHITE : SUBTEXT);
		Skin.text(graphics, "Settings", settingsTab.x + 12,
			settingsTab.y + (settingsTab.height + graphics.getFontMetrics().getAscent()) / 2 - 2);
		graphics.setFont(small);
		if (settingsOpen)
		{
			chosenY = sy;
		}
		sy += 27;
		// The marker slides to the chosen tab.
		tabMarkerY = tabMarkerY < 0 ? chosenY : tabMarkerY + (chosenY - tabMarkerY) * 0.3f;
		graphics.setColor(TITLE);
		graphics.fillRect(side.x + 6, Math.round(tabMarkerY) + 4, 3, 16);

		if (tab == Tab.ACTIVE && !settingsOpen && !patchNotes)
		{
			sy += 10;
			graphics.setFont(small);
			graphics.setColor(LABEL);
			Skin.text(graphics, "TIERS", side.x + PAD + 2, sy + 8);
			sy += 13;
			for (TaskCardTier tier : TaskCardTier.values())
			{
				Rectangle row = new Rectangle(side.x + 6, sy, side.width - 12, 19);
				boolean on = filterTier == tier;
				boolean hover = row.contains(mx, my);
				hits.add(new Hit(row, () ->
				{
					filterTier = filterTier == tier ? null : tier;
					saveFilters();
					refresh();
					sideChanged();
				}));
				if (on || hover)
				{
					graphics.setColor(on ? new Color(tier.getAccent().getRed(), tier.getAccent().getGreen(),
						tier.getAccent().getBlue(), 60) : CARD_HOVER);
					graphics.fillRect(row.x, row.y, row.width, row.height);
				}
				graphics.setColor(tier.getAccent());
				graphics.setColor(Color.BLACK);
				graphics.fillRect(row.x + 6, row.y + 5, 10, 10);
				graphics.setColor(tier.getAccent());
				graphics.fillRect(row.x + 7, row.y + 6, 8, 8);
				graphics.setColor(tier.getAccent().brighter());
				graphics.fillRect(row.x + 7, row.y + 6, 3, 2);
				graphics.setColor(on || hover ? Color.WHITE : SUBTEXT);
				Skin.text(graphics, tier.getDisplayName(), row.x + 21, row.y + 13);
				String n = String.valueOf(tierCounts.getOrDefault(tier, 0));
				graphics.setColor(on ? Color.WHITE : LABEL);
				Skin.text(graphics, n, row.x + row.width - 6 - sm.stringWidth(n), row.y + 13);
				sy += 20;
			}
		}
		// The current-chunk switch (or the chunk picked on the map) sits at the bottom.
		String chunkLabel = pinnedChunk != null ? Skin.fit(sm, pinnedChunk, side.width - 40) : side.width < SIDEBAR ? "This chunk" : "This chunk only";
		drawChunkToggle(graphics, new Rectangle(side.x + 6, side.y + side.height - 30, side.width - 12, 22), chunkLabel, mx, my);

		// The content: the list, then a hint line.
		int cx = side.x + side.width + PAD;
		int cw = x + width - PAD - cx;

		// Old School: column headings over the list, like the Leagues task table.
		// The headings carry the search box, Filters and Sort too, so the strip is a little taller.
		int columns = patchNotes || settingsOpen ? 0 : 24;
		Rectangle filtersTab = null;
		Rectangle sortTab = null;
		if (columns > 0)
		{
			Rectangle strip = new Rectangle(cx - 2, y + HEADER + 6, cw + 6, columns);
			graphics.setColor(DARK);
			graphics.fillRect(strip.x, strip.y, strip.width, strip.height);
			Skin.bevel(graphics, strip, true);
			graphics.setFont(small);
			FontMetrics stripFm = graphics.getFontMetrics();
			int textY = strip.y + strip.height / 2 + 5;
			graphics.setColor(TITLE);
			Skin.text(graphics, "Task", strip.x + 52, textY);
			int pointsX = strip.x + strip.width - 12 - stripFm.stringWidth("Points");
			Skin.text(graphics, "Points", pointsX, textY);
			int tabY = strip.y + (strip.height - 18) / 2;

			// Sort, just left of Points: what the list is sorted by, opening the choices. Short,
			// so "Level needed" shows as "Level".
			String sortLabel = "Sort: " + sortField.label.replace(" needed", "") + (ascending ? " \u25B2" : " \u25BC");
			int sortW = stripFm.stringWidth(sortLabel) + 16;
			sortTab = new Rectangle(pointsX - 12 - sortW, tabY, sortW, 18);
			hits.add(new Hit(sortTab, () ->
			{
				sortOpen = !sortOpen;
				filtersOpen = false;
			}));
			boolean sortHover = sortTab.contains(mx, my);
			Skin.button(graphics, sortTab, sortHover, sortOpen);
			graphics.setColor(sortOpen || sortHover ? Color.WHITE : SUBTEXT);
			Skin.text(graphics, sortLabel, sortTab.x + 8, sortTab.y + 13);

			// Filters beside it, showing how many are set.
			int setCount = filterCount();
			String filtersLabel = setCount > 0 ? "Filters (" + setCount + ")" : "Filters";
			int filtersW = stripFm.stringWidth(filtersLabel) + 16;
			filtersTab = new Rectangle(sortTab.x - 6 - filtersW, tabY, filtersW, 18);
			hits.add(new Hit(filtersTab, () ->
			{
				filtersOpen = !filtersOpen;
				sortOpen = false;
			}));
			boolean filtersHover = filtersTab.contains(mx, my);
			Skin.button(graphics, filtersTab, filtersHover, filtersOpen);
			graphics.setColor(filtersOpen || filtersHover ? Color.WHITE : setCount > 0 ? new Color(120, 220, 120) : SUBTEXT);
			Skin.text(graphics, filtersLabel, filtersTab.x + 8, filtersTab.y + 13);

			// The search box fills the space between Task and Filters.
			int searchX = strip.x + 52 + stripFm.stringWidth("Task") + 12;
			drawSearchBox(graphics, new Rectangle(searchX, tabY, Math.max(40, filtersTab.x - 8 - searchX), 18), mx, my);
		}
		else
		{
			searchBox = null;
			searchClear = null;
			filtersOpen = false;
			sortOpen = false;
		}
		Rectangle list = new Rectangle(cx - 2, y + HEADER + 8 + columns, cw + 6, height - HEADER - 8 - FOOTER - 2 - columns);
		listBounds = list;
		rowSpots.clear();
		firstRowId = null;
		if (patchNotes || settingsOpen)
		{
			String footer = "Click Patch notes again, or a list, to go back to your tasks";
			if (settingsOpen)
			{
				String hovered = drawSettingsPage(graphics, list, mx, my);
				footer = hovered != null ? hovered
					: "Hover a setting to see what it does. Colours and keys are in RuneLite's plugin settings.";
			}
			else
			{
				drawPatchNotes(graphics, list, regular, small);
			}
			graphics.setFont(small);
			graphics.setColor(settingsOpen && !footer.startsWith("Hover") ? Color.WHITE : LABEL);
			Skin.text(graphics, Skin.fit(graphics.getFontMetrics(), footer, x + width - PAD - cx), cx, y + height - 7);
			finishHover(window, mx, my);
			shownArea = new Rectangle(x, y, width, height);
			if (questPane != null)
			{
				drawSidePane(graphics);
			}
			else
			{
				drawTip(graphics, mx, my, 0, 0);
			}
			return null;
		}
		// The Filters or Sort dropdown sits over the list: rows under it don't take the mouse.
		int popTop = y + HEADER + 6 + columns + 2;
		Rectangle pop = filtersOpen && filtersTab != null ? filterPopBounds(window, filtersTab.x, popTop)
			: sortOpen && sortTab != null ? sortPopBounds(window, sortTab.x, popTop) : null;
		boolean overPop = pop != null && pop.contains(mx, my);
		int shown = drawList(graphics, list, overPop ? -1 : mx, overPop ? -1 : my, regular, small);

		// The hint line: how many, and what clicks do.
		graphics.setFont(small);
		graphics.setColor(LABEL);
		int footY = y + height - 7;
		String countLabel = shown + (shown == 1 ? " task" : " tasks");
		Skin.text(graphics, countLabel, cx, footY);
		// When something narrows the list, say what, with a link that shows everything again.
		List<String> narrowing = new ArrayList<>();
		if (!search.trim().isEmpty())
		{
			narrowing.add("search");
		}
		if (anyFilter())
		{
			narrowing.add("filters");
		}
		if (pinnedChunk != null || currentChunkOnly)
		{
			narrowing.add("this chunk");
		}
		if (!narrowing.isEmpty() && tab != Tab.NEW)
		{
			String why = " (" + String.join(", ", narrowing) + ")  ";
			int whyX = cx + sm.stringWidth(countLabel);
			Skin.text(graphics, why, whyX, footY);
			String showAll = "Show all";
			Rectangle showAllArea = new Rectangle(whyX + sm.stringWidth(why), footY - 11, sm.stringWidth(showAll), 14);
			boolean showAllHover = showAllArea.contains(mx, my);
			graphics.setColor(showAllHover ? Color.WHITE : TITLE);
			Skin.text(graphics, showAll, showAllArea.x, footY);
			graphics.drawLine(showAllArea.x, footY + 1, showAllArea.x + showAllArea.width, footY + 1);
			hits.add(new Hit(showAllArea, this::showAll));
			graphics.setColor(LABEL);
		}
		// Each control on its own: the key in the title colour, what it does in the label colour,
		// with a divider between, so it doesn't read as one long sentence.
		String[][] hints = {{"Click", "track"}, {"Right-click", "save"}, {"Arrow", "details"}};
		String gap = "   |   ";
		int hintX = x + width - PAD;
		for (int i = hints.length - 1; i >= 0; i--)
		{
			String action = " " + hints[i][1];
			hintX -= sm.stringWidth(action);
			graphics.setColor(LABEL);
			Skin.text(graphics, action, hintX, footY);
			hintX -= sm.stringWidth(hints[i][0]);
			graphics.setColor(TITLE);
			Skin.text(graphics, hints[i][0], hintX, footY);
			if (i > 0)
			{
				hintX -= sm.stringWidth(gap);
				graphics.setColor(LABEL.darker());
				Skin.text(graphics, gap, hintX, footY);
			}
		}

		if (pop != null && filtersOpen)
		{
			drawFilterPop(graphics, window, pop, mx, my);
		}
		else if (pop != null)
		{
			drawSortPop(graphics, window, pop, mx, my);
		}
		filterPop = pop;

		finishHover(window, mx, my);
		shownArea = new Rectangle(x, y, width, height);
		if (questPane != null)
		{
			// A quest opened from the window shows beside it; the window stays open.
			drawSidePane(graphics);
		}
		else
		{
			drawTip(graphics, mx, my, 0, 0);
		}
		return null;
	}

	/** The quest/saved box over the side panel. Anything that moves the side panel out from under it closes it. */
	private void drawSidePane(Graphics2D graphics)
	{
		Rectangle side = sidePanel();
		if (paneSide != null && !paneSide.equals(side))
		{
			questPane = null;
		}
		else if (!config.movableTaskBox())
		{
			drawQuestPane(graphics, side, 0, 0);
		}
		paneSide = side;
	}

	/** A header number with its label ("123 pts"), right-aligned to {@code right}; returns its width. */
	private static int statChip(Graphics2D graphics, int right, int y, String value, String label)
	{
		FontMetrics fm = graphics.getFontMetrics();
		int w = fm.stringWidth(value) + fm.stringWidth(label) + 16;
		graphics.setColor(FIELD);
		graphics.fillRect(right - w, y, w, 18);
		Skin.bevel(graphics, new Rectangle(right - w, y, w, 18), false);
		graphics.setColor(POINTS);
		Skin.text(graphics, value, right - w + 6, y + 13);
		graphics.setColor(SUBTEXT);
		Skin.text(graphics, label, right - w + 10 + fm.stringWidth(value), y + 13);
		return w;
	}

	/** Active tasks per tier, for the sidebar's tier filter. */
	private void countTiers()
	{
		tierCounts.clear();
		Set<String> archived = archive.ids();
		for (NuzlockeTask task : pool(false))
		{
			if (!archived.contains(task.getTaskId()))
			{
				tierCounts.merge(TaskCardTier.fromTask(task), 1, Integer::sum);
			}
		}
	}

	// --- Settings tab -----------------------------------------------------------

	private static final int SETTINGS_ROW = 22;

	/**
	 * One line of the Settings tab: a section heading (no key), a toggle, a choice (click
	 * for the next one) or a number (- and +). Keys are RuneLite's config keys, so they
	 * keep working everywhere the plugin reads its config.
	 */
	private static final class Setting
	{
		final String label;
		final String description;
		final String key;
		final Supplier<Object> current;
		final Object[] choices;
		final int min;
		final int max;
		final int step;

		Setting(String label, String description, String key, Supplier<Object> current, Object[] choices,
			int min, int max, int step)
		{
			this.label = label;
			this.description = description;
			this.key = key;
			this.current = current;
			this.choices = choices;
			this.min = min;
			this.max = max;
			this.step = step;
		}
	}

	private static Setting heading(String label)
	{
		return new Setting(label, null, null, null, null, 0, 0, 0);
	}

	private static Setting toggle(String label, String key, Supplier<Object> current, String description)
	{
		return new Setting(label, description, key, current, null, 0, 0, 0);
	}

	private static Setting choice(String label, String key, Supplier<Object> current, Object[] choices, String description)
	{
		return new Setting(label, description, key, current, choices, 0, 0, 0);
	}

	private static Setting number(String label, String key, Supplier<Object> current, int min, int max, int step,
		String description)
	{
		return new Setting(label, description, key, current, null, min, max, step);
	}

	/** Every in-game UI setting, in sections. Colours, keybinds and server sync stay in RuneLite's panel. */
	private List<Setting> settings()
	{
		if (settings == null)
		{
			settings = Arrays.asList(
				heading("Tasks"),
				toggle("Reveal new tasks as cards", "showTaskCards", config::showTaskCards,
					"New tasks arrive as face-down cards you flip to reveal."),
				choice("Card entrance", "cardEntrance", config::cardEntrance, CardEntrance.values(),
					"How a fresh deal of task cards arrives on screen. Click to change."),
				choice("Task box", "taskTrackerStyle", config::taskTrackerStyle, TaskTrackerStyle.values(),
					"How the task you track is shown in game. Off hides it. Click to change."),
				toggle("Auto-track tasks", "autoTrackTasks", config::autoTrackTasks,
					"Using an NPC or object a task needs tracks that task. A task you picked yourself is kept."),
				toggle("Saved tasks bubble", "showSavedTaskTracker", config::showSavedTaskTracker,
					"A bubble that opens your saved tasks (Alt + drag to move)."),
				toggle("Movable quest/saved box", "movableTaskBox", config::movableTaskBox,
					"Open quest requirements and saved tasks in a box you can Alt + drag, instead of over the inventory."),
				toggle("Right-click Tasks menu", "taskRightClickMenu", config::taskRightClickMenu,
					"Adds a Tasks submenu when right-clicking NPCs and objects your tasks need."),
				choice("Task tooltip key", "taskTooltipKey", config::taskTooltipKey, TaskTooltipKey.values(),
					"Hold this key over an NPC or object to list its tasks in a tooltip. Click to change."),
				choice("Outlines", "taskOutlineMode", config::taskOutlineMode, OutlineMode.values(),
					"Which task NPCs and objects get outlined: all, saved, doable now, or none. Click to change."),
				toggle("Highlight task items", "highlightEquipItems", config::highlightEquipItems,
					"Outline items your tasks need (gear, tools) in your inventory, bank and shops."),
				heading("World map"),
				toggle("Chunk borders", "showWorldMapChunks", config::showWorldMapChunks,
					"Draw chunk borders and tints on the world map."),
				toggle("Chunk costs", "showChunkCostLabels", config::showChunkCostLabels,
					"Show each unlockable chunk's cost (free, points or a Boss Token) inside it."),
				toggle("Colour legend", "showChunkLegend", config::showChunkLegend,
					"Show a key explaining the chunk colours in the corner of the world map."),
				toggle("Lines between unlocked", "showChunkGridLines", config::showChunkGridLines,
					"Outline each unlocked chunk. Off shows your unlocked area as one piece."),
				heading("Minimap"),
				toggle("Chunk borders & tints", "showMinimapChunks", config::showMinimapChunks,
					"Chunk borders and tints on the minimap. Click a neighbouring chunk to unlock it."),
				toggle("Minimap orbs", "showMinimapOrbs", config::showMinimapOrbs,
					"The Boss Token and Points orbs. Turned off, open this window again from RuneLite's plugin settings."),
				heading("In game"),
				toggle("Chunk borders", "showSceneChunks", config::showSceneChunks,
					"Draw chunk borders on the ground in the game world."),
				toggle("Locked chunk walls", "showChunkWalls", config::showChunkWalls,
					"A see-through wall along the border between unlocked and locked chunks."),
				toggle("Chunk name banner", "showChunkNamePopups", config::showChunkNamePopups,
					"Show a chunk's name at the top of the screen when you enter it."),
				toggle("Task completion popup", "showTaskCompletionPopup", config::showTaskCompletionPopup,
					"Display a popup when you complete a task."),
				toggle("Unlock popup", "showUnlockPopup", config::showUnlockPopup,
					"Show an in-game popup to unlock regions when you walk into them."),
				heading("Sounds"),
				toggle("Task completion sound", "playTaskCompletionSound", config::playTaskCompletionSound,
					"Play a region-specific sound when you complete a task."),
				number("Task sound volume", "taskCompletionSoundVolume", config::taskCompletionSoundVolume, 0, 100, 10,
					"Volume of the task completion sound (0 = silent, 100 = full)."),
				toggle("Region unlock jingle", "playRegionUnlockSound", config::playRegionUnlockSound,
					"Play a region-specific jingle the first time you unlock a chunk."),
				heading("Chat messages"),
				toggle("Task progress", "showChatProgress", config::showChatProgress,
					"Show messages when you make progress on a task."),
				toggle("Task success", "showChatSuccess", config::showChatSuccess,
					"Show messages when you complete a task."),
				toggle("Task failed", "showChatFailed", config::showChatFailed,
					"Show messages when a task attempt fails."));
		}
		return settings;
	}

	/**
	 * The Settings tab in the list's place: its sections in one column, or two when there's
	 * room, scrolling. Returns the hovered setting's description (for the footer), or null.
	 */
	private String drawSettingsPage(Graphics2D graphics, Rectangle area, int mx, int my)
	{
		graphics.setFont(Skin.small());
		FontMetrics fm = graphics.getFontMetrics();
		List<Setting> all = settings();
		// Split into sections at each heading, then share them between the columns.
		List<List<Setting>> sections = new ArrayList<>();
		for (Setting setting : all)
		{
			if (setting.key == null || sections.isEmpty())
			{
				sections.add(new ArrayList<>());
			}
			sections.get(sections.size() - 1).add(setting);
		}
		boolean two = area.width >= 440;
		int columnWidth = two ? (area.width - 30) / 2 : area.width - 20;
		List<List<List<Setting>>> columns = new ArrayList<>();
		columns.add(new ArrayList<>());
		if (two)
		{
			columns.add(new ArrayList<>());
			// Fill the left column to about half the rows, the rest on the right.
			int half = (all.size() + sections.size()) / 2;
			int count = 0;
			for (List<Setting> section : sections)
			{
				columns.get(count < half ? 0 : 1).add(section);
				count += section.size() + 1;
			}
		}
		else
		{
			columns.get(0).addAll(sections);
		}
		int contentHeight = 0;
		for (List<List<Setting>> column : columns)
		{
			contentHeight = Math.max(contentHeight, 8 + column.stream().mapToInt(sec -> sec.size() * SETTINGS_ROW + 8).sum());
		}
		patchScroll = Math.max(0, Math.min(patchScroll, Math.max(0, contentHeight - area.height)));

		Shape oldClip = graphics.getClip();
		graphics.clip(area);
		graphics.setColor(new Color(0, 0, 0, 40));
		graphics.fillRect(area.x, area.y, area.width, area.height);
		boolean in = area.contains(mx, my);
		String hovered = null;
		for (int c = 0; c < columns.size(); c++)
		{
			int colX = area.x + 8 + c * (columnWidth + 10);
			int rowY = area.y + 6 - patchScroll;
			for (List<Setting> section : columns.get(c))
			{
				for (Setting setting : section)
				{
					Rectangle row = new Rectangle(colX, rowY, columnWidth, SETTINGS_ROW);
					if (setting.key == null)
					{
						graphics.setColor(TITLE);
						Skin.text(graphics, setting.label.toUpperCase(), colX + 2, rowY + SETTINGS_ROW - 7);
						Skin.divider(graphics, colX, rowY + SETTINGS_ROW - 2, colX + columnWidth);
					}
					else
					{
						boolean hover = in && row.contains(mx, my);
						if (hover)
						{
							hovered = setting.description;
						}
						drawSetting(graphics, fm, row, setting, in ? mx : -1, my, area);
					}
					rowY += SETTINGS_ROW;
				}
				rowY += 8;
			}
		}
		graphics.setClip(oldClip);
		drawScrollbar(graphics, area, contentHeight, patchScroll);
		return hovered;
	}

	/** One setting row: its label, then a switch, a choice button or - value +, at the right. */
	private void drawSetting(Graphics2D graphics, FontMetrics fm, Rectangle row, Setting setting, int mx, int my,
		Rectangle clip)
	{
		boolean hover = row.contains(mx, my);
		if (hover)
		{
			graphics.setColor(CARD_HOVER);
			graphics.fillRect(row.x, row.y + 1, row.width, row.height - 2);
		}
		int textY = row.y + (row.height + fm.getAscent()) / 2 - 1;
		Object value = setting.current.get();
		int right = row.x + row.width - 6;
		int controlWidth;
		if (setting.choices != null)
		{
			int widest = 0;
			for (Object choice : setting.choices)
			{
				widest = Math.max(widest, fm.stringWidth(String.valueOf(choice)));
			}
			controlWidth = Math.min(widest + 16, row.width / 2);
			Rectangle button = new Rectangle(right - controlWidth, row.y + 3, controlWidth, row.height - 6);
			int index = Arrays.asList(setting.choices).indexOf(value);
			Object next = setting.choices[(index + 1) % setting.choices.length];
			if (mx >= 0)
			{
				hits.add(new Hit(row.intersection(clip), () -> configManager.setConfiguration(CONFIG_GROUP, setting.key, next)));
			}
			Skin.button(graphics, button, hover, false);
			graphics.setColor(hover ? Color.WHITE : TITLE);
			String text = Skin.fit(fm, String.valueOf(value), controlWidth - 8);
			Skin.text(graphics, text, button.x + (button.width - fm.stringWidth(text)) / 2, textY);
		}
		else if (setting.step > 0)
		{
			int current = value instanceof Integer ? (Integer) value : setting.min;
			controlWidth = 74;
			Rectangle minus = new Rectangle(right - controlWidth, row.y + 3, 18, row.height - 6);
			Rectangle plus = new Rectangle(right - 18, row.y + 3, 18, row.height - 6);
			if (mx >= 0)
			{
				hits.add(new Hit(minus.intersection(clip), () -> configManager.setConfiguration(CONFIG_GROUP, setting.key,
					Math.max(setting.min, current - setting.step))));
				hits.add(new Hit(plus.intersection(clip), () -> configManager.setConfiguration(CONFIG_GROUP, setting.key,
					Math.min(setting.max, current + setting.step))));
			}
			for (Rectangle b : new Rectangle[]{minus, plus})
			{
				boolean bh = b.contains(mx, my);
				Skin.button(graphics, b, bh, false);
				graphics.setColor(bh ? Color.WHITE : TITLE);
				String sign = b == minus ? "-" : "+";
				Skin.text(graphics, sign, b.x + (b.width - fm.stringWidth(sign)) / 2, textY);
			}
			String n = String.valueOf(current);
			graphics.setColor(POINTS);
			Skin.text(graphics, n, minus.x + minus.width + (controlWidth - 36 - fm.stringWidth(n)) / 2, textY);
		}
		else
		{
			boolean on = Boolean.TRUE.equals(value);
			controlWidth = 22;
			if (mx >= 0)
			{
				hits.add(new Hit(row.intersection(clip), () -> configManager.setConfiguration(CONFIG_GROUP, setting.key, !on)));
			}
			// The same little switch as "This chunk only": the knob slides right and goes orange when on.
			int sx = right - controlWidth;
			int sy = row.y + (row.height - 12) / 2;
			graphics.setColor(on ? new Color(255, 152, 31, 150) : FIELD);
			graphics.fillRect(sx, sy, controlWidth, 12);
			graphics.setColor(on ? Color.WHITE : SUBTEXT);
			graphics.fillOval(on ? sx + controlWidth - 11 : sx + 1, sy + 1, 10, 10);
		}
		graphics.setColor(hover ? Color.WHITE : SUBTEXT);
		Skin.text(graphics, Skin.fit(fm, setting.label, row.width - controlWidth - 18), row.x + 6, textY);
	}

	/** patch_notes.txt, read once: "## " starts a version, "- " a note. Missing just means none. */
	private static List<String> patchNoteLines()
	{
		if (patchNoteLines == null)
		{
			List<String> lines = new ArrayList<>();
			try (java.io.InputStream in = TaskBrowserOverlay.class.getResourceAsStream("patch_notes.txt"))
			{
				if (in != null)
				{
					java.io.BufferedReader reader = new java.io.BufferedReader(
						new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
					for (String line = reader.readLine(); line != null; line = reader.readLine())
					{
						lines.add(line);
					}
				}
			}
			catch (java.io.IOException e)
			{
				lines.clear();
			}
			patchNoteLines = lines;
		}
		return patchNoteLines;
	}

	/** The patch notes in the list's place: version headings and wrapped bullet points, scrolling. */
	private void drawPatchNotes(Graphics2D graphics, Rectangle area, Font regular, Font small)
	{
		FontMetrics rm = graphics.getFontMetrics(regular);
		FontMetrics sm = graphics.getFontMetrics(small);
		int textWidth = area.width - 40;
		// Lay out first, to know the height for scrolling.
		List<Object[]> parts = new ArrayList<>(); // {text, font, colour, x offset, line height}
		for (String raw : patchNoteLines())
		{
			String line = raw.trim();
			if (line.isEmpty())
			{
				parts.add(new Object[]{"", small, SUBTEXT, 0, 8});
			}
			else if (line.startsWith("## "))
			{
				parts.add(new Object[]{line.substring(3), regular, TITLE, 0, rm.getHeight() + 4});
			}
			else
			{
				boolean bullet = line.startsWith("- ");
				boolean first = true;
				for (String part : wrap(sm, bullet ? line.substring(2) : line, textWidth - 12))
				{
					parts.add(new Object[]{(bullet && first ? "\u2022 " : "") + part, small, Color.WHITE,
						bullet && !first ? 20 : 12, DETAIL_LINE});
					first = false;
				}
			}
		}
		int contentHeight = 12;
		for (Object[] part : parts)
		{
			contentHeight += (Integer) part[4];
		}
		if (parts.isEmpty())
		{
			graphics.setFont(regular);
			graphics.setColor(SUBTEXT);
			drawCentered(graphics, "No patch notes yet.", area);
			return;
		}
		patchScroll = Math.max(0, Math.min(patchScroll, Math.max(0, contentHeight - area.height)));
		Shape oldClip = graphics.getClip();
		graphics.clip(area);
		graphics.setColor(new Color(0, 0, 0, 40));
		graphics.fillRect(area.x, area.y, area.width, area.height);
		int ty = area.y + 8 - patchScroll;
		for (Object[] part : parts)
		{
			int lineHeight = (Integer) part[4];
			Font font = (Font) part[1];
			graphics.setFont(font);
			if (font == regular && ty > area.y + 8 - patchScroll)
			{
				// A divider over each version after the first.
				Skin.divider(graphics, area.x + 8, ty - 2, area.x + area.width - 14);
			}
			graphics.setColor((Color) part[2]);
			Skin.text(graphics, (String) part[0], area.x + 10 + (Integer) part[3], ty + graphics.getFontMetrics().getAscent());
			ty += lineHeight;
		}
		graphics.setClip(oldClip);
		drawScrollbar(graphics, area, contentHeight, patchScroll);
	}

	/** The ChunkBlazer icon for the header, read once (null if missing). */
	private static BufferedImage logo()
	{
		if (logo == null)
		{
			try (java.io.InputStream in = TaskBrowserOverlay.class.getResourceAsStream("icon.png"))
			{
				logo = in == null ? new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB) : javax.imageio.ImageIO.read(in);
			}
			catch (java.io.IOException e)
			{
				logo = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			}
		}
		return logo.getWidth() > 1 ? logo : null;
	}

	/** The task list as cards, with expanded details, scrolling and the empty-list message. Returns how many tasks. */
	private int drawList(Graphics2D graphics, Rectangle list, int mx, int my, Font regular, Font small)
	{
		List<Entry> entries = currentRows();
		FontMetrics sm = graphics.getFontMetrics(small);
		int detailWidth = list.width - 70;
		FontMetrics rm = graphics.getFontMetrics(regular);
		List<List<QuestRequirements.Line>> details = new ArrayList<>();
		List<Integer> heights = new ArrayList<>();
		int contentHeight = 4;
		int tasks = 0;
		for (Entry entry : entries)
		{
			List<QuestRequirements.Line> wrapped = entry.task != null && expanded.contains(entry.task.getTaskId())
				? wrapDetail(sm, entry.task, detailWidth) : new ArrayList<>();
			details.add(wrapped);
			int nameLines = entry.task == null ? 1 : rowName(rm, entry.task, list.width - 12 - (entry.child ? 18 : 0) - 160).size();
			heights.add(ROW + (nameLines - 1) * NAME_LINE + detailHeight(wrapped));
			contentHeight += heights.get(heights.size() - 1) + ROW_GAP;
			tasks += entry.task != null ? 1 : entry.members.size();
		}
		scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - list.height)));

		Set<String> saved = savedIds();
		Shape oldClip = graphics.getClip();
		graphics.clip(list);
		if (entries.isEmpty())
		{
			graphics.setFont(regular);
			graphics.setColor(SUBTEXT);
			// The empty Archived list's message is about the book icon, so it shows the book.
			if (tab == Tab.ARCHIVED)
			{
				graphics.drawImage(Skin.image("book_empty"), list.x + list.width / 2 - 10, list.y + list.height / 2 - 35, null);
			}
			else
			{
				graphics.drawImage(Skin.image("star_empty"), list.x + list.width / 2 - 13, list.y + list.height / 2 - 35, null);
			}
			drawCentered(graphics, emptyMessage(), list);
		}
		int rowY = list.y + 4 - scroll;
		for (int i = 0; i < entries.size(); i++)
		{
			Entry entry = entries.get(i);
			int rowHeight = heights.get(i);
			if (rowY + rowHeight >= list.y && rowY <= list.y + list.height)
			{
				int indent = entry.child ? 18 : 0;
				Rectangle area = new Rectangle(list.x + 2 + indent, rowY, list.width - 12 - indent, rowHeight);
				boolean hover = list.contains(mx, my) && area.contains(mx, my);
				// Leagues-style rows: alternately darker, lit under the mouse, a thin line between.
				graphics.setColor(hover ? CARD_HOVER : i % 2 == 0 ? new Color(0, 0, 0, 48) : new Color(0, 0, 0, 22));
				graphics.fillRect(area.x, area.y, area.width, area.height);
				graphics.setColor(new Color(0, 0, 0, 120));
				graphics.drawLine(area.x, area.y + area.height + ROW_GAP / 2, area.x + area.width, area.y + area.height + ROW_GAP / 2);
				if (entry.task == null)
				{
					drawGroupRow(graphics, entry, area, list, mx, my, regular, small);
				}
				else
				{
					drawRow(graphics, entry.task, area, details.get(i), saved, list, mx, my, regular, small);
				}
			}
			rowY += rowHeight + ROW_GAP;
		}
		graphics.setClip(oldClip);
		drawScrollbar(graphics, list, contentHeight, scroll);
		return tasks;
	}

	private String emptyMessage()
	{
		if (pinnedChunk != null)
		{
			return "No tasks left in " + pinnedChunk + ".";
		}
		if (currentChunkOnly)
		{
			String here = currentChunkName();
			return here.isEmpty() ? "You're not in a chunk with tasks." : "No tasks here in " + here + ".";
		}
		if (search.isEmpty() && tab != Tab.ACTIVE)
		{
			return tab == Tab.NEW ? "No new tasks left to show."
				: tab == Tab.ARCHIVED ? "Click a task's book icon to archive it here."
				: "Star tasks in the Active tab to save them here.";
		}
		return "No tasks match.";
	}

	/** Work out what a click would do. */
	private void finishHover(Rectangle window, int mx, int my)
	{
		// The quest box is drawn on top, so over it, it gets the click.
		boolean inPane = paneIn && questPane != null;
		mouseInWindow = inPane || window.contains(mx, my);
		if (inPane)
		{
			titleHovered = false;
		}
		else
		{
			hoveredAction = firstHit(hits, mx, my);
		}
	}

	private static Runnable firstHit(List<Hit> list, int mx, int my)
	{
		for (Hit hit : list)
		{
			if (hit.area.contains(mx, my))
			{
				return hit.action;
			}
		}
		return null;
	}

	/**
	 * While the window is closed with new tasks waiting: a pulsing glow around the Points orb.
	 */
	private void drawNewTaskAlert(Graphics2D graphics)
	{
		Rectangle b = taskButton;
		if (b == null)
		{
			return;
		}
		double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() * 2 * Math.PI / PULSE_MS);
		Stroke previousStroke = graphics.getStroke();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(new Color(255, 152, 31, (int) (35 * pulse)));
		graphics.fillRect(b.x - 3, b.y - 3, b.width + 6, b.height + 6);
		graphics.setColor(new Color(255, 152, 31, (int) (90 + 150 * pulse)));
		graphics.setStroke(new BasicStroke(2.5f));
		graphics.drawRect(b.x - 3, b.y - 3, b.width + 6, b.height + 6);
		graphics.setStroke(previousStroke);
	}

	// --- Header controls -------------------------------------------------------

	/** "This chunk only" as a switch; or, showing a chunk picked on the map, its name with an x. */
	private void drawChunkToggle(Graphics2D graphics, Rectangle area, String label, int mx, int my)
	{
		boolean hover = area.contains(mx, my);
		hits.add(0, new Hit(area, () ->
		{
			if (pinnedChunk != null)
			{
				pinnedChunk = null;
			}
			else
			{
				currentChunkOnly = !currentChunkOnly;
			}
			refresh();
		}));
		graphics.setFont(Skin.small());
		FontMetrics fm = graphics.getFontMetrics();
		int textY = area.y + (area.height + fm.getAscent()) / 2 - 1;
		if (hover)
		{
			graphics.setColor(CARD_HOVER);
			graphics.fillRect(area.x, area.y, area.width, area.height);
		}
		if (pinnedChunk != null)
		{
			graphics.setColor(TITLE);
			Skin.text(graphics, label, area.x + 6, textY);
			cross(graphics, area.x + area.width - 9, area.y + area.height / 2, 3, hover ? NO_LEVEL : SUBTEXT);
			return;
		}
		// A little switch: the knob slides right and goes orange when on.
		int sw = 22;
		int sx = area.x + 6;
		int sy = area.y + (area.height - 12) / 2;
		graphics.setColor(currentChunkOnly ? new Color(255, 152, 31, 150) : FIELD);
		graphics.fillRect(sx, sy, sw, 12);
		graphics.setColor(currentChunkOnly ? Color.WHITE : SUBTEXT);
		graphics.fillOval(currentChunkOnly ? sx + sw - 11 : sx + 1, sy + 1, 10, 10);
		graphics.setColor(currentChunkOnly || hover ? Color.WHITE : SUBTEXT);
		Skin.text(graphics, label, sx + sw + 6, textY);
	}

	/** A tab down the sidebar: its name and a count badge. (The marker by the chosen one is drawn after.) */
	private void drawTab(Graphics2D graphics, Rectangle area, String label, int count, Tab which, int mx, int my)
	{
		hits.add(new Hit(area, () ->
		{
			tab = which;
			patchNotes = false;
			settingsOpen = false;
			refresh();
		}));
		boolean chosen = tab == which && !patchNotes && !settingsOpen;
		boolean hover = area.contains(mx, my);
		// Stone tab buttons; the chosen one glows red like the game's open side tab.
		Skin.button(graphics, area, hover, chosen);
		graphics.setFont(Skin.regular());
		FontMetrics fm = graphics.getFontMetrics();
		graphics.setColor(chosen || hover ? Color.WHITE : SUBTEXT);
		Skin.text(graphics, label, area.x + 12, area.y + (area.height + fm.getAscent()) / 2 - 2);
		graphics.setFont(Skin.small());
		fm = graphics.getFontMetrics();
		String n = String.valueOf(count);
		int bw = Math.max(18, fm.stringWidth(n) + 10);
		int bx = area.x + area.width - bw - 4;
		graphics.setColor(chosen ? new Color(255, 152, 31, 170) : FIELD);
		graphics.fillRect(bx, area.y + 5, bw, 14);
		graphics.setColor(chosen ? Color.WHITE : SUBTEXT);
		Skin.text(graphics, n, bx + (bw - fm.stringWidth(n)) / 2, area.y + 16);
	}

	// --- List rows ---------------------------------------------------------------

	/** Header for one skill's level-up tasks; click to show or hide its rungs. */
	private void drawGroupRow(Graphics2D graphics, Entry entry, Rectangle row, Rectangle list, int mx, int my,
		Font regular, Font small)
	{
		boolean hover = list.contains(mx, my) && row.contains(mx, my);
		if (list.contains(mx, my))
		{
			hits.add(new Hit(row.intersection(list), () ->
			{
				if (!expandedGroups.remove(entry.group))
				{
					expandedGroups.add(entry.group);
				}
				rowsBuiltAt = 0;
			}));
		}
		arrow(graphics, row.x + 11, row.y + ROW / 2, expandedGroups.contains(entry.group), hover);
		Skill skill = null;
		try
		{
			skill = Skill.valueOf(entry.group);
		}
		catch (IllegalArgumentException ignored)
		{
			// Not a skill we know: no icon.
		}
		drawTaskIcon(graphics, row.x + 22, row.y + (ROW - 22) / 2, skill, null);
		int points = entry.members.stream().mapToInt(NuzlockeTask::getBasePoints).sum();
		int left = entry.members.size();
		String name = entry.group.charAt(0) + entry.group.substring(1).toLowerCase() + " levels";
		String info = left + (left == 1 ? " level-up left" : " level-ups left") + "  ·  next: level "
			+ requiredLevel(entry.members.get(0));
		drawRowText(graphics, row, row.x + 50, name, Color.WHITE, points + " pts", POINTS, info, SUBTEXT, regular, small);
	}

	/**
	 * Line 1: the name (left) and a points chip (right, tinted {@code chipColor}). Line 2: the
	 * info text. Returns where the chip starts, so the row's icons can sit before it.
	 */
	private int drawRowText(Graphics2D graphics, Rectangle row, int textX, String name, Color nameColor, String points,
		Color chipColor, String info, Color infoColor, Font regular, Font small)
	{
		int rightEdge = row.x + row.width - 8;
		graphics.setFont(small);
		FontMetrics sm = graphics.getFontMetrics();
		int chipW = sm.stringWidth(points) + 12;
		int chipX = rightEdge - chipW;
		graphics.setColor(POINTS);
		// A plain number in the Points column, as on the Leagues list.
		Skin.text(graphics, points, rightEdge - sm.stringWidth(points), row.y + 17);
		graphics.setFont(regular);
		FontMetrics fm = graphics.getFontMetrics();
		graphics.setColor(nameColor);
		String[] lines = name.split("\n");
		for (int i = 0; i < lines.length; i++)
		{
			Skin.text(graphics, Skin.fit(fm, lines[i], chipX - 48 - textX), textX, row.y + 17 + i * NAME_LINE);
		}
		if (info != null)
		{
			graphics.setFont(small);
			graphics.setColor(infoColor);
			Skin.text(graphics, Skin.fit(sm, info, rightEdge - 90 - textX), textX, row.y + 32 + (lines.length - 1) * NAME_LINE);
		}
		return chipX;
	}

	/**
	 * Every task's icon, in the same light stone slot: a quest's quest icon, an Obtain or Equip
	 * task's item, a skill task's skill icon, a combat task's crossed swords, or (none of
	 * those) the tier's colour with the category's first letter. Each picture is scaled to fit
	 * inside the slot, keeping its shape, so none hangs over the edge.
	 */
	private void drawTaskIcon(Graphics2D graphics, int x, int y, Skill skill, NuzlockeTask task)
	{
		Rectangle slot = new Rectangle(x - 1, y - 3, ICON_SLOT, ICON_SLOT);
		graphics.setColor(ITEM_SLOT);
		graphics.fillRect(slot.x, slot.y, slot.width, slot.height);
		Skin.bevel(graphics, slot, false);
		BufferedImage image = iconFor(task, skill);
		if (image != null && image.getWidth() > 0 && image.getHeight() > 0)
		{
			int room = ICON_SLOT - 4;
			double scale = Math.min(room / (double) image.getWidth(), room / (double) image.getHeight());
			int w = (int) Math.round(image.getWidth() * scale);
			int h = (int) Math.round(image.getHeight() * scale);
			Object hint = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.drawImage(image, slot.x + (ICON_SLOT - w) / 2, slot.y + (ICON_SLOT - h) / 2, w, h, null);
			if (hint != null)
			{
				graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
			}
			return;
		}
		Color tier = task == null ? SUBTEXT : TaskCardTier.fromTask(task).getAccent();
		graphics.setColor(new Color(tier.getRed(), tier.getGreen(), tier.getBlue(), 120));
		graphics.fillOval(slot.x + 4, slot.y + 4, ICON_SLOT - 8, ICON_SLOT - 8);
		String letter = task == null || task.getCategory() == null || task.getCategory().isEmpty() ? "?"
			: NuzlockeTask.displayCategory(task.getCategory()).substring(0, 1);
		graphics.setFont(Skin.small());
		graphics.setColor(Color.WHITE);
		drawCentered(graphics, letter, slot);
	}

	/** The picture for a task's slot (see drawTaskIcon), or null for the letter fallback. */
	private BufferedImage iconFor(NuzlockeTask task, Skill skill)
	{
		if (task != null && QuestRequirements.isQuestTask(task))
		{
			return sprite(net.runelite.api.gameval.SpriteID.SideIcons.QUEST);
		}
		BufferedImage item = itemImage(task);
		if (item != null)
		{
			return item;
		}
		if (skill == null && task != null && typeIs(task, "SKILL_THRESHOLD"))
		{
			skill = progressionSkill(task); // "Reach Level 50 Mining": the skill it's a goal for
		}
		if (skill != null)
		{
			return skillIcons.getSkillImage(skill, false);
		}
		if (task != null && typeIs(task, "NPC_DIALOGUE"))
		{
			return sprite(net.runelite.api.gameval.SpriteID.OptionsIcons.CHAT); // the speech bubble
		}
		if (task != null && (typeIs(task, "NPC_KILL", "SLAYER", "COMBAT")
			|| (task.getCategory() != null && task.getCategory().toLowerCase().contains("combat"))))
		{
			return sprite(net.runelite.api.gameval.SpriteID.SideIcons.COMBAT);
		}
		return null;
	}

	/** The skill a progression (level-up) task is for, or null. */
	private static Skill progressionSkill(NuzlockeTask task)
	{
		TaskConstraints c = task.getConstraints();
		try
		{
			return c == null || c.getRequiredSkill() == null ? null : Skill.valueOf(c.getRequiredSkill().toUpperCase());
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	/** A game sprite (frame 0), loaded once. Overlays draw on the client thread, as loading needs. */
	private BufferedImage sprite(int id)
	{
		BufferedImage image = sprites.get(id);
		if (image == null && spriteManager != null)
		{
			image = spriteManager.getSprite(id, 0);
			if (image != null)
			{
				sprites.put(id, image);
			}
		}
		return image;
	}

	/** The picture of an Obtain or Equip task's (first) item, or null for any other task. */
	private BufferedImage itemImage(NuzlockeTask task)
	{
		String type = task == null || task.getCompletionType() == null ? "" : task.getCompletionType().toUpperCase();
		if (itemManager == null || !(type.equals("OBTAIN") || type.equals("EQUIP"))
			|| task.getRequiredItems() == null || task.getRequiredItems().isEmpty())
		{
			return null;
		}
		RequiredItem first = task.getRequiredItems().get(0);
		List<Integer> ids = first == null ? null : first.getItemIds();
		return ids == null || ids.isEmpty() ? null : itemManager.getImage(ids.get(0));
	}

	private void drawRow(Graphics2D graphics, NuzlockeTask task, Rectangle row, List<QuestRequirements.Line> detail,
		Set<String> saved, Rectangle list, int mx, int my, Font regular, Font small)
	{
		boolean inList = list.contains(mx, my);
		boolean hoverRow = inList && row.contains(mx, my);
		String taskId = task.getTaskId();
		if (hoverRow)
		{
			listRowId = taskId;
		}
		boolean isTracked = isTracked(task);
		Color tier = TaskCardTier.fromTask(task).getAccent();
		if (isTracked)
		{
			// The task in the task box: a warm glow and an orange outline.
			graphics.setColor(TRACKED_FILL);
			graphics.fillRect(row.x, row.y, row.width, row.height);
			graphics.setColor(TITLE);
			graphics.drawRect(row.x, row.y, row.width - 1, row.height - 1);
		}
		// Its tier down the left edge.
		graphics.setColor(tier);
		graphics.fillRect(row.x + 2, row.y + 6, 3, row.height - 12);

		// Expand arrow (a quest's opens its pane), and, at the right, star and archive book;
		// added first so they win the click over the row.
		int iconY = row.y + (ROW - 18) / 2;
		Rectangle arrowArea = new Rectangle(row.x + 6, iconY, 14, 18);
		boolean expandable = !requirementsFor(task).isEmpty() || QuestRequirements.isQuestTask(task) || isItemTask(task);
		int textX = row.x + 50;
		boolean canDo = canDo(task);
		List<String> names = rowName(graphics.getFontMetrics(regular), task, row.width - 160);
		String name = String.join("\n", names);
		// Everything under the name moves down a line when the name takes two.
		int lift = (names.size() - 1) * NAME_LINE;
		String chunk = chunkNames.getOrDefault(taskId, "");
		String here = currentChunkName();
		boolean inHere = !chunk.isEmpty() && chunk.equals(here);
		String info = NuzlockeTask.displayCategory(task.getCategory()) + (chunk.isEmpty() ? "" : "  ·  " + chunk);
		int chipX = drawRowText(graphics, row, textX, name, canDo ? Color.WHITE : NO_LEVEL,
			task.getBasePoints() + (task.getBasePoints() == 1 ? " pt" : " pts"), tier, info, inHere ? HERE : SUBTEXT, regular, small);

		// A fixed spot from the right edge, so they line up down the list whatever the points say.
		Rectangle book = new Rectangle(row.x + row.width - 70, iconY - 3, 14, 18);
		Rectangle star = new Rectangle(book.x - 20, iconY - 3, 18, 18);
		if (row.intersects(list))
		{
			rowSpots.put(taskId, new Rectangle[]{row.intersection(list), star.intersection(list)});
			if (firstRowId == null)
			{
				firstRowId = taskId;
			}
		}
		if (inList)
		{
			if (expandable)
			{
				hits.add(new Hit(arrowArea.intersection(list), () ->
				{
					if (QuestRequirements.isQuestTask(task))
					{
						openQuestPane(task);
					}
					else if (!expanded.remove(taskId))
					{
						expanded.add(taskId);
					}
				}));
			}
			hits.add(new Hit(star.intersection(list), () -> toggleSaved(taskId)));
			hits.add(new Hit(book.intersection(list), () -> toggleArchived(task)));
		}
		// An Obtain or Equip task's item picture opens that item's OSRS Wiki page.
		Rectangle iconSlot = new Rectangle(row.x + 21, row.y + (ROW - 22) / 2 - 3, ICON_SLOT, ICON_SLOT);
		List<String[]> wikiItems = wikiItems(task);
		boolean iconHover = false;
		if (inList && !wikiItems.isEmpty())
		{
			String url = wikiItems.get(0)[1];
			hits.add(new Hit(iconSlot.intersection(list), () -> LinkBrowser.browse(url)));
			iconHover = iconSlot.contains(mx, my);
			if (iconHover)
			{
				tip = "Click to open " + wikiItems.get(0)[0] + " on the OSRS Wiki";
			}
		}
		drawDetail(graphics, detail, textX, row.y + ROW - 1 + lift, list, mx, my, small);
		if (inList)
		{
			hits.add(new Hit(row.intersection(list), () ->
			{
				// A click tracks (right-click saves, see the mouse listener).
				trackOrOpen(task, this::openQuestPane);
			}));
		}
		if (expandable)
		{
			arrow(graphics, arrowArea.x + 7, arrowArea.y + 9, expanded.contains(taskId), inList && arrowArea.contains(mx, my));
		}
		drawTaskIcon(graphics, row.x + 22, row.y + (ROW - 22) / 2, TaskTargetExtras.categorySkill(task.getCategory()), task);
		if (iconHover)
		{
			graphics.setColor(WIKI_LINK);
			graphics.drawRect(iconSlot.x, iconSlot.y, iconSlot.width - 1, iconSlot.height - 1);
		}
		// Star and book show on hover, and always once they're on.
		boolean isSaved = saved.contains(taskId);
		boolean isArchived = archive.isArchived(task);
		if (hoverRow || isSaved)
		{
			graphics.drawImage(isSaved ? STAR_ON : star.contains(mx, my) ? STAR_HOVER : STAR_OFF, star.x + 1, star.y + 1, null);
		}
		if (hoverRow || isArchived)
		{
			graphics.drawImage(isArchived ? BOOK_ON : book.contains(mx, my) ? BOOK_HOVER : BOOK_OFF, book.x + 1, book.y + 2, null);
		}

		// Line 2's right end: "TRACKING", or progress as a small bar and count. One-off
		// tasks (target 1) leave it blank, since a "0/1" bar says nothing.
		graphics.setFont(small);
		FontMetrics sm = graphics.getFontMetrics();
		int rightEdge = row.x + row.width - 8;
		int target = Math.max(1, task.getTargetQuantity());
		if (isTracked)
		{
			String tag = "TRACKING";
			graphics.setColor(TITLE);
			Skin.text(graphics, tag, rightEdge - sm.stringWidth(tag), row.y + 32 + lift);
		}
		else if (target > 1)
		{
			String progress = Math.min(task.getCurrentProgress(), target) + "/" + target;
			int progressWidth = sm.stringWidth(progress);
			graphics.setColor(SUBTEXT);
			Skin.text(graphics, progress, rightEdge - progressWidth, row.y + 32 + lift);
			// A fixed slot for the count, so every row's bar lines up whether it says 0/4 or 0/56.
			int slot = Math.max(progressWidth, sm.stringWidth("888/888"));
			int barX = rightEdge - slot - 62;
			Skin.bar(graphics, barX, row.y + 26 + lift, 56, 6, (float) TaskTargetExtras.progress(task), tier, 0, false);
		}
	}

	/**
	 * Detail lines from (x, y). Clickable ones get a hit: a section header opens/closes, a
	 * chunk shows on the world map (now, or as soon as it's opened), a quest opens in the pane.
	 */
	private void drawDetail(Graphics2D graphics, List<QuestRequirements.Line> detail, int x, int y, Rectangle clip,
		int mx, int my, Font small)
	{
		graphics.setFont(small);
		FontMetrics sm = graphics.getFontMetrics();
		boolean in = clip.contains(mx, my);
		for (int i = 0; i < detail.size(); i++)
		{
			QuestRequirements.Line line = detail.get(i);
			Rectangle area = detailLineArea(sm, detail, x, y, i);
			if (line.clickable() && in)
			{
				hits.add(new Hit(area.intersection(clip), line.region > 0 ? () -> worldMap.focusRegion(line.region)
					: line.quest != null ? () -> showQuest(line.quest)
					: line.wiki != null ? () -> LinkBrowser.browse(line.wiki)
					: () -> quests.toggleSection(line.toggle)));
			}
			int lineY = area.y + 1 + sm.getAscent();
			boolean hover = line.clickable() && in && area.contains(mx, my);
			if (hover && line.region > 0)
			{
				tip = worldMap.isMapOpen() ? "Click to show it on the world map" : "Click, then open the world map to see it";
			}
			else if (hover && line.wiki != null)
			{
				tip = "Click to open this item on the OSRS Wiki";
			}
			else if (hover && line.quest != null)
			{
				tip = hasQuestTask(line.quest) ? "Click to see this quest's requirements"
					: "Not in your task list (done, or not a ChunkBlazer quest)";
			}
			// Section headings sit on a faint band; anything clickable lights up under the mouse.
			if (line.header || hover)
			{
				graphics.setColor(hover ? ROW_HOVER : ROW_ALT);
				graphics.fillRect(x - 3, area.y, line.header ? clip.width - 12 : area.width + 6, DETAIL_LINE);
			}
			if (line.header)
			{
				arrow(graphics, x + 3, lineY - 1 - sm.getAscent() / 2, line.open, hover);
			}
			int lineX = x + lineIndent(line);
			graphics.setColor(lineColor(line.color));
			Skin.text(graphics, line.text, lineX, lineY);
		}
	}

	/** A requirement's colour in this style: Old School uses the game's own green, red and yellow. */
	private static Color lineColor(Color color)
	{
		if (color == null)
		{
			return color;
		}
		if (color.equals(QuestRequirements.MET))
		{
			return HERE;
		}
		if (color.equals(QuestRequirements.NOT_MET))
		{
			return NO_LEVEL;
		}
		if (color.equals(QuestRequirements.NEXT_TO_YOURS))
		{
			return new Color(255, 255, 0);
		}
		if (color.equals(QuestRequirements.TEXT) || color.equals(DETAIL))
		{
			return DETAIL;
		}
		return color;
	}

	/** A task's detail lines, wrapped to {@code width}. */
	private List<QuestRequirements.Line> wrapDetail(FontMetrics sm, NuzlockeTask task, int width)
	{
		List<QuestRequirements.Line> wrapped = new ArrayList<>();
		for (QuestRequirements.Line line : detailLines(task))
		{
			boolean first = true;
			for (String part : wrap(sm, line.text, width - lineIndent(line)))
			{
				wrapped.add(line.withText(part, first));
				first = false;
			}
		}
		return wrapped;
	}

	/** Where detail line {@code index} is: from x to the end of its text. */
	private static Rectangle detailLineArea(FontMetrics fm, List<QuestRequirements.Line> detail, int x, int top, int index)
	{
		int y = top;

		for (int i = 0; i <= index; i++)
		{
			y += detail.get(i).gap + (i < index ? DETAIL_LINE : 0);
		}
		QuestRequirements.Line line = detail.get(index);
		return new Rectangle(x, y, lineIndent(line) + fm.stringWidth(line.text), DETAIL_LINE);
	}

	private static int lineIndent(QuestRequirements.Line line)
	{
		return line.header ? SECTION_TEXT : line.indent;
	}

	private static int detailHeight(List<QuestRequirements.Line> lines)
	{
		return lines.isEmpty() ? 0 : 4 + lines.stream().mapToInt(l -> DETAIL_LINE + l.gap).sum();
	}

	/** A prerequisite quest was clicked: open it in the pane, or say in chat that it isn't a task right now. */
	private void showQuest(String questName)
	{
		NuzlockeTask found = questTask(questName);
		if (found != null)
		{
			navigateTo(found);
		}
	}

	/** For the hover tip: whether the quest is in your task list, rechecked at most once a second. */
	private boolean hasQuestTask(String questName)
	{
		long now = System.currentTimeMillis();
		if (!questName.equals(tipQuest) || now - tipQuestAt > 1000)
		{
			tipQuest = questName;
			tipQuestAt = now;
			tipQuestFound = questTask(questName) != null;
		}
		return tipQuestFound;
	}

	/** The quest task for this quest (a RuneLite Quest name), or null if it isn't in your task list. */
	private NuzlockeTask questTask(String questName)
	{
		return pool(true).stream()
			.filter(t -> QuestRequirements.isQuestTask(t) && t.getConstraints() != null
				&& questName.equals(t.getConstraints().getQuest()))
			.findFirst().orElse(null);
	}

	/**
	 * Show a quest's requirements over the side panel (inventory, prayer...), not over its
	 * tab buttons. The task window stays open beside it. It has its own close button,
	 * and switching tab or pressing any key closes it too.
	 */
	void openQuestPane(NuzlockeTask task)
	{
		// Opened fresh (window, side panel, bubble): no Back trail from whatever was open before.
		questBack.clear();
		paneSide = null;
		showInPane(task);
	}

	/** Follow a link inside the box (a required quest, a saved quest): Back returns here. */
	private void navigateTo(NuzlockeTask task)
	{
		if (questPane != null && questPane != task)
		{
			questBack.push(questPane);
		}
		showInPane(task);
	}

	private void showInPane(NuzlockeTask task)
	{
		questPane = task;
		questPaneAt = System.currentTimeMillis();
		paneScroll = 0;
	}

	/** The quest/saved box at {@code pane}; (ox, oy) is where that's drawn on screen when it's moved. */
	private void drawQuestPane(Graphics2D graphics, Rectangle pane, int ox, int oy)
	{
		NuzlockeTask task = questPane;
		int mx = paneMouse.x - ox;
		int my = paneMouse.y - oy;
		Runnable windowHover = hoveredAction;
		Rectangle windowArea = shownArea;
		boolean windowIn = open && windowArea != null && windowArea.contains(paneMouse);
		hits.clear();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// A rounded card with a darker header band and an orange rule under it.
		boolean savedList = task == SAVED_PANE;
		List<NuzlockeTask> saved = savedList ? savedTasks() : null;
		graphics.setFont(Skin.bold());
		List<String> title = wrap(graphics.getFontMetrics(), savedList ? "Saved tasks (" + saved.size() + ")"
			: task.getName().replaceFirst("^Complete ", ""), pane.width - (questBack.isEmpty() ? 62 : 96));
		int top = pane.y + 14 + title.size() * 15;
		Skin.panel(graphics, new Rectangle(pane.x, pane.y, pane.width, pane.height), top - pane.y, null);
		Rectangle close = new Rectangle(pane.x + pane.width - 21, pane.y + 5, 16, 16);
		hits.add(new Hit(close, () -> questPane = null));
		Skin.close(graphics, close, close.contains(mx, my));
		// The quest's OSRS Wiki page, beside the close button.
		graphics.setFont(Skin.small());
		Rectangle wiki = new Rectangle(close.x - 30, close.y + 1, 27, 14);
		if (!savedList)
		{
			// The wiki's search jumps straight to the page when the name matches, and
			// lists the closest pages when it doesn't (the Recipe for Disaster parts).
			String quest = task.getName().replaceFirst("^Complete ", "").replace("&", "%26").replace(' ', '+');
			hits.add(new Hit(wiki, () -> LinkBrowser.browse("https://oldschool.runescape.wiki/?title=Special:Search&go=Go&search=" + quest)));
			pill(graphics, wiki, "Wiki", wiki.contains(mx, my));
		}
		// Back to the quest this one was opened from.
		Rectangle back = new Rectangle(wiki.x - 33, close.y + 1, 30, 14);
		if (!questBack.isEmpty())
		{
			hits.add(new Hit(back, () ->
			{
				questPane = questBack.pop();
				questPaneAt = System.currentTimeMillis();
				paneScroll = 0;
			}));
			pill(graphics, back, "Back", back.contains(mx, my));
		}
		// A quick fade and slide in, so moving to a required quest reads as a new page.
		float in = Math.min(1f, (System.currentTimeMillis() - questPaneAt) / 200f);
		Composite oldComposite = graphics.getComposite();
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, in));
		graphics.translate((int) (16 * (1 - in) * (1 - in)), 0);
		// The whole name, wrapped, without the "Complete" every quest task starts with.
		graphics.setFont(Skin.bold());
		graphics.setColor(TITLE);
		for (int i = 0; i < title.size(); i++)
		{
			Skin.text(graphics, title.get(i), pane.x + 8, pane.y + 21 + i * 15);
		}

		Font small = Skin.small();
		// The saved list keeps its how-to line pinned at the bottom.
		int foot = savedList && !saved.isEmpty() ? 19 : 0;
		Rectangle body = new Rectangle(pane.x + 1, top + 2, pane.width - 2, pane.y + pane.height - 3 - top - foot);
		if (foot > 0)
		{
			Skin.divider(graphics, pane.x + 6, body.y + body.height, pane.x + pane.width - 8);
			graphics.setFont(small);
			graphics.setColor(SUBTEXT);
			Skin.text(graphics, "Right-click to remove", pane.x + 8, body.y + body.height + 14);
		}
		Shape oldClip = graphics.getClip();
		graphics.clip(body);
		if (savedList)
		{
			drawScrollbar(graphics, body, drawSavedList(graphics, saved, body, mx, my, small), paneScroll);
		}
		else
		{
			List<QuestRequirements.Line> lines = wrapDetail(graphics.getFontMetrics(small), task, pane.width - 12);
			paneScroll = Math.max(0, Math.min(paneScroll, detailHeight(lines) - body.height));
			drawDetail(graphics, lines, pane.x + 6, body.y - paneScroll, body, mx, my, small);
			drawScrollbar(graphics, body, detailHeight(lines), paneScroll);
		}
		graphics.setClip(oldClip);
		graphics.translate(-(int) (16 * (1 - in) * (1 - in)), 0);
		graphics.setComposite(oldComposite);
		paneIn = pane.contains(mx, my);
		paneArea = new Rectangle(pane.x + ox, pane.y + oy, pane.width, pane.height);
		if (paneIn)
		{
			mouseInWindow = true;
			hoveredAction = firstHit(hits, mx, my);
			titleHovered = false;
		}
		else
		{
			mouseInWindow = windowIn;
			hoveredAction = windowIn ? windowHover : null;
		}
		drawTip(graphics, mx, my, ox, oy);
	}

	/**
	 * The hint set while drawing ({@code tip}), in a small box by the mouse, over everything.
	 * Drawn here rather than by RuneLite's tooltips, which follow the game's mouse: over the
	 * window and box the game is told the mouse is off screen. (ox, oy) is where (0, 0) is on screen.
	 */
	private void drawTip(Graphics2D graphics, int mx, int my, int ox, int oy)
	{
		String text = tip;
		tip = null;
		if (text == null)
		{
			return;
		}
		graphics.setFont(Skin.small());
		FontMetrics fm = graphics.getFontMetrics();
		Rectangle box = new Rectangle(0, 0, fm.stringWidth(text) + 10, 17);
		int canvasWidth = client.getCanvasWidth();
		int canvasHeight = client.getCanvasHeight();
		box.x = Math.max(-ox, Math.min(mx + 12, canvasWidth - ox - box.width - 2));
		box.y = my + 20 + box.height > canvasHeight - oy ? my - box.height - 4 : my + 20;
		Skin.tooltip(graphics, box, text);
	}

	/** Whether a task is in the chunk you're standing in ({@code here} is that chunk's name). */
	private boolean isHere(NuzlockeTask task, String here)
	{
		if (here.isEmpty() || plugin.isGlobalTask(task.getTaskId()))
		{
			return false;
		}
		return here.equals(chunkNames.computeIfAbsent(task.getTaskId(), k -> chunkName(task)));
	}

	/**
	 * The saved list in the pane, tasks in the chunk you're standing in first: name (red if
	 * your level's too low), then its chunk (green if you're in it) or
	 * category and progress. Click to track or untrack (a quest opens its requirements),
	 * Shift + click to unsave. Returns its full height, for the scrollbar.
	 */
	private int drawSavedList(Graphics2D graphics, List<NuzlockeTask> saved, Rectangle body, int mx, int my, Font small)
	{
		graphics.setFont(small);
		FontMetrics fm = graphics.getFontMetrics();
		paneRowId = null;
		if (saved.isEmpty())
		{
			graphics.setColor(SUBTEXT);
			Skin.text(graphics, "Star tasks in the task window", body.x + 5, body.y + 14);
			return 0;
		}
		// Tasks in the chunk you're standing in go first, their chunk in green (a stable sort,
		// so the rest keep their order).
		String hereChunk = currentChunkName();
		saved = new ArrayList<>(saved);
		saved.sort(Comparator.comparing((NuzlockeTask t) -> !isHere(t, hereChunk)));
		// Long names wrap onto a second line (and the card grows) rather than being cut off.
		int width = body.width - 28;
		List<List<String>> names = new ArrayList<>();
		int total = 4;
		for (NuzlockeTask task : saved)
		{
			List<String> lines = wrap(fm, task.getName(), width);
			names.add(lines.size() > 2 ? Arrays.asList(lines.get(0), Skin.fit(fm, lines.get(1) + " " + lines.get(2), width)) : lines);
			total += 24 + 12 * names.get(names.size() - 1).size();
		}
		paneScroll = Math.max(0, Math.min(paneScroll, total - body.height));
		boolean shift = client.isKeyPressed(KeyCode.KC_SHIFT);
		int y = body.y + 4 - paneScroll;
		for (int i = 0; i < saved.size(); i++)
		{
			NuzlockeTask task = saved.get(i);
			List<String> name = names.get(i);
			// Each task is a small rounded card: its tier colour down the left, like the side
			// panel's, and a thin progress bar along the bottom for counted tasks.
			Rectangle row = new Rectangle(body.x + 4, y, body.width - 14, 20 + 12 * name.size());
			y += row.height + 4;
			boolean hover = row.contains(mx, my) && body.contains(mx, my);
			boolean isTracked = isTracked(task);
			Color tier = TaskCardTier.fromTask(task).getAccent();
			graphics.setColor(hover && shift ? new Color(255, 70, 70, 50) : hover ? ROW_HOVER : isTracked ? TRACKED_FILL : ROW_ALT);
			graphics.fillRect(row.x, row.y, row.width, row.height);
			graphics.setColor(isTracked ? TITLE : new Color(0, 0, 0, 120));
			graphics.drawRect(row.x, row.y, row.width, row.height);
			graphics.setColor(tier);
			graphics.fillRect(row.x + 2, row.y + 4, 3, row.height - 8);
			int target = Math.max(1, task.getTargetQuantity());
			if (target > 1)
			{
				graphics.setColor(Skin.FILL);
				graphics.fillRect(row.x + 8, row.y + row.height - 3, (row.width - 14) * Math.min(task.getCurrentProgress(), target) / target, 2);
			}
			if (hover)
			{
				paneRowId = task.getTaskId();
			}
			if (body.contains(mx, my))
			{
				hits.add(new Hit(row, () ->
				{
					if (client.isKeyPressed(KeyCode.KC_SHIFT))
					{
						toggleSaved(task.getTaskId());
					}
					else
					{
						trackOrOpen(task, this::navigateTo);
					}
				}));
			}
			String progress = target > 1 ? Math.min(task.getCurrentProgress(), target) + "/" + target : "";
			String where = plugin.isGlobalTask(task.getTaskId()) ? NuzlockeTask.displayCategory(task.getCategory())
				: chunkNames.computeIfAbsent(task.getTaskId(), k -> chunkName(task));
			// Red when you can't do it yet, with why ("(Lvl 40)") ahead of where it is.
			boolean ready = canDo(task);
			graphics.setColor(hover && shift || !ready ? NO_LEVEL : Color.WHITE);
			for (int l = 0; l < name.size(); l++)
			{
				Skin.text(graphics, hover && shift && l == 0 ? Skin.fit(fm, "Remove " + name.get(0), width) : name.get(l),
					row.x + 8, row.y + 14 + 12 * l);
			}
			int lineY = row.y + 15 + 12 * name.size();
			String why = ready ? "" : levelNote(task) + " ";
			graphics.setColor(NO_LEVEL);
			Skin.text(graphics, why, row.x + 8, lineY);
			graphics.setColor(isHere(task, hereChunk) ? HERE : SUBTEXT);
			Skin.text(graphics, Skin.fit(fm, where, row.width - 19 - fm.stringWidth(why) - fm.stringWidth(progress)),
				row.x + 8 + fm.stringWidth(why), lineY);
			graphics.setColor(SUBTEXT);
			Skin.text(graphics, progress, row.x + row.width - 5 - fm.stringWidth(progress), lineY);
		}
		return total;
	}

	/** A small rounded button with a label, lit up while hovered. */
	private static void pill(Graphics2D graphics, Rectangle r, String text, boolean hover)
	{
		Skin.button(graphics, new Rectangle(r.x, r.y, r.width + 1, r.height + 1), hover, false);
		FontMetrics fm = graphics.getFontMetrics();
		graphics.setColor(hover ? Color.WHITE : SUBTEXT);
		Skin.text(graphics, text, r.x + (r.width - fm.stringWidth(text)) / 2 + 1, r.y + 13);
	}

	/** The side panel's area in whichever layout is on; a spot by the right edge if it's closed. */
	private Rectangle sidePanel()
	{
		for (int id : new int[]{InterfaceID.Toplevel.SIDE_PANELS, InterfaceID.ToplevelOsrsStretch.SIDE_PANELS,
			InterfaceID.ToplevelPreEoc.SIDE_PANELS, InterfaceID.ToplevelOsm.SIDE_PANELS})
		{
			Widget panel = client.getWidget(id);
			if (panel != null && !panel.isHidden())
			{
				return panel.getBounds();
			}
		}
		// A bank, shop, Grand Exchange or trade hides the side panels and shows its own
		// inventory in their place: sit over that instead.
		for (int group : new int[]{15, 301, 467, 336})
		{
			Widget side = client.getWidget(group, 0);
			if (side != null && !side.isHidden() && side.getWidth() > 0)
			{
				return side.getBounds();
			}
		}
		return new Rectangle(client.getCanvasWidth() - 214, client.getCanvasHeight() - 340, 204, 275);
	}

	// Interfaces that take over the screen and inventory: bank, deposit box, shop, Grand
	// Exchange and its collection box, seed vault and trade. Opening one closes the box.
	private static final Set<Integer> TAKEOVER = Set.of(12, 192, 300, 465, 402, 631, 335);

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (TAKEOVER.contains(event.getGroupId()))
		{
			questPane = null;
		}
		// Opening the world map closes the task window (the tasks key + click opens it again).
		if (event.getGroupId() == InterfaceID.WORLDMAP && open)
		{
			close();
		}
	}

	/** Switching side tab (prayer, inventory...) closes the quest pane. */
	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		if (event.getIndex() == VarClientID.TOPLEVEL_PANEL && !config.movableTaskBox())
		{
			questPane = null;
		}
	}

	// --- Small drawing helpers ------------------------------------------------

	private static void cross(Graphics2D graphics, int cx, int cy, int r, Color color)
	{
		graphics.setColor(color);
		graphics.drawLine(cx - r, cy - r, cx + r, cy + r);
		graphics.drawLine(cx - r, cy + r, cx + r, cy - r);
	}

	/** A small triangle centred on (cx, cy): down when open, else right; white when hovered. */
	private static void arrow(Graphics2D graphics, int cx, int cy, boolean open, boolean hover)
	{
		graphics.drawImage(ARROWS[(open ? 2 : 0) + (hover ? 1 : 0)], cx - 5, cy - 5, null);
	}

	private void drawScrollbar(Graphics2D graphics, Rectangle area, int contentHeight, int scroll)
	{
		if (contentHeight <= area.height)
		{
			return;
		}
		int thumbHeight = Math.max(20, area.height * area.height / contentHeight);
		int thumbY = area.y + (area.height - thumbHeight) * scroll / Math.max(1, contentHeight - area.height);
		Skin.scrollbar(graphics, area, thumbY, thumbHeight);
	}

	private static void drawCentered(Graphics2D graphics, String text, Rectangle area)
	{
		FontMetrics fm = graphics.getFontMetrics();
		Skin.text(graphics, text, area.x + (area.width - fm.stringWidth(text)) / 2, area.y + (area.height + fm.getAscent()) / 2 - 1);
	}


	/** Split text into lines that fit the width, breaking between words. */
	/** A task's name (with any missing levels) as its row shows it, wrapped onto up to three lines. */
	// Wrapped task names, per task and width: worked out once per list rebuild, not every frame
	// for every task (each one checks levels and quest readiness and measures every word).
	private final Map<String, List<String>> rowNames = new HashMap<>();

	private List<String> rowName(FontMetrics fm, NuzlockeTask task, int width)
	{
		return rowNames.computeIfAbsent(task.getTaskId() + "|" + width + "|" + fm.getFont().getSize(), k -> buildRowName(fm, task, width));
	}

	private List<String> buildRowName(FontMetrics fm, NuzlockeTask task, int width)
	{
		String name = (task.getName() == null ? task.getTaskId() : task.getName()) + (canDo(task) ? "" : " " + levelNote(task));
		List<String> lines = wrap(fm, name, width);
		return lines.size() <= 3 ? lines
			: Arrays.asList(lines.get(0), lines.get(1), Skin.fit(fm, String.join(" ", lines.subList(2, lines.size())), width));
	}

	static List<String> wrap(FontMetrics fm, String text, int width)
	{
		List<String> lines = new ArrayList<>();
		String line = "";
		for (String word : text.split(" "))
		{
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (fm.stringWidth(candidate) > width && !line.isEmpty())
			{
				lines.add(line);
				line = word;
			}
			else
			{
				line = candidate;
			}
		}
		if (!line.isEmpty())
		{
			lines.add(line);
		}
		return lines;
	}
}
