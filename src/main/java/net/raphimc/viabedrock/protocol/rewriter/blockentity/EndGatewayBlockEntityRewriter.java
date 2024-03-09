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
package net.raphimc.viabedrock.protocol.rewriter.blockentity;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.blockentity.BlockEntity;
import com.viaversion.viaversion.api.minecraft.blockentity.BlockEntityImpl;
import com.viaversion.viaversion.libs.opennbt.tag.builtin.CompoundTag;
import com.viaversion.viaversion.libs.opennbt.tag.builtin.IntTag;
import com.viaversion.viaversion.libs.opennbt.tag.builtin.ListTag;
import com.viaversion.viaversion.libs.opennbt.tag.builtin.LongTag;
import net.raphimc.viabedrock.api.chunk.BedrockBlockEntity;
import net.raphimc.viabedrock.protocol.rewriter.BlockEntityRewriter;

public class EndGatewayBlockEntityRewriter implements BlockEntityRewriter.Rewriter {

    @Override
    public BlockEntity toJava(UserConnection user, BedrockBlockEntity bedrockBlockEntity) {
        final CompoundTag bedrockTag = bedrockBlockEntity.tag();
        final CompoundTag javaTag = new CompoundTag();

        if (bedrockTag.get("Age") instanceof IntTag) {
            javaTag.put("Age", new LongTag(((IntTag) bedrockTag.get("Age")).asInt()));
        }
        final ListTag<IntTag> bedrockExitPortal = bedrockTag.getListTag("ExitPortal", IntTag.class);
        if (bedrockExitPortal != null && bedrockExitPortal.size() == 3) {
            final CompoundTag javaExitPortal = new CompoundTag();
            javaExitPortal.put("X", bedrockExitPortal.get(0));
            javaExitPortal.put("Y", bedrockExitPortal.get(1));
            javaExitPortal.put("Z", bedrockExitPortal.get(2));
            javaTag.put("ExitPortal", javaExitPortal);
        }

        return new BlockEntityImpl(bedrockBlockEntity.packedXZ(), bedrockBlockEntity.y(), -1, javaTag);
    }

}
