/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023-2024 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.raphimc.viabedrock.api.protocol;

import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.base.ServerboundHandshakePackets;
import com.viaversion.viaversion.protocols.base.v1_7.ServerboundBaseProtocol1_7;
import net.raphimc.viabedrock.protocol.storage.HandshakeStorage;

public class BedrockBaseProtocol extends ServerboundBaseProtocol1_7 {

    public static final BedrockBaseProtocol INSTANCE = new BedrockBaseProtocol();

    private BedrockBaseProtocol() {
        this.initialize();
    }

    @Override
    protected void registerPackets() {
        super.registerPackets();
        this.registerServerbound(State.HANDSHAKE, ServerboundHandshakePackets.CLIENT_INTENTION.getId(), -1, wrapper -> {
            wrapper.cancel();
            final int protocolVersion = wrapper.read(Types.VAR_INT); // protocol id
            final String hostname = wrapper.read(Types.STRING); // hostname
            final int port = wrapper.read(Types.UNSIGNED_SHORT); // port

            wrapper.user().put(new HandshakeStorage(protocolVersion, hostname, port));
        });
    }

}
