/*
 * Copyright (c) 2023, WSO2 LLC. (https://www.wso2.com) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerina.persist.nodegenerator.syntax.utils;

import io.ballerina.compiler.syntax.tree.AbstractNodeFactory;
import io.ballerina.compiler.syntax.tree.AnnotationNode;
import io.ballerina.compiler.syntax.tree.ArrayDimensionNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.IdentifierToken;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.ImportOrgNameNode;
import io.ballerina.compiler.syntax.tree.ImportPrefixNode;
import io.ballerina.compiler.syntax.tree.MappingConstructorExpressionNode;
import io.ballerina.compiler.syntax.tree.MappingFieldNode;
import io.ballerina.compiler.syntax.tree.MinutiaeList;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeFactory;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.NodeParser;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.SpecificFieldNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.persist.components.Client;
import io.ballerina.persist.components.Function;
import io.ballerina.persist.components.TypeDescriptor;
import io.ballerina.persist.models.Entity;
import io.ballerina.persist.models.EntityField;
import io.ballerina.persist.models.Enum;
import io.ballerina.persist.models.EnumMember;
import io.ballerina.persist.models.Module;
import io.ballerina.persist.models.Relation;
import io.ballerina.persist.nodegenerator.syntax.constants.BalSyntaxConstants;
import io.ballerina.persist.nodegenerator.syntax.constants.SyntaxTokenConstants;
import io.ballerina.tools.text.TextDocument;
import io.ballerina.tools.text.TextDocuments;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * This class is used to generate the common syntax tree for the client.
 *
 * @since 0.3.1
 */
public class BalSyntaxUtils {

    public static NodeList<ImportDeclarationNode> generateImport(Module entityModule) {
        NodeList<ImportDeclarationNode> imports = AbstractNodeFactory.createEmptyNodeList();
        MinutiaeList commentMinutiaeList = createCommentMinutiaeList(String.format(
                BalSyntaxConstants.AUTO_GENERATED_COMMENT_WITH_REASON, entityModule.getModuleName()));
        imports = imports.add(getImportDeclarationNodeWithAutogeneratedComment(
                commentMinutiaeList));
        imports = imports.add(getImportDeclarationNode(BalSyntaxConstants.KEYWORD_BALLERINA,
                BalSyntaxConstants.KEYWORD_JBALLERINA_JAVA_PREFIX, null));
        return imports;
    }

    public static NodeList<ModuleMemberDeclarationNode> generateConstantVariables(Module entityModule) {
        NodeList<ModuleMemberDeclarationNode> moduleMembers = AbstractNodeFactory.createEmptyNodeList();
        for (Entity entity : entityModule.getEntityMap().values()) {
            moduleMembers = moduleMembers.add(NodeParser.parseModuleMemberDeclaration(String.format(
                    "const %s = \"%s\";", getStringWithUnderScore(entity.getEntityName()),
                    entity.getResourceName())));
        }
        return moduleMembers;
    }

    public static SyntaxTree generateSyntaxTree(NodeList<ImportDeclarationNode> imports,
                                                NodeList<ModuleMemberDeclarationNode> moduleMembers) {
        Token eofToken = AbstractNodeFactory.createIdentifierToken(BalSyntaxConstants.EMPTY_STRING);
        ModulePartNode modulePartNode = NodeFactory.createModulePartNode(imports, moduleMembers, eofToken);
        TextDocument textDocument = TextDocuments.from(BalSyntaxConstants.EMPTY_STRING);
        SyntaxTree balTree = SyntaxTree.from(textDocument);
        return balTree.modifyWith(modulePartNode);
    }

    public static Client generateClientSignature() {
        Client clientObject = new Client("Client", true);
        clientObject.addQualifiers(new String[] { BalSyntaxConstants.KEYWORD_CLIENT });
        clientObject.addMember(NodeFactory.createTypeReferenceNode(
                AbstractNodeFactory.createToken(SyntaxKind.ASTERISK_TOKEN),
                NodeFactory.createQualifiedNameReferenceNode(
                        NodeFactory.createIdentifierToken(
                                BalSyntaxConstants.InheritedTypeReferenceConstants.PERSIST_MODULE_NAME),
                        AbstractNodeFactory.createToken(SyntaxKind.COLON_TOKEN),
                        NodeFactory.createIdentifierToken(
                                BalSyntaxConstants.InheritedTypeReferenceConstants.ABSTRACT_PERSIST_CLIENT)),
                AbstractNodeFactory.createToken(SyntaxKind.SEMICOLON_TOKEN)), false);
        return clientObject;
    }

