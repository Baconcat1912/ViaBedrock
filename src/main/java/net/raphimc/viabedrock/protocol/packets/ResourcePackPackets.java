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
package net.raphimc.viabedrock.protocol.packets;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.remapper.PacketHandler;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.protocols.protocol1_20_2to1_20.packet.ServerboundConfigurationPackets1_20_2;
import com.viaversion.viaversion.protocols.protocol1_20_3to1_20_2.packet.ClientboundConfigurationPackets1_20_3;
import com.viaversion.viaversion.util.Pair;
import com.viaversion.viaversion.util.Triple;
import net.raphimc.viabedrock.ViaBedrock;
import net.raphimc.viabedrock.api.model.ResourcePack;
import net.raphimc.viabedrock.api.util.TextUtil;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ClientboundBedrockPackets;
import net.raphimc.viabedrock.protocol.ServerboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ResourcePackStatus;
import net.raphimc.viabedrock.protocol.data.enums.java.JavaResourcePackStatus;
import net.raphimc.viabedrock.protocol.model.Experiment;
import net.raphimc.viabedrock.protocol.providers.ResourcePackProvider;
import net.raphimc.viabedrock.protocol.storage.ResourcePacksStorage;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class ResourcePackPackets {

    public static void register(final BedrockProtocol protocol) {
        protocol.registerClientboundTransition(ClientboundBedrockPackets.RESOURCE_PACKS_INFO, ClientboundConfigurationPackets1_20_3.RESOURCE_PACK_PUSH, (PacketHandler) wrapper -> {
            if (wrapper.user().has(ResourcePacksStorage.class)) {
                ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Received RESOURCE_PACKS_INFO twice");
                wrapper.cancel();
                return;
            }

            final ResourcePacksStorage resourcePacksStorage = new ResourcePacksStorage();
            wrapper.user().put(resourcePacksStorage);

            wrapper.read(Type.BOOLEAN); // must accept
            wrapper.read(Type.BOOLEAN); // scripting
            wrapper.read(Type.BOOLEAN); // server packs force enabled

            final ResourcePack[] behaviorPacks = wrapper.read(BedrockTypes.BEHAVIOUR_PACK_ARRAY);
            for (ResourcePack behaviorPack : behaviorPacks) {
                resourcePacksStorage.addPack(behaviorPack);
            }
            final ResourcePack[] resourcePacks = wrapper.read(BedrockTypes.RESOURCE_PACK_ARRAY);
            for (ResourcePack resourcePack : resourcePacks) {
                resourcePacksStorage.addPack(resourcePack);
            }
            final int cdnEntriesCount = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT); // cdn entries count
            for (int i = 0; i < cdnEntriesCount; i++) {
                wrapper.read(BedrockTypes.STRING); // pack id
                wrapper.read(BedrockTypes.STRING); // remote url
            }

            if (wrapper.user().getProtocolInfo().getProtocolVersion() >= ProtocolConstants.JAVA_VERSION.getVersion()
                  && ViaBedrock.config.getEnableResourcePack()) {
                final UUID httpToken = UUID.randomUUID();
                ViaBedrock.getResourcePackServer().addConnection(httpToken, wrapper.user());

                wrapper.write(Type.UUID, UUID.randomUUID()); // pack id
                wrapper.write(Type.STRING, ViaBedrock.getResourcePackServer().getUrl() + "?token=" + httpToken); // url
                wrapper.write(Type.STRING, ""); // hash
                wrapper.write(Type.BOOLEAN, false); // requires accept
                wrapper.write(Type.OPTIONAL_TAG, TextUtil.stringToNbt(
                        "\n§aIf you press 'Yes', the resource packs will be downloaded and converted to the Java Edition format. " +
                                "This may take a while, depending on your internet connection and the size of the packs. " +
                                "If you press 'No', you can join without loading the resource packs but you will have a worse gameplay experience.")
                ); // prompt message
            } else {
                wrapper.cancel();
                final PacketWrapper resourcePack = PacketWrapper.create(ServerboundConfigurationPackets1_20_2.RESOURCE_PACK, wrapper.user());
                resourcePack.write(Type.UUID, UUID.randomUUID()); // pack id
                resourcePack.write(Type.VAR_INT, 1); // status | 1 = DECLINED
                resourcePack.sendToServer(BedrockProtocol.class, false);
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.RESOURCE_PACK_DATA_INFO, null, wrapper -> {
            wrapper.cancel();
            final ResourcePacksStorage resourcePacksStorage = wrapper.user().get(ResourcePacksStorage.class);
            if (resourcePacksStorage.hasCompletedTransfer()) {
                ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Received RESOURCE_PACK_DATA_INFO after transfer completion");
                return;
            }

            final Pair<UUID, String> idAndVersion = wrapper.read(BedrockTypes.PACK_ID_AND_VERSION); // pack id and version
            final int maxChunkSize = wrapper.read(BedrockTypes.UNSIGNED_INT_LE).intValue(); // max chunk size
            wrapper.read(BedrockTypes.UNSIGNED_INT_LE); // chunk count | Ignored by Mojang client
            final long compressedPackSize = wrapper.read(BedrockTypes.LONG_LE); // compressed pack size
            final byte[] hash = wrapper.read(BedrockTypes.BYTE_ARRAY); // hash
            final boolean premium = wrapper.read(Type.BOOLEAN); // premium
            final short type = wrapper.read(Type.UNSIGNED_BYTE); // type
            final int actualChunkCount = (int) Math.ceil((double) compressedPackSize / maxChunkSize);

            if (resourcePacksStorage.hasPack(idAndVersion.key())) {
                final ResourcePack resourcePack = resourcePacksStorage.getPack(idAndVersion.key());
                resourcePack.setHash(hash);
                resourcePack.setPremium(premium);
                resourcePack.setType(type);
                resourcePack.setCompressedDataLength((int) compressedPackSize, maxChunkSize);
            } else { // Mojang client requests data anyway
                ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Received RESOURCE_PACK_DATA_INFO for unknown pack: " + idAndVersion.key());
            }

            for (int i = 0; i < actualChunkCount; i++) {
                final PacketWrapper resourcePackChunkRequest = wrapper.create(ClientboundBedrockPackets.RESOURCE_PACK_CHUNK_REQUEST);
                resourcePackChunkRequest.write(BedrockTypes.PACK_ID_AND_VERSION, idAndVersion); // pack id and version
                resourcePackChunkRequest.write(BedrockTypes.INT_LE, i); // chunk index
                resourcePackChunkRequest.sendToServer(BedrockProtocol.class);
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.RESOURCE_PACK_CHUNK_DATA, null, wrapper -> {
            wrapper.cancel();
            final ResourcePacksStorage resourcePacksStorage = wrapper.user().get(ResourcePacksStorage.class);
            if (resourcePacksStorage.hasCompletedTransfer()) {
                ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Received RESOURCE_PACK_CHUNK_DATA after transfer completion");
                return;
            }

            final Pair<UUID, String> idAndVersion = wrapper.read(BedrockTypes.PACK_ID_AND_VERSION); // pack id and version
            final int chunkIndex = wrapper.read(BedrockTypes.INT_LE); // chunk index
            wrapper.read(BedrockTypes.LONG_LE); // progress
            final byte[] data = wrapper.read(BedrockTypes.BYTE_ARRAY); // data

            if (resourcePacksStorage.hasPack(idAndVersion.key()) && !resourcePacksStorage.getPack(idAndVersion.key()).isDecompressed()) {
                final ResourcePack resourcePack = resourcePacksStorage.getPack(idAndVersion.key());
                if (resourcePack.processDataChunk(chunkIndex, data)) {
                    Via.getManager().getProviders().get(ResourcePackProvider.class).addPack(resourcePack);
                }
            } else {
                ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Received RESOURCE_PACK_CHUNK_DATA for unknown pack: " + idAndVersion.key());
            }

            if (resourcePacksStorage.areAllPacksDecompressed()) {
                resourcePacksStorage.setCompletedTransfer();
                ViaBedrock.getPlatform().getLogger().log(Level.INFO, "All packs have been decompressed and decrypted");
                final PacketWrapper resourcePackClientResponse = wrapper.create(ClientboundBedrockPackets.RESOURCE_PACK_CLIENT_RESPONSE);
                resourcePackClientResponse.write(Type.UNSIGNED_BYTE, ResourcePackStatus.HAVE_ALL_PACKS); // status
                resourcePackClientResponse.write(BedrockTypes.SHORT_LE_STRING_ARRAY, new String[0]); // resource pack ids
                resourcePackClientResponse.sendToServer(BedrockProtocol.class);
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.RESOURCE_PACK_STACK, null, wrapper -> {
            wrapper.cancel();
            final ResourcePacksStorage resourcePacksStorage = wrapper.user().get(ResourcePacksStorage.class);
            if (resourcePacksStorage.hasFinishedLoading()) {
                ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Received RESOURCE_PACK_STACK after loading completion");
                return;
            }

            wrapper.read(Type.BOOLEAN); // must accept
            final Triple<UUID, String, String>[] behaviourPacks = wrapper.read(BedrockTypes.PACK_ID_AND_VERSION_AND_NAME_ARRAY); // behaviour packs
            final Triple<UUID, String, String>[] resourcePacks = wrapper.read(BedrockTypes.PACK_ID_AND_VERSION_AND_NAME_ARRAY); // resource packs
            wrapper.read(BedrockTypes.STRING); // game version
            final Experiment[] experiments = wrapper.read(BedrockTypes.EXPERIMENT_ARRAY); // experiments
            wrapper.read(Type.BOOLEAN); // experiments previously toggled

            for (Experiment experiment : experiments) {
                if (experiment.enabled()) {
                    ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "This server uses an experimental resource pack: " + experiment.name());
                }
            }

            final UUID[] behaviourPackIds = new UUID[behaviourPacks.length];
            for (int i = 0; i < behaviourPacks.length; i++) {
                behaviourPackIds[i] = behaviourPacks[i].first();
            }
            final UUID[] resourcePackIds = new UUID[resourcePacks.length];
            for (int i = 0; i < resourcePacks.length; i++) {
                resourcePackIds[i] = resourcePacks[i].first();
            }

            resourcePacksStorage.setCompletedTransfer();
            resourcePacksStorage.setPackStack(resourcePackIds, behaviourPackIds);

            if (!resourcePacksStorage.isJavaClientWaitingForPack()) {
                final PacketWrapper resourcePackClientResponse = wrapper.create(ClientboundBedrockPackets.RESOURCE_PACK_CLIENT_RESPONSE);
                resourcePackClientResponse.write(Type.UNSIGNED_BYTE, ResourcePackStatus.COMPLETED); // status
                resourcePackClientResponse.write(BedrockTypes.SHORT_LE_STRING_ARRAY, new String[0]); // pack ids
                resourcePackClientResponse.sendToServer(BedrockProtocol.class);
            }
        });

        protocol.registerServerboundTransition(ServerboundConfigurationPackets1_20_2.RESOURCE_PACK, ServerboundBedrockPackets.RESOURCE_PACK_CLIENT_RESPONSE, wrapper -> {
            final ResourcePacksStorage resourcePacksStorage = wrapper.user().get(ResourcePacksStorage.class);

            wrapper.read(Type.UUID); // pack id
            final int status = wrapper.read(Type.VAR_INT); // status
            switch (status) {
                case JavaResourcePackStatus.SUCCESSFULLY_LOADED:
                    if (!resourcePacksStorage.hasFinishedLoading()) {
                        wrapper.cancel();
                    }

                    resourcePacksStorage.setLoadedOnJavaClient();
                    wrapper.write(Type.UNSIGNED_BYTE, ResourcePackStatus.COMPLETED); // status
                    wrapper.write(BedrockTypes.SHORT_LE_STRING_ARRAY, new String[0]); // pack ids
                    break;
                case JavaResourcePackStatus.FAILED_DOWNLOAD:
                case JavaResourcePackStatus.INVALID_URL:
                case JavaResourcePackStatus.FAILED_RELOAD:
                case JavaResourcePackStatus.DISCARDED:
                    if (!resourcePacksStorage.hasFinishedLoading()) {
                        wrapper.cancel();
                    }

                    resourcePacksStorage.setJavaClientWaitingForPack(false);
                    ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Client resource pack download/loading failed");
                    wrapper.write(Type.UNSIGNED_BYTE, ResourcePackStatus.COMPLETED); // status
                    wrapper.write(BedrockTypes.SHORT_LE_STRING_ARRAY, new String[0]); // pack ids
                    break;
                case JavaResourcePackStatus.DECLINED:
                    final ResourcePacksStorage emptyResourcePackStorage = new ResourcePacksStorage();
                    emptyResourcePackStorage.setCompletedTransfer();
                    wrapper.user().put(emptyResourcePackStorage);

                    wrapper.write(Type.UNSIGNED_BYTE, ResourcePackStatus.HAVE_ALL_PACKS); // status
                    wrapper.write(BedrockTypes.SHORT_LE_STRING_ARRAY, new String[0]); // pack ids
                    break;
                case JavaResourcePackStatus.ACCEPTED:
                    resourcePacksStorage.setJavaClientWaitingForPack(true);
                    final Set<String> missingPacks = new HashSet<>();
                    for (ResourcePack pack : resourcePacksStorage.getPacks()) {
                        if (resourcePacksStorage.isPreloaded(pack.packId())) continue;

                        if (Via.getManager().getProviders().get(ResourcePackProvider.class).hasPack(pack)) {
                            Via.getManager().getProviders().get(ResourcePackProvider.class).loadPack(pack);
                        } else {
                            missingPacks.add(pack.packId() + "_" + pack.version());
                        }
                    }

                    if (!missingPacks.isEmpty()) {
                        ViaBedrock.getPlatform().getLogger().log(Level.INFO, "Downloading " + missingPacks.size() + " packs");
                        wrapper.write(Type.UNSIGNED_BYTE, ResourcePackStatus.SEND_PACKS); // status
                        wrapper.write(BedrockTypes.SHORT_LE_STRING_ARRAY, missingPacks.toArray(new String[0])); // pack ids
                    } else {
                        resourcePacksStorage.setCompletedTransfer();
                        wrapper.write(Type.UNSIGNED_BYTE, ResourcePackStatus.HAVE_ALL_PACKS); // status
                        wrapper.write(BedrockTypes.SHORT_LE_STRING_ARRAY, new String[0]); // pack ids
                    }
                    break;
                case JavaResourcePackStatus.DOWNLOADED:
                    wrapper.cancel();
                    break;
                default:
                    BedrockProtocol.kickForIllegalState(wrapper.user(), "Unknown resource pack status: " + status);
            }
        });
    }

}
