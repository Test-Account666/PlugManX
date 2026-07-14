package core.com.rylinaux.plugman.util;

/*
 * #%L
 * PlugMan
 * %%
 * Copyright (C) 2010 - 2015 PlugMan
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utilities for dealing with flags passed to commands.
 *
 * @author rylinaux
 */
@UtilityClass
public class FlagUtil {

    /**
     * Check if a flag exists in the command arguments.
     *
     * @param args the array of arguments.
     * @param flag the flag to check for.
     * @return true if the flag exists.
     */
    public static boolean hasFlag(String[] args, char flag) {
        return parse(args, flag).hasFlag(flag);
    }

    /**
     * Parse supported flags without leaving null entries in the command arguments.
     *
     * @param args           the command arguments
     * @param supportedFlags the flags to extract
     * @return the parsed flags and cleaned arguments
     */
    public static ParsedArguments parse(String[] args, char... supportedFlags) {
        var supported = new HashSet<Character>();
        for (var flag : supportedFlags) supported.add(Character.toLowerCase(flag));

        var arguments = new ArrayList<String>();
        var flags = new HashSet<Character>();

        for (var argument : args) {
            if (argument == null) continue;

            if (argument.length() == 2 && argument.charAt(0) == '-') {
                var flag = Character.toLowerCase(argument.charAt(1));
                if (supported.contains(flag)) {
                    flags.add(flag);
                    continue;
                }
            }

            arguments.add(argument);
        }

        return new ParsedArguments(arguments, flags);
    }

    public record ParsedArguments(List<String> arguments, Set<Character> flags) {
        public ParsedArguments {
            arguments = List.copyOf(arguments);
            flags = Set.copyOf(flags);
        }

        public String[] argumentArray() {
            return arguments.toArray(String[]::new);
        }

        public boolean hasFlag(char flag) {
            return flags.contains(Character.toLowerCase(flag));
        }
    }
}
