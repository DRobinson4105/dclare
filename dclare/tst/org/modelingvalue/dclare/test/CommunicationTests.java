package org.modelingvalue.dclare.test;

import static org.junit.Assert.*;
import static org.modelingvalue.dclare.test.Shared.*;

import org.junit.Test;
import org.modelingvalue.dclare.Observed;
import org.modelingvalue.dclare.Observer;
import org.modelingvalue.dclare.Setable;
import org.modelingvalue.dclare.State;
import org.modelingvalue.dclare.UniverseTransaction;

public class CommunicationTests {
    @Test
    public void source2target() {
        Uni a = new Uni("A");
        Uni b = new Uni("B");

        UniverseTransaction txA = UniverseTransaction.of(a.universe, THE_POOL);
        UniverseTransaction txB = UniverseTransaction.of(b.universe, THE_POOL);

        // TODO: connect a <=> b

        txB.put("stepB1", () -> b.child.set(b.universe, b.object));

        txA.put("stepA1", () -> a.child.set(a.universe, a.object));
        txA.put("stepA2", () -> a.source.set(a.object, 3));

        txA.stop();
        txB.stop();

        State resultA = txA.waitForEnd();
        State resultB = txB.waitForEnd();

        printState(txA, resultA);
        printState(txB, resultB);

        assertEquals(3, (int) resultA.get(a.object, a.source));
        assertEquals(3, (int) resultA.get(a.object, a.target));

        assertEquals(1, (int) resultB.get(b.object, b.source));
        assertEquals(1, (int) resultB.get(b.object, b.target));
        // TODO: assertEquals(3, (int) resultB.get(b.object, b.source));
        // TODO: assertEquals(3, (int) resultB.get(b.object, b.target));
    }

    private static class Uni {
        final Observed<DUniverse, DObject> child  = Observed.of("child", null, true);
        final Observed<DObject, Integer>   source = Observed.of("source", 1);
        final Setable<DObject, Integer>    target = Setable.of("target", 2);
        final DClass                       dClass = DClass.of("Object", Observer.of("observer", o -> target.set(o, source.get(o))));
        final DObject                      object = DObject.of("object", dClass);
        final DUniverse                    universe;

        Uni(String name) {
            universe = DUniverse.of("universe-" + name, DClass.of("Universe", child));
        }
    }
}
