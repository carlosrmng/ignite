/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache.transactions;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteEvents;
import org.apache.ignite.IgniteTransactions;
import org.apache.ignite.events.Event;
import org.apache.ignite.events.TransactionStartedEvent;
import org.apache.ignite.lang.IgnitePredicate;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.apache.ignite.transactions.Transaction;
import org.apache.ignite.transactions.TransactionRollbackException;

import static org.apache.ignite.events.EventType.EVTS_TX;
import static org.apache.ignite.events.EventType.EVT_TX_STARTED;

/**
 * Tests transaction labels.
 */
public class TxLabelTest extends GridCommonAbstractTest {
    /**
     * Tests transaction labels.
     */
    public void testLabel() throws Exception {
        Ignite ignite = startGrid(0);

        IgniteCache cache = ignite.getOrCreateCache(DEFAULT_CACHE_NAME);

        testLabel0(grid(0), "lbl0", cache);
        testLabel0(grid(0), "lbl1", cache);

        try {
            testLabel0(grid(0), null, cache);

            fail();
        }
        catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("label should not be empty."));
        }
    }

    /**
     * @param ignite Ignite.
     * @param lbl Label.
     */
    private void testLabel0(Ignite ignite, String lbl, IgniteCache cache) {
        try (Transaction tx = ignite.transactions().withLabel(lbl).txStart()) {
            assertEquals(lbl, tx.label());

            cache.put(0, 0);

            tx.commit();
        }
    }

    /**
     *
     */
    public void testLabelFilledLocalGuarantee() throws Exception {
        Ignite ignite = startGrid(0);

        final IgniteEvents evts = ignite.events();

        evts.enableLocal(EVTS_TX);

        evts.localListen((IgnitePredicate<Event>)e -> {
            assert e instanceof TransactionStartedEvent;

            TransactionStartedEvent evt = (TransactionStartedEvent)e;

            IgniteTransactions tx = evt.tx();

            if (tx.label() == null)
                tx.tx().rollback();

            return true;
        }, EVT_TX_STARTED);

        try (Transaction tx = ignite.transactions().withLabel("test").txStart()) {
            tx.commit();
        }

        try (Transaction tx = ignite.transactions().txStart()) {
            tx.commit();

            fail("Should fail prior this line.");
        }
        catch (TransactionRollbackException ignored) {
            // No-op.
        }
    }

    /**
     *
     */
    public void testLabelFilledRemoteGuarantee() throws Exception {
        Ignite ignite = startGrid(0);
        Ignite remote = startGrid(1);

        final IgniteEvents evts = ignite.events();

        evts.enableLocal(EVTS_TX);

        evts.remoteListen(null,
            (IgnitePredicate<Event>)e -> {
                assert e instanceof TransactionStartedEvent;

                TransactionStartedEvent evt = (TransactionStartedEvent)e;

                IgniteTransactions tx = evt.tx();

                if (tx.label() == null)
                    tx.tx().rollback();

                return true;
            },
            EVT_TX_STARTED);

        try (Transaction tx = ignite.transactions().withLabel("test").txStart()) {
            tx.commit();
        }

        try (Transaction tx = remote.transactions().withLabel("test").txStart()) {
            tx.commit();
        }

        try (Transaction tx = ignite.transactions().txStart()) {
            tx.commit();

            fail("Should fail prior this line.");
        }
        catch (TransactionRollbackException ignored) {
            // No-op.
        }

        try (Transaction tx = remote.transactions().txStart()) {
            tx.commit();

            fail("Should fail prior this line.");
        }
        catch (TransactionRollbackException ignored) {
            // No-op.
        }
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        stopAllGrids();
    }
}
