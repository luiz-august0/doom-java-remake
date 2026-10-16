package doom.game.mapspecial;

import doom.game.actor.Actor;
import doom.game.actor.SpecialThinker;
import doom.game.actor.Thinker;
import doom.game.actor.TraceSink;
import doom.game.player.Player;
import doom.game.view.Card;
import doom.game.view.SoundId;
import doom.game.world.Line;
import doom.game.world.Sector;
import doom.game.world.World;
import doom.math.DoomFatalException;
import doom.math.Fixed;
import doom.math.annotation.VanillaRef;

/** {@code p_doors.c}: vertical doors. */
final class Doors {

    /** {@code VDOORSPEED}. */
    static final int VDOORSPEED = Fixed.FRACUNIT * 2;
    /** {@code VDOORWAIT}. */
    static final int VDOORWAIT = 150;

    private final World world;
    private final Sectors sectors;

    Doors(World world, Sectors sectors) {
        this.world = world;
        this.sectors = sectors;
    }

    /** {@code vldoor_t} with {@code T_VerticalDoor} as its function. */
    final class Door extends SpecialThinker {
        int type;
        Sector sector;
        int topHeight;
        int speed;
        /** 1 = up, 0 = waiting at top, -1 = down, 2 = initial wait. */
        int direction;
        int topWait;
        int topCountdown;

        @Override
        @VanillaRef("p_doors.c:T_VerticalDoor")
        protected void think() {
            switch (direction) {
                case 0 -> {
                    // WAITING
                    if (--topCountdown == 0) {
                        switch (type) {
                            case SpecialTypes.Door.BLAZE_RAISE -> {
                                direction = -1; // time to go back down
                                world.startSectorSound(sector, SoundId.BDCLS);
                            }
                            case SpecialTypes.Door.NORMAL -> {
                                direction = -1; // time to go back down
                                world.startSectorSound(sector, SoundId.DORCLS);
                            }
                            case SpecialTypes.Door.CLOSE_30_THEN_OPEN -> {
                                direction = 1;
                                world.startSectorSound(sector, SoundId.DOROPN);
                            }
                            default -> {
                            }
                        }
                    }
                }
                case 2 -> {
                    // INITIAL WAIT
                    if (--topCountdown == 0) {
                        if (type == SpecialTypes.Door.RAISE_IN_5_MINS) {
                            direction = 1;
                            type = SpecialTypes.Door.NORMAL;
                            world.startSectorSound(sector, SoundId.DOROPN);
                        }
                    }
                }
                case -1 -> {
                    // DOWN
                    int res = sectors.movePlane(sector, speed, sector.floorHeight, 0, 1, direction);
                    if (res == SpecialTypes.Result.PASTDEST) {
                        switch (type) {
                            case SpecialTypes.Door.BLAZE_RAISE, SpecialTypes.Door.BLAZE_CLOSE -> {
                                sector.specialData = null;
                                world.thinkers.remove(this); // unlink and free
                                world.startSectorSound(sector, SoundId.BDCLS);
                            }
                            case SpecialTypes.Door.NORMAL, SpecialTypes.Door.CLOSE -> {
                                sector.specialData = null;
                                world.thinkers.remove(this); // unlink and free
                            }
                            case SpecialTypes.Door.CLOSE_30_THEN_OPEN -> {
                                direction = 0;
                                topCountdown = World.TICRATE * 30;
                            }
                            default -> {
                            }
                        }
                    } else if (res == SpecialTypes.Result.CRUSHED) {
                        switch (type) {
                            case SpecialTypes.Door.BLAZE_CLOSE, SpecialTypes.Door.CLOSE -> {
                                // DO NOT GO BACK UP!
                            }
                            default -> {
                                direction = 1;
                                world.startSectorSound(sector, SoundId.DOROPN);
                            }
                        }
                    }
                }
                case 1 -> {
                    // UP
                    int res = sectors.movePlane(sector, speed, topHeight, 0, 1, direction);
                    if (res == SpecialTypes.Result.PASTDEST) {
                        switch (type) {
                            case SpecialTypes.Door.BLAZE_RAISE, SpecialTypes.Door.NORMAL -> {
                                direction = 0; // wait at top
                                topCountdown = topWait;
                            }
                            case SpecialTypes.Door.CLOSE_30_THEN_OPEN, SpecialTypes.Door.BLAZE_OPEN,
                                    SpecialTypes.Door.OPEN -> {
                                sector.specialData = null;
                                world.thinkers.remove(this); // unlink and free
                            }
                            default -> {
                            }
                        }
                    }
                }
                default -> {
                }
            }
        }

        @Override
        public int traceCode() {
            return 1;
        }

        @Override
        public void traceFields(TraceSink sink) {
            sink.put(type);
            sink.put(sector.index);
            sink.put(topHeight);
            sink.put(speed);
            sink.put(direction);
            sink.put(topWait);
            sink.put(topCountdown);
        }
    }

