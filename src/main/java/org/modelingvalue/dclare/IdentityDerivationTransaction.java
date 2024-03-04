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

import java.util.Objects;
import java.util.function.Supplier;

public class IdentityDerivationTransaction extends AbstractDerivationTransaction {

    protected IdentityDerivationTransaction(UniverseTransaction universeTransaction) {
        super(universeTransaction);
    }

    private int     depth;
    private Mutable contextMutable;

    @SuppressWarnings("rawtypes")
    public <R> R derive(Supplier<R> action, State state, int depth, Mutable contextMutable, ConstantState constantState) {
        this.contextMutable = contextMutable;
        this.depth = depth;
        try {
            return derive(action, state, constantState, this);
        } finally {
            this.depth = 0;
            this.contextMutable = null;
        }
    }

    @Override
    protected <O, T> boolean doDerive(O object, Getable<O, T> getable, T nonDerived) {
        return super.doDerive(object, getable, nonDerived) && !isChanged(object, getable);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <O, T> T getNonDerived(O object, Getable<O, T> getable) {
        if (isOld(object)) {
            return universeTransaction().startState(Priority.OUTER).get(object, getable);
        } else {
            return super.getNonDerived(object, getable);
        }
    }

    private <O, T> boolean isChanged(O object, Getable<O, T> getable) {
        T pre = universeTransaction().preStartState(Priority.OUTER).getRaw(object, getable);
        T post = universeTransaction().startState(Priority.OUTER).get(object, getable);
        return !Objects.equals(pre, post);
    }

    private <O> boolean isOld(O object) {
        return object instanceof Mutable && universeTransaction().startState(Priority.OUTER).get((Mutable) object, Mutable.D_PARENT_CONTAINING) != null;
    }

    public Mutable getContextMutable() {
        return contextMutable;
    }

    @Override
    public int depth() {
        return depth + super.depth();
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected <O> boolean isTraceDerivation(O object, Setable setable) {
        return super.isTraceDerivation(object, setable) && (universeTransaction().getConfig().isTraceMatching() || memoization(object) != memoization());
    }

    @Override
    protected String getCurrentTypeForTrace() {
        return "ID";
    }
}