    public static Function generateCloseFunction() {
        Function close = new Function(BalSyntaxConstants.CLOSE, SyntaxKind.OBJECT_METHOD_DEFINITION);
        close.addQualifiers(new String[] { BalSyntaxConstants.KEYWORD_PUBLIC });
        close.addReturns(TypeDescriptor.getOptionalTypeDescriptorNode(BalSyntaxConstants.EMPTY_STRING,
                BalSyntaxConstants.PERSIST_ERROR));
        return close;
    }

    public static FunctionDefinitionNode generateGetFunction(Entity entity, String className) {
        return (FunctionDefinitionNode) NodeParser.parseObjectMember(
                String.format(BalSyntaxConstants.EXTERNAL_GET_METHOD_TEMPLATE,
                        entity.getResourceName(), entity.getEntityName(), className));
    }

    public static FunctionDefinitionNode generateGetByKeyFunction(Entity entity, String className) {
        StringBuilder keyBuilder = new StringBuilder();
        for (EntityField keyField : entity.getKeys()) {
            if (keyBuilder.length() > 0) {
                keyBuilder.append("/");
            }
            keyBuilder.append(BalSyntaxConstants.OPEN_BRACKET);
            keyBuilder.append(keyField.getFieldType());
            keyBuilder.append(BalSyntaxConstants.SPACE);
            keyBuilder.append(keyField.getFieldName());
            keyBuilder.append(BalSyntaxConstants.CLOSE_BRACKET);
        }

        return (FunctionDefinitionNode) NodeParser.parseObjectMember(
                String.format(BalSyntaxConstants.EXTERNAL_GET_BY_KEY_METHOD_TEMPLATE,
                        entity.getResourceName(), keyBuilder, entity.getEntityName(), className));
    }

    public static Function generatePostFunction(Entity entity, List<EntityField> primaryKeys, String parameterType) {
        Function create = new Function(BalSyntaxConstants.POST, SyntaxKind.RESOURCE_ACCESSOR_DEFINITION);
        NodeList<Node> resourcePaths = AbstractNodeFactory.createEmptyNodeList();
        resourcePaths = resourcePaths.add(AbstractNodeFactory.createIdentifierToken(entity.getResourceName()));
        create.addRelativeResourcePaths(resourcePaths);
        create.addRequiredParameter(
                TypeDescriptor.getArrayTypeDescriptorNode(parameterType), BalSyntaxConstants.KEYWORD_VALUE);
        create.addQualifiers(new String[] { BalSyntaxConstants.KEYWORD_ISOLATED, BalSyntaxConstants.KEYWORD_RESOURCE });
        addReturnsToPostResourceSignature(create, primaryKeys);
        return create;
    }

    public static Function generatePutFunction(Entity entity, StringBuilder filterKeys, StringBuilder path) {
        Function update = new Function(BalSyntaxConstants.PUT, SyntaxKind.RESOURCE_ACCESSOR_DEFINITION);
        update.addQualifiers(new String[] { BalSyntaxConstants.KEYWORD_ISOLATED, BalSyntaxConstants.KEYWORD_RESOURCE });
        update.addRequiredParameter(TypeDescriptor.getSimpleNameReferenceNode(
                String.format(BalSyntaxConstants.UPDATE_RECORD, entity.getEntityName())), BalSyntaxConstants.VALUE);
        NodeList<Node> resourcePaths = AbstractNodeFactory.createEmptyNodeList();
        resourcePaths = getResourcePath(resourcePaths, entity.getKeys(), filterKeys, path, entity.getResourceName());
        update.addRelativeResourcePaths(resourcePaths);
        update.addReturns(TypeDescriptor.getUnionTypeDescriptorNode(
                TypeDescriptor.getSimpleNameReferenceNode(entity.getEntityName()),
                TypeDescriptor.getQualifiedNameReferenceNode(BalSyntaxConstants.PERSIST_MODULE,
                        BalSyntaxConstants.SPECIFIC_ERROR)));
        return update;
    }

