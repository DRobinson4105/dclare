//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2021 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

package org.modelingvalue.dclare;

import java.util.function.Consumer;

import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Set;

public class NonCheckingObserver<O extends Mutable> extends Observer<O> {

    public static <M extends Mutable> NonCheckingObserver<M> of(Object id, Consumer<M> action) {
        return new NonCheckingObserver<>(id, action, Priority.forward);
    }

    public static <M extends Mutable> NonCheckingObserver<M> of(Object id, Consumer<M> action, Priority initPriority) {
        return new NonCheckingObserver<>(id, action, initPriority);
    }

    protected NonCheckingObserver(Object id, Consumer<O> action, Priority initPriority) {
        super(id, action, initPriority);
    }

    @Override
    public NonCheckingObserver.NonCheckingTransaction openTransaction(Direction direction, MutableTransaction parent) {
        return parent.universeTransaction().nonCheckingTransactions.get().open(direction, this, parent);
    }

    @Override
    public void closeTransaction(Transaction tx) {
        tx.universeTransaction().nonCheckingTransactions.get().close((NonCheckingObserver.NonCheckingTransaction) tx);
    }

    @Override
    public NonCheckingObserver.NonCheckingTransaction newTransaction(UniverseTransaction universeTransaction) {
        return new NonCheckingTransaction(universeTransaction);
    }

    public static class NonCheckingTransaction extends ObserverTransaction {

        protected NonCheckingTransaction(UniverseTransaction root) {
            super(root);
        }

        @SuppressWarnings("rawtypes")
        @Override
        protected void checkTooManyObserved(DefaultMap<Observed, Set<Mutable>> observeds) {
        }

        @SuppressWarnings("rawtypes")
        @Override
        protected void checkTooManyChanges(State pre, DefaultMap<Observed, Set<Mutable>> observeds) {
        }

    }

}
