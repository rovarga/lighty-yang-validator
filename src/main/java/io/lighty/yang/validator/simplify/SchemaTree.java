/*
 * Copyright (c) 2021 Pantheon Technologies s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package io.lighty.yang.validator.simplify;

import static java.util.Objects.requireNonNull;

import io.lighty.yang.validator.formats.utility.LyvNodePath;
import java.util.LinkedHashSet;
import java.util.Set;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

public class SchemaTree implements Comparable<SchemaTree> {
    public static final LyvNodePath ROOT = LyvNodePath.of(QName.create("root", "root"));

    private final LyvNodePath path;
    private final DataSchemaNode schemaNode;
    private final boolean isRootNode;
    private final boolean isAugmenting;
    private final ActionDefinition actionNode;
    private final Set<SchemaTree> children = new LinkedHashSet<>();

    SchemaTree(final LyvNodePath path, final DataSchemaNode schemaNode, final boolean isRootNode,
            final boolean isAugmenting, final ActionDefinition actionNode) {
        this.path = requireNonNull(path);
        this.schemaNode = schemaNode;
        this.isRootNode = isRootNode;
        this.isAugmenting = isAugmenting;
        this.actionNode = actionNode;
    }

    public QName getQname() {
        return path.getQName();
    }

    public LyvNodePath getPath() {
        return path;
    }

    // FIXME: remove this
    public Absolute getAbsolutePath() {
        return path.toAbsolute();
    }

    public boolean isRootNode() {
        return isRootNode;
    }

    public boolean isAugmenting() {
        return isAugmenting;
    }

    public DataSchemaNode getSchemaNode() {
        return schemaNode;
    }

    public ActionDefinition getActionNode() {
        return actionNode;
    }

    public SchemaTree addChild(final SchemaTree tree) {
        if (children.add(tree)) {
            return tree;
        } else {
            return children.stream()
                    .filter(schemaTree -> schemaTree.equals(tree))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            String.format("Failed to add SchemaNode [%s] to SchemaTree path", getAbsolutePath())));
        }
    }

    public SchemaTree addChild(final DataSchemaNode schemaNodeInput, final boolean isRootNodeInput,
            final boolean isAugmentingInput, final LyvNodePath path) {
        final SchemaTree tree = new SchemaTree(path, schemaNodeInput, isRootNodeInput, isAugmentingInput, null);
        return addChild(tree);
    }

    SchemaTree addChild(final ActionDefinition schemaNodeInput, final boolean isRootNodeInput,
            final boolean augmentation, final LyvNodePath path) {
        final SchemaTree tree = new SchemaTree(path, null, isRootNodeInput, augmentation, schemaNodeInput);
        return addChild(tree);
    }

    public Set<SchemaTree> getChildren() {
        return children;
    }

    public Set<SchemaTree> getDataSchemaNodeChildren() {
        final Set<SchemaTree> ret = new LinkedHashSet<>();
        for (final SchemaTree child : children) {
            if (child.getSchemaNode() != null) {
                ret.add(child);
            }
        }
        return ret;
    }

    public Set<SchemaTree> getActionDefinitionChildren() {
        final Set<SchemaTree> ret = new LinkedHashSet<>();
        for (final SchemaTree child : children) {
            if (child.getActionNode() != null) {
                ret.add(child);
            }
        }
        return ret;
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || obj != null && getClass() == obj.getClass() && path.equals(((SchemaTree) obj).path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public int compareTo(final SchemaTree originalTree) {
        return getQname().compareTo(originalTree.getQname());
    }
}

