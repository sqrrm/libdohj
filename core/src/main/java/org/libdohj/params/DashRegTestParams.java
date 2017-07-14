/*
 * Copyright 2017 Hash Engineering Solutions.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.libdohj.params;

import org.bitcoinj.core.AltcoinBlock;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;

import java.math.BigInteger;

import static com.google.common.base.Preconditions.checkState;

/**
 * Network parameters for the regression test mode of bitcoind in which all blocks are trivially solvable.
 */

//TODO just copied form litcoin not adopted required changes yet!!!

public class DashRegTestParams extends DashTestNet3Params {
    private static final BigInteger MAX_TARGET = new BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16);

    public DashRegTestParams() {
        super();
        // Difficulty adjustments are disabled for regtest.
        // By setting the block interval for difficulty adjustments to Integer.MAX_VALUE we make sure difficulty never changes.
        interval = Integer.MAX_VALUE;
        maxTarget = MAX_TARGET;
        subsidyDecreaseBlockCount = 150;
        port = 19444;
        id = ID_DASH_REGTEST;
        packetMagic = 0xfcc1b7dc;
    }

    @Override
    public boolean allowEmptyPeerChain() {
        return true;
    }

    private static Block genesis;



    @Override
    public Block getGenesisBlock() {
        synchronized (DashRegTestParams.class) {
            if (genesis == null) {
                genesis = super.getGenesisBlock();
                genesis.setNonce(1096447);
                genesis.setDifficultyTarget(0x207fffffL);
                genesis.setTime(1417713337L);
                ((AltcoinBlock)genesis).resetAltcoinHash();
                checkState(genesis.getVersion() == 1);
                checkState(genesis.getMerkleRoot().toString().equals("e0028eb9648db56b1ac77cf090b99048a8007e2bb64b68f092c03c7f56a662c7"));
                checkState(genesis.getHashAsString().toLowerCase().equals("000008ca1832a4baf228eb1553c03d3a2c8e02399550dd6ea8d65cec3ef23d2e"));
                genesis.verifyHeader();
            }
            return genesis;
        }
    }

    private static DashRegTestParams instance;

    public static synchronized DashRegTestParams get() {
        if (instance == null) {
            instance = new DashRegTestParams();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return ID_DASH_REGTEST;
    }

    @Override
    /** the testnet rules don't work for regtest, where difficulty stays the same */
    public long calculateNewDifficultyTarget(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore)
            throws VerificationException, BlockStoreException {
        final Block prev = storedPrev.getHeader();
        return prev.getDifficultyTarget();
    }

    @Override
    public boolean allowMinDifficultyBlocks() {
        return false;
    }
}
