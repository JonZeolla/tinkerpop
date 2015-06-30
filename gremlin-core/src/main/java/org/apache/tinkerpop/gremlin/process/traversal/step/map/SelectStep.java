/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.process.traversal.step.map;

import org.apache.tinkerpop.gremlin.process.traversal.Pop;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.step.PathProcessor;
import org.apache.tinkerpop.gremlin.process.traversal.step.Scoping;
import org.apache.tinkerpop.gremlin.process.traversal.step.TraversalParent;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.TraverserRequirement;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalRing;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalUtil;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class SelectStep<S, E> extends MapStep<S, Map<String, E>> implements Scoping, TraversalParent, PathProcessor {

    private TraversalRing<Object, E> traversalRing = new TraversalRing<>();
    private final Pop pop;
    private final List<String> selectKeys;

    public SelectStep(final Traversal.Admin traversal, final Pop pop, final String... selectKeys) {
        super(traversal);
        this.pop = pop;
        this.selectKeys = Arrays.asList(selectKeys);
        if (this.selectKeys.size() < 2)
            throw new IllegalArgumentException("At least two select keys must be provided: " + this);
    }

    @Override
    protected Map<String, E> map(final Traverser.Admin<S> traverser) {
        final Map<String, E> bindings = new LinkedHashMap<>();
        for (final String selectKey : this.selectKeys) {
            final E end = this.getNullableScopeValue(this.pop, selectKey, traverser);
            if (null != end)
                bindings.put(selectKey, TraversalUtil.apply(end, this.traversalRing.next()));
            else {
                this.traversalRing.reset();
                return null;
            }
        }
        this.traversalRing.reset();
        return bindings;
    }

    @Override
    public void reset() {
        super.reset();
        this.traversalRing.reset();
    }

    @Override
    public String toString() {
        return StringFactory.stepString(this, this.pop, this.selectKeys, this.traversalRing);
    }

    @Override
    public SelectStep<S, E> clone() {
        final SelectStep<S, E> clone = (SelectStep<S, E>) super.clone();
        clone.traversalRing = this.traversalRing.clone();
        clone.getLocalChildren().forEach(clone::integrateChild);
        return clone;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode() ^ this.traversalRing.hashCode() ^ this.selectKeys.hashCode();
        if (null != this.pop)
            result ^= this.pop.hashCode();
        return result;
    }

    @Override
    public List<Traversal.Admin<Object, E>> getLocalChildren() {
        return this.traversalRing.getTraversals();
    }

    @Override
    public void addLocalChild(final Traversal.Admin<?, ?> selectTraversal) {
        this.traversalRing.addTraversal(this.integrateChild(selectTraversal));
    }

    @Override
    public Set<TraverserRequirement> getRequirements() {
        return this.getSelfAndChildRequirements(TraversalHelper.getLabels(TraversalHelper.getRootTraversal(this.traversal)).stream().filter(this.selectKeys::contains).findAny().isPresent() ?
                TYPICAL_GLOBAL_REQUIREMENTS_ARRAY :
                TYPICAL_LOCAL_REQUIREMENTS_ARRAY);
    }

    @Override
    public Set<String> getScopeKeys() {
        return new HashSet<>(this.selectKeys);
    }
}
