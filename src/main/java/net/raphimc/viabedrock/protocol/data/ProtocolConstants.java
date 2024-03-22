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
package net.raphimc.viabedrock.protocol.data;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.libs.mcstructs.text.serializer.TextComponentCodec;

public class ProtocolConstants {

    public static final ProtocolVersion JAVA_VERSION = ProtocolVersion.v1_20_3;
    public static final int JAVA_PACK_VERSION = 22;
    public static final TextComponentCodec JAVA_TEXT_COMPONENT_SERIALIZER = TextComponentCodec.V1_20_3;
    public static final int JAVA_PAINTING_VARIANT_ID = 8;

    public static final String BEDROCK_VERSION_NAME = "1.20.70";
    public static final int BEDROCK_PROTOCOL_VERSION = 662;
    public static final int BEDROCK_RAKNET_PROTOCOL_VERSION = 11;
    public static final int BEDROCK_DEFAULT_PORT = 19132;
    public static final int BEDROCK_COMMAND_VERSION = 38;
    public static final short BEDROCK_REQUEST_CHUNK_RADIUS_MAX_RADIUS = 28;

}
