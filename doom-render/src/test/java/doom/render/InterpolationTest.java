package doom.render;

import static org.assertj.core.api.Assertions.assertThat;

import doom.game.view.ActorView;
import doom.game.view.PlayerView;
import doom.game.view.WorldView;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InterpolationTest {

    private static final int U = 65536;

    /** A view whose methods return the value registered under their name (0 when none). */
    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, Map<String, Integer> values) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            Integer v = values.get(method.getName());
            return v == null ? 0 : v;
        });
    }

    @Test
    void fixedAlphaClamps() {
        assertThat(Interpolation.fixedAlpha(-1)).isZero();
        assertThat(Interpolation.fixedAlpha(Double.NaN)).isZero();
        assertThat(Interpolation.fixedAlpha(0.5)).isEqualTo(U / 2);
        assertThat(Interpolation.fixedAlpha(1.0)).isEqualTo(Interpolation.ONE);
        assertThat(Interpolation.fixedAlpha(7)).isEqualTo(Interpolation.ONE);
    }

    @Test
    void actorsMoveBetweenPreviousAndCurrent() {
        Map<String, Integer> v = new HashMap<>();
        v.put("prevX", 100 * U);
        v.put("x", 108 * U);
        v.put("prevY", -50 * U);
        v.put("y", -60 * U);
        v.put("prevZ", 0);
        v.put("z", 16 * U);
        Interpolation.Actors a = new Interpolation.Actors().set(stub(ActorView.class, v), U / 4);
        assertThat(a.x(3)).isEqualTo(102 * U);
        assertThat(a.y(3)).isEqualTo(-52 * U - U / 2);
        assertThat(a.z(3)).isEqualTo(4 * U);
        a.set(stub(ActorView.class, v), 0);
        assertThat(a.x(3)).isEqualTo(100 * U);
        a.set(stub(ActorView.class, v), Interpolation.ONE);
        assertThat(a.x(3)).isEqualTo(108 * U);
    }

    @Test
    void anglesTakeTheShortestPath() {
        Map<String, Integer> v = new HashMap<>();
        v.put("prevAngle", 0xFFF00000); // just below 360 degrees
        v.put("angle", 0x00100000); // just above 0
        Interpolation.Actors a = new Interpolation.Actors().set(stub(ActorView.class, v), U / 2);
        assertThat(a.angle(0)).isEqualTo(0);
        v.put("prevAngle", 0x7FF00000);
        v.put("angle", 0x80100000);
        a.set(stub(ActorView.class, v), U / 2);
        assertThat(a.angle(0)).isEqualTo(0x80000000);
    }

    @Test
    void sectorsAndScrollingWalls() {
        Map<String, Integer> v = new HashMap<>();
        v.put("sectorPrevFloorHeight", 0);
        v.put("sectorFloorHeight", 8 * U);
        v.put("sectorPrevCeilingHeight", 128 * U);
        v.put("sectorCeilingHeight", 120 * U);
        v.put("sidePrevTextureOffset", 10 * U);
        v.put("sideTextureOffset", 11 * U);
        v.put("sectorLightLevel", 160);
        Interpolation.World w = new Interpolation.World().set(stub(WorldView.class, v), U / 2);
        assertThat(w.sectorFloorHeight(0)).isEqualTo(4 * U);
        assertThat(w.sectorCeilingHeight(0)).isEqualTo(124 * U);
        assertThat(w.sideTextureOffset(0)).isEqualTo(10 * U + U / 2);
        assertThat(w.sectorLightLevel(0)).as("delegated").isEqualTo(160);
    }

    @Test
    void jumpsAreNotInterpolated() {
        // a level start or a loaded game leaves the previous heights at 0: no 200-unit slide through the floor
        Map<String, Integer> v = new HashMap<>();
        v.put("sectorPrevFloorHeight", 0);
        v.put("sectorFloorHeight", 200 * U);
        v.put("sidePrevTextureOffset", 0);
        v.put("sideTextureOffset", 500 * U);
        Interpolation.World w = new Interpolation.World().set(stub(WorldView.class, v), U / 2);
        assertThat(w.sectorFloorHeight(0)).isEqualTo(200 * U);
        assertThat(w.sideTextureOffset(0)).isEqualTo(500 * U);
    }

    @Test
    void weaponOffsetsFollowTheTic() {
        Map<String, Integer> v = new HashMap<>();
        v.put("pspritePrevSy", 32 * U);
        v.put("pspriteSy", 38 * U);
        v.put("pspritePrevSx", 0);
        v.put("pspriteSx", 0);
        v.put("health", 77);
        Interpolation.Player p = new Interpolation.Player().set(stub(PlayerView.class, v), U / 2);
        assertThat(p.pspriteSy(0)).isEqualTo(35 * U);
        assertThat(p.pspriteSx(1)).isZero();
        assertThat(p.health()).isEqualTo(77);
    }
}
