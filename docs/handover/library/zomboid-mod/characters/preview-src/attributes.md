# Harvestman: attributes

Oct 28, 2026 · @Josiah Longfield

The Harvestman is a rare predator that thinks for itself. It roams dark buildings, remembers each player, and decides on its own when to stalk, set a trap, feed or run. Every number below is a starting point: edit any of them in place, or answer the numbered questions at the end.

It is one shared creature for everyone on the map, like the zombie population. It keeps living and moving when nobody is near, and it never depends on daylight.

## The brain

Its brain has four parts: what it senses, what it remembers, what it needs, and who it is. Every second or two it scores each thing it could do and picks the best, so no two encounters play out the same.

&#91;embedded content: how it thinks · senses, memory, needs and traits feed one choice\]

Every result feeds back in: a feed calms its hunger, a wound teaches it to fear that player, and a trap that worked gets used again.

### Needs

| Need | Rises when | Falls when |
| --- | --- | --- |
| Hunger | Time passes: full to starving in about one in-game day | It feeds on a body |
| Fear | It is hurt, a torch beam holds on it, 3 or more players stand together, fire or explosions | It is in darkness and alone, or it wins a fight |
| Curiosity | Strange sounds, a new player in its area, doors left open | It goes and looks |
| Territory | Someone loots, fights or sleeps near its lair | The intruder leaves or dies |

### Traits

Each Harvestman rolls its own traits once, from 1 to 5. One might be a patient ceiling hanger, another a glutton that follows gunfire to steal bodies.

| Trait | At 1 | At 5 |
| --- | --- | --- |
| Boldness | Backs off from a torch at once | Ignores torches when hungry and comes into lit rooms |
| Patience | Strikes early and is easy to spot | Waits on a ceiling for hours |
| Cunning | Simple ambushes | Layered lures, fake sounds, moves your bait |
| Cruelty | Kills quickly | Toys with you and steals your kills while you watch |
| Appetite | Feeds rarely | Always hungry, easy to bait with bodies |

### Memory

- **Of each player:** where they were last seen, the routes and rooms they use, where they sleep, what they shoot it with, how badly they have hurt it, and what scared it off. One player becomes its own, as described under Its chosen player.
- **Of places:** good ceilings and dead ends for ambushes, the doors players use, where bodies lie, and where it was hurt.
- Memories fade over a few in-game days and are saved with the world, so it can recognise you next session.

### What it can choose to do

| Goal | What it does |
| --- | --- |
| Roam | Moves between dark buildings by alleys, the subway and service tunnels |
| Investigate | Creeps toward a noise, out of sight |
| Stalk | Follows a player at the edge of the torch, watching |
| Lure | Drags a body across a doorway, knocks, opens a door, mimics a zombie |
| Set a trap | Picks a room on your usual route, leaves bait and loot, hangs above the door |
| Ambush | Drops from the ceiling or strikes from a dark side room |
| Feed | Drains a body's head in its mouth |
| Drag | Hauls a body, or you, back to its lair |
| Hide and heal | Retreats to its lair or a dark crawlspace |
| Flee | Runs when fear wins, and remembers what caused it |
| Move lair | Finds a new lair once its old one is found or attacked twice |

It works with two brains, like the alien in Alien: Isolation. A hidden director always knows roughly where players are and nudges it toward them, so it never wanders off for good. The creature itself only acts on what it sees, hears and smells, so if it finds you, it's because you made a noise, bled, or used a route it learned.

It is not a chatbot. Running it on a language model would be slow and need an online service. Needs, traits and memory give the same thinking feel and run inside the game, even on a server with nobody watching.

## Learning and evolving

Every Harvestman starts as a clumsy scavenger and gets worse the longer it lives. It learns each player's habits, and it grows through five stages as it feeds and survives.

### How it learns you

