package com.l.gpom.compat.sfm.integration.capability;

public interface GpomPotentialEnergyStorage {
    int getEnergy();

    int getMaxEnergy();

    boolean canReceiveEnergy();

    boolean canExtractEnergy();

    int receiveEnergy(int amount, boolean simulate);

    int extractEnergy(int amount, boolean simulate);
}
