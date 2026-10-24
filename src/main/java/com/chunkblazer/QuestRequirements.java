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
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;

/**
 * What each quest needs before you can do it in a chunk account: the chunks its steps
 * happen in, skill levels, quests to finish first, quest points and combat level. UI
 * only: it reads quest_requirements.json and the player's own state, and is used by the
 * task window's "Quests (ready now)" filter and the requirement list under a quest.
 *
 * Data: chunks and requirements are derived from the Quest Helper plugin's step
 * coordinates and requirements (https://github.com/Zoinkwiz/quest-helper, BSD 2-Clause,
 * Copyright (c) 2020, Zoinkwiz). Into the Tombs and The Frozen Door, which Quest Helper
 * doesn't cover, were filled in by hand. Optional "maybe" chunks are supported by the
 * format but not currently used.
 */
@Slf4j
@Singleton
public class QuestRequirements
{
	private static final String RESOURCE = "quest_requirements.json";

	/** One quest's needs, as stored in quest_requirements.json. */
	static final class Need
	{
		List<Integer> chunks;
		List<Integer> maybe;
		Map<String, Integer> skills;
		List<String> quests;
		Integer questPoints;
		Integer combat;
	}

	private final Client client;
	private final ChunkBlazerPlugin plugin;
	private final Gson gson;

	private volatile Map<String, Need> needs = Collections.emptyMap();
	// Prerequisite quest states, refreshed a few per tick (getState runs a client script).
	private final Map<Quest, QuestState> states = new ConcurrentHashMap<>();
	private List<Quest> watched = Collections.emptyList();
	private int nextToCheck;

	@Inject
	QuestRequirements(Client client, ChunkBlazerPlugin plugin, Gson gson)
	{
		this.client = client;
		this.plugin = plugin;
		this.gson = gson;
	}

