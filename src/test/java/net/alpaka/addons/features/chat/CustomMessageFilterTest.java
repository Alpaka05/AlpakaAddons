package net.alpaka.addons.features.chat;

import net.alpaka.addons.features.chat.CustomMessageFilter.Mode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomMessageFilterTest {
    private static final String LINE = "Your Ultimate Wise has healed you for 120 health!";

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "EQUALS      | Your Ultimate Wise has healed you for 120 health! | true",
            "EQUALS      | Your Ultimate Wise has healed you                 | false",
            "STARTS_WITH | Your Ultimate Wise                                | true",
            "STARTS_WITH | Ultimate Wise                                     | false",
            "CONTAINS    | healed you for                                    | true",
            "ENDS_WITH   | health!                                           | true",
            "ENDS_WITH   | health                                            | false",
            "REGEX       | healed you for \\d+ health                         | true",
            "REGEX       | ^healed                                           | false",
    })
    void comparesTheWayItsModeSays(Mode mode, String text, boolean expected) {
        assertEquals(expected, new CustomMessageFilter(text.strip(), mode).matches(LINE));
    }

    @Test
    void ignoreCaseIsOptIn() {
        CustomMessageFilter filter = new CustomMessageFilter("your ultimate wise", Mode.STARTS_WITH);
        assertFalse(filter.matches(LINE));
        filter.ignoreCase = true;
        assertTrue(filter.matches(LINE));
    }

    @Test
    void colorCodesInTheCopiedTextDontMatter() {
        assertTrue(new CustomMessageFilter("§aYour §6Ultimate Wise §ahas healed you for §c120 §ahealth!", Mode.EQUALS).matches(LINE));
    }

    @Test
    void switchedOffOrBlankFiltersHideNothing() {
        CustomMessageFilter off = new CustomMessageFilter("Your", Mode.STARTS_WITH);
        off.enabled = false;
        assertFalse(off.matches(LINE));
        assertFalse(new CustomMessageFilter("   ", Mode.CONTAINS).matches(LINE));
    }

    @Test
    void aBrokenRegexIsMarkedAndHidesNothing() {
        CustomMessageFilter broken = new CustomMessageFilter("healed (", Mode.REGEX);
        assertFalse(broken.isValid());
        assertFalse(broken.matches(LINE));
        broken.text = "healed \\(?";
        assertTrue(broken.isValid());
        assertTrue(broken.matches(LINE));
    }

    @Test
    void anyMatchingFilterHidesTheMessage() {
        List<CustomMessageFilter> filters = List.of(
                new CustomMessageFilter("nothing like it", Mode.CONTAINS),
                new CustomMessageFilter("120 health", Mode.CONTAINS));
        assertTrue(MessageFilterFeature.matchesCustom(LINE, filters));
        assertFalse(MessageFilterFeature.matchesCustom("Party > Someone: hi", filters));
    }
}
