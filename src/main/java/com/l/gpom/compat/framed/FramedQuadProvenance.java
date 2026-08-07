package com.l.gpom.compat.framed;

import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Runtime-only material ownership for framed-model quads.
 *
 * <p>Vanilla BakedQuad has no extension slot. Provenance is therefore held in an
 * identity-weak side table and deliberately is not copied into tile NBT. CTM
 * neighbors are resolved from the supplied live block access, never from saved masks.</p>
 */
public final class FramedQuadProvenance {
    private static final ReferenceQueue<BakedQuad> STALE_QUADS = new ReferenceQueue<>();
    private static final ConcurrentMap<QuadKey, QuadData> QUADS = new ConcurrentHashMap<>();
    private static final ThreadLocal<QuadLookupKey> QUAD_LOOKUP = ThreadLocal.withInitial(QuadLookupKey::new);

    private FramedQuadProvenance() {
    }

    public static void register(BakedQuad quad, int materialIndex, IBlockState materialState,
                                EnumFacing faceHint, Source source) {
        if (quad == null || materialState == null) {
            return;
        }
        discardStaleEntries();
        QUADS.put(new QuadKey(quad, STALE_QUADS), QuadData.create(materialIndex, materialState, face(quad, faceHint), source));
    }

    public static QuadData data(BakedQuad quad) {
        if (quad == null) {
            return null;
        }
        discardStaleEntries();
        QuadLookupKey lookup = QUAD_LOOKUP.get();
        lookup.set(quad);
        try {
            return QUADS.get(lookup);
        } finally {
            lookup.clear();
        }
    }

    /** Returns live effective-neighbor context for CTM consumers such as AUSM. */
    public static CtmContext ctmContext(BakedQuad quad, IBlockAccess access, BlockPos position) {
        QuadData data = data(quad);
        if (data == null || access == null || position == null) {
            return null;
        }
        return new CtmContext(data, FramedBlockEffectiveState.wrap(access), position);
    }

    private static EnumFacing face(BakedQuad quad, EnumFacing fallback) {
        Object value = MinecraftMappingCompat.invoke(quad, "bakedQuad.getFace",
                MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_178210_d", "getFace");
        return value instanceof EnumFacing ? (EnumFacing) value : fallback;
    }

    private static void discardStaleEntries() {
        QuadKey stale;
        while ((stale = (QuadKey) STALE_QUADS.poll()) != null) {
            QUADS.remove(stale);
        }
    }

    public enum Source {
        COPIED_MODEL,
        HOST_FALLBACK
    }

    public static final class QuadData {
        private final int materialIndex;
        private final IBlockState materialState;
        private final String materialId;
        private final int materialMeta;
        private final int emission;
        private final boolean bloom;
        private final EnumFacing face;
        private final Source source;

        private QuadData(int materialIndex, IBlockState materialState, String materialId, int materialMeta,
                         int emission, boolean bloom, EnumFacing face, Source source) {
            this.materialIndex = materialIndex;
            this.materialState = materialState;
            this.materialId = materialId;
            this.materialMeta = materialMeta;
            this.emission = emission;
            this.bloom = bloom;
            this.face = face;
            this.source = source;
        }

        private static QuadData create(int materialIndex, IBlockState state, EnumFacing face, Source source) {
            Block block = MinecraftMappingCompat.blockStateBlock(state);
            ResourceLocation name = MinecraftMappingCompat.blockRegistryName(block);
            return new QuadData(
                    materialIndex,
                    state,
                    name == null ? "" : name.toString(),
                    block == null ? 0 : MinecraftMappingCompat.blockMetaFromState(block, state),
                    FramedMaterialData.visualEmission(state),
                    FramedMaterialData.hasBloom(state),
                    face,
                    source == null ? Source.COPIED_MODEL : source
            );
        }

        public int materialIndex() {
            return materialIndex;
        }

        public IBlockState materialState() {
            return materialState;
        }

        public String materialId() {
            return materialId;
        }

        public int materialMeta() {
            return materialMeta;
        }

        public int emission() {
            return emission;
        }

        public boolean bloom() {
            return bloom;
        }

        public EnumFacing face() {
            return face;
        }

        public Source source() {
            return source;
        }
    }

    public static final class CtmContext {
        private final QuadData quad;
        private final IBlockAccess access;
        private final BlockPos position;

        private CtmContext(QuadData quad, IBlockAccess access, BlockPos position) {
            this.quad = quad;
            this.access = access;
            this.position = position;
        }

        public QuadData quad() {
            return quad;
        }

        /** Effective framed-material access; AUSM should query this live for CTM neighbors. */
        public IBlockAccess access() {
            return access;
        }

        public BlockPos position() {
            return position;
        }
    }

    private static final class QuadKey extends WeakReference<BakedQuad> {
        private final int identityHash;

        private QuadKey(BakedQuad quad, ReferenceQueue<BakedQuad> queue) {
            super(quad, queue);
            this.identityHash = System.identityHashCode(quad);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            BakedQuad quad = get();
            if (quad == null) {
                return false;
            }
            if (other instanceof QuadKey) {
                return quad == ((QuadKey) other).get();
            }
            return other instanceof QuadLookupKey && quad == ((QuadLookupKey) other).quad;
        }
    }

    /** Mutable lookup token; it is thread-local and is never stored in QUADS. */
    private static final class QuadLookupKey {
        private BakedQuad quad;
        private int identityHash;

        private void set(BakedQuad quad) {
            this.quad = quad;
            this.identityHash = System.identityHashCode(quad);
        }

        private void clear() {
            quad = null;
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof QuadKey) {
                return quad != null && quad == ((QuadKey) other).get();
            }
            return other instanceof QuadLookupKey && quad != null && quad == ((QuadLookupKey) other).quad;
        }
    }

}