	/** Reads quest_requirements.json. Missing or broken data just means no quest info. */
	public void load()
	{
		try (InputStream in = QuestRequirements.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				log.warn("{} not found", RESOURCE);
				return;
			}
			Type type = new TypeToken<Map<String, Need>>()
			{
			}.getType();
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				Map<String, Need> loaded = gson.fromJson(reader, type);
				needs = loaded == null ? Collections.emptyMap() : loaded;
			}
		}
		catch (Exception e)
		{
			log.warn("Could not read {}", RESOURCE, e);
			needs = Collections.emptyMap();
		}

		Set<Quest> prereqs = new LinkedHashSet<>();
		for (Need need : needs.values())
		{
			if (need.quests != null)
			{
				for (String name : need.quests)
				{
					Quest quest = quest(name);
					if (quest != null)
					{
						prereqs.add(quest);
					}
				}
			}
		}
		watched = new ArrayList<>(prereqs);
		nextToCheck = 0;
	}

	/** Forget quest states, e.g. after logging out (the next account may differ). */
	public void reset()
	{
		states.clear();
		nextToCheck = 0;
	}

	/**
	 * Checks the state of up to {@code budget} prerequisite quests, taking turns so the
	 * whole list is covered every few ticks. Client thread, logged in only.
	 */
	public void refreshStates(int budget)
	{
		List<Quest> list = watched;
		for (int i = 0; i < budget && !list.isEmpty(); i++)
		{
			nextToCheck = nextToCheck % list.size();
			Quest quest = list.get(nextToCheck++);
			try
			{
				states.put(quest, quest.getState(client));
			}
			catch (RuntimeException e)
			{
			}
		}
	}

	/**
	 * True for quest tasks (they get the requirement list, with or without data): a "Complete
	 * {@code <quest>}" task. Other tasks can complete on a quest too (Defeat Damis, Obtain
	 * a Black Candle: QUEST_CHECK, but category Combat/Obtain); those are normal tasks, tracked
	 * and listed like any other, not quests.
	 */
	public static boolean isQuestTask(NuzlockeTask task)
	{
		return task != null && "QUEST_CHECK".equalsIgnoreCase(task.getCompletionType())
			&& ("Quest".equalsIgnoreCase(task.getCategory())
			|| (task.getTaskId() != null && task.getTaskId().startsWith("quest_")));
	}

	/** The needs of a quest task, or null if it isn't a quest task we have data for. */
	Need needFor(NuzlockeTask task)
	{
		if (!isQuestTask(task))
		{
			return null;
		}
		TaskConstraints c = task.getConstraints();
		return c == null || c.getQuest() == null ? null : needs.get(c.getQuest());
	}

	/**
	 * True if every chunk, level, prerequisite quest and point total is met. Tasks that
	 * aren't quests always pass; a quest with no data never does, since we can't tell.
	 */
	public boolean isReady(NuzlockeTask task)
	{
		if (!isQuestTask(task))
		{
			return true;
		}
		Need need = needFor(task);
		if (need == null)
		{
			return false;
		}
		if (need.chunks != null)
		{
			for (int region : need.chunks)
			{
				if (!reachable(region))
				{
					return false;
				}
			}
		}
		if (need.skills != null)
		{
			for (Map.Entry<String, Integer> e : need.skills.entrySet())
			{
				Skill skill = skill(e.getKey());
				if (skill != null && client.getRealSkillLevel(skill) < e.getValue())
				{
					return false;
				}
			}
		}
		if (need.quests != null)
		{
			for (String name : need.quests)
			{
				Quest quest = quest(name);
				if (quest != null && states.get(quest) != QuestState.FINISHED)
				{
					return false;
				}
			}
		}
		if (need.questPoints != null && questPoints() < need.questPoints)
		{
			return false;
		}
		return need.combat == null || combatLevel() >= need.combat;
	}

	/** Chunk name to its first listed region, one entry per chunk (multi-region chunks share a name). */
	private static Map<String, Integer> chunks(List<Integer> regions, IntFunction<String> chunkName)
	{
		Map<String, Integer> out = new LinkedHashMap<>();
		if (regions != null)
		{
			for (int region : regions)
			{
				String name = chunkName.apply(region);
				out.putIfAbsent(name == null || name.isEmpty() ? "Region " + region : name, region);
			}
		}
		return out;
	}

	/** Locked chunks of every unfinished prerequisite quest, recursively, minus the quest's own. */
	private void collectEarlierChunks(Need need, Set<String> visited, Map<String, Integer> own,
		Map<String, Integer> out, IntFunction<String> chunkName)
	{
		if (need.quests == null)
		{
			return;
		}
		for (String name : need.quests)
		{
			Quest quest = quest(name);
			Need prereq = needs.get(name);
			if (quest == null || prereq == null || states.get(quest) == QuestState.FINISHED || !visited.add(name))
			{
				continue;
			}
			for (Map.Entry<String, Integer> e : chunks(prereq.chunks, chunkName).entrySet())
			{
				if (!reachable(e.getValue()) && !own.containsKey(e.getKey()))
				{
					out.putIfAbsent(e.getKey(), e.getValue());
				}
			}
			collectEarlierChunks(prereq, visited, own, out, chunkName);
		}
	}

	/**
	 * Unlocked, or not a lockable chunk at all (dungeons, and the odd region that isn't
	 * part of the chunk grid), so it never holds a quest back.
	 */
	public boolean reachable(int region)
	{
		return !plugin.isKnownRegion(region) || plugin.isRegionUnlocked(region);
	}

	private int questPoints()
	{
		return client.getVarpValue(VarPlayerID.QP);
	}

	private int combatLevel()
	{
		Player player = client.getLocalPlayer();
		return player == null ? 0 : player.getCombatLevel();
	}

	private static Quest quest(String name)
	{
		try
		{
			return Quest.valueOf(name);
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	private static Skill skill(String name)
	{
		try
		{
			return Skill.valueOf(name);
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}
}
