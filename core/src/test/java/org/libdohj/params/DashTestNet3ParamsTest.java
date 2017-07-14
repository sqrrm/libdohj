/*
 * Copyright 2015 J. Ross Nicoll
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

import org.bitcoinj.core.Block;
import org.bitcoinj.core.Context;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Ross Nicoll
 */
public class DashTestNet3ParamsTest {
    private static final DashTestNet3Params params = DashTestNet3Params.get();

    @Before
    public void setUp() throws Exception {
        Context context = new Context(params);
    }

    @Test
    public void testRegTestGenesisBlock() {
        Block genesis = params.getGenesisBlock();
        assertEquals("00000bafbc94add76cb75e2ec92894837288a481e5c005f6563d91623bf8bc2c", genesis.getHashAsString());
    }
}
