//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2024 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
//                                                                                                                       ~
//  Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in       ~
//  compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0   ~
//  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on  ~
//  an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the   ~
//  specific language governing permissions and limitations under the License.                                           ~
//                                                                                                                       ~
//  Maintainers:                                                                                                         ~
//      Wim Bast, Tom Brus                                                                                               ~
//                                                                                                                       ~
//  Contributors:                                                                                                        ~
//      Ronald Krijgsheld ✝, Arjan Kok, Carel Bast                                                                       ~
// --------------------------------------------------------------------------------------------------------------------- ~
//  In Memory of Ronald Krijgsheld, 1972 - 2023                                                                          ~
//      Ronald was suddenly and unexpectedly taken from us. He was not only our long-term colleague and team member      ~
//      but also our friend. "He will live on in many of the lines of code you see below."                               ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.dclare;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import org.modelingvalue.collections.*;
import org.modelingvalue.collections.util.Concurrent;
import org.modelingvalue.collections.util.Context;
import org.modelingvalue.collections.util.Pair;
import org.modelingvalue.dclare.Construction.Reason;
import org.modelingvalue.dclare.Observer.Constructed;
import org.modelingvalue.dclare.Priority.Concurrents;
import org.modelingvalue.dclare.ex.ConsistencyError;
import org.modelingvalue.dclare.ex.NonDeterministicException;
import org.modelingvalue.dclare.ex.TooManyChangesException;
import org.modelingvalue.dclare.ex.TooManyObservedException;

public class ObserverTransaction extends ActionTransaction {
    private static final Set<Boolean>                            FALSE          = Set.of();
    private static final Set<Boolean>                            TRUE           = Set.of(true);
    public static final Context<Boolean>                         OBSERVE        = Context.of(true);

    @SuppressWarnings("rawtypes")
    private final Concurrent<DefaultMap<Observed, Set<Mutable>>> observeds      = Concurrent.of();
    @SuppressWarnings({"rawtypes", "RedundantSuppression"})
    private final Concurrent<Map<Construction.Reason, Mutable>>  constructions  = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       emptyMandatory = Concurrent.of();
    private final Concurrent<Set<Boolean>>                       changed        = Concurrent.of();
    private final Concurrents<Set<Boolean>>                      defer          = new Concurrents<>(Priority.INNER);

    private Pair<Instant, Throwable>                             throwable;
    private int                                                  nrOfChanges;
    private int                                                  totalNrOfChanges;

    protected ObserverTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    public Observer<?> observer() {
        return (Observer<?>) action();
    }

    @Override
    protected String traceId() {
        return "observer";
    }

    @Override
    protected State merge() {
        observeds.merge();
        emptyMandatory.merge();
        changed.merge();
        defer.merge();
        Map<Reason, Mutable> cons = constructions.merge(); // The merge must be done after merge. 
        if (throwable == null) {
            Set<Boolean> ch = changed.get();
            observer().constructed().set(mutable(), cons);
            changed.set(ch);
        }
        return super.merge();
    }

    private void rollback(boolean atomic) {
        if (atomic) {
            rollback();
            if (throwable == null) {
                observer().constructed().set(mutable(), constructions.get());
            }
        }
    }

    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    @Override
    protected final void run(State pre, UniverseTransaction universeTransaction) {
        Observer<?> observer = observer();
        // check if the universe is still in the same transaction run, if not: reset the counts of my observer
        observer.startTransaction(universeTransaction.stats());
        // check if we should do the work...
        if (!universeTransaction.isKilled() && observer.isActive(mutable())) {
            observeds.init(Observed.OBSERVED_MAP);
            constructions.init(Map.of());
            emptyMandatory.init(FALSE);
            changed.init(FALSE);
            defer.init(FALSE);
            try {
                doRun(pre, universeTransaction);
            } catch (Throwable t) {
                for (Throwable tt = t; tt != null; tt = tt.getCause()) {
                    if (tt instanceof ConsistencyError) {
                        observer().stop();
                        throwable = Pair.of(Instant.now(), tt);
                        return;
                    }
                    if (tt instanceof NullPointerException) {
                        throwable = Pair.of(Instant.now(), tt);
                        return;
                    }
                }
                throwable = Pair.of(Instant.now(), t);
            } finally {
                merge();
                finish(pre, observer);
                changed.clear();
                defer.clear();
                observeds.clear();
                constructions.clear();
                emptyMandatory.clear();
                throwable = null;
                nrOfChanges = 0;
                totalNrOfChanges = 0;
            }
        }
    }

