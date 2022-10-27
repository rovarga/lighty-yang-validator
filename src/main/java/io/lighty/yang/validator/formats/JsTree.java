/*
 * Copyright (c) 2021 Pantheon Technologies s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package io.lighty.yang.validator.formats;

import com.google.common.io.Resources;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.lighty.yang.validator.GroupArguments;
import io.lighty.yang.validator.exceptions.NotFoundException;
import io.lighty.yang.validator.formats.utility.LyvNodeData;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsTree extends FormatPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(JsTree.class);
    private static final String HELP_NAME = "jstree";
    private static final String HELP_DESCRIPTION = "Prints out html, javascript tree of the modules";
    private static final String INPUT = "input";

    private Map<XMLNamespace, String> namespacePrefix = new HashMap<>();

    @Override
    @SuppressFBWarnings(value = "SLF4J_SIGN_ONLY_FORMAT",
                        justification = "Valid output from LYV is dependent on Logback output")
    public void emitFormat() {
        namespacePrefix = new HashMap<>();
        for (final SourceIdentifier source : sources) {
            final Module module = schemaContext.findModule(source.name().getLocalName(), source.revision())
                    .orElseThrow(() -> new NotFoundException("Module", source.name().getLocalName()));
            final SingletonListInitializer singletonListInitializer = new SingletonListInitializer(1);

            // Nodes
            printLines(getChildNodesLines(singletonListInitializer, module));

            // Augmentations
            for (final AugmentationSchemaNode augNode : module.getAugmentations()) {
                printLines(getAugmentationNodesLines(singletonListInitializer.getSingletonListWithIncreasedValue(),
                        augNode));
            }

            // Rpcs
            printLines(getRpcsLines(singletonListInitializer, module));

            // Notifications
            printLines(getNotificationsLines(singletonListInitializer, module));
        }

        LOG.info("</table>");
        LOG.info("</div>");
        LOG.info("{}", loadJS());
        LOG.info("</body>");
        LOG.info("</html>");
    }

    @SuppressFBWarnings(value = "SLF4J_SIGN_ONLY_FORMAT",
                        justification = "Valid output from LYV is dependent on Logback output")
    private static void printLines(final List<Line> lines) {
        for (final Line line : lines) {
            LOG.info("{}", line);
        }
    }

    private List<Line> getNotificationsLines(final SingletonListInitializer singletonListInitializer,
            final Module module) {
        final List<Line> lines = new ArrayList<>();
        final Deque<QName> currentPath = new ArrayDeque<>();
        for (final NotificationDefinition node : module.getNotifications()) {
            currentPath.addLast(node.getQName());
            final List<Integer> ids = singletonListInitializer.getSingletonListWithIncreasedValue();
            final LyvNodeData lyvNodeData = new LyvNodeData(schemaContext, node, currentPath);
            final HtmlLine htmlLine = new HtmlLine(new ArrayList<>(ids), lyvNodeData, RpcInputOutput.OTHER,
                    namespacePrefix);
            lines.add(htmlLine);
            resolveChildNodes(lines, new ArrayList<>(ids), node, RpcInputOutput.OTHER, Collections.emptyList(),
                currentPath);
            currentPath.removeLast();
        }
        return lines;
    }

    private List<Line> getRpcsLines(final SingletonListInitializer singletonListInitializer, final Module module) {
        final List<Line> lines = new ArrayList<>();
        final Deque<QName> currentPath = new ArrayDeque<>();
        for (final RpcDefinition node : module.getRpcs()) {
            currentPath.addLast(node.getQName());
            final List<Integer> rpcId = singletonListInitializer.getSingletonListWithIncreasedValue();
            LyvNodeData lyvNodeData = new LyvNodeData(schemaContext, node, currentPath);
            HtmlLine htmlLine = new HtmlLine(rpcId, lyvNodeData, RpcInputOutput.OTHER, namespacePrefix);
            lines.add(htmlLine);
            final boolean inputExists = !node.getInput().getChildNodes().isEmpty();
            final boolean outputExists = !node.getOutput().getChildNodes().isEmpty();
            List<Integer> ids = new ArrayList<>(rpcId);
            if (inputExists) {
                ids.add(1);
                currentPath.addLast(node.getInput().getQName());
                lyvNodeData = new LyvNodeData(schemaContext, node.getInput(), currentPath);
                htmlLine = new HtmlLine(new ArrayList<>(ids), lyvNodeData, RpcInputOutput.INPUT, namespacePrefix);
                lines.add(htmlLine);
                resolveChildNodes(lines, new ArrayList<>(ids), node.getInput(), RpcInputOutput.INPUT,
                        Collections.emptyList(), currentPath);
                currentPath.removeLast();
            }
            ids = new ArrayList<>(rpcId);
            if (outputExists) {
                if (!inputExists) {
                    ids.add(1);
                } else {
                    ids.add(2);
                }
                currentPath.addLast(node.getOutput().getQName());
                lyvNodeData = new LyvNodeData(schemaContext, node.getOutput(), currentPath);
                htmlLine = new HtmlLine(new ArrayList<>(ids), lyvNodeData, RpcInputOutput.OUTPUT, namespacePrefix);
                lines.add(htmlLine);
                resolveChildNodes(lines, new ArrayList<>(ids), node.getOutput(), RpcInputOutput.OUTPUT,
                        Collections.emptyList(), currentPath);
                currentPath.removeLast();
            }
            currentPath.removeLast();
        }
        return lines;
    }

    @SuppressFBWarnings(value = "SLF4J_SIGN_ONLY_FORMAT",
                        justification = "Valid output from LYV is dependent on Logback output")
    private List<Line> getChildNodesLines(final SingletonListInitializer singletonListInitializer,
            final Module module) {
        final List<Line> lines = new ArrayList<>();
        final String headerText = prepareHeader(module);
        LOG.info("{}", headerText);
        for (final Module m : schemaContext.getModules()) {
            if (!m.getPrefix().equals(module.getPrefix())) {
                namespacePrefix.put(m.getNamespace(), m.getPrefix());
            }
        }

        final Deque<QName> currentPath = new ArrayDeque<>();
        for (final DataSchemaNode node : module.getChildNodes()) {
            currentPath.addLast(node.getQName());
            final List<Integer> ids = singletonListInitializer.getSingletonListWithIncreasedValue();
            final LyvNodeData lyvNodeData = new LyvNodeData(schemaContext, node, currentPath);
            final HtmlLine htmlLine = new HtmlLine(ids, lyvNodeData, RpcInputOutput.OTHER, namespacePrefix);
            lines.add(htmlLine);
            resolveChildNodes(lines, new ArrayList<>(ids), node, RpcInputOutput.OTHER, Collections.emptyList(),
                currentPath);
            currentPath.removeLast();
        }
        return lines;
    }

    private List<Line> getAugmentationNodesLines(final List<Integer> ids, final AugmentationSchemaNode augNode) {
        final List<Line> lines = new ArrayList<>();
        final Deque<QName> currentPath = new ArrayDeque<>();
        currentPath.addAll(augNode.getTargetPath().getNodeIdentifiers());
        final DataSchemaNode dataSchemaNode = augNode.getChildNodes().iterator().next();
        currentPath.addLast(dataSchemaNode.getQName());
        LyvNodeData lyvNodeData = new LyvNodeData(schemaContext, dataSchemaNode, currentPath);
        final HtmlLine htmlLine = new HtmlLine(new ArrayList<>(ids), lyvNodeData, RpcInputOutput.OTHER, namespacePrefix,
                augNode);
        lines.add(htmlLine);
        currentPath.removeLast();
        int modelAugmentationNumber = 1;
        for (DataSchemaNode node : augNode.getChildNodes()) {
            currentPath.addLast(node.getQName());
            final RpcInputOutput inputOutputOther = getAugmentationRpcInputOutput(currentPath);
            ids.add(modelAugmentationNumber++);
            lyvNodeData = new LyvNodeData(schemaContext, node, currentPath);
            final HtmlLine line = new HtmlLine(new ArrayList<>(ids), lyvNodeData, inputOutputOther, namespacePrefix);
            lines.add(line);
            resolveChildNodes(lines, new ArrayList<>(ids), node, RpcInputOutput.OTHER, Collections.emptyList(),
                currentPath);
            ids.remove(ids.size() - 1);
            currentPath.removeLast();
        }
        return lines;
    }

    private RpcInputOutput getAugmentationRpcInputOutput(final Deque<QName> currentPath) {
        // FIXME: do not perform this copy?
        List<QName> qnames = new ArrayList<>(currentPath);

        Collection<? extends ActionDefinition> actions = new HashSet<>();
        RpcInputOutput inputOutputOther = RpcInputOutput.OTHER;
        for (int i = 1; i <= qnames.size(); i++) {
            final List<QName> qnamesCopy = qnames.subList(0, i);
            inputOutputOther = getRpcInputOutput(qnames, actions, inputOutputOther, i, qnamesCopy);
            if (schemaContext.findDataTreeChild(qnamesCopy).get() instanceof ActionNodeContainer) {
                final ActionNodeContainer actionSchemaNode =
                        (ActionNodeContainer) schemaContext.findDataTreeChild(qnamesCopy).get();
                actions = actionSchemaNode.getActions();
            }
        }
        return inputOutputOther;
    }

    private static RpcInputOutput getRpcInputOutput(final List<QName> qnames,
            final Collection<? extends ActionDefinition> actions, final RpcInputOutput inputOutputOther,
            final int iteration, final List<QName> qnamesCopy) {
        if (actions.isEmpty()) {
            return inputOutputOther;
        }
        for (final ActionDefinition action : actions) {
            if (action.getQName().getLocalName().equals(qnamesCopy.get(qnamesCopy.size() - 1).getLocalName())) {
                if (INPUT.equals(qnames.get(iteration).getLocalName())) {
                    return RpcInputOutput.INPUT;
                } else {
                    return RpcInputOutput.OUTPUT;
                }
            }
        }
        return inputOutputOther;
    }

    private static String loadJS() {
        final URL url = Resources.getResource("js");
        String text = "";
        try {
            text = Resources.toString(url, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            LOG.error("Can not load text from js file");
        }

        return text;
    }

    private static String prepareHeader(final Module module) {
        final StringBuilder nameRevision = new StringBuilder(module.getName());
        module.getRevision().ifPresent(value -> nameRevision.append("@").append(value));
        final URL url = Resources.getResource("header");
        String text = "";
        try {
            text = Resources.toString(url, StandardCharsets.UTF_8);
            text = text.replace("<NAME_REVISION>", nameRevision);
            text = text.replace("<NAMESPACE>", module.getNamespace().toString());
            text = text.replace("<PREFIX>", module.getPrefix());
        } catch (final IOException e) {
            LOG.error("Can not load text from header file");
        }

        return text;
    }

    private void resolveChildNodes(final List<Line> lines, final List<Integer> connections, final SchemaNode node,
            final RpcInputOutput inputOutput, final List<QName> keys, final Deque<QName> currentPath) {
        if (node instanceof DataNodeContainer) {
            final Iterator<? extends DataSchemaNode> childNodes = ((DataNodeContainer) node).getChildNodes().iterator();
            resolveDataNodeContainer(childNodes, lines, connections, inputOutput, keys, currentPath);
        } else if (node instanceof ChoiceSchemaNode) {
            connections.add(0);
            final Collection<? extends CaseSchemaNode> cases = ((ChoiceSchemaNode) node).getCases();
            final Iterator<? extends CaseSchemaNode> iterator = cases.iterator();
            resolveChoiceSchemaNode(iterator, lines, connections, inputOutput, currentPath);
        }
        // If action is in container or list
        if (node instanceof ActionNodeContainer) {
            resolveActionNodeContainer(lines, connections, node, currentPath);
        }
    }

    private void resolveActionNodeContainer(final List<Line> lines, final List<Integer> connections,
            final SchemaNode node, final Deque<QName> currentPath) {
        for (final ActionDefinition action : ((ActionNodeContainer) node).getActions()) {
            final int id = 1;
            connections.add(0);
            connections.set(connections.size() - 1, id);
            currentPath.addLast(action.getQName());
            LyvNodeData lyvNodeData = new LyvNodeData(schemaContext, action, currentPath);
            HtmlLine htmlLine = new HtmlLine(new ArrayList<>(connections), lyvNodeData, RpcInputOutput.OTHER,
                    namespacePrefix);
            lines.add(htmlLine);
            final boolean inputExists = !action.getInput().getChildNodes().isEmpty();
            final boolean outputExists = !action.getOutput().getChildNodes().isEmpty();
            if (inputExists) {
                connections.add(1);
                currentPath.addLast(action.getInput().getQName());
                lyvNodeData = new LyvNodeData(schemaContext, action.getInput(), currentPath);
                htmlLine = new HtmlLine(new ArrayList<>(connections), lyvNodeData, RpcInputOutput.INPUT,
                        namespacePrefix);
                lines.add(htmlLine);
                resolveChildNodes(lines, new ArrayList<>(connections), action.getInput(), RpcInputOutput.INPUT,
                        Collections.emptyList(), currentPath);
                connections.remove(connections.size() - 1);
                currentPath.removeLast();
            }
            if (outputExists) {
                connections.add(1);
                currentPath.addLast(action.getOutput().getQName());
                lyvNodeData = new LyvNodeData(schemaContext, action.getOutput(), currentPath);
                htmlLine = new HtmlLine(new ArrayList<>(connections), lyvNodeData, RpcInputOutput.OUTPUT,
                        namespacePrefix);
                lines.add(htmlLine);
                resolveChildNodes(lines, new ArrayList<>(connections), action.getOutput(), RpcInputOutput.OUTPUT,
                        Collections.emptyList(), currentPath);
                connections.remove(connections.size() - 1);
                currentPath.removeLast();
            }
            connections.remove(connections.size() - 1);
            currentPath.removeLast();
        }
    }

    private void resolveChoiceSchemaNode(final Iterator<? extends CaseSchemaNode> iterator, final List<Line> lines,
            final List<Integer> connections, final RpcInputOutput inputOutput, final Deque<QName> currentPath) {
        int id = 1;
        while (iterator.hasNext()) {
            final DataSchemaNode child = iterator.next();
            currentPath.addLast(child.getQName());
            connections.set(connections.size() - 1, id++);
            final LyvNodeData lyvNodeData = new LyvNodeData(schemaContext, child, currentPath);
            final HtmlLine htmlLine = new HtmlLine(new ArrayList<>(connections), lyvNodeData, inputOutput,
                    namespacePrefix);
            lines.add(htmlLine);
            resolveChildNodes(lines, new ArrayList<>(connections), child, inputOutput, Collections.emptyList(),
                    currentPath);
            currentPath.removeLast();
        }
        // remove last
        connections.remove(connections.size() - 1);
    }

    private void resolveDataNodeContainer(final Iterator<? extends DataSchemaNode> childNodes,
            final List<Line> lines, final List<Integer> connections, final RpcInputOutput inputOutput,
            final List<QName> keys, final Deque<QName> currentPath) {
        int id = 1;
        connections.add(0);
        while (childNodes.hasNext()) {
            final DataSchemaNode child = childNodes.next();
            currentPath.addLast(child.getQName());
            connections.set(connections.size() - 1, id++);
            final LyvNodeData lyvNodeData = new LyvNodeData(schemaContext, child, currentPath, keys);
            final HtmlLine htmlLine = new HtmlLine(new ArrayList<>(connections), lyvNodeData, inputOutput,
                    namespacePrefix);
            lines.add(htmlLine);
            List<QName> keyDefinitions = Collections.emptyList();
            if (child instanceof ListSchemaNode) {
                keyDefinitions = ((ListSchemaNode) child).getKeyDefinition();
            }
            resolveChildNodes(lines, new ArrayList<>(connections), child, inputOutput, keyDefinitions, currentPath);
            currentPath.removeLast();
        }
        // remove last only if the conatiner is not root container
        if (connections.size() > 1) {
            connections.remove(connections.size() - 1);
        }
    }

    @Override
    public Help getHelp() {
        return new Help(HELP_NAME, HELP_DESCRIPTION);
    }

    @Override
    public Optional<GroupArguments> getGroupArguments() {
        return Optional.empty();
    }

    private static class SingletonListInitializer {

        private int id;

        SingletonListInitializer(final int initialValue) {
            id = initialValue;
        }

        List<Integer> getSingletonListWithIncreasedValue() {
            return new ArrayList<>(Collections.singletonList(id++));
        }
    }
}