    public static Function generateDeleteFunction(Entity entity, StringBuilder filterKeys, StringBuilder path) {
        Function delete = new Function(BalSyntaxConstants.DELETE, SyntaxKind.RESOURCE_ACCESSOR_DEFINITION);
        delete.addQualifiers(new String[] { BalSyntaxConstants.KEYWORD_ISOLATED, BalSyntaxConstants.KEYWORD_RESOURCE });
        NodeList<Node> resourcePaths = AbstractNodeFactory.createEmptyNodeList();
        resourcePaths = getResourcePath(resourcePaths, entity.getKeys(), filterKeys, path, entity.getResourceName());
        delete.addRelativeResourcePaths(resourcePaths);
        delete.addReturns(TypeDescriptor.getUnionTypeDescriptorNode(
                TypeDescriptor.getSimpleNameReferenceNode(entity.getEntityName()),
                TypeDescriptor.getQualifiedNameReferenceNode(BalSyntaxConstants.PERSIST_MODULE,
                        BalSyntaxConstants.SPECIFIC_ERROR)));
        return delete;
    }

    private static MinutiaeList createCommentMinutiaeList(String comment) {
        return NodeFactory.createMinutiaeList(
                AbstractNodeFactory.createCommentMinutiae(BalSyntaxConstants.AUTOGENERATED_FILE_COMMENT),
                AbstractNodeFactory.createEndOfLineMinutiae(System.lineSeparator()),
                AbstractNodeFactory.createEndOfLineMinutiae(System.lineSeparator()),
                AbstractNodeFactory.createCommentMinutiae(comment),
                AbstractNodeFactory.createEndOfLineMinutiae(System.lineSeparator()),
                AbstractNodeFactory.createCommentMinutiae(BalSyntaxConstants.COMMENT_SHOULD_NOT_BE_MODIFIED),
                AbstractNodeFactory.createEndOfLineMinutiae(System.lineSeparator()),
                AbstractNodeFactory.createEndOfLineMinutiae(System.lineSeparator()));
    }

    private static ImportDeclarationNode getImportDeclarationNodeWithAutogeneratedComment(
            MinutiaeList commentMinutiaeList) {
        Token orgNameToken = AbstractNodeFactory.createIdentifierToken(BalSyntaxConstants.KEYWORD_BALLERINA);
        ImportOrgNameNode importOrgNameNode = NodeFactory.createImportOrgNameNode(
                orgNameToken,
                SyntaxTokenConstants.SYNTAX_TREE_SLASH);
        Token moduleNameToken = AbstractNodeFactory.createIdentifierToken(BalSyntaxConstants.PERSIST_MODULE);
        SeparatedNodeList<IdentifierToken> moduleNodeList = AbstractNodeFactory
                .createSeparatedNodeList(moduleNameToken);
        Token importToken = NodeFactory.createToken(SyntaxKind.IMPORT_KEYWORD,
                commentMinutiaeList, NodeFactory.createMinutiaeList(AbstractNodeFactory
                        .createWhitespaceMinutiae(BalSyntaxConstants.SPACE)));
        return NodeFactory.createImportDeclarationNode(
                importToken,
                importOrgNameNode,
                moduleNodeList,
                null,
                SyntaxTokenConstants.SYNTAX_TREE_SEMICOLON);
    }

    private static void addReturnsToPostResourceSignature(Function create, List<EntityField> primaryKeys) {
        ArrayDimensionNode arrayDimensionNode = NodeFactory.createArrayDimensionNode(
                SyntaxTokenConstants.SYNTAX_TREE_OPEN_BRACKET,
                null,
                SyntaxTokenConstants.SYNTAX_TREE_CLOSE_BRACKET);
        NodeList<ArrayDimensionNode> dimensionList = NodeFactory.createNodeList(arrayDimensionNode);
        List<Node> typeTuple = new ArrayList<>();
        if (primaryKeys.size() > 1) {
            primaryKeys.forEach(primaryKey -> {
                if (!typeTuple.isEmpty()) {
                    typeTuple.add(NodeFactory.createToken(SyntaxKind.COMMA_TOKEN));
                }
                typeTuple.add(NodeFactory.createSimpleNameReferenceNode(
                        NodeFactory.createIdentifierToken(primaryKey.getFieldType())));
            });
            create.addReturns(TypeDescriptor.getUnionTypeDescriptorNode(
                    NodeFactory.createArrayTypeDescriptorNode(NodeFactory.createTupleTypeDescriptorNode(
                            NodeFactory.createToken(SyntaxKind.OPEN_BRACKET_TOKEN),
                            NodeFactory.createSeparatedNodeList(typeTuple),
                            NodeFactory.createToken(SyntaxKind.CLOSE_BRACKET_TOKEN)), dimensionList),
                    TypeDescriptor.getQualifiedNameReferenceNode(BalSyntaxConstants.PERSIST_MODULE,
                            BalSyntaxConstants.SPECIFIC_ERROR)));
        } else {
            create.addReturns(TypeDescriptor.getUnionTypeDescriptorNode(
                    TypeDescriptor.getArrayTypeDescriptorNode(primaryKeys.get(0).getFieldType()),
                    TypeDescriptor.getQualifiedNameReferenceNode(BalSyntaxConstants.PERSIST_MODULE,
                            BalSyntaxConstants.SPECIFIC_ERROR)));

        }
    }

