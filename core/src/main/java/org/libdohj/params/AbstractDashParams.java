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

import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.MonetaryFormat;
import org.dashj.hash.X11;
import org.libdohj.core.AltcoinNetworkParameters;
import org.libdohj.core.AltcoinSerializer;
import org.libdohj.core.AuxPoWNetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;

import static org.bitcoinj.core.Coin.COIN;

/**
 * Common parameters for Dash networks.
 */
public abstract class AbstractDashParams extends NetworkParameters implements AltcoinNetworkParameters {
    /** Standard format for the DASH denomination. */
    public static final MonetaryFormat DASH;
    /** Standard format for the mDASH denomination. */
    public static final MonetaryFormat MDASH;
    /** Standard format for the Duff denomination. */
    public static final MonetaryFormat DUFF;


    public static final int DASH_TARGET_TIMESPAN = 24 * 60 * 60;  // 24 hours per difficulty cycle, on average.
    public static final int DASH_TARGET_SPACING = 150;  // 2.5 minutes per block.
    public static final int DASH_INTERVAL = DASH_TARGET_TIMESPAN / DASH_TARGET_SPACING;

    /** Currency code for base 1 Dash. */
    public static final String CODE_DASH = "DASH";
    /** Currency code for base 1/1,000 Dash. */
    public static final String CODE_MDASH = "mDASH";
    /** Currency code for base 1/100,000,000 Dash. */
    public static final String CODE_DUFF = "Duffs";

    static {
        DASH = MonetaryFormat.BTC.noCode()
            .code(0, CODE_DASH)
            .code(3, CODE_MDASH)
            .code(7, CODE_DUFF);
        MDASH = DASH.shift(3).minDecimals(2).optionalDecimals(2);
        DUFF = DASH.shift(7).minDecimals(0).optionalDecimals(2);
    }

    /** The string returned by getId() for the main, production network where people trade things. */
    public static final String ID_DASH_MAINNET = "org.dash.production";
    /** The string returned by getId() for the testnet. */
    public static final String ID_DASH_TESTNET = "org.dash.test";
    /** The string returned by getId() for the testnet. */
    public static final String ID_DASH_REGTEST = "org.dash.regtest";

    protected final int newInterval;
    protected final int newTargetTimespan;
    protected final int diffChangeTarget;

    protected Logger log = LoggerFactory.getLogger(AbstractDashParams.class);
    public static final int DASHCOIN_PROTOCOL_VERSION_CURRENT = 70206;


    public AbstractDashParams(final int setDiffChangeTarget) {
        super();
        genesisBlock = createGenesis(this);
        interval = DASH_INTERVAL;
        newInterval = DASH_INTERVAL;
        targetTimespan = DASH_TARGET_TIMESPAN;
        newTargetTimespan = DASH_TARGET_TIMESPAN;
        maxTarget = Utils.decodeCompactBits(0x1e0fffffL);
        diffChangeTarget = setDiffChangeTarget;

        packetMagic = 0xbf0c6bbd;
        bip32HeaderPub = 0x0488B21E; //The 4 byte header that serializes in base58 to "xpub".
        bip32HeaderPriv = 0x0488ADE4; //The 4 byte header that serializes in base58 to "xprv"
    }

    private static AltcoinBlock createGenesis(NetworkParameters params) {
        AltcoinBlock genesisBlock = new AltcoinBlock(params, Block.BLOCK_VERSION_GENESIS);
        Transaction t = new Transaction(params);
        try {
            byte[] bytes = Utils.HEX.decode
                    ("04ffff001d01044c5957697265642030392f4a616e2f3230313420546865204772616e64204578706572696d656e7420476f6573204c6976653a204f76657273746f636b2e636f6d204973204e6f7720416363657074696e6720426974636f696e73");
            t.addInput(new TransactionInput(params, t, bytes));
            ByteArrayOutputStream scriptPubKeyBytes = new ByteArrayOutputStream();
            Script.writeBytes(scriptPubKeyBytes, Utils.HEX.decode
                    ("040184710fa689ad5023690c80f3a49c8f13f8d45b8c857fbcbc8bc4a8e4d3eb4b10f4d4604fa08dce601aaf0f470216fe1b51850b4acf21b179c45070ac7b03a9"));
            scriptPubKeyBytes.write(ScriptOpCodes.OP_CHECKSIG);
            t.addOutput(new TransactionOutput(params, t, COIN.multiply(50), scriptPubKeyBytes.toByteArray()));
        } catch (Exception e) {
            // Cannot happen.
            throw new RuntimeException(e);
        }
        genesisBlock.addTransaction(t);
        return genesisBlock;
    }

