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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The ChunkBlazer UI plugin, as ChunkBlazer sees it while that plugin is running: each call is
 * passed on to it as a message (see {@link UiBridge}).
 *
 * <p>ChunkBlazer asks the tutorial things many times a frame (is this chunk a practice
 * unlock?), so the UI sends its tutorial's state whenever it changes and the answers come from
 * that copy. The task cards are asked directly: that's once a frame, or a click.
 */
@Singleton
public class RemoteUi implements ChunkBlazerUi
{
	@Inject
	private UiBridge bridge;
	@Inject
	private javax.inject.Provider<ChunkBlazerPlugin> plugin;
	@Inject
	private net.runelite.client.config.ConfigManager configManager;

	/** ChunkBlazer UI's settings group. */
	static final String CONFIG_GROUP = "chunkblazerui";

	private final Tutorial tutorial = new Tutorial();
	private final TaskCardShow cards = new TaskCardShow()
	{
		@Override
		public boolean isActive()
		{
			return Boolean.TRUE.equals(cards("isActive").get("r"));
		}

		@Override
		public boolean onClick(int x, int y)
		{
			Map<String, Object> data = new HashMap<>();
			data.put("op", "onClick");
			data.put("x", x);
			data.put("y", y);
			return Boolean.TRUE.equals(bridge.post("cards", data).get("r"));
		}

		@Override
		public void clear()
		{
			cards("clear");
		}

		@Override
		public void onSweepStarted(boolean test, long durationMs)
		{
			Map<String, Object> data = new HashMap<>();
			data.put("op", "sweepStarted");
			data.put("test", test);
			data.put("durationMs", durationMs);
			bridge.post("cards", data);
		}

		@Override
		public void onSweepFinished()
		{
			cards("sweepFinished");
		}

		private Map<String, Object> cards(String op)
		{
			Map<String, Object> data = new HashMap<>();
			data.put("op", op);
			return bridge.post("cards", data);
		}
	};

	private void post(String name)
	{
		bridge.post(name, null);
	}

	private void post(String name, String key, Object value)
	{
		Map<String, Object> data = new HashMap<>();
		data.put(key, value);
		bridge.post(name, data);
	}

	@Override
	public void startUp()
	{
		boundsSent = false;
		post("start");
	}

	@Override
	public void shutDown()
	{
		post("stop");
		tutorial.forget();
	}

	@Override
	public void showGameUi(boolean show)
	{
		post("gameUi", "show", show);
	}

	@Override
	public void startTaskUi()
	{
		post("taskUi", "show", true);
	}

	@Override
	public void stopTaskUi()
	{
		post("taskUi", "show", false);
	}

	@Override
	public void toggleWindow()
	{
		post("toggleWindow");
	}

	// The Points orb's bounds as last sent: they're set every tick, but rarely change.
	private Rectangle sentBounds;
	private boolean boundsSent;

	@Override
	public synchronized void setTaskButtonBounds(Rectangle bounds)
	{
		if (boundsSent && java.util.Objects.equals(bounds, sentBounds))
		{
			return;
		}
		boundsSent = true;
		sentBounds = bounds == null ? null : new Rectangle(bounds);
		post("taskButtonBounds", "bounds", sentBounds == null ? null : new Rectangle(sentBounds));
	}

	@Override
	public void openTask(NuzlockeTask task)
	{
		post("openTask", "task", bridge.toJson(task));
	}

	/**
	 * The UI's "Tracked task in" setting (Widget unless set): the side panel lists the tracked
	 * task for Side panel or Both, and whenever the UI's task box is turned off.
	 */
	@Override
	public boolean sidePanelShowsTracked()
	{
		String shows = configManager.getConfiguration(CONFIG_GROUP, "trackedTaskShows");
		String box = configManager.getConfiguration(CONFIG_GROUP, "taskTrackerStyle");
		return "SIDE_PANEL".equals(shows) || "BOTH".equals(shows) || "OFF".equals(box);
	}

	@Override
	public boolean showTaskDetails()
	{
		return !"false".equals(configManager.getConfiguration(CONFIG_GROUP, "showTaskDetails"));
	}

	@Override
	public void searchChanged(String search)
	{
		post("search", "search", search);
	}

	@Override
	public TaskCardShow cards()
	{
		return cards;
	}

	@Override
	public TutorialHooks tutorial()
	{
		return tutorial;
	}

