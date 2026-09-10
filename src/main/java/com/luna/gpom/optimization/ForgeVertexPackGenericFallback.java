package com.luna.gpom.optimization;

import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;

/** Cold generic-format half of the Forge vertex packer. */
public final class ForgeVertexPackGenericFallback {
    private ForgeVertexPackGenericFallback() {
    }

    public static void pack(float[] from, int[] to, VertexFormat format, int vertex, int elementIndex) {
        VertexFormatElement element = format.getElement(elementIndex);
        int vertexStart = vertex * format.getSize() + format.getOffset(elementIndex);
        int count = Math.min(element.getElementCount(), 4);
        VertexFormatElement.EnumType type = element.getType();
        int size = type.getSize();
        int mask = (256 << (8 * (size - 1))) - 1;

        if (type == VertexFormatElement.EnumType.FLOAT) {
            for (int component = 0; component < count; component++) {
                float value = component < from.length ? from[component] : 0.0F;
                int bytePosition = vertexStart + size * component;
                int index = bytePosition >> 2;
                int shift = (bytePosition & 3) * 8;
                int bits = Float.floatToRawIntBits(value);
                to[index] = to[index] & ~(mask << shift) | (bits & mask) << shift;
            }
            return;
        }

        if ((vertexStart & 3) == 0 && type == VertexFormatElement.EnumType.UBYTE
                && count == 4 && from.length >= 4) {
            int component0 = Math.round(from[0] * 255.0F);
            int component1 = Math.round(from[1] * 255.0F);
            int component2 = Math.round(from[2] * 255.0F);
            int component3 = Math.round(from[3] * 255.0F);
            to[vertexStart >> 2] = component0 & 0xFF
                    | (component1 & 0xFF) << 8
                    | (component2 & 0xFF) << 16
                    | (component3 & 0xFF) << 24;
            return;
        }

        if ((vertexStart & 3) == 0 && type == VertexFormatElement.EnumType.SHORT
                && count == 2 && from.length >= 2) {
            int component0 = Math.round(from[0] * 32767.0F);
            int component1 = Math.round(from[1] * 32767.0F);
            to[vertexStart >> 2] = component0 & 0xFFFF | (component1 & 0xFFFF) << 16;
            return;
        }

        if ((vertexStart & 3) == 0 && type == VertexFormatElement.EnumType.BYTE
                && count == 3 && from.length >= 3) {
            int component0 = Math.round(from[0] * 127.0F);
            int component1 = Math.round(from[1] * 127.0F);
            int component2 = Math.round(from[2] * 127.0F);
            int index = vertexStart >> 2;
            to[index] = to[index] & 0xFF000000
                    | component0 & 0xFF
                    | (component1 & 0xFF) << 8
                    | (component2 & 0xFF) << 16;
            return;
        }

        if ((vertexStart & 3) == 0 && type == VertexFormatElement.EnumType.BYTE
                && count == 4 && from.length >= 4) {
            int component0 = Math.round(from[0] * 127.0F);
            int component1 = Math.round(from[1] * 127.0F);
            int component2 = Math.round(from[2] * 127.0F);
            int component3 = Math.round(from[3] * 127.0F);
            to[vertexStart >> 2] = component0 & 0xFF
                    | (component1 & 0xFF) << 8
                    | (component2 & 0xFF) << 16
                    | (component3 & 0xFF) << 24;
            return;
        }

        if ((vertexStart & 3) == 0 && type == VertexFormatElement.EnumType.SHORT
                && count == 4 && from.length >= 4) {
            int component0 = Math.round(from[0] * 32767.0F);
            int component1 = Math.round(from[1] * 32767.0F);
            int component2 = Math.round(from[2] * 32767.0F);
            int component3 = Math.round(from[3] * 32767.0F);
            int index = vertexStart >> 2;
            to[index] = component0 & 0xFFFF | (component1 & 0xFFFF) << 16;
            to[index + 1] = component2 & 0xFFFF | (component3 & 0xFFFF) << 16;
            return;
        }

        int scale = type == VertexFormatElement.EnumType.UBYTE
                || type == VertexFormatElement.EnumType.USHORT
                || type == VertexFormatElement.EnumType.UINT
                ? mask
                : mask >> 1;
        for (int component = 0; component < count; component++) {
            float value = component < from.length ? from[component] : 0.0F;
            int bytePosition = vertexStart + size * component;
            int index = bytePosition >> 2;
            int shift = (bytePosition & 3) * 8;
            int bits = Math.round(value * scale);
            to[index] = to[index] & ~(mask << shift) | (bits & mask) << shift;
        }
    }
}
