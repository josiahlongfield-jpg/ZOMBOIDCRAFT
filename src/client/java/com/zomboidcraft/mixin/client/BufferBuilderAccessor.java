package com.zomboidcraft.mixin.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Lets FastVerts write whole vertices into a BufferBuilder at once (as Sodium's own vertex writer does): reserve the
 * bytes, then move the builder's count and last-vertex pointer past them.
 */
@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {
    @Accessor("buffer")
    ByteBufferBuilder zomboidcraft$buffer();

    @Accessor("format")
    VertexFormat zomboidcraft$format();

    @Accessor("primitiveTopology")
    PrimitiveTopology zomboidcraft$topology();

    @Accessor("building")
    boolean zomboidcraft$building();

    @Accessor("vertices")
    int zomboidcraft$vertices();

    @Accessor("vertices")
    void zomboidcraft$setVertices(int vertices);

    @Accessor("vertexPointer")
    void zomboidcraft$setVertexPointer(long pointer);

    @Accessor("elementsToFill")
    void zomboidcraft$setElementsToFill(int elements);

    @Invoker("endLastVertex")
    void zomboidcraft$endLastVertex();
}
