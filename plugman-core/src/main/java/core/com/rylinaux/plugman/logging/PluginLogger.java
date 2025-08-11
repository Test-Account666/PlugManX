package core.com.rylinaux.plugman.logging;

/*
 * #%L
 * PlugMan
 * %%
 * Copyright (C) 2010 - 2014 PlugMan
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

/**
 * Platform-agnostic interface for logging operations.
 * This abstracts the underlying logging system (Bukkit, Bungee, etc.)
 *
 * @author rylinaux
 */
public interface PluginLogger {

    /**
     * Log an info message
     *
     * @param message the message to log
     */
    void info(String message);

    /**
     * Log an info message
     *
     * @param message   the message to log
     * @param throwable error that was thrown
     */
    void info(String message, Throwable throwable);

    /**
     * Log a warning message
     *
     * @param message the message to log
     */
    void warning(String message);

    /**
     * Log a warning message
     *
     * @param message   the message to log
     * @param throwable error that was thrown
     */
    void warning(String message, Throwable throwable);

    /**
     * Log a severe/error message
     *
     * @param message the message to log
     */
    void severe(String message);

    /**
     * Log a severe/error message
     *
     * @param message   the message to log
     * @param throwable error that was thrown
     */
    void severe(String message, Throwable throwable);
}