| It notices | So it learns to |
| --- | --- |
| The routes you take between your base and the places you loot | Wait on those routes and trap rooms you will walk into |
| Where you sleep or hole up | Visit while you are there and leave signs: a body at the door, scratches on the walls |
| You always hold a doorway or corridor | Come through the ceiling, a window or a side room |
| You always point a torch ahead | Come from behind or above, or wait until the torch is off |
| You chase sounds | Use sounds to pull you into its traps |
| You loot every shelf | Bait shelves and crates under its perch |
| You travel in a group | Split you up by luring one away with a sound or a body |
| A gun or trick hurt it | Avoid that player, or rush them before they can use it |

It follows you between buildings at a distance and learns how you move: how fast, where you stop, and which rooms you clear first. What it learns is saved with the world, and it fades if you change your habits.

### Stages

| Stage | It gets there by | What it starts doing |
| --- | --- | --- |
| 1. Scavenger | Starts here | Feeds on bodies, ambushes from ceilings, flees easily |
| 2. Stalker | 5 feeds and surviving 1 fight | Follows players between buildings, perches in dark corners, drags bodies across doorways as lures |
| 3. Trapper | 15 feeds and 3 fights | Sets traps on your routes, mimics sounds, poses bodies in rooms you are about to enter |
| 4. Puppeteer | 30 feeds and having hurt a player | Hangs bodies in stairwells and from ceilings, leaves bodies in your base, splits up groups |
| 5. Apex | 50 feeds | 50% more health, much bolder, can drag a living player away, keeps a second lair |

**Choices**

- Growth: (A) the five stages above, my pick, or (B) it only learns and never grows stronger.
- Starving: (A) three days without food drops it a stage, my pick, or (B) a stage, once reached, is kept.

## Its chosen player

Each Harvestman picks one player and makes them its own. It hunts them first, saves its worst tricks for them, and remembers them by their account, so a different name or days offline don't throw it off.

**How it chooses.** It weighs who has hurt it most or escaped it, who spends the most time alone in its territory, and whose habits it knows best. It only changes its mind if someone else hurts it badly, or if its player stays offline for 7 days.

**While they are offline**, it prepares. It visits their base and the last place it saw them, learns the area and stages bodies there. It never harms them or their things while they are away.

**When they log back in**, it notices within minutes and starts moving toward them. It builds up slowly: sounds and signs first, then glimpses, then traps.

| Only its player gets | For example |
| --- | --- |
| Sounds | Knocks behind them, clicks inside the walls, breathing on the far side of a door |
| Glimpses | Red eyes in a corner that vanish, a leg pulling back round a doorway |
| False alarms | A body that seems to have moved when they look back |
| Fake company | A friend's footsteps behind them when nobody is there |

These ride on the paranoia layer, aimed at one player. The real things stay real for everyone: bodies posed facing that player's bed, a trail of bodies to wherever they are, drag marks up to their door. Other players can stumble on it while it travels or at its lair. It fights back or slips away, then goes back to its player, and it only hunts someone else when it is starving or attacked.

**Commands** (admins):

- `/zc harvestman target <player>` makes the nearest Harvestman hunt that player.
- `/zc harvestman target clear` lets it choose for itself again.
- `/zc harvestman info` shows the nearest one's stage, traits, hunger and chosen player.

## Size and getting around

Standing, it is 3.6 blocks tall with a 4.2-block leg span and its belly 1.6 blocks off the floor. It folds its legs to fit any corridor 2 blocks high and squeezes through a 1-wide door, slowly and quietly.

- It climbs walls and ceilings and perches high in dark corners, legs folded, waiting. Its leg points punch small holes as it goes: marks you can learn to spot.
- It opens doors itself, quietly. It only smashes one when it is starving or chasing.
- Folded up, it fits through a broken window or a 1 by 2 gap.
- Outdoors it keeps to dark alleys, under bridges and the subway, and crosses open streets fast.

**Choices**

- Roam range: (A) one building and its block, (B) a district of dark buildings, my pick, or (C) the whole map, subway and sewers included.
- Tight spaces: (A) doors, corridors and broken windows only, my pick, or (B) also 1 by 1 vents and crawlspaces.

