package org.modelingvalue.dclare.test.support;

import org.modelingvalue.dclare.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ImperativeTestUniverse extends TestMutable implements Universe {

    public static ImperativeTestUniverse of(Object id, TestMutableClass clazz, Consumer<ImperativeTestUniverse> initConsumer, Consumer<ImperativeTestUniverse> exitConsumer) {
        return new ImperativeTestUniverse(id, clazz, initConsumer, exitConsumer);
    }

    private static final Setable<ImperativeTestUniverse, Long> DUMMY     = Setable.of("$DUMMY", 0l);

    private final AtomicInteger counter   = new AtomicInteger(0);

    public UniverseTransaction universeTransaction;

    private final Consumer<ImperativeTestUniverse> initConsumer;
    private final Consumer<ImperativeTestUniverse> exitConsumer;

    private ImperativeTestUniverse(Object id, TestMutableClass clazz, Consumer<ImperativeTestUniverse> initConsumer, Consumer<ImperativeTestUniverse> exitConsumer) {
        super(id, clazz);
        this.initConsumer = initConsumer;
        this.exitConsumer = exitConsumer;
    }

    @Override
    public void init() {
        Universe.super.init();
        universeTransaction = LeafTransaction.getCurrent().universeTransaction();
        initConsumer.accept(this);
    }

    @Override
    public void exit() {
        exitConsumer.accept(this);
        Universe.super.exit();
    }

    public int uniqueInt() {
        return counter.getAndIncrement();
    }


    public State waitForEnd(UniverseTransaction universeTransaction) throws Throwable {
        try {
            return universeTransaction.waitForEnd();
        } catch (Error e) {
            throw e.getCause();
        }
    }

    @Override
    public boolean dIsOrphan(State state) {
        return Universe.super.dIsOrphan(state);
    }

}