	@Override
	public void showTaskCompletion(NuzlockeTask task, int points, String region)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("task", bridge.toJson(task));
		data.put("points", points);
		data.put("region", region);
		bridge.post("showTaskCompletion", data);
	}

	/** The UI's tutorial state, as it last sent it. */
	void tutorialState(Map<String, Object> state)
	{
		tutorial.update(state);
	}

	/** ChunkBlazer's copy of the UI's tutorial state. */
	private final class Tutorial implements TutorialHooks
	{
		private volatile Set<Integer> unlocked = Collections.emptySet();
		private volatile boolean preview;
		private volatile boolean onIsland;
		private volatile boolean holdsUnlock;
		private volatile boolean quiet;
		private volatile List<String> cards = Collections.emptyList();
		private volatile Set<String> completed = Collections.emptySet();
		private volatile List<String> active = Collections.emptyList();
		private volatile Map<String, NuzlockeTask> tasks = Collections.emptyMap();
		private volatile Map<String, String> targets = Collections.emptyMap();
		// The JSON each practice task was read from, so an unchanged one stays the same object.
		private Map<String, String> taskJson = new HashMap<>();

		synchronized void forget()
		{
			update(Collections.emptyMap());
		}

		@SuppressWarnings("unchecked")
		synchronized void update(Map<String, Object> state)
		{
			unlocked = Collections.unmodifiableSet(new LinkedHashSet<>(collection(state.get("unlocked"))));
			boolean wasOnIsland = onIsland;
			boolean wasPreview = preview;
			preview = Boolean.TRUE.equals(state.get("preview"));
			onIsland = Boolean.TRUE.equals(state.get("onIsland"));
			if (wasOnIsland != onIsland || wasPreview != preview)
			{
				// The side panel shows the tutorial's tasks (and hides Server Sync) on the island:
				// it's redrawn once this copy knows, not just when the UI said so a moment before.
				ChunkBlazerPanel panel = plugin.get().getPanel();
				if (panel != null)
				{
					panel.updatePanel();
				}
			}
			holdsUnlock = Boolean.TRUE.equals(state.get("holdsUnlock"));
			quiet = Boolean.TRUE.equals(state.get("quiet"));
			cards = Collections.unmodifiableList(new ArrayList<>(collection(state.get("cards"))));
			completed = Collections.unmodifiableSet(new LinkedHashSet<>(collection(state.get("completed"))));
			active = Collections.unmodifiableList(new ArrayList<>(collection(state.get("active"))));
			Object rawTasks = state.get("tasks");
			Map<String, NuzlockeTask> read = new HashMap<>();
			Map<String, String> json = new HashMap<>();
			if (rawTasks instanceof Map)
			{
				for (Map.Entry<?, ?> e : ((Map<?, ?>) rawTasks).entrySet())
				{
					String id = String.valueOf(e.getKey());
					String text = String.valueOf(e.getValue());
					NuzlockeTask task = text.equals(taskJson.get(id)) ? tasks.get(id) : bridge.fromJson(text);
					if (task != null)
					{
						read.put(id, task);
						json.put(id, text);
					}
				}
			}
			tasks = Collections.unmodifiableMap(read);
			taskJson = json;
			Object rawTargets = state.get("targets");
			targets = rawTargets instanceof Map ? new HashMap<>((Map<String, String>) rawTargets) : Collections.emptyMap();
		}

		@SuppressWarnings("unchecked")
		private <T> Collection<T> collection(Object o)
		{
			return o instanceof Collection ? (Collection<T>) o : Collections.emptyList();
		}

		@Override
		public boolean isPracticeUnlocked(int regionId)
		{
			return unlocked.contains(regionId);
		}

		@Override
		public boolean isPreview()
		{
			return preview;
		}

		@Override
		public List<NuzlockeTask> practiceActive()
		{
			List<NuzlockeTask> list = new ArrayList<>();
			Map<String, NuzlockeTask> byId = tasks;
			for (String id : active)
			{
				NuzlockeTask task = byId.get(id);
				if (task != null)
				{
					list.add(task);
				}
			}
			return list;
		}

		@Override
		public List<String> practiceCards()
		{
			return new ArrayList<>(cards);
		}

		@Override
		public Set<String> practiceCompleted()
		{
			return new LinkedHashSet<>(completed);
		}

		@Override
		public NuzlockeTask practiceTask(String id)
		{
			return tasks.get(id);
		}

		@Override
		public String practiceTarget(String id)
		{
			return targets.get(id);
		}

		@Override
		public void revealPractice(String id)
		{
			// The UI sends its new state back before this returns.
			post("revealPractice", "id", id);
		}

		@Override
		public boolean isOnIsland()
		{
			return onIsland;
		}

		@Override
		public boolean holdsUnlock()
		{
			return holdsUnlock;
		}

		@Override
		public boolean isQuiet()
		{
			return quiet;
		}
	}
}