    /** {@code EV_DoLockedDoor}: move a locked door up/down (switches 99, 133..137). */
    @VanillaRef("p_doors.c:EV_DoLockedDoor")
    boolean doLockedDoor(Line line, int type, Actor thing) {
        Player p = thing.player;
        if (p == null) {
            return false;
        }
        switch (line.special) {
            case 99, 133 -> {
                // Blue Lock
                if (!p.cards[Card.BLUE_CARD] && !p.cards[Card.BLUE_SKULL]) {
                    denied(p, "PD_BLUEO");
                    return false;
                }
            }
            case 134, 135 -> {
                // Red Lock
                if (!p.cards[Card.RED_CARD] && !p.cards[Card.RED_SKULL]) {
                    denied(p, "PD_REDO");
                    return false;
                }
            }
            case 136, 137 -> {
                // Yellow Lock
                if (!p.cards[Card.YELLOW_CARD] && !p.cards[Card.YELLOW_SKULL]) {
                    denied(p, "PD_YELLOWO");
                    return false;
                }
            }
            default -> {
            }
        }
        return doDoor(line, type);
    }

    /** {@code player->message = DEH_String(key); S_StartSound(NULL, sfx_oof)}. */
    private void denied(Player p, String key) {
        String text = world.strings.get(key);
        p.message = text;
        world.events.playerMessage(p.index, text);
        world.startSound(null, SoundId.OOF);
    }

    /** {@code EV_DoDoor}: tagged door actions. */
    @VanillaRef("p_doors.c:EV_DoDoor")
    boolean doDoor(Line line, int type) {
        int secnum = -1;
        boolean rtn = false;

        while ((secnum = sectors.findSectorFromLineTag(line, secnum)) >= 0) {
            Sector sec = world.level.sectors[secnum];
            if (sec.specialData != null) {
                continue;
            }

            // new door thinker
            rtn = true;
            Door door = new Door();
            world.thinkers.add(door);
            sec.specialData = door;
            door.sector = sec;
            door.type = type;
            door.topWait = VDOORWAIT;
            door.speed = VDOORSPEED;

            switch (type) {
                case SpecialTypes.Door.BLAZE_CLOSE -> {
                    door.topHeight = Sectors.findLowestCeilingSurrounding(sec);
                    door.topHeight -= 4 * Fixed.FRACUNIT;
                    door.direction = -1;
                    door.speed = VDOORSPEED * 4;
                    world.startSectorSound(door.sector, SoundId.BDCLS);
                }
                case SpecialTypes.Door.CLOSE -> {
                    door.topHeight = Sectors.findLowestCeilingSurrounding(sec);
                    door.topHeight -= 4 * Fixed.FRACUNIT;
                    door.direction = -1;
                    world.startSectorSound(door.sector, SoundId.DORCLS);
                }
                case SpecialTypes.Door.CLOSE_30_THEN_OPEN -> {
                    door.topHeight = sec.ceilingHeight;
                    door.direction = -1;
                    world.startSectorSound(door.sector, SoundId.DORCLS);
                }
                case SpecialTypes.Door.BLAZE_RAISE, SpecialTypes.Door.BLAZE_OPEN -> {
                    door.direction = 1;
                    door.topHeight = Sectors.findLowestCeilingSurrounding(sec);
                    door.topHeight -= 4 * Fixed.FRACUNIT;
                    door.speed = VDOORSPEED * 4;
                    if (door.topHeight != sec.ceilingHeight) {
                        world.startSectorSound(door.sector, SoundId.BDOPN);
                    }
                }
                case SpecialTypes.Door.NORMAL, SpecialTypes.Door.OPEN -> {
                    door.direction = 1;
                    door.topHeight = Sectors.findLowestCeilingSurrounding(sec);
                    door.topHeight -= 4 * Fixed.FRACUNIT;
                    if (door.topHeight != sec.ceilingHeight) {
                        world.startSectorSound(door.sector, SoundId.DOROPN);
                    }
                }
                default -> {
                }
            }
        }
        return rtn;
    }

