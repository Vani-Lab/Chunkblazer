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

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.chatbox.ChatboxPanelManager;

/**
 * ChunkBlazer's start without ChunkBlazer UI (whose tutorial does all this): arriving in
 * Lumbridge with no mode picked, a few lines on how to play and a chatbox prompt to pick
 * Server Sync and a mode, the same choices as the side panel. Once per login; "Later" leaves
 * it to the side panel.
 */
@Singleton
public class CoreModePrompt
{
	// Lumbridge, where new accounts arrive off Tutorial Island.
	private static final int LUMBRIDGE = 12850;
	// Tutorial Island's progress (VarPlayerID.TUTORIAL) once you've left it.
	private static final int ISLAND_DONE = 1000;
	// Game ticks in Lumbridge first, so it isn't over the arrival.
	private static final int SETTLE_TICKS = 3;

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ChatboxPanelManager chatboxPanelManager;
	@Inject
	private ChunkBlazerPlugin plugin;
	@Inject
	private ChunkBlazerConfig config;
	@Inject
	private CompanionPrompt companions;

	private boolean shown;
	private int ticksHere;

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			shown = false;
			ticksHere = 0;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Player player = client.getLocalPlayer();
		// The add-ons box comes first: nothing here until it's skipped or both are on.
		if (shown || companions.isShowing() || player == null || player.getName() == null || plugin.isModeLocked()
			|| client.getVarpValue(VarPlayerID.TUTORIAL) < ISLAND_DONE)
		{
			return;
		}
		if (player.getWorldLocation().getRegionID() != LUMBRIDGE)
		{
			ticksHere = 0;
			return;
		}
		boolean syncUndecided = !config.apiEnabled() && !plugin.isPlayOffline();
		// With sync on, wait for the server: it may already have a mode for this account.
		if (++ticksHere < SETTLE_TICKS || !syncUndecided && !plugin.canRollMissing())
		{
			return;
		}
		shown = true;
		say("Welcome to ChunkBlazer! You play one chunk at a time: do tasks for points, then spend them "
			+ "to unlock the chunks around you.");
		say("Pick how you'll play to start. You can also do it any time from the ChunkBlazer side panel.");
		if (syncUndecided)
		{
			askSync();
		}
		else
		{
			askMode();
		}
	}

	/** Whether this prompt has the player (no mode yet, in Lumbridge): ChunkBlazer's own notices hold off. */
	boolean handlesHere()
	{
		Player player = client.getLocalPlayer();
		return !plugin.isModeLocked() && player != null && player.getWorldLocation().getRegionID() == LUMBRIDGE;
	}

	private void say(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[ChunkBlazer] " + message, null);
	}

	/** Server Sync first: the starting tasks wait on it. */
	private void askSync()
	{
		chatboxPanelManager.openTextMenuInput("Save your ChunkBlazer progress online?")
			.option("Enable Server Sync", () -> SwingUtilities.invokeLater(() ->
			{
				if (confirmSync())
				{
					plugin.enableServerSync();
					clientThread.invokeLater(this::askMode);
				}
			}))
			.option("Play offline (this computer only)", () ->
			{
				plugin.choosePlayOffline();
				clientThread.invokeLater(this::askMode);
			})
			.option("Later", () ->
			{
			})
			.build();
	}

	/** RuneLite's wording for a 3rd-party server, as the side panel shows it. Swing thread. */
	private boolean confirmSync()
	{
		return JOptionPane.showConfirmDialog(client.getCanvas(), ChunkBlazerConfig.SERVER_SYNC_WARNING, "Enable Server Sync",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
	}

	private void askMode()
	{
		if (plugin.isModeLocked())
		{
			return;
		}
		boolean ironman = isIronman();
		chatboxPanelManager.openTextMenuInput("Choose your ChunkBlazer mode (it's permanent)")
			.option("Casual: any account, play at your own pace", () -> confirm(GameMode.CASUAL))
			.option(ironman ? "Competitive: checked, on the leaderboards" : "Competitive (needs a new ironman)",
				() -> confirm(GameMode.NUZLOCKE))
			.option("Later (ChunkBlazer side panel)", () ->
			{
			})
			.build();
	}

	private void confirm(GameMode mode)
	{
		if (mode == GameMode.NUZLOCKE && !isIronman())
		{
			say("Competitive needs a new ironman account. This account can play Casual.");
			clientThread.invokeLater(this::askMode);
			return;
		}
		clientThread.invokeLater(() -> chatboxPanelManager.openTextMenuInput("Play " + mode.getName() + "? You can't change it later.")
			.option("Yes, play " + mode.getName(), () -> lock(mode))
			.option("No, go back", () -> clientThread.invokeLater(this::askMode))
			.build());
	}

	private void lock(GameMode mode)
	{
		if (mode != GameMode.NUZLOCKE)
		{
			plugin.lockGameMode(mode);
			return;
		}
		// Competitive is checked by the ChunkBlazer server, so it needs Server Sync.
		if (plugin.isServerSyncEnabled())
		{
			plugin.lockCompetitive();
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			if (confirmSync())
			{
				plugin.enableServerSyncAndLockCompetitive();
			}
		});
	}

	private boolean isIronman()
	{
		int type = client.getVarbitValue(VarbitID.IRONMAN);
		return type >= 1 && type <= 6;
	}
}
