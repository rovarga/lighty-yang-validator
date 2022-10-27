/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package io.lighty.yang.validator.formats.utility;

import java.util.ArrayDeque;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

/**
 * A simple stack for tracking current path and capturing instances as {@link LyvNodePath}s.
 */
public class LyvNodePathStack {
    private final ArrayDeque<Object> stack = new ArrayDeque<>();

    public void clear() {
        stack.clear();
    }

    public void enter(final QName qname) {
        stack.addLast(qname);
    }

    public void enter(final SchemaNodeIdentifier targetPath) {
        if (targetPath instanceof Absolute) {
            clear();
        }
        stack.addAll(targetPath.getNodeIdentifiers());
    }

    public void exit() {
        stack.removeLast();
    }

    public @NonNull LyvNodePath currentPath() {
        final var last = stack.peekLast();
        if (last instanceof LyvNodePath path) {
            return path;
        } else if (last instanceof QName qname) {
            stack.removeLast();
            return constructPath(qname);
        } else {
            throw new IllegalStateException("Unhandled path item " + last);
        }
    }

    private @NonNull LyvNodePath constructPath(final QName qname) {
        final var path = stack.isEmpty() ? LyvNodePath.of(qname) : currentPath().child(qname);
        stack.addLast(path);
        return path;
    }
}