    protected void doRun(State pre, UniverseTransaction universeTransaction) {
        observe(mutable(), observer().constructed());
        super.run(pre, universeTransaction);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void finish(State pre, Observer<?> observer) {
        Mutable mutable = mutable();
        try {
            DefaultMap<Observed, Set<Mutable>> observeds = this.observeds.get();
            checkTooManyObserved(mutable, observeds);
            if (!observer.atomic() && changed.get().equals(TRUE)) {
                checkTooManyChanges(pre, observeds);
                trigger(mutable, (Observer<Mutable>) observer, Priority.one);
            } else {
                Priority def = defer.first(TRUE::equals);
                if (def != null) {
                    rollback(observer.atomic());
                    trigger(mutable, (Observer<Mutable>) observer, def);
                } else if (changed.get().equals(TRUE)) {
                    checkTooManyChanges(pre, observeds);
                    trigger(mutable, (Observer<Mutable>) observer, Priority.one);
                }
            }
            trace(pre, observeds);
            DefaultMap preObserveds = super.set(mutable, observer.observeds(), observeds);
            if (preObserveds.isEmpty() && !observeds.isEmpty()) {
                observer.addInstance();
            } else if (!preObserveds.isEmpty() && observeds.isEmpty()) {
                observer.removeInstance();
            }
        } catch (ConsistencyError ce) {
            observer().stop();
            throwable = Pair.of(Instant.now(), ce);
        }
        if (throwable != null) {
            if (universeTransaction().getConfig().isTraceActions()) {
                runSilent(() -> System.err.println(DclareTrace.getLineStart("DCLARE", this) + mutable + "." + observer() + " (" + throwable.b() + ")"));
            }
            if (throwable.b() instanceof NullPointerException && emptyMandatory.get().equals(TRUE)) {
                throwable = null;
            }
        }
        observer.exception().set(mutable, throwable);
    }

    @SuppressWarnings("rawtypes")
    protected void checkTooManyObserved(Mutable mutable, DefaultMap<Observed, Set<Mutable>> observeds) {
        if (universeTransaction().stats().tooManyObserved(observer(), mutable, observeds)) {
            throw new TooManyObservedException(mutable, observer(), observeds, universeTransaction());
        }
    }

    @SuppressWarnings({"rawtypes"})
    protected void checkTooManyChanges(State pre, DefaultMap<Observed, Set<Mutable>> observeds) {
        UniverseStatistics stats = universeTransaction().stats();
        totalNrOfChanges = stats.bumpAndGetTotalChanges();
        nrOfChanges = observer().countChangesPerInstance();
        if (stats.tooManyChangesPerInstance(nrOfChanges, observer(), mutable()) || stats.maxTotalNrOfChanges() < totalNrOfChanges) {
            stats.setDebugging(true);
        }
    }

    @Override
    protected void bumpTotalChanges() {
        // Do nothing, already done by checkTooManyChanges
    }

    @SuppressWarnings({"rawtypes"})
    protected void trace(State pre, DefaultMap<Observed, Set<Mutable>> observeds) {
        if (observer().isTracing()) {
            trace(pre, observeds, observer().traces());
        }
        UniverseStatistics stats = universeTransaction().stats();
        if (stats.debugging() && changed.get().equals(TRUE)) {
            ObserverTrace trace = trace(pre, observeds, observer().debugs());
            if (nrOfChanges > Math.min(stats.maxNrOfChanges(), 32) && trace.done().size() > 16) {
                throw new TooManyChangesException(current(), trace, nrOfChanges);
            }
            if (totalNrOfChanges > stats.maxTotalNrOfChanges() + Math.min(stats.maxTotalNrOfChanges(), 256) && trace.done().size() > 8) {
                throw new TooManyChangesException(current(), trace, totalNrOfChanges);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ObserverTrace trace(State pre, DefaultMap<Observed, Set<Mutable>> observeds, Setable<Mutable, List<ObserverTrace>> setable) {
        List<ObserverTrace> traces = setable.get(mutable());
        Pair<Mutable, Setable<Mutable, ?>> p = Mutable.D_PARENT_CONTAINING.get(mutable());
        if (p != null && p.b() instanceof Observed) {
            observeds = observeds.put((Observed) p.b(), observeds.get((Observed) p.b()).add(p.a()));
        }
        ObserverTrace trace = new ObserverTrace(mutable(), observer(), traces.last(), nrOfChanges, //
                observeds.filter(e -> !e.getKey().isPlumbing()).flatMap(e -> e.getValue().map(m -> {
                    m = m.dResolve(mutable());
                    return Entry.of(ObservedInstance.of(m, e.getKey()), pre.get(m, e.getKey()));
                })).asMap(e -> e), //
                pre.diff(current(), o -> o instanceof Mutable, s -> s instanceof Observed && !s.isPlumbing()).//
                        flatMap(e1 -> e1.getValue().map(e2 -> Entry.of(ObservedInstance.of((Mutable) e1.getKey(), (Observed) e2.getKey()), e2.getValue().b()))).asMap(e -> e));
        setable.set(mutable(), traces.append(trace));
        return trace;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <O, T> T get(O object, Getable<O, T> getable) {
        if (getable instanceof Observed && Constant.DERIVED.get() != null && ObserverTransaction.OBSERVE.get()) {
            throw new NonDeterministicException(Constant.DERIVED.get().a(), Constant.DERIVED.get().b(), "Reading observed '" + object + "." + getable + //
                    "' while initializing constant '" + Constant.DERIVED.get().a() + "." + Constant.DERIVED.get().b() + "'");
        }
        if (observing(object, getable)) {
            observe(object, (Observed<O, T>) getable);
        }
        T result = super.get(object, getable);
        if (result == null && getable instanceof Observed && getable.mandatory()) {
            emptyMandatory.set(TRUE);
        }
        return result;
    }

    @Override
    public <O, T> T pre(O object, Getable<O, T> getable) {
        if (observing(object, getable)) {
            observe(object, (Observed<O, T>) getable);
        }
        T result = super.pre(object, getable);
        if (result == null && getable instanceof Observed && getable.mandatory()) {
            result = super.get(object, getable);
            if (result == null) {
                emptyMandatory.set(TRUE);
            }
        }
        return result;
    }

    @Override
    public <O, T> T current(O object, Getable<O, T> getable) {
        if (observing(object, getable)) {
            observe(object, (Observed<O, T>) getable);
        }
        T result = super.current(object, getable);
        if (result == null && getable instanceof Observed && getable.mandatory()) {
            emptyMandatory.set(TRUE);
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    protected <T, O> void set(O object, Setable<O, T> setable, T pre, T post) {
        T result = post;
        if (observing(object, setable)) {
            if (setable.mandatory() && !setable.isPlumbing() && !Objects.equals(pre, post) && setable.isEmpty(post) && emptyMandatory.merge().equals(TRUE)) {
                throw new NullPointerException(setable.toString());
            }
            observe(object, (Observed<O, T>) setable);
            if (!setable.isPlumbing() && !Objects.equals(pre, post)) {
                merge();
                result = getSilent(() -> {
                    if (pre instanceof Newable || post instanceof Newable) {
                        return (T) singleMatch((Mutable) object, (Observed) setable, pre, post);
                    } else if (isCollection(pre) && isCollection(post) && (isNewableCollection(pre) || isNewableCollection(post))) {
                        return (T) manyMatch((Mutable) object, (Observed) setable, (ContainingCollection<Object>) pre, (ContainingCollection<Object>) post);
                    } else {
                        return rippleOut(object, (Observed<O, T>) setable, pre, post);
                    }
                });
            }
        }
        super.set(object, setable, pre, result);
    }

    private static <T> boolean isCollection(T val) {
        return val == null || val instanceof ContainingCollection;
    }

    @SuppressWarnings("rawtypes")
    private static <T> boolean isNewableCollection(T val) {
        return val != null && !((ContainingCollection) val).isEmpty() && ((ContainingCollection) val).get(0) instanceof Newable;
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    private <O, T> void observe(O object, Observed<O, T> observed) {
        observeds.change(o -> o.add(observed.entry((Mutable) object, mutable()), Set::addAll));
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void handleMergeConflict(Object object, Setable property, Object pre, Object... branches) {
    }

    @Override
    public void runSilent(Runnable action) {
        if (observeds.isInitialized()) {
            OBSERVE.run(false, action);
        } else {
            super.runSilent(action);
        }
    }

    @Override
    public <T> T getSilent(Supplier<T> action) {
        if (observeds.isInitialized()) {
            return OBSERVE.get(false, action);
        } else {
            return super.getSilent(action);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    @Override
    public <O, T> void changed(O object, Setable<O, T> setable, T preValue, T rawPreValue, T postValue) {
        if (observing(object, setable)) {
            changed.set(TRUE);
        }
        runSilent(() -> super.changed(object, setable, preValue, rawPreValue, postValue));
    }

    private <O, T> boolean observing(O object, Getable<O, T> setable) {
        return object instanceof Mutable && setable instanceof Observed && observeds.isInitialized() && OBSERVE.get();
    }

    @Override
    public <O extends Newable> O directConstruct(Construction.Reason reason, Supplier<O> supplier) {
        return super.construct(reason, supplier);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public <O extends Mutable> O construct(Construction.Reason reason, Supplier<O> supplier) {
        if (Constant.DERIVED.get() != null) {
            return super.construct(reason, supplier);
        } else {
            Constructed constructed = observer().constructed();
            Mutable mutable = mutable();
            Construction cons = Construction.of(mutable, observer(), reason);
            O result = (O) actualize(current(mutable, constructed)).get(reason);
            if (result == null) {
                for (IState state : longHistory()) {
                    Mutable found = actualize(state.get(mutable, constructed)).get(reason);
                    if (found != null && current(found, Mutable.D_PARENT_CONTAINING) == null && //
                            current(found, Newable.D_ALL_DERIVATIONS).get(reason.direction()) == null) {
                        result = (O) found;
                        break;
                    }
                }
                if (result == null) {
                    result = supplier.get();
                    Newable.D_INITIAL_CONSTRUCTION.force(result, cons);
                }
            } else {
                O pre = (O) actualize(preStartState(Priority.three).get(mutable, constructed)).get(reason);
                O post = (O) actualize(startState(Priority.three).get(mutable, constructed)).get(reason);
                if (pre == null && post != null && !post.equals(result)) {
                    setConstructed(reason, cons, result);
                    defer.set(Priority.three, TRUE);
                    traceRippleOut(mutable, observer(), result, post);
                    return post;
                }
            }
            setConstructed(reason, cons, result);
            return result;
        }
    }

    private void setConstructed(Construction.Reason reason, Construction cons, Mutable result) {
        Newable.D_ALL_DERIVATIONS.set(result, QualifiedSet::put, cons);
        constructions.set((m, e) -> m.put(reason, e), result);
    }

    private Map<Reason, Mutable> actualize(Map<Reason, Mutable> map) {
        return map.flatMap(e -> e.getKey().actualize().map(r -> Entry.of(r, e.getValue()))).asMap(Function.identity());
    }

    @SuppressWarnings("unchecked")
    private <O, T, E> T rippleOut(O object, Observed<O, T> observed, T pre, T post) {
        boolean isColl = isNonMapCollection(pre) && isNonMapCollection(post);
        boolean isList = isColl && isList(pre) && isList(post);
        if (isColl) {
            ContainingCollection<E>[] result = new ContainingCollection[]{(ContainingCollection<E>) post};
            Observed<O, ContainingCollection<E>> many = (Observed<O, ContainingCollection<E>>) observed;
            Setable.<T, E> diff(pre, post, added -> {
                Priority delay = added(object, many, added);
                if (delay != null) {
                    defer.set(delay, TRUE);
                    result[0] = result[0].remove(added);
                }
            }, removed -> {
                Priority delay = removed(object, many, removed);
                if (delay != null) {
                    defer.set(delay, TRUE);
                    if (isList) {
                        int i = Math.min(((List<E>) pre).firstIndexOf(removed), result[0].size());
                        result[0] = ((List<E>) result[0]).insert(i, removed);
                    } else {
                        result[0] = result[0].add(removed);
                    }
                }
            });
            if (!Objects.equals(post, result[0])) {
                traceRippleOut(object, observed, post, result[0]);
                return (T) result[0];
            }
        }
        if (!isColl || isList) {
            Priority delay = changed(object, observed, pre, post);
            if (delay != null) {
                defer.set(delay, TRUE);
                traceRippleOut(object, observed, post, pre);
                return pre;
            }
        }
        return post;
    }

    private <T> boolean isNonMapCollection(T t) {
        return t instanceof ContainingCollection && !(t instanceof Map) && !(t instanceof DefaultMap);
    }

    private <T> boolean isList(T t) {
        return t instanceof List;
    }

    private <O, T extends ContainingCollection<E>, E> Priority added(O object, Observed<O, T> observed, E added) {
        return added(object, observed, startState(Priority.INNER), state(), added) ? Priority.INNER : //
                becameDerived(observed, added, startState(Priority.three), current()) ? Priority.three : //
                        (isNew(startState(Priority.four), state()) && added(object, observed, startState(), startState(Priority.four), added)) ? Priority.four : //
                                added(object, observed, preStartState(Priority.OUTER).raw(), startState(Priority.OUTER), added) ? Priority.OUTER : null;
    }

    private <O, T extends ContainingCollection<E>, E> Priority removed(O object, Observed<O, T> observed, E removed) {
        return removed(object, observed, startState(Priority.INNER), state(), removed) ? Priority.INNER : //
                (isNew(startState(Priority.four), state()) && removed(object, observed, startState(), startState(Priority.four), removed)) ? Priority.four : //
                        becameContained(observed, removed, startState(Priority.four), startState(Priority.INNER)) ? Priority.four : //
                                removed(object, observed, preStartState(Priority.OUTER).raw(), startState(Priority.OUTER), removed) ? Priority.OUTER : null;
    }

    private <O, T> Priority changed(O object, Observed<O, T> observed, T pre, T post) {
        return changed(object, observed, startState(Priority.INNER), state(), pre, post) ? Priority.INNER : //
                becameDerived(observed, post, startState(Priority.three), current()) ? Priority.three : //
                        (isNew(startState(Priority.four), state()) && changed(object, observed, startState(), startState(Priority.four), pre, post)) ? Priority.four : //
                                becameContained(observed, pre, startState(Priority.four), startState(Priority.INNER)) ? Priority.four : //
                                        changed(object, observed, preStartState(Priority.OUTER).raw(), startState(Priority.OUTER), pre, post) ? Priority.OUTER : null;
    }

    private boolean isNew(IState preState, IState postState) {
        return !preState.get(mutable(), Mutable.D_OBSERVERS).contains(observer()) && postState.get(mutable(), Mutable.D_OBSERVERS).contains(observer());
    }

    private <O, T extends ContainingCollection<E>, E> boolean added(O object, Observed<O, T> observed, IState preState, IState postState, E added) {
        return isChildChanged(observed, added, preState, postState) || isRemoved(object, observed, added, preState, postState);
    }

    private <O, T extends ContainingCollection<E>, E> boolean removed(O object, Observed<O, T> observed, IState preState, IState postState, E removed) {
        return isChildChanged(observed, removed, preState, postState) || isAdded(object, observed, removed, preState, postState);
    }

    private <O, T> boolean changed(O object, Observed<O, T> observed, IState preState, IState postState, T pre, T post) {
        return isChangedBack(object, observed, pre, post, preState, postState) || //
                isChildChanged(observed, pre, preState, postState) || isChildChanged(observed, post, preState, postState);
    }

    @SuppressWarnings("unused")
    private <O, T, E> boolean becameDerived(Observed<O, T> observed, E element, IState preState, IState postState) {
        return element instanceof Newable && ((Newable) element).dInitialConstruction().isDerived() && //
                preState.get((Newable) element, Newable.D_ALL_DERIVATIONS).isEmpty() && //
                !postState.get((Newable) element, Newable.D_ALL_DERIVATIONS).isEmpty();
    }

    @SuppressWarnings("unused")
    private <O, T, E> boolean becameContained(Observed<O, T> observed, E element, IState preState, IState postState) {
        return element instanceof Newable && //
                preState.get((Newable) element, Mutable.D_PARENT_CONTAINING) == null && //
                postState.get((Newable) element, Mutable.D_PARENT_CONTAINING) != null;
    }

    private <O, T, E> boolean isChildChanged(Observed<O, T> observed, E element, IState preState, IState postState) {
        if (observed.containment() && element instanceof Mutable && preState.get((Mutable) element, Mutable.D_PARENT_CONTAINING) != null) {
            TransactionId txid = postState.get((Mutable) element, Mutable.D_CHANGE_ID);
            return txid != null && txid.number() > preState.transactionId().number();
        }
        return false;
    }

    private <O, T extends ContainingCollection<E>, E> boolean isAdded(O object, Observed<O, T> observed, E removed, IState preState, IState postState) {
        return !observed.collection(preState.get(object, observed)).contains(removed) && //
                (postState == state() || observed.collection(postState.get(object, observed)).contains(removed));
    }

    private <O, T extends ContainingCollection<E>, E> boolean isRemoved(O object, Observed<O, T> observed, E added, IState preState, IState postState) {
        return observed.collection(preState.get(object, observed)).contains(added) && //
                (postState == state() || !observed.collection(postState.get(object, observed)).contains(added));
    }

    @SuppressWarnings("unused")
    private <O, T> boolean isChangedBack(O object, Observed<O, T> observed, T pre, T post, IState preState, IState postState) {
        T before = preState.get(object, observed);
        return Objects.equals(before, post) && //
                (postState == state() || !Objects.equals(before, postState.get(object, observed)));
    }

    private <O> void traceRippleOut(O object, Feature feature, Object post, Object result) {
        if (universeTransaction().getConfig().isTraceRippleOut()) {
            runSilent(() -> System.err.println(DclareTrace.getLineStart("DEFER", this) + mutable() + "." + observer() + //
                    " " + deferPriorityName() + " (" + object + "." + feature + "=" + result + "<-" + post + ")"));
        }
    }

    private String deferPriorityName() {
        //noinspection DataFlowIssue
        return defer.first(TRUE::equals).name().toUpperCase();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object singleMatch(Mutable object, Observed observed, Object before, Object after) {
        if (after instanceof Newable && before instanceof Newable && ((Newable) after).dNewableType().equals(((Newable) before).dNewableType())) {
            MatchInfo preInfo = MatchInfo.of((Newable) before, this, object, observed, false);
            MatchInfo postInfo = MatchInfo.of((Newable) after, this, object, observed, false);
            if (preInfo.mustReplace(postInfo)) {
                replace(postInfo, preInfo);
                after = preInfo.newable();
            } else if (postInfo.mustReplace(preInfo)) {
                replace(preInfo, postInfo);
                before = postInfo.newable();
            } else if (observed.containment()) {
                boolean found = false;
                for (Observed cont : MutableClass.D_CONTAINMENTS.get(object.dClass()).filter(Observed.class).exclude(observed::equals)) {
                    Object val = cont.current(object);
                    if (val instanceof Newable && ((Newable) after).dNewableType().equals(((Newable) val).dNewableType())) {
                        if (after.equals(val)) {
                            found = true;
                            break;
                        }
                        MatchInfo valInfo = MatchInfo.of((Newable) val, this, object, cont, false);
                        if (valInfo.identity() != null && valInfo.mustReplace(postInfo)) {
                            found = true;
                            replace(postInfo, valInfo);
                            after = val;
                            break;
                        }
                    }
                }
                if (!found && universeTransaction().getConfig().isTraceMatching()) {
                    runSilent(() -> System.err.println(DclareTrace.getLineStart("MATCH", this) + mutable() + "." + observer() + " (" + preInfo + "!=" + postInfo + ")"));
                }
            }
        }
        return !Objects.equals(before, after) ? rippleOut(object, observed, before, after) : after;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object manyMatch(Mutable object, Observed observed, ContainingCollection<Object> bef, ContainingCollection<Object> aft) {
        ContainingCollection<Object> befores = bef != null ? bef : aft.clear();
        ContainingCollection<Object> afters = aft != null ? aft : bef.clear();
        QualifiedSet<Newable, MatchInfo> infos = null;
        ContainingCollection<Object> pres = befores;
        ContainingCollection<Object> posts = afters;
        while (!posts.isEmpty()) {
            Object after = posts.get(0);
            posts = posts.remove(after);
            if (after instanceof Newable) {
                MatchInfo postInfo = infos != null ? infos.get((Newable) after) : null;
                for (Object before : pres) {
                    if (before instanceof Newable && ((Newable) after).dNewableType().equals(((Newable) before).dNewableType())) {
                        if (after.equals(before)) {
                            if (pres instanceof List) {
                                pres = pres.remove(before);
                                break;
                            } else {
                                continue;
                            }
                        }
                        if (infos == null) {
                            infos = Collection.concat(befores, afters).distinct().filter(Newable.class).map(n -> MatchInfo.of(n, this, object, observed, true)).asQualifiedSet(MatchInfo::newable);
                            postInfo = infos.get((Newable) after);
                        }
                        MatchInfo preInfo = infos.get((Newable) before);
                        if (preInfo.mustReplace(postInfo)) {
                            pres = pres.remove(before);
                            if (posts.contains(before)) {
                                posts = posts.replaceFirst(before, after);
                                if (pres instanceof List) {
                                    afters = afters.replaceFirst(before, after);
                                    afters = afters.replaceFirst(after, before);
                                }
                                replace(postInfo, preInfo);
                                replace(preInfo, postInfo);
                                postInfo.setAllDerivations(preInfo);
                            } else {
                                afters = afters.replaceFirst(after, before);
                                replace(postInfo, preInfo);
                            }
                            break;
                        } else if (postInfo.mustReplace(preInfo) && !posts.contains(before)) {
                            pres = pres.remove(before);
                            befores = befores.replaceFirst(before, after);
                            replace(preInfo, postInfo);
                            break;
                        } else if (universeTransaction().getConfig().isTraceMatching()) {
                            MatchInfo finalPostInfo = postInfo;
                            runSilent(() -> System.err.println(DclareTrace.getLineStart("MATCH", this) + mutable() + "." + observer() + " (" + preInfo + "!=" + finalPostInfo + ")"));
                        }
                    }
                }
            }
        }
        if (bef instanceof List && bef.size() > 1 && aft instanceof List && aft.size() > 1 && !afters.equals(aft)) {
            afters = afters.sortedBy(e -> {
                int i = ((List) bef).firstIndexOf(e);
                return i < 0 ? ((List) aft).firstIndexOf(e) + bef.size() : i;
            }).asList();
        }
        return !befores.equals(afters) ? rippleOut(object, observed, befores, afters) : afters;
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantSuppression"})
    private void replace(MatchInfo replaced, MatchInfo replacing) {
        Mutable mutable = mutable();
        Observer<?> observer = observer();
        if (universeTransaction().getConfig().isTraceMatching()) {
            runSilent(() -> System.err.println(DclareTrace.getLineStart("MATCH", this) + mutable + "." + observer + " (" + replacing + "==" + replaced + ")"));
        }
        if (Mutable.D_INITIAL_CONSTRUCTION.get(replacing.newable()).isDirect()) {
            super.set(replaced.newable(), Newable.D_REPLACING, Newable.D_REPLACING.getDefault(replaced.newable()), replacing.newable());
        }
        for (Construction cons : replaced.allDerivations()) {
            super.set(replacing.newable(), Newable.D_ALL_DERIVATIONS, QualifiedSet::put, cons);
            if (cons.object().equals(mutable) && cons.observer().equals(observer)) {
                constructions.set((map, c) -> map.put(c.reason(), replacing.newable()), cons);
            }
        }
    }

    @Override
    protected String getCurrentTypeForTrace() {
        return "OB";
    }

}
