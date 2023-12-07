//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2023 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import org.modelingvalue.collections.Collection;
import org.modelingvalue.collections.Set;
import org.modelingvalue.collections.util.StringUtil;

public abstract class Leaf implements TransactionClass, Feature {

    private final Object               id;
    private final Priority             initPriority;
    private final Set<LeafModifier<?>> modifierSet;

    protected Leaf(Object id, LeafModifier<?>... modifiers) {
        this.id = id;
        this.modifierSet = Collection.of(modifiers).notNull().asSet();
        Priority prio = getModifier(Priority.class);
        this.initPriority = prio == null ? Priority.one : prio;
    }

    public boolean hasModifier(LeafModifier<?> modifier) {
        return modifierSet.contains(modifier);
    }

    @SuppressWarnings({"unused", "unchecked"})
    public <SM extends LeafModifier<?>> SM getModifier(Class<SM> modifierClass) {
        return (SM) FeatureModifier.ofClass(modifierClass, modifierSet);
    }

    @Override
    public int hashCode() {
        return id.hashCode() ^ getClass().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (getClass() != obj.getClass()) {
            return false;
        } else {
            return id.equals(((Leaf) obj).id);
        }
    }

    @Override
    public String toString() {
        return StringUtil.toString(id);
    }

    public Object id() {
        return id;
    }

    protected final Priority initPriority() {
        return initPriority;
    }

}
