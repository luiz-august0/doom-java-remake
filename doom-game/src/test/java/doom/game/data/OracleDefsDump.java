package doom.game.data;

import doom.game.view.AmmoType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Renders the tables of a session in the line format of the oracle's {@code -dumpdefs} (tools/oracle/FORMAT.md,
 * "doom-defs v1"), so a dump of the patched Chocolate Doom can be compared line by line with ours.
 */
public final class OracleDefsDump {

    private static final String[] AMMO_NAMES = {"clip", "shell", "cell", "misl"};

    private OracleDefsDump() {
    }

    /** The body of a dump: everything except the header and {@code nodeh} lines and the {@code string} lines. */
    public static List<String> tables(GameDefs defs, GameStrings strings) {
        List<String> out = new ArrayList<>();
        out.add("sprites " + defs.spriteNames().length);
        for (int i = 0; i < defs.spriteNames().length; i++) {
            out.add("sprite " + i + " " + defs.spriteNames()[i]);
        }
        out.add("states " + defs.states().length);
        for (int i = 0; i < defs.states().length; i++) {
            State s = defs.states()[i];
            out.add("state " + i + " sprite=" + s.sprite + " frame=" + s.frame + " tics=" + s.tics + " action="
                    + (s.action == StateAction.NONE ? "NULL" : s.action.name()) + " next=" + s.nextState + " misc1="
                    + s.misc1 + " misc2=" + s.misc2);
        }
        out.add("mobjinfo " + defs.things().length);
        for (int i = 0; i < defs.things().length; i++) {
            ThingType m = defs.things()[i];
            out.add("mobj " + i + " doomednum=" + m.doomedNum + " spawnstate=" + m.spawnState + " spawnhealth="
                    + m.spawnHealth + " seestate=" + m.seeState + " seesound=" + m.seeSound + " reactiontime="
                    + m.reactionTime + " attacksound=" + m.attackSound + " painstate=" + m.painState + " painchance="
                    + m.painChance + " painsound=" + m.painSound + " meleestate=" + m.meleeState + " missilestate="
                    + m.missileState + " deathstate=" + m.deathState + " xdeathstate=" + m.xDeathState
                    + " deathsound=" + m.deathSound + " speed=" + m.speed + " radius=" + m.radius + " height="
                    + m.height + " mass=" + m.mass + " damage=" + m.damage + " activesound=" + m.activeSound
                    + " flags=" + m.flags + " raisestate=" + m.raiseState);
        }
        out.add("weaponinfo " + defs.weapons().length);
        for (int i = 0; i < defs.weapons().length; i++) {
            WeaponInfo w = defs.weapons()[i];
            out.add("weapon " + i + " ammo=" + w.ammo + " upstate=" + w.upState + " downstate=" + w.downState
                    + " readystate=" + w.readyState + " atkstate=" + w.atkState + " flashstate=" + w.flashState);
        }
        out.add("ammo " + AmmoType.COUNT);
        for (int i = 0; i < AmmoType.COUNT; i++) {
            out.add("ammo " + i + " name=" + AMMO_NAMES[i] + " max=" + defs.maxAmmo()[i] + " clip="
                    + defs.clipAmmo()[i]);
        }
        out.add("sounds " + defs.sounds().length);
        for (int i = 0; i < defs.sounds().length; i++) {
            SoundInfo s = defs.sounds()[i];
            out.add("sfx " + i + " name=" + s.name() + " priority=" + s.priority + " link=" + s.link + " pitch="
                    + s.pitch + " volume=" + s.volume);
        }
        out.add("music " + defs.musicNames().length);
        for (int i = 0; i < defs.musicNames().length; i++) {
            out.add("mus " + i + " name=" + (defs.musicNames()[i] == null ? "NULL" : defs.musicNames()[i]));
        }
        MiscSettings misc = defs.misc();
        out.add("misc");
        out.add("initial_health " + misc.initialHealth());
        out.add("initial_bullets " + misc.initialBullets());
        out.add("max_health " + misc.maxHealth());
        out.add("max_armor " + misc.maxArmor());
        out.add("green_armor_class " + misc.greenArmorClass());
        out.add("blue_armor_class " + misc.blueArmorClass());
        out.add("max_soulsphere " + misc.maxSoulsphere());
        out.add("soulsphere_health " + misc.soulsphereHealth());
        out.add("megasphere_health " + misc.megasphereHealth());
        out.add("god_mode_health " + misc.godModeHealth());
        out.add("idfa_armor " + misc.idfaArmor());
        out.add("idfa_armor_class " + misc.idfaArmorClass());
        out.add("idkfa_armor " + misc.idkfaArmor());
        out.add("idkfa_armor_class " + misc.idkfaArmorClass());
        out.add("bfg_cells_per_shot " + misc.bfgCellsPerShot());
        out.add("species_infighting " + (misc.speciesInfighting() ? 1 : 0));
        out.add("cheats " + CheatId.values().length);
        for (CheatId id : CheatId.values()) {
            out.add(cheat(strings.cheat(id)));
        }
        return out;
    }

    /** The {@code string "from" -> "to"} lines, sorted. */
    public static List<String> substitutions(GameStrings strings) {
        Map<String, String> sorted = new TreeMap<>(strings.substitutions());
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            out.add("string " + quote(e.getKey()) + " -> " + quote(e.getValue()));
        }
        return out;
    }

    private static String cheat(CheatSequence c) {
        StringBuilder hex = new StringBuilder();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < c.originalLength(); i++) {
            char ch = c.at(i);
            hex.append(String.format("%02x", (int) ch));
            if (ch == '\\' || ch == '"') {
                text.append('\\').append(ch);
            } else if (ch < 0x20 || ch >= 0x7f) {
                text.append(String.format("\\x%02x", (int) ch));
            } else {
                text.append(ch);
            }
        }
        return "cheat \"" + c.id().dehName() + "\" len=" + c.originalLength() + " params=" + c.parameterChars()
                + " seq=" + hex + " text=\"" + text + "\"";
    }

    /** Chocolate's {@code PrintQuoted}. */
    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            if (ch == '\\') {
                sb.append("\\\\");
            } else if (ch == '"') {
                sb.append("\\\"");
            } else if (ch == '\n') {
                sb.append("\\n");
            } else if (ch < 0x20 || ch >= 0x7f) {
                sb.append(String.format("\\x%02x", (int) ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.append('"').toString();
    }
}