    private static NodeList<Node> getResourcePath(NodeList<Node> resourcePaths, List<EntityField> keys,
                                                  StringBuilder filterKeys, StringBuilder path, String tableName) {
        resourcePaths = resourcePaths.add(AbstractNodeFactory.createIdentifierToken(tableName));
        for (EntityField entry : keys) {
            resourcePaths = resourcePaths.add(AbstractNodeFactory.createToken(SyntaxKind.SLASH_TOKEN));
            resourcePaths = resourcePaths.add(NodeFactory.createResourcePathParameterNode(
                    SyntaxKind.RESOURCE_PATH_SEGMENT_PARAM,
                    AbstractNodeFactory.createToken(SyntaxKind.OPEN_BRACKET_TOKEN),
                    AbstractNodeFactory.createEmptyNodeList(),
                    NodeFactory.createBuiltinSimpleNameReferenceNode(SyntaxKind.STRING_TYPE_DESC,
                            AbstractNodeFactory.createIdentifierToken(entry.getFieldType() +
                                    BalSyntaxConstants.SPACE)),
                    null,
                    AbstractNodeFactory.createIdentifierToken(entry.getFieldName()),
                    AbstractNodeFactory.createToken(SyntaxKind.CLOSE_BRACKET_TOKEN)));
            filterKeys.append(BalSyntaxConstants.DOUBLE_QUOTE).append(stripEscapeCharacter(entry.getFieldName()))
                    .append(BalSyntaxConstants.DOUBLE_QUOTE).append(BalSyntaxConstants.COLON).
                    append(entry.getFieldName()).append(BalSyntaxConstants.COMMA_SPACE);
            path.append(BalSyntaxConstants.BACK_SLASH).append(BalSyntaxConstants.OPEN_BRACKET).
                    append(entry.getFieldName()).append(BalSyntaxConstants.CLOSE_BRACKET);
        }
        return resourcePaths;
    }

    public static SyntaxTree generateTypeSyntaxTree(Module entityModule) {
        NodeList<ImportDeclarationNode> imports = AbstractNodeFactory.createEmptyNodeList();
        NodeList<ModuleMemberDeclarationNode> moduleMembers = AbstractNodeFactory.createEmptyNodeList();
        MinutiaeList commentMinutiaeList = createCommentMinutiaeList(String.format(
                BalSyntaxConstants.AUTO_GENERATED_COMMENT_WITH_REASON, entityModule.getModuleName()));
        for (String modulePrefix : entityModule.getImportModulePrefixes()) {
            if (imports.isEmpty()) {
                imports = imports.add(getImportDeclarationNodeWithAutogeneratedComment(
                        modulePrefix,
                        commentMinutiaeList));
            } else {
                imports = imports.add(getImportDeclarationNode(modulePrefix));
            }
        }
        boolean includeAutoGeneratedComment = imports.isEmpty();

        for (Enum enumValue: entityModule.getEnumMap().values()) {
            moduleMembers = moduleMembers.add(createEnumDeclaration(enumValue, includeAutoGeneratedComment,
                    entityModule.getModuleName()));

            if (includeAutoGeneratedComment) {
                includeAutoGeneratedComment = false;
            }
        }

        for (Entity entity : entityModule.getEntityMap().values()) {
            boolean hasRelations = false;
            for (EntityField field : entity.getFields()) {
                if (field.getRelation() != null) {
                    hasRelations = true;
                    break;
                }
            }

            moduleMembers = moduleMembers.add(createEntityRecord(entity, includeAutoGeneratedComment,
                    entityModule.getModuleName()));

            if (includeAutoGeneratedComment) {
                includeAutoGeneratedComment = false;
            }

            moduleMembers = moduleMembers.add(createEntityRecordOptionalized(entity));
            if (hasRelations) {
                moduleMembers = moduleMembers.add(createEntityRecordWithRelation(entity));
            }
            moduleMembers = moduleMembers.add(createEntityTargetType(entity, hasRelations));
            moduleMembers = moduleMembers.add(NodeParser.parseModuleMemberDeclaration(
                    String.format("public type %sInsert %s;", entity.getEntityName(),
                            entity.getEntityName())));
            moduleMembers = moduleMembers.add(createUpdateRecord(entity));
        }
        Token eofToken = AbstractNodeFactory.createIdentifierToken(BalSyntaxConstants.EMPTY_STRING);
        ModulePartNode modulePartNode = NodeFactory.createModulePartNode(imports, moduleMembers, eofToken);
        TextDocument textDocument = TextDocuments.from(BalSyntaxConstants.EMPTY_STRING);
        SyntaxTree balTree = SyntaxTree.from(textDocument);
        return balTree.modifyWith(modulePartNode);
    }

