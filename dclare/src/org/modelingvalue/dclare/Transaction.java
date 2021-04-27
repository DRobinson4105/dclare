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

import java.util.ConcurrentModificationException;

import org.modelingvalue.collections.util.StringUtil;

public abstract class Transaction {

    private final UniverseTransaction universeTransaction;
    private Direction                 direction;
    private MutableTransaction        parent;
    private TransactionClass          cls;

    protected Transaction(UniverseTransaction universeTransaction) {
        this.universeTransaction = universeTransaction;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + (cls != null ? (parent != null ? StringUtil.toString(parent.mutable()) + "." : "") + StringUtil.toString(cls) : super.toString());
    }

    public MutableTransaction parent() {
        return parent;
    }

    protected abstract State run(State state);

    public final TransactionClass cls() {
        if (cls == null) {
            throw new ConcurrentModificationException();
        }
        return cls;
    }

    public boolean isOpen() {
        return cls != null;
    }

    public Direction direction() {
        return direction;
    }

    public UniverseTransaction universeTransaction() {
        return universeTransaction;
    }

    public void start(Direction direction, TransactionClass cls, MutableTransaction parent) {
        if (this.cls != null) {
            throw new ConcurrentModificationException();
        }
        this.cls = cls;
        this.parent = parent;
        this.direction = direction;
    }

    public void stop() {
        if (cls == null) {
            throw new ConcurrentModificationException();
        }
        cls = null;
        parent = null;
        direction = null;
    }

    public int depth() {
        int i = 0;
        for (Transaction t = parent(); t != null; t = t.parent()) {
            i++;
        }
        return i;
    }

    public String indent(String indent) {
        StringBuffer i = new StringBuffer();
        for (Transaction t = parent(); t != null; t = t.parent()) {
            i.append(indent);
        }
        return i.toString();
    }

    public abstract Mutable mutable();

}
