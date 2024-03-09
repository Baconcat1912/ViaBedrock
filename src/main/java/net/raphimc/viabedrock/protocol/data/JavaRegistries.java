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

import com.viaversion.viaversion.libs.fastutil.ints.IntIntPair;
import com.viaversion.viaversion.libs.opennbt.tag.builtin.*;
import net.raphimc.viabedrock.ViaBedrock;
import net.raphimc.viabedrock.api.util.MathUtil;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.storage.GameSessionStorage;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class JavaRegistries {

    public static CompoundTag createJavaRegistries(final GameSessionStorage gameSession) {
        final CompoundTag registries = BedrockProtocol.MAPPINGS.getJavaRegistries().copy();
        final CompoundTag dimensionRegistry = registries.get("minecraft:dimension_type");
        final CompoundTag biomeRegistry = registries.get("minecraft:worldgen/biome");

        modifyDimensionRegistry(gameSession, dimensionRegistry);
        biomeRegistry.put("value", buildJavaBiomeRegistry(gameSession.getBedrockBiomeDefinitions()));

        return registries;
    }

    private static void modifyDimensionRegistry(final GameSessionStorage gameSession, final CompoundTag dimensionRegistry) {
        final ListTag<?> dimensions = dimensionRegistry.get("value");
        final Map<String, CompoundTag> dimensionMap = dimensions.stream()
                .map(CompoundTag.class::cast)
                .collect(Collectors.toMap(tag -> tag.get("name").getValue().toString(), tag -> tag.get("element")));

        if (gameSession.getBedrockVanillaVersion().isLowerThan("1.18.0")) {
            dimensionMap.get("minecraft:overworld").put("min_y", new IntTag(0));
            dimensionMap.get("minecraft:overworld").put("height", new IntTag(256));
            dimensionMap.get("minecraft:overworld").put("logical_height", new IntTag(256));
            dimensionMap.get("minecraft:overworld_caves").put("min_y", new IntTag(0));
            dimensionMap.get("minecraft:overworld_caves").put("height", new IntTag(256));
            dimensionMap.get("minecraft:overworld_caves").put("logical_height", new IntTag(256));
        }
        for (Map.Entry<String, IntIntPair> entry : gameSession.getBedrockDimensionDefinitions().entrySet()) {
            final CompoundTag dimensionTag = new CompoundTag();
            final int height = entry.getValue().rightInt() - entry.getValue().leftInt();
            dimensionTag.put("min_y", new IntTag(entry.getValue().leftInt()));
            dimensionTag.put("height", new IntTag(height));
            dimensionTag.put("logical_height", new IntTag(height));
            dimensionMap.get(entry.getKey()).putAll(dimensionTag);
        }
    }

    private static ListTag<CompoundTag> buildJavaBiomeRegistry(final CompoundTag biomeDefinitions) {
        final ListTag<CompoundTag> javaBiomes = new ListTag<>(CompoundTag.class);
        javaBiomes.add(getTheVoidBiome());

        final Map<String, Object> fogColor = BedrockProtocol.MAPPINGS.getBedrockToJavaBiomeExtraData().get("fog_color");
        final Map<String, Object> waterFogColor = BedrockProtocol.MAPPINGS.getBedrockToJavaBiomeExtraData().get("water_fog_color");
        final Map<String, Object> foliageColor = BedrockProtocol.MAPPINGS.getBedrockToJavaBiomeExtraData().get("foliage_color");
        final Map<String, Object> grassColor = BedrockProtocol.MAPPINGS.getBedrockToJavaBiomeExtraData().get("grass_color");
        final Map<String, Object> grassColorModifier = BedrockProtocol.MAPPINGS.getBedrockToJavaBiomeExtraData().get("grass_color_modifier");
        final Map<String, Object> moodSound = BedrockProtocol.MAPPINGS.getBedrockToJavaBiomeExtraData().get("mood_sound");

        for (Map.Entry<String, Tag> entry : biomeDefinitions.entrySet()) {
            final String bedrockIdentifier = entry.getKey();
            final String javaIdentifier = "minecraft:" + bedrockIdentifier;
            final CompoundTag bedrockBiome = (CompoundTag) entry.getValue();
            final CompoundTag javaBiome = new CompoundTag();
            final int bedrockId = BedrockProtocol.MAPPINGS.getBedrockBiomes().getOrDefault(bedrockIdentifier, -1);
            final int javaId = bedrockId + 1;

            if (bedrockId == -1) {
                ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Missing biome mapping for " + bedrockIdentifier);
                continue;
            }

            javaBiome.put("name", new StringTag(javaIdentifier));
            javaBiome.put("id", new IntTag(javaId));
            final CompoundTag element = new CompoundTag();
            javaBiome.put("element", element);
            element.put("temperature", bedrockBiome.get("temperature"));
            element.put("downfall", bedrockBiome.get("downfall"));
            element.put("has_precipitation", bedrockBiome.get("rain"));

            final List<String> tags = bedrockBiome.getListTag("tags").stream().map(StringTag.class::cast).map(StringTag::getValue).collect(Collectors.toList());

            final CompoundTag effects = new CompoundTag();
            element.put("effects", effects);

            final float blue_spores = bedrockBiome.get("blue_spores") instanceof FloatTag ? bedrockBiome.<FloatTag>get("blue_spores").asFloat() : 0;
            final float white_ash = bedrockBiome.get("white_ash") instanceof FloatTag ? bedrockBiome.<FloatTag>get("white_ash").asFloat() : 0;
            final float red_spores = bedrockBiome.get("red_spores") instanceof FloatTag ? bedrockBiome.<FloatTag>get("red_spores").asFloat() : 0;
            final float ash = bedrockBiome.get("ash") instanceof FloatTag ? bedrockBiome.<FloatTag>get("ash").asFloat() : 0;
            if (blue_spores > 0) {
                effects.put("particle", createParticle("minecraft:warped_spore", blue_spores / 10F));
            } else if (white_ash > 0) {
                effects.put("particle", createParticle("minecraft:white_ash", white_ash / 10F));
            } else if (red_spores > 0) {
                effects.put("particle", createParticle("minecraft:crimson_spore", red_spores / 10F));
            } else if (ash > 0) {
                effects.put("particle", createParticle("minecraft:ash", ash / 10F));
            }

            final int waterColorR = (int) (bedrockBiome.<FloatTag>get("waterColorR").asFloat() * 255);
            final int waterColorG = (int) (bedrockBiome.<FloatTag>get("waterColorG").asFloat() * 255);
            final int waterColorB = (int) (bedrockBiome.<FloatTag>get("waterColorB").asFloat() * 255);
            final int waterColorA = (int) (bedrockBiome.<FloatTag>get("waterColorA").asFloat() * 255);
            final int waterTransparency = (int) (bedrockBiome.<FloatTag>get("waterTransparency").asFloat() * 255);
            final int waterColor = (waterColorR << 16) + (waterColorG << 8) + waterColorB;
            effects.put("water_color", new IntTag(waterColor));

            for (String tag : tags) {
                if (fogColor.containsKey(tag)) {
                    effects.put("fog_color", new IntTag((Integer) fogColor.get(tag)));
                }
                if (foliageColor.containsKey(tag)) {
                    effects.put("foliage_color", new IntTag((Integer) foliageColor.get(tag)));
                }
                if (grassColor.containsKey(tag)) {
                    effects.put("grass_color", new IntTag((Integer) grassColor.get(tag)));
                }
                if (grassColorModifier.containsKey(tag)) {
                    effects.put("grass_color_modifier", new StringTag((String) grassColorModifier.get(tag)));
                }
                if (moodSound.containsKey(tag)) {
                    effects.put("mood_sound", createMoodSound((String) moodSound.get(tag)));
                }
            }

            if (waterFogColor.containsKey(bedrockIdentifier)) {
                effects.put("water_fog_color", new IntTag((Integer) waterFogColor.get(bedrockIdentifier)));
            }

            // One warning is enough
            if (!effects.contains("fog_color")) {
                ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Missing fog color for " + bedrockIdentifier + ": " + bedrockBiome);
            } else if (!effects.contains("water_fog_color")) {
                ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Missing water fog color for " + bedrockIdentifier + ": " + bedrockBiome);
            } else if (!effects.contains("mood_sound")) {
                ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Missing mood sound for " + bedrockIdentifier + ": " + bedrockBiome);
            }

            if (tags.contains("the_end")) {
                effects.put("sky_color", new IntTag(0));
            } else {
                effects.put("sky_color", new IntTag(getSkyColor(bedrockBiome.<FloatTag>get("temperature").asFloat())));
            }

            // TODO: Enhancement: Biome sounds

            javaBiomes.add(javaBiome);
        }

        return javaBiomes;
    }

    private static CompoundTag getTheVoidBiome() {
        final CompoundTag biome = new CompoundTag();
        biome.put("name", new StringTag("minecraft:the_void"));
        biome.put("id", new IntTag(0));

        final CompoundTag element = new CompoundTag();
        biome.put("element", element);
        element.put("temperature", new FloatTag(0.5F));
        element.put("downfall", new FloatTag(0.5F));
        element.put("has_precipitation", new ByteTag((byte) 0));

        final CompoundTag effects = new CompoundTag();
        element.put("effects", effects);
        effects.put("sky_color", new IntTag(8103167));
        effects.put("water_fog_color", new IntTag(329011));
        effects.put("fog_color", new IntTag(12638463));
        effects.put("water_color", new IntTag(4159204));
        effects.put("mood_sound", createMoodSound("minecraft:ambient.cave"));
        return biome;
    }

    private static CompoundTag createParticle(final String name, final float probability) {
        final CompoundTag particle = new CompoundTag();
        particle.put("probability", new FloatTag(probability));
        final CompoundTag options = new CompoundTag();
        particle.put("options", options);
        options.put("type", new StringTag(name));
        return particle;
    }

    private static CompoundTag createMoodSound(final String soundId) {
        final CompoundTag moodSound = new CompoundTag();
        moodSound.put("tick_delay", new IntTag(6000));
        moodSound.put("offset", new FloatTag(2F));
        moodSound.put("sound", new StringTag(soundId));
        moodSound.put("block_search_extent", new IntTag(8));
        return moodSound;
    }

    private static int getSkyColor(final float temperature) {
        float f = temperature / 3F;
        f = MathUtil.clamp(f, -1F, 1F);
        return Color.HSBtoRGB(0.62222224F - f * 0.05F, 0.5F + f * 0.1F, 1F) & 0xFFFFFF;
    }

}
