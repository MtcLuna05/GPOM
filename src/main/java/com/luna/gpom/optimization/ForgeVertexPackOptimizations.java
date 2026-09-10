package com.luna.gpom.optimization;

import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;

/** Type-hoisted equivalent of Forge 1.12's generic LightUtil.pack inner loop. */
public final class ForgeVertexPackOptimizations {
    private static volatile VertexFormat pipelineBlockFormat;

    private ForgeVertexPackOptimizations() {
    }

    public static void pack(float[] from, int[] to, VertexFormat format, int vertex, int elementIndex) {
        if ((format == pipelineBlockFormat || validateAndCachePipelineBlockFormat(format))
                && packPipelineBlock(from, to, vertex, elementIndex)) {
            return;
        }
        ForgeVertexPackGenericFallback.pack(from, to, format, vertex, elementIndex);
    }

    private static boolean validateAndCachePipelineBlockFormat(VertexFormat format) {
        if (format == null || format.getSize() != 56 || format.getElementCount() != 10
                || !matches(format, 0, 0, VertexFormatElement.EnumType.FLOAT, 3)
                || !matches(format, 1, 12, VertexFormatElement.EnumType.UBYTE, 4)
                || !matches(format, 2, 16, VertexFormatElement.EnumType.FLOAT, 2)
                || !matches(format, 3, 24, VertexFormatElement.EnumType.SHORT, 2)
                || !matches(format, 4, 28, VertexFormatElement.EnumType.BYTE, 3)
                || !matches(format, 5, 31, VertexFormatElement.EnumType.BYTE, 1)
                || !matches(format, 6, 32, VertexFormatElement.EnumType.SHORT, 4)
                || !matches(format, 7, 40, VertexFormatElement.EnumType.FLOAT, 2)
                || !matches(format, 8, 48, VertexFormatElement.EnumType.BYTE, 4)
                || !matches(format, 9, 52, VertexFormatElement.EnumType.BYTE, 4)) {
            return false;
        }
        pipelineBlockFormat = format;
        return true;
    }

    private static boolean matches(VertexFormat format, int index, int offset,
                                   VertexFormatElement.EnumType type, int count) {
        VertexFormatElement element = format.getElement(index);
        return format.getOffset(index) == offset
                && element.getType() == type
                && element.getElementCount() == count;
    }

    private static boolean packPipelineBlock(float[] from, int[] to, int vertex, int elementIndex) {
        int byteStart = vertex * 56;
        int index;
        switch (elementIndex) {
            case 0:
                if (from.length < 3) return false;
                index = byteStart >> 2;
                to[index] = Float.floatToRawIntBits(from[0]);
                to[index + 1] = Float.floatToRawIntBits(from[1]);
                to[index + 2] = Float.floatToRawIntBits(from[2]);
                return true;
            case 1:
                if (from.length < 4) return false;
                index = (byteStart + 12) >> 2;
                to[index] = Math.round(from[0] * 255.0F) & 0xFF
                        | (Math.round(from[1] * 255.0F) & 0xFF) << 8
                        | (Math.round(from[2] * 255.0F) & 0xFF) << 16
                        | (Math.round(from[3] * 255.0F) & 0xFF) << 24;
                return true;
            case 2:
                if (from.length < 2) return false;
                index = (byteStart + 16) >> 2;
                to[index] = Float.floatToRawIntBits(from[0]);
                to[index + 1] = Float.floatToRawIntBits(from[1]);
                return true;
            case 3:
                if (from.length < 2) return false;
                index = (byteStart + 24) >> 2;
                to[index] = Math.round(from[0] * 32767.0F) & 0xFFFF
                        | (Math.round(from[1] * 32767.0F) & 0xFFFF) << 16;
                return true;
            case 4:
                if (from.length < 3) return false;
                index = (byteStart + 28) >> 2;
                to[index] = to[index] & 0xFF000000
                        | Math.round(from[0] * 127.0F) & 0xFF
                        | (Math.round(from[1] * 127.0F) & 0xFF) << 8
                        | (Math.round(from[2] * 127.0F) & 0xFF) << 16;
                return true;
            case 5:
                if (from.length < 1) return false;
                index = (byteStart + 31) >> 2;
                to[index] = to[index] & 0x00FFFFFF | (Math.round(from[0] * 127.0F) & 0xFF) << 24;
                return true;
            case 6:
                if (from.length < 4) return false;
                index = (byteStart + 32) >> 2;
                to[index] = Math.round(from[0] * 32767.0F) & 0xFFFF
                        | (Math.round(from[1] * 32767.0F) & 0xFFFF) << 16;
                to[index + 1] = Math.round(from[2] * 32767.0F) & 0xFFFF
                        | (Math.round(from[3] * 32767.0F) & 0xFFFF) << 16;
                return true;
            case 7:
                if (from.length < 2) return false;
                index = (byteStart + 40) >> 2;
                to[index] = Float.floatToRawIntBits(from[0]);
                to[index + 1] = Float.floatToRawIntBits(from[1]);
                return true;
            case 8:
            case 9:
                if (from.length < 4) return false;
                index = (byteStart + (elementIndex == 8 ? 48 : 52)) >> 2;
                to[index] = Math.round(from[0] * 127.0F) & 0xFF
                        | (Math.round(from[1] * 127.0F) & 0xFF) << 8
                        | (Math.round(from[2] * 127.0F) & 0xFF) << 16
                        | (Math.round(from[3] * 127.0F) & 0xFF) << 24;
                return true;
            default:
                return false;
        }
    }
}
