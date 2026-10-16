package doom.game.api;

import static org.assertj.core.api.Assertions.assertThat;

import doom.game.view.SoundId;
import org.junit.jupiter.api.Test;

class TicCmdSmokeTest {

    @Test
    void specialButtonKeepsItsBitsInASignedByte() {
        TicCmd cmd = new TicCmd();
        cmd.buttons = (byte) (TicCmd.BT_SPECIAL | TicCmd.BTS_SAVEGAME | (3 << TicCmd.BTS_SAVESHIFT));
        assertThat(cmd.buttons & TicCmd.BT_SPECIAL).isEqualTo(TicCmd.BT_SPECIAL);
        assertThat((cmd.buttons & TicCmd.BTS_SAVEMASK) >> TicCmd.BTS_SAVESHIFT).isEqualTo(3);
        assertThat(SoundId.COUNT).isEqualTo(109);
    }
}
