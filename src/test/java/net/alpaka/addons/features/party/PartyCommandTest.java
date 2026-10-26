package net.alpaka.addons.features.party;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PartyCommandTest {

    @BeforeEach
    void fixedMeasurements() {
        PartyCommand.Measurements.tpsSource = () -> "19.8";
        PartyCommand.Measurements.pingSource = () -> "42ms";
        PartyCommand.Measurements.coordsSource = () -> "x: 1, y: 2, z: 3";
        PartyCommand.Measurements.randomSource = bound -> bound;
    }

    @AfterEach
    void liveMeasurements() {
        PartyCommand.Measurements.tpsSource = PartyCommandsFeature::currentTps;
        PartyCommand.Measurements.pingSource = PartyCommandsFeature::currentPing;
        PartyCommand.Measurements.coordsSource = PartyCommandsFeature::currentCoords;
        PartyCommand.Measurements.randomSource =
                bound -> java.util.concurrent.ThreadLocalRandom.current().nextInt(bound) + 1;
    }

    /** Color-stripped party chat lines and the command each answers with. */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "Party > [MVP+] Friend: !warp            | party warp",
            "Party > [VIP] Friend: !w                | party warp",
            "Party > Friend: !pt                     | party transfer Friend",
            "Party > [MVP++] Friend: !transfer Other | party transfer Other",
            "Party > [MVP+] Friend ቾ: !allinv        | party settings allinvite",
            "Party > [MVP+] Friend: !inv Newbie      | party invite Newbie",
            "Party > [MVP+] Friend: !kick Troll      | party kick Troll",
            "Party > [MVP+] Friend: !f7              | joininstance catacombs_floor_seven",
            "Party > [MVP+] Friend: !M3              | joininstance master_catacombs_floor_three",
            "Party > [MVP+] Friend: !t5              | joininstance kuudra_infernal",
            "Party > [MVP+] Friend: !tps             | pc TPS: 19.8",
            "Party > [MVP+] Friend: !ping            | pc Ping: 42ms",
            "Party > [MVP+] Friend: !coords          | pc x: 1, y: 2, z: 3",
            "Party > [MVP+] Friend: !dice            | pc Friend rolled 6 (1-6)",
            "Party > [MVP+] Friend: !roll 20         | pc Friend rolled 20 (1-20)",
            "Party > [MVP+] Friend: !cf              | pc Tails",
    })
    void answersPartyChatCommands(String line, String expected) {
        PartyChatCommand request = PartyChatCommand.parse(line.strip());
        PartyCommand command = PartyCommand.byAlias(request.name());
        assertEquals(expected, command.answer(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[MVP+] Someone: Party > [MVP+] Friend: !warp",
            "Guild > [MVP+] Friend: !warp",
            "Party > [MVP+] Friend: warp please",
            "Friend: !warp",
            "From [MVP+] Friend: !warp",
    })
    void ignoresAnythingButAPartyMembersCommand(String line) {
        assertNull(PartyChatCommand.parse(line));
    }

    @Test
    void unknownCommandsAndMissingNamesAnswerNothing() {
        assertNull(PartyCommand.byAlias("f8"));
        assertNull(PartyCommand.byAlias("t6"));
        assertNull(PartyCommand.byAlias("nope"));
        PartyChatCommand invite = PartyChatCommand.parse("Party > Friend: !inv");
        assertNull(PartyCommand.INVITE.answer(invite));
    }
}
