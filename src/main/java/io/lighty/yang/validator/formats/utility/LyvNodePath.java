/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package io.lighty.yang.validator.formats.utility;

import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

/**
 * A compact representation of a schema tree path to a {@link LyvNodeData}.
 */
public final class LyvNodePath {
    private final @Nullable LyvNodePath parent;
    private final @NonNull QName qname;

    private LyvNodePath(final QName qname, final LyvNodePath parent) {
        this.qname = requireNonNull(qname);
        this.parent = parent;
    }

    public static @NonNull LyvNodePath of(final QName qname) {
        return new LyvNodePath(qname, null);
    }

    public QName getQName() {
        return qname;
    }

    public @NonNull LyvNodePath child(final QName qname) {
        return new LyvNodePath(qname, this);
    }

    public @NonNull Absolute toAbsolute() {
        var wlk = parent;
        if (wlk == null) {
            return Absolute.of(qname);
        }

        // Use Deque for efficient prepending of QNames as we descend down
        final var nodeIdentifiers = new ArrayDeque<QName>();
        nodeIdentifiers.addFirst(qname);
        do {
            nodeIdentifiers.addFirst(wlk.qname);
            wlk = wlk.parent;
        } while (wlk != null);

        return Absolute.of(nodeIdentifiers);
    }
}
