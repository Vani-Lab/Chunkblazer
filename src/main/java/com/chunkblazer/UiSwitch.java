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
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;

/**
 * The ChunkBlazerUi everything in ChunkBlazer talks to. It passes each call to the ChunkBlazer
 * UI plugin ({@link RemoteUi}) while that plugin is running, else to {@link CoreOnlyUi}, and
 * swaps them over live when the UI plugin is turned on or off: whatever the old one had up is
 * taken down and the same is put up by the new one.
 *
 * <p>The UI plugin says "hello" when it starts and "bye" when it stops; when ChunkBlazer starts
 * after it, ChunkBlazer asks whether it's there. It also brings the UI's calls in to
 * {@link UiBridge}.
 */
@Singleton
public class UiSwitch implements ChunkBlazerUi
{
	private final CoreOnlyUi coreOnly;
	@Inject
	private RemoteUi remote;
	@Inject
	private UiBridge bridge;
	@Inject
	private EventBus eventBus;
	@Inject
	private ClientThread clientThread;
	@Inject
	private TaskLists lists;
	@Inject
	private TaskSidePanel sidePanel;

	private volatile ChunkBlazerUi current;
	// What ChunkBlazer has asked for, to hand over on a swap.
	private boolean started;
	private boolean gameUi;
	private boolean taskUi;

	@Inject
	UiSwitch(CoreOnlyUi coreOnly)
	{
		this.coreOnly = coreOnly;
		this.current = coreOnly;
	}

	/** The UI plugin: arriving, leaving, asking something, or sending its tutorial's state. */
	@Subscribe
	public void onPluginMessage(PluginMessage message)
	{
		if (!UiBridge.TO_CORE.equals(message.getNamespace()))
		{
			return;
		}
		switch (message.getName())
		{
			case "call":
				bridge.answer(message.getData());
				break;
			case "tutorial":
				remote.tutorialState(message.getData());
				break;
			case "hello":
				// Said from its startUp, before RuneLite has it listening: hand over just after.
				clientThread.invokeLater(() ->
				{
					if (started && bridge.isUiThere())
					{
						install(remote);
					}
				});
				break;
			case "bye":
				// It's already stopped listening (and takes its own UI down): just take ours back.
				install(coreOnly);
				break;
			default:
				break;
		}
	}

	// Game ticks since ChunkBlazer last looked for ChunkBlazer UI.
	private int ticksSinceLook;

	/**
	 * Every few seconds without the UI, look for it again. Its "hello" is normally enough, but
	 * one can go unheard while the client is still starting up (the plugins start one after the
	 * other, and a message is only heard once a plugin has finished starting).
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (current != coreOnly || ++ticksSinceLook < 8)
		{
			return;
		}
		ticksSinceLook = 0;
		if (started && bridge.isUiThere())
		{
			install(remote);
		}
	}

	/** The task filters are shared with the UI's task window: either can change them. */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (current == remote && "chunkblazer".equals(event.getGroup()) && "taskFilters".equals(event.getKey()))
		{
			lists.loadFilters();
			sidePanel.sideChanged();
		}
		// Where the tracked task shows (the UI's settings): the side panel's Selected section.
		if (RemoteUi.CONFIG_GROUP.equals(event.getGroup())
			&& ("trackedTaskShows".equals(event.getKey()) || "taskTrackerStyle".equals(event.getKey())
				|| "showTaskDetails".equals(event.getKey())))
		{
			sidePanel.sideChanged();
		}
	}

	/** Hand everything over to {@code next}. */
	private synchronized void install(ChunkBlazerUi next)
	{
		ChunkBlazerUi old = current;
		if (next == old)
		{
			return;
		}
		if (taskUi)
		{
			old.stopTaskUi();
		}
		if (gameUi)
		{
			old.showGameUi(false);
		}
		if (started)
		{
			old.shutDown();
		}
		current = next;
		if (started)
		{
			next.startUp();
		}
		if (gameUi)
		{
			next.showGameUi(true);
		}
		if (taskUi)
		{
			next.startTaskUi();
		}
	}

	@Override
	public synchronized void startUp()
	{
		eventBus.register(this);
		current = bridge.isUiThere() ? remote : coreOnly;
		started = true;
		current.startUp();
	}

	@Override
	public synchronized void shutDown()
	{
		eventBus.unregister(this);
		started = false;
		gameUi = false;
		taskUi = false;
		current.shutDown();
		current = coreOnly;
	}
	@Override
	public synchronized void showGameUi(boolean show)
	{
		gameUi = show;
		current.showGameUi(show);
	}

	@Override
	public synchronized void startTaskUi()
	{
		taskUi = true;
		current.startTaskUi();
	}

	@Override
	public synchronized void stopTaskUi()
	{
		taskUi = false;
		current.stopTaskUi();
	}

	@Override
	public void toggleWindow()
	{
		current.toggleWindow();
	}

	@Override
	public void setTaskButtonBounds(Rectangle bounds)
	{
		current.setTaskButtonBounds(bounds);
	}

	@Override
	public void openTask(NuzlockeTask task)
	{
		current.openTask(task);
	}

	@Override
	public boolean sidePanelShowsTracked()
	{
		return current.sidePanelShowsTracked();
	}

	@Override
	public boolean showTaskDetails()
	{
		return current.showTaskDetails();
	}

	@Override
	public void searchChanged(String search)
	{
		current.searchChanged(search);
	}

	@Override
	public TaskCardShow cards()
	{
		return current.cards();
	}

	@Override
	public TutorialHooks tutorial()
	{
		return current.tutorial();
	}

	@Override
	public void showTaskCompletion(NuzlockeTask task, int points, String region)
	{
		current.showTaskCompletion(task, points, region);
	}
}