    private static ImportDeclarationNode getImportDeclarationNode(String moduleName) {
        Token orgNameToken = AbstractNodeFactory.createIdentifierToken(BalSyntaxConstants.KEYWORD_BALLERINA);
        ImportOrgNameNode importOrgNameNode = NodeFactory.createImportOrgNameNode(
                orgNameToken,
                SyntaxTokenConstants.SYNTAX_TREE_SLASH);
        Token moduleNameToken = AbstractNodeFactory.createIdentifierToken(moduleName);
        SeparatedNodeList<IdentifierToken> moduleNodeList = AbstractNodeFactory
                .createSeparatedNodeList(moduleNameToken);

        return NodeFactory.createImportDeclarationNode(
                SyntaxTokenConstants.SYNTAX_TREE_KEYWORD_IMPORT,
                importOrgNameNode,
                moduleNodeList,
                null,
                SyntaxTokenConstants.SYNTAX_TREE_SEMICOLON);
    }

    private static ModuleMemberDeclarationNode createEntityRecord(Entity entity, boolean includeAutogeneratedComment,
                                                                  String moduleName) {
        StringBuilder recordFields = new StringBuilder();
        for (EntityField field : entity.getFields()) {
            if (entity.getKeys().stream().anyMatch(key -> key == field)) {
                addConstrainAnnotationToField(field, recordFields);
                recordFields.append(BalSyntaxConstants.KEYWORD_READONLY);
                recordFields.append(BalSyntaxConstants.SPACE);
                recordFields.append(field.getFieldType());
                if (field.isArrayType()) {
                    recordFields.append(BalSyntaxConstants.ARRAY);
                }
                recordFields.append(BalSyntaxConstants.SPACE);
                recordFields.append(field.getFieldName());
                recordFields.append(BalSyntaxConstants.SEMICOLON);
                recordFields.append(BalSyntaxConstants.SPACE);
            } else if (field.getRelation() != null) {
                if (field.getRelation().isOwner()) {
                    for (Relation.Key key : field.getRelation().getKeyColumns()) {
                        addConstraintsAnnotationForForeignKey(field, recordFields);
                        recordFields.append(key.getType());
                        recordFields.append(BalSyntaxConstants.SPACE);
                        recordFields.append(key.getField());
                        recordFields.append(BalSyntaxConstants.SEMICOLON);
                        recordFields.append(BalSyntaxConstants.SPACE);
                    }
                }
            } else {
                addConstrainAnnotationToField(field, recordFields);
                recordFields.append(field.isOptionalType() ? field.getFieldType() + (field.isArrayType() ?
                        BalSyntaxConstants.ARRAY : "") + BalSyntaxConstants.QUESTION_MARK : field.getFieldType() +
                        (field.isArrayType() ? BalSyntaxConstants.ARRAY : ""));
                recordFields.append(BalSyntaxConstants.SPACE);
                recordFields.append(field.getFieldName());
                recordFields.append(BalSyntaxConstants.SEMICOLON);
                recordFields.append(BalSyntaxConstants.SPACE);
            }

        }
        if (includeAutogeneratedComment) {
            String commentBuilder = BalSyntaxConstants.AUTOGENERATED_FILE_COMMENT + System.lineSeparator() +
                    System.lineSeparator() + String.format(BalSyntaxConstants.AUTO_GENERATED_COMMENT_WITH_REASON,
                    moduleName) + System.lineSeparator() + BalSyntaxConstants.COMMENT_SHOULD_NOT_BE_MODIFIED +
                    System.lineSeparator() + System.lineSeparator() + "public type %s record {| %s |};";
            return NodeParser.parseModuleMemberDeclaration(String.format(commentBuilder,
                    entity.getEntityName().trim(), recordFields));
        }
        return NodeParser.parseModuleMemberDeclaration(String.format("public type %s record {| %s |};",
                entity.getEntityName().trim(), recordFields));
    }