## Health, armour and weak spots

It has 300 health, 15 times a walker, and aimed fire is what kills it. With a lever-action (22 damage) that is about 5 hits to the eyes, 9 to the red underside, or 55 to the plates. Shots never knock it back.

| Part | Damage it takes | What happens |
| --- | --- | --- |
| Eyes | 300% | Blinded for 3 seconds, and it backs off |
| Red underside | 150% | Only open when it rears up or hangs from a ceiling |
| Skull | 100% |  |
| Armour plates | 25%, full through a rust hole | Sparks and a clang |
| Each leg | Its own 80 health, 150% at the knee gear | The leg breaks off at the knee |

Losing legs changes how it fights:

1. One gone: slower, and it can no longer climb ceilings.
2. Two gone: it retreats to heal.
3. Three gone: it crawls and can be finished off.

At rest it heals 2 health a minute, and each body it feeds on heals 60. Fire does double damage and always makes it flee. At 0 it collapses with its legs twitching, and the body stays where it fell.

**Choices**

- Regrowing legs: (A) one leg per full feed, my pick, or (B) a lost leg stays lost until it dies.

## Speed

It is slow while it stalks and much faster than you for a moment when it strikes. You can't outrun a lunge, but you can break its line of sight. For comparison, you walk at 4.3 blocks a second and sprint at 5.6.

| Doing | Blocks a second | Notes |
| --- | --- | --- |
| Stalking | 1.5 to 2.5 | Almost silent |
| Roaming | 3 | Joint clicks carry about 16 blocks |
| Climbing walls and ceilings | 2.5 |  |
| Dragging a body | 2 | Leaves a blood trail |
| Striking | 9 for 1.5 seconds | Then a 4-second pause before it can lunge again |
| Fleeing | 7 |  |

## Senses and fears

It hunts by sound, smell and sight in the dark, and it hates light it can't avoid.

| Sense | Range | Notes |
| --- | --- | --- |
| Hearing | Uses the game's noise ranges: a shotgun about 130 blocks, sprinting 12, walking 6, crouching 2 | Gunfire means bodies, so a hungry one comes to it |
| Smell | Blood from a bleeding player at 40 blocks, a body at 64 |  |
| Sight | 24 blocks in the dark, about 8 in a lit room | Its red eyes are what you'll usually see first |

What makes it back off:

- A torch beam held on it for 2 seconds, unless it is starving, cornered or very bold.
- Bright rooms, fire and explosions. After an explosion it flees and remembers where it happened.
- Groups of 3 or more players. It waits for one to split off.

Nothing depends on daylight. Outdoors in the grey "day" it is a little more uneasy, and that is all.

**Choices**

- Zombies: (A) they ignore it and it ignores them, my pick, or (B) they shuffle away from it, a tell that it is near.

## Attacks

It would rather grab you than fight you. Most attacks end with you held in its scythe arms, your head in its mouth, while you struggle and your friends try to shoot it off.

| Attack | Damage | How it works |
| --- | --- | --- |
| Leg stab | 6 (3 hearts) and a bleeding wound | Reach 3 blocks; used when cornered or to finish you |
| Scythe grab | None at first | Within 2 blocks it hooks you and lifts you off your feet |
| Ceiling drop | Grab | Drops from above a doorway. The only warning is falling dust, a drip and a click |
| Head bite | 2 a second, and your screen goes dark and red | Follows a grab. Mash to struggle free in about 3 seconds |
| Drag | None | A downed or dead player is hauled to its lair, gear and all, for the corpse run |

Getting free while held:

- Struggling breaks the grab in about 3 seconds, or faster with a melee hit to the skull.
- A friend's hit to its eyes or underside makes it drop you at once.
- Breaking free leaves you bleeding, and it backs off for a moment to re-plan.

**Choices**

- Head bite: (A) a slow drain you can escape, my pick, or (B) a kill after 5 seconds unless someone saves you.

## Feeding and mind games

