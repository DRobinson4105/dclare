//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2019 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
//                                                                                                                     ~
// Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in      ~
// compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0  ~
// Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on ~
// an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the  ~
// specific language governing permissions and limitations under the License.                                          ~
//                                                                                                                     ~
// Maintainers:                                                                                                        ~
//     Wim Bast, Tom Brus, Ronald Krijgsheld                                                                           ~
// Contributors:                                                                                                       ~
//     Arjan Kok, Carel Bast                                                                                           ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.dclare.test.support;

import static java.lang.management.ManagementFactory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.modelingvalue.collections.util.TraceTimer.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import org.modelingvalue.collections.util.ContextThread.*;
import org.modelingvalue.collections.util.*;
import org.modelingvalue.dclare.sync.*;

public class CommunicationHelper {
    private static final boolean                WE_ARE_DEBUGGED                             = getRuntimeMXBean().getInputArguments().toString().indexOf("-agentlib:jdwp") > 0;
    private static final int                    IDLE_DETECT_TIMEOUT                         = WE_ARE_DEBUGGED ? 24 * 60 * 60 * 1_000 : 5 * 1_000;
    private static final int                    IDLE_SAMPLES_FOR_DEFINITIVE_IDLE_CONCLUSION = 10;
    private static final int                    SIMULATED_NETWORK_DELAY                     = 100;
    //
    private static final List<ModelMaker>       ALL_MODEL_MAKERS                            = new ArrayList<>();
    private static final List<TestDeltaAdaptor> ALL_DELTA_ADAPTORS                          = new ArrayList<>();
    private static final List<WorkDaemon<?>>    ALL_DAEMONS                                 = new ArrayList<>();
    private static final List<ContextPool>      ALL_POOLS                                   = new ArrayList<>();

    public static void add(ModelMaker r) {
        ALL_MODEL_MAKERS.add(r);
    }

    public static void add(TestDeltaAdaptor a) {
        ALL_DELTA_ADAPTORS.add(a);
    }

    public static void add(WorkDaemon<?> daemon) {
        ALL_DAEMONS.add(daemon);
    }

    public static void add(ContextPool pool) {
        ALL_POOLS.add(pool);
    }

    public static TestDeltaAdaptor hookupDeltaAdaptor(ModelMaker mm) {
        TestDeltaAdaptor adaptor = new TestDeltaAdaptor(mm.getName(), mm.getTx());
        add(adaptor);
        return adaptor;
    }

    public static void hookupTransportDaemon(String name, TestDeltaAdaptor producer, TestDeltaAdaptor consumer) {
        WorkDaemon<String> transportDaemon = new WorkDaemon<>("transport-" + name) {
            @Override
            protected String waitForWork() {
                return producer.get();
            }

            @Override
            protected void execute(String w) throws InterruptedException {
                Thread.sleep(SIMULATED_NETWORK_DELAY);
                consumer.accept(w);
            }
        };
        add(transportDaemon);
        transportDaemon.start();
    }

    public static void tearDownAll() {
        ALL_MODEL_MAKERS.forEach(mm -> {
            if (mm.getTx() != null) {
                mm.getTx().stop();
            }
        });
        ALL_MODEL_MAKERS.forEach(mm -> {
            if (mm.getTx() != null) {
                mm.getTx().waitForEnd();
            }
        });
        ALL_DAEMONS.forEach(WorkDaemon::forceStop);
        ALL_DAEMONS.forEach(WorkDaemon::join_);
        busyWaitAllForIdle();
        ALL_POOLS.forEach(pool -> {
            if (!ModelMaker.BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER) {
                pool.shutdownNow();
                assertDoesNotThrow(() -> assertTrue(pool.awaitTermination(1, TimeUnit.SECONDS)));
            }
            assertEquals(0, pool.getNumInOverflow(), "the contextFactory had to create overflow Threads as a fall back");
        });

        ALL_MODEL_MAKERS.clear();
        ALL_DELTA_ADAPTORS.clear();
        ALL_DAEMONS.clear();
        if (!ModelMaker.BUGGERS_THERE_IS_A_BUG_IN_STATE_COMPARER) {
            ALL_POOLS.clear();
        }

        rethrowAllDaemonProblems();
    }

    private static void rethrowAllDaemonProblems() {
        assertAll(ALL_DAEMONS
                .stream()
                .map(WorkDaemon::getThrowable)
                .filter(Objects::nonNull)
                .map(t -> () -> {
                    throw t;
                }));
    }

    public static void busyWaitAllForIdle() {
        final long t0 = System.currentTimeMillis();
        boolean    busy;
        do {
            // probe isBusy() 10 times and 1 ms apart until we find a busy sample or conclude that we are idle
            busy = false;
            for (int i = 0; i < IDLE_SAMPLES_FOR_DEFINITIVE_IDLE_CONCLUSION && !busy; i++) {
                nap();
                rethrowAllDaemonProblems();
                busy = ALL_DAEMONS.stream().anyMatch(WorkDaemon::isBusy) || ALL_DELTA_ADAPTORS.stream().anyMatch(DeltaAdaptor::isBusy);
            }
        } while (System.currentTimeMillis() < t0 + IDLE_DETECT_TIMEOUT && busy);
        //System.err.printf("busyWait ended after %d ms\n", System.currentTimeMillis() - t0);
        if (busy) {
            // darn,
            System.err.println("this test did not get idle in time (" + ALL_MODEL_MAKERS.size() + " model-makers, " + ALL_DELTA_ADAPTORS.size() + " delta-adaptors, " + ALL_DAEMONS.size() + " daemons, " + ALL_POOLS.size() + " pools):");
            for (TestDeltaAdaptor ad : ALL_DELTA_ADAPTORS) {
                StringBuilder adWhy  = new StringBuilder();
                boolean       adBusy = ad.isBusy(adWhy);
                System.err.printf(" - modelmaker %-16s: %s (%s)\n", ad.getName(), adBusy ? "BUSY" : "idle", adWhy);
            }
            for (WorkDaemon<?> wd : ALL_DAEMONS) {
                System.err.printf(" - workDaemon %-16s: %s\n", wd.getName(), wd.isBusy() ? "BUSY" : "idle");
            }
            TraceTimer.dumpStacks();
            ModelMaker.assertNoUncaughtThrowables();
            fail("the test did not get idle in time");
        }
        ModelMaker.assertNoUncaughtThrowables();
    }

    private static void nap() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            fail(e);
        }
    }

    public static void interpreter(InputStream in, AtomicBoolean stop, Map<Character, BiConsumer<Character, String>> actions) throws IOException {
        BiConsumer<Character, String> defaultAction  = actions.get('*');
        BufferedReader                bufferedReader = new BufferedReader(new InputStreamReader(in));
        String                        line;
        while (!stop.get() && (line = bufferedReader.readLine()) != null) {
            busyWaitAllForIdle();
            traceLog("got line: " + line);
            if (1 <= line.length()) {
                char cmd = line.charAt(0);
                actions.getOrDefault(cmd, defaultAction).accept(cmd, line.substring(1));
            }
        }
        busyWaitAllForIdle();
    }
}
