/*
 * Copyright (c) 2021 Pantheon Technologies s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package io.lighty.yang.validator.formats.utility;

import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.MandatoryAware;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

public class LyvNodeData {

    private final boolean isKey;
    private final EffectiveModelContext context;
    private final SchemaNode node;
    private final LyvNodePath path;

    public LyvNodeData(final @NonNull EffectiveModelContext context, final @NonNull SchemaNode node,
            final @NonNull LyvNodePath path) {
        this(context, node, path, null);
    }

    public LyvNodeData(final @NonNull EffectiveModelContext context, final @NonNull SchemaNode node,
            final @NonNull LyvNodePath path, @Nullable final List<QName> keys) {
        this.context = context;
        this.path = path;
        this.node = node;
        isKey = keys != null && keys.contains(node.getQName());
    }

    public EffectiveModelContext getContext() {
        return context;
    }

    public SchemaNode getNode() {
        return node;
    }

    public LyvNodePath getPath() {
        return path;
    }

    // FIXME: remove this
    public Absolute getAbsolutePath() {
        return path.toAbsolute();
    }

    public boolean isNodeMandatory() {
        return node instanceof MandatoryAware && ((MandatoryAware) node).isMandatory()
                || node instanceof ContainerLike || node instanceof CaseSchemaNode
                || node instanceof NotificationDefinition || node instanceof ActionDefinition
                || node instanceof RpcDefinition || isKey;
    }
}