    @Override
    public Coin getBlockSubsidy(final int height) {
        return Coin.valueOf(10000, 0);
    }

    public MonetaryFormat getMonetaryFormat() {
        return DASH;
    }

    @Override
    public Coin getMaxMoney() {
        // TODO: Change to be Dash compatible
        return MAX_MONEY;
    }

    @Override
    public Coin getMinNonDustOutput() {
        return Coin.valueOf(30000);
    }

    @Override
    public String getUriScheme() {
        return "dash";
    }

    @Override
    public boolean hasMaxMoney() {
        return true;
    }

    @Override
    public void checkDifficultyTransitions(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore)
            throws VerificationException, BlockStoreException {
        try {
            final long newTargetCompact = calculateNewDifficultyTarget(storedPrev, nextBlock, blockStore);
            final long receivedTargetCompact = nextBlock.getDifficultyTarget();

            if(getId().compareTo(ID_DASH_MAINNET) == 0)
            {
                int height = storedPrev.getHeight() + 1;

                if(height <= 68589)
                {
                    long nBitsNext = nextBlock.getDifficultyTarget();

                    long calcDiffBits = newTargetCompact;

                    double n1 = ConvertBitsToDouble(calcDiffBits);
                    double n2 = ConvertBitsToDouble(nBitsNext);

                    if(java.lang.Math.abs(n1-n2) > n1*0.2)
                        throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                                newTargetCompact + " vs " + receivedTargetCompact);
                    else return;  //let it go
                }

            }

            if (newTargetCompact != receivedTargetCompact)
                throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                        newTargetCompact + " vs " + receivedTargetCompact);
        } catch (AbstractDashParams.CheckpointEncounteredException ex) {
            // Just have to take it on trust then
        }
    }
    /**
     * Calculates the difficulty of the nextBlock
     */

    public long calculateNewDifficultyTarget(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore) throws BlockStoreException, VerificationException, CheckpointEncounteredException {

        int DiffMode = 1;
        if (getId().equals(ID_DASH_TESTNET)) {
            if (storedPrev.getHeight()+1 >= 3000) { DiffMode = 4; }
            else DiffMode = 1;
        }
        else {
            if (storedPrev.getHeight()+1 >= 68589) { DiffMode = 4; }
            else if (storedPrev.getHeight()+1 >= 34140) { DiffMode = 3; }
            else if (storedPrev.getHeight()+1 >= 15200) { DiffMode = 2; }
        }

        if (DiffMode == 1) { return calculateNewDifficultyTarget_V1(storedPrev, nextBlock, blockStore); }
        else if (DiffMode == 2) { return calculateNewDifficultyTarget_V2(storedPrev, nextBlock, blockStore); }
        else if (DiffMode == 3) { return DarkGravityWave(storedPrev, nextBlock, blockStore); }
        else if (DiffMode == 4) { return DarkGravityWave3(storedPrev, nextBlock, blockStore); }

        return DarkGravityWave3(storedPrev, nextBlock, blockStore);


    }

    private long DarkGravityWave(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore) throws BlockStoreException, CheckpointEncounteredException{
    /* current difficulty formula, limecoin - DarkGravity, written by Evan Duffield - evan@limecoin.io */
        StoredBlock BlockLastSolved = storedPrev;
        StoredBlock BlockReading = storedPrev;
        Block BlockCreating = nextBlock;
        long nBlockTimeAverage = 0;
        long nBlockTimeAveragePrev = 0;
        long nBlockTimeCount = 0;
        long nBlockTimeSum2 = 0;
        long nBlockTimeCount2 = 0;
        long LastBlockTime = 0;
        long PastBlocksMin = 14;
        long PastBlocksMax = 140;
        long CountBlocks = 0;
        BigInteger PastDifficultyAverage = BigInteger.valueOf(0);
        BigInteger PastDifficultyAveragePrev = BigInteger.valueOf(0);

        //if (BlockLastSolved == NULL || BlockLastSolved->nHeight == 0 || BlockLastSolved->nHeight < PastBlocksMin) { return bnProofOfWorkLimit.GetCompact(); }
        if (BlockLastSolved == null || BlockLastSolved.getHeight() == 0 || (long)BlockLastSolved.getHeight() < PastBlocksMin)
        { return Utils.encodeCompactBits(getMaxTarget()); }

        for (int i = 1; BlockReading != null && BlockReading.getHeight() > 0; i++) {
            if (PastBlocksMax > 0 && i > PastBlocksMax)
            {
                break;
            }
            CountBlocks++;

            if(CountBlocks <= PastBlocksMin) {
                if (CountBlocks == 1) { PastDifficultyAverage = BlockReading.getHeader().getDifficultyTargetAsInteger(); }
                else
                {
                    PastDifficultyAverage = BlockReading.getHeader().getDifficultyTargetAsInteger().subtract(PastDifficultyAveragePrev).divide(BigInteger.valueOf(CountBlocks)).add(PastDifficultyAveragePrev);

                }
                PastDifficultyAveragePrev = PastDifficultyAverage;
            }

            if(LastBlockTime > 0){
                long Diff = (LastBlockTime - BlockReading.getHeader().getTimeSeconds());

                if(nBlockTimeCount <= PastBlocksMin) {
                    nBlockTimeCount++;

                    if (nBlockTimeCount == 1) { nBlockTimeAverage = Diff; }
                    else { nBlockTimeAverage = ((Diff - nBlockTimeAveragePrev) / nBlockTimeCount) + nBlockTimeAveragePrev; }
                    nBlockTimeAveragePrev = nBlockTimeAverage;
                }
                nBlockTimeCount2++;
                nBlockTimeSum2 += Diff;
            }
            LastBlockTime = BlockReading.getHeader().getTimeSeconds();


            try {
                StoredBlock BlockReadingPrev = blockStore.get(BlockReading.getHeader().getPrevBlockHash());
                if (BlockReadingPrev == null)
                {
                    throw new CheckpointEncounteredException();
                }
                BlockReading = BlockReadingPrev;
            }
            catch(BlockStoreException x)
            {
                throw new CheckpointEncounteredException();
            }
        }

        BigInteger bnNew = PastDifficultyAverage;
        if (nBlockTimeCount != 0 && nBlockTimeCount2 != 0) {
            double SmartAverage = ((((double)nBlockTimeAverage)*0.7)+(((double)nBlockTimeSum2 / (double)nBlockTimeCount2)*0.3));
            if(SmartAverage < 1) SmartAverage = 1;
            double Shift = DASH_TARGET_SPACING/SmartAverage;

            double fActualTimespan = (((double)CountBlocks*(double)DASH_TARGET_SPACING)/Shift);
            double fTargetTimespan = ((double)CountBlocks*DASH_TARGET_SPACING);
            if (fActualTimespan < fTargetTimespan/3)
                fActualTimespan = fTargetTimespan/3;
            if (fActualTimespan > fTargetTimespan*3)
                fActualTimespan = fTargetTimespan*3;

            long nActualTimespan = (long)fActualTimespan;
            long nTargetTimespan = (long)fTargetTimespan;

            // Retarget
            bnNew = bnNew.multiply(BigInteger.valueOf(nActualTimespan));
            bnNew = bnNew.divide(BigInteger.valueOf(nTargetTimespan));
        }

        if (bnNew.compareTo(getMaxTarget()) > 0) {
            log.info("Difficulty hit proof of work limit: {}", bnNew.toString(16));
            bnNew = getMaxTarget();
        }
        return Utils.encodeCompactBits(bnNew);

    }
    private long DarkGravityWave3(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore) throws BlockStoreException, CheckpointEncounteredException {
        /* current difficulty formula, darkcoin - DarkGravity v3, written by Evan Duffield - evan@darkcoin.io */
        StoredBlock BlockLastSolved = storedPrev;
        StoredBlock BlockReading = storedPrev;
        Block BlockCreating = nextBlock;
        BlockCreating = BlockCreating;
        long nActualTimespan = 0;
        long LastBlockTime = 0;
        long PastBlocksMin = 24;
        long PastBlocksMax = 24;
        long CountBlocks = 0;
        BigInteger PastDifficultyAverage = BigInteger.ZERO;
        BigInteger PastDifficultyAveragePrev = BigInteger.ZERO;

        if (BlockLastSolved == null || BlockLastSolved.getHeight() == 0 || BlockLastSolved.getHeight() < PastBlocksMin) {
            return Utils.encodeCompactBits(getMaxTarget());
        }

        for (int i = 1; BlockReading != null && BlockReading.getHeight() > 0; i++) {
            if (PastBlocksMax > 0 && i > PastBlocksMax) { break; }
            CountBlocks++;

            if(CountBlocks <= PastBlocksMin) {
                if (CountBlocks == 1) { PastDifficultyAverage = BlockReading.getHeader().getDifficultyTargetAsInteger(); }
                else { PastDifficultyAverage = ((PastDifficultyAveragePrev.multiply(BigInteger.valueOf(CountBlocks)).add(BlockReading.getHeader().getDifficultyTargetAsInteger()).divide(BigInteger.valueOf(CountBlocks + 1)))); }
                PastDifficultyAveragePrev = PastDifficultyAverage;
            }

            if(LastBlockTime > 0){
                long Diff = (LastBlockTime - BlockReading.getHeader().getTimeSeconds());
                nActualTimespan += Diff;
            }
            LastBlockTime = BlockReading.getHeader().getTimeSeconds();

            try {
                StoredBlock BlockReadingPrev = blockStore.get(BlockReading.getHeader().getPrevBlockHash());
                if (BlockReadingPrev == null)
                {
                    //assert(BlockReading); break;
                    throw new CheckpointEncounteredException();
                }
                BlockReading = BlockReadingPrev;
            }
            catch(BlockStoreException x)
            {
                throw new CheckpointEncounteredException();
            }
        }

        BigInteger bnNew= PastDifficultyAverage;

        long nTargetTimespan = CountBlocks*DASH_TARGET_SPACING;//nTargetSpacing;

        if (nActualTimespan < nTargetTimespan/3)
            nActualTimespan = nTargetTimespan/3;
        if (nActualTimespan > nTargetTimespan*3)
            nActualTimespan = nTargetTimespan*3;

        // Retarget
        bnNew = bnNew.multiply(BigInteger.valueOf(nActualTimespan));
        bnNew = bnNew.divide(BigInteger.valueOf(nTargetTimespan));

        if (bnNew.compareTo(getMaxTarget()) > 0) {
            log.info("Difficulty hit proof of work limit: {}", bnNew.toString(16));
            bnNew = getMaxTarget();
        }
        return Utils.encodeCompactBits(bnNew);

    }


    private long calculateNewDifficultyTarget_V1(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore) throws BlockStoreException, VerificationException, CheckpointEncounteredException {
        Block prev = storedPrev.getHeader();

        // Is this supposed to be a difficulty transition point?
        if ((storedPrev.getHeight() + 1) % getInterval() != 0) {

            // TODO: Refactor this hack after 0.5 is released and we stop supporting deserialization compatibility.
            // This should be a method of the NetworkParameters, which should in turn be using singletons and a subclass
            // for each network type. Then each network can define its own difficulty transition rules.
            if (getId().equals(ID_DASH_TESTNET)) {
                return calculateTestnetDifficulty(storedPrev, prev, nextBlock, blockStore);
            }

            // No ... so check the difficulty didn't actually change.
            if (nextBlock.getDifficultyTarget() != prev.getDifficultyTarget())
                throw new VerificationException("Unexpected change in difficulty at height " + storedPrev.getHeight() +
                        ": " + Long.toHexString(nextBlock.getDifficultyTarget()) + " vs " +
                        Long.toHexString(prev.getDifficultyTarget()));
            return nextBlock.getDifficultyTarget();
        }

        // We need to find a block far back in the chain. It's OK that this is expensive because it only occurs every
        // two weeks after the initial block chain download.
        long now = System.currentTimeMillis();
        StoredBlock cursor = blockStore.get(prev.getHash());

        int blockstogoback = getInterval() - 1;
        if(storedPrev.getHeight()+1 != getInterval())
            blockstogoback = getInterval();

        for (int i = 0; i < blockstogoback; i++) {
            if (cursor == null) {
                // This should never happen. If it does, it means we are following an incorrect or busted chain.
                throw new VerificationException(
                        "Difficulty transition point but we did not find a way back to the genesis block.");
            }
            cursor = blockStore.get(cursor.getHeader().getPrevBlockHash());
        }
        long elapsed = System.currentTimeMillis() - now;
        if (elapsed > 50)
            log.info("Difficulty transition traversal took {}msec", elapsed);

        Block blockIntervalAgo = cursor.getHeader();
        int timespan = (int) (prev.getTimeSeconds() - blockIntervalAgo.getTimeSeconds());
        // Limit the adjustment step.
        final int targetTimespan = getTargetTimespan();
        if (timespan < targetTimespan / 4)
            timespan = targetTimespan / 4;
        if (timespan > targetTimespan * 4)
            timespan = targetTimespan * 4;

        BigInteger newTarget = Utils.decodeCompactBits(prev.getDifficultyTarget());
        newTarget = newTarget.multiply(BigInteger.valueOf(timespan));
        newTarget = newTarget.divide(BigInteger.valueOf(targetTimespan));

        if (newTarget.compareTo(getMaxTarget()) > 0) {
            log.info("Difficulty hit proof of work limit: {}", newTarget.toString(16));
            newTarget = getMaxTarget();
        }

        int accuracyBytes = (int) (nextBlock.getDifficultyTarget() >>> 24) - 3;

        // The calculated difficulty is to a higher precision than received, so reduce here.
        BigInteger mask = BigInteger.valueOf(0xFFFFFFL).shiftLeft(accuracyBytes * 8);
        newTarget = newTarget.and(mask);
        long newTargetCompact = Utils.encodeCompactBits(newTarget);

        return newTargetCompact;
    }

    private long calculateNewDifficultyTarget_V2(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore) throws BlockStoreException, VerificationException, CheckpointEncounteredException {
        final long      	BlocksTargetSpacing			= (long)(2.5 * 60); // 10 minutes
        int         		TimeDaySeconds				= 60 * 60 * 24;
        long				PastSecondsMin				= TimeDaySeconds / 40;
        long				PastSecondsMax				= TimeDaySeconds * 7;
        long				PastBlocksMin				= PastSecondsMin / BlocksTargetSpacing;   //? blocks
        long				PastBlocksMax				= PastSecondsMax / BlocksTargetSpacing;   //? blocks

        return KimotoGravityWell(storedPrev, nextBlock, BlocksTargetSpacing, PastBlocksMin, PastBlocksMax, blockStore);
    }

    private long KimotoGravityWell(StoredBlock storedPrev, Block nextBlock, long TargetBlocksSpacingSeconds, long PastBlocksMin, long PastBlocksMax, BlockStore blockStore)  throws BlockStoreException, VerificationException, CheckpointEncounteredException {
	/* current difficulty formula, megacoin - kimoto gravity well */
        StoredBlock         BlockLastSolved             = storedPrev;
        StoredBlock         BlockReading                = storedPrev;
        Block               BlockCreating               = nextBlock;

        BlockCreating				= BlockCreating;
        long				PastBlocksMass				= 0;
        long				PastRateActualSeconds		= 0;
        long				PastRateTargetSeconds		= 0;
        double				PastRateAdjustmentRatio		= 1f;
        BigInteger			PastDifficultyAverage = BigInteger.valueOf(0);
        BigInteger			PastDifficultyAveragePrev = BigInteger.valueOf(0);;
        double				EventHorizonDeviation;
        double				EventHorizonDeviationFast;
        double				EventHorizonDeviationSlow;


        if (BlockLastSolved == null || BlockLastSolved.getHeight() == 0 || (long)BlockLastSolved.getHeight() < PastBlocksMin)
        { return Utils.encodeCompactBits(getMaxTarget()); }

        int i = 0;
        long LatestBlockTime = BlockLastSolved.getHeader().getTimeSeconds();

        for (i = 1; BlockReading != null && BlockReading.getHeight() > 0; i++) {
            if (PastBlocksMax > 0 && i > PastBlocksMax) { break; }
            PastBlocksMass++;

            if (i == 1)	{ PastDifficultyAverage = BlockReading.getHeader().getDifficultyTargetAsInteger(); }
            else		{ PastDifficultyAverage = ((BlockReading.getHeader().getDifficultyTargetAsInteger().subtract(PastDifficultyAveragePrev)).divide(BigInteger.valueOf(i)).add(PastDifficultyAveragePrev)); }
            PastDifficultyAveragePrev = PastDifficultyAverage;


            if (BlockReading.getHeight() > 646120 && LatestBlockTime < BlockReading.getHeader().getTimeSeconds()) {
                //eliminates the ability to go back in time
                LatestBlockTime = BlockReading.getHeader().getTimeSeconds();
            }

            PastRateActualSeconds			= BlockLastSolved.getHeader().getTimeSeconds() - BlockReading.getHeader().getTimeSeconds();
            PastRateTargetSeconds			= TargetBlocksSpacingSeconds * PastBlocksMass;
            PastRateAdjustmentRatio			= 1.0f;
            if (BlockReading.getHeight() > 646120){
                //this should slow down the upward difficulty change
                if (PastRateActualSeconds < 5) { PastRateActualSeconds = 5; }
            }
            else {
                if (PastRateActualSeconds < 0) { PastRateActualSeconds = 0; }
            }
            if (PastRateActualSeconds != 0 && PastRateTargetSeconds != 0) {
                PastRateAdjustmentRatio			= (double)PastRateTargetSeconds / PastRateActualSeconds;
            }
            EventHorizonDeviation			= 1 + (0.7084 * java.lang.Math.pow((Double.valueOf(PastBlocksMass)/Double.valueOf(28.2)), -1.228));
            EventHorizonDeviationFast		= EventHorizonDeviation;
            EventHorizonDeviationSlow		= 1 / EventHorizonDeviation;

            if (PastBlocksMass >= PastBlocksMin) {
                if ((PastRateAdjustmentRatio <= EventHorizonDeviationSlow) || (PastRateAdjustmentRatio >= EventHorizonDeviationFast))
                {
                    break;
                }
            }
            StoredBlock BlockReadingPrev = blockStore.get(BlockReading.getHeader().getPrevBlockHash());
            if (BlockReadingPrev == null)
            {
                //Since we are using the checkpoint system, there may not be enough blocks to do this diff adjust, so skip until we do
                //break;
                throw new CheckpointEncounteredException();
            }
            BlockReading = BlockReadingPrev;
        }

        BigInteger newDifficulty = PastDifficultyAverage;
        if (PastRateActualSeconds != 0 && PastRateTargetSeconds != 0) {
            newDifficulty = newDifficulty.multiply(BigInteger.valueOf(PastRateActualSeconds));
            newDifficulty = newDifficulty.divide(BigInteger.valueOf(PastRateTargetSeconds));
        }

        if (newDifficulty.compareTo(getMaxTarget()) > 0) {
            log.info("Difficulty hit proof of work limit: {}", newDifficulty.toString(16));
            newDifficulty = getMaxTarget();
        }


        return Utils.encodeCompactBits(newDifficulty);

    }

    static double ConvertBitsToDouble(long nBits){
        long nShift = (nBits >> 24) & 0xff;

        double dDiff =
                (double)0x0000ffff / (double)(nBits & 0x00ffffff);

        while (nShift < 29)
        {
            dDiff *= 256.0;
            nShift++;
        }
        while (nShift > 29)
        {
            dDiff /= 256.0;
            nShift--;
        }

        return dDiff;
    }

    private long calculateTestnetDifficulty(StoredBlock storedPrev, Block prev, Block next, BlockStore blockStore) throws VerificationException, BlockStoreException {

        final long timeDelta = next.getTimeSeconds() - prev.getTimeSeconds();

        if (timeDelta >= 0 && timeDelta > DASH_TARGET_SPACING * 2) {
            if (next.getDifficultyTargetAsInteger().equals(getMaxTarget()))
                return next.getDifficultyTarget();
            else throw new VerificationException("Unexpected change in difficulty");
        }
        else {
            // Walk backwards until we find a block that doesn't have the easiest proof of work, then check
            // that difficulty is equal to that one.
            StoredBlock cursor = storedPrev;
            while (!cursor.getHeader().equals(getGenesisBlock()) &&
                    cursor.getHeight() % getInterval() != 0 &&
                    cursor.getHeader().getDifficultyTargetAsInteger().equals(getMaxTarget()))
                cursor = cursor.getPrev(blockStore);
            BigInteger cursorTarget = cursor.getHeader().getDifficultyTargetAsInteger();
            BigInteger newTarget = next.getDifficultyTargetAsInteger();
            if (!cursorTarget.equals(newTarget))
                throw new VerificationException("Testnet block transition that is not allowed: " +
                        Long.toHexString(cursor.getHeader().getDifficultyTarget()) + " vs " +
                        Long.toHexString(next.getDifficultyTarget()));
            return Utils.encodeCompactBits(newTarget);
        }
    }

    /**
     * Whether this network has special rules to enable minimum difficulty blocks
     * after a long interval between two blocks (i.e. testnet).
     */
    public abstract boolean allowMinDifficultyBlocks();

    /**
     * Get the POW hash to use for a block.  Dash uses X11, which is also the same
     * as the block hash.
     */
    @Override
    public Sha256Hash getBlockDifficultyHash(Block block) {
        return block.getHash();
    }

    /**
     * Get the hash to use for a block.  Most coins use SHA256D for block hashes,
     * but DASH uses X11.
     */
    @Override
    public boolean isBlockHashSHA256D() { return false; }

    @Override
    public Sha256Hash calculateBlockHash(byte[] header)
    {
        return Sha256Hash.wrapReversed(X11.digest(header));
    }

    @Override
    public Sha256Hash calculateBlockHash(byte[] payload, int offset, int length)
    {
        return Sha256Hash.wrapReversed(X11.digest(payload, offset, length));
    }

    @Override
    public boolean allowMoreInventoryTypes() { return true; }

    @Override
    public boolean allowMoreMessages() { return true; }

    @Override
    public AltcoinSerializer getSerializer(boolean parseRetain) {
        return new AltcoinSerializer(this, parseRetain);
    }

    @Override
    public int getProtocolVersionNum(final ProtocolVersion version) {
        switch (version) {
            case PONG:
            case BLOOM_FILTER:
                return DASHCOIN_PROTOCOL_VERSION_CURRENT;
            case CURRENT:
                return DASHCOIN_PROTOCOL_VERSION_CURRENT;
            case MINIMUM:
            default:
                return DASHCOIN_PROTOCOL_VERSION_CURRENT;
        }
    }


    private static class CheckpointEncounteredException extends Exception {

        private CheckpointEncounteredException() {
        }
    }
}
