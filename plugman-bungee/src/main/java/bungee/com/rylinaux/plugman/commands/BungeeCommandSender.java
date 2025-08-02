package bungee.com.rylinaux.plugman.commands;

/*
 * #%L
 * PlugManBungee
 * %%
 * Copyright (C) 2010 - 2024 PlugMan
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

import bungee.com.rylinaux.plugman.PlugManBungee;
import core.com.rylinaux.plugman.commands.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public record BungeeCommandSender(net.md_5.bungee.api.CommandSender bungeeSender) implements CommandSender {

    @Override
    public void sendMessage(String message) {
        sendMessage(true, message, new Object[0]);
    }

    @Override
    public void sendMessage(boolean prefix, String message) {
        sendMessage(prefix, message, new Object[0]);
    }

    @Override
    public void sendMessage(String message, Object... args) {
        sendMessage(true, message, args);
    }

    @Override
    public void sendMessage(boolean prefix, String message, Object... args) {
        message = PlugManBungee.getInstance().getMessageFormatter().formatMessage(prefix, message, args);
        bungeeSender.sendMessage(String.format(message, args));
    }

    @Override
    public boolean hasPermission(String permission) {
        return bungeeSender.hasPermission(permission);
    }

    @Override
    public boolean isConsole() {
        return !(bungeeSender instanceof ProxiedPlayer);
    }

    @SuppressWarnings("unchecked")
    @Override
    public net.md_5.bungee.api.CommandSender getHandle() {
        return bungeeSender();
    }
}