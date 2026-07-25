package com.l.gpom.compat.sfm.integration.capability;

public interface GpomManaStorage {
    int getMana();

    int getMaxMana();

    boolean canReceiveMana();

    boolean canExtractMana();

    int receiveMana(int amount, boolean simulate);

    int extractMana(int amount, boolean simulate);
}