    private static ModuleMemberDeclarationNode createEnumDeclaration(Enum enumValue,
                                                                     boolean includeAutogeneratedComment,
                                                                     String moduleName) {
        StringBuilder enumMembers = new StringBuilder();
        for (int i = 0; i < enumValue.getMembers().size(); i++) {
            EnumMember member = enumValue.getMembers().get(i);
            enumMembers.append(member.getIdentifier());
            if (member.getValue() != null) {
                enumMembers.append(BalSyntaxConstants.SPACE);
                enumMembers.append(BalSyntaxConstants.EQUAL);
                enumMembers.append(BalSyntaxConstants.SPACE);
                enumMembers.append(BalSyntaxConstants.DOUBLE_QUOTE);
                enumMembers.append(member.getValue());
                enumMembers.append(BalSyntaxConstants.DOUBLE_QUOTE);
            }

            if (i != enumValue.getMembers().size() - 1) {
                enumMembers.append(BalSyntaxConstants.COMMA_SPACE);
            }
            enumMembers.append(System.lineSeparator());
        }

        if (includeAutogeneratedComment) {
            String commentBuilder = BalSyntaxConstants.AUTOGENERATED_FILE_COMMENT + System.lineSeparator() +
                    System.lineSeparator() + String.format(BalSyntaxConstants.AUTO_GENERATED_COMMENT_WITH_REASON,
                    moduleName) + System.lineSeparator() + BalSyntaxConstants.COMMENT_SHOULD_NOT_BE_MODIFIED +
                    System.lineSeparator() + System.lineSeparator() + "public enum %s { %s }";
            return NodeParser.parseModuleMemberDeclaration(String.format(commentBuilder,
                    enumValue.getEnumName().trim(), enumMembers));
        }
        return NodeParser.parseModuleMemberDeclaration(String.format("public enum %s { %s }",
                enumValue.getEnumName().trim(), enumMembers));
    }


    private static ModuleMemberDeclarationNode createEntityRecordOptionalized(Entity entity) {
        StringBuilder recordFields = new StringBuilder();
        for (EntityField field : entity.getFields()) {
            if (field.getRelation() != null) {
                addConstraintsAnnotationForForeignKey(field, recordFields);
                if (field.getRelation().isOwner()) {
                    for (Relation.Key key : field.getRelation().getKeyColumns()) {
                        recordFields.append(key.getType());
                        recordFields.append(BalSyntaxConstants.SPACE);
                        recordFields.append(key.getField());
                        recordFields.append(BalSyntaxConstants.QUESTION_MARK);
                        recordFields.append(BalSyntaxConstants.SEMICOLON);
                        recordFields.append(BalSyntaxConstants.SPACE);
                    }
                }
            } else {
                addConstrainAnnotationToField(field, recordFields);
                recordFields.append(field.isOptionalType() ? field.getFieldType() + (field.isArrayType() ?
                        BalSyntaxConstants.ARRAY : "") + BalSyntaxConstants.QUESTION_MARK : field.getFieldType() +
                        (field.isArrayType() ? BalSyntaxConstants.ARRAY : ""));
                recordFields.append(BalSyntaxConstants.SPACE);
                recordFields.append(field.getFieldName());
                recordFields.append(BalSyntaxConstants.QUESTION_MARK);
                recordFields.append(BalSyntaxConstants.SEMICOLON);
                recordFields.append(BalSyntaxConstants.SPACE);
            }

        }
        return NodeParser.parseModuleMemberDeclaration(String.format("public type %sOptionalized record {| %s |};",
                entity.getEntityName().trim(), recordFields));
    }