    /** {@code EV_VerticalDoor}: open a door manually, no tag value. */
    @VanillaRef("p_doors.c:EV_VerticalDoor")
    void verticalDoor(Line line, Actor thing) {
        int side = 0; // only front sides can be used

        // Check for locks
        Player player = thing.player;

        switch (line.special) {
            case 26, 32 -> {
                // Blue Lock
                if (player == null) {
                    return;
                }
                if (!player.cards[Card.BLUE_CARD] && !player.cards[Card.BLUE_SKULL]) {
                    denied(player, "PD_BLUEK");
                    return;
                }
            }
            case 27, 34 -> {
                // Yellow Lock
                if (player == null) {
                    return;
                }
                if (!player.cards[Card.YELLOW_CARD] && !player.cards[Card.YELLOW_SKULL]) {
                    denied(player, "PD_YELLOWK");
                    return;
                }
            }
            case 28, 33 -> {
                // Red Lock
                if (player == null) {
                    return;
                }
                if (!player.cards[Card.RED_CARD] && !player.cards[Card.RED_SKULL]) {
                    denied(player, "PD_REDK");
                    return;
                }
            }
            default -> {
            }
        }

        // if the sector has an active thinker, use it
        if (line.sideNum[side ^ 1] == -1) {
            throw new DoomFatalException("EV_VerticalDoor: DR special type on 1-sided linedef");
        }
        Sector sec = world.level.sides[line.sideNum[side ^ 1]].sector;
        if (sec.specialData != null) {
            switch (line.special) {
                case 1, 26, 27, 28, 117 -> {
                    // ONLY FOR "RAISE" DOORS, NOT "OPEN"s
                    Thinker data = sec.specialData;
                    if (data instanceof Door door && door.direction == -1) {
                        door.direction = 1; // go back up
                    } else {
                        if (thing.player == null) {
                            return; // JDC: bad guys never close doors
                        }
                        closeThinker(data);
                    }
                    return;
                }
                default -> {
                }
            }
        }

        // for proper sound
        switch (line.special) {
            case 117, 118 -> world.startSectorSound(sec, SoundId.BDOPN); // BLAZING DOOR RAISE / OPEN
            case 1, 31 -> world.startSectorSound(sec, SoundId.DOROPN); // NORMAL DOOR SOUND
            default -> world.startSectorSound(sec, SoundId.DOROPN); // LOCKED DOOR SOUND
        }

        // new door thinker
        Door door = new Door();
        world.thinkers.add(door);
        sec.specialData = door;
        door.sector = sec;
        door.direction = 1;
        door.speed = VDOORSPEED;
        door.topWait = VDOORWAIT;
        switch (line.special) {
            case 1, 26, 27, 28 -> door.type = SpecialTypes.Door.NORMAL;
            case 31, 32, 33, 34 -> {
                door.type = SpecialTypes.Door.OPEN;
                line.special = 0;
            }
            case 117 -> {
                // blazing door raise
                door.type = SpecialTypes.Door.BLAZE_RAISE;
                door.speed = VDOORSPEED * 4;
            }
            case 118 -> {
                // blazing door open
                door.type = SpecialTypes.Door.BLAZE_OPEN;
                line.special = 0;
                door.speed = VDOORSPEED * 4;
            }
            default -> {
            }
        }

        // find the top and bottom of the movement range
        door.topHeight = Sectors.findLowestCeilingSurrounding(sec);
        door.topHeight -= 4 * Fixed.FRACUNIT;
    }

    /**
     * The "close" branch of {@code EV_VerticalDoor}: a door goes down immediately, a platform gets {@code wait = -1}
     * (its {@code vldoor_t.direction} alias), anything else is hit through the field offset the 64-bit Chocolate Doom
     * build writes.
     */
    private static void closeThinker(Thinker data) {
        // When is a door not a door? In Vanilla, door->direction is set, even though "specialdata" might not
        // actually point at a door.
        if (data instanceof Door door) {
            door.direction = -1; // start going down immediately
        } else if (data instanceof Plats.PlatRaise plat) {
            // Erm, this is a plat, not a door: the direction field of vldoor_t corresponds to the wait field.
            plat.wait = -1;
        } else if (data instanceof Ceilings.CeilingMove ceiling) {
            // VANILLA-BUG: "Tried to close something that wasn't a door": the write lands on ceiling_t.speed
            ceiling.speed = -1;
        } else if (data instanceof Floors.FloorMove floor) {
            // VANILLA-BUG: the write lands on floormove_t.texture (64-bit layout of the oracle)
            floor.texture = -1;
        }
    }

    /** {@code P_SpawnDoorCloseIn30}: a door that closes after 30 seconds. */
    @VanillaRef("p_doors.c:P_SpawnDoorCloseIn30")
    void spawnDoorCloseIn30(Sector sec) {
        Door door = new Door();
        world.thinkers.add(door);
        sec.specialData = door;
        sec.special = 0;
        door.sector = sec;
        door.direction = 0;
        door.type = SpecialTypes.Door.NORMAL;
        door.speed = VDOORSPEED;
        door.topCountdown = 30 * World.TICRATE;
    }

    /** {@code P_SpawnDoorRaiseIn5Mins}: a door that opens after 5 minutes. */
    @VanillaRef("p_doors.c:P_SpawnDoorRaiseIn5Mins")
    void spawnDoorRaiseIn5Mins(Sector sec) {
        Door door = new Door();
        world.thinkers.add(door);
        sec.specialData = door;
        sec.special = 0;
        door.sector = sec;
        door.direction = 2;
        door.type = SpecialTypes.Door.RAISE_IN_5_MINS;
        door.speed = VDOORSPEED;
        door.topHeight = Sectors.findLowestCeilingSurrounding(sec);
        door.topHeight -= 4 * Fixed.FRACUNIT;
        door.topWait = VDOORWAIT;
        door.topCountdown = 5 * 60 * World.TICRATE;
    }
}
