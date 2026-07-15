package com.rylinaux.plugman.util;

import core.com.rylinaux.plugman.util.FlagUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlagUtilTest {

    @Test
    void parsesFlagsWithoutMutatingOrPaddingArguments() {
        var original = new String[]{"check", "Example Plugin", "-F"};

        var parsed = FlagUtil.parse(original, 'f');

        assertTrue(parsed.hasFlag('f'));
        assertArrayEquals(new String[]{"check", "Example Plugin"}, parsed.argumentArray());
        assertArrayEquals(new String[]{"check", "Example Plugin", "-F"}, original);
    }

    @Test
    void keepsUnsupportedFlagsAsArguments() {
        var parsed = FlagUtil.parse(new String[]{"list", "-x"}, 'v');

        assertFalse(parsed.hasFlag('v'));
        assertArrayEquals(new String[]{"list", "-x"}, parsed.argumentArray());
    }
}