    private static void addConstraintsAnnotationForForeignKey(EntityField field, StringBuilder recordFields) {
        Relation relation = field.getRelation();
        List<String> references = relation.getReferences();
        for (EntityField assocField : relation.getAssocEntity().getFields()) {
            for (String reference : references) {
                if (assocField.getFieldName().equals(reference)) {
                    NodeList<AnnotationNode> annotation = assocField.getAnnotation();
                    if (annotation != null) {
                        String params = getConstraintField(assocField);
                        if (params != null) {
                            recordFields.append(String.format(BalSyntaxConstants.CONSTRAINT_ANNOTATION,
                                    params));
                        }
                    }
                    break;
                }
            }
        }
    }

    private static void addConstrainAnnotationToField(EntityField field, StringBuilder recordFields) {
        NodeList<AnnotationNode> annotation = field.getAnnotation();
        if (annotation != null) {
            String params = getConstraintField(field);
            if (params != null) {
                recordFields.append(String.format(BalSyntaxConstants.CONSTRAINT_ANNOTATION, params));
            }
        }
    }

    private static String getConstraintField(EntityField field) {
        for (AnnotationNode annotationNode : field.getAnnotation()) {
            String annotationName = annotationNode.annotReference().toSourceCode().trim();
            if (annotationName.equals(BalSyntaxConstants.CONSTRAINT_STRING)) {
                Optional<MappingConstructorExpressionNode> annotationFieldNode = annotationNode.annotValue();
                if (annotationFieldNode.isPresent()) {
                    for (MappingFieldNode mappingFieldNode : annotationFieldNode.get().fields()) {
                        SpecificFieldNode specificFieldNode = (SpecificFieldNode) mappingFieldNode;
                        String fieldName = specificFieldNode.fieldName().toSourceCode().trim();
                        if (fieldName.equals(BalSyntaxConstants.MAX_LENGTH)) {
                            Optional<ExpressionNode> valueExpr = specificFieldNode.valueExpr();
                            if (valueExpr.isPresent()) {
                                return BalSyntaxConstants.MAX_LENGTH + ":" +
                                        valueExpr.get().toSourceCode().trim();
                            }
                        } else if (fieldName.equals(BalSyntaxConstants.LENGTH)) {
                            Optional<ExpressionNode> valueExpr = specificFieldNode.valueExpr();
                            if (valueExpr.isPresent()) {
                                return BalSyntaxConstants.LENGTH + ":" +
                                        valueExpr.get().toSourceCode().trim();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static ModuleMemberDeclarationNode createEntityRecordWithRelation(Entity entity) {
        StringBuilder recordFields = new StringBuilder();
        recordFields.append(String.format("*%sOptionalized;", entity.getEntityName()));
        for (EntityField field : entity.getFields()) {
            if (field.getRelation() != null) {
                recordFields.append(String.format("%sOptionalized", field.getFieldType()));
                if (field.isArrayType()) {
                    recordFields.append("[]");
                }
                recordFields.append(BalSyntaxConstants.SPACE);
                recordFields.append(field.getFieldName());
                recordFields.append(BalSyntaxConstants.QUESTION_MARK);
                recordFields.append(BalSyntaxConstants.SEMICOLON);
                recordFields.append(BalSyntaxConstants.SPACE);
            }

        }
        return NodeParser.parseModuleMemberDeclaration(String.format("public type %sWithRelations record {| %s |};",
                entity.getEntityName().trim(), recordFields));
    }

    private static ModuleMemberDeclarationNode createEntityTargetType(Entity entity, boolean hasRelations) {
        return NodeParser.parseModuleMemberDeclaration(String.format("public type %sTargetType " +
                "typedesc<%s>;", entity.getEntityName().trim(), entity.getEntityName().trim() +
                (hasRelations ? "WithRelations" : "Optionalized")));
    }

    private static ModuleMemberDeclarationNode createUpdateRecord(Entity entity) {
        StringBuilder recordFields = new StringBuilder();
        for (EntityField field : entity.getFields()) {
            if (entity.getKeys().stream().noneMatch(key -> key == field)) {
                if (field.getRelation() != null) {
                    if (field.getRelation().isOwner()) {
                        for (Relation.Key key : field.getRelation().getKeyColumns()) {
                            addConstraintsAnnotationForForeignKey(field, recordFields);
                            recordFields.append(key.getType());
                            recordFields.append(" ");
                            recordFields.append(key.getField());
                            recordFields.append(BalSyntaxConstants.QUESTION_MARK);
                            recordFields.append(BalSyntaxConstants.SEMICOLON);
                            recordFields.append(BalSyntaxConstants.SPACE);
                        }
                    }
                } else {
                    addConstrainAnnotationToField(field, recordFields);
                    recordFields.append(field.isOptionalType()
                            ? field.getFieldType() + (field.isArrayType() ? BalSyntaxConstants.ARRAY : "") +
                            BalSyntaxConstants.QUESTION_MARK : field.getFieldType() + (field.isArrayType() ?
                            BalSyntaxConstants.ARRAY : ""));
                    recordFields.append(BalSyntaxConstants.SPACE);
                    recordFields.append(field.getFieldName());
                    recordFields.append(BalSyntaxConstants.QUESTION_MARK);
                    recordFields.append(BalSyntaxConstants.SEMICOLON);
                    recordFields.append(BalSyntaxConstants.SPACE);
                }
            }

        }
        return NodeParser.parseModuleMemberDeclaration(String.format("public type %sUpdate record {| %s |};",
                entity.getEntityName().trim(), recordFields));
    }

    private static ImportDeclarationNode getImportDeclarationNodeWithAutogeneratedComment(
            String moduleName, MinutiaeList commentMinutiaeList) {
        Token orgNameToken = AbstractNodeFactory.createIdentifierToken(BalSyntaxConstants.KEYWORD_BALLERINA);
        ImportOrgNameNode importOrgNameNode = NodeFactory.createImportOrgNameNode(
                orgNameToken,
                SyntaxTokenConstants.SYNTAX_TREE_SLASH);
        Token moduleNameToken = AbstractNodeFactory.createIdentifierToken(moduleName);
        SeparatedNodeList<IdentifierToken> moduleNodeList = AbstractNodeFactory
                .createSeparatedNodeList(moduleNameToken);
        Token importToken = NodeFactory.createToken(SyntaxKind.IMPORT_KEYWORD,
                commentMinutiaeList, NodeFactory.createMinutiaeList(AbstractNodeFactory
                        .createWhitespaceMinutiae(BalSyntaxConstants.SPACE)));
        return NodeFactory.createImportDeclarationNode(
                importToken,
                importOrgNameNode,
                moduleNodeList,
                null,
                SyntaxTokenConstants.SYNTAX_TREE_SEMICOLON);
    }

    public static String getStringWithUnderScore(String entityName) {
        StringBuilder outputString = new StringBuilder();
        String[] splitedStrings = stripEscapeCharacter(entityName).split(
                BalSyntaxConstants.REGEX_FOR_SPLIT_BY_CAPITOL_LETTER);
        for (String splitedString : splitedStrings) {
            if (outputString.length() != 0) {
                outputString.append(BalSyntaxConstants.UNDERSCORE);
            }
            outputString.append(splitedString.toUpperCase(Locale.ENGLISH));
        }
        if (entityName.startsWith(BalSyntaxConstants.SINGLE_QUOTE)) {
            return BalSyntaxConstants.SINGLE_QUOTE + outputString;
        }
        return outputString.toString();
    }

    public static String stripEscapeCharacter(String fieldName) {
        return fieldName.startsWith(BalSyntaxConstants.SINGLE_QUOTE) ? fieldName.substring(1) : fieldName;
    }

    public static ImportDeclarationNode getImportDeclarationNode(String orgName, String moduleName,
                                                                 ImportPrefixNode prefix) {
        Token orgNameToken = AbstractNodeFactory.createIdentifierToken(orgName);
        ImportOrgNameNode importOrgNameNode = NodeFactory.createImportOrgNameNode(
                orgNameToken,
                SyntaxTokenConstants.SYNTAX_TREE_SLASH);
        Token moduleNameToken = AbstractNodeFactory.createIdentifierToken(moduleName);
        SeparatedNodeList<IdentifierToken> moduleNodeList = AbstractNodeFactory
                .createSeparatedNodeList(moduleNameToken);

        return NodeFactory.createImportDeclarationNode(
                SyntaxTokenConstants.SYNTAX_TREE_KEYWORD_IMPORT,
                importOrgNameNode,
                moduleNodeList,
                prefix,
                SyntaxTokenConstants.SYNTAX_TREE_SEMICOLON);
    }
}
