# ChunkBlazer

Visit the [Chunkblazer](https://chunkblazer.com) website for more info!

ChunkBlazer is a Randomized, Automated, Task rolling [RuneLite](https://github.com/runelite/runelite) plugin for Old School RuneScape.

The Runescape map is carved into 64x64 tile regions called chunks. You start with a single chunk and earn points by completing random tasks rolled to you. Spend those points to buy new chunks and grow your world one piece at a time.

# How to Play Video

[![How to Play ChunkBlazer](https://img.youtube.com/vi/c7kYMUxwu5I/hqdefault.jpg)](https://www.youtube.com/watch?v=c7kYMUxwu5I)

# Data & the ChunkBlazer server

ChunkBlazer is a server-backed plugin. We send data over HTTPS to
`api.chunkblazer.com` which **syncs your account progress** for saves, leaderboards, and
completion verification. It also **downloads** the task catalog and completion
sounds. Nothing is shared with third parties, and you can play
offline by disabling server verification. 

Full privacy policy details here: [PRIVACY.md](PRIVACY.md).

# Acknowledgements

The ChunkBlazer GPU renderer is heavily inspired by [Region Locker GPU](https://github.com/SlayToStay/region-locker) by slaytostay and [RuneLite's GPU plugin](https://github.com/runelite/runelite/wiki/GPU).
Note: The GPU is currently not available and will be pushed in a later update.

Quest chunk and requirement data in the task window is derived from [Quest Helper](https://github.com/Zoinkwiz/quest-helper) by Zoinkwiz, used under the BSD 2-Clause License (see LICENSE-quest-helper).
