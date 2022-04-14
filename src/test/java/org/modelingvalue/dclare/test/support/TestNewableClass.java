//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2022 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.modelingvalue.collections.util.SerializableConsumer;
import org.modelingvalue.collections.util.SerializableFunction;
import org.modelingvalue.collections.util.Triple;
import org.modelingvalue.dclare.Direction;
import org.modelingvalue.dclare.Setable;

@SuppressWarnings({"unused", "rawtypes"})
public class TestNewableClass extends TestMutableClass {

    @SafeVarargs
    public static TestNewableClass of(Object id, Direction direction, Function<TestNewable, Object> identity, Setable<? extends TestMutable, ?>... setables) {
        return new TestNewableClass(id, direction, identity, new AtomicInteger(0), setables);
    }

    private final Direction                     direction;
    private final Function<TestNewable, Object> identity;
    private final AtomicInteger                 objectCounter;

    protected TestNewableClass(Object id, Direction direction, Function<TestNewable, Object> identity, AtomicInteger objectCounter, Setable... setables) {
        super(id, setables);
        this.identity = identity;
        this.direction = direction;
        this.objectCounter = objectCounter;
    }

    public Function<TestNewable, Object> identity() {
        return identity;
    }

    public int newObjectInt() {
        return objectCounter.getAndIncrement();
    }

    public Direction direction() {
        return direction;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final TestNewableClass observe(SerializableConsumer<TestMutable> action) {
        return (TestNewableClass) super.observe(action);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final TestNewableClass observe(Direction direction, SerializableConsumer<TestMutable> action) {
        return (TestNewableClass) super.observe(direction, action);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <V> TestNewableClass observe(Setable<TestMutable, V> setable, SerializableFunction<TestMutable, V> value) {
        return (TestNewableClass) super.observe(setable, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <V> TestNewableClass observe(Direction direction, Setable<TestMutable, V> setable, SerializableFunction<TestMutable, V> value) {
        return (TestNewableClass) super.observe(direction, setable, value);
    }

    @Override
    public String toString() {
        return super.toString().replace("Triple", "").replace("Pair", "");
    }

    private static final class AnonymousKey extends Triple<TestNewableClass, TestMutable, String> {

        private static final long                     serialVersionUID = -7004150121696331537L;

        private final UnaryOperator<TestNewableClass> init;

        private AnonymousKey(TestNewableClass original, TestMutable ctx, String id, UnaryOperator<TestNewableClass> init) {
            super(original, ctx, id);
            this.init = init;
        }

    }

}
