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
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.util.LinkBrowser;

/**
 * The Points orb's "Tasks" without ChunkBlazer UI: a chatbox prompt offering its Plugin Hub
 * page (plugins can't install other plugins, so the player clicks Install there).
 */
@Singleton
public class UiInstallPrompt
{
	/** ChunkBlazer UI's Plugin Hub page. */
	static final String HUB_PAGE = "https://runelite.net/plugin-hub/show/chunkblazer-ui";

	@Inject
	private ChatboxPanelManager chatboxPanelManager;
	@Inject
	private ClientThread clientThread;

	/** Any thread. */
	public void show()
	{
		// The title is one line of the chatbox: kept short so it isn't cut off at the sides.
		clientThread.invokeLater(() -> chatboxPanelManager.openTextMenuInput("The task window needs ChunkBlazer UI")
			.option("Open its Plugin Hub page", () -> LinkBrowser.browse(HUB_PAGE))
			.option("Not now", () ->
			{
			})
			.build());
	}
}
