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

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Entry;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.Internable;

public interface MutableClass extends Internable {

    @SuppressWarnings({"rawtypes"})
    Constant<MutableClass, Set<Setable>>                       D_CONTAINMENTS      = Constant.of("D_CONTAINMENTS",                                                                         //
            c -> c.dSetables().filter(Setable::containment).map(s -> (Setable) s).asSet());

    @SuppressWarnings({"rawtypes"})
    Constant<MutableClass, Set<Observed>>                      D_OBSERVEDS         = Constant.of("D_OBSERVEDS",                                                                            //
            c -> c.dSetables().filter(s -> s instanceof Observed).map(s -> (Observed) s).asSet());

    @SuppressWarnings({"rawtypes"})
    Constant<MutableClass, Set<Constant>>                      D_PUSHING_CONSTANTS = Constant.of("D_PUSHING_CONSTANTS",                                                                    //
            c -> c.dSetables().filter(s -> s instanceof Constant && s.isHandlingChange() && ((Constant) s).deriver() != null).map(s -> (Constant) s).asSet());

    @SuppressWarnings({"rawtypes", "unchecked"})
    Constant<MutableClass, DefaultMap<Setable, Set<Observer>>> D_DERIVERS          = Constant.of("D_DERIVERS",                                                                             //
            c -> {
                Set<Setable> setables = (Set) c.dObservers().flatMap(Observer::targets).asSet();
                return setables.asDefaultMap(k -> Set.of(), s -> Entry.<Setable, Set<Observer>> of(s, c.dObservers().map(o -> (Observer) o).filter(o -> o.targets().contains(s)).asSet()));
            });

    @SuppressWarnings({"rawtypes", "unchecked"})
    Constant<MutableClass, Set<Observer>>                      D_NON_DERIVERS      = Constant.of("D_NON_DERIVERS",                                                                         //
            c -> c.dObservers().filter(o -> o.targets().isEmpty()).map(s -> (Observer) s).asSet());

    Collection<? extends Observer<?>> dObservers();

    Collection<? extends Setable<? extends Mutable, ?>> dSetables();

    @SuppressWarnings("rawtypes")
    default Collection<Observer> dDerivers(Setable setable) {
        return D_DERIVERS.get(this).get(setable);
    }

}
