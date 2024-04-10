package org.modelingvalue.dclare.test;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.modelingvalue.dclare.*;
import org.modelingvalue.dclare.test.support.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.modelingvalue.dclare.CoreSetableModifier.containment;
import static org.modelingvalue.dclare.test.support.Shared.THE_POOL;

public class ImperativeTransactionTests {
    @Test
    public void noImperativeTransactions() {
        TestScheduler scheduler = TestScheduler.of();
        Observed<ImperativeTestUniverse, TestMutable> child = Observed.of("child", null, containment);
        Semaphore finalSemaphore = new Semaphore(0);
        ImperativeTestUniverse itu = ImperativeTestUniverse.of(
                "universe",
                TestMutableClass.of("Universe", child),
                (u) -> {
                    scheduler.start();
                },
                (u) -> {
                    scheduler.stop();
                }
        );
        UniverseTransaction universeTransaction = new UniverseTransaction(itu, THE_POOL, new DclareConfig().withDevMode(true));
        universeTransaction.stop();
        State result = assertDoesNotThrow(() -> itu.waitForEnd(universeTransaction));
        assertEquals(0, finalSemaphore.availablePermits());
//        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertDoesNotThrow(() -> finalSemaphore.acquire(0)));
    }

    @RepeatedTest(100)
    public void oneImperativeTransaction() {
        TestScheduler scheduler = TestScheduler.of();
        Observed<ImperativeTestUniverse, TestMutable> child = Observed.of("child", null, containment);
        Semaphore finalSemaphore = new Semaphore(0);
        ImperativeTestUniverse itu = ImperativeTestUniverse.of(
                "universe",
                TestMutableClass.of("Universe", child),
                (u) -> {
                    scheduler.start();
                    u.universeTransaction.addImperative("S1", (pre, post, last, setted) -> {
                        finalSemaphore.release();
                    }, scheduler, false);
                },
                (u) -> {
                    scheduler.stop();
                }
        );
        UniverseTransaction universeTransaction = new UniverseTransaction(itu, THE_POOL, new DclareConfig().withDevMode(true));
        universeTransaction.stop();
        State result = assertDoesNotThrow(() -> itu.waitForEnd(universeTransaction));
        assertNotEquals(0, finalSemaphore.availablePermits());
//        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertDoesNotThrow(() -> finalSemaphore.acquire()));
    }

    @RepeatedTest(10000)
    public void twoImperativeTransactionsNotOrdered() {
        TestScheduler scheduler = TestScheduler.of();
        Observed<ImperativeTestUniverse, TestMutable> child = Observed.of("child", null, containment);
        Semaphore semaphore1 = new Semaphore(0);
        Semaphore semaphore2 = new Semaphore(0);
        AtomicBoolean firstRan = new AtomicBoolean(false);
        AtomicBoolean secondRan = new AtomicBoolean(false);
        Setable<Mutable, Boolean> setable = Setable.of("test", false);
        ImperativeTestUniverse itu = ImperativeTestUniverse.of(
                "universe",
                TestMutableClass.of("Universe", child),
                (u) -> {
                    scheduler.start();
                    u.universeTransaction.addImperative("S1", (pre, post, last, setted) -> {
                        if (post.get(u, setable)) {
                            firstRan.set(true);
//                            semaphore1.release();
                        } else {
                            System.out.println("s1 ran when not true");
                        }
                    }, scheduler, true);

                    u.universeTransaction.addImperative("S2", (pre, post, last, setted) -> {
                        if (post.get(u, setable)) {
                            secondRan.set(true);
//                            semaphore2.release();
                        } else {
                            System.out.println("s2 ran when not true");
                        }
                    }, scheduler, true);
                },
                (u) -> {
                    scheduler.stop();
                }
        );
        UniverseTransaction universeTransaction = new UniverseTransaction(itu, THE_POOL, new DclareConfig().withDevMode(true));
        Semaphore semaphore = new Semaphore(0);
        universeTransaction.put(new Object() + "", () -> {
            setable.set(itu, true);
            semaphore.release();
        });

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {}
        universeTransaction.stop();
//        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
//            try {
//                semaphore1.acquire();
//                semaphore2.acquire();
//            } catch (InterruptedException e) {}
//        });
        State result = assertDoesNotThrow(() -> itu.waitForEnd(universeTransaction));
        System.out.println(firstRan.get() + " " + secondRan.get());

        assertTrue(firstRan.get() && secondRan.get());
    }
}
