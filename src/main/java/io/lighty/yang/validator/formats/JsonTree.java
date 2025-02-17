/*
 * Copyright (c) 2021 Pantheon Technologies s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package io.lighty.yang.validator.formats;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.lighty.yang.validator.GroupArguments;
import io.lighty.yang.validator.config.Configuration;
import io.lighty.yang.validator.exceptions.NotFoundException;
import io.lighty.yang.validator.simplify.SchemaTree;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.json.JSONObject;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.AnydataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AnyxmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.IdentitySchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.TypedDataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonTree extends FormatPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(JsonTree.class);
    private static final String HELP_NAME = "json-tree";
    private static final String HELP_DESCRIPTION = "return json tree with module and node metadata";
    private static final String BASETYPENAMESPACE = "urn:ietf:params:xml:ns:yang:1";
    private static final String EARLIEST_REVISION = "1970-01-01";
    private static final String CHILDREN = "children";
    private static final String NAME = "name";
    private static final String REVISION = "revision";
    private static final String NAMESPACE = "namespace";
    private static final String CONFIG = "config";
    private static final String DESCRIPTION = "description";
    private static final String MODULE = "module";
    private static final String MODULE_STRING = "Module";
    private static final String TYPE_INFO = "type_info";
    private static final String STATUS = "status";
    private static final String CLASS = "class";
    private static final String NOTIFICATION = "notification";
    private static final String NOTIFICATIONS = NOTIFICATION + "s";
    private static final String PATH = "path";
    private static final String RPC = "rpc";
    private static final String RPCS = RPC + "s";
    private static final String AUG = "augmentation";
    private static final String AUGMENTS = "augments";
    private static final String ACTION = "action";
    private static final String EMPTY = "";
    private static final String OUTPUT_TEXT = "output";
    private static final String PREFIX = "prefix";
    private static final String CONTACT = "contact";
    private static final String TYPE = "type";
    private static final String DEFAULT = "default";
    private static final String BASE = "base";
    private static final String UNKNOWN = "unknown";
    private static final String SLASH = "/";
    private static final String COLON = ":";

    @Override
    void init(final EffectiveModelContext context, final List<SourceIdentifier> testFilesSchemaSources,
            final SchemaTree schemaTree, final Configuration config) {
        super.init(context, testFilesSchemaSources, schemaTree, config);
    }

    @Override
    @SuppressFBWarnings(value = "SLF4J_SIGN_ONLY_FORMAT",
                        justification = "Valid output from LYV is dependent on Logback output")
    public void emitFormat() {
        for (final SourceIdentifier source : this.sources) {
            final Module module = this.schemaContext.findModule(source.name().getLocalName(), source.revision())
                    .orElseThrow(() -> new NotFoundException(MODULE_STRING, source.name().getLocalName()));
            final JSONObject moduleMetadata = resolveModuleMetadata(module);
            final JSONObject jsonTree = new JSONObject();
            final SchemaInferenceStack schemaInferenceStack = SchemaInferenceStack.of(this.schemaContext);
            appendChildNodesToJsonTree(module, jsonTree, schemaInferenceStack);
            appendNotificationsToJsonTree(module, jsonTree, schemaInferenceStack);
            appendRpcsToJsonTree(module, jsonTree, schemaInferenceStack);

            for (final AugmentationSchemaNode augmentation : module.getAugmentations()) {
                schemaInferenceStack.enterSchemaTree(augmentation.getTargetPath());
                final JSONObject augmentationJson = new JSONObject();
                final boolean isConfig = isAugmentConfig(augmentation);
                augmentationJson.put(CONFIG, isConfig);
                augmentationJson.put(STATUS, augmentation.getStatus().name());
                augmentationJson.put(DESCRIPTION, augmentation.getDescription().orElse(EMPTY));
                augmentationJson.put(STATUS, augmentation.getStatus().name());
                augmentationJson.put(CLASS, AUG);
                final String path = resolvePath(augmentation.getTargetPath());
                augmentationJson.put(PATH, path);
                augmentationJson.put(NAME, path);
                for (final DataSchemaNode child : augmentation.getChildNodes()) {
                    if (isConfig) {
                        augmentationJson.append(CHILDREN, resolveChildMetadata(child, Optional.empty(),
                                schemaInferenceStack));
                    } else {
                        augmentationJson.append(CHILDREN, resolveChildMetadata(child, Optional.of(false),
                                schemaInferenceStack));
                    }
                }

                appendActionsToAugmentationJson(augmentation, augmentationJson, schemaInferenceStack);
                appendNotificationsToAugmentationJson(module, augmentationJson, schemaInferenceStack);
                jsonTree.append(AUGMENTS, augmentationJson);
                schemaInferenceStack.clear();
            }
            jsonTree.put(MODULE, moduleMetadata);
            final String jsonTreeText = jsonTree.toString(4);
            LOG.info("{}", jsonTreeText);
        }
    }

    private void appendNotificationsToAugmentationJson(final Module module, final JSONObject augmentationJson,
            final SchemaInferenceStack schemaInferenceStack) {
        for (final NotificationDefinition notification : module.getNotifications()) {
            final JSONObject jsonNotification = new JSONObject();
            for (final DataSchemaNode node : notification.getChildNodes()) {
                jsonNotification.append(CHILDREN, resolveChildMetadata(node, Optional.of(false), schemaInferenceStack));
            }
            putNotificationDataToJsonNotification(notification, jsonNotification, schemaInferenceStack);
            augmentationJson.append(NOTIFICATIONS, jsonNotification);
        }
    }

    private void appendActionsToAugmentationJson(final AugmentationSchemaNode augmentation,
            final JSONObject augmentationJson,
            final SchemaInferenceStack schemaInferenceStack) {
        for (final ActionDefinition child : augmentation.getActions()) {
            schemaInferenceStack.enterSchemaTree(child.getQName());
            final JSONObject jsonModuleChildAction = new JSONObject();
            jsonModuleChildAction.put(NAME, child.getQName().getLocalName());
            jsonModuleChildAction.put(DESCRIPTION, child.getDescription().orElse(EMPTY));
            jsonModuleChildAction.put(STATUS, child.getStatus().name());
            jsonModuleChildAction.put(TYPE_INFO, new JSONObject());
            jsonModuleChildAction.put(CLASS, ACTION);
            jsonModuleChildAction.put(PATH, resolvePath(schemaInferenceStack.toSchemaNodeIdentifier()));
            jsonModuleChildAction.append(CHILDREN, resolveChildMetadata(child.getInput(), Optional.empty(),
                    schemaInferenceStack));
            jsonModuleChildAction.append(CHILDREN, resolveChildMetadata(child.getOutput(), Optional.of(false),
                    schemaInferenceStack));
            augmentationJson.append(CHILDREN, jsonModuleChildAction);
            schemaInferenceStack.exit();
        }
    }

    private void appendRpcsToJsonTree(final Module module, final JSONObject jsonTree,
            final SchemaInferenceStack schemaInferenceStack) {
        for (final RpcDefinition rpc : module.getRpcs()) {
            schemaInferenceStack.enterSchemaTree(rpc.getQName());
            final JSONObject jsonRpc = new JSONObject();
            jsonRpc.put(NAME, rpc.getQName().getLocalName());
            jsonRpc.put(DESCRIPTION, rpc.getDescription().orElse(EMPTY));
            jsonRpc.put(STATUS, rpc.getStatus().name());
            jsonRpc.put(TYPE_INFO, new JSONObject());
            jsonRpc.put(CLASS, RPC);
            jsonRpc.put(PATH, resolvePath(schemaInferenceStack.toSchemaNodeIdentifier()));
            jsonRpc.append(CHILDREN, resolveChildMetadata(rpc.getInput(), Optional.empty(), schemaInferenceStack));
            jsonRpc.append(CHILDREN, resolveChildMetadata(rpc.getOutput(), Optional.of(false), schemaInferenceStack));
            jsonTree.append(RPCS, jsonRpc);
            schemaInferenceStack.exit();
        }
    }

    private void appendNotificationsToJsonTree(final Module module, final JSONObject jsonTree,
            final SchemaInferenceStack schemaInferenceStack) {
        for (final NotificationDefinition notification : module.getNotifications()) {
            schemaInferenceStack.enterSchemaTree(notification.getQName());
            final JSONObject jsonNotification = new JSONObject();
            for (final DataSchemaNode node : notification.getChildNodes()) {
                jsonNotification.append(CHILDREN, resolveChildMetadata(node, Optional.of(false), schemaInferenceStack));
            }
            putNotificationDataToJsonNotification(notification, jsonNotification, schemaInferenceStack);
            jsonTree.append(NOTIFICATIONS, jsonNotification);
            schemaInferenceStack.exit();
        }
    }

    private void putNotificationDataToJsonNotification(final NotificationDefinition notification,
            final JSONObject jsonNotification, final SchemaInferenceStack schemaInferenceStack) {
        jsonNotification.put(NAME, notification.getQName().getLocalName());
        jsonNotification.put(DESCRIPTION, notification.getDescription().orElse(EMPTY));
        jsonNotification.put(STATUS, notification.getStatus().name());
        jsonNotification.put(TYPE_INFO, new JSONObject());
        jsonNotification.put(CLASS, NOTIFICATION);
        jsonNotification.put(PATH, schemaInferenceStack.toSchemaNodeIdentifier());
    }

    private void appendChildNodesToJsonTree(final Module module, final JSONObject jsonTree,
            final SchemaInferenceStack schemaInferenceStack) {
        for (final DataSchemaNode node : module.getChildNodes()) {
            jsonTree.append(CHILDREN, resolveChildMetadata(node, Optional.empty(), schemaInferenceStack));
        }
    }

    private Boolean isAugmentConfig(final AugmentationSchemaNode augmentation) {
        final List<QName> qNames = new ArrayList<>();
        Collection<? extends ActionDefinition> actions = new HashSet<>();
        boolean isAction = false;
        for (final QName path : augmentation.getTargetPath().getNodeIdentifiers()) {
            if (isAction) {
                return !OUTPUT_TEXT.equals(path.getLocalName());
            }
            if (shouldSkipThisIteration(actions, path)) {
                isAction = true;
                continue;
            }

            qNames.add(path);
            final Optional<DataSchemaNode> dataTreeChild = schemaContext.findDataTreeChild(qNames);
            if (dataTreeChild.isPresent()) {
                final Optional<Boolean> isConfig = dataTreeChild.get().effectiveConfig();
                if (isConfig.isPresent() && !isConfig.get()) {
                    return false;
                }
                if (dataTreeChild.get() instanceof ActionNodeContainer) {
                    actions = ((ActionNodeContainer) dataTreeChild.get()).getActions();
                }
            } else {
                qNames.remove(path);
            }
        }
        return true;
    }

    private boolean shouldSkipThisIteration(final Collection<? extends ActionDefinition> actions, final QName path) {
        for (final ActionDefinition action : actions) {
            if (action.getQName().getLocalName().equals(path.getLocalName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Help getHelp() {
        return new Help(HELP_NAME, HELP_DESCRIPTION);
    }

    @Override
    public Optional<GroupArguments> getGroupArguments() {
        return Optional.empty();
    }

    private JSONObject resolveChildMetadata(final DataSchemaNode node, final Optional<Boolean> isConfig,
            final SchemaInferenceStack schemaInferenceStack) {
        final JSONObject jsonModuleChild = new JSONObject();
        jsonModuleChild.put(NAME, node.getQName().getLocalName());
        jsonModuleChild.put(CONFIG, isConfig.orElse(node.isConfiguration()));
        jsonModuleChild.put(DESCRIPTION, node.getDescription().orElse(EMPTY));
        jsonModuleChild.put(STATUS, node.getStatus().name());
        jsonModuleChild.put(TYPE_INFO, new JSONObject());
        jsonModuleChild.put(CLASS, resolveNodeClass(node));
        schemaInferenceStack.enterSchemaTree(node.getQName());
        jsonModuleChild.put(PATH, resolvePath(schemaInferenceStack.toSchemaNodeIdentifier()));
        if (node instanceof ActionNodeContainer) {
            for (final ActionDefinition child : ((ActionNodeContainer) node).getActions()) {
                schemaInferenceStack.enterSchemaTree(child.getQName());
                final JSONObject jsonModuleChildAction = new JSONObject();
                jsonModuleChildAction.put(NAME, child.getQName().getLocalName());
                jsonModuleChildAction.put(DESCRIPTION, child.getDescription().orElse(EMPTY));
                jsonModuleChildAction.put(STATUS, child.getStatus().name());
                jsonModuleChildAction.put(TYPE_INFO, new JSONObject());
                jsonModuleChildAction.put(PATH, resolvePath(schemaInferenceStack.toSchemaNodeIdentifier()));
                jsonModuleChildAction.put(CLASS, ACTION);
                jsonModuleChildAction.append(CHILDREN, resolveChildMetadata(child.getInput(), isConfig,
                        schemaInferenceStack));
                jsonModuleChildAction.append(CHILDREN, resolveChildMetadata(child.getOutput(), Optional.of(false),
                        schemaInferenceStack));
                jsonModuleChild.append(CHILDREN, jsonModuleChildAction);
                schemaInferenceStack.exit();
            }
        }
        if (node instanceof DataNodeContainer) {
            for (final DataSchemaNode child : ((DataNodeContainer) node).getChildNodes()) {
                jsonModuleChild.append(CHILDREN, resolveChildMetadata(child, isConfig, schemaInferenceStack));
            }
        } else if (node instanceof ChoiceSchemaNode) {
            for (final CaseSchemaNode caseNode : ((ChoiceSchemaNode) node).getCases()) {
                jsonModuleChild.append(CHILDREN, resolveChildMetadata(caseNode, isConfig, schemaInferenceStack));
            }
        } else if (node instanceof TypedDataSchemaNode) {
            jsonModuleChild.put(TYPE_INFO, resolveType(((TypedDataSchemaNode) node).getType()));
            jsonModuleChild.put(CHILDREN, Collections.emptyList());
        }
        schemaInferenceStack.exit();
        return jsonModuleChild;
    }

    private JSONObject resolveType(final TypeDefinition<? extends TypeDefinition<?>> nodeType) {
        final JSONObject jsonLeafType = new JSONObject();
        final QName typeqName = nodeType.getQName();
        final int equals = typeqName.getNamespace().compareTo(XMLNamespace.of(BASETYPENAMESPACE));
        final String type;
        if (equals == 0) {
            type = typeqName.getLocalName();
        } else if (nodeType instanceof IdentityrefTypeDefinition) {
            type = typeqName.getLocalName();
            for (final IdentitySchemaNode base : ((IdentityrefTypeDefinition) nodeType).getIdentities()) {
                jsonLeafType.append(BASE, base.getQName().getLocalName());
            }
        } else {
            final String prefix = schemaContext.findModule(typeqName.getNamespace(), typeqName.getRevision())
                    .orElseThrow(() -> new NotFoundException(MODULE_STRING, typeqName.getNamespace().toString()))
                    .getPrefix();
            type = prefix + COLON + typeqName.getLocalName();
        }
        jsonLeafType.put(DESCRIPTION, nodeType.getDescription().orElse(EMPTY));
        jsonLeafType.put(TYPE, type);
        nodeType.getDefaultValue().ifPresent(value -> jsonLeafType.put(DEFAULT, value));
        return jsonLeafType;
    }

    private String resolveNodeClass(final DataSchemaNode node) {
        if (node instanceof ListSchemaNode) {
            return "list";
        } else if (node instanceof ContainerLike) {
            return "container";
        } else if (node instanceof LeafListSchemaNode) {
            return "leaf-list";
        } else if (node instanceof LeafSchemaNode) {
            return "leaf";
        } else if (node instanceof ChoiceSchemaNode) {
            return "choice";
        } else if (node instanceof CaseSchemaNode) {
            return "case";
        } else if (node instanceof AnyxmlSchemaNode) {
            return "anyxml";
        } else if (node instanceof AnydataSchemaNode) {
            return "anydata";
        } else {
            LOG.warn("Node type unknown: {}", node);
            return UNKNOWN;
        }
    }

    private JSONObject resolveModuleMetadata(final Module module) {
        final JSONObject jsonModuleMetadata = new JSONObject();
        jsonModuleMetadata.put(NAME, module.getName());
        jsonModuleMetadata.put(REVISION, module.getRevision().orElse(Revision.of(EARLIEST_REVISION)).toString());
        jsonModuleMetadata.put(NAMESPACE, module.getNamespace());
        jsonModuleMetadata.put(PREFIX, module.getPrefix());
        jsonModuleMetadata.put(CONTACT, module.getContact().orElse(EMPTY));
        jsonModuleMetadata.put(DESCRIPTION, module.getDescription().orElse(EMPTY));
        return jsonModuleMetadata;
    }

    private String resolvePath(final SchemaNodeIdentifier schemaNodeIdentifier) {
        final Iterable<QName> pathFromRoot = schemaNodeIdentifier.getNodeIdentifiers();
        final StringBuilder path = new StringBuilder(SLASH);
        for (final QName pathQname : pathFromRoot) {
            schemaContext.findModule(pathQname.getModule()).ifPresent(module1 -> path.append(module1.getPrefix()));
            path.append(COLON)
                    .append(pathQname.getLocalName())
                    .append(SLASH);
        }
        return path.toString();
    }
}
