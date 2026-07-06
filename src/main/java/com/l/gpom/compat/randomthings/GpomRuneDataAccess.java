package com.l.gpom.compat.randomthings;

public interface GpomRuneDataAccess {
    int[][] gpom$getRuneDataRaw();

    void gpom$setRuneDataRaw(int[][] data);

    int[] gpom$getRuneDisconnectedEdges();

    void gpom$setRuneDisconnectedEdges(int[] disconnectedEdges);

    boolean gpom$hasRuneConnectionMetadata();

    void gpom$setRuneConnectionMetadata(boolean hasMetadata);
}
