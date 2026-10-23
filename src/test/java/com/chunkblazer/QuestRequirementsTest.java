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

import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestRequirementsTest
{
	private static final int LUMBRIDGE = 12850;

	@Mock
	private Client client;

	@Mock
	private ChunkBlazerPlugin plugin;

	private QuestRequirements quests;

	@BeforeEach
	void setUp()
	{
		quests = new QuestRequirements(client, plugin, new Gson());
		quests.load();
		lenient().when(plugin.isKnownRegion(anyInt())).thenReturn(true);
		lenient().when(plugin.isRegionUnlocked(anyInt())).thenReturn(true);
	}

	private static NuzlockeTask quest(String questName)
	{
		NuzlockeTask task = new NuzlockeTask();
		task.setTaskId("quest_" + questName.toLowerCase());
		task.setCompletionType("QUEST_CHECK");
		TaskConstraints c = new TaskConstraints();
		c.setQuest(questName);
		task.setConstraints(c);
		return task;
	}

	@Test
	void shippedDataLoads()
	{
		assertNotNull(quests.needFor(quest("COOKS_ASSISTANT")));
		assertNotNull(quests.needFor(quest("DRAGON_SLAYER_II")));
	}

	@Test
	void questIsReadyWhenItsChunkIsUnlocked()
	{
		assertTrue(quests.isReady(quest("COOKS_ASSISTANT")));
	}

	@Test
	void lockedChunkBlocksTheQuest()
	{
		lenient().when(plugin.isRegionUnlocked(LUMBRIDGE)).thenReturn(false);
		NuzlockeTask task = quest("COOKS_ASSISTANT");

		assertFalse(quests.isReady(task));
	}

	@Test
	void questWithoutDataIsNeverReady()
	{
		NuzlockeTask task = quest("NOT_A_REAL_QUEST");
		assertFalse(quests.isReady(task));
	}

	@Test
	void miniquestsNeedTheirQuestsFirst()
	{
		// Reported: both showed as available. They need Beneath Cursed Sands / Desert Treasure I first.
		assertFalse(quests.isReady(quest("INTO_THE_TOMBS")));
		assertFalse(quests.isReady(quest("THE_FROZEN_DOOR")));
	}

	@Test
	void questPointsAreChecked()
	{
		NuzlockeTask task = quest("DRAGON_SLAYER_I"); // needs 32 quest points
		lenient().when(client.getVarpValue(VarPlayerID.QP)).thenReturn(10);
		assertFalse(quests.isReady(task));

		lenient().when(client.getVarpValue(VarPlayerID.QP)).thenReturn(40);
		assertTrue(quests.isReady(task));
	}

	@Test
	void unknownPrerequisiteStateIsNotReady()
	{
		// Dragon Slayer II needs other quests first; their state isn't known until checked in game.
		assertFalse(quests.isReady(quest("DRAGON_SLAYER_II")));
	}

	@Test
	void nonQuestTasksAreUnaffected()
	{
		NuzlockeTask task = new NuzlockeTask();
		task.setCompletionType("NPC_KILL");
		assertNull(quests.needFor(task));
		assertTrue(quests.isReady(task));
	}
}