Bodies are its food, its bait and its way into your head. It only eats the dead: zombies you killed and players who died. So the more you kill near its home, the more it feeds and the faster it grows.

- One body fills it for about a day. Each feed heals 60 health and counts toward its next stage.
- It leaves drained grey husks behind, the surest sign it lives nearby.
- You can starve it by burning or hiding bodies. A hungry one takes risks and comes into the open, and three days without food weakens it.

| Trick | What you see | From stage |
| --- | --- | --- |
| Steals your kills | A zombie you just shot is dragged out of sight while you watch | 1 |
| Drag trails | A body slides across a doorway at the edge of your torch | 2 |
| Watching | Two red eyes in a dark corner, gone when you look back | 2 |
| Posed bodies | Bodies sat at a table, facing a window, lined up in a corridor, turned toward where you will come in | 3 |
| Moved bodies | A body you left on the stairs is now in your doorway | 3 |
| Sounds | Knocks, a door creaking open, a moan from the wrong direction, footsteps overhead | 3 |
| Bait rooms | Real loot laid out under its perch in a room on your route | 3 |
| Hanging bodies | Bodies hung in stairwells, from ceiling lights and over atriums, turning slowly | 4 |
| Gifts | A body left at your base door, or your dead friend propped up facing your bed | 4 |

Everyone sees these, because they are real. That sets them apart from the paranoia visions, which only one player sees, so you never quite know which is which.

## Where it lives

It makes its lair in big, dark buildings: hotels, hospitals, office towers, Union Station, the stadium's underbelly, cargo ships and the subway. It picks a dark room high up or deep down and fills it with husks and its victims' gear.

- One lair per Harvestman. It moves house once the old lair is found or attacked twice.
- It keeps living when no player is near, moving between buildings as a cheap background record like the zombie population does.
- A killed Harvestman stays dead. It follows the population respawn setting, which is off by default.

**Choices**

- How many: (A) one per downtown or city and one on the island, about 25 per world, my pick, (B) very rare, 1 to 3 per world, or (C) one per town too.

## Rewards

Killing one, or raiding its lair while it is out, is one of the best paydays in the game.

| Where | What you get |
| --- | --- |
| Its lair | Everything its victims carried, a dead player's gear included, plus 2 to 4 loot rolls at Rare or better |
| Its body | One guaranteed Epic or better roll, a small chance at Legendary, and more the higher its stage |
| Its skull | A trophy to mount at your base |
| Its parts | Gears, pistons, plates and sinew for crafting, such as barbed barricade spikes or plate armour |

**Choices**

- Bunker maps: (A) a lair can hold a marked bunker map, since maps come from hard places, my pick, or (B) no maps from it.

## Tells and settings

You should always be able to tell it is around before it strikes. The tells are joint clicks, falling dust, blood drips, drag marks and husks, holes punched in walls and ceilings, red eye glints, and breathing in the ducts. The audio work supplies the sounds.

Everything above gets a setting to tune: on or off, how many, health, damage, bite mode, roam range, whether it learns, how fast it evolves, and respawn days.

## Your picks

Answer with letters, like "1B, 2A", or just tell me what to change. My picks are marked in each section.

1. Roam range: one building, a district, or the whole map?
2. Tight spaces: doors and windows only, or vents and crawlspaces too?
3. Can it regrow lost legs by feeding?
4. Do zombies ignore it, or shuffle away from it?
5. Five growth stages, or learning only?
6. Does starving drop it a stage?
7. Head bite: slow drain, or a kill after 5 seconds?
8. How many: about 25 per world, 1 to 3, or one per town too?
9. Can a lair hold a bunker map?
10. Its traits: hidden, or hinted by things you find, like a survivor's notes on "the patient one"?
11. Its chosen player: can it switch on its own after 7 days offline or a worse grudge, or keep one player until it dies?
12. Should the chosen player get a sign they've been picked, like a body left at their door, or find out the hard way?
13. With about 25 in a world, can several pick the same player, or does each pick a different one when it can?